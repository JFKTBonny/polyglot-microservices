def installFromTar(String name, String url) {
    sh """
        if ! command -v ${name} &>/dev/null; then
            echo "Installing ${name}..."
            curl -sSfL ${url} | tar -xz -C /tmp 2>/dev/null || true
            sudo mv /tmp/${name} /usr/local/bin/${name} 2>/dev/null || \
            mv /tmp/${name} \${WORKSPACE}/${name} 2>/dev/null || true
        fi
        echo "${name} ready"
    """
}

def installFromScript(String name, String url) {
    sh """
        if ! command -v ${name} &>/dev/null; then
            echo "Installing ${name}..."
            curl -sSfL ${url} | sh -s -- -b /tmp 2>/dev/null || true
            sudo mv /tmp/${name} /usr/local/bin/${name} 2>/dev/null || \
            mv /tmp/${name} \${WORKSPACE}/${name} 2>/dev/null || true
        fi
        echo "${name} ready"
    """
}

def installFromBinary(String name, String url) {
    sh """
        if ! command -v ${name} &>/dev/null; then
            echo "Installing ${name}..."
            curl -sSfL ${url} -o /tmp/${name}
            chmod +x /tmp/${name}
            sudo mv /tmp/${name} /usr/local/bin/${name} 2>/dev/null || \
            mv /tmp/${name} \${WORKSPACE}/${name} 2>/dev/null || true
        fi
        echo "${name} ready"
    """
}

def installPip(String name) {
    sh """
        if ! command -v ${name} &>/dev/null; then
            pip install ${name} --quiet 2>/dev/null || true
        fi
        echo "${name} ready"
    """
}

def installNpm(String name) {
    sh """
        if ! command -v ${name} &>/dev/null; then
            npm install -g ${name} --quiet 2>/dev/null || true
        fi
        echo "${name} ready"
    """
}

def installGo(String name, String pkg) {
    sh """
        if ! command -v ${name} &>/dev/null; then
            go install ${pkg}@latest 2>/dev/null || true
        fi
        echo "${name} ready"
    """
}

def printVersions() {
    sh '''
        echo "Tool Versions"
        echo "Git:     $(git --version 2>/dev/null || echo not found)"
        echo "Docker:  $(docker --version 2>/dev/null || echo not found)"
        echo "Node:    $(node --version 2>/dev/null || echo not found)"
        echo "Python:  $(python3 --version 2>/dev/null || echo not found)"
        echo "Go:      $(go version 2>/dev/null || echo not found)"
        echo "Java:    $(java -version 2>&1 | head -1 || echo not found)"
        echo "Maven:   $(mvn -version 2>/dev/null | head -1 || echo not found)"
        echo "kubectl: $(kubectl version --client 2>/dev/null | head -1 || echo not found)"
    '''
}




