definition(
    name: "Hubitat DMP",
    namespace: "offtohavasu",
    author: "Curtis & ChatGPT",
    description: "Native Hubitat app skeleton for XT30 communication",
    singleInstance: true
)

// -----------------------------------------------------------------------------
// metadata
// -----------------------------------------------------------------------------

preferences {
    page(name: "mainPage")
}

// -----------------------------------------------------------------------------
// preferences
// -----------------------------------------------------------------------------

Map mainPage() {
    dynamicPage(name: "mainPage", title: "Hubitat DMP") {
        section("Panel Connection") {
            input "panelIp", "text", title: "Panel IP Address", required: true
            input "panelPort", "number", title: "Port", defaultValue: 2011, required: true
            input "accountNumber", "text", title: "Account Number", required: true
            input "remoteKey", "text", title: "Remote Key"
            input "debugLogging", "bool", title: "Enable debug logging", defaultValue: false
        }

        section("Actions") {
            input "connectButton", "button", title: "Connect"
            input "disconnectButton", "button", title: "Disconnect"
            input "testButton", "button", title: "Test Connection"
        }

        section("Status") {
            paragraph "Connected: ${state.connected ?: false}"
            paragraph "Authenticated: ${state.authenticated ?: false}"
            paragraph "Last Keepalive: ${state.lastKeepAlive ?: 'n/a'}"
            paragraph "Last Error: ${state.lastError ?: 'n/a'}"
        }
    }
}

// -----------------------------------------------------------------------------
// lifecycle (installed, updated, initialize)
// -----------------------------------------------------------------------------

private Object socket


void installed() {
    initialize()
    ensurePanelDevice()
}

void updated() {
    unsubscribe()
    initialize()
    ensurePanelDevice()
}

void initialize() {
    state.connected = false
    state.authenticated = false
    state.lastKeepAlive = null
    state.lastError = null
    state.panelInfo = null
    state.debugLogging = settings.debugLogging ?: false
    socket = null
}

private void ensurePanelDevice() {
    if (state.panelDeviceId) {
        def existing = getChildDevice(state.panelDeviceId)
        if (existing) {
            return
        }
        state.panelDeviceId = null
    }

    def existingDevices = getChildDevices()
    def existingDevice = existingDevices?.find { it?.deviceNetworkId?.startsWith('dmp-panel') || it?.name == 'DMP Panel' }
    if (existingDevice) {
        state.panelDeviceId = existingDevice.deviceNetworkId
        return
    }

    def child = addChildDevice('offtohavasu', 'DMP Panel', "dmp-panel-${app.id}", [name: 'DMP Panel', label: 'DMP Panel'])
    if (child) {
        state.panelDeviceId = child.deviceNetworkId
    }
}

// -----------------------------------------------------------------------------
// connection management
// -----------------------------------------------------------------------------

void appButtonHandler(buttonName) {
    switch (buttonName) {
        case 'connectButton':
            connectToPanel()
            break
        case 'disconnectButton':
            disconnectFromPanel()
            break
        case 'testButton':
            loginToPanel()
            break
    }
}

private void connectToPanel() {
    if (!settings.panelIp || !settings.panelPort || !settings.accountNumber) {
        state.lastError = 'Panel IP, port, and account number are required'
        state.connected = false
        updateDisplay()
        return
    }

    ensurePanelDevice()
    def panelDevice = getChildDevice(state.panelDeviceId)
    if (panelDevice) {
        panelDevice.connect()
        state.connected = true
        state.lastError = null
        updateDisplay()
        return
    }

    state.connected = false
    socket = null
    state.lastError = 'Unable to locate the DMP panel child device.'
    updateDisplay()
    logInfo(state.lastError)
}

private void disconnectFromPanel() {
    ensurePanelDevice()
    def panelDevice = getChildDevice(state.panelDeviceId)
    if (panelDevice) {
        panelDevice.disconnect()
        state.connected = false
        state.lastError = 'Disconnected from panel driver.'
        updateDisplay()
        return
    }

    socket = null
    state.connected = false
    state.lastError = 'No active socket connection from the App. Use a Driver implementing interfaces.rawSocket for persistent outbound TCP connections.'
    updateDisplay()
    logInfo(state.lastError)
}

private void loginToPanel() {
    logInfo("loginToPanel() stub")
}

// -----------------------------------------------------------------------------
// protocol engine
// -----------------------------------------------------------------------------

private Object sendCommand() {
    logInfo("sendCommand() stub")
    return null
}

private byte[] encodeCommand() {
    logInfo("encodeCommand() stub")
    return null
}

private Object decodeResponse() {
    logInfo("decodeResponse() stub")
    return null
}

private void parseStatus() {
    logInfo("parseStatus() stub")
}

private void parseOutputs() {
    logInfo("parseOutputs() stub")
}

private void parseUsers() {
    logInfo("parseUsers() stub")
}

// -----------------------------------------------------------------------------
// crypto engine
// -----------------------------------------------------------------------------

private String encryptPayload() {
    logInfo("encryptPayload() stub")
    return null
}

private String decryptPayload() {
    logInfo("decryptPayload() stub")
    return null
}

// -----------------------------------------------------------------------------
// response parsing
// -----------------------------------------------------------------------------

private void handleSocketMessage() {
    logInfo("handleSocketMessage() stub")
}

private void handleSocketMessage(Object data) {
    if (data == null) {
        return
    }

    byte[] payload = data instanceof byte[] ? (byte[]) data : data.toString().getBytes("UTF-8")
    if (payload == null || payload.length == 0) {
        return
    }

    state.lastSocketBytes = payload
    logInfo("Inbound raw bytes: ${bytesToHex(payload)}")
}

void onSocketStatus(Object status) {
    String statusText = status?.toString() ?: 'unknown'
    logInfo("Socket status callback: ${statusText}")

    if (statusText.equalsIgnoreCase('connected') || statusText.equalsIgnoreCase('open')) {
        state.connected = true
        state.lastError = null
        updateDisplay()
    } else if (statusText.equalsIgnoreCase('disconnected') || statusText.equalsIgnoreCase('closed') || statusText.equalsIgnoreCase('error')) {
        state.connected = false
        state.lastError = statusText
        updateDisplay()
    }
}

void onSocketData(Object data) {
    logInfo("Socket data callback received")
    handleSocketMessage(data)
}

void onSocketError(Object error) {
    String errorText = error?.toString() ?: 'unknown socket error'
    logInfo("Socket error callback: ${errorText}")
    state.connected = false
    state.lastError = errorText
    updateDisplay()
}

// -----------------------------------------------------------------------------
// keepalive
// -----------------------------------------------------------------------------

private void startKeepalive() {
    logInfo("startKeepalive() stub")
}

private void stopKeepalive() {
    logInfo("stopKeepalive() stub")
}

// -----------------------------------------------------------------------------
// public panel commands
// -----------------------------------------------------------------------------

void requestStatus() {
    logInfo("requestStatus() stub")
}

void requestOutputs() {
    logInfo("requestOutputs() stub")
}

void requestUsers() {
    logInfo("requestUsers() stub")
}

void armArea() {
    logInfo("armArea() stub")
}

void disarmArea() {
    logInfo("disarmArea() stub")
}

void setOutput() {
    logInfo("setOutput() stub")
}

// -----------------------------------------------------------------------------
// logging helpers
// -----------------------------------------------------------------------------

private void updateDisplay() {
    sendEvent(name: "connected", value: state.connected ? "true" : "false")
    sendEvent(name: "authenticated", value: state.authenticated ? "true" : "false")
    sendEvent(name: "lastKeepAlive", value: state.lastKeepAlive?.toString())
    sendEvent(name: "lastError", value: state.lastError ?: "")
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

private void logInfo(String message) {
    if (state.debugLogging ?: false) {
        log.debug(message)
    }
}
