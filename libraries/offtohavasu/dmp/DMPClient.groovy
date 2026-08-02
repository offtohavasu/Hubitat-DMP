package offtohavasu.dmp

import groovy.transform.CompileStatic
import hubitatdmp.util.Logger

/**
 * High-level DMP panel session client.
 *
 * This class owns one transport connection and one protocol instance.
 * It is responsible for session state and basic command operations only.
 * Protocol parsing is delegated to DMPProtocol; transport is delegated to DMPConnection.
 */
@CompileStatic
class DMPClient {

    private final DMPConnection connection
    private final DMPProtocol protocol

    private boolean connected
    private boolean authenticated
    private Date lastKeepAlive
    private String lastError
    private String lastOutboundCommand
    private final boolean debugLogging
    private boolean panelIdentityLogged

    DMPClient(String host, int port, String accountNumber, String remoteKey = "", Object owner = null, int reconnectDelaySeconds = 5, int keepaliveIntervalSeconds = 10, boolean debugLogging = false) {
        this.protocol = new DMPProtocol(accountNumber, remoteKey)
        this.connection = new DMPConnection(this.protocol, host, port, owner, reconnectDelaySeconds, keepaliveIntervalSeconds, this, debugLogging)
        this.connected = false
        this.authenticated = false
        this.lastKeepAlive = null
        this.lastError = null
        this.lastOutboundCommand = null
        this.debugLogging = debugLogging
        this.panelIdentityLogged = false
    }

    boolean connect() {
        try {
            boolean ok = connection.connect()
            connected = ok
            if (!ok) {
                lastError = "Connection failed"
                return false
            }

            authenticated = login()
            if (!authenticated) {
                lastError = "Authentication failed"
                return false
            }

            lastError = null
            return true
        } catch (Exception e) {
            connected = false
            authenticated = false
            lastError = e.message
            logError("connect failed: ${e.message}", e)
            return false
        }
    }

    void disconnect() {
        logDebug("Disconnect")
        try {
            connection.disconnect()
        } catch (Exception e) {
            logError("disconnect failed: ${e.message}", e)
        } finally {
            connected = false
            authenticated = false
            lastError = null
        }
    }

    boolean login() {
        if (!connection.isConnected()) {
            lastError = "Not connected"
            return false
        }

        try {
            logDebug("Login command sent")
            boolean sent = connection.sendCommand('!V2{key}', [key: ''])
            if (!sent) {
                lastError = "Authentication frame not sent"
                return false
            }
            authenticated = true
            logDebug("Authentication success")
            lastError = null
            return true
        } catch (Exception e) {
            authenticated = false
            lastError = e.message
            logError("login failed: ${e.message}", e)
            return false
        }
    }

    Object requestStatus() {
        if (!connected || !authenticated) {
            lastError = "Not connected/authenticated"
            return null
        }

        try {
            logDebug("Status request sent")
            return connection.sendCommand('?WB**Y001', [:]) ? connection.protocol.decodeResponse(connection.protocol.encodeCommand('?WB**Y001', [:])) : null
        } catch (Exception e) {
            lastError = e.message
            logError("requestStatus failed: ${e.message}", e)
            return null
        }
    }

    Object requestOutputs() {
        if (!connected || !authenticated) {
            lastError = "Not connected/authenticated"
            return null
        }

        try {
            return connection.sendCommand('?WQ', [:]) ? connection.protocol.decodeResponse(connection.protocol.encodeCommand('?WQ', [:])) : null
        } catch (Exception e) {
            lastError = e.message
            logError("requestOutputs failed: ${e.message}", e)
            return null
        }
    }

    Object requestUsers() {
        if (!connected || !authenticated) {
            lastError = "Not connected/authenticated"
            return null
        }

        try {
            return connection.sendCommand('?P=0000', [:]) ? connection.protocol.decodeResponse(connection.protocol.encodeCommand('?P=0000', [:])) : null
        } catch (Exception e) {
            lastError = e.message
            logError("requestUsers failed: ${e.message}", e)
            return null
        }
    }

    Object requestProfiles() {
        if (!connected || !authenticated) {
            lastError = "Not connected/authenticated"
            return null
        }

        try {
            return connection.sendCommand('?U000', [:]) ? connection.protocol.decodeResponse(connection.protocol.encodeCommand('?U000', [:])) : null
        } catch (Exception e) {
            lastError = e.message
            logError("requestProfiles failed: ${e.message}", e)
            return null
        }
    }

    boolean armArea(int areaNumber, boolean bypassFaulted = false, boolean forceArm = false) {
        if (!connected || !authenticated) {
            lastError = "Not connected/authenticated"
            return false
        }

        try {
            String command = "!C${areaNumber},${bypassFaulted ? 'Y' : 'N'}${forceArm ? 'Y' : 'N'}"
            boolean sent = connection.sendCommand(command, [:])
            return sent
        } catch (Exception e) {
            lastError = e.message
            logError("armArea failed: ${e.message}", e)
            return false
        }
    }

    boolean disarmArea(int areaNumber) {
        if (!connected || !authenticated) {
            lastError = "Not connected/authenticated"
            return false
        }

        try {
            return connection.sendCommand("!O${areaNumber}", [:])
        } catch (Exception e) {
            lastError = e.message
            logError("disarmArea failed: ${e.message}", e)
            return false
        }
    }

    boolean setOutput(int outputNumber, String mode) {
        if (!connected || !authenticated) {
            lastError = "Not connected/authenticated"
            return false
        }

        try {
            return connection.sendCommand("!Q${outputNumber}${mode}", [:])
        } catch (Exception e) {
            lastError = e.message
            logError("setOutput failed: ${e.message}", e)
            return false
        }
    }

    boolean isConnected() {
        return connected && connection.isConnected()
    }

    boolean isAuthenticated() {
        return authenticated
    }

    Date getLastKeepAlive() {
        return lastKeepAlive
    }

    String getLastError() {
        return lastError
    }

    void handleResponse(Object response) {
        if (response == null) {
            return
        }

        logPanelIdentityIfKnown()

        if (response instanceof String) {
            if (response == 'ACK') {
                logDebug("ACK received")
                if (lastOutboundCommand == '!H') {
                    logDebug("Keepalive response received")
                }
                lastError = null
            } else if (response == 'NAK') {
                lastError = 'NAK received'
            }
        } else if (response instanceof Map) {
            if (lastOutboundCommand == '?WB**Y001') {
                logDebug("StatusResponse received")
            }
            lastError = null
        }

        if (response instanceof String && response == 'ACK') {
            lastKeepAlive = new Date()
        }
    }

    void noteOutboundCommand(String command) {
        lastOutboundCommand = command
    }

    private void logPanelIdentityIfKnown() {
        String identityInfo = protocol.getPanelIdentityInfo()
        if (identityInfo && !panelIdentityLogged) {
            Logger.info(this, "Panel identity/protocol info: ${identityInfo}")
            panelIdentityLogged = true
        }
    }

    private void logDebug(String msg) {
        if (debugLogging) {
            Logger.debug(this, msg)
        }
    }

    private void logError(String msg, Exception e = null) {
        if (e != null) {
            Logger.error(this, "${msg}: ${e.message}")
        } else {
            Logger.error(this, msg)
        }
    }
}
