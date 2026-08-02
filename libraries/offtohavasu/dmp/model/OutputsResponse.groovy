package offtohavasu.dmp.model

import groovy.transform.Immutable

@Immutable
class OutputsResponse {
    Map<String, OutputStatus> outputs
}