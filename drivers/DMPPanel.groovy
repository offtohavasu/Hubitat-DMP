metadata {
    definition(name: "DMP Panel", namespace: "offtohavasu", author: "Curtis & ChatGPT") {
        capability "Initialize"
        capability "Refresh"
    }
}

def initialize() {
    log.info "Initializing DMP Panel"
}

def refresh() {
    log.info "Refresh requested"
}
