library(
    name: "DMP_Status_Response_Model",
    base: "app",
    author: "Curtis Cienfuegos",
    category: "Security",
    namespace: "offtohavasu",
    documentationLink: "https://github.com/offtohavasu/Hubitat-DMP",
    version: "0.1.0-alpha1",
    description: "Hubitat library model for DMP status response payloads."
)

package offtohavasu.dmp.model

import groovy.transform.Immutable

@Immutable
class StatusResponse {
    Map<String, AreaStatus> areas
    Map<String, ZoneStatus> zones
}