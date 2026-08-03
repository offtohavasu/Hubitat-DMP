library(
    name: "DMP_Protocol_Constants",
    base: "app",
    author: "Curtis Cienfuegos",
    category: "Security",
    namespace: "offtohavasu",
    documentationLink: "https://github.com/offtohavasu/Hubitat-DMP",
    version: "0.1.0-alpha1",
    description: "Hubitat library containing shared constants for the DMP protocol implementation."
)

package offtohavasu.dmp.const

class Constants {
    static final Integer DEFAULT_PORT = 2011
    static final BigDecimal RATE_LIMIT_SECONDS = 0.3G
    static final String MESSAGE_TERMINATOR = "\r"
    static final String MESSAGE_PREFIX = "@"
    static final String RESPONSE_DELIMITER = "\u0002"
    static final String ZONE_DELIMITER = "\u001E"
}
