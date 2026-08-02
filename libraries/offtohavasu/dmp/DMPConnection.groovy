library(
    name: "DMP Connection",
    base: "app",
    author: "Curtis Cienfuegos",
    category: "Security",
    namespace: "offtohavasu",
    documentationLink: "https://github.com/offtohavasu/Hubitat-DMP",
    version: "0.1.0-alpha1",
    description: "Hubitat library for the TCP transport layer used by the DMP client."
)

package offtohavasu.dmp

import hubitat.helper.SocketWrapper
import hubitatdmp.util.Logger

/**
 * Transport-only DMP socket connection for Hubitat.
 *
 * This class is responsible for TCP connectivity only. Protocol parsing is
 * delegated to the supplied DMPProtocol instance via decodeResponse().
 */
class DMPConnection implements hubitat.helper.Interface {

    private final DMPProtocol protocol
    private final String host
    private final int port
    private final Object owner
    private final int reconnectDelaySeconds
    private final int keepaliveIntervalSeconds
    private final DMPClient client

    private SocketWrapper socket
    private boolean connected
    private boolean reconnectPending
    private boolean shutdownRequested
    private boolean keepaliveActive
    private boolean keepaliveScheduled
    private final boolean debugLogging

    DMPConnection(DMPProtocol protocol, String host, int port, Object owner = null, int reconnectDelaySeconds = 5, int keepaliveIntervalSeconds = 10, DMPClient client = null, boolean debugLogging = false) {
        if (protocol == null) {
            throw new IllegalArgumentException("protocol must not be null")
        }
        if (!host) {
            throw new IllegalArgumentException("host must not be blank")
        }
        if (port <= 0) {
            throw new IllegalArgumentException("port must be greater than zero")
        }

        this.protocol = protocol
        this.host = host
        this.port = port
        this.owner = owner
        this.reconnectDelaySeconds = Math.max(1, reconnectDelaySeconds)
        this.keepaliveIntervalSeconds = Math.max(1, keepaliveIntervalSeconds)
        this.client = client
        this.connected = false
        this.reconnectPending = false
        this.shutdownRequested = false
        this.keepaliveActive = false
        this.keepaliveScheduled = false
        this.debugLogging = debugLogging
        this.socket = new SocketWrapper(this)
    }

    boolean connect() {
        if (isConnected()) {
            return true
        }

        shutdownRequested = false
        reconnectPending = false

        try {
            logInfo("Connecting to ${host}:${port}")
            socket.connect(host, port, 10000)
            connected = true
            logDebug("TCP connection established to ${host}:${port}")
            logInfo("Connected to ${host}:${port}")
            startKeepalive()
            return true
        } catch (Exception e) {
            connected = false
            logError("Connection attempt failed for ${host}:${port}: ${e.message}", e)
            scheduleReconnect()
            return false
        }
    }

    void disconnect() {
        logDebug("Disconnect requested for ${host}:${port}")
        shutdownRequested = true
        reconnectPending = false
        stopKeepalive()

        try {
            if (socket != null) {
                socket.disconnect()
            }
        } catch (Exception e) {
            logWarn("Disconnect warning: ${e.message}")
        } finally {
            connected = false
        }
    }

    boolean reconnect() {
        logDebug("Reconnect requested for ${host}:${port}")
        disconnect()
        shutdownRequested = false
        return connect()
    }

    boolean isConnected() {
        return connected && socket != null && socket.isConnected()
    }

    boolean send(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return false
        }

        if (!isConnected()) {
            logWarn("Dropping outbound payload because socket is not connected")
            return false
        }

        try {
            socket.send(payload)
            return true
        } catch (Exception e) {
            logError("Failed to send payload: ${e.message}", e)
            connected = false
            scheduleReconnect()
            return false
        }
    }

    boolean sendCommand(String command, Map args = [:]) {
        if (!command) {
            return false
        }

        try {
            byte[] payload = protocol.encodeCommand(command, args)
            if (client != null) {
                client.noteOutboundCommand(command)
            }
            return send(payload)
        } catch (Exception e) {
            logError("Failed to encode/send command '${command}': ${e.message}", e)
            return false
        }
    }

    void onSocketStatus(Object status) {
        String state = status?.toString() ?: "unknown"
        logInfo("Socket status: ${state}")

        if (state.equalsIgnoreCase("connected") || state.equalsIgnoreCase("open")) {
            connected = true
            reconnectPending = false
            startKeepalive()
        } else if (state.equalsIgnoreCase("disconnected") || state.equalsIgnoreCase("closed") || state.equalsIgnoreCase("error")) {
            connected = false
            stopKeepalive()
            if (!shutdownRequested && !reconnectPending) {
                scheduleReconnect()
            }
        }
    }

    void onSocketData(Object data) {
        if (data == null) {
            return
        }

        try {
            byte[] payload = data instanceof byte[] ? (byte[]) data : data.toString().getBytes("UTF-8")
            if (payload != null && payload.length > 0) {
                Object decoded = protocol.decodeResponse(payload)
                if (client != null) {
                    client.handleResponse(decoded)
                }
            }
        } catch (Exception e) {
            logError("Failed to pass socket data to protocol: ${e.message}", e)
        }
    }

    void onSocketError(Object error) {
        String message = error?.toString() ?: "unknown socket error"
        logError("Socket error: ${message}")
        connected = false
        stopKeepalive()
        if (!shutdownRequested && !reconnectPending) {
            scheduleReconnect()
        }
    }

    private void startKeepalive() {
        if (!connected || !isConnected() || keepaliveActive || shutdownRequested) {
            return
        }

        keepaliveActive = true
        keepaliveScheduled = false
        logInfo("Starting keepalive every ${keepaliveIntervalSeconds}s")
        runIn(keepaliveIntervalSeconds, keepaliveIntervalSeconds, "keepalive")
    }

    private void stopKeepalive() {
        if (!keepaliveActive && !keepaliveScheduled) {
            return
        }
        keepaliveActive = false
        keepaliveScheduled = false
        logInfo("Stopping keepalive")
    }

    private void scheduleReconnect() {
        if (reconnectPending || shutdownRequested) {
            return
        }

        reconnectPending = true
        logDebug("Reconnect scheduled for ${host}:${port} in ${reconnectDelaySeconds}s")
        logWarn("Scheduling reconnect in ${reconnectDelaySeconds}s")

        try {
            runIn(5, reconnectDelaySeconds, "reconnect")
        } catch (Exception e) {
            logError("Failed to schedule reconnect: ${e.message}", e)
            reconnectPending = false
        }
    }

    private void runIn(int delaySeconds, int count, String action) {
        if (owner == null) {
            return
        }

        owner?.metaClass?.invokeMethod(owner, 'runIn', [delaySeconds, { ->
            if (shutdownRequested) {
                return
            }
            if (action == 'reconnect' && !reconnectPending) {
                reconnect()
            } else if (action == 'keepalive' && connected && isConnected() && !keepaliveActive) {
                keepaliveActive = true
                logDebug("Keepalive sent")
                sendCommand('!H')
                keepaliveActive = false
                if (connected && isConnected()) {
                    runIn(keepaliveIntervalSeconds, keepaliveIntervalSeconds, 'keepalive')
                }
            }
        }] as Object[])
    }

    private void logDebug(String msg) {
        if (debugLogging) {
            Logger.debug(owner, msg)
        }
    }

    private void logInfo(String msg) {
        Logger.info(owner, msg)
    }

    private void logWarn(String msg) {
        Logger.warn(owner, msg)
    }

    private void logError(String msg, Exception e = null) {
        if (e != null) {
            Logger.error(owner, "${msg}: ${e.message}")
        } else {
            Logger.error(owner, msg)
        }
    }
}
