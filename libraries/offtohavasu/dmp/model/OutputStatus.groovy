library(
    name: "DMP Output Status Model",
    base: "app",
    author: "Curtis Cienfuegos",
    category: "Security",
    namespace: "offtohavasu",
    documentationLink: "https://github.com/offtohavasu/Hubitat-DMP",
    version: "0.1.0-alpha1",
    description: "Hubitat library model for DMP output status data."
)

package offtohavasu.dmp.model

import groovy.transform.Immutable

@Immutable
class OutputStatus {
    String number
    String state
    String name
}