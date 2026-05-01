pipeline {
    agent any

    environment {
        DOCKER_CREDS        = credentials('dockerhub')
        PIPELINE_START_TIME = ''
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
                    def config = [:]
                    load 'jenkins/stages/init.groovy'.call(config)

                    // Persist state across nodes
                    stash name: 'pipeline-state', includes: 'jenkins/state/*'

                    env.PIPELINE_START_TIME = System.currentTimeMillis().toString()
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
                        def s = getState()
                        notify(
                            'Pre-flight Failed',
                            """Branch: ${s.branch ?: 'unknown'}
Fix branch name or commit message"""
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
                        def s = getState()
                        notify(
                            'CRITICAL — Secrets Detected',
                            """Branch: ${s.branch ?: 'unknown'}
Rotate credentials immediately"""
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
                    // Restore state if needed
                    unstash 'pipeline-state'

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
                    unstash 'pipeline-state'
                    def s = getState()

                    notify(
                        'Pipeline Passed',
                        """Branch: ${s.branch ?: 'unknown'}
Author: ${s.author ?: 'unknown'}
Commit: ${s.commit ?: 'unknown'}"""
                    )
                }
            }
        }

        failure {
            node('built-in') {
                script {
                    unstash 'pipeline-state'
                    def s = getState()

                    notify(
                        'Pipeline Failed',
                        """Branch: ${s.branch ?: 'unknown'}
Failed Stage: ${env.FAILED_STAGE ?: 'unknown'}
Author: ${s.author ?: 'unknown'}"""
                    )
                }
            }
        }
    }

} // end pipeline


// =======================
// 🔧 HELPERS
// =======================

def getState() {
    def state = load 'jenkins/helpers/state.groovy'
    return state.load()
}

def notify(String title, String message) {
    echo """
════════════════════════════════════
  ${title}
════════════════════════════════════
${message?.trim()}
════════════════════════════════════
    """
}