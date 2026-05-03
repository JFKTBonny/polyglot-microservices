def execute() {
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 12 - Push to Registry')

    def services = [
        [name: 'user-service',        image: 'santonix/user-service'],
        [name: 'order-service',        image: 'santonix/order-service'],
        [name: 'inventory-service',    image: 'santonix/inventory-service'],
        [name: 'payment-service',      image: 'santonix/payment-service'],
        [name: 'analytics-service',    image: 'santonix/analytics-service'],
        [name: 'auth-service',         image: 'santonix/auth-service'],
        [name: 'notification-service', image: 'santonix/notification-service'],
        [name: 'api-gateway',          image: 'santonix/api-gateway'],
        [name: 'ui-service',           image: 'santonix/ui-service']
    ]

    def branch      = env.DETECTED_BRANCH ?: 'unknown'
    def shortCommit = env.SHORT_COMMIT    ?: 'latest'
    def buildTag    = "${branch}-${shortCommit}".replaceAll('/', '-')

    // ── 12.1 Docker Login ─────────────────────────────────────
    stage('Docker Login') {
        echo "Logging in to Docker Hub..."
        withCredentials([usernamePassword(
            credentialsId: 'DOCKER_CREDS',
            usernameVariable: 'DOCKER_USER',
            passwordVariable: 'DOCKER_PASS'
        )]) {
            sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin'
        }
        echo "Docker login successful"
    }

    // ── 12.2 Push images ──────────────────────────────────────
    stage('Push Images') {
        echo "Pushing images to Docker Hub..."

        def pushResults = [:]

        for (int i = 0; i < services.size(); i++) {
            def svc  = services[i]
            def name = svc.name
            def img  = svc.image

            writeFile file: "push-${name}.sh", text: """#!/bin/bash
set -e
IMAGE="${img}"
TAG="${buildTag}"

echo "Pushing \$IMAGE:\$TAG..."
docker push "\$IMAGE:\$TAG"

echo "Pushing \$IMAGE:latest..."
docker push "\$IMAGE:latest"

echo "Push complete for \$IMAGE"
"""
            def result = sh(script: "bash push-${name}.sh", returnStatus: true)
            sh "rm -f push-${name}.sh"

            if (result == 0) {
                pushResults[name] = 'pushed'
                echo "${name} — pushed ✅"
            } else {
                pushResults[name] = 'failed'
                echo "${name} — push failed ❌"
            }
        }

        // Summary
        echo "Push Summary:"
        def failed = []
        for (int i = 0; i < services.size(); i++) {
            def name   = services[i].name
            def status = pushResults[name] ?: 'unknown'
            echo "  ${name}: ${status}"
            if (status == 'failed') failed << name
        }

        if (failed.size() > 0) {
            pipeline.block('Push Images', "Failed to push: ${failed.join(', ')}")
        }
    }

    // ── 12.3 Docker Logout ────────────────────────────────────
    stage('Docker Logout') {
        sh 'docker logout || true'
        echo "Docker logout complete"
    }

    echo "Stage 12 complete - all images pushed"
}

return this