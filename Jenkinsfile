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
                    try {
                        def config = [:]

                        def initStage = load 'jenkins/stages/init.groovy'
                        initStage.call(config)

                        // stash only if file exists
                        if (fileExists('jenkins/state/pipeline-meta.json')) {
                            stash name: 'pipeline-state', includes: 'jenkins/state/*'
                        }

                        env.PIPELINE_START_TIME = System.currentTimeMillis().toString()

                    } catch (err) {
                        env.FAILED_STAGE = "Init"
                        throw err
                    }
                }
            }
        }

        stage('Init Metadata') {
            steps {
                script {
                    def state = load 'jenkins/helpers/state.groovy'

                    def meta = [
                        branch: sh(script: 'git rev-parse --abbrev-ref HEAD', returnStdout: true).trim(),
                        commit: sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim(),
                        author: sh(script: 'git log -1 --pretty=%an', returnStdout: true).trim()
                    ]

                    state.save(meta)
                }
            }
        }

        stage('Pre-flight') {
            steps {
                script {
                    try {
                        def pf = load 'jenkins/stages/preflight.groovy'
                        pf.execute()
                    } catch (err) {
                        env.FAILED_STAGE = "Pre-flight"
                        throw err
                    }
                }
            }
            post {
                failure {
                    script {
                        def s = safeState()
                        notify(
                            'Pre-flight Failed',
                            "Branch: ${s.branch}\nFix branch name or commit message"
                        )
                    }
                }
            }
        }

        stage('Secret Detection') {
            steps {
                script {
                    try {
                        def sd = load 'jenkins/stages/secret-detection.groovy'
                        sd.execute()
                    } catch (err) {
                        env.FAILED_STAGE = "Secret Detection"
                        throw err
                    }
                }
            }
            post {
                failure {
                    script {
                        def s = safeState()
                        notify(
                            'CRITICAL — Secrets Detected',
                            "Branch: ${s.branch}\nRotate credentials immediately"
                        )
                    }
                }
            }
        }

    }

    post {
        always {
            node('built-in') {
                script {
                    safeUnstash()

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
                    safeUnstash()
                    def s = safeState()

                    notify(
                        'Pipeline Passed',
                        """Branch: ${s.branch}
Author: ${s.author}
Commit: ${s.commit}"""
                    )
                }
            }
        }

        failure {
            node('built-in') {
                script {
                    safeUnstash()
                    def s = safeState()

                    notify(
                        'Pipeline Failed',
                        """Branch: ${s.branch}
Failed Stage: ${env.FAILED_STAGE}
Author: ${s.author}"""
                    )
                }
            }
        }
    }
}


// =======================
// 🔧 SAFE HELPERS
// =======================

def safeUnstash() {
    try {
        unstash 'pipeline-state'
    } catch (err) {
        echo "No stash found (pipeline likely failed early)"
    }
}

def safeState() {
    try {
        def state = load 'jenkins/helpers/state.groovy'
        return state.load()
    } catch (err) {
        return [branch: 'unknown', author: 'unknown', commit: 'unknown']
    }
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