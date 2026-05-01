def execute() {
    def tools    = load 'jenkins/helpers/tools.groovy'
    def notify   = load 'jenkins/helpers/notify.groovy'
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 2 - Secret Detection')

    parallel(
        'GitLeaks': {
            stage('GitLeaks') {
                steps {
                    script {
                        echo "Running GitLeaks..."
                        tools.installFromTar(
                            'gitleaks',
                            'https://github.com/gitleaks/gitleaks/releases/download/v8.18.4/gitleaks_8.18.4_linux_x64.tar.gz'
                        )
                        sh """
                            ./gitleaks detect \\
                                --source . \\
                                --report-format json \\
                                --report-path gitleaks-report.json \\
                                --redact --git-history \\
                                2>&1 || true
                        """
                        pipeline.checkSecretReport('gitleaks-report.json', 'GitLeaks')
                        pipeline.archiveReport('gitleaks-report.json')
                        echo "GitLeaks passed"
                    }
                }
            }
        },

        'TruffleHog': {
            stage('TruffleHog') {
                steps {
                    script {
                        echo "Running TruffleHog..."
                        tools.installFromScript(
                            'trufflehog',
                            'https://raw.githubusercontent.com/trufflesecurity/trufflehog/main/scripts/install.sh'
                        )
                        sh """
                            trufflehog filesystem . \\
                                --json --no-update \\
                                2>/dev/null \\
                                | tee trufflehog-report.json
                        """
                        def findings = sh(
                            script: "jq '[.[] | select(.Verified == true)] | length' trufflehog-report.json 2>/dev/null || echo 0",
                            returnStdout: true
                        ).trim().toInteger()

                        if (findings > 0) {
                            notify.securityAlert('TruffleHog', "${findings} verified secret(s) found")
                            pipeline.block('TruffleHog', "${findings} verified secret(s) found")
                        }
                        pipeline.archiveReport('trufflehog-report.json')
                        echo "TruffleHog passed"
                    }
                }
            }
        },

        'Hardcoded Credentials': {
            stage('Hardcoded Credentials') {
                steps {
                    script {
                        echo "Scanning for hardcoded credentials..."
                        def excludes = '.git,node_modules,vendor,.venv,venv,__pycache__,target,charts,terraform/.terraform,ansible/.vault'
                        def findings = []

                        // Define full grep commands with pre-escaped patterns (avoids interpolation issues)
                        def grepCommands = [
                            'JWT_SECRET\\s*=\\s*["\'][^"\']{8,}',
                            'DB_PASSWORD\\s*=\\s*["\'][^"\']{4,}',
                            'AKIA[0-9A-Z]{16}',
                            '-----BEGIN (RSA |EC )?PRIVATE KEY-----',
                            'VAULT_TOKEN\\s*=\\s*["\'][^"\']{8,}',  // Vault
                            'secret\\s*=\\s*["\'][^"\']{8,}',
                            'token\\s*=\\s*["\'][^"\']{8,}',
                            '(ELK|KIBANA|LOGSTASH)_PASSWORD\\s*=\\s*["\'][^"\']{4,}'  // ELK
                        ]
                        def names = ['JWT Secret', 'DB Password', 'AWS Key', 'Private Key', 'Vault Token', 'Generic Secret', 'Generic Token', 'ELK Password']

                        grepCommands.eachWithIndex { pattern, i ->
                            def name = names[i]
                            def result = sh(
                                script: """
                                    grep -rn \\
                                        --include="*.py" --include="*.js" --include="*.go" --include="*.java" \\
                                        --include="*.php" --include="*.yaml" --include="*.yml" --include="*.env" --include="*.tf" \\
                                        --exclude-dir={${excludes}} \\
                                        -iE '${pattern}' . 2>/dev/null \\
                                    | grep -v -E 'env\\\\.|os.environ|process.env|getenv|credentials\\\\(|valueFrom|vault.read|ansible-vault' \\
                                    || true
                                """,
                                returnStdout: true
                            ).trim()

                            if (result) findings << [type: name, matches: result]
                        }

                        if (findings) {
                            findings.each { f ->
                                echo "FOUND - ${f.type}:"
                                echo "${f.matches}"
                            }
                            notify.securityAlert('Hardcoded Credentials', "${findings.size()} pattern(s) found")
                            pipeline.block('Hardcoded Credentials', "${findings.size()} credential pattern(s) found")
                        }
                        echo "Credential scan passed"
                    }
                }
            }
        },

        'Env File Check': {
            stage('Env File Check') {
                steps {
                    script {
                        echo "Checking for committed .env files..."
                        def envFiles = sh(
                            script: '''
                                git ls-files | grep -E '^\\.env$|\\.env\\.' \
                                    | grep -v -E '(\\.example|\\.sample|\\.template)' \
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
        }
    )

    echo "Secret Detection complete - no secrets found"
}

return this