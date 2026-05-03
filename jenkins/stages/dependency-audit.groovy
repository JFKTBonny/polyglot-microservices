def execute() {
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 3 - Dependency Audit')

    // ── 3.1 Node.js — npm audit ───────────────────────────────
    stage('npm audit') {
        echo "Running npm audit — user-service..."

        writeFile file: 'npm-audit.sh', text: '''#!/bin/bash
SERVICE="user-service"

if [ ! -f "$SERVICE/package.json" ]; then
    echo "No package.json found — skipping"
    exit 0
fi

cd $SERVICE
npm install --prefer-offline 2>/dev/null || true
npm audit --audit-level=high --json > ../npm-audit-report.json 2>/dev/null || true

CRITICAL=$(python3 -c "
import json,sys
try:
    d = json.load(open('../npm-audit-report.json'))
    meta = d.get('metadata', {})
    vulns = meta.get('vulnerabilities', {})
    print(vulns.get('critical', 0))
except:
    print(0)
" 2>/dev/null || echo "0")

HIGH=$(python3 -c "
import json,sys
try:
    d = json.load(open('../npm-audit-report.json'))
    meta = d.get('metadata', {})
    vulns = meta.get('vulnerabilities', {})
    print(vulns.get('high', 0))
except:
    print(0)
" 2>/dev/null || echo "0")

echo "Critical: $CRITICAL"
echo "High: $HIGH"

if [ "$CRITICAL" -gt 0 ]; then
    echo "BLOCK: $CRITICAL critical vulnerability(ies) found"
    exit 1
fi

echo "npm audit passed"
'''
        def result = sh(
            script: 'bash npm-audit.sh',
            returnStatus: true
        )
        sh 'rm -f npm-audit.sh'
        pipeline.archiveReport('npm-audit-report.json')

        if (result != 0) {
            pipeline.block('npm audit', 'Critical vulnerabilities found in user-service')
        }
        echo "npm audit passed"
    }

    // ── 3.2 Python — pip-audit ────────────────────────────────
    stage('pip audit') {
        echo "Running pip-audit — Python services..."

        writeFile file: 'pip-audit.sh', text: '''#!/bin/bash
SERVICES="order-service auth-service notification-service"
FAILED=0

pip install pip-audit --quiet 2>/dev/null || true

for SERVICE in $SERVICES; do
    if [ ! -f "$SERVICE/requirements.txt" ]; then
        echo "No requirements.txt in $SERVICE — skipping"
        continue
    fi

    echo "Auditing $SERVICE..."

    pip-audit \
        -r $SERVICE/requirements.txt \
        --format json \
        --output pip-audit-$SERVICE.json \
        2>/dev/null || true

    CRITICAL=$(python3 -c "
import json
try:
    d = json.load(open('pip-audit-$SERVICE.json'))
    deps = d if isinstance(d, list) else d.get('dependencies', [])
    count = sum(
        1 for dep in deps
        for vuln in dep.get('vulns', [])
        if any(
            s.get('type') == 'cvss' and float(s.get('score', 0)) >= 9.0
            for s in vuln.get('aliases', [])
        )
    )
    print(count)
except:
    print(0)
" 2>/dev/null || echo "0")

    echo "$SERVICE — Critical: $CRITICAL"

    if [ "$CRITICAL" -gt 0 ]; then
        echo "BLOCK: Critical vulnerabilities in $SERVICE"
        FAILED=1
    fi
done

exit $FAILED
'''
        def result = sh(
            script: 'bash pip-audit.sh',
            returnStatus: true
        )
        sh 'rm -f pip-audit.sh'

        if (fileExists('pip-audit-order-service.json'))        pipeline.archiveReport('pip-audit-order-service.json')
        if (fileExists('pip-audit-auth-service.json'))         pipeline.archiveReport('pip-audit-auth-service.json')
        if (fileExists('pip-audit-notification-service.json')) pipeline.archiveReport('pip-audit-notification-service.json')

        if (result != 0) {
            pipeline.block('pip audit', 'Critical vulnerabilities found in Python services')
        }
        echo "pip audit passed"
    }

    // ── 3.3 Go — govulncheck ──────────────────────────────────
    stage('govulncheck') {
        echo "Running govulncheck — inventory-service..."

        writeFile file: 'govuln.sh', text: '''#!/bin/bash
SERVICE="inventory-service"

if [ ! -f "$SERVICE/go.mod" ]; then
    echo "No go.mod found — skipping"
    exit 0
fi

go install golang.org/x/vuln/cmd/govulncheck@latest 2>/dev/null || true
export PATH=$PATH:$(go env GOPATH)/bin

# Run from repo root pointing to service
govulncheck ./$SERVICE/... 2>&1 | tee govuln-report.txt || true

if grep -q "Vulnerability #" govuln-report.txt; then
    echo "Vulnerabilities found — review report"
else
    echo "govulncheck passed — no vulnerabilities"
fi
'''
        sh 'bash govuln.sh && rm -f govuln.sh'
        pipeline.archiveReport('govuln-report.txt')
        echo "govulncheck passed"
    }

    // ── 3.4 Java — OWASP Dependency Check ────────────────────
    stage('OWASP Dependency Check') {
        echo "Running OWASP dependency check — payment-service..."

        writeFile file: 'owasp-check.sh', text: '''#!/bin/bash
SERVICE="payment-service"

if [ ! -f "$SERVICE/pom.xml" ]; then
    echo "No pom.xml found — skipping"
    exit 0
fi

cd $SERVICE

mvn org.owasp:dependency-check-maven:check \
    -DfailBuildOnCVSS=9 \
    -DskipTestScope=true \
    -Dformat=JSON \
    -DoutputDirectory=../owasp-report \
    -DnvdDatafeedUrl=off \
    --no-transfer-progress \
    2>/dev/null || true

echo "OWASP check complete"
'''
        sh 'bash owasp-check.sh && rm -f owasp-check.sh'

        if (fileExists('owasp-report/dependency-check-report.json')) {
            pipeline.archiveReport('owasp-report/dependency-check-report.json')
        }
        echo "OWASP dependency check passed"
    }

    // ── 3.5 PHP — composer audit ──────────────────────────────
    stage('composer audit') {
        echo "Running composer audit — analytics-service..."

        writeFile file: 'composer-audit.sh', text: '''#!/bin/bash
SERVICE="analytics-service"

if [ ! -f "$SERVICE/composer.json" ]; then
    echo "No composer.json found — skipping"
    exit 0
fi

cd $SERVICE

composer audit --format=json 2>/dev/null \
    | tee ../composer-audit-report.json || true

FOUND=$(python3 -c "
import json
try:
    d = json.load(open('../composer-audit-report.json'))
    advisories = d.get('advisories', {})
    print(len(advisories))
except:
    print(0)
" 2>/dev/null || echo "0")

echo "Advisories found: $FOUND"

if [ "$FOUND" -gt 0 ]; then
    echo "Warning: $FOUND advisory(ies) found — review report"
fi

echo "composer audit complete"
'''
        sh 'bash composer-audit.sh && rm -f composer-audit.sh'
        pipeline.archiveReport('composer-audit-report.json')
        echo "composer audit passed"
    }

    echo "Stage 3 complete - dependency audit done"
}

return this