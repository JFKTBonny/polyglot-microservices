def call(config) {

    def state = load 'jenkins/helpers/state.groovy'

    def gitInfo = sh(
        returnStdout: true,
        script: 'git log -1 --pretty=format:"%an|%ae|%h|%H"'
    ).trim().split("\\|")

    // ✅ robust branch detection
    

    def branch = env.BRANCH_NAME ?: sh(
    returnStdout: true,
    script: 'git rev-parse --abbrev-ref HEAD'
    
    ).trim()

    if (!branch || branch == 'HEAD') {
        branch = sh(
            returnStdout: true,
            script: 'git branch -r --contains HEAD | head -n 1 | sed "s|origin/||" | tr -d " "'
        ).trim()
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