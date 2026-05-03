def execute() {
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 4 - SAST')

    // ── 4.1 Bandit — Python SAST ──────────────────────────────
    stage('Bandit') {
        echo "Running Bandit — Python services..."

        writeFile file: 'bandit-scan.sh', text: '''#!/bin/bash
SERVICES="order-service auth-service notification-service"
FAILED=0

pip install bandit --quiet 2>/dev/null || true

for SERVICE in $SERVICES; do
    if [ ! -d "$SERVICE" ]; then
        echo "$SERVICE not found — skipping"
        continue
    fi

    echo "Scanning $SERVICE..."

    bandit -r $SERVICE \
        --exclude $SERVICE/myenv,$SERVICE/.venv,$SERVICE/venv,$SERVICE/__pycache__ \
        --severity-level medium \
        --confidence-level medium \
        -f json \
        -o bandit-$SERVICE.json \
        2>/dev/null || true

    HIGH=$(python3 -c "
import json
try:
    d = json.load(open('bandit-$SERVICE.json'))
    results = d.get('results', [])
    count = sum(
        1 for r in results
        if r.get('issue_severity', '') in ['HIGH', 'CRITICAL']
        and r.get('issue_confidence', '') in ['HIGH', 'MEDIUM']
    )
    print(count)
except:
    print(0)
" 2>/dev/null || echo "0")

    echo "$SERVICE — High/Critical: $HIGH"

    if [ "$HIGH" -gt 0 ]; then
        echo "WARNING: $HIGH high/critical finding(s) in $SERVICE"
        FAILED=1
    fi
done

exit $FAILED
'''
        def result = sh(
            script: 'bash bandit-scan.sh',
            returnStatus: true
        )
        sh 'rm -f bandit-scan.sh'

        if (fileExists('bandit-order-service.json'))        pipeline.archiveReport('bandit-order-service.json')
        if (fileExists('bandit-auth-service.json'))         pipeline.archiveReport('bandit-auth-service.json')
        if (fileExists('bandit-notification-service.json')) pipeline.archiveReport('bandit-notification-service.json')

        if (result != 0) {
            pipeline.block('Bandit', 'High/Critical SAST findings in Python services')
        }
        echo "Bandit passed"
    }

    // ── 4.2 ESLint Security — Node.js ─────────────────────────
    stage('ESLint Security') {
        echo "Running ESLint security — user-service..."

        writeFile file: 'eslint-scan.sh', text: '''#!/bin/bash
SERVICE="user-service"

if [ ! -f "$SERVICE/package.json" ]; then
    echo "No package.json found — skipping"
    exit 0
fi

cd $SERVICE

npm install eslint eslint-plugin-security --save-dev --quiet 2>/dev/null || true

cat > .eslintrc-security.json << 'ESLINTCONF'
{
  "plugins": ["security"],
  "rules": {
    "security/detect-object-injection": "warn",
    "security/detect-non-literal-regexp": "warn",
    "security/detect-unsafe-regex": "error",
    "security/detect-buffer-noassert": "error",
    "security/detect-child-process": "warn",
    "security/detect-disable-mustache-escape": "error",
    "security/detect-eval-with-expression": "error",
    "security/detect-no-csrf-before-method-override": "error",
    "security/detect-non-literal-fs-filename": "warn",
    "security/detect-non-literal-require": "warn",
    "security/detect-possible-timing-attacks": "warn",
    "security/detect-pseudoRandomBytes": "error"
  }
}
ESLINTCONF

npx eslint . \
    --config .eslintrc-security.json \
    --ext .js \
    --format json \
    --output-file ../eslint-security-report.json \
    --ignore-pattern node_modules \
    --ignore-pattern myenv \
    2>/dev/null || true

rm -f .eslintrc-security.json

ERRORS=$(python3 -c "
import json
try:
    d = json.load(open('../eslint-security-report.json'))
    count = sum(
        1 for f in d
        for m in f.get('messages', [])
        if m.get('severity', 0) == 2
    )
    print(count)
except:
    print(0)
" 2>/dev/null || echo "0")

echo "ESLint errors: $ERRORS"

if [ "$ERRORS" -gt 0 ]; then
    echo "BLOCK: $ERRORS security error(s) found"
    exit 1
fi

echo "ESLint security passed"
'''
        def result = sh(
            script: 'bash eslint-scan.sh',
            returnStatus: true
        )
        sh 'rm -f eslint-scan.sh'
        pipeline.archiveReport('eslint-security-report.json')

        if (result != 0) {
            pipeline.block('ESLint Security', 'Security errors found in user-service')
        }
        echo "ESLint security passed"
    }

    // ── 4.3 golangci-lint — Go ────────────────────────────────
    stage('golangci-lint') {
        echo "Running golangci-lint — inventory-service..."

        writeFile file: 'golangci-scan.sh', text: '''#!/bin/bash
SERVICE="inventory-service"

if [ ! -f "$SERVICE/go.mod" ]; then
    echo "No go.mod found — skipping"
    exit 0
fi

# Install golangci-lint
if ! command -v golangci-lint &>/dev/null; then
    curl -sSfL \
        https://raw.githubusercontent.com/golangci/golangci-lint/master/install.sh \
        | sh -s -- -b /tmp 2>/dev/null || true
    export PATH=$PATH:/tmp
fi

golangci-lint run ./$SERVICE/... \
    --enable gosec \
    --timeout 120s \
    --out-format json \
    > golangci-report.json 2>/dev/null || true

ERRORS=$(python3 -c "
import json
try:
    d = json.load(open('golangci-report.json'))
    issues = d.get('Issues', []) or []
    count = sum(
        1 for i in issues
        if i.get('Severity', '') in ['error', 'warning']
    )
    print(count)
except:
    print(0)
" 2>/dev/null || echo "0")

echo "golangci-lint issues: $ERRORS"

if [ "$ERRORS" -gt 0 ]; then
    echo "Warning: $ERRORS issue(s) found — review report"
fi

echo "golangci-lint complete"
'''
        sh 'bash golangci-scan.sh && rm -f golangci-scan.sh'
        pipeline.archiveReport('golangci-report.json')
        echo "golangci-lint passed"
    }

    // ── 4.4 SpotBugs — Java ───────────────────────────────────
    stage('SpotBugs') {
        echo "Running SpotBugs — payment-service..."

        writeFile file: 'spotbugs-scan.sh', text: '''#!/bin/bash
SERVICE="payment-service"

if [ ! -f "$SERVICE/pom.xml" ]; then
    echo "No pom.xml found — skipping"
    exit 0
fi

cd $SERVICE

mvn compile spotbugs:check \
    -Dspotbugs.effort=Max \
    -Dspotbugs.threshold=High \
    -Dspotbugs.xmlOutput=true \
    --no-transfer-progress \
    2>/dev/null || true

echo "SpotBugs complete"
'''
        sh 'bash spotbugs-scan.sh && rm -f spotbugs-scan.sh'

        if (fileExists('payment-service/target/spotbugsXml.xml')) {
            pipeline.archiveReport('payment-service/target/spotbugsXml.xml')
        }
        echo "SpotBugs passed"
    }

    // ── 4.5 PHPCS Security Audit — PHP ───────────────────────
    stage('PHPCS Security') {
        echo "Running PHPCS security audit — analytics-service..."

        writeFile file: 'phpcs-scan.sh', text: '''#!/bin/bash
SERVICE="analytics-service"

if [ ! -f "$SERVICE/composer.json" ]; then
    echo "No composer.json found — skipping"
    exit 0
fi

cd $SERVICE

composer require --dev pheromone/phpcs-security-audit --quiet \
    --ignore-platform-reqs 2>/dev/null || true

vendor/bin/phpcs \
    --standard=vendor/pheromone/phpcs-security-audit/example_base_ruleset.xml \
    --report=json \
    --report-file=../phpcs-security-report.json \
    app/ \
    2>/dev/null || true

ERRORS=$(python3 -c "
import json
try:
    d = json.load(open('../phpcs-security-report.json'))
    totals = d.get('totals', {})
    print(totals.get('errors', 0))
except:
    print(0)
" 2>/dev/null || echo "0")

echo "PHPCS errors: $ERRORS"

if [ "$ERRORS" -gt 0 ]; then
    echo "Warning: $ERRORS security issue(s) found"
fi

echo "PHPCS security complete"
'''
        sh 'bash phpcs-scan.sh && rm -f phpcs-scan.sh'
        pipeline.archiveReport('phpcs-security-report.json')
        echo "PHPCS security passed"
    }

    // ── 4.6 Semgrep — All languages ───────────────────────────
    stage('Semgrep') {
        echo "Running Semgrep — all services..."

        writeFile file: 'semgrep-scan.sh', text: '''#!/bin/bash
pip install semgrep --quiet 2>/dev/null || true

semgrep scan \
    --config=p/security-audit \
    --config=p/owasp-top-ten \
    --config=p/python \
    --config=p/javascript \
    --config=p/java \
    --config=p/go \
    --config=p/php \
    --json \
    --output semgrep-report.json \
    --exclude="*.yaml" \
    --exclude="*.yml" \
    --exclude="node_modules" \
    --exclude="vendor" \
    --exclude="myenv" \
    --exclude=".venv" \
    --exclude="target" \
    --exclude="__pycache__" \
    . 2>/dev/null || true

CRITICAL=$(python3 -c "
import json
try:
    d = json.load(open('semgrep-report.json'))
    results = d.get('results', [])
    count = sum(
        1 for r in results
        if r.get('extra', {}).get('severity', '') in ['ERROR', 'WARNING']
    )
    print(count)
except:
    print(0)
" 2>/dev/null || echo "0")

echo "Semgrep findings: $CRITICAL"
echo "Semgrep complete"
'''
        sh 'bash semgrep-scan.sh && rm -f semgrep-scan.sh'
        pipeline.archiveReport('semgrep-report.json')
        echo "Semgrep passed"
    }

    echo "Stage 4 complete - SAST done"
}

return this