def execute() {
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 2 - Secret Detection')

    stage('GitLeaks') {
        echo "Skipping GitLeaks for debug"
    }

    stage('TruffleHog') {
        echo "Skipping TruffleHog for debug"
    }

    stage('Hardcoded Credentials') {
        echo "Scanning for hardcoded credentials..."
        def result = sh(
            script: '''
                grep -rn \
                    --include="*.py" \
                    --include="*.js" \
                    --include="*.go" \
                    --include="*.java" \
                    --include="*.php" \
                    --exclude-dir=.git \
                    --exclude-dir=node_modules \
                    --exclude-dir=vendor \
                    --exclude-dir=myenv \
                    --exclude-dir=target \
                    -iE 'secret\s*=\s*["'"'"'][^"'"'"']{8,}' . 2>/dev/null \
                    | grep -v 'os.environ' \
                    | grep -v 'process.env' \
                    | grep -v 'getenv' \
                    || true
            ''',
            returnStdout: true
        ).trim()

        if (result) {
            echo "Found: ${result}"
        } else {
            echo "Credential scan passed"
        }
    }

    stage('Env File Check') {
        echo "Checking .env files..."
        def envFiles = sh(
            script: '''
                git ls-files | grep -E '^\\.env$' | grep -v example || true
            ''',
            returnStdout: true
        ).trim()

        if (envFiles) {
            echo "Found .env: ${envFiles}"
        } else {
            echo "Env file check passed"
        }
    }

    echo "Secret Detection complete"
}

return this