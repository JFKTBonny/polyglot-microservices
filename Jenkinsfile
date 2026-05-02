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
                    try {
                        // Write git info script to avoid pipe interpretation
                        writeFile file: 'get-git-info.sh', text: '''#!/bin/bash
echo "AUTHOR=$(git log -1 --pretty=format:%an)"
echo "EMAIL=$(git log -1 --pretty=format:%ae)"
echo "SHORT=$(git log -1 --pretty=format:%h)"
echo "FULL=$(git log -1 --pretty=format:%H)"
'''
                        def gitOut = sh(
                            returnStdout: true,
                            script: 'bash get-git-info.sh'
                        ).trim()

                        sh 'rm -f get-git-info.sh'

                        def gitMap = [:]
                        gitOut.split('\n').each { line ->
                            def parts = line.split('=', 2)
                            if (parts.size() == 2) gitMap[parts[0]] = parts[1]
                        }

                        def branch = env.BRANCH_NAME ?: sh(
                            returnStdout: true,
                            script: 'git rev-parse --abbrev-ref HEAD'
                        ).trim()

                        if (!branch || branch == 'HEAD') {
                            branch = sh(
                                returnStdout: true,
                                script: 'git branch -r --contains HEAD | head -n 1 | sed "s|origin/||" | tr -d " "'
                            ).trim()
                        }

                        env.DETECTED_BRANCH  = branch                ?: 'unknown'
                        env.GIT_AUTHOR       = gitMap['AUTHOR']      ?: 'unknown'
                        env.GIT_AUTHOR_EMAIL = gitMap['EMAIL']       ?: 'unknown'
                        env.SHORT_COMMIT     = gitMap['SHORT']       ?: 'unknown'
                        env.FULL_COMMIT      = gitMap['FULL']        ?: 'unknown'
                        env.PIPELINE_START   = System.currentTimeMillis().toString()

                        writeFile file: '.pipeline-state', text: """DETECTED_BRANCH=${env.DETECTED_BRANCH}
GIT_AUTHOR=${env.GIT_AUTHOR}
SHORT_COMMIT=${env.SHORT_COMMIT}
PIPELINE_START=${env.PIPELINE_START}"""

                        def gitOut = sh(
                            returnStdout: true,
                            script: 'bash get-git-info.sh'
                        ).trim()

                        sh 'rm -f get-git-info.sh'

                        // Debug
                        echo "gitOut: '${gitOut}'"
                        echo "gitOut lines: ${gitOut.split('\n').size()}"

                        def gitMap = [:]
                        gitOut.split('\n').each { line ->
                            echo "line: '${line}'"
                            def parts = line.split('=', 2)
                            echo "parts: ${parts.size()}"
                            if (parts.size() == 2) gitMap[parts[0]] = parts[1]
                        }

                        echo "gitMap: ${gitMap}"

                        stash name: 'pipeline-state', includes: '.pipeline-state'

                        echo """
─── PIPELINE INIT ───────────────────
Branch : ${env.DETECTED_BRANCH}
Author : ${env.GIT_AUTHOR}
Commit : ${env.SHORT_COMMIT}
─────────────────────────────────────
                        """
                    } catch (err) {
                        env.FAILED_STAGE = 'Init'
                        throw err
                    }
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
    def state = [
        branch: env.DETECTED_BRANCH ?: 'unknown',
        commit: env.SHORT_COMMIT    ?: 'unknown',
        author: env.GIT_AUTHOR      ?: 'unknown'
    ]
    try {
        unstash 'pipeline-state'
        if (fileExists('.pipeline-state')) {
            readFile('.pipeline-state').split('\n').each { line ->
                def parts = line.split('=', 2)
                if (parts.size() == 2) {
                    if (parts[0] == 'DETECTED_BRANCH') state.branch = parts[1]
                    if (parts[0] == 'GIT_AUTHOR')      state.author = parts[1]
                    if (parts[0] == 'SHORT_COMMIT')    state.commit = parts[1]
                }
            }
        }
    } catch (e) {
        echo "safeState: using env vars — ${e.message}"
    }
    return state
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


