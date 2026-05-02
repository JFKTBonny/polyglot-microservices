def send(String title, String message) {
    echo "════════════════════════════════════"
    echo "  ${title}"
    echo "════════════════════════════════════"
    echo message.trim()
    echo "════════════════════════════════════"
}

def getState() {
    def state = load 'jenkins/helpers/state.groovy'
    return state.load() ?: [:]
}

def pipelineSucceeded() {
    def s = getState()

    send('Pipeline Passed', """
Branch: ${s.branch ?: 'unknown'}
Author: ${s.author ?: 'unknown'}
Commit: ${s.commit ?: 'unknown'}
    """)
}

def pipelineFailed() {
    def s = getState()

    send('Pipeline Failed', """
Branch: ${s.branch ?: 'unknown'}
Author: ${s.author ?: 'unknown'}
Commit: ${s.commit ?: 'unknown'}
Stage:  ${env.FAILED_STAGE ?: 'unknown'}
    """)
}

return this