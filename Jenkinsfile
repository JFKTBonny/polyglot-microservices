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

                    // Ensure repo is checked out
                    checkout scm

                    // Single git call (fast + atomic)
                    def gitInfo = sh(
                        returnStdout: true,
                        script: '''
                            git log -1 --pretty=format:"%an|%ae|%h|%H"
                        '''
                    ).trim().split("\\|")

                    def author = gitInfo[0]
                    def email  = gitInfo[1]
                    def shortC = gitInfo[2]
                    def fullC  = gitInfo[3]

                    // Use Jenkins-native branch detection FIRST
                    def branch = env.BRANCH_NAME ?: sh(
                        returnStdout: true,
                        script: 'git rev-parse --abbrev-ref HEAD'
                    ).trim()

                    // Fallback for detached HEAD
                    if (branch == "HEAD") {
                        branch = sh(
                            returnStdout: true,
                            script: 'git branch -r --contains HEAD | head -n 1 | sed "s|origin/||"'
                        ).trim()
                    }

                    // Export globally (ONLY env, no binding)
                    env.PIPELINE_BRANCH = branch
                    env.PIPELINE_AUTHOR = author
                    env.PIPELINE_EMAIL  = email
                    env.PIPELINE_COMMIT = shortC
                    env.PIPELINE_FULL   = fullC
                    env.PIPELINE_START  = System.currentTimeMillis().toString()

                    echo """
                    ─── PIPELINE INIT ───
                    Branch : ${env.PIPELINE_BRANCH}
                    Author : ${env.PIPELINE_AUTHOR}
                    Email  : ${env.PIPELINE_EMAIL}
                    Commit : ${env.PIPELINE_COMMIT}
                    ─────────────────────
                    """
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