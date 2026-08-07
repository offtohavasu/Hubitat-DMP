metadata {
    definition(name: "DMP Panel", namespace: "offtohavasu", author: "Curtis & ChatGPT") {
        capability "Initialize"
        capability "Refresh"
        command "connect"
        command "disconnect"
    }
}

preferences {
    section("Panel Connection") {
        input "debugLogging", "bool", title: "Enable debug logging", defaultValue: false
    }
}

void installed() {
    initialize()
}

void updated() {
    initialize()
}

void initialize() {
    state.connected = false
    // sendEvent(name: "connected", value: "false")
    logDebug("Initializing DMP Panel")
}

void refresh() {
    logDebug("Refresh requested")
}

void connect() {

    def panelIp = getDataValue("panelIp")
    def panelPort = getDataValue("panelPort")
    def accountNumber = getDataValue("accountNumber")
    def remoteKey = getDataValue("remoteKey")

    logDebug("panelIp = ${panelIp}")
    logDebug("panelPort = ${panelPort}")
    logDebug("accountNumber = ${accountNumber}")
    logDebug("remoteKey present = ${remoteKey ? 'yes' : 'no'}")

    if (!panelIp || !panelPort) {
        logDebug("Panel IP and port are required")
        state.connected = false
      //  sendEvent(name: "connected", value: "false")
        return
    }

    try {

        logDebug("Attempting raw TCP connection to ${panelIp}:${panelPort}")

        interfaces.rawSocket.connect(
            panelIp,
            panelPort.toInteger()
        )

        logDebug("Raw TCP connection requested to ${panelIp}:${panelPort}")

        String auth = "@${accountNumber}!V2${remoteKey}\r"

        logDebug("Sending AUTH command")

        interfaces.rawSocket.sendMessage(auth)

    } catch (Exception e) {

        state.connected = false
       // sendEvent(name: "connected", value: "false")

        logDebug("Raw TCP connection failed: ${e.message}")
    }
}
    

void disconnect() {
    try {
        interfaces.rawSocket.disconnect()
        state.connected = false
       // sendEvent(name: "connected", value: "false")
        logDebug("Raw TCP socket disconnected")
    } catch (Exception e) {
        logDebug("Disconnect failed: ${e.message}")
    }
}

void socketStatus(String message) {
    String statusText = message?.toString() ?: "unknown"
    logDebug("Socket status callback: ${statusText}")

    if (statusText.equalsIgnoreCase("connected") || statusText.equalsIgnoreCase("open")) {
        state.connected = true
      //  sendEvent(name: "connected", value: "true")
    } else if (statusText.equalsIgnoreCase("disconnected") || statusText.equalsIgnoreCase("closed") || statusText.equalsIgnoreCase("error")) {
        state.connected = false
       //  sendEvent(name: "connected", value: "false")
    }
}

void parse(String message) {

    if (message == null) {
        logDebug("RAW MESSAGE: <null>")
        return
    }

    logDebug("RAW MESSAGE: ${message}")

   if (message.contains("2B563032")) {

    logDebug("Panel authentication acknowledged")

    state.authenticated = true

    String accountNumber = getDataValue("accountNumber")
    String statusCmd = "@${accountNumber}?WB**Y001\r"

    logDebug("TX: ${statusCmd}")

    interfaces.rawSocket.sendMessage(statusCmd)
}
}
 

void onSocketError(Object error) {
    String errorText = error?.toString() ?: "unknown socket error"
    state.connected = false
   // sendEvent(name: "connected", value: "false")
    logDebug("Socket error callback: ${errorText}")
}



   

private void logDebug(String message) {
    if (settings?.debugLogging) {
        log.debug(message)
    }
}
