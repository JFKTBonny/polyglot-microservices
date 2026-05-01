// pipeline {
//     agent any

//     environment {
//         DOCKER_CREDS    = credentials('dockerhub')
//         PIPELINE_START  = ''
//         FAILED_STAGE    = ''
//         DETECTED_BRANCH = ''
//         SHORT_COMMIT    = ''
//         GIT_AUTHOR      = ''
//         CHANGED_SERVICES = ''
//         PATH            = "/var/lib/jenkins/bin:${env.PATH}"
//     }

//     options {
//         buildDiscarder(logRotator(numToKeepStr: '10'))
//         timeout(time: 60, unit: 'MINUTES')
//         timestamps()
//         disableConcurrentBuilds()
//     }

//     stages {

//         // ── INIT ──────────────────────────────────────────────
//         stage('Init') {
//             steps {
//                 script {
//                     try {
//                         def config = [:]

//                         def initStage = load 'jenkins/stages/init.groovy'
//                         initStage.call(config)

//                         env.PIPELINE_START_TIME = System.currentTimeMillis().toString()


//                         echo """
// ════════════════════════════════════
//   Pipeline Init
// ════════════════════════════════════
// Branch : ${env.DETECTED_BRANCH}
// Author : ${env.GIT_AUTHOR}
// Commit : ${env.SHORT_COMMIT}
// ════════════════════════════════════
//                         """

//                     } catch (err) {
//                         env.FAILED_STAGE = "Init"
//                         throw err
//                     }
//                 }
//             }
//         }

                        
                    

//         // ── PRE-FLIGHT ────────────────────────────────────────
//         stage('Pre-flight') {
//             steps {
//                 script {
//                     try {
//                         def pf = load 'jenkins/stages/preflight.groovy'
//                         pf.execute()
//                     } catch (err) {
//                         env.FAILED_STAGE = 'Pre-flight'
//                         throw err
//                     }
//                 }
//             }
//             post {
//                 failure {
//                     script {
//                         notify(
//                             'Pre-flight Failed',
//                             "Branch: ${safeState().branch}\nFix branch name or commit message"
//                         )
//                     }
//                 }
//             }
//         }

//         // ── SECRET DETECTION ──────────────────────────────────
//         stage('Secret Detection') {
//             steps {
//                 script {
//                     try {
//                         def sd = load 'jenkins/stages/secret-detection.groovy'
//                         sd.execute()
//                     } catch (err) {
//                         env.FAILED_STAGE = 'Secret Detection'
//                         throw err
//                     }
//                 }
//             }
//             post {
//                 failure {
//                     script {
//                         notify(
//                             'CRITICAL — Secrets Detected',
//                             "Branch: ${safeState().branch}\nRotate credentials immediately"
//                         )
//                     }
//                 }
//             }
//         }

//     } // end stages

//     // ── POST ──────────────────────────────────────────────────
//     post {
//         always {
//             node('built-in') {
//                 script {
//                     try {
//                         def duration = env.PIPELINE_START
//                             ? ((System.currentTimeMillis() - env.PIPELINE_START.toLong()) / 1000).toInteger()
//                             : 0
//                         echo "Pipeline duration: ${duration}s"
//                         cleanWs()
//                     } catch (err) {
//                         echo "Cleanup error: ${err.message}"
//                     }
//                 }
//             }
//         }
//         success {
//             node('built-in') {
//                 script {
//                     def s = safeState()
//                     notify(
//                         'Pipeline Passed',
//                  """Branch: ${s.branch}
//                     Author: ${s.author}
//                     Commit: ${s.commit}"""
//                     )
//                 }
//             }
//         } 
//         failure {
//             node('built-in') {
//                 script {
//                         def s = safeState()
//                         notify(
//                             'Pipeline Passed',
//                      """Branch: ${s.branch}
//                         Author: ${s.author}
//                         Commit: ${s.commit}"""
//                         )
//                 }
//             }
//         }
//     }

// } // end pipeline

// // =======================
// // 🔧 SAFE HELPERS
// // =======================

// // def safeUnstash() {
// //     try {
// //         unstash 'pipeline-state'
// //     } catch (err) {
// //         echo "No stash found (pipeline likely failed early)"
// //     }
// // }

// def safeState() {
//     try {
//         def state = load 'jenkins/helpers/state.groovy'
//         return state.load()
//     } catch (err) {
//         return [branch: 'unknown', author: 'unknown', commit: 'unknown']
//     }
// }

// def notify(String title, String message) {
//     echo """
// ════════════════════════════════════
//   ${title}
// ════════════════════════════════════
// ${message?.trim()}
// ════════════════════════════════════
// """
// }



pipeline {
    agent any

    environment {
        DOCKER_CREDS        = credentials('dockerhub')
        PIPELINE_START_TIME = ''
        FAILED_STAGE        = ''

        // ✅ persistent metadata (cross-stage safe)
        DETECTED_BRANCH = ''
        SHORT_COMMIT    = ''
        GIT_AUTHOR      = ''
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
                    try {
                        def config = [:]

                        def initStage = load 'jenkins/stages/init.groovy'
                        initStage.call(config)

                        env.PIPELINE_START_TIME = System.currentTimeMillis().toString()

                    } catch (err) {
                        env.FAILED_STAGE = "Init"
                        throw err
                    }
                }
            }
        }

        // =======================
        // INIT METADATA (FIXED)
        // =======================
        stage('Init Metadata') {
            steps {
                script {
                    def meta = [
                        branch: sh(script: 'git rev-parse --abbrev-ref HEAD', returnStdout: true).trim(),
                        commit: sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim(),
                        author: sh(script: 'git log -1 --pretty=%an', returnStdout: true).trim()
                    ]

                    // ✅ save to file
                    def state = load 'jenkins/helpers/state.groovy'
                    state.save(meta)

                    // ✅ ALSO save to env (CRITICAL FIX)
                    env.DETECTED_BRANCH = meta.branch
                    env.SHORT_COMMIT    = meta.commit
                    env.GIT_AUTHOR      = meta.author

                    echo "Metadata initialized:"
                    echo "Branch: ${env.DETECTED_BRANCH}"
                    echo "Commit: ${env.SHORT_COMMIT}"
                    echo "Author: ${env.GIT_AUTHOR}"
                }
            }
        }

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

    }

    // =======================
    // POST
    // =======================
    post {

        success {
            node('built-in') {
                script {
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

        always {
            node('built-in') {
                script {
                    def duration = env.PIPELINE_START_TIME
                        ? ((System.currentTimeMillis() - env.PIPELINE_START_TIME.toLong()) / 1000).toInteger()
                        : 0

                    echo "Pipeline duration: ${duration}s"

                    // ✅ NOW VALID
                    cleanWs()
                }
            }
        }    
    }
}


// =======================
// 🔧 SAFE HELPERS (FIXED)
// =======================

def safeState() {
    try {
        def state = load 'jenkins/helpers/state.groovy'
        def s = state.load()

        return [
            branch: s.branch ?: env.DETECTED_BRANCH ?: 'unknown',
            author: s.author ?: env.GIT_AUTHOR ?: 'unknown',
            commit: s.commit ?: env.SHORT_COMMIT ?: 'unknown'
        ]
    } catch (err) {
        return [
            branch: env.DETECTED_BRANCH ?: 'unknown',
            author: env.GIT_AUTHOR ?: 'unknown',
            commit: env.SHORT_COMMIT ?: 'unknown'
        ]
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


