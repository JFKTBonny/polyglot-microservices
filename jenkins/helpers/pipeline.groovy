// ── Pipeline Control Helpers ──────────────────────────────────
// Provides pipeline gates, report handling and utility methods
// Used by all stage files

/**
 * Block the pipeline with a clear error message
 * Sets FAILED_STAGE for reporting
 * @param tool    name of tool/stage that triggered block
 * @param reason  human readable reason
 */
def block(String tool, String reason) {
    env.FAILED_STAGE = tool
    error """
╔══════════════════════════════════════════╗
║  PIPELINE BLOCKED                        ║
╠══════════════════════════════════════════╣
║  Tool:   ${tool.take(34).padRight(34)}║
║  Reason: ${reason.take(34).padRight(34)}║
╠══════════════════════════════════════════╣
║  Fix the issue and re-push to unblock    ║
╚══════════════════════════════════════════╝
    """
}

/**
 * Check a JSON secret scan report and block if findings exist
 * @param file   path to JSON report file
 * @param tool   tool name for error messages
 */
def checkSecretReport(String file, String tool) {
    if (!fileExists(file)) {
        echo "${tool} — no report file found, assuming clean"
        return
    }

    def content = readFile(file).trim()

    if (!content || content == '[]' || content == 'null' || content == '') {
        echo "${tool} passed — no secrets found"
        return
    }

    try {
        def findings = readJSON text: content
        if (findings instanceof List && findings.size() > 0) {
            echo """
╔══════════════════════════════════════════╗
║  SECRETS DETECTED — ${tool.take(20).padRight(20)}  ║
╠══════════════════════════════════════════╣
║  Count: ${findings.size().toString().padRight(33)}║
╚══════════════════════════════════════════╝
            """
            findings.take(5).each { f ->
                echo """
Type:   ${f.RuleID        ?: f.DetectorName ?: 'unknown'}
File:   ${f.File          ?: f.SourceMetadata?.Data?.Filesystem?.file ?: 'unknown'}
Line:   ${f.StartLine     ?: 'unknown'}
Commit: ${f.Commit        ?: 'N/A'}
Author: ${f.Author        ?: 'N/A'}
────────────────────────────────────────
                """
            }
            block(tool, "${findings.size()} secret(s) detected")
        } else {
            echo "${tool} passed — no secrets found"
        }
    } catch (e) {
        echo "${tool} — could not parse report: ${e.message}"
        echo "Treating as clean — manual review recommended"
    }
}

/**
 * Check a SAST report and block if high/critical findings exist
 * @param file      path to report file
 * @param tool      tool name
 * @param severity  minimum severity to block on (HIGH, CRITICAL)
 */
def checkSastReport(String file, String tool, String severity = 'HIGH') {
    if (!fileExists(file)) {
        echo "${tool} — no report file found"
        return
    }

    def content = readFile(file).trim()
    if (!content || content == '[]') {
        echo "${tool} passed"
        return
    }

    try {
        def findings = readJSON text: content
        def blocking = findings?.findAll { f ->
            def s = (f.severity ?: f.issue_severity ?: f.level ?: '').toUpperCase()
            s == 'CRITICAL' || (severity == 'HIGH' && s == 'HIGH')
        }

        if (blocking?.size() > 0) {
            echo """
╔══════════════════════════════════════════╗
║  SAST FINDINGS — ${tool.take(23).padRight(23)}║
╠══════════════════════════════════════════╣
║  Blocking findings: ${blocking.size().toString().padRight(21)}║
╚══════════════════════════════════════════╝
            """
            blocking.take(5).each { f ->
                echo """
Severity: ${f.severity ?: f.issue_severity ?: 'unknown'}
File:     ${f.filename ?: f.file ?: 'unknown'}
Line:     ${f.line_number ?: f.line ?: 'unknown'}
Issue:    ${f.issue_text ?: f.message ?: f.check_id ?: 'unknown'}
────────────────────────────────────────
                """
            }
            block(tool, "${blocking.size()} HIGH/CRITICAL finding(s)")
        } else {
            echo "${tool} passed — no blocking findings"
        }
    } catch (e) {
        echo "${tool} — could not parse report: ${e.message}"
    }
}

/**
 * Archive a report file if it exists
 * @param file  path to report file
 */
def archiveReport(String file) {
    if (fileExists(file)) {
        archiveArtifacts artifacts: file, allowEmptyArchive: true
        echo "Archived: ${file}"
    } else {
        echo "No report to archive: ${file}"
    }
}

/**
 * Archive multiple report files
 * @param files  list of file paths
 */
def archiveReports(List files) {
    files.each { f -> archiveReport(f) }
}

/**
 * Print a stage banner for clarity in logs
 * @param title  stage title
 */
def banner(String title) {
    echo """
╔══════════════════════════════════════════╗
║  ${title.take(40).padRight(40)}║
╚══════════════════════════════════════════╝
    """
}

/**
 * Print a summary table
 * @param title  summary title
 * @param rows   list of [label, value] pairs
 */
def summary(String title, List rows) {
    def lines = rows.collect { r ->
        "║  ${r[0].take(15).padRight(15)}: ${r[1].toString().take(22).padRight(22)}║"
    }.join('\n')

    echo """
╔══════════════════════════════════════════╗
║  ${title.take(40).padRight(40)}║
╠══════════════════════════════════════════╣
${lines}
╚══════════════════════════════════════════╝
    """
}

/**
 * Check if a service was changed and should be processed
 * @param service  service name
 */
def isChanged(String service) {
    def changed = env.CHANGED_SERVICES?.split(',')?.toList() ?: []
    return changed.contains(service)
}

/**
 * Get list of changed services
 */
def changedServices() {
    return env.CHANGED_SERVICES?.split(',')?.toList() ?: []
}

/**
 * Check if running on main or develop branch
 */
def isDeployBranch() {
    return env.DETECTED_BRANCH == 'main' || env.DETECTED_BRANCH == 'develop'
}

