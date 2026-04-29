// ── Tool Installation Helpers ─────────────────────────────────
// Provides safe, idempotent tool installation methods
// Used by all stage files that need external tools

/**
 * Install a tool from a tar.gz archive
 * @param name       tool binary name (e.g. 'gitleaks')
 * @param tarUrl     download URL of tar.gz
 */
def installFromTar(String name, String tarUrl) {
    sh """
        if ! command -v ${name} &>/dev/null; then
            echo "Installing ${name}..."
            curl -sSfL ${tarUrl} \
                | tar -xz -C /tmp 2>/dev/null || true
            sudo mv /tmp/${name} /usr/local/bin/${name} 2>/dev/null || \
            mv /tmp/${name} \${WORKSPACE}/${name} 2>/dev/null || true
            echo "${name} installed"
        else
            echo "${name} already installed: \$(${name} --version 2>/dev/null || echo 'version unknown')"
        fi
    """
}

/**
 * Install a tool from an install shell script
 * @param name       tool binary name (e.g. 'trufflehog')
 * @param scriptUrl  URL of install.sh script
 */
def installFromScript(String name, String scriptUrl) {
    sh """
        if ! command -v ${name} &>/dev/null; then
            echo "Installing ${name}..."
            curl -sSfL ${scriptUrl} \
                | sh -s -- -b /tmp 2>/dev/null || true
            sudo mv /tmp/${name} /usr/local/bin/${name} 2>/dev/null || \
            mv /tmp/${name} \${WORKSPACE}/${name} 2>/dev/null || true
            echo "${name} installed"
        else
            echo "${name} already installed: \$(${name} --version 2>/dev/null || echo 'version unknown')"
        fi
    """
}

/**
 * Install a tool from a direct binary URL
 * @param name      tool binary name
 * @param binaryUrl direct URL to binary
 */
def installFromBinary(String name, String binaryUrl) {
    sh """
        if ! command -v ${name} &>/dev/null; then
            echo "Installing ${name}..."
            curl -sSfL ${binaryUrl} -o /tmp/${name}
            chmod +x /tmp/${name}
            sudo mv /tmp/${name} /usr/local/bin/${name} 2>/dev/null || \
            mv /tmp/${name} \${WORKSPACE}/${name} 2>/dev/null || true
            echo "${name} installed"
        else
            echo "${name} already installed"
        fi
    """
}

/**
 * Install a Python tool via pip
 * @param name     pip package name (e.g. 'bandit')
 * @param binary   binary name if different from package name
 */
def installPip(String name, String binary = '') {
    def bin = binary ?: name
    sh """
        if ! command -v ${bin} &>/dev/null; then
            echo "Installing ${name} via pip..."
            pip install ${name} --quiet --break-system-packages 2>/dev/null || \
            pip install ${name} --quiet || true
            echo "${name} installed"
        else
            echo "${name} already installed"
        fi
    """
}

/**
 * Install a Node.js tool via npm
 * @param name  npm package name
 */
def installNpm(String name) {
    sh """
        if ! command -v ${name} &>/dev/null; then
            echo "Installing ${name} via npm..."
            npm install -g ${name} --quiet 2>/dev/null || true
            echo "${name} installed"
        else
            echo "${name} already installed"
        fi
    """
}

/**
 * Install a Go tool via go install
 * @param name       binary name
 * @param goPackage  full Go package path
 */
def installGo(String name, String goPackage) {
    sh """
        if ! command -v ${name} &>/dev/null; then
            echo "Installing ${name} via go install..."
            go install ${goPackage}@latest 2>/dev/null || true
            export PATH=\$PATH:\$(go env GOPATH)/bin
            echo "${name} installed"
        else
            echo "${name} already installed"
        fi
    """
}

/**
 * Verify a tool is available
 * Fails the pipeline if tool is missing and required
 * @param name      binary name
 * @param required  if true — block pipeline if missing
 */
def verify(String name, Boolean required = false) {
    def present = sh(
        script: "command -v ${name} &>/dev/null && echo 'true' || echo 'false'",
        returnStdout: true
    ).trim() == 'true'

    if (!present && required) {
        error "Required tool '${name}' is not installed on Jenkins agent"
    }

    echo "${name}: ${present ? 'available' : 'not found'}"
    return present
}

/**
 * Print all tool versions for audit trail
 */
def printVersions() {
    sh '''
        echo "════════════════════════════════════"
        echo "  Tool Versions"
        echo "════════════════════════════════════"
        echo "OS:         $(uname -r)"
        echo "Docker:     $(docker --version 2>/dev/null || echo 'not found')"
        echo "Git:        $(git --version 2>/dev/null || echo 'not found')"
        echo "Node:       $(node --version 2>/dev/null || echo 'not found')"
        echo "Python:     $(python3 --version 2>/dev/null || echo 'not found')"
        echo "Go:         $(go version 2>/dev/null || echo 'not found')"
        echo "Java:       $(java -version 2>&1 | head -1 || echo 'not found')"
        echo "Maven:      $(mvn -version 2>/dev/null | head -1 || echo 'not found')"
        echo "kubectl:    $(kubectl version --client 2>/dev/null | head -1 || echo 'not found')"
        echo "Gitleaks:   $(gitleaks version 2>/dev/null || echo 'not found')"
        echo "Trivy:      $(trivy --version 2>/dev/null | head -1 || echo 'not found')"
        echo "Semgrep:    $(semgrep --version 2>/dev/null || echo 'not found')"
        echo "Hadolint:   $(hadolint --version 2>/dev/null || echo 'not found')"
        echo "════════════════════════════════════"
    '''
}

