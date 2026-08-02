library(
    name: "DMP Crypto",
    base: "app",
    author: "Curtis Cienfuegos",
    category: "Security",
    namespace: "offtohavasu",
    documentationLink: "https://github.com/offtohavasu/Hubitat-DMP",
    version: "0.1.0-alpha1",
    description: "Hubitat library providing the crypto helpers used by DMP user-code parsing."
)

package offtohavasu.dmp

/**
 * Direct Groovy port of reference/pydmp/crypto.py.
 */
class DMPCrypto {
    static final String LFSR_CONTROL_STRING = "----2222222223333"

    private final int accountNumber
    private final String remoteKey
    private int seed

    DMPCrypto(int accountNumber, String remoteKey = "") {
        if (!(1 <= accountNumber && accountNumber <= 99999)) {
            throw new IllegalArgumentException("Account number must be between 1 and 99999")
        }
        this.accountNumber = accountNumber
        this.remoteKey = remoteKey ?: ""
        this.seed = 0
    }

    private int generateSeed(String userCode) {
        int codeInt = Integer.parseInt(userCode.substring(0, Math.min(4, userCode.length())))
        int baseSeed = (accountNumber + codeInt) & 0xFF

        int systemSeed = 0
        String rk = remoteKey ?: ""
        if (rk.length() >= 8) {
            try {
                int a = Integer.parseInt(rk.substring(0, 2), 16)
                int b = Integer.parseInt(rk.substring(6, 8), 16)
                systemSeed = a ^ b
            } catch (Exception ignored) {
                systemSeed = 0
            }
        }

        return baseSeed ^ systemSeed
    }

    private int performLfsr() {
        int currentSeed = seed
        int bit0 = currentSeed & 1
        int bit2 = (currentSeed >> 2) & 1
        int bit3 = (currentSeed >> 3) & 1
        int bit4 = (currentSeed >> 4) & 1
        int bitVal = bit0 ^ bit2 ^ bit3 ^ bit4

        currentSeed = currentSeed >> 1
        if (bitVal == 1) {
            currentSeed |= 0x80
        }
        if (currentSeed == 0) {
            currentSeed = 255
        }

        seed = currentSeed
        return currentSeed
    }

    String encryptString(String stringToEncrypt) {
        seed = generateSeed(stringToEncrypt.substring(0, Math.min(4, stringToEncrypt.length())))

        List<Character> result = stringToEncrypt.toList()
        int stringPos = 0

        for (String controlChar : LFSR_CONTROL_STRING.toList().collect { it.toString() }) {
            if (stringPos >= result.size()) {
                break
            }

            if (controlChar == "3") {
                if (stringPos + 3 <= result.size()) {
                    String workNumText = result.subList(stringPos, stringPos + 3).join('')
                    int workNum = Integer.parseInt(workNumText) & 0xFF
                    workNum = workNum ^ performLfsr()
                    String encrypted = String.format("%03d", workNum)
                    result[(stringPos)..<(stringPos + 3)] = encrypted.toList()
                    stringPos += 3
                }
            } else if (controlChar == "2") {
                if (stringPos + 2 <= result.size()) {
                    String workNumText = result.subList(stringPos, stringPos + 2).join('')
                    int workNum = Integer.parseInt(workNumText, 16)
                    workNum = workNum ^ performLfsr()
                    String encrypted = String.format("%02X", workNum)
                    result[(stringPos)..<(stringPos + 2)] = encrypted.toList()
                    stringPos += 2
                }
            } else {
                stringPos += 1
            }
        }

        return result.join('')
    }

    String decryptString(String stringToDecrypt) {
        return encryptString(stringToDecrypt)
    }

    String encryptUserCode(String userCode) {
        if (!userCode.isInteger() || !(4 <= userCode.length() && userCode.length() <= 6)) {
            throw new IllegalArgumentException("User code must be 4-6 digits")
        }
        String paddedCode = userCode.padRight(6, '0')
        return encryptString(paddedCode)
    }
}
