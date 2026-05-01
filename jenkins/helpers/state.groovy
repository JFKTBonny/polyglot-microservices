import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def FILE = "jenkins/state/pipeline-meta.json"

def save(Map data) {
    writeFile file: FILE, text: JsonOutput.prettyPrint(JsonOutput.toJson(data))
}

def load() {
    if (!fileExists(FILE)) {
        return [:]
    }
    return new JsonSlurper().parseText(readFile(FILE))
}

return this