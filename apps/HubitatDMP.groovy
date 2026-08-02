definition(
    name: "Hubitat DMP",
    namespace: "offtohavasu",
    author: "Curtis & ChatGPT",
    description: "DMP Integration for Hubitat",
    singleInstance: true
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Hubitat DMP") {
        section("Connection") {
            input "panelIp", "text", title: "Panel IP Address"
            input "panelPort", "number", title: "Port", defaultValue: 2011
        }
    }
}

def installed() {}
def updated() {}
