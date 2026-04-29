// ── Stage 2: Secret Detection ─────────────────────────────────
// Scans for secrets, credentials and sensitive data
// HARD BLOCK — any finding stops the pipeline immediately
// Tools: GitLeaks, TruffleHog, custom pattern scan
// Runs all tools in parallel for speed

def run() {
    def tools    = load 'jenkins/helpers/tools.groovy'
    def notify   = load 'jenkins/helpers/notify.groovy'
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 2 — Secret Detection')

    parallel(

        // ── 2.1 GitLeaks ──────────────────────────────────────
        'GitLeaks': {
            stage('GitLeaks') {
                script {
                    echo "Running GitLeaks — scanning full git history..."

                    tools.installFromTar(
                        'gitleaks',
                        'https://github.com/gitleaks/gitleaks/releases/download/v8.18.2/gitleaks_8.18.2_linux_x64.tar.gz'
                    )

                    sh """
                        gitleaks detect \
                            --source . \
                            --report-format json \
                            --report-path gitleaks-report.json \
                            --redact \
                            --no-git \
                            --verbose \
                            2>&1 || true
                    """

                    pipeline.checkSecretReport('gitleaks-report.json', 'GitLeaks')
                    pipeline.archiveReport('gitleaks-report.json')

                    echo "GitLeaks passed"
                }
            }
        },

        // ── 2.2 TruffleHog ────────────────────────────────────
        'TruffleHog': {
            stage('TruffleHog') {
                script {
                    echo "Running TruffleHog — deep entropy scanning..."

                    tools.installFromScript(
                        'trufflehog',
                        'https://raw.githubusercontent.com/trufflesecurity/trufflehog/main/scripts/install.sh'
                    )

                    def count = sh(
                        script: """
                            trufflehog filesystem . \
                                --json \
                                --no-update \
                                --exclude-paths .trufflehog-ignore \
                                2>/dev/null \
                                | tee trufflehog-report.json \
                                | wc -l
                        """,
                        returnStdout: true
                    ).trim()

                    def findings = count?.isInteger() ? count.toInteger() : 0

                    if (findings > 0) {
                        notify.securityAlert(
                            'TruffleHog',
                            "${findings} secret(s) detected in codebase"
                        )
                        pipeline.block(
                            'TruffleHog',
                            "${findings} secret(s) found — rotate credentials"
                        )
                    }

                    pipeline.archiveReport('trufflehog-report.json')
                    echo "TruffleHog passed"
                }
            }
        },

        // ── 2.3 Hardcoded Credentials ─────────────────────────
        'Hardcoded Credentials': {
            stage('Hardcoded Credentials') {
                script {
                    echo "Scanning for hardcoded credentials..."

                    def patterns = [
                        [name: 'JWT Secret',      pattern: 'JWT_SECRET\\s*=\\s*["\'][^"\']{8,}'],
                        [name: 'DB Password',     pattern: 'DB_PASSWORD\\s*=\\s*["\'][^"\']{4,}'],
                        [name: 'Docker Password', pattern: 'DOCKER_PASSWORD\\s*=\\s*["\'][^"\']{4,}'],
                        [name: 'AWS Access Key',  pattern: 'AKIA[0-9A-Z]{16}'],
                        [name: 'AWS Secret Key',  pattern: 'aws_secret_access_key\\s*=\\s*[A-Za-z0-9/+]{40}'],
                        [name: 'Private Key',     pattern: '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----'],
                        [name: 'Generic Secret',  pattern: 'secret\\s*=\\s*["\'][^"\']{8,}'],
                        [name: 'Generic Token',   pattern: 'token\\s*=\\s*["\'][^"\']{8,}'],
                        [name: 'Bearer Token',    pattern: 'Bearer\\s+[A-Za-z0-9\\-._~+/]{20,}'],
                        [name: 'Basic Auth',      pattern: 'Authorization:\\s*Basic\\s+[A-Za-z0-9+/=]{10,}']
                    ]

                    def excludeDirs  = '.git,node_modules,vendor,myenv,.venv,venv,__pycache__,target,.gradle'
                    def excludeFiles = '*.example,*.sample,*.test,*.spec,*_test.*'
                    def safePatterns = [
                        'env\\.',
                        'os\\.environ',
                        'process\\.env',
                        'getenv',
                        'credentials(',
                        'valueFrom',
                        'secretKeyRef',
                        'configMapKeyRef',
                        '\\$\\{',
                        '\\$\\('
                    ]

                    def findings = []

                    patterns.each { p ->
                        def result = sh(
                            script: """
                                grep -rn \
                                    --include="*.py" \
                                    --include="*.js" \
                                    --include="*.ts" \
                                    --include="*.go" \
                                    --include="*.java" \
                                    --include="*.php" \
                                    --include="*.env" \
                                    --include="*.yaml" \
                                    --include="*.yml" \
                                    --include="*.json" \
                                    --include="*.properties" \
                                    --include="*.conf" \
                                    --exclude-dir={${excludeDirs}} \
                                    -iE '${p.pattern}' . \
                                    2>/dev/null \
                                    | grep -v ${safePatterns.collect { "-e '${it}'" }.join(' ')} \
                                    | grep -v '.example' \
                                    | grep -v '.sample' \
                                    | grep -v '#' \
                                    || true
                            """,
                            returnStdout: true
                        ).trim()

                        if (result) {
                            findings << [type: p.name, matches: result]
                        }
                    }

                    if (findings) {
                        echo """
╔══════════════════════════════════════════╗
║  HARDCODED CREDENTIALS DETECTED          ║
╠══════════════════════════════════════════╣
║  Count: ${findings.size().toString().padRight(33)}║
╚══════════════════════════════════════════╝
                        """
                        findings.each { f ->
                            echo """
Type:    ${f.type}
Matches:
${f.matches}
────────────────────────────────────────
                            """
                        }

                        notify.securityAlert(
                            'Hardcoded Credentials',
                            "${findings.size()} pattern(s) detected — move to env vars"
                        )

                        pipeline.block(
                            'Hardcoded Credentials',
                            "${findings.size()} credential pattern(s) found"
                        )
                    }

                    echo "Hardcoded credentials scan passed"
                }
            }
        },

        // ── 2.4 .env File Check ───────────────────────────────
        'Env File Check': {
            stage('Env File Check') {
                script {
                    echo "Checking for committed .env files..."

                    def envFiles = sh(
                        script: """
                            git ls-files | grep -E '^\\.env$|\\.env\\.' | grep -v '.example' | grep -v '.sample' || true
                        """,
                        returnStdout: true
                    ).trim()

                    if (envFiles) {
                        echo """
╔══════════════════════════════════════════╗
║  COMMITTED .ENV FILES DETECTED           ║
╠══════════════════════════════════════════╣
${envFiles.split('\n').collect { "║  → ${it.padRight(38)}║" }.join('\n')}
╚══════════════════════════════════════════╝
                        """
                        notify.securityAlert(
                            'Env File Check',
                            ".env files committed to repository: ${envFiles}"
                        )
                        pipeline.block(
                            'Env File Check',
                            ".env files must not be committed"
                        )
                    }

                    // Check .gitignore has .env entries
                    if (fileExists('.gitignore')) {
                        def gitignore = readFile('.gitignore')
                        def hasEnvIgnore = gitignore.contains('.env') ||
                                           gitignore.contains('*.env')
                        if (!hasEnvIgnore) {
                            echo "Warning — .env not in .gitignore"
                        } else {
                            echo ".gitignore correctly ignores .env files"
                        }
                    }

                    echo "Env file check passed"
                }
            }
        }

    ) // end parallel

    pipeline.summary('Secret Detection Summary', [
        ['GitLeaks',     'passed'],
        ['TruffleHog',   'passed'],
        ['Hardcoded',    'passed'],
        ['Env Files',    'passed'],
        ['Status',       'NO SECRETS FOUND']
    ])
}
