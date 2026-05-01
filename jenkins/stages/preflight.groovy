def execute() {
    def tools    = load 'jenkins/helpers/tools.groovy'
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 1 - Pre-flight')

    // Set pipeline context before parallel
    env.DETECTED_BRANCH = env.BRANCH_NAME
        ?: env.GIT_BRANCH?.replaceFirst('origin/', '')
        ?: sh(script: "git rev-parse --abbrev-ref HEAD", returnStdout: true).trim()

    env.GIT_AUTHOR = env.GIT_AUTHOR_NAME
        ?: sh(script: "git log -1 --pretty=%an", returnStdout: true).trim()

    env.GIT_AUTHOR_EMAIL = sh(
        script: "git log -1 --pretty=%ae",
        returnStdout: true
    ).trim()

    env.SHORT_COMMIT = sh(
        script: "git log -1 --pretty=%h",
        returnStdout: true
    ).trim()

    env.FULL_COMMIT = sh(
        script: "git log -1 --pretty=%H",
        returnStdout: true
    ).trim()

    env.PIPELINE_START_TIME = System.currentTimeMillis().toString()

    tools.printVersions()

    parallel(

        'Validate Branch': {
            stage('Validate Branch') {
                script {
                    def branch = env.DETECTED_BRANCH?.trim()
                    echo "Validating branch: ${branch}"

                    if (!branch || branch == 'HEAD') {
                        echo "Detached HEAD - skipping branch validation"
                        return
                    }

                    def valid = (
                        branch == 'main' ||
                        branch == 'develop' ||
                        branch.matches('^feature/[a-z0-9][a-z0-9\\-]{2,49}$') ||
                        branch.matches('^fix/[a-z0-9][a-z0-9\\-]{2,49}$') ||
                        branch.matches('^hotfix/[a-z0-9][a-z0-9\\-]{2,49}$') ||
                        branch.matches('^release/v\\d+\\.\\d+\\.\\d+$')
                    )

                    if (!valid) {
                        pipeline.block(
                            'Branch Validation',
                            "Invalid branch name: ${branch}"
                        )
                    }
                    echo "Branch valid: ${branch}"
                }
            }
        },

        'Validate Commit': {
            stage('Validate Commit') {
                script {
                    def msg = sh(
                        script: "git log -1 --pretty=%B HEAD",
                        returnStdout: true
                    ).trim()

                    echo "Validating commit: ${msg}"

                    def skip = msg.startsWith('Merge ') ||
                               msg.startsWith('Initial') ||
                               msg.startsWith('Revert ')

                    def valid = msg.matches(
                        '^(feat|fix|docs|style|refactor|test|chore|ci|security|perf|build|revert)(\\([a-z0-9\\-]+\\))?: .{10,100}$'
                    )

                    if (!skip && !valid) {
                        pipeline.block(
                            'Commit Validation',
                            "Invalid commit message: ${msg}"
                        )
                    }
                    echo "Commit valid: ${msg}"
                }
            }
        },

        'Detect Changes': {
            stage('Detect Changes') {
                script {
                    def allServices = [
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

                    def changedFiles = sh(
                        script: "git diff --name-only HEAD~1 HEAD 2>/dev/null || echo ''",
                        returnStdout: true
                    ).trim()

                    def forceAll = (
                        env.FORCE_ALL_SERVICES == 'true' ||
                        changedFiles.isEmpty() ||
                        env.DETECTED_BRANCH == 'main' ||
                        env.DETECTED_BRANCH == 'develop' ||
                        changedFiles.contains('Jenkinsfile') ||
                        changedFiles.contains('jenkins/')
                    )

                    def changed = forceAll
                        ? allServices
                        : allServices.findAll { changedFiles.contains(it) }

                    env.CHANGED_SERVICES = changed.join(',')

                    echo "Branch:   ${env.DETECTED_BRANCH}"
                    echo "Commit:   ${env.SHORT_COMMIT}"
                    echo "Author:   ${env.GIT_AUTHOR}"
                    echo "Services: ${changed.join(', ')}"
                }
            }
        },

        'Validate K8s': {
            stage('Validate K8s') {
                script {
                    echo "Validating K8s manifests..."
                    sh '''
                        find k8s/ -name "*.yaml" -o -name "*.yml" 2>/dev/null | \
                        while read f; do
                            python3 -c "
import yaml, sys
try:
    list(yaml.safe_load_all(open('$f')))
    print('OK:', '$f')
except Exception as e:
    print('WARN:', '$f', str(e))
" 2>/dev/null || echo "Skipping: $f"
                        done
                        echo "K8s validation done"
                    '''
                }
            }
        },

        'Validate Docker Compose': {
            stage('Validate Docker Compose') {
                script {
                    echo "Validating docker-compose..."
                    sh '''
                        if [ -f docker-compose.yaml ] || [ -f docker-compose.yml ]; then
                            docker compose config --quiet 2>/dev/null \
                                && echo "docker-compose valid" \
                                || echo "Warning: docker-compose validation failed"
                        else
                            echo "No docker-compose file found"
                        fi
                    '''
                }
            }
        }

    ) // end parallel

    echo "Pre-flight complete"
}



return this
