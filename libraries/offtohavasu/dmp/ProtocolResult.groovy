library(
    name: "DMP Protocol Result",
    base: "app",
    author: "Curtis Cienfuegos",
    category: "Security",
    namespace: "offtohavasu",
    documentationLink: "https://github.com/offtohavasu/Hubitat-DMP",
    version: "0.1.0-alpha1",
    description: "Hubitat library containing the protocol result model used by DMP responses."
)

package offtohavasu.dmp

/**
 * General protocol response types returned by DMPProtocol.
 */
enum ProtocolResult {
    ACK,
    NAK
}