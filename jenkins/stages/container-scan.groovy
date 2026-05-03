def execute() {
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 8 - Container Scan')

    def services = [
        'user-service',
        'order-service',
        'inventory-service',
        'payment-service',
        'analytics-service',
        'auth-service',
        'notification-service',
        'api-gateway',
        'ui-service'
    ]

    def branch      = env.DETECTED_BRANCH ?: 'unknown'
    def shortCommit = env.SHORT_COMMIT    ?: 'latest'
    def buildTag    = "${branch}-${shortCommit}".replaceAll('/', '-')

    // ── 8.1 Install Trivy ─────────────────────────────────────
    stage('Install Trivy') {
        writeFile file: 'install-trivy.sh', text: '''#!/bin/bash
set -e

if command -v trivy &>/dev/null; then
    echo "Trivy already installed: $(trivy --version | head -1)"
    exit 0
fi

echo "Installing Trivy..."
TRIVY_VERSION=$(curl -s https://api.github.com/repos/aquasecurity/trivy/releases/latest \
    | grep '"tag_name"' | sed 's/.*"v\\([^"]*\\)".*/\\1/')

curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh \
    | sh -s -- -b /usr/local/bin v${TRIVY_VERSION}

echo "Trivy installed: $(trivy --version | head -1)"
'''
        sh 'bash install-trivy.sh && rm -f install-trivy.sh'
    }

    // ── 8.2 Install Grype ─────────────────────────────────────
    stage('Install Grype') {
        writeFile file: 'install-grype.sh', text: '''#!/bin/bash
set -e

if command -v grype &>/dev/null; then
    echo "Grype already installed: $(grype version | head -1)"
    exit 0
fi

echo "Installing Grype..."
curl -sSfL https://raw.githubusercontent.com/anchore/grype/main/install.sh \
    | sh -s -- -b /usr/local/bin

echo "Grype installed: $(grype version | head -1)"
'''
        sh 'bash install-grype.sh && rm -f install-grype.sh'
    }

    // ── 8.3 Scan with Trivy ───────────────────────────────────
    stage('Trivy Scan') {
        echo "Running Trivy vulnerability scans..."

        def trivyResults = [:]
        mkdir 'trivy-reports'

        for (int i = 0; i < services.size(); i++) {
            def svc = services[i]
            def image = "santonix/${svc}:${buildTag}"

            writeFile file: "trivy-scan-${svc}.sh", text: """#!/bin/bash
IMAGE="${image}"
REPORT="trivy-reports/${svc}.json"

echo "Scanning \$IMAGE with Trivy..."

trivy image \\
    --format json \\
    --output "\$REPORT" \\
    --severity HIGH,CRITICAL \\
    --no-progress \\
    --ignore-unfixed \\
    "\$IMAGE" 2>&1 || true

# Count findings
if [ -f "\$REPORT" ]; then
    CRITICAL=\$(cat "\$REPORT" | grep -o '"Severity":"CRITICAL"' | wc -l || echo 0)
    HIGH=\$(cat "\$REPORT" | grep -o '"Severity":"HIGH"' | wc -l || echo 0)
    echo "TRIVY_CRITICAL=\$CRITICAL"
    echo "TRIVY_HIGH=\$HIGH"
else
    echo "TRIVY_CRITICAL=0"
    echo "TRIVY_HIGH=0"
fi
"""
            def out = sh(script: "bash trivy-scan-${svc}.sh", returnStdout: true).trim()
            sh "rm -f trivy-scan-${svc}.sh"

            def critical = 0
            def high = 0
            def lines = out.split('\n')
            for (int j = 0; j < lines.size(); j++) {
                def line = lines[j].trim()
                if (line.startsWith('TRIVY_CRITICAL=')) {
                    critical = line.substring('TRIVY_CRITICAL='.length()).toInteger()
                }
                if (line.startsWith('TRIVY_HIGH=')) {
                    high = line.substring('TRIVY_HIGH='.length()).toInteger()
                }
            }

            trivyResults[svc] = [critical: critical, high: high]
            echo "${svc} — CRITICAL: ${critical}, HIGH: ${high}"
        }

        // Summary
        echo "Trivy Scan Summary:"
        def criticalServices = []
        for (int i = 0; i < services.size(); i++) {
            def svc = services[i]
            def r = trivyResults[svc]
            echo "  ${svc}: CRITICAL=${r.critical}, HIGH=${r.high}"
            if (r.critical > 0) criticalServices << svc
        }

        if (criticalServices.size() > 0) {
            echo "WARNING: Critical vulnerabilities found in: ${criticalServices.join(', ')}"
            // Non-blocking — record but continue
        }

        stash name: 'trivy-reports', includes: 'trivy-reports/**'
    }

    // ── 8.4 Scan with Grype ───────────────────────────────────
    stage('Grype Scan') {
        echo "Running Grype vulnerability scans..."

        def grypeResults = [:]
        mkdir 'grype-reports'

        for (int i = 0; i < services.size(); i++) {
            def svc = services[i]
            def image = "santonix/${svc}:${buildTag}"

            writeFile file: "grype-scan-${svc}.sh", text: """#!/bin/bash
IMAGE="${image}"
REPORT="grype-reports/${svc}.json"

echo "Scanning \$IMAGE with Grype..."

grype "\$IMAGE" \\
    --output json \\
    --file "\$REPORT" \\
    --only-fixed \\
    -q 2>&1 || true

# Count findings
if [ -f "\$REPORT" ]; then
    CRITICAL=\$(cat "\$REPORT" | grep -o '"severity":"Critical"' | wc -l || echo 0)
    HIGH=\$(cat "\$REPORT" | grep -o '"severity":"High"' | wc -l || echo 0)
    echo "GRYPE_CRITICAL=\$CRITICAL"
    echo "GRYPE_HIGH=\$HIGH"
else
    echo "GRYPE_CRITICAL=0"
    echo "GRYPE_HIGH=0"
fi
"""
            def out = sh(script: "bash grype-scan-${svc}.sh", returnStdout: true).trim()
            sh "rm -f grype-scan-${svc}.sh"

            def critical = 0
            def high = 0
            def lines = out.split('\n')
            for (int j = 0; j < lines.size(); j++) {
                def line = lines[j].trim()
                if (line.startsWith('GRYPE_CRITICAL=')) {
                    critical = line.substring('GRYPE_CRITICAL='.length()).toInteger()
                }
                if (line.startsWith('GRYPE_HIGH=')) {
                    high = line.substring('GRYPE_HIGH='.length()).toInteger()
                }
            }

            grypeResults[svc] = [critical: critical, high: high]
            echo "${svc} — CRITICAL: ${critical}, HIGH: ${high}"
        }

        // Summary
        echo "Grype Scan Summary:"
        def criticalServices = []
        for (int i = 0; i < services.size(); i++) {
            def svc = services[i]
            def r = grypeResults[svc]
            echo "  ${svc}: CRITICAL=${r.critical}, HIGH=${r.high}"
            if (r.critical > 0) criticalServices << svc
        }

        if (criticalServices.size() > 0) {
            echo "WARNING: Critical vulnerabilities found in: ${criticalServices.join(', ')}"
        }

        stash name: 'grype-reports', includes: 'grype-reports/**'
    }

    echo "Stage 8 complete - container scans finished"
}

def mkdir(String dir) {
    sh "mkdir -p ${dir}"
}

return this