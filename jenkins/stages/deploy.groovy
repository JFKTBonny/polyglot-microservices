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

    stage('Verify Cluster') {
        writeFile file: 'verify-cluster.sh', text: """#!/bin/bash
    set -e

    NAMESPACE="${namespace}"

    echo "kubectl version:"
    kubectl version --client

    echo ""
    echo "Cluster info:"
    kubectl cluster-info --request-timeout=10s || echo "WARNING: Cannot reach cluster"

    echo ""
    echo "Namespace check:"
    kubectl get namespace "\$NAMESPACE" >/dev/null 2>&1 || {
        echo "Creating namespace \$NAMESPACE..."
        kubectl create namespace "\$NAMESPACE" || echo "Namespace may already exist"
    }

    echo ""
    echo "Nodes:"
    kubectl get nodes || echo "WARNING: Cannot list nodes"
    """
        sh 'chmod +x verify-cluster.sh && ./verify-cluster.sh'
    }


    // ############## Find and Deploy manifests ####################################################
    stage('Find and Deploy manifests') {

    def serviceNames = services.collect { it.name }.join(' ')
    def serviceImages = services.collect { "${it.name}=${it.image}" }.join(' ')

        writeFile file: 'deploy.sh', text: """#!/bin/bash
    set -e

    NAMESPACE="${namespace}"

    declare -A IMAGES
    for pair in ${serviceImages}; do
        key=\${pair%%=*}
        value=\${pair#*=}
        IMAGES[\$key]=\$value
    done

    SERVICES="${serviceNames}"

    for SVC in \$SERVICES; do
        IMAGE=\${IMAGES[\$SVC]}

        echo "Processing \$SVC..."

        kubectl get namespace "\$NAMESPACE" >/dev/null 2>&1 || \\
            kubectl create namespace "\$NAMESPACE"

        echo "Applying configmaps..."
        find feature/k8s/configmaps/ -name "*.yaml" | \\
            xargs -r kubectl apply -n "\$NAMESPACE" -f || true

        echo "Applying secrets..."
        find feature/k8s/secrets/ -name "*.yaml" | \\
            xargs -r kubectl apply -n "\$NAMESPACE" -f || true

        MANIFEST=\$(find feature/k8s/ -name "*.yaml" -path "*\$SVC*" | head -1)

        if [ -z "\$MANIFEST" ]; then
            echo "No manifest found for \$SVC ⚠️"
            continue
        fi

        kubectl apply -f "\$MANIFEST" -n "\$NAMESPACE"

        if kubectl get deployment "\$SVC" -n "\$NAMESPACE" >/dev/null 2>&1; then
            kubectl set image deployment/"\$SVC" "\$SVC"="\$IMAGE" -n "\$NAMESPACE"
        fi

        echo "Done \$SVC ✅"
    done
    """
        sh 'chmod +x deploy.sh && ./deploy.sh'
    }
       

     
    





    stage('Wait for Rollout') {

    def serviceNames = services.collect { it.name }.join(' ')
    def timeout = 120

        writeFile file: 'rollout.sh', text: """#!/bin/bash

    NAMESPACE="${namespace}"
    TIMEOUT=${timeout}
    SERVICES="${serviceNames}"

    for SVC in \$SERVICES; do
        if kubectl get deployment "\$SVC" -n "\$NAMESPACE" >/dev/null 2>&1; then
            echo "Waiting for \$SVC..."
            kubectl rollout status deployment/"\$SVC" \\
                -n "\$NAMESPACE" \\
                --timeout="\${TIMEOUT}s" || \\
                echo "WARNING: \$SVC rollout failed"
        else
            echo "\$SVC not found - skipping"
        fi
    done
    """
        sh 'chmod +x rollout.sh && ./rollout.sh'
    }

    stage('Deployment Status') {

        writeFile file: 'status.sh', text: """#!/bin/bash

    NAMESPACE="${namespace}"

    echo "════ Deployments ════"
    kubectl get deployments -n "\$NAMESPACE" -o wide || echo "No deployments"

    echo ""
    echo "════ Pods ════"
    kubectl get pods -n "\$NAMESPACE" -o wide || echo "No pods"

    echo ""
    echo "════ Services ════"
    kubectl get services -n "\$NAMESPACE" || echo "No services"

    echo ""
    echo "════ Ingress ════"
    kubectl get ingress -n "\$NAMESPACE" || echo "No ingress"
    """
        sh 'chmod +x status.sh && ./status.sh'
    }

    echo "Stage 13 complete - deployment done"
}    
return this