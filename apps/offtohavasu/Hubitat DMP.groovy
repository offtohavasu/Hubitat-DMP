definition(
    name: "Hubitat DMP",
    namespace: "offtohavasu",
    author: "Curtis & ChatGPT",
    description: "Native Hubitat app skeleton for XT30 communication",
    category: "Safety & Security",
    singleInstance: true
)

preferences {
    page(name: "mainPage")
}

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
        }

        section("Status") {
            paragraph "Connected: ${state.connected ?: false}"
            paragraph "Authenticated: ${state.authenticated ?: false}"
            paragraph "Last Error: ${state.lastError ?: 'n/a'}"
        }
    }
}

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
    state.lastError = null
    state.debugLogging = settings.debugLogging ?: false
}

void appButtonHandler(buttonName) {
    switch (buttonName) {
        case 'connectButton':
            connectToPanel()
            break
        case 'disconnectButton':
            disconnectFromPanel()
            break
    }
}

void ensurePanelDevice() {
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

void connectToPanel() {
    if (!settings.panelIp || !settings.panelPort || !settings.accountNumber) {
        state.lastError = 'Panel IP, port, and account number are required'
        state.connected = false
        return
    }

    ensurePanelDevice()
    def panelDevice = getChildDevice(state.panelDeviceId)
    if (panelDevice) {
        panelDevice.connect()
        state.connected = true
        state.lastError = null
        return
    }

    state.connected = false
    state.lastError = 'Unable to locate the DMP panel child device.'
    logInfo(state.lastError)
}

void disconnectFromPanel() {
    ensurePanelDevice()
    def panelDevice = getChildDevice(state.panelDeviceId)
    if (panelDevice) {
        panelDevice.disconnect()
        state.connected = false
        state.lastError = 'Disconnected from panel driver.'
        return
    }

    state.connected = false
    state.lastError = 'No active socket connection from the App. Use a Driver implementing interfaces.rawSocket for persistent outbound TCP connections.'
    logInfo(state.lastError)
}

void logInfo(String message) {
    if (state.debugLogging ?: false) {
        log.debug(message)
    }
}
