def execute() {
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 7 - Build Docker Images')

    def services = [
        [name: 'user-service',         image: 'santonix/user-service'],
        [name: 'order-service',         image: 'santonix/order-service'],
        [name: 'inventory-service',     image: 'santonix/inventory-service'],
        [name: 'payment-service',       image: 'santonix/payment-service'],
        [name: 'analytics-service',     image: 'santonix/analytics-service'],
        [name: 'auth-service',          image: 'santonix/auth-service'],
        [name: 'notification-service',  image: 'santonix/notification-service'],
        [name: 'api-gateway',           image: 'santonix/api-gateway'],
        [name: 'ui-service',            image: 'santonix/ui-service']
    ]

    def changedServices = env.CHANGED_SERVICES?.split(',')?.toList() ?: []
    def branch          = env.DETECTED_BRANCH ?: 'unknown'
    def shortCommit     = env.SHORT_COMMIT    ?: 'latest'
    def buildTag        = "${branch}-${shortCommit}".replaceAll('/', '-')

    echo "Build tag: ${buildTag}"
    echo "Changed services: ${changedServices.join(', ')}"


    // ################## 7.1 Build images #########################################################
    stage('Build Images') {
        echo "Building Docker images..."

        def buildResults = [:]

        for (int i = 0; i < services.size(); i++) {
            def svc  = services[i]
            def name = svc.name
            def img  = svc.image

            if (!fileExists("${name}/Dockerfile")) {
                echo "No Dockerfile for ${name} — skipping"
                buildResults[name] = 'skipped'
                continue
            }

            writeFile file: "build-${name}.sh", text: """#!/bin/bash
set -e
SERVICE="${name}"
IMAGE="${img}"
TAG="${buildTag}"

echo "Building \$IMAGE:\$TAG..."

docker build \
    -t \$IMAGE:\$TAG \
    -t \$IMAGE:latest \
    --label "branch=${branch}" \
    --label "commit=${shortCommit}" \
    --label "build=\${BUILD_NUMBER}" \
    --no-cache \
    \$SERVICE/ 2>&1

echo "Built \$IMAGE:\$TAG"
"""
            def result = sh(
                script: "bash build-${name}.sh",
                returnStatus: true
            )
            sh "rm -f build-${name}.sh"

            if (result == 0) {
                buildResults[name] = 'success'
                echo "${name} — built ✅"
            } else {
                buildResults[name] = 'failed'
                echo "${name} — build failed ❌"
            }
        }

        // Summary
        echo "Build Summary:"
        def failed = []
        for (int i = 0; i < services.size(); i++) {
            def name = services[i].name
            def status = buildResults[name] ?: 'unknown'
            echo "  ${name}: ${status}"
            if (status == 'failed') failed << name
        }

        if (failed.size() > 0) {
            pipeline.block('Build Images', "Failed to build: ${failed.join(', ')}")
        }
    }


    // ################## 7.2 Verify images #########################################################
    stage('Verify Images') {
        echo "Verifying built images..."

        writeFile file: 'verify-images.sh', text: """#!/bin/bash
TAG="${buildTag}"

echo "Built images:"
docker images | grep "santonix" | grep "\$TAG" || true

echo ""
echo "Image sizes:"
docker images --format "{{.Repository}}:{{.Tag}} {{.Size}}" \\
    | grep "santonix" \\
    | grep "\$TAG" \\
    || true

echo "Verification complete"
"""
        sh 'bash verify-images.sh && rm -f verify-images.sh'
    }


    // ################## 7.3 Save build metadata #########################################################
    stage('Save Build Metadata') {
        echo "Saving build metadata..."

        def metadata = """BUILD_TAG=${buildTag}
BUILD_NUMBER=${env.BUILD_NUMBER}
BRANCH=${branch}
COMMIT=${shortCommit}
SERVICES=${services.collect { it.name }.join(',')}
"""
        writeFile file: '.build-metadata', text: metadata
        stash name: 'build-metadata', includes: '.build-metadata'
        echo "Build metadata saved"
    }

    echo "Stage 7 complete - all images built"
}

return this