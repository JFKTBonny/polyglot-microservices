def execute() {
    def pipeline = load 'jenkins/helpers/pipeline.groovy'
    def notify = load 'jenkins/helpers/notify.groovy'
    def state = load 'jenkins/helpers/state.groovy'

    pipeline.banner('Stage 2 - Secret Detection')
    

// ── 2.1 GitLeaks ──────────────────────────────────────────
stage('GitLeaks') {
    echo "Running GitLeaks..."

    writeFile file: 'install-gitleaks.sh', text: '''#!/bin/bash
set -e

if ! command -v gitleaks &>/dev/null; then
    echo "Installing gitleaks..."

    TMP_DIR=$(mktemp -d)
    cd $TMP_DIR

    curl -sSfL https://github.com/gitleaks/gitleaks/releases/download/v8.18.2/gitleaks_8.18.2_linux_x64.tar.gz \
        | tar -xz

    chmod +x gitleaks

    # install locally first (safe for Jenkins agents)
    mkdir -p $HOME/bin
    mv gitleaks $HOME/bin/gitleaks

    export PATH="$HOME/bin:$PATH"

    echo "Installed gitleaks to $HOME/bin/gitleaks"
fi

gitleaks version
echo "gitleaks ready"
'''
    sh 'bash install-gitleaks.sh && rm -f install-gitleaks.sh'

    writeFile file: 'run-gitleaks.sh', text: '''#!/bin/bash
set -e

gitleaks detect \
    --source . \
    --report-format json \
    --report-path gitleaks-report.json \
    --redact \
    || true
'''
    sh 'bash run-gitleaks.sh && rm -f run-gitleaks.sh'

    pipeline.checkSecretReport('gitleaks-report.json', 'GitLeaks')
    pipeline.archiveReport('gitleaks-report.json')

    echo "GitLeaks passed"
}



    // ── 2.2 TruffleHog ────────────────────────────────────────
    stage('TruffleHog') {
        echo "Running TruffleHog..."

        writeFile file: 'install-trufflehog.sh', text: '''#!/bin/bash
if ! command -v trufflehog &>/dev/null; then
    echo "Installing trufflehog..."
    curl -sSfL https://raw.githubusercontent.com/trufflesecurity/trufflehog/main/scripts/install.sh \
        | sh -s -- -b /tmp 2>/dev/null || true
    sudo mv /tmp/trufflehog /usr/local/bin/trufflehog 2>/dev/null || \
    mv /tmp/trufflehog ./trufflehog 2>/dev/null || true
fi
echo "trufflehog ready"
'''
        sh 'bash install-trufflehog.sh && rm -f install-trufflehog.sh'

        writeFile file: 'run-trufflehog.sh', text: '''#!/bin/bash
trufflehog filesystem . \
    --json \
    --no-update \
    2>/dev/null \
    | tee trufflehog-report.json \
    | wc -l
'''
        def count = sh(
            script: 'bash run-trufflehog.sh && rm -f run-trufflehog.sh',
            returnStdout: true
        ).trim()

        def findings = count?.isInteger() ? count.toInteger() : 0

        if (findings > 0) {
            echo "TruffleHog found ${findings} secret(s)"
            pipeline.archiveReport('trufflehog-report.json')
            pipeline.block('TruffleHog', "${findings} secret(s) found — rotate credentials")
        }

        pipeline.archiveReport('trufflehog-report.json')
        echo "TruffleHog passed"
    }

    // ── 2.3 Hardcoded Credentials ─────────────────────────────
    stage('Hardcoded Credentials') {
        echo "Scanning for hardcoded credentials..."

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
    --exclude-dir=__pycache__ \
    -iE "AKIA[0-9A-Z]{16}|BEGIN PRIVATE KEY|BEGIN RSA PRIVATE KEY|BEGIN EC PRIVATE KEY" \
    . 2>/dev/null \
    | grep -v "example" \
    | grep -v "sample" \
    | grep -v "test" \
    || true
'''
        def result = sh(
            script: 'bash scan-creds.sh && rm -f scan-creds.sh',
            returnStdout: true
        ).trim()

        if (result) {
            echo "CRITICAL findings found:"
            echo "${result}"
            pipeline.block('Hardcoded Credentials', 'Critical credentials found in code')
        } else {
            echo "Credential scan passed"
        }
    }

    // ── 2.4 Env File Check ────────────────────────────────────
    stage('Env File Check') {
        echo "Checking for committed .env files..."

        writeFile file: 'check-env.sh', text: '''#!/bin/bash
git ls-files | grep -E "^\\.env$|\\.env\\." \
    | grep -v "example" \
    | grep -v "sample" \
    || true
'''
        def envFiles = sh(
            script: 'bash check-env.sh && rm -f check-env.sh',
            returnStdout: true
        ).trim()

        if (envFiles) {
            echo "Committed .env files detected:"
            echo "${envFiles}"
            pipeline.block('Env File Check', '.env files must not be committed')
        } else {
            echo "Env file check passed"
        }
    }

    echo "Stage 2 complete - no secrets found"
}

return this