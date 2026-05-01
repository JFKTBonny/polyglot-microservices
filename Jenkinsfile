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
                    env.DETECTED_BRANCH = env.BRANCH_NAME
                        ?: env.GIT_BRANCH?.replaceFirst('origin/', '')
                        ?: sh(
                            script: "git rev-parse --abbrev-ref HEAD",
                            returnStdout: true
                        ).trim()

                    env.GIT_AUTHOR = sh(
                        script: "git log -1 --pretty=%an",
                        returnStdout: true
                    ).trim()

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

                    echo "Branch:  ${env.DETECTED_BRANCH}"
                    echo "Author:  ${env.GIT_AUTHOR}"
                    echo "Commit:  ${env.SHORT_COMMIT}"
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