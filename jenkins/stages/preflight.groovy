// ── Stage 1: Pre-flight ───────────────────────────────────────
// Validates branch naming, commit message format
// Detects changed services and sets pipeline context
// All checks run in parallel — fast, typically under 30s

def execute() {
    def tools    = load 'jenkins/helpers/tools.groovy'
    def notify   = load 'jenkins/helpers/notify.groovy'
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    // ── Set pipeline context BEFORE parallel ─────────────────
    // Must be set here so post{} blocks can access them
    env.DETECTED_BRANCH = env.BRANCH_NAME
        ?: env.GIT_BRANCH?.replaceFirst('origin/', '')
        ?: sh(
            script: "git name-rev --name-only HEAD 2>/dev/null | sed 's|remotes/origin/||'",
            returnStdout: true
        ).trim()

    env.GIT_AUTHOR = env.GIT_AUTHOR_NAME
        ?: sh(
            script: "git log -1 --pretty=%an",
            returnStdout: true
        ).trim()

    env.GIT_AUTHOR_EMAIL = env.GIT_AUTHOR_EMAIL
        ?: sh(
            script: "git log -1 --pretty=%ae",
            returnStdout: true
        ).trim()

    env.SHORT_COMMIT = env.GIT_COMMIT?.take(7)
        ?: sh(
            script: "git log -1 --pretty=%h",
            returnStdout: true
        ).trim()

    env.FULL_COMMIT = env.GIT_COMMIT
        ?: sh(
            script: "git log -1 --pretty=%H",
            returnStdout: true
        ).trim()

    env.PIPELINE_START_TIME = System.currentTimeMillis().toString()

    pipeline.banner('Stage 1 — Pre-flight')
    tools.printVersions()

    // ── Run checks in parallel ────────────────────────────────
    parallel(

        // ── 1.1 Validate Branch ───────────────────────────────
        'Validate Branch': {
            stage('Validate Branch') {
                script {
                    def branch = env.DETECTED_BRANCH?.trim()
                    echo "Validating branch: ${branch}"

                    if (!branch || branch == 'HEAD') {
                        echo "Detached HEAD or unknown branch — skipping validation"
                        return
                    }

                    def valid = (
                        branch == 'main'    ||
                        branch == 'develop' ||
                        branch.matches('^feature/[a-z0-9][a-z0-9\\-]{2,49}$')  ||
                        branch.matches('^fix/[a-z0-9][a-z0-9\\-]{2,49}$')      ||
                        branch.matches('^hotfix/[a-z0-9][a-z0-9\\-]{2,49}$')   ||
                        branch.matches('^release/v\\d+\\.\\d+\\.\\d+$')
                    )

                    if (!valid) {
                        pipeline.block('Branch Validation', "Invalid branch: ${branch}")
                    }

                    echo "Branch valid: ${branch}"
                }
            }
        },

        // ── 1.2 Validate Commit Message ───────────────────────
        'Validate Commit': {
            stage('Validate Commit') {
                script {
                    def commitMsg = sh(
                        script: "git log -1 --pretty=%B HEAD",
                        returnStdout: true
                    ).trim()

                    echo "Validating commit: ${commitMsg}"

                    def isMerge   = commitMsg.startsWith('Merge ')
                    def isInitial = commitMsg.startsWith('Initial')
                    def isRevert  = commitMsg.startsWith('Revert ')
                    def isValid   = commitMsg.matches(
                        '^(feat|fix|docs|style|refactor|test|chore|ci|security|perf|build|revert)(\\([a-z0-9\\-]+\\))?: .{10,100}$'
                    )

                    if (!isMerge && !isInitial && !isRevert && !isValid) {
                        pipeline.block(
                            'Commit Validation',
                            "Invalid message format"
                        )
                    }

                    echo "Commit valid: ${commitMsg}"
                }
            }
        },

        // ── 1.3 Detect Changed Services ───────────────────────
        'Detect Changes': {
            stage('Detect Changes') {
                script {
                    echo "Detecting changed services..."

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

                    def changedFiles = ''
                    try {
                        changedFiles = sh(
                            script: "git diff --name-only HEAD~1 HEAD 2>/dev/null || echo ''",
                            returnStdout: true
                        ).trim()
                    } catch (e) {
                        echo "Could not detect changes — building all services"
                        changedFiles = ''
                    }

                    // Force all on main/develop or when pipeline files change
                    def forceAll = (
                        env.FORCE_ALL_SERVICES == 'true'    ||
                        changedFiles.isEmpty()               ||
                        env.DETECTED_BRANCH == 'main'       ||
                        env.DETECTED_BRANCH == 'develop'    ||
                        changedFiles.contains('Jenkinsfile') ||
                        changedFiles.contains('jenkins/')   ||
                        changedFiles.contains('docker-compose')
                    )

                    def changed = forceAll
                        ? allServices
                        : allServices.findAll { svc ->
                            changedFiles.contains(svc)
                          }

                    env.CHANGED_SERVICES = changed.join(',')

                    pipeline.summary('Pre-flight Summary', [
                        ['Branch',   env.DETECTED_BRANCH],
                        ['Commit',   env.SHORT_COMMIT],
                        ['Author',   env.GIT_AUTHOR],
                        ['Email',    env.GIT_AUTHOR_EMAIL],
                        ['Services', changed.size().toString()],
                        ['Force All', forceAll.toString()]
                    ])

                    echo "Changed services: ${changed.join(', ')}"
                }
            }
        },

        // ── 1.4 Validate K8s Manifests Structure ──────────────
        'Validate K8s': {
            stage('Validate K8s') {
                script {
                    echo "Validating Kubernetes manifest structure..."

                    def requiredDirs = [
                        'k8s/databases',
                        'k8s/kafka',
                        'k8s/api-gateway',
                        'k8s/user-service',
                        'k8s/order-service',
                        'k8s/inventory-service',
                        'k8s/payment-service',
                        'k8s/analytics-service',
                        'k8s/auth-service',
                        'k8s/notification-service',
                        'k8s/ui-service'
                    ]

                    def missing = requiredDirs.findAll { dir ->
                        !fileExists(dir)
                    }

                    if (missing) {
                        echo "Warning — missing K8s directories:"
                        missing.each { d -> echo "  → ${d}" }
                    } else {
                        echo "K8s manifest structure valid"
                    }

                    // Validate yaml syntax
                    sh '''
                        if command -v python3 &>/dev/null; then
                            find k8s/ -name "*.yaml" -o -name "*.yml" | while read f; do
                                python3 -c "
import yaml, sys
try:
    yaml.safe_load_all(open('$f'))
    print('OK: $f')
except Exception as e:
    print('FAIL: $f -', str(e))
    sys.exit(1)
" 2>/dev/null || echo "Warning: $f may have syntax issues"
                            done
                        else
                            echo "python3 not available — skipping yaml validation"
                        fi
                    '''
                }
            }
        },

        // ── 1.5 Validate Docker Compose ───────────────────────
        'Validate Docker Compose': {
            stage('Validate Docker Compose') {
                script {
                    echo "Validating docker-compose.yaml..."

                    if (!fileExists('docker-compose.yaml') && !fileExists('docker-compose.yml')) {
                        echo "Warning — no docker-compose file found"
                        return
                    }

                    def exitCode = sh(
                        script: "docker compose config --quiet 2>/dev/null",
                        returnStatus: true
                    )

                    if (exitCode != 0) {
                        echo "Warning — docker-compose validation failed"
                    } else {
                        echo "docker-compose.yaml valid"
                    }
                }
            }
        }

    ) // end parallel

    // ── Notify pipeline started ───────────────────────────────
    notify.pipelineStarted()
}

