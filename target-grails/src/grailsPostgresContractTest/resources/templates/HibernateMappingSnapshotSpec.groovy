package com.example

import grails.testing.mixin.integration.Integration
import groovy.json.JsonOutput
import spock.lang.Specification

@Integration
class HibernateMappingSnapshotSpec extends Specification {

    def grailsApplication
    def sessionFactory

    def "collects the actual Hibernate mapping"() {
        when:
        def snapshot = HibernateMappingSnapshotCollector.collect(grailsApplication, sessionFactory)

        then:
        snapshot.entities.size() > 0

        when:
        def target = new File('build/ili2grails-contract/hibernate-mapping.json')
        target.parentFile.mkdirs()
        target.text = JsonOutput.prettyPrint(JsonOutput.toJson(snapshot))

        then:
        target.exists()
        target.text.contains('entities')
    }
}
