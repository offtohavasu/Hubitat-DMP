library(
    name: "DMP Area Status Model",
    base: "app",
    author: "Curtis Cienfuegos",
    category: "Security",
    namespace: "offtohavasu",
    documentationLink: "https://github.com/offtohavasu/Hubitat-DMP",
    version: "0.1.0-alpha1",
    description: "Hubitat library model for DMP area status data."
)

package offtohavasu.dmp.model

import groovy.transform.Immutable

@Immutable
class AreaStatus {
    String number
    String state
    String name
}