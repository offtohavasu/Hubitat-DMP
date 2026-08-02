package offtohavasu.dmp

import java.util.regex.Pattern

import hubitatdmp.protocol.Constants
import offtohavasu.dmp.model.AreaStatus
import offtohavasu.dmp.model.OutputStatus
import offtohavasu.dmp.model.OutputsResponse
import offtohavasu.dmp.model.StatusResponse
import offtohavasu.dmp.model.ZoneStatus

/**
 * Direct Groovy port of reference/pydmp/protocol.py.
 */
class DMPProtocol {

    private static final String MESSAGE_PREFIX = Constants.MESSAGE_PREFIX
    private static final String MESSAGE_TERMINATOR = Constants.MESSAGE_TERMINATOR
    private static final String RESPONSE_DELIMITER = Constants.RESPONSE_DELIMITER
    private static final String ZONE_DELIMITER = Constants.ZONE_DELIMITER

    private static final Pattern AUTH_REDACT_PATTERN = Pattern.compile("(!V2)[^\\r]*")

    private final String accountNumber
    private final String remoteKey
    private final DMPCrypto crypto
    private String lastNakDetail

    DMPProtocol(String accountNumber, String remoteKey = "") {
        String normalizedAccount = accountNumber == null ? "" : accountNumber.toString()
        this.accountNumber = normalizedAccount.padLeft(5, ' ')
        if (this.accountNumber.length() != 5) {
            throw new IllegalArgumentException("Account number must be 5 digits or less")
        }

        this.remoteKey = remoteKey ?: ""
        int accountInt = Integer.parseInt(this.accountNumber.trim() ?: "0")
        this.crypto = new DMPCrypto(accountInt, this.remoteKey)
        this.lastNakDetail = null
    }

    String getLastNakDetail() {
        return lastNakDetail
    }

    byte[] encodeCommand(String command, Map<String, Object> kwargs = [:]) {
        try {
            String formattedCommand = formatCommand(command, kwargs)
            String message = "${MESSAGE_PREFIX}${this.accountNumber}${formattedCommand}${MESSAGE_TERMINATOR}"
            return message.getBytes("UTF-8")
        } catch (Exception e) {
            throw new DMPProtocolError("Failed to encode command: ${e.message}", e)
        }
    }

    Object decodeResponse(Object response) {
        if (response == null) {
            return null
        }

        try {
            String decoded = response instanceof byte[] ? new String((byte[]) response, "UTF-8") : response.toString()
            this.lastNakDetail = null

            List<String> lines = decoded.split(Pattern.quote(RESPONSE_DELIMITER)) as List<String>
            StatusResponse statusResponse = new StatusResponse(areas: [:], zones: [:])
            OutputsResponse outputsResponse = new OutputsResponse(outputs: [:])
            boolean hasStatusData = false
            boolean hasOutputData = false

            for (String line : lines) {
                if (!line) {
                    continue
                }

                if (line.length() < 8) {
                    continue
                }

                int ackPos = -1
                String ackNakChar = ""
                for (int i = 6; i < Math.min(line.length(), 12); i++) {
                    String current = line.substring(i, i + 1)
                    if (current == "+" || current == "-") {
                        ackPos = i
                        ackNakChar = current
                        break
                    }
                }

                String cmdWithPrefix = (ackPos != -1 && line.length() > ackPos + 2) ? line.substring(ackPos + 1, ackPos + 3) : ""
                if (cmdWithPrefix == "!V") {
                    continue
                }

                if (isCommandAcknowledgement(cmdWithPrefix) || isCommandShortForm(cmdWithPrefix)) {
                    if (ackNakChar == "+") {
                        return "ACK"
                    }
                    if (ackNakChar == "-") {
                        String detail = ""
                        if (line.length() >= ackPos + 3) {
                            String shortCode = line.substring(ackPos + 1, ackPos + 3)
                            if (shortCode.length() > 0 && shortCode[0:1] in ["C", "O", "X", "Y", "Q"]) {
                                detail = shortCode
                            }
                        }
                        if (!detail && cmdWithPrefix.length() == 2 && cmdWithPrefix.startsWith("!")) {
                            detail = cmdWithPrefix.substring(1)
                        }
                        this.lastNakDetail = detail ?: null
                        return "NAK"
                    }
                }

                int markerPos = -1
                for (String marker : ["*WB", "!WB", "?WB"]) {
                    int pos = line.indexOf(marker)
                    if (pos != -1) {
                        markerPos = pos
                        break
                    }
                }
                if (markerPos != -1 && line.length() > markerPos + 3) {
                    String payload = line.substring(markerPos + 3)
                    parseStatusLine(payload, statusResponse)
                    hasStatusData = true
                }

                markerPos = -1
                for (String marker : ["*WQ", "?WQ", "!WQ"]) {
                    int pos = line.indexOf(marker)
                    if (pos != -1) {
                        markerPos = pos
                        break
                    }
                }
                if (markerPos != -1 && line.length() > markerPos + 3) {
                    String payload = line.substring(markerPos + 3)
                    parseOutputStatusLine(payload, outputsResponse)
                    hasOutputData = true
                }

                if (line.contains("*P=")) {
                    return parseUserCodesLine(line.split(Pattern.quote("*P="), 2)[1])
                }

                if (line.contains("*U")) {
                    return parseUserProfilesLine(line.substring(line.indexOf("*U") + 2))
                }
            }

            if (hasStatusData) {
                return statusResponse
            }
            if (hasOutputData) {
                return outputsResponse
            }

            return null
        } catch (Exception e) {
            throw new DMPInvalidResponseError("Failed to decode response: ${e.message}", e)
        }
    }

    private static String redactAuth(String frame) {
        return AUTH_REDACT_PATTERN.matcher(frame).replaceAll("$1<redacted>")
    }

    private static boolean isCommandAcknowledgement(String cmdWithPrefix) {
        return cmdWithPrefix.length() == 2 && cmdWithPrefix.startsWith("!") && (cmdWithPrefix[1] in ["C", "O", "X", "Y", "Q"])
    }

    private static boolean isCommandShortForm(String cmdWithPrefix) {
        return cmdWithPrefix.length() >= 1 && cmdWithPrefix[0:1] in ["C", "O", "X", "Y", "Q"]
    }

    private String formatCommand(String command, Map<String, Object> kwargs) {
        StringBuilder formatted = new StringBuilder()
        int index = 0
        while (index < command.length()) {
            char current = command.charAt(index)
            if (current == '{') {
                if (index + 1 < command.length() && command.charAt(index + 1) == '{') {
                    formatted << '{'
                    index += 2
                    continue
                }
                int endIndex = command.indexOf('}', index + 1)
                if (endIndex == -1) {
                    throw new DMPProtocolError("Failed to encode command: invalid format string")
                }
                String key = command.substring(index + 1, endIndex)
                if (!kwargs.containsKey(key)) {
                    throw new DMPProtocolError("Failed to encode command: missing key '${key}'")
                }
                formatted << kwargs[key]?.toString() ?: ''
                index = endIndex + 1
            } else if (current == '}') {
                if (index + 1 < command.length() && command.charAt(index + 1) == '}') {
                    formatted << '}'
                    index += 2
                } else {
                    throw new DMPProtocolError("Failed to encode command: invalid format string")
                }
            } else {
                formatted << current
                index++
            }
        }
        return formatted.toString()
    }

    private void parseStatusLine(String statusData, StatusResponse response) {
        if (!statusData || statusData.startsWith("-\r")) {
            return
        }

        List<String> items = statusData.split(Pattern.quote(ZONE_DELIMITER)) as List<String>
        for (String item : items) {
            if (item.length() < 5) {
                continue
            }

            String itemType = item.substring(0, 1)
            if (itemType == "A") {
                String number = item.substring(1, 4)
                String stateChar = item.substring(4, 5)
                String name = item.substring(5).trim()
                String areaNum = number.trim()
                if (!areaNum) {
                    continue
                }

                String state = stateChar in ["A", "D", "S"] ? stateChar : "unknown"
                response.areas[areaNum] = new AreaStatus(number: areaNum, state: state, name: name)
            } else if (itemType == "L") {
                String number = item.substring(1, 4)
                String stateChar = item.substring(4, 5)
                String name = item.substring(5).trim()
                String state = stateChar in ["N", "O", "S", "X", "L", "M"] ? stateChar : "unknown"
                response.zones[number] = new ZoneStatus(number: number, state: state, name: name)
            }
        }
    }

    private void parseOutputStatusLine(String data, OutputsResponse response) {
        if (!data || data.startsWith("-\r") || data == "-") {
            return
        }

        List<String> items = data.split(Pattern.quote(ZONE_DELIMITER)) as List<String>
        for (String item : items) {
            if (item.length() < 5) {
                continue
            }
            String number = item.substring(0, 3)
            String mode = item.substring(3, 4)
            String name = item.substring(4).trim()
            response.outputs[number] = new OutputStatus(number: number, mode: mode, name: name)
        }
    }

    private UserCodesResponse parseUserCodesLine(String data) {
        List<UserCode> users = []
        boolean hasMore = false
        String lastNumber = null

        for (String item : data.split(Pattern.quote(ZONE_DELIMITER))) {
            String plainItem = item.rstrip("\r")
            if (!plainItem) {
                continue
            }
            if (plainItem.startsWith("----")) {
                hasMore = true
                continue
            }
            try {
                String plain = crypto.decryptString(plainItem)
                if (plain.length() < 44) {
                    continue
                }
                String number = plain.substring(0, 4)
                String code = plain.substring(4, 16).split("F", 2)[0]
                String pin = plain.substring(16, 22).split("F", 2)[0]
                String p1 = plain.substring(22, 25)
                String p2 = plain.substring(25, 28)
                String p3 = plain.substring(28, 31)
                String p4 = plain.substring(31, 34)
                String endDate = plain.substring(34, 40)
                String legacyExp = plain.substring(40, 44)
                String tail = plain.substring(44)
                String flags = null
                String startDate = null
                String name = ""
                if (tail) {
                    String maybeFlags = tail.length() >= 3 ? tail.substring(0, 3) : ""
                    String maybeDate = tail.length() >= 9 ? tail.substring(3, 9) : ""
                    if (maybeFlags.length() == 3 && maybeFlags.toList().every { it in ["Y", "N"] } && maybeDate.length() == 6 && maybeDate.isNumber()) {
                        flags = maybeFlags
                        startDate = maybeDate
                        name = tail.substring(9)
                    } else {
                        name = tail
                    }
                }
                lastNumber = number
                users << new UserCode(
                        number: number,
                        code: code,
                        pin: pin,
                        profiles: [p1, p2, p3, p4],
                        tempDate: endDate,
                        expDate: legacyExp,
                        startDate: startDate,
                        endDate: endDate,
                        flags: flags,
                        active: flags != null ? flags[0] == "Y" : null,
                        temporary: flags != null ? flags[2] == "Y" : null,
                        name: name
                )
            } catch (Exception e) {
                throw new DMPInvalidResponseError("Malformed user code data: ${e.message}", e)
            }
        }
        return new UserCodesResponse(users: users, hasMore: hasMore, lastNumber: lastNumber)
    }

    private UserProfilesResponse parseUserProfilesLine(String data) {
        List<UserProfile> profiles = []
        boolean hasMore = false
        String lastNumber = null

        for (String item : data.split(Pattern.quote(ZONE_DELIMITER))) {
            String plainItem = item.rstrip("\r")
            if (!plainItem) {
                continue
            }
            if (plainItem.startsWith("----")) {
                hasMore = true
                continue
            }
            String number = plainItem.substring(0, 3)
            String areas = plainItem.substring(3, 11)
            String accessAreas = plainItem.substring(11, 19)
            String outputGroup = plainItem.substring(19, 22)
            String menu = plainItem.substring(22, 30)
            String rearm = plainItem.length() >= 49 ? plainItem.substring(46, 49) : ""
            String name = plainItem.length() >= 49 ? plainItem.substring(49) : plainItem.substring(30)
            lastNumber = number
            profiles << new UserProfile(
                    number: number,
                    areasMask: areas,
                    accessAreasMask: accessAreas,
                    outputGroup: outputGroup,
                    menuOptions: menu,
                    rearmDelay: rearm,
                    name: name
            )
        }
        return new UserProfilesResponse(profiles: profiles, hasMore: hasMore, lastNumber: lastNumber)
    }
}

class UserCode {
    String number
    String code
    String pin
    List<String> profiles
    String tempDate
    String expDate
    String name
    String startDate
    String endDate
    String flags
    Boolean active
    Boolean temporary
}

class UserCodesResponse {
    List<UserCode> users
    boolean hasMore
    String lastNumber
}

class UserProfile {
    String number
    String areasMask
    String accessAreasMask
    String outputGroup
    String menuOptions
    String rearmDelay
    String name
}

class UserProfilesResponse {
    List<UserProfile> profiles
    boolean hasMore
    String lastNumber
}

class DMPProtocolError extends Exception {
    DMPProtocolError(String message) {
        super(message)
    }

    DMPProtocolError(String message, Throwable cause) {
        super(message, cause)
    }
}

class DMPInvalidResponseError extends DMPProtocolError {
    DMPInvalidResponseError(String message) {
        super(message)
    }

    DMPInvalidResponseError(String message, Throwable cause) {
        super(message, cause)
    }
}