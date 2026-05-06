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
        steps {
            script {
                

                sh """
                set -e

                echo "kubectl version:"
                kubectl version --client

                echo ""
                echo "Cluster info:"
                kubectl cluster-info --request-timeout=10s || \\
                    echo "WARNING: Cannot reach cluster"

                echo ""
                echo "Namespace check:"
                kubectl get namespace ${namespace} >/dev/null 2>&1 || {
                    echo "Creating namespace ${namespace}..."
                    kubectl create namespace ${namespace} || \\
                        echo "Namespace may already exist"
                }

                echo ""
                echo "Nodes:"
                kubectl get nodes || echo "WARNING: Cannot list nodes"
                """
            }
        }
    }
        sh 'bash verify-cluster.sh && rm -f verify-cluster.sh'
    }
    // ############## Find and Deploy manifests ####################################################
    stage('Find and Deploy manifests') {
        steps {
            script {
                

                services.each { svc ->
                    def name  = svc.name
                    def image = svc.image

                    sh """
                    #!/bin/bash
                    set -e

                    NAMESPACE="${namespace}"
                    DEPLOYMENT="${name}"
                    IMAGE="${image}"

                    echo "Processing \$DEPLOYMENT..."

                    # Ensure namespace exists
                    kubectl get namespace "\$NAMESPACE" >/dev/null 2>&1 || \\
                        kubectl create namespace "\$NAMESPACE"

                    # Apply shared resources
                    echo "Applying configmaps..."
                    find feature/k8s/configmaps/ -name "*.yaml" 2>/dev/null | \\
                        xargs -r kubectl apply -n "\$NAMESPACE" -f || true

                    echo "Applying secrets..."
                    find feature/k8s/secrets/ -name "*.yaml" 2>/dev/null | \\
                        xargs -r kubectl apply -n "\$NAMESPACE" -f || true

                    # Find manifest for this service
                    MANIFEST=\$(find feature/k8s/ -name "*.yaml" -path "*${name}*" 2>/dev/null | head -1)

                    if [ -z "\$MANIFEST" ]; then
                        echo "No manifest found for \$DEPLOYMENT ⚠️"
                        exit 0
                    fi

                    # Apply manifest
                    echo "Applying \$MANIFEST..."
                    kubectl apply -f "\$MANIFEST" -n "\$NAMESPACE"

                    # Update image (only if deployment exists)
                    if kubectl get deployment "\$DEPLOYMENT" -n "\$NAMESPACE" >/dev/null 2>&1; then
                        kubectl set image deployment/"\$DEPLOYMENT" \\
                            "\$DEPLOYMENT"="\$IMAGE" \\
                            -n "\$NAMESPACE"
                    fi

                    echo "Done \$DEPLOYMENT ✅"
                    """
                }

                sh """
                echo "Current deployments:"
                kubectl get deployments -n ${namespace} || true

                echo "Current pods:"
                kubectl get pods -n ${namespace} || true
                """
            }
        }
    }

       

     
    





    stage('Wait for Rollout') {
        steps {
            script {
                
                def timeout   = 120

                services.each { svc ->
                    def name = svc.name

                    sh """
                    NAMESPACE="${namespace}"
                    SVC="${name}"
                    TIMEOUT=${timeout}

                    if kubectl get deployment "\$SVC" -n "\$NAMESPACE" >/dev/null 2>&1; then
                        echo "Waiting for \$SVC..."
                        kubectl rollout status deployment/"\$SVC" \\
                            -n "\$NAMESPACE" \\
                            --timeout="\${TIMEOUT}s" || \\
                            echo "WARNING: \$SVC rollout failed or timed out"
                    else
                        echo "\$SVC not found - skipping"
                    fi
                    """
                }

                echo "Rollout wait complete"
            }
        }
    }

    stage('Deployment Status') {
        steps {
            script {
                

                sh """
                echo "════ Deployments ════"
                kubectl get deployments -n ${namespace} -o wide || echo "No deployments"

                echo ""
                echo "════ Pods ════"
                kubectl get pods -n ${namespace} -o wide || echo "No pods"

                echo ""
                echo "════ Services ════"
                kubectl get services -n ${namespace} || echo "No services"

                echo ""
            echo "════ Ingress ════"
            kubectl get ingress -n ${namespace} || echo "No ingress"
            """
        }
    }
}

echo "Stage 13 complete - deployment done"
return this