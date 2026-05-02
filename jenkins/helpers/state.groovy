import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def getFile() {
    "jenkins/state/pipeline-meta.json"
}

def save(Map data) {
    sh "mkdir -p jenkins/state"   // 👈 ADD THIS LINE

    writeFile file: getFile(),
        text: JsonOutput.prettyPrint(JsonOutput.toJson(data))
}

def toSafeObject(obj) {
    if (obj instanceof Map) {
        return obj.collectEntries { k, v ->
            [(k): toSafeObject(v)]
        }
    }
    if (obj instanceof List) {
        return obj.collect { toSafeObject(it) }
    }
    return obj
}

def load() {
    def file = getFile()

    if (!fileExists(file)) {
        return [:]
    }

    def raw = readFile(file)
    def parsed = new JsonSlurper().parseText(raw)

    return toSafeObject(parsed ?: [:])
}

return this