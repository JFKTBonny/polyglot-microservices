def block(String tool, String reason) {
    env.FAILED_STAGE = tool
    error "PIPELINE BLOCKED | Tool: ${tool} | Reason: ${reason}"
}

def checkSecretReport(String file, String tool) {
    if (!fileExists(file)) {
        echo "${tool} - no report found, assuming clean"
        return
    }
    def content = readFile(file).trim()
    if (!content || content == '[]' || content == 'null') {
        echo "${tool} passed - no secrets found"
        return
    }
    try {
        def findings = readJSON text: content
        if (findings instanceof List && findings.size() > 0) {
            echo "${tool} - found ${findings.size()} secret(s)"
            findings.take(3).each { f ->
                echo "  Type: ${f.RuleID ?: f.DetectorName ?: 'unknown'}"
                echo "  File: ${f.File ?: 'unknown'}"
                echo "  Line: ${f.StartLine ?: 'unknown'}"
            }
            block(tool, "${findings.size()} secret(s) detected")
        } else {
            echo "${tool} passed"
        }
    } catch (e) {
        echo "${tool} - could not parse report: ${e.message}"
    }
}

def checkSastReport(String file, String tool) {
    if (!fileExists(file)) {
        echo "${tool} - no report found"
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
            def s = (f.severity ?: f.issue_severity ?: '').toUpperCase()
            s == 'CRITICAL' || s == 'HIGH'
        }
        if (blocking?.size() > 0) {
            echo "${tool} - ${blocking.size()} HIGH/CRITICAL finding(s)"
            blocking.take(3).each { f ->
                echo "  Severity: ${f.severity ?: f.issue_severity ?: 'unknown'}"
                echo "  File:     ${f.filename ?: f.file ?: 'unknown'}"
                echo "  Issue:    ${f.issue_text ?: f.message ?: 'unknown'}"
            }
            block(tool, "${blocking.size()} HIGH/CRITICAL finding(s)")
        } else {
            echo "${tool} passed - no blocking findings"
        }
    } catch (e) {
        echo "${tool} - could not parse report: ${e.message}"
    }
}

def archiveReport(String file) {
    if (fileExists(file)) {
        archiveArtifacts artifacts: file, allowEmptyArchive: true
        echo "Archived: ${file}"
    }
}

def archiveReports(List files) {
    files.each { archiveReport(it) }
}

def banner(String title) {
    echo "========== ${title} =========="
}

def isChanged(String service) {
    def changed = env.CHANGED_SERVICES?.split(',')?.toList() ?: []
    return changed.contains(service)
}

def changedServices() {
    return env.CHANGED_SERVICES?.split(',')?.toList() ?: []
}

def isDeployBranch() {
    return env.DETECTED_BRANCH == 'main' || env.DETECTED_BRANCH == 'develop'
}



return this
