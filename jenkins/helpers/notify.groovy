def send(String title, String message) {
    echo "[$title] ${message?.trim()}"
}

// Helper to safely read env vars
def val(name, fallback = 'unknown') {
    return env[name] ?: fallback
}

def pipelineStarted() {
    send('Pipeline Started', """
Branch:   ${val('PIPELINE_BRANCH')}
Author:   ${val('PIPELINE_AUTHOR')}
Commit:   ${val('PIPELINE_COMMIT')}
Services: ${val('CHANGED_SERVICES', 'none')}
    """)
}

def pipelineSucceeded() {
    send('Pipeline Passed', """
Branch: ${val('PIPELINE_BRANCH')}
Author: ${val('PIPELINE_AUTHOR')}
Commit: ${val('PIPELINE_COMMIT')}
    """)
}

def pipelineFailed() {
    send('Pipeline Failed', """
Branch:        ${val('PIPELINE_BRANCH')}
Failed Stage:  ${val('FAILED_STAGE')}
Author:        ${val('PIPELINE_AUTHOR')}
Commit:        ${val('PIPELINE_COMMIT')}
    """)
}

def securityAlert(String tool, String detail) {
    send("SECURITY ALERT - ${tool}", """
Branch: ${val('PIPELINE_BRANCH')}
Author: ${val('PIPELINE_AUTHOR')}
Detail: ${detail}
Action: Fix immediately
    """)
}

def stageFailed(String stage, String reason) {
    send("Stage Failed - ${stage}", """
Branch: ${val('PIPELINE_BRANCH')}
Reason: ${reason}
Commit: ${val('PIPELINE_COMMIT')}
    """)
}

return this


// def send(String title, String message) {
//     echo "[$title] ${message?.trim()}"
// }

// def pipelineStarted() {
//     send('Pipeline Started', """
// Branch:   ${env.DETECTED_BRANCH ?: 'unknown'}
// Author:   ${env.GIT_AUTHOR ?: 'unknown'}
// Commit:   ${env.SHORT_COMMIT ?: 'unknown'}
// Services: ${env.CHANGED_SERVICES ?: 'none'}
//     """)
// }

// def pipelineSucceeded() {
//     send('Pipeline Passed', """
// Branch: ${env.PIPELINE_BRANCH}
// Author: ${env.PIPELINE_AUTHOR}
// Commit: ${env.PIPELINE_COMMIT}
//     """)
// }

// def pipelineFailed() {
//     send('Pipeline Failed', """
// Branch:        ${env.DETECTED_BRANCH ?: 'unknown'}
// Failed Stage:  ${env.FAILED_STAGE ?: 'unknown'}
// Author:        ${env.GIT_AUTHOR ?: 'unknown'}
//     """)
// }

// def securityAlert(String tool, String detail) {
//     send("SECURITY ALERT - ${tool}", """
// Branch: ${env.DETECTED_BRANCH ?: 'unknown'}
// Author: ${env.GIT_AUTHOR ?: 'unknown'}
// Detail: ${detail}
// Action: Fix immediately
//     """)
// }

// def stageFailed(String stage, String reason) {
//     send("Stage Failed - ${stage}", """
// Branch: ${env.DETECTED_BRANCH ?: 'unknown'}
// Reason: ${reason}
//     """)
// }




// return this
