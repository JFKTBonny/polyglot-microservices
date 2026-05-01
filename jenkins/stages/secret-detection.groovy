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

        // Write scan script to file — avoids quote escaping issues
        writeFile file: 'scan-creds.sh', text: '''#!/bin/bash
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
    -iE "JWT_SECRET|DB_PASSWORD|AKIA[0-9A-Z]{16}|BEGIN PRIVATE KEY" . 2>/dev/null \
    | grep -v "os.environ" \
    | grep -v "process.env" \
    | grep -v "getenv" \
    | grep -v "valueFrom" \
    | grep -v "secretKeyRef" \
    | grep -v "credentials(" \
    || true
'''
        def result = sh(
            script: 'bash scan-creds.sh',
            returnStdout: true
        ).trim()

        sh 'rm -f scan-creds.sh'

        if (result) {
            echo "Potential findings:"
            echo "${result}"
            echo "Review findings above — if false positives update exclusions"
        } else {
            echo "Credential scan passed"
        }
    }

    stage('Env File Check') {
        echo "Checking for committed .env files..."

        writeFile file: 'check-env.sh', text: '''#!/bin/bash
git ls-files | grep -E "^\\.env$|\\.env\\." | grep -v example | grep -v sample || true
'''
        def envFiles = sh(
            script: 'bash check-env.sh',
            returnStdout: true
        ).trim()

        sh 'rm -f check-env.sh'

        if (envFiles) {
            echo "Committed .env files: ${envFiles}"
            pipeline.block('Env File Check', '.env files must not be committed')
        } else {
            echo "Env file check passed"
        }
    }

    echo "Secret Detection complete"
}

return this