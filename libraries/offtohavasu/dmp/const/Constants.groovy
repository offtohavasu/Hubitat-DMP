package hubitatdmp.protocol

class Constants {
    static final Integer DEFAULT_PORT = 2011
    static final BigDecimal RATE_LIMIT_SECONDS = 0.3G
    static final String MESSAGE_TERMINATOR = "\r"
    static final String MESSAGE_PREFIX = "@"
    static final String RESPONSE_DELIMITER = "\u0002"
    static final String ZONE_DELIMITER = "\u001E"
}
