def run() {
    def tools   = load 'jenkins/helpers/tools.groovy'
    def notify  = load 'jenkins/helpers/notify.groovy'
    def helpers = load 'jenkins/helpers/pipeline.groovy'

    parallel(
        'GitLeaks': {
            stage('GitLeaks') {
                tools.install(
                    'gitleaks',
                    'https://github.com/gitleaks/gitleaks/releases/download/v8.18.2/gitleaks_8.18.2_linux_x64.tar.gz'
                )
                sh """
                    gitleaks detect \
                        --source . \
                        --report-format json \
                        --report-path gitleaks-report.json \
                        --redact --no-git 2>&1 || true
                """
                helpers.checkSecretReport('gitleaks-report.json', 'GitLeaks')
                helpers.archiveReport('gitleaks-report.json')
            }
        },
        'TruffleHog': {
            stage('TruffleHog') {
                tools.installScript(
                    'trufflehog',
                    'https://raw.githubusercontent.com/trufflesecurity/trufflehog/main/scripts/install.sh'
                )
                def count = sh(
                    script: """
                        trufflehog filesystem . --json --no-update \
                            2>/dev/null | tee trufflehog-report.json | wc -l
                    """,
                    returnStdout: true
                ).trim().toInteger()
                if (count > 0) helpers.blockPipeline('TruffleHog', "${count} secret(s) found")
                helpers.archiveReport('trufflehog-report.json')
            }
        },
        'Hardcoded Credentials': {
            stage('Hardcoded Credentials') {
                def patterns = [
                    [name: 'JWT Secret',     pattern: 'JWT_SECRET\\s*=\\s*["\'][^"\']{8,}'],
                    [name: 'DB Password',    pattern: 'password\\s*=\\s*["\'][^"\']{4,}'],
                    [name: 'AWS Key',        pattern: 'AKIA[0-9A-Z]{16}'],
                    [name: 'Private Key',    pattern: '-----BEGIN (RSA |EC )?PRIVATE KEY-----'],
                    [name: 'Generic Secret', pattern: 'secret\\s*=\\s*["\'][^"\']{8,}']
                ]
                def excludes = '.git,node_modules,vendor,myenv,.venv,target'
                def findings = []
                patterns.each { p ->
                    def result = sh(
                        script: """
                            grep -rn --exclude-dir={${excludes}} \
                                -iE '${p.pattern}' . 2>/dev/null \
                                | grep -v 'env\\.' \
                                | grep -v 'os.environ' \
                                | grep -v 'process.env' \
                                | grep -v 'getenv' \
                                || true
                        """,
                        returnStdout: true
                    ).trim()
                    if (result) findings << [type: p.name, matches: result]
                }
                if (findings) {
                    findings.each { f -> echo "FOUND — ${f.type}:\n${f.matches}" }
                    helpers.blockPipeline('Credential Scan', 'Hardcoded credentials detected')
                }
                echo "Credential scan passed"
            }
        }
    )
}

