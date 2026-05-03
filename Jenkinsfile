



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
        // Use 'built-in' only if the notification logic requires it
        node('built-in') {
            script {
                // Use a safe getter to prevent script termination on nulls
                def s = getSafeState()
                notify("✅ Pipeline Passed", 
                    "Branch: ${s.branch}\nAuthor: ${s.author}\nCommit: ${s.commit}")
            }
        }
    }

    failure {
        node('built-in') {
            script {
                def s = getSafeState()
                // env.STAGE_NAME is a built-in Jenkins variable
                def failedAt = env.STAGE_NAME ?: "Unknown Stage"
                
                notify("❌ Pipeline Failed", 
                    "Branch: ${s.branch}\nFailed at: ${failedAt}\nAuthor: ${s.author}")
            }
        }
    }

    always {
        // Always clean up to prevent disk space issues
        node('built-in') {
            script {
                calculateAndLogDuration()
                cleanWs(deleteDirs: false, disableDeferredWipeout: false)
            }
        }
    }
}
}





// =======================
// 🔧 SAFE HELPERS (FIXED)
// =======================

import groovy.json.JsonSlurper

/**
 * Safely retrieves pipeline metadata by unstashing and parsing the state JSON.
 * Designed to be called within a script block inside post-actions.
 */
def getSafeState() {
    def state = [
        branch: 'unknown',
        author: 'unknown',
        commit: 'unknown'
    ]

    try {
        // Unstash the file into the current node's workspace
        unstash 'pipeline-state'
        
        def jsonPath = "jenkins/state/pipeline-meta.json"
        if (fileExists(jsonPath)) {
            def fileContent = readFile(jsonPath)
            def json = new JsonSlurper().parseText(fileContent)
            
            state.branch = json.branch ?: state.branch
            state.author = json.author ?: state.author
            state.commit = json.commit ?: state.commit
        }
    } catch (Exception e) {
        // If Init stage failed or stash is missing, we log a warning but don't break the build
        echo "⚠️ Helper: Could not retrieve pipeline-state stash. Using defaults. (${e.message})"
    }
    
    return state
}

/**
 * Formats and prints a consistent notification block to the console.
 */
def notify(String title, String message) {
    echo """
══════════════════════════════════════════════════════
  ${title}
══════════════════════════════════════════════════════
${message?.trim()}
══════════════════════════════════════════════════════
"""
}

/**
 * Calculates build duration using Jenkins built-in timing metadata.
 */
def logPipelineDuration() {
    try {
        if (currentBuild.startTimeInMillis) {
            def durationMs = System.currentTimeMillis() - currentBuild.startTimeInMillis
            def durationSec = (durationMs / 1000).toInteger()
            echo "⏱️ Total Pipeline Duration: ${durationSec}s"
        }
    } catch (e) {
        echo "⏱️ Total Pipeline Duration: N/A"
    }
}
