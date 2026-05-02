def call(config) {

    def state = load 'jenkins/helpers/state.groovy'

    def gitInfo = sh(
        returnStdout: true,
        script: 'git log -1 --pretty=format:"%an|%ae|%h|%H"'
    ).trim().split("\\|")

    def branch = env.BRANCH_NAME ?: sh(
        returnStdout: true,
        branch = $(git rev-parse --abbrev-ref HEAD)
        script: 'git rev-parse --abbrev-ref HEAD'
    ).trim()

    config.branch = branch
    config.author = gitInfo[0]
    config.email  = gitInfo[1]
    config.commit = gitInfo[2]
    config.full   = gitInfo[3]
    config.start  = System.currentTimeMillis()

    // ✅ ensure directory exists
    sh 'mkdir -p jenkins/state'

    // ✅ save state
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