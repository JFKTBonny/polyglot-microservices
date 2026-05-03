def execute() {
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 9 - Infrastructure Scan')


    // ######################## 9.1 Install Checkov ################################################
    stage('Install Checkov') {
        writeFile file: 'install-checkov.sh', text: '''#!/bin/bash
set -e

if command -v checkov &>/dev/null; then
    echo "Checkov already installed: $(checkov --version)"
    exit 0
fi

echo "Installing Checkov..."
pip install checkov --quiet --break-system-packages 2>&1 | tail -5
echo "Checkov installed: $(checkov --version)"
'''
        sh 'bash install-checkov.sh && rm -f install-checkov.sh'
    }


    // ######################## 9.2 Install kubesec ################################################
    stage('Install kubesec') {
        writeFile file: 'install-kubesec.sh', text: '''#!/bin/bash
set -e

if /tmp/kubesec version &>/dev/null 2>&1; then
    echo "kubesec already installed"
    exit 0
fi

echo "Installing kubesec..."
KUBESEC_VERSION=$(curl -s https://api.github.com/repos/controlplaneio/kubesec/releases/latest \
    | grep '"tag_name"' | sed 's/.*"v\\([^"]*\\)".*/\\1/')

curl -sSL "https://github.com/controlplaneio/kubesec/releases/download/v${KUBESEC_VERSION}/kubesec_linux_amd64.tar.gz" \
    | tar -xz -C /tmp kubesec

echo "kubesec installed: $(/tmp/kubesec version 2>/dev/null || echo v${KUBESEC_VERSION})"
'''
        sh 'bash install-kubesec.sh && rm -f install-kubesec.sh'
    }


    // ######################## 9.3 Checkov — Kubernetes manifests #################################
    stage('Checkov K8s Scan') {
        echo "Running Checkov on Kubernetes manifests..."

        writeFile file: 'checkov-k8s.sh', text: '''#!/bin/bash
set -e

mkdir -p checkov-reports

echo "Scanning k8s/ directory..."

checkov \
    --directory k8s/ \
    --framework kubernetes \
    --output json \
    --output-file-path checkov-reports/ \
    --soft-fail \
    --quiet 2>/dev/null || true

# Also scan with compact summary to stdout
checkov \
    --directory k8s/ \
    --framework kubernetes \
    --compact \
    --soft-fail \
    --quiet 2>/dev/null || true

# Count results
if ls checkov-reports/results_json.json &>/dev/null; then
    PASSED=$(cat checkov-reports/results_json.json | grep -o '"passed"' | wc -l || echo 0)
    FAILED=$(cat checkov-reports/results_json.json | grep -o '"failed"' | wc -l || echo 0)
    echo "CHECKOV_PASSED=$PASSED"
    echo "CHECKOV_FAILED=$FAILED"
else
    echo "CHECKOV_PASSED=0"
    echo "CHECKOV_FAILED=0"
fi
'''
        def out = sh(script: 'bash checkov-k8s.sh', returnStdout: true).trim()
        sh 'rm -f checkov-k8s.sh'

        def passed = 0
        def failed = 0
        def lines = out.split('\n')
        for (int i = 0; i < lines.size(); i++) {
            def line = lines[i].trim()
            if (line.startsWith('CHECKOV_PASSED=')) {
                passed = line.substring('CHECKOV_PASSED='.length()).toInteger()
            }
            if (line.startsWith('CHECKOV_FAILED=')) {
                failed = line.substring('CHECKOV_FAILED='.length()).toInteger()
            }
        }

        echo "Checkov K8s: PASSED=${passed}, FAILED=${failed}"

        if (fileExists('checkov-reports/results_json.json')) {
            stash name: 'checkov-reports', includes: 'checkov-reports/**'
            echo "Checkov reports stashed"
        }
    }

    // ######################## 9.4 Checkov — Dockerfiles ##########################################
    stage('Checkov Dockerfile Scan') {
        echo "Running Checkov on Dockerfiles..."

        def services = [
            'user-service', 'order-service', 'inventory-service',
            'payment-service', 'analytics-service', 'auth-service',
            'notification-service', 'api-gateway', 'ui-service'
        ]

        def results = [:]

        for (int i = 0; i < services.size(); i++) {
            def svc = services[i]

            if (!fileExists("${svc}/Dockerfile")) {
                results[svc] = 'skipped'
                continue
            }

            writeFile file: "checkov-docker-${svc}.sh", text: """#!/bin/bash
checkov \\
    --file ${svc}/Dockerfile \\
    --framework dockerfile \\
    --compact \\
    --soft-fail \\
    --quiet 2>/dev/null || true

echo "DOCKER_SCAN_DONE=1"
"""
            def out = sh(script: "bash checkov-docker-${svc}.sh", returnStdout: true).trim()
            sh "rm -f checkov-docker-${svc}.sh"
            results[svc] = 'scanned'
            echo "${svc} Dockerfile scanned"
        }

        echo "Checkov Dockerfile scan complete"
    }

    // ######################## 9.5 kubesec — Kubernetes manifests #################################
    stage('kubesec Scan') {
        echo "Running kubesec on Kubernetes manifests..."

        writeFile file: 'kubesec-scan.sh', text: '''#!/bin/bash

mkdir -p kubesec-reports

PASS=0
FAIL=0
WARN=0

# Find all deployment/pod/statefulset manifests
find k8s/ -name "*.yaml" -o -name "*.yml" | while read f; do
    # Only scan resource types kubesec supports
    if grep -qE "^kind: (Deployment|Pod|StatefulSet|DaemonSet|Job|CronJob)" "$f" 2>/dev/null; then
        BASENAME=$(basename "$f" .yaml)
        REPORT="kubesec-reports/${BASENAME}.json"

        echo "Scanning $f..."
        /tmp/kubesec scan "$f" > "$REPORT" 2>/dev/null || true

        # Check score
        SCORE=$(cat "$REPORT" | grep -o '"score":[0-9-]*' | head -1 | cut -d: -f2 || echo 0)
        echo "  $f — score: $SCORE"

        if [ "$SCORE" -ge 0 ] 2>/dev/null; then
            echo "KUBESEC_PASS=$f"
        else
            echo "KUBESEC_FAIL=$f"
        fi
    fi
done

echo "kubesec scan complete"
'''
        sh 'bash kubesec-scan.sh && rm -f kubesec-scan.sh'

        if (fileExists('kubesec-reports')) {
            stash name: 'kubesec-reports', includes: 'kubesec-reports/**'
            echo "kubesec reports stashed"
        }

        echo "kubesec scan complete"
    }

    echo "Stage 9 complete - infrastructure scan done"
}

return this