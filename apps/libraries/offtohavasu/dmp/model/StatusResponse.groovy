package offtohavasu.dmp.model

import groovy.transform.Immutable

@Immutable
class StatusResponse {
    Map<String, AreaStatus> areas
    Map<String, ZoneStatus> zones
}