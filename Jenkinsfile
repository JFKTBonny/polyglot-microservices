// pipeline {
//     agent any

//     environment {
//         DOCKER_CREDS    = credentials('dockerhub')
//         PIPELINE_START  = ''
//         FAILED_STAGE    = ''
//         GIT_BRANCH = ''
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
// Branch : ${env.GIT_BRANCH}
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
//                             'CRITICAL — Secrets GIT',
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
        GIT_BRANCH = ''
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
                    def config = [:]

                    def initStage = load 'jenkins/stages/init.groovy'
                    initStage(config)

                    // persist across nodes
                    stash name: 'pipeline-state', includes: 'jenkins/state/*'
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
                            'CRITICAL — Secrets GIT',
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
        always {
            script {
                try {
                    cleanWs()
                } catch (err) {
                    echo "Cleanup: ${err.message}"
                }
            }
        }
        success {
            script {
                def branch = 'unknown'
                def author = 'unknown'
                def commit = 'unknown'
                try {
                    if (fileExists('.pipeline-state')) {
                        def lines = readFile('.pipeline-state').split('\n')
                        for (int i = 0; i < lines.size(); i++) {
                            def idx = lines[i].indexOf('=')
                            if (idx > 0) {
                                def k = lines[i].substring(0, idx)
                                def v = lines[i].substring(idx + 1)
                                if (k == 'DETECTED_BRANCH') branch = v
                                if (k == 'GIT_AUTHOR')      author = v
                                if (k == 'SHORT_COMMIT')    commit = v
                            }
                        }
                    }
                } catch (e) {
                    echo "Could not read state: ${e.message}"
                }
                notify('Pipeline Passed', "Branch: ${branch}\nAuthor: ${author}\nCommit: ${commit}")
            }
        }
        failure {
            script {
                def branch = 'unknown'
                def author = 'unknown'
                try {
                    if (fileExists('.pipeline-state')) {
                        def lines = readFile('.pipeline-state').split('\n')
                        for (int i = 0; i < lines.size(); i++) {
                            def idx = lines[i].indexOf('=')
                            if (idx > 0) {
                                def k = lines[i].substring(0, idx)
                                def v = lines[i].substring(idx + 1)
                                if (k == 'DETECTED_BRANCH') branch = v
                                if (k == 'GIT_AUTHOR')      author = v
                            }
                        }
                    }
                } catch (e) {
                    echo "Could not read state: ${e.message}"
                }
                notify('Pipeline Failed', "Branch: ${branch}\nFailed Stage: ${env.FAILED_STAGE ?: 'unknown'}\nAuthor: ${author}")
            }
        }
    }
}


// =======================
// 🔧 SAFE HELPERS (FIXED)
// =======================


def safeState() {
        
        return state.load() ?: [branch: 'unknown', author: 'unknown', commit: 'unknown']
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


