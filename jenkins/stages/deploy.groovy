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

        writeFile file: 'verify.sh', text: """#!/bin/bash
    set -e

    kubectl cluster-info

    kubectl get ns ${namespace} >/dev/null 2>&1 || \\
    kubectl create ns ${namespace}

    kubectl get nodes
    """

        sh 'bash verify.sh'
    }

    // ############## Find and Deploy manifests ####################################################
    stage('Deploy') {

        writeFile file: 'deploy.sh', text: """#!/bin/bash
    set -e

    NAMESPACE="${namespace}"

    SERVICES=(
    "user-service"
    "order-service"
    "inventory-service"
    "payment-service"
    "analytics-service"
    "auth-service"
    "notification-service"
    "api-gateway"
    "ui-service"
    )

    echo "Applying configmaps..."

    for FILE in feature/k8s/configmaps/*.yaml; do
        [ -f "\$FILE" ] || continue
        kubectl apply -f "\$FILE" -n "\$NAMESPACE"  --validate=false 
    done

    echo "Applying secrets..."

    for FILE in feature/k8s/secrets/*.yaml; do
        [ -f "\$FILE" ] || continue
        kubectl apply -f "\$FILE" -n "\$NAMESPACE"  
    done

    echo "Deploying services..."

    for SVC in "\${SERVICES[@]}"; do

        if [ ! -d "feature/k8s/\$SVC" ]; then
            echo "No manifests for \$SVC"
            continue
        fi

        echo "Deploying \$SVC..."

        find feature/k8s/\$SVC -name "*.yaml" \\
            -exec kubectl apply -n "\$NAMESPACE" -f {} \\;

    done
    """

        sh 'bash deploy.sh'
    }

    // ############## Rollout and Status ####################################################
    stage('Rollout and Status') {

        writeFile file: 'rollout.sh', text: """#!/bin/bash

    NAMESPACE="${namespace}"

    SERVICES=(
    "user-service"
    "order-service"
    "inventory-service"
    "payment-service"
    "analytics-service"
    "auth-service"
    "notification-service"
    "api-gateway"
    "ui-service"
    )

    for SVC in "\${SERVICES[@]}"; do

        echo "Waiting for \$SVC..."

        kubectl rollout status deployment/\$SVC \\
            -n "\$NAMESPACE" \\
            --timeout=120s || true

    done

    echo ""
    echo "════ Deployments ════"
    kubectl get deployments -n "\$NAMESPACE"

    echo ""
    echo "════ Pods ════"
    kubectl get pods -n "\$NAMESPACE"

    echo ""
    echo "════ Services ════"
    kubectl get svc -n "\$NAMESPACE"

    echo ""
    echo "════ Ingress ════"
    kubectl get ingress -n "\$NAMESPACE" || true
    """

        sh 'bash rollout.sh'
    }

    echo "Stage 13 complete - deployment done"
}    
return this
     
    





   


     