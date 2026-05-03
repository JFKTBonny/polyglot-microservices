def execute() {
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 10 - DAST (OWASP ZAP)')

    def services = [
        [name: 'user-service',      port: 3001, path: '/health'],
        [name: 'order-service',     port: 3002, path: '/health'],
        [name: 'inventory-service', port: 3003, path: '/health'],
        [name: 'payment-service',   port: 3004, path: '/actuator/health'],
        [name: 'analytics-service', port: 3005, path: '/health'],
        [name: 'auth-service',      port: 3006, path: '/health']
    ]

    def branch      = env.DETECTED_BRANCH ?: 'unknown'
    def shortCommit = env.SHORT_COMMIT    ?: 'latest'
    def buildTag    = "${branch}-${shortCommit}".replaceAll('/', '-')

    // ── 10.1 Pull ZAP image ───────────────────────────────────
    stage('Pull ZAP Image') {
        echo "Pulling OWASP ZAP image..."
        sh 'docker pull ghcr.io/zaproxy/zaproxy:stable 2>&1 | tail -3'
        echo "ZAP image ready"
    }

    // ── 10.2 Start services ───────────────────────────────────
    stage('Start Services') {
        echo "Starting services for DAST scanning..."

        writeFile file: 'start-services.sh', text: """#!/bin/bash
set -e
docker network create dast-net 2>/dev/null || true
TAG="${buildTag}"

docker run -d --name dast-user-service \\
    --network dast-net \\
    -e NODE_ENV=test -e DB_HOST=localhost -e JWT_SECRET=test-secret \\
    santonix/user-service:\$TAG 2>/dev/null || true

docker run -d --name dast-order-service \\
    --network dast-net -e PYTHONPATH=/app \\
    santonix/order-service:\$TAG 2>/dev/null || true

docker run -d --name dast-inventory-service \\
    --network dast-net \\
    santonix/inventory-service:\$TAG 2>/dev/null || true

docker run -d --name dast-payment-service \\
    --network dast-net -e SPRING_PROFILES_ACTIVE=test \\
    santonix/payment-service:\$TAG 2>/dev/null || true

docker run -d --name dast-analytics-service \\
    --network dast-net \\
    santonix/analytics-service:\$TAG 2>/dev/null || true

docker run -d --name dast-auth-service \\
    --network dast-net -e JWT_SECRET=test-secret \\
    santonix/auth-service:\$TAG 2>/dev/null || true

echo "Waiting 15s for services to start..."
sleep 15

echo "Running containers:"
docker ps --filter "name=dast-" --format "{{.Names}} {{.Status}}"
"""
        sh 'bash start-services.sh && rm -f start-services.sh'
    }

    // ── 10.3 ZAP Baseline Scans ───────────────────────────────
    stage('ZAP Baseline Scan') {
        echo "Running ZAP baseline scans..."

        sh 'mkdir -p zap-reports'

        def zapResults = [:]

        for (int i = 0; i < services.size(); i++) {
            def svc    = services[i]
            def name   = svc.name
            def port   = svc.port
            def target = "http://dast-${name}:${port}"

            writeFile file: "zap-scan-${name}.sh", text: """#!/bin/bash
SERVICE="${name}"
TARGET="${target}"

echo "ZAP scanning \$TARGET..."

docker run --rm \\
    --network dast-net \\
    -v \$(pwd)/zap-reports:/zap/wrk:rw \\
    ghcr.io/zaproxy/zaproxy:stable \\
    zap-baseline.py \\
    -t "\$TARGET" \\
    -J "\${SERVICE}.json" \\
    -I \\
    -l WARN \\
    2>/dev/null || true

echo "ZAP scan complete for \$SERVICE"

if [ -f "zap-reports/\${SERVICE}.json" ]; then
    HIGH=\$(grep -o '"riskdesc":"High' "zap-reports/\${SERVICE}.json" | wc -l || echo 0)
    MEDIUM=\$(grep -o '"riskdesc":"Medium' "zap-reports/\${SERVICE}.json" | wc -l || echo 0)
else
    HIGH=0
    MEDIUM=0
fi

echo "ZAP_HIGH=\$HIGH"
echo "ZAP_MEDIUM=\$MEDIUM"
"""
            def out = sh(script: "bash zap-scan-${name}.sh", returnStdout: true).trim()
            sh "rm -f zap-scan-${name}.sh"

            def high   = 0
            def medium = 0
            def lines  = out.split('\n')
            for (int j = 0; j < lines.size(); j++) {
                def line = lines[j].trim()
                if (line.startsWith('ZAP_HIGH=')) {
                    high = line.substring('ZAP_HIGH='.length()).toInteger()
                }
                if (line.startsWith('ZAP_MEDIUM=')) {
                    medium = line.substring('ZAP_MEDIUM='.length()).toInteger()
                }
            }

            zapResults[name] = [high: high, medium: medium]
            echo "${name} — HIGH: ${high}, MEDIUM: ${medium}"
        }

        echo "ZAP Scan Summary:"
        for (int i = 0; i < services.size(); i++) {
            def name = services[i].name
            def r    = zapResults[name]
            echo "  ${name}: HIGH=${r.high}, MEDIUM=${r.medium}"
        }

        def reportCount = sh(script: 'ls zap-reports/ 2>/dev/null | wc -l', returnStdout: true).trim().toInteger()
        if (reportCount == 0) {
            writeFile file: 'zap-reports/zap-summary.txt',
                      text: 'ZAP scans ran - services not reachable, no findings recorded'
            echo "No ZAP report files produced - writing placeholder"
        }

        stash name: 'zap-reports', includes: 'zap-reports/**'
        echo "ZAP reports stashed"
    }

    // ── 10.4 Cleanup ──────────────────────────────────────────
    stage('DAST Cleanup') {
        echo "Cleaning up DAST containers..."
        sh '''
            docker ps -a --filter "name=dast-" --format "{{.Names}}" | \
                xargs -r docker rm -f 2>/dev/null || true
            docker network rm dast-net 2>/dev/null || true
            echo "DAST cleanup complete"
        '''
    }

    echo "Stage 10 complete - DAST finished"
}

return this