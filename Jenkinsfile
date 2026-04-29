pipeline {
    agent any

    environment {
        DOCKER_CREDS        = credentials('dockerhub')
        PIPELINE_START_TIME = ''
        CHANGED_SERVICES    = ''
        DETECTED_BRANCH     = ''
        GIT_AUTHOR          = ''
        GIT_AUTHOR_EMAIL    = ''
        SHORT_COMMIT        = ''
        FULL_COMMIT         = ''
        FAILED_STAGE        = ''
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
    }

    stages {

        stage('Pre-flight') {
            steps {
                script {
                    def pf = load 'jenkins/stages/preflight.groovy'
                    pf.run()
                }
            }
            post {
                failure {
                    script {
                        def notify = load 'jenkins/helpers/notify.groovy'
                        notify.stageFailed(
                            'Pre-flight',
                            'Branch name or commit message invalid'
                        )
                    }
                }
            }
        }

    } // end stages

    post {
        always {
            node('built-in') {
                script {
                    def duration = env.PIPELINE_START_TIME
                        ? ((System.currentTimeMillis() - env.PIPELINE_START_TIME.toLong()) / 1000).toInteger()
                        : 0
                    echo "Pipeline duration: ${duration}s"
                    cleanWs()
                }
            }
        }
        success {
            node('built-in') {
                script {
                    def notify = load 'jenkins/helpers/notify.groovy'
                    notify.pipelineSucceeded()
                }
            }
        }
        failure {
            node('built-in') {
                script {
                    def notify = load 'jenkins/helpers/notify.groovy'
                    notify.pipelineFailed()
                }
            }
        }
    }
}


// pipeline {
//     agent any

//     environment {
//         DOCKER_CREDS        = credentials('dockerhub')
//         PIPELINE_START_TIME = ''
//         CHANGED_SERVICES    = ''
//         DETECTED_BRANCH     = ''
//         GIT_AUTHOR          = ''
//         SHORT_COMMIT        = ''
//         FAILED_STAGE        = ''
//     }

//     options {
//         buildDiscarder(logRotator(numToKeepStr: '10'))
//         timeout(time: 60, unit: 'MINUTES')
//         timestamps()
//         disableConcurrentBuilds()
//     }

//     stages {

//         stage('Pre-flight') {
//             steps {
//                 script {
//                     def pf = load 'jenkins/stages/preflight.groovy'
//                     pf.run()
//                 }
//             }
//         }

//         stage('Secret Detection') {
//             steps {
//                 script {
//                     def sd = load 'jenkins/stages/secret-detection.groovy'
//                     sd.run()
//                 }
//             }
//         }

//         stage('Dependency Audit') {
//             steps {
//                 script {
//                     def da = load 'jenkins/stages/dependency-audit.groovy'
//                     da.run()
//                 }
//             }
//         }

//         stage('SAST') {
//             steps {
//                 script {
//                     def sast = load 'jenkins/stages/sast.groovy'
//                     sast.run()
//                 }
//             }
//         }

//         stage('Tests') {
//             steps {
//                 script {
//                     def tests = load 'jenkins/stages/tests.groovy'
//                     tests.run()
//                 }
//             }
//         }

//         stage('Build') {
//             steps {
//                 script {
//                     def build = load 'jenkins/stages/build.groovy'
//                     build.run()
//                 }
//             }
//         }

//         stage('Container Scan') {
//             steps {
//                 script {
//                     def cs = load 'jenkins/stages/container-scan.groovy'
//                     cs.run()
//                 }
//             }
//         }

//         stage('Infrastructure Scan') {
//             steps {
//                 script {
//                     def is = load 'jenkins/stages/infra-scan.groovy'
//                     is.run()
//                 }
//             }
//         }

//         stage('DAST') {
//             steps {
//                 script {
//                     def dast = load 'jenkins/stages/dast.groovy'
//                     dast.run()
//                 }
//             }
//         }

//         stage('Push') {
//             when {
//                 anyOf {
//                     branch 'main'
//                     branch 'develop'
//                 }
//             }
//             steps {
//                 script {
//                     def push = load 'jenkins/stages/push.groovy'
//                     push.run()
//                 }
//             }
//         }

//         stage('Deploy') {
//             when {
//                 anyOf {
//                     branch 'main'
//                     branch 'develop'
//                 }
//             }
//             steps {
//                 script {
//                     def deploy = load 'jenkins/stages/deploy.groovy'
//                     deploy.run()
//                 }
//             }
//         }

//         stage('Verify') {
//             steps {
//                 script {
//                     def verify = load 'jenkins/stages/verify.groovy'
//                     verify.run()
//                 }
//             }
//         }

//     }

//     post {
//         always {
//             node('built-in') {
//                 script {
//                     def duration = env.PIPELINE_START_TIME
//                         ? ((System.currentTimeMillis() - env.PIPELINE_START_TIME.toLong()) / 1000).toInteger()
//                         : 0
//                     echo "Pipeline duration: ${duration}s"
//                     cleanWs()
//                 }
//             }
//         }
//         success {
//             node('built-in') {
//                 script {
//                     def notify = load 'jenkins/helpers/notify.groovy'
//                     notify.slack(
//                         color: '#36a64f',
//                         title: 'Pipeline Passed',
//                         message: "Branch: ${env.DETECTED_BRANCH}\nDuration: ${((System.currentTimeMillis() - (env.PIPELINE_START_TIME?.toLong() ?: System.currentTimeMillis())) / 1000).toInteger()}s"
//                     )
//                 }
//             }
//         }
//         failure {
//             node('built-in') {
//                 script {
//                     def notify = load 'jenkins/helpers/notify.groovy'
//                     notify.slack(
//                         color: '#cc0000',
//                         title: 'Pipeline Failed',
//                         message: "Branch: ${env.DETECTED_BRANCH}\nFailed Stage: ${env.FAILED_STAGE ?: 'unknown'}"
//                     )
//                 }
//             }
//         }
//     }
// }

// pipeline {
//     agent any

//     environment {
//         DOCKER_CREDS        = credentials('dockerhub')
        
//         PIPELINE_START_TIME = ''
//         CHANGED_SERVICES    = ''
//     }

//     options {
//         buildDiscarder(logRotator(numToKeepStr: '10'))
//         timeout(time: 60, unit: 'MINUTES')
//         timestamps()
//         disableConcurrentBuilds()
//     }

//     stages {

//         // ── STAGE 1: PRE-FLIGHT ──────────────────────────────────
//         stage('Pre-flight') {
//             parallel {

//                 // ── 1.1 Validate Branch Name ─────────────────────
//                 stage('Validate Branch') {
//                     steps {
//                         script {
//                             // Try multiple ways to get branch name
//                             def branch = env.BRANCH_NAME
//                                 ?: env.GIT_BRANCH?.replaceFirst('origin/', '')
//                                 ?: sh(
//                                     script: "git name-rev --name-only HEAD 2>/dev/null | sed 's|remotes/origin/||'",
//                                     returnStdout: true
//                                 ).trim()

//                             // Clean up branch name
//                             branch = branch
//                                 ?.replaceFirst('refs/heads/', '')
//                                 ?.replaceFirst('origin/', '')
//                                 ?.trim()

//                             echo "Validating branch: ${branch}"

//                             // Skip validation for detached HEAD (CI systems)
//                             if (branch == 'HEAD' || branch?.startsWith('HEAD~')) {
//                                 echo "Detached HEAD detected — skipping branch validation"
//                                 env.DETECTED_BRANCH = 'unknown'
//                                 return
//                             }

//                             def valid = (
//                                 branch == 'main' ||
//                                 branch == 'develop' ||
//                                 branch.matches('^feature/[a-z0-9][a-z0-9\\-]{2,49}$') ||
//                                 branch.matches('^fix/[a-z0-9][a-z0-9\\-]{2,49}$') ||
//                                 branch.matches('^hotfix/[a-z0-9][a-z0-9\\-]{2,49}$') ||
//                                 branch.matches('^release/v\\d+\\.\\d+\\.\\d+$')
//                             )

//                             if (!valid) {
//                                 error """
// INVALID BRANCH NAME: '${branch}'

// Allowed patterns:
//   main
//   develop
//   feature/short-description
//   fix/short-description
//   hotfix/short-description
//   release/v1.0.0
//                                 """
//                             }

//                             env.DETECTED_BRANCH = branch
//                             echo "Branch name valid: ${branch}"
//                         }
//                     }
//                 }
//                 // ── 1.2 Validate Commit Message ───────────────────
//                 stage('Validate Commit') {
//                     steps {
//                         script {
//                             def commitMsg = sh(
//                                 script: "git log -1 --pretty=%B HEAD",
//                                 returnStdout: true
//                             ).trim()

//                             echo "Validating commit: ${commitMsg}"

//                             def isMerge   = commitMsg.startsWith('Merge ')
//                             def isInitial = commitMsg.startsWith('Initial')
//                             def isValid   = commitMsg.matches(
//                                 '^(feat|fix|docs|style|refactor|test|chore|ci|security|perf|build|revert)(\\([a-z0-9\\-]+\\))?: .{10,100}$'
//                             )

//                             if (!isMerge && !isInitial && !isValid) {
//                                 error """
// INVALID COMMIT MESSAGE: '${commitMsg}'

// Required format:
//   type(scope): description

// Allowed types:
//   feat      new feature
//   fix       bug fix
//   docs      documentation only
//   style     formatting no logic change
//   refactor  code restructure
//   test      adding tests
//   chore     maintenance tasks
//   ci        pipeline changes
//   security  security fix
//   perf      performance improvement
//   build     build system changes
//   revert    revert a commit

// Rules:
//   Description 10-100 characters
//   Scope optional but recommended

// Examples:
//   feat(auth): add JWT refresh token rotation
//   fix(payment): handle Kafka timeout gracefully
//   security(gateway): patch auth bypass vulnerability
//                                 """
//                             }
//                             echo "Commit message valid: ${commitMsg}"
//                         }
//                     }
//                 }

//                 // ── 1.3 Detect Changed Services ───────────────────
//                 stage('Detect Changes') {
//                     steps {
//                         script {
//                             echo "Detecting changed services..."

//                             def allServices = [
//                                 'user-service',
//                                 'order-service',
//                                 'inventory-service',
//                                 'payment-service',
//                                 'analytics-service',
//                                 'auth-service',
//                                 'notification-service',
//                                 'api-gateway',
//                                 'ui-service'
//                             ]

//                             def changedFiles = ''

//                             try {
//                                 changedFiles = sh(
//                                     script: "git diff --name-only HEAD~1 HEAD 2>/dev/null || echo ''",
//                                     returnStdout: true
//                                 ).trim()
//                             } catch (e) {
//                                 echo "Could not detect changes — building all services"
//                                 changedFiles = allServices.join('\n')
//                             }

//                             def forceAll = (
//                                 env.FORCE_ALL_SERVICES == 'true' ||
//                                 changedFiles.isEmpty() ||
//                                 env.BRANCH_NAME == 'main' ||
//                                 env.BRANCH_NAME == 'develop'
//                             )

//                             def changed = forceAll
//                                 ? allServices
//                                 : allServices.findAll { svc ->
//                                     changedFiles.contains(svc) ||
//                                     changedFiles.contains('Jenkinsfile') ||
//                                     changedFiles.contains('docker-compose')
//                                   }

//                             if (changed.isEmpty()) {
//                                 echo "No service changes detected"
//                                 changed = []
//                             }

//                             env.CHANGED_SERVICES    = changed.join(',')
//                             env.PIPELINE_START_TIME = System.currentTimeMillis().toString()

//                             echo """
// ============================================
//          PRE-FLIGHT SUMMARY
// ============================================
// Branch:   ${env.DETECTED_BRANCH ?: env.GIT_BRANCH ?: 'unknown'}
// Commit:   ${env.GIT_COMMIT?.take(7)}
// Author:   ${env.GIT_AUTHOR_NAME ?: sh(script: 'git log -1 --pretty=%an', returnStdout: true).trim()}
// --------------------------------------------
// Changed Services:
// ${changed.collect { "  -> ${it}" }.join('\n')}
// ============================================
//                             """
//                         }
//                     }
//                 }

//             } // end parallel

//             post {
//                 success {
//                     script {
//                         def changed = env.CHANGED_SERVICES?.split(',')?.toList() ?: []
//                         slackNotify(
//                             color: '#36a64f',
//                             title: 'Pipeline Started',
//                             message: """
// Branch:   ${env.BRANCH_NAME}
// Author:   ${env.GIT_AUTHOR_NAME}
// Commit:   ${env.GIT_COMMIT?.take(7)}
// Services: ${changed.join(', ') ?: 'none'}
//                             """
//                         )
//                     }
//                 }
//                 failure {
//                     script {
//                         slackNotify(
//                             color: '#cc0000',
//                             title: 'Pipeline Blocked — Pre-flight Failed',
//                             message: """
// Branch: ${env.BRANCH_NAME}
// Author: ${env.GIT_AUTHOR_NAME}
// Action: Fix branch name or commit message
//                             """
//                         )
//                     }
//                 }
//             }
//         } // end Pre-flight

//     } // end stages

//     post {
//         always {
//             node('built-in') {
//                 script {
//                     def duration = env.PIPELINE_START_TIME
//                         ? ((System.currentTimeMillis() - env.PIPELINE_START_TIME.toLong()) / 1000).toInteger()
//                         : 0
//                     echo "Pipeline duration: ${duration}s"
//                     cleanWs()
//                 }
//             }
//         }
//         success {
//             node('built-in') {
//                 script {
//                     slackNotify(
//                         color: '#36a64f',
//                         title: 'Pipeline Passed',
//                         message: "Branch: ${env.BRANCH_NAME}"
//                     )
//                 }
//             }
//         }
//         failure {
//             node('built-in') {
//                 script {
//                     slackNotify(
//                         color: '#cc0000',
//                         title: 'Pipeline Failed',
//                         message: "Branch: ${env.BRANCH_NAME}"
//                     )
//                 }
//             }
//         }
//     }
// } // end pipeline

// // ── HELPER: Slack Notification ────────────────────────────────
// def slackNotify(Map config) {
//     echo "NOTIFY: ${config.title} — ${config.message?.trim()}"
// }