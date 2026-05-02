def call(config) {

    def state = load 'jenkins/helpers/state.groovy'

    // ✅ Get git info safely
    def gitInfo = sh(
        returnStdout: true,
        script: 'git log -1 --pretty=format:"%an|%ae|%h|%H"'
    ).trim().split("\\|")

    // ✅ FIXED branch detection
    def branch = env.BRANCH_NAME
    if (!branch) {
        branch = sh(
            returnStdout: true,
            script: 'git rev-parse --abbrev-ref HEAD'
        ).trim()
    }

    // ✅ Build CLEAN serializable map
    def meta = [
        branch: branch,
        author: gitInfo[0],
        email : gitInfo[1],
        commit: gitInfo[2],
        full  : gitInfo[3],
        start : System.currentTimeMillis()
    ]

    // ✅ Ensure directory exists
    sh 'mkdir -p jenkins/state'

    // ✅ Save ONLY clean map (not config reference)
    state.save(meta)

    // ✅ (optional) also populate config safely
    config.putAll(meta)

    echo """
    ─── PIPELINE INIT ───
    Branch : ${meta.branch}
    Author : ${meta.author}
    Commit : ${meta.commit}
    ─────────────────────
    """
}

return this