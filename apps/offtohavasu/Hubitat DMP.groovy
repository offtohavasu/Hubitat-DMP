#include DMP_Client

definition(
    name: "Hubitat DMP",
    namespace: "offtohavasu",
    author: "Curtis & ChatGPT",
    description: "Parent app for XT30 communication testing",
    singleInstance: true
)

preferences {
    page(name: "mainPage")
}

void installed() {
    initialize()
}

void updated() {
    unsubscribe()
    initialize()
}

void initialize() {
    state.client = null
    state.connected = false
    state.authenticated = false
    state.lastKeepAlive = null
    state.lastError = null

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

void appButtonHandler(buttonName) {
    switch (buttonName) {
        case 'connectButton':
            connectToPanel()
            break
        case 'disconnectButton':
            disconnectFromPanel()
            break
        case 'testButton':
            testConnection()
            break
    }
}

void connectToPanel() {
    try {
        if (!settings.panelIp || !settings.panelPort || !settings.accountNumber) {
            state.lastError = 'Panel IP, port, and account number are required'
            updateDisplay()
            return
        }

        if (state.client == null) {
            state.client = new DMPClient(settings.panelIp, settings.panelPort.toInteger(), settings.accountNumber, settings.remoteKey ?: '', this, 5, 10, settings.debugLogging ?: false)
        }

        boolean transportConnected = state.client.connect()
        state.connected = state.client.isConnected()

        if (transportConnected && state.client.isConnected()) {
            boolean authenticated = state.client.login()
            state.authenticated = state.client.isAuthenticated()
            if (authenticated) {
                state.lastError = null
            }
        } else {
            state.authenticated = false
        }

        state.lastKeepAlive = state.client.getLastKeepAlive()
        state.lastError = state.client.getLastError()
        updateDisplay()
    } catch (Exception e) {
        state.lastError = e.message
        state.connected = false
        state.authenticated = false
        updateDisplay()
    }
}

void disconnectFromPanel() {
    try {
        if (state.client != null) {
            state.client.disconnect()
        }
    } catch (Exception e) {
        state.lastError = e.message
    } finally {
        state.connected = false
        state.authenticated = false
        state.lastKeepAlive = null
        state.lastError = state.lastError ?: 'Disconnected'
        updateDisplay()
    }
}

void testConnection() {
    if (state.client == null) {
        connectToPanel()
        return
    }

    try {
        Object status = state.client.requestStatus()
        state.connected = state.client.isConnected()
        state.authenticated = state.client.isAuthenticated()
        state.lastKeepAlive = state.client.getLastKeepAlive()
        state.lastError = state.client.getLastError()
        updateDisplay()
    } catch (Exception e) {
        state.lastError = e.message
        state.connected = false
        state.authenticated = false
        updateDisplay()
    }
}

void updateDisplay() {
    state.connected = state.client?.isConnected() ?: false
    state.authenticated = state.client?.isAuthenticated() ?: false
    state.lastKeepAlive = state.client?.getLastKeepAlive()
    state.lastError = state.client?.getLastError() ?: state.lastError
    sendEvent(name: "connected", value: state.connected ? "true" : "false")
    sendEvent(name: "authenticated", value: state.authenticated ? "true" : "false")
    sendEvent(name: "lastKeepAlive", value: state.lastKeepAlive?.toString())
    sendEvent(name: "lastError", value: state.lastError ?: "")
}
