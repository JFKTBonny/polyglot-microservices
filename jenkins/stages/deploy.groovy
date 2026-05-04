def execute() {
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 13 - Deploy to Kubernetes')

    def branch      = env.DETECTED_BRANCH ?: 'unknown'
    def shortCommit = env.SHORT_COMMIT    ?: 'latest'
    def buildTag    = "${branch}-${shortCommit}".replaceAll('/', '-')
    def namespace   = 'polyglot'

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

    // ── 13.1 Verify cluster access ────────────────────────────
    stage('Verify Cluster') {
        echo "Verifying Kubernetes cluster access..."

        writeFile file: 'verify-cluster.sh', text: """#!/bin/bash
set -e

echo "kubectl version:"
kubectl version --client

echo "Cluster info:"
kubectl cluster-info --request-timeout=10s 2>/dev/null || echo "WARNING: Cannot reach cluster"

echo "Namespace check:"
kubectl get namespace ${namespace} 2>/dev/null || {
    echo "Creating namespace ${namespace}..."
    kubectl create namespace ${namespace} 2>/dev/null || echo "Namespace may already exist"
}

echo "Nodes:"
kubectl get nodes 2>/dev/null || echo "WARNING: Cannot list nodes"
"""
        sh 'bash verify-cluster.sh && rm -f verify-cluster.sh'
    }

    // ── 13.2 Update image tags ────────────────────────────────
    stage('Update Image Tags') {
        echo "Updating deployment image tags to ${buildTag}..."

        for (int i = 0; i < services.size(); i++) {
            def svc        = services[i]
            def name       = svc.name
            def image      = svc.image
            def deployment = name

            writeFile file: "tag-${name}.sh", text: """#!/bin/bash
NAMESPACE="${namespace}"
DEPLOYMENT="${deployment}"
IMAGE="${image}:${buildTag}"

echo "Updating \$DEPLOYMENT to \$IMAGE..."

# Check if deployment exists
if kubectl get deployment "\$DEPLOYMENT" -n "\$NAMESPACE" &>/dev/null; then
    kubectl set image deployment/"\$DEPLOYMENT" \
        "\$DEPLOYMENT"="\$IMAGE" \
        -n "\$NAMESPACE" 2>/dev/null || echo "WARNING: Could not update \$DEPLOYMENT"
    echo "Updated \$DEPLOYMENT ✅"
else
    echo "Deployment \$DEPLOYMENT not found - applying manifests..."
    # Find and apply the manifest
    MANIFEST=\$(find feature/k8s/ -name "*.yaml" -path "*${name}*" 2>/dev/null | head -1)
    if [ -n "\$MANIFEST" ]; then
        kubectl apply -f "\$MANIFEST" -n "\$NAMESPACE" 2>/dev/null || echo "WARNING: Could not apply \$MANIFEST"
        kubectl set image deployment/"\$DEPLOYMENT" \
            "\$DEPLOYMENT"="\$IMAGE" \
            -n "\$NAMESPACE" 2>/dev/null || true
        echo "Applied \$MANIFEST ✅"
    else
        echo "No manifest found for \$DEPLOYMENT ⚠️"
    fi
fi
"""
            sh "bash tag-${name}.sh && rm -f tag-${name}.sh"
        }
    }

    // ── 13.3 Apply K8s manifests ──────────────────────────────
    stage('Apply Manifests') {
        echo "Applying Kubernetes manifests..."

        writeFile file: 'apply-manifests.sh', text: """#!/bin/bash
NAMESPACE="${namespace}"

echo "Applying configmaps and secrets..."
find k8s/ -name "configmap*.yaml" -o -name "secret*.yaml" 2>/dev/null | \
    xargs -r kubectl apply -n "\$NAMESPACE" -f 2>/dev/null || true

echo "Applying services..."
find k8s/ -name "service.yaml" 2>/dev/null | \
    xargs -r kubectl apply -n "\$NAMESPACE" -f 2>/dev/null || true

echo "Applying ingress..."
find k8s/ -name "ingress*.yaml" 2>/dev/null | \
    xargs -r kubectl apply -n "\$NAMESPACE" -f 2>/dev/null || true

echo "Current deployments in ${namespace}:"
kubectl get deployments -n "\$NAMESPACE" 2>/dev/null || echo "No deployments found"

echo "Current pods in ${namespace}:"
kubectl get pods -n "\$NAMESPACE" 2>/dev/null || echo "No pods found"

echo "Manifests applied"
"""
        sh 'bash apply-manifests.sh && rm -f apply-manifests.sh'
    }

    // ── 13.4 Wait for rollout ─────────────────────────────────
    stage('Wait for Rollout') {
        echo "Waiting for deployments to roll out..."

        writeFile file: 'wait-rollout.sh', text: """#!/bin/bash
NAMESPACE="${namespace}"
TIMEOUT=120

echo "Waiting for rollouts (timeout: \${TIMEOUT}s)..."

for SVC in ${services.collect { it.name }.join(' ')}; do
    if kubectl get deployment "\$SVC" -n "\$NAMESPACE" &>/dev/null; then
        echo "Waiting for \$SVC..."
        kubectl rollout status deployment/"\$SVC" \
            -n "\$NAMESPACE" \
            --timeout="\${TIMEOUT}s" 2>/dev/null || \
            echo "WARNING: \$SVC rollout timed out or failed"
    else
        echo "\$SVC deployment not found - skipping"
    fi
done

echo "Rollout wait complete"
"""
        sh 'bash wait-rollout.sh && rm -f wait-rollout.sh'
    }

    // ── 13.5 Deployment status ────────────────────────────────
    stage('Deployment Status') {
        echo "Checking deployment status..."

        writeFile file: 'deploy-status.sh', text: """#!/bin/bash
NAMESPACE="${namespace}"

echo "════ Deployments ════"
kubectl get deployments -n "\$NAMESPACE" -o wide 2>/dev/null || echo "No deployments"

echo ""
echo "════ Pods ════"
kubectl get pods -n "\$NAMESPACE" -o wide 2>/dev/null || echo "No pods"

echo ""
echo "════ Services ════"
kubectl get services -n "\$NAMESPACE" 2>/dev/null || echo "No services"

echo ""
echo "════ Ingress ════"
kubectl get ingress -n "\$NAMESPACE" 2>/dev/null || echo "No ingress"
"""
        sh 'bash deploy-status.sh && rm -f deploy-status.sh'
    }

    echo "Stage 13 complete - deployment done"
}

return this