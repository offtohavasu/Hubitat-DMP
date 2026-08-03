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
        input "panelIp", "text", title: "Panel IP Address", required: true
        input "panelPort", "number", title: "Port", defaultValue: 2011, required: true
        input "debugLogging", "bool", title: "Enable debug logging", defaultValue: false
    }
}

private boolean connected = false
private boolean debugLogging = false

void installed() {
    initialize()
}

void updated() {
    initialize()
}

void initialize() {
    debugLogging = settings.debugLogging ?: false
    connected = false
    sendEvent(name: "connected", value: "false")
    logDebug("Initializing DMP Panel")
}

void refresh() {
    logDebug("Refresh requested")
}

void connect() {
    if (!settings.panelIp || !settings.panelPort) {
        logDebug("Panel IP and port are required")
        connected = false
        sendEvent(name: "connected", value: "false")
        return
    }

    logDebug("Attempting raw TCP connection to ${settings.panelIp}:${settings.panelPort}")

    try {
        interfaces.rawSocket.connect(settings.panelIp, settings.panelPort.toInteger(), 10000)
        connected = true
        sendEvent(name: "connected", value: "true")
        logDebug("Raw TCP connection requested to ${settings.panelIp}:${settings.panelPort}")
    } catch (Exception e) {
        connected = false
        sendEvent(name: "connected", value: "false")
        logDebug("Raw TCP connection failed: ${e.message}")
    }
}

void disconnect() {
    try {
        interfaces.rawSocket.disconnect()
        connected = false
        sendEvent(name: "connected", value: "false")
        logDebug("Raw TCP socket disconnected")
    } catch (Exception e) {
        logDebug("Disconnect failed: ${e.message}")
    }
}

void onSocketStatus(Object status) {
    String statusText = status?.toString() ?: "unknown"
    logDebug("Socket status callback: ${statusText}")

    if (statusText.equalsIgnoreCase("connected") || statusText.equalsIgnoreCase("open")) {
        connected = true
        sendEvent(name: "connected", value: "true")
    } else if (statusText.equalsIgnoreCase("disconnected") || statusText.equalsIgnoreCase("closed") || statusText.equalsIgnoreCase("error")) {
        connected = false
        sendEvent(name: "connected", value: "false")
    }
}

void onSocketData(Object data) {
    if (data == null) {
        return
    }

    byte[] payload = data instanceof byte[] ? (byte[]) data : data.toString().getBytes("UTF-8")
    if (payload == null || payload.length == 0) {
        return
    }

    logDebug("Raw socket receive: ${bytesToHex(payload)}")
}

void onSocketError(Object error) {
    String errorText = error?.toString() ?: "unknown socket error"
    connected = false
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
    if (debugLogging) {
        log.debug(message)
    }
}
