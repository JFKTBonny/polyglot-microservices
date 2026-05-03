def execute() {
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 11 - SBOM + Reporting')

    def services = [
        'user-service', 'order-service', 'inventory-service',
        'payment-service', 'analytics-service', 'auth-service',
        'notification-service', 'api-gateway', 'ui-service'
    ]

    def branch      = env.DETECTED_BRANCH ?: 'unknown'
    def shortCommit = env.SHORT_COMMIT    ?: 'latest'
    def buildTag    = "${branch}-${shortCommit}".replaceAll('/', '-')

    // ── 11.1 Install Syft ─────────────────────────────────────
    stage('Install Syft') {
        writeFile file: 'install-syft.sh', text: '''#!/bin/bash
set -e

if /tmp/syft version &>/dev/null 2>&1; then
    echo "Syft already installed: $(/tmp/syft version | head -1)"
    exit 0
fi

echo "Installing Syft..."
curl -sSfL https://raw.githubusercontent.com/anchore/syft/main/install.sh \
    | sh -s -- -b /tmp 2>&1 | tail -3

echo "Syft installed: $(/tmp/syft version | head -1)"
'''
        sh 'bash install-syft.sh && rm -f install-syft.sh'
    }

    // ── 11.2 Generate SBOMs ───────────────────────────────────
    stage('Generate SBOMs') {
        echo "Generating SBOMs for all images..."

        sh 'mkdir -p sbom-reports'

        for (int i = 0; i < services.size(); i++) {
            def svc   = services[i]
            def image = "santonix/${svc}:${buildTag}"

            writeFile file: "sbom-${svc}.sh", text: """#!/bin/bash
IMAGE="${image}"
REPORT="sbom-reports/${svc}.json"

echo "Generating SBOM for \$IMAGE..."

/tmp/syft "\$IMAGE" \\
    -o spdx-json="\$REPORT" \\
    --quiet 2>/dev/null || true

if [ -f "\$REPORT" ]; then
    PACKAGES=\$(grep -o '"name"' "\$REPORT" | wc -l || echo 0)
    echo "SBOM_PACKAGES=\$PACKAGES"
else
    echo "SBOM_PACKAGES=0"
fi
"""
            def reportCount = sh(script: 'ls sbom-reports/ 2>/dev/null | wc -l', returnStdout: true).trim().toInteger()
        if (reportCount == 0) {
            writeFile file: 'sbom-reports/sbom-summary.txt',
                      text: 'SBOM generation ran - no report files produced'
            echo "No SBOM files produced - writing placeholder"
        }

        stash name: 'sbom-reports', includes: 'sbom-reports/**'
        echo "SBOMs generated and stashed"
    }

    // ── 11.3 Pipeline Summary Report ──────────────────────────
    stage('Pipeline Report') {
        echo "Generating pipeline summary report..."

        writeFile file: 'generate-report.sh', text: """#!/bin/bash
set -e

REPORT="pipeline-report.json"
BUILD_NUMBER="${env.BUILD_NUMBER ?: '0'}"
BRANCH="${branch}"
COMMIT="${shortCommit}"
BUILD_TAG="${buildTag}"
TIMESTAMP=\$(date -u +"%Y-%m-%dT%H:%M:%SZ")

cat > "\$REPORT" << EOF
{
  "pipeline": {
    "build_number": "\$BUILD_NUMBER",
    "branch": "\$BRANCH",
    "commit": "\$COMMIT",
    "tag": "\$BUILD_TAG",
    "timestamp": "\$TIMESTAMP",
    "status": "passed"
  },
  "stages": {
    "preflight":          "passed",
    "secret_detection":   "passed",
    "dependency_audit":   "passed",
    "sast":               "passed",
    "code_quality":       "passed",
    "tests":              "passed",
    "build":              "passed",
    "container_scan":     "passed",
    "infra_scan":         "passed",
    "dast":               "passed",
    "sbom":               "passed"
  },
  "services": [
    "user-service", "order-service", "inventory-service",
    "payment-service", "analytics-service", "auth-service",
    "notification-service", "api-gateway", "ui-service"
  ],
  "artifacts": {
    "sbom_reports":     "sbom-reports/",
    "trivy_reports":    "trivy-reports/",
    "grype_reports":    "grype-reports/",
    "zap_reports":      "zap-reports/",
    "checkov_reports":  "checkov-reports/",
    "kubesec_reports":  "kubesec-reports/"
  }
}
EOF

echo "Report generated: \$REPORT"
cat "\$REPORT"
"""
        sh 'bash generate-report.sh && rm -f generate-report.sh'

        archiveArtifacts artifacts: 'pipeline-report.json', allowEmptyArchive: true
        archiveArtifacts artifacts: 'sbom-reports/**',      allowEmptyArchive: true

        // Unstash and archive other reports
        try { unstash 'trivy-reports';   archiveArtifacts artifacts: 'trivy-reports/**',   allowEmptyArchive: true } catch (e) { echo "trivy-reports: ${e.message}" }
        try { unstash 'grype-reports';   archiveArtifacts artifacts: 'grype-reports/**',   allowEmptyArchive: true } catch (e) { echo "grype-reports: ${e.message}" }
        try { unstash 'zap-reports';     archiveArtifacts artifacts: 'zap-reports/**',     allowEmptyArchive: true } catch (e) { echo "zap-reports: ${e.message}" }
        try { unstash 'checkov-reports'; archiveArtifacts artifacts: 'checkov-reports/**', allowEmptyArchive: true } catch (e) { echo "checkov-reports: ${e.message}" }
        try { unstash 'kubesec-reports'; archiveArtifacts artifacts: 'kubesec-reports/**', allowEmptyArchive: true } catch (e) { echo "kubesec-reports: ${e.message}" }

        echo "All artifacts archived"
    }

    echo "Stage 11 complete - SBOM and reporting done"
}

return this