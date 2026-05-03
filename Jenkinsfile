



pipeline {
    agent any

    environment {
        DOCKER_CREDS        = credentials('dockerhub')
        PIPELINE_START_TIME = ''
        FAILED_STAGE        = ''

        DETECTED_BRANCH = ''
        SHORT_COMMIT    = ''
        GIT_AUTHOR      = ''

        PATH = "/var/lib/jenkins/bin:${env.PATH}"   // ✅ FIX
    }
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
    }

    stages {

        // =======================
        // INIT
        // =======================
        stage('Init') {
            steps {
                script {
                    def config = [:]

                    def initStage = load 'jenkins/stages/init.groovy'
                    initStage(config)

                    echo "Saved config: branch=${config.branch}, author=${config.author}, commit=${config.commit}"

                    sh 'ls -lah jenkins/state || true'
                    sh 'test -f jenkins/state/pipeline-meta.json && echo "pipeline-meta.json exists"'

                    stash name: 'pipeline-state', includes: 'jenkins/state/pipeline-meta.json'
                }
            }
        }

        // =======================
        // INIT METADATA (FIXED)
        // =======================
        
           

        // =======================
        // PRE-FLIGHT
        // =======================
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

        // =======================
        // SECRET DETECTION
        // =======================
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
    

        stage('Dependency Audit') {
                steps {
                    script {
                        try {
                            def da = load 'jenkins/stages/dependency-audit.groovy'
                            da.execute()
                        } catch (err) {
                            env.FAILED_STAGE = 'Dependency Audit'
                            throw err
                        }
                    }
                }
                post {
                    failure {
                        script {
                            notify('Dependency Audit Failed',
                                "Branch: ${env.DETECTED_BRANCH ?: 'unknown'}\nCritical vulnerabilities found")
                        }
                    }
                }
        }

        stage('SAST') {
            steps {
                script {
                    try {
                        def sast = load 'jenkins/stages/sast.groovy'
                        sast.execute()
                    } catch (err) {
                        env.FAILED_STAGE = 'SAST'
                        throw err
                    }
                }
            }
            post {
                failure {
                    script {
                        notify('SAST Failed',
                            "Branch: ${env.DETECTED_BRANCH ?: 'unknown'}\nHigh/Critical findings detected")
                    }
                }
            }
        }

        stage('Code Quality') {
            steps {
                script {
                    try {
                        def cq = load 'jenkins/stages/code-quality.groovy'
                        cq.execute()
                    } catch (err) {
                        env.FAILED_STAGE = 'Code Quality'
                        throw err
                    }
                }
            }
            post {
                failure {
                    script {
                        notify('Code Quality Failed',
                            "Branch: ${env.DETECTED_BRANCH ?: 'unknown'}\nQuality gate not met")
                    }
                }
            }
        }

        stage('Tests') {
            steps {
                script {
                    try {
                        def t = load 'jenkins/stages/tests.groovy'
                        t.execute()
                    } catch (err) {
                        env.FAILED_STAGE = 'Tests'
                        throw err
                    }
                }
            }
            post {
                failure {
                    script {
                        notify('Tests Failed',
                            "Branch: ${env.DETECTED_BRANCH ?: 'unknown'}\nTest suite failed")
                    }
                }
            }
        }

        stage('Build') {
            steps {
                script {
                    try {
                        def b = load 'jenkins/stages/build.groovy'
                        b.execute()
                    } catch (err) {
                        env.FAILED_STAGE = 'Build'
                        throw err
                    }
                }
            }
            post {
                failure {
                    script {
                        notify('Build Failed',
                            "Branch: ${env.DETECTED_BRANCH ?: 'unknown'}\nDocker build failed")
                    }
                }
            }
        }

        stage('Container Scan') {
            steps {
                script {
                    try {
                        def cs = load 'jenkins/stages/container-scan.groovy'
                        cs.execute()
                    } catch (err) {
                        env.FAILED_STAGE = 'Container Scan'
                        throw err
                    }
                }
            }
            post {
                failure {
                    script {
                        notify('Container Scan Failed',
                            "Branch: ${env.DETECTED_BRANCH ?: 'unknown'}\nContainer scan stage failed")
                    }
                }
            }
        }

        stage('Infra Scan') {
            steps {
                script {
                    try {
                        def is = load 'jenkins/stages/infra-scan.groovy'
                        is.execute()
                    } catch (err) {
                        env.FAILED_STAGE = 'Infra Scan'
                        throw err
                    }
                }
            }
            post {
                failure {
                    script {
                        notify('Infra Scan Failed',
                            "Branch: ${env.DETECTED_BRANCH ?: 'unknown'}\nInfrastructure scan failed")
                    }
                }
            }
        }


        stage('DAST') {
            steps {
                script {
                    try {
                        def dast = load 'jenkins/stages/dast.groovy'
                        dast.execute()
                    } catch (err) {
                        env.FAILED_STAGE = 'DAST'
                        // Always cleanup on failure
                        sh '''
                            docker ps -a --filter "name=dast-" --format "{{.Names}}" | \
                                xargs -r docker rm -f 2>/dev/null || true
                            docker network rm dast-net 2>/dev/null || true
                        '''
                        throw err
                    }
                }
            }
        }
    }
    // =======================
    // POST
    // =======================
    post {
        success {
            node('built-in') {
                script {
                    def s = getSafeState()
                    notify("✅ Pipeline Passed", "Branch: ${s.branch}\nAuthor: ${s.author}")
                }
            }
        }
        failure {
            node('built-in') {
                script {
                    def s = getSafeState()
                    notify("❌ Pipeline Failed", "Branch: ${s.branch}\nFailed at: ${env.STAGE_NAME}")
                }
            }
        }
        always {
            node('built-in') {
                script {
                    // This must match the function name defined below
                    logPipelineDuration() 
                    // cleanWs()
                }
            }
        }
    }
}





// =======================
// 🔧 SAFE HELPERS (FIXED)
// =======================
import groovy.json.JsonSlurper

def getSafeState() {
    def state = [branch: 'unknown', author: 'unknown', commit: 'unknown']
    try {
        unstash 'pipeline-state'
        def jsonPath = "jenkins/state/pipeline-meta.json"
        if (fileExists(jsonPath)) {
            def json = new JsonSlurper().parseText(readFile(jsonPath))
            state.branch = json.branch ?: state.branch
            state.author = json.author ?: state.author
            state.commit = json.commit ?: state.commit
        }
    } catch (e) { echo "⚠️ State fetch failed: ${e.message}" }
    return state
}

def notify(String title, String message) {
    echo "════════════════════════════════════\n  ${title}\n════════════════════════════════════\n${message?.trim()}\n════════════════════════════════════"
}

// MAKE SURE THIS NAME MATCHES YOUR POST BLOCK CALL
def logPipelineDuration() {
    try {
        if (currentBuild.startTimeInMillis) {
            def duration = ((System.currentTimeMillis() - currentBuild.startTimeInMillis) / 1000).toInteger()
            echo "⏱️ Pipeline duration: ${duration}s"
        }
    } catch (e) { echo "⏱️ Duration: N/A" }
}