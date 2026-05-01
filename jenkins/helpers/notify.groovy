def send(String title, String message) {
    echo "[$title] ${message?.trim()}"
}

def pipelineStarted() {
    send('Pipeline Started', """
Branch:   ${env.DETECTED_BRANCH ?: 'unknown'}
Author:   ${env.GIT_AUTHOR ?: 'unknown'}
Commit:   ${env.SHORT_COMMIT ?: 'unknown'}
Services: ${env.CHANGED_SERVICES ?: 'none'}
    """)
}

def pipelineSucceeded() {
    send('Pipeline Passed', """
Branch: ${env.DETECTED_BRANCH ?: 'unknown'}
Author: ${env.GIT_AUTHOR ?: 'unknown'}
Commit: ${env.SHORT_COMMIT ?: 'unknown'}
    """)
}

def pipelineFailed() {
    send('Pipeline Failed', """
Branch:        ${env.DETECTED_BRANCH ?: 'unknown'}
Failed Stage:  ${env.FAILED_STAGE ?: 'unknown'}
Author:        ${env.GIT_AUTHOR ?: 'unknown'}
    """)
}

def securityAlert(String tool, String detail) {
    send("SECURITY ALERT - ${tool}", """
Branch: ${env.DETECTED_BRANCH ?: 'unknown'}
Author: ${env.GIT_AUTHOR ?: 'unknown'}
Detail: ${detail}
Action: Fix immediately
    """)
}

def stageFailed(String stage, String reason) {
    send("Stage Failed - ${stage}", """
Branch: ${env.DETECTED_BRANCH ?: 'unknown'}
Reason: ${reason}
    """)
}




return this
