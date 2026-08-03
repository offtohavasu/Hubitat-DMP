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
    sendEvent(name: "connected", value: "false")
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

    if (!panelIp || !panelPort) {
        logDebug("Panel IP and port are required")
        state.connected = false
        sendEvent(name: "connected", value: "false")
        return
    }

    logDebug("Attempting raw TCP connection to ${panelIp}:${panelPort}")
    logDebug("panelIp = ${getDataValue('panelIp')}")
    logDebug("panelPort = ${getDataValue('panelPort')}")
    logDebug("accountNumber = ${getDataValue('accountNumber')}")
    logDebug("remoteKey present = ${getDataValue('remoteKey') ? 'yes' : 'no'}")

    try {
        interfaces.rawSocket.connect(
            panelIp,
            panelPort.toInteger(),
            [byteInterface: true]
        )
        state.connected = true
        sendEvent(name: "connected", value: "true")
        logDebug("Raw TCP connection requested to ${panelIp}:${panelPort}")
    } catch (Exception e) {
        state.connected = false
        sendEvent(name: "connected", value: "false")
        logDebug("Raw TCP connection failed: ${e.message}")
    }
}

void disconnect() {
    try {
        interfaces.rawSocket.disconnect()
        state.connected = false
        sendEvent(name: "connected", value: "false")
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
        sendEvent(name: "connected", value: "true")
    } else if (statusText.equalsIgnoreCase("disconnected") || statusText.equalsIgnoreCase("closed") || statusText.equalsIgnoreCase("error")) {
        state.connected = false
        sendEvent(name: "connected", value: "false")
    }
}

void parse(String message) {
    if (message == null) {
        return
    }

    byte[] payload = message instanceof byte[] ? (byte[]) message : message.toString().getBytes("UTF-8")
    if (payload == null || payload.length == 0) {
        return
    }

    logDebug("Raw socket receive: ${bytesToHex(payload)}")
}

void onSocketError(Object error) {
    String errorText = error?.toString() ?: "unknown socket error"
    state.connected = false
    sendEvent(name: "connected", value: "false")
    logDebug("Socket error callback: ${errorText}")
}

private String bytesToHex(byte[] payload) {
    if (payload == null || payload.length == 0) {
        return ""
    }

    StringBuilder builder = new StringBuilder()
    for (int i = 0; i < payload.length; i++) {
        if (i > 0) {
            builder.append(' ')
        }
        builder.append(String.format('%02x', payload[i] & 0xFF))
    }
    return builder.toString()
}

private void logDebug(String message) {
    if (settings?.debugLogging) {
        log.debug(message)
    }
}
