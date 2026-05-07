def execute() {
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 13 - Deploy to Kubernetes')

    def branch      = env.DETECTED_BRANCH ?: 'unknown'
    def shortCommit = env.SHORT_COMMIT    ?: 'latest'
    def buildTag    = "${branch}-${shortCommit}".replaceAll('/', '-')
    def namespace   = 'polyglot'
    def DEPLOY_PATH = 'feature/k8s'

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
    "kafka"
    )

    echo "Applying configmaps..."

    
    kubectl apply -f "$DEPLOY_PATH/configmaps/" -n "\$NAMESPACE"  --validate=false 
    

    echo "Applying secrets..."

    
    kubectl apply -f "$DEPLOY_PATH/secrets/" -n "\$NAMESPACE" 

    echo "Applying databases..." 
    kubectl apply -f "$DEPLOY_PATH/databases/" -n "\$NAMESPACE"  
    

    echo "Deploying services..."

    for SVC in "\${SERVICES[@]}"; do

        kubectl apply -f "$DEPLOY_PATH/\$SVC"  -n "\$NAMESPACE"

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

    echo "Checking final status..."
    echo "════ Deployments ════"

    sleep 60

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
     
    





   


     