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
                    // Test simple assignment
                    env.TEST_VAR = "hello_world"
                    echo "TEST_VAR: ${env.TEST_VAR}"

                    // Test with rawBranch value
                    env.TEST_BRANCH = rawBranch
                    echo "TEST_BRANCH: ${env.TEST_BRANCH}"

                    def rawAuthor = sh(returnStdout: true, script: 'git log -1 --pretty=format:%an || true').trim()
                    def rawEmail  = sh(returnStdout: true, script: 'git log -1 --pretty=format:%ae || true').trim()
                    def rawShort  = sh(returnStdout: true, script: 'git log -1 --pretty=format:%h || true').trim()
                    def rawFull   = sh(returnStdout: true, script: 'git log -1 --pretty=format:%H || true').trim()
                    def rawBranch = sh(returnStdout: true, script: '''
                        git name-rev --name-only HEAD 2>/dev/null \
                            | sed "s|remotes/origin/||" \
                            | sed "s|~.*||" \
                            || true
                    ''').trim()

                    echo "RAW branch: '${rawBranch}'"
                    echo "RAW author: '${rawAuthor}'"
                    echo "RAW commit: '${rawShort}'"

                    // Use currentBuild.description as workaround
                    // and store in global binding
                    binding.variables.PIPELINE_BRANCH  = rawBranch
                    binding.variables.PIPELINE_AUTHOR  = rawAuthor
                    binding.variables.PIPELINE_EMAIL   = rawEmail
                    binding.variables.PIPELINE_COMMIT  = rawShort
                    binding.variables.PIPELINE_FULL    = rawFull
                    binding.variables.PIPELINE_START   = System.currentTimeMillis().toString()

                    env.PIPELINE_BRANCH  = rawBranch
                    env.PIPELINE_AUTHOR  = rawAuthor
                    env.PIPELINE_EMAIL   = rawEmail
                    env.PIPELINE_COMMIT  = rawShort
                    env.PIPELINE_FULL    = rawFull
                    env.PIPELINE_START   = System.currentTimeMillis().toString()

                    echo "Branch:  ${env.PIPELINE_BRANCH}"
                    echo "Author:  ${env.PIPELINE_AUTHOR}"
                    echo "Commit:  ${env.PIPELINE_COMMIT}"
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