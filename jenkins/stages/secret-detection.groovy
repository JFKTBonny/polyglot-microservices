def execute() {
    def tools    = load 'jenkins/helpers/tools.groovy'
    def notify   = load 'jenkins/helpers/notify.groovy'
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 2 - Secret Detection')

    parallel(

        'GitLeaks': {
            stage('GitLeaks') {
                script {
                    echo "Running GitLeaks..."

                    tools.installFromTar(
                        'gitleaks',
                        'https://github.com/gitleaks/gitleaks/releases/download/v8.18.2/gitleaks_8.18.2_linux_x64.tar.gz'
                    )

                    sh """
                        gitleaks detect \
                            --source . \
                            --report-format json \
                            --report-path gitleaks-report.json \
                            --redact --no-git \
                            2>&1 || true
                    """

                    pipeline.checkSecretReport('gitleaks-report.json', 'GitLeaks')
                    pipeline.archiveReport('gitleaks-report.json')
                    echo "GitLeaks passed"
                }
            }
        },

        'TruffleHog': {
            stage('TruffleHog') {
                script {
                    echo "Running TruffleHog..."

                    tools.installFromScript(
                        'trufflehog',
                        'https://raw.githubusercontent.com/trufflesecurity/trufflehog/main/scripts/install.sh'
                    )

                    def count = sh(
                        script: """
                            trufflehog filesystem . \
                                --json --no-update \
                                2>/dev/null \
                                | tee trufflehog-report.json \
                                | wc -l
                        """,
                        returnStdout: true
                    ).trim()

                    def findings = count?.isInteger() ? count.toInteger() : 0

                    if (findings > 0) {
                        notify.securityAlert('TruffleHog', "${findings} secret(s) found")
                        pipeline.block('TruffleHog', "${findings} secret(s) found")
                    }

                    pipeline.archiveReport('trufflehog-report.json')
                    echo "TruffleHog passed"
                }
            }
        },

        'Hardcoded Credentials': {
            stage('Hardcoded Credentials') {
                script {
                    echo "Scanning for hardcoded credentials..."

                    def patterns = [
                        [name: 'JWT Secret',     pattern: 'JWT_SECRET\\s*=\\s*["\'][^"\']{8,}'],
                        [name: 'DB Password',    pattern: 'DB_PASSWORD\\s*=\\s*["\'][^"\']{4,}'],
                        [name: 'AWS Key',        pattern: 'AKIA[0-9A-Z]{16}'],
                        [name: 'Private Key',    pattern: '-----BEGIN (RSA |EC )?PRIVATE KEY-----'],
                        [name: 'Generic Secret', pattern: 'secret\\s*=\\s*["\'][^"\']{8,}'],
                        [name: 'Generic Token',  pattern: 'token\\s*=\\s*["\'][^"\']{8,}']
                    ]

                    def excludes = '.git,node_modules,vendor,myenv,.venv,venv,__pycache__,target'
                    def findings = []

                    patterns.each { p ->
                        def result = sh(
                            script: """
                                grep -rn \
                                    --include="*.py" --include="*.js" \
                                    --include="*.go" --include="*.java" \
                                    --include="*.php" --include="*.env" \
                                    // --include="*.yaml" --include="*.yml" \
                                    --exclude-dir={${excludes}} \
                                    -iE '${p.pattern}' . 2>/dev/null \
                                    | grep -v 'env\\.' \
                                    | grep -v 'os.environ' \
                                    | grep -v 'process.env' \
                                    | grep -v 'getenv' \
                                    | grep -v 'credentials(' \
                                    | grep -v 'valueFrom' \
                                    || true
                            """,
                            returnStdout: true
                        ).trim()

                        if (result) findings << [type: p.name, matches: result]
                    }

                    if (findings) {
                        findings.each { f ->
                            echo "FOUND - ${f.type}:"
                            echo "${f.matches}"
                        }
                        notify.securityAlert(
                            'Hardcoded Credentials',
                            "${findings.size()} pattern(s) found"
                        )
                        pipeline.block(
                            'Hardcoded Credentials',
                            "${findings.size()} credential pattern(s) found"
                        )
                    }

                    echo "Credential scan passed"
                }
            }
        },

        'Env File Check': {
            stage('Env File Check') {
                script {
                    echo "Checking for committed .env files..."

                    def envFiles = sh(
                        script: '''
                            git ls-files | grep -E '^\\.env$|\\.env\\.' \
                                | grep -v '.example' \
                                | grep -v '.sample' \
                                || true
                        ''',
                        returnStdout: true
                    ).trim()

                    if (envFiles) {
                        echo "Committed .env files found:"
                        echo "${envFiles}"
                        pipeline.block('Env File Check', ".env files must not be committed")
                    }

                    echo "Env file check passed"
                }
            }
        }

    ) // end parallel

    echo "Secret Detection complete - no secrets found"
}


return this
