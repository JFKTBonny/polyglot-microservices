pipeline {
    agent any

    environment {
        DOCKER_USERNAME     = credentials('DOCKER_USERNAME')
        DOCKER_PASSWORD     = credentials('DOCKER_PASSWORD')
        SLACK_WEBHOOK       = credentials('SLACK_WEBHOOK')
        PIPELINE_START_TIME = ''
        CHANGED_SERVICES    = ''
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
        ansiColor('xterm')
        timestamps()
        disableConcurrentBuilds()
    }

    stages {

        // ── STAGE 1: PRE-FLIGHT ──────────────────────────────────
        stage('Pre-flight') {
            parallel {

                // ── 1.1 Validate Branch Name ─────────────────────
                stage('Validate Branch') {
                    steps {
                        script {
                            def branch = env.BRANCH_NAME ?: env.GIT_BRANCH?.replaceFirst('origin/', '')
                            echo "🔍 Validating branch: ${branch}"

                            def allowed = [
                                /^main$/,
                                /^develop$/,
                                /^feature\/[a-z0-9][a-z0-9\-]{2,49}$/,
                                /^fix\/[a-z0-9][a-z0-9\-]{2,49}$/,
                                /^hotfix\/[a-z0-9][a-z0-9\-]{2,49}$/,
                                /^release\/v\d+\.\d+\.\d+$/
                            ]

                            def valid = allowed.any { pattern ->
                                branch ==~ pattern
                            }

                            if (!valid) {
                                def msg = """
❌ INVALID BRANCH NAME: '${branch}'

Allowed patterns:
  main
  develop
  feature/short-description
  fix/short-description
  hotfix/short-description
  release/v1.0.0

Rules:
  - Lowercase only
  - Hyphens allowed (no underscores)
  - Minimum 3 characters after prefix
  - Maximum 50 characters after prefix

Examples:
  ✅ feature/jwt-refresh-token
  ✅ fix/payment-timeout
  ✅ hotfix/critical-auth-bypass
  ❌ Feature/Something
  ❌ my-branch
  ❌ WIP
                                """
                                error(msg)
                            }
                            echo "✅ Branch name valid: ${branch}"
                        }
                    }
                }

                // ── 1.2 Validate Commit Message ───────────────────
                stage('Validate Commit') {
                    steps {
                        script {
                            def commitMsg = sh(
                                script: "git log -1 --pretty=%B HEAD",
                                returnStdout: true
                            ).trim()

                            echo "🔍 Validating commit message: ${commitMsg}"

                            // Conventional commits pattern
                            def pattern = /^(feat|fix|docs|style|refactor|test|chore|ci|security|perf|build|revert)(\([a-z0-9\-]+\))?: .{10,100}$/

                            // Allow merge commits
                            def isMerge = commitMsg.startsWith('Merge ')

                            if (!isMerge && !(commitMsg ==~ pattern)) {
                                def msg = """
❌ INVALID COMMIT MESSAGE

Your message: '${commitMsg}'

Required format:
  type(scope): description

Allowed types:
  feat      → new feature
  fix       → bug fix
  docs      → documentation only
  style     → formatting, no logic change
  refactor  → code restructure
  test      → adding tests
  chore     → maintenance tasks
  ci        → pipeline changes
  security  → security fix
  perf      → performance improvement
  build     → build system changes
  revert    → revert a commit

Rules:
  - Description: 10-100 characters
  - Scope is optional but recommended
  - Use imperative mood

Examples:
  ✅ feat(auth): add JWT refresh token rotation
  ✅ fix(payment): handle Kafka timeout gracefully
  ✅ security(api-gateway): patch auth bypass vulnerability
  ✅ ci(jenkins): add DAST stage to pipeline
  ❌ "fixed stuff"
  ❌ "WIP"
  ❌ "update"
                                """
                                error(msg)
                            }
                            echo "✅ Commit message valid: ${commitMsg}"
                        }
                    }
                }

                // ── 1.3 Detect Changed Services ───────────────────
                stage('Detect Changes') {
                    steps {
                        script {
                            echo "🔍 Detecting changed services..."

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
                                    script: "git diff --name-only HEAD~1 HEAD 2>/dev/null || git diff --name-only HEAD 2>/dev/null || echo ''",
                                    returnStdout: true
                                ).trim()
                            } catch (e) {
                                echo "⚠️ Could not detect changes — building all services"
                                changedFiles = allServices.join('\n')
                            }

                            // First commit or force run — build all
                            def forceAll = (
                                env.FORCE_ALL_SERVICES == 'true' ||
                                changedFiles.isEmpty() ||
                                env.BRANCH_NAME == 'main' ||
                                env.BRANCH_NAME == 'develop'
                            )

                            def changed = forceAll
                                ? allServices
                                : allServices.findAll { svc ->
                                    changedFiles.contains(svc) ||
                                    changedFiles.contains('.github') ||
                                    changedFiles.contains('Jenkinsfile') ||
                                    changedFiles.contains('docker-compose')
                                  }

                            if (changed.isEmpty()) {
                                echo "ℹ️ No service changes detected — pipeline will run pre-flight only"
                                changed = []
                            }

                            env.CHANGED_SERVICES = changed.join(',')
                            env.PIPELINE_START_TIME = System.currentTimeMillis().toString()

                            echo """
╔══════════════════════════════════════════╗
║         PRE-FLIGHT SUMMARY               ║
╠══════════════════════════════════════════╣
║ Branch:   ${env.BRANCH_NAME?.padRight(30)}║
║ Commit:   ${env.GIT_COMMIT?.take(7)?.padRight(30)}║
║ Author:   ${env.GIT_AUTHOR_NAME?.take(30)?.padRight(30)}║
╠══════════════════════════════════════════╣
║ Changed Services:                        ║
${changed.collect { "║   → ${it.padRight(37)}║" }.join('\n')}
╚══════════════════════════════════════════╝
                            """
                        }
                    }
                }

            } // end parallel

            post {
                success {
                    script {
                        def changed = env.CHANGED_SERVICES?.split(',')?.toList() ?: []
                        slackNotify(
                            color: '#36a64f',
                            title: '🚀 Pipeline Started',
                            fields: [
                                [title: 'Branch',    value: env.BRANCH_NAME,    short: true],
                                [title: 'Author',    value: env.GIT_AUTHOR_NAME, short: true],
                                [title: 'Commit',    value: env.GIT_COMMIT?.take(7), short: true],
                                [title: 'Services',  value: changed.join(', ') ?: 'none', short: false]
                            ]
                        )
                    }
                }
                failure {
                    script {
                        slackNotify(
                            color: '#cc0000',
                            title: '❌ Pipeline Blocked — Pre-flight Failed',
                            fields: [
                                [title: 'Branch', value: env.BRANCH_NAME, short: true],
                                [title: 'Author', value: env.GIT_AUTHOR_NAME, short: true],
                                [title: 'Action', value: 'Fix branch name or commit message', short: false]
                            ]
                        )
                        error "Pre-flight failed — pipeline blocked"
                    }
                }
            }
        } // end Pre-flight

    } // end stages

    post {
        always {
            script {
                def duration = env.PIPELINE_START_TIME
                    ? ((System.currentTimeMillis() - env.PIPELINE_START_TIME.toLong()) / 1000).toInteger()
                    : 0

                echo "Pipeline duration: ${duration}s"
                cleanWs()
            }
        }
        success {
            script {
                slackNotify(
                    color: '#36a64f',
                    title: '✅ Pipeline Passed',
                    fields: [
                        [title: 'Branch',   value: env.BRANCH_NAME, short: true],
                        [title: 'Duration', value: "${((System.currentTimeMillis() - (env.PIPELINE_START_TIME?.toLong() ?: System.currentTimeMillis())) / 1000).toInteger()}s", short: true]
                    ]
                )
            }
        }
        failure {
            script {
                slackNotify(
                    color: '#cc0000',
                    title: '❌ Pipeline Failed',
                    fields: [
                        [title: 'Branch', value: env.BRANCH_NAME, short: true],
                        [title: 'Stage',  value: env.FAILED_STAGE ?: 'Unknown', short: true]
                    ]
                )
            }
        }
    }

} // end pipeline

// ── HELPER: Slack Notification ────────────────────────────────
def slackNotify(Map config) {
    try {
        def payload = groovy.json.JsonOutput.toJson([
            attachments: [[
                color:  config.color,
                title:  config.title,
                fields: config.fields,
                footer: "Jenkins | ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                ts:     (System.currentTimeMillis() / 1000).toInteger()
            ]]
        ])

        sh """
            curl -s -X POST '${env.SLACK_WEBHOOK}' \
                -H 'Content-Type: application/json' \
                -d '${payload}' || true
        """
    } catch (e) {
        echo "⚠️ Slack notification failed: ${e.message}"
    }
}