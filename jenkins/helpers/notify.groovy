// ── Notification Helpers ──────────────────────────────────────
// Centralized notification system
// Supports: Slack, Email, Console
// All methods fail gracefully — notification failure
// never blocks the pipeline

/**
 * Send Slack notification
 * @param config  Map with: color, title, message
 */
def slack(Map config) {
    try {
        // Console fallback — always works
        echo """
════════════════════════════════════
  NOTIFY: ${config.title}
════════════════════════════════════
${config.message?.trim()}
════════════════════════════════════
        """

        // Slack webhook — uncomment when configured
        // def payload = groovy.json.JsonOutput.toJson([
        //     attachments: [[
        //         color:    config.color ?: '#439FE0',
        //         title:    config.title,
        //         text:     config.message?.trim(),
        //         footer:   "Jenkins | ${env.JOB_NAME} #${env.BUILD_NUMBER}",
        //         ts:       (System.currentTimeMillis() / 1000).toInteger()
        //     ]]
        // ])
        // sh """
        //     curl -s -X POST '${env.SLACK_WEBHOOK}' \
        //         -H 'Content-Type: application/json' \
        //         -d '${payload}' || true
        // """

    } catch (e) {
        echo "Slack notification failed: ${e.message}"
    }
}

/**
 * Send pipeline start notification
 */
def pipelineStarted() {
    slack(
        color: '#439FE0',
        title: 'Pipeline Started',
        message: """
Branch:   ${env.DETECTED_BRANCH ?: 'unknown'}
Author:   ${env.GIT_AUTHOR ?: 'unknown'}
Commit:   ${env.SHORT_COMMIT ?: 'unknown'}
Services: ${env.CHANGED_SERVICES ?: 'none'}
URL:      ${env.BUILD_URL ?: 'N/A'}
        """
    )
}

/**
 * Send pipeline success notification
 */
def pipelineSucceeded() {
    def duration = env.PIPELINE_START_TIME
        ? ((System.currentTimeMillis() - env.PIPELINE_START_TIME.toLong()) / 1000).toInteger()
        : 0

    slack(
        color: '#36a64f',
        title: 'Pipeline Passed',
        message: """
Branch:   ${env.DETECTED_BRANCH ?: 'unknown'}
Author:   ${env.GIT_AUTHOR ?: 'unknown'}
Commit:   ${env.SHORT_COMMIT ?: 'unknown'}
Duration: ${duration}s
URL:      ${env.BUILD_URL ?: 'N/A'}
        """
    )
}

/**
 * Send pipeline failure notification
 */
def pipelineFailed() {
    def duration = env.PIPELINE_START_TIME
        ? ((System.currentTimeMillis() - env.PIPELINE_START_TIME.toLong()) / 1000).toInteger()
        : 0

    slack(
        color: '#cc0000',
        title: 'Pipeline FAILED',
        message: """
Branch:       ${env.DETECTED_BRANCH ?: 'unknown'}
Author:       ${env.GIT_AUTHOR ?: 'unknown'}
Commit:       ${env.SHORT_COMMIT ?: 'unknown'}
Failed Stage: ${env.FAILED_STAGE ?: 'unknown'}
Duration:     ${duration}s
URL:          ${env.BUILD_URL ?: 'N/A'}
        """
    )
}

/**
 * Send security alert notification
 */
def securityAlert(String tool, String details) {
    slack(
        color: '#cc0000',
        title: "SECURITY ALERT — ${tool}",
        message: """
Branch:  ${env.DETECTED_BRANCH ?: 'unknown'}
Author:  ${env.GIT_AUTHOR ?: 'unknown'}
Commit:  ${env.SHORT_COMMIT ?: 'unknown'}
Tool:    ${tool}
Details: ${details}
Action:  Fix immediately before proceeding
URL:     ${env.BUILD_URL ?: 'N/A'}
        """
    )
}

/**
 * Send stage failure notification
 */
def stageFailed(String stage, String reason) {
    slack(
        color: '#ff6600',
        title: "Stage Failed — ${stage}",
        message: """
Branch: ${env.DETECTED_BRANCH ?: 'unknown'}
Stage:  ${stage}
Reason: ${reason}
URL:    ${env.BUILD_URL ?: 'N/A'}
        """
    )
}

