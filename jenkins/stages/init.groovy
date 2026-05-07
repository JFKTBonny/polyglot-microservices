def call(config) {

    def state = load 'jenkins/helpers/state.groovy'

    def gitInfo = sh(
        returnStdout: true,
        script: 'git log -1 --pretty=format:"%an|%ae|%h|%H"'
    ).trim().split("\\|")

    // =========================
    // BRANCH DETECTION (FIXED)
    // =========================
    def branch = env.BRANCH_NAME

    if (!branch || branch == 'null') {
        branch = sh(
            returnStdout: true,
            script: 'git rev-parse --abbrev-ref HEAD || echo "unknown"'
        ).trim()
    }

    if (!branch || branch == 'HEAD') {
        branch = sh(
            returnStdout: true,
            script: '''
                git branch -r --contains HEAD 2>/dev/null \
                | head -n 1 \
                | sed "s|origin/||" \
                | tr -d " " \
                || echo "unknown"
            '''
        ).trim()
    }

    if (!branch) {
        branch = "unknown"
    }

    // =========================
    // BUILD CONFIG
    // =========================
    config.branch = branch
    config.author = gitInfo[0]
    config.email  = gitInfo[1]
    config.commit = gitInfo[2]
    config.full   = gitInfo[3]
    config.start  = System.currentTimeMillis()

    // =========================
    // PERSIST STATE
    // =========================
    sh 'mkdir -p jenkins/state'
    state.save(config)

   

    echo """
    ─── PIPELINE INIT ───
    Branch : ${config.branch}
    Author : ${config.author}
    Commit : ${config.commit}
    ─────────────────────
    """

//     env.DETECTED_BRANCH = config.branch
//     env.GIT_AUTHOR      = config.author
//     env.SHORT_COMMIT    = config.commit
// 

}

return this