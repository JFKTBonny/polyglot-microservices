import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def getFile() {
    return "jenkins/state/pipeline-meta.json"
}

def save(Map data) {
    def file = getFile()

    writeFile file: file,
        text: JsonOutput.prettyPrint(JsonOutput.toJson(data))
}

def load() {
    def file = getFile()

    if (!fileExists(file)) {
        return [:]
    }

    return new JsonSlurper().parseText(readFile(file))
}

return this