def getGitInfo() {
    def data = sh(
        returnStdout: true,
        script: 'git log -1 --pretty=format:"%an|%ae|%h|%H"'
    ).trim().split("\\|")

    return [
        author : data[0],
        email  : data[1],
        short  : data[2],
        full   : data[3]
    ]
}

def getBranch() {
    return env.BRANCH_NAME ?: sh(
        returnStdout: true,
        script: 'git rev-parse --abbrev-ref HEAD'
    ).trim()
}

return this