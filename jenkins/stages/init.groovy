def call(config) {

    def state = load 'jenkins/helpers/state.groovy'

    def gitInfo = sh(
        returnStdout: true,
        script: 'git log -1 --pretty=format:"%an|%ae|%h|%H"'
    ).trim().split("\\|")

    // ✅ robust branch detection
    def branch = env.GIT_BRANCH

    if (!branch) {
        branch = sh(
            returnStdout: true,
            script: 'git branch --show-current'
        ).trim()
    }

    if (!branch) {
        branch = sh(
            returnStdout: true,
            script: 'git rev-parse --abbrev-ref HEAD'
        ).trim()
    }

    if (branch == 'HEAD' || !branch) {
        branch = env.GIT_BRANCH ?: 'unknown'
    }

    config.branch = branch
    config.author = gitInfo[0]
    config.email  = gitInfo[1]
    config.commit = gitInfo[2]
    config.full   = gitInfo[3]
    config.start  = System.currentTimeMillis()

    sh 'mkdir -p jenkins/state'
    state.save(config)

    echo """
    ─── PIPELINE INIT ───
    Branch : ${config.branch}
    Author : ${config.author}
    Commit : ${config.commit}
    ─────────────────────
    """
}

return this