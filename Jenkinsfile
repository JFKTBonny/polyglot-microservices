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

        stage('Init') {
            steps {
                script {
                    // Explicit checkout to branch (fixes detached HEAD, enables git log/branch)
                    def branchName = params.BRANCH ?: env.BRANCH_NAME ?: 'main'  // Fallback
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: "*/${branchName}"]],
                        extensions: [
                            [$class: 'CloneOption', shallow: true, depth: 10],  // Shallow history for log
                            [$class: 'LocalBranch']  // Creates local branch ref
                        ],
                        userRemoteConfigs: [[url: env.GIT_URL ?: scm.userRemoteConfigs[0]?.url]]
                    ])

                    // Now git commands work
                    env.DETECTED_BRANCH = sh(script: "git branch --show-current || git rev-parse --abbrev-ref HEAD || echo 'unknown'", returnStdout: true).trim()
                    env.GIT_AUTHOR = sh(script: "git log -1 --pretty='%an' || echo 'unknown'", returnStdout: true).trim()
                    env.GIT_AUTHOR_EMAIL = sh(script: "git log -1 --pretty='%ae' || echo 'unknown'", returnStdout: true).trim()
                    env.SHORT_COMMIT = sh(script: "git log -1 --pretty='%h' || echo 'unknown'", returnStdout: true).trim()
                    env.FULL_COMMIT = sh(script: "git rev-parse HEAD", returnStdout: true).trim()

                    // Changed services (now reliable)
                    def baseBranch = (env.DETECTED_BRANCH == 'main') ? 'origin/develop' : 'origin/main'
                    sh "git fetch origin ${baseBranch} || git fetch origin main || true"
                    def changed = sh(script: """
                        git diff --name-only \$(git merge-base ${baseBranch} HEAD || echo HEAD~1) HEAD 2>/dev/null | grep -E '^(services/[^/]+)/' || true
                    """, returnStdout: true).trim()
                    env.CHANGED_SERVICES = changed ?: 'none'

                    env.PIPELINE_START_TIME = System.currentTimeMillis().toString()

                    echo "Branch:  ${env.DETECTED_BRANCH}"
                    echo "Author:  ${env.GIT_AUTHOR} <${env.GIT_AUTHOR_EMAIL}>"
                    echo "Commit:  ${env.SHORT_COMMIT}"
                    echo "Changed: ${env.CHANGED_SERVICES}"
                }
            }
        }

        stage('Pre-flight') {
            steps {
                script {
                    def pf = load 'jenkins/stages/preflight.groovy'
                    pf.execute()
                }
            }
            post {
                failure {
                    script {
                        notify(
                            'Pre-flight Failed',
                            "Branch: ${env.DETECTED_BRANCH}\nFix branch name or commit message"
                        )
                    }
                }
            }
        }

        stage('Secret Detection') {
            steps {
                script {
                    def sd = load 'jenkins/stages/secret-detection.groovy'
                    sd.execute()
                }
            }
            post {
                failure {
                    script {
                        notify(
                            'CRITICAL — Secrets Detected',
                            "Branch: ${env.DETECTED_BRANCH}\nRotate credentials immediately"
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
                    notify(
                        'Pipeline Passed',
                        "Branch: ${env.DETECTED_BRANCH}\nAuthor: ${env.GIT_AUTHOR}\nCommit: ${env.SHORT_COMMIT}"
                    )
                }
            }
        }
        failure {
            node('built-in') {
                script {
                    notify(
                        'Pipeline Failed',
                        "Branch: ${env.DETECTED_BRANCH}\nFailed Stage: ${env.FAILED_STAGE}\nAuthor: ${env.GIT_AUTHOR}"
                    )
                }
            }
        }
    }

} // end pipeline

def notify(String title, String message) {
    echo """
════════════════════════════════════
  ${title}
════════════════════════════════════
${message?.trim()}
════════════════════════════════════
    """
}