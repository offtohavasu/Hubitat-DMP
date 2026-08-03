library(
    name: "DMP_Zone_Status_Model",
    base: "app",
    author: "Curtis Cienfuegos",
    category: "Security",
    namespace: "offtohavasu",
    documentationLink: "https://github.com/offtohavasu/Hubitat-DMP",
    version: "0.1.0-alpha1",
    description: "Hubitat library model for DMP zone status data."
)

package offtohavasu.dmp.model

import groovy.transform.Immutable

@Immutable
class ZoneStatus {
    String number
    String state
    String name
}
