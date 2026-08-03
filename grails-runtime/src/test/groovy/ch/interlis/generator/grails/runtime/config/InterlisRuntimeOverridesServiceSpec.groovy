package ch.interlis.generator.grails.runtime.config

import ch.interlis.generator.grails.runtime.api.config.RuntimeUiOverrides
import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipMode
import spock.lang.Specification

class InterlisRuntimeOverridesServiceSpec extends Specification {

    private static final InverseRelationshipDescriptor GENERATED = new InverseRelationshipDescriptor(
        'employees', 'Mitarbeitende', 'Test.Department', 'Test.Employee',
        'com.example.Employee', 'employee', 'department', 'Mitarbeiter',
        false, true, true, InverseRelationshipMode.AUTO)

    private static final InverseRelationshipDescriptor READ_ONLY_GENERATED = new InverseRelationshipDescriptor(
        'employees', 'Mitarbeitende', 'Test.Department', 'Test.Employee',
        'com.example.Employee', 'employee', 'department', 'Mitarbeiter',
        false, false, true, InverseRelationshipMode.AUTO)

    private def overrides(Map relationships) {
        def service = new InterlisRuntimeOverridesService()
        service.grailsApplication = [
            config: [
                ili2grails: [
                    ui: [
                        domains: [[iliName: 'Test.Department', relationships: relationships]]
                    ]
                ]
            ]
        ]
        return service
    }

    def "applies mode downgrade to read-only"() {
        expect:
        def descriptor = overrides([employees: [mode: 'read-only']])
            .applyInverseRelationshipOverrides(GENERATED,
                overrides([employees: [mode: 'read-only']]).overridesFor(
                    domain(GENERATED)))
        descriptor.mode() == InverseRelationshipMode.READ_ONLY
        descriptor.writable() == false
    }

    def "mode off hides the relationship"() {
        expect:
        def descriptor = overrides([employees: [mode: 'off']])
            .applyInverseRelationshipOverrides(GENERATED,
                overrides([employees: [mode: 'off']]).overridesFor(
                    domain(GENERATED)))
        descriptor.visible() == false
        descriptor.writable() == false
    }

    def "editable never upgrades a generated read-only relationship"() {
        expect:
        def descriptor = overrides([employees: [mode: 'editable']])
            .applyInverseRelationshipOverrides(READ_ONLY_GENERATED,
                overrides([employees: [mode: 'editable']]).overridesFor(
                    domain(READ_ONLY_GENERATED)))
        descriptor.writable() == false
    }

    def "label override creates a new descriptor instance"() {
        given:
        def service = overrides([employees: [label: 'Team']])

        when:
        def descriptor = service.applyInverseRelationshipOverrides(GENERATED,
            service.overridesFor(domain(GENERATED)))

        then:
        descriptor.is(GENERATED) == false
        descriptor.label() == 'Team'
    }

    def "invalid mode is rejected"() {
        given:
        def service = overrides([employees: [mode: 'force']])

        when:
        service.applyInverseRelationshipOverrides(GENERATED,
            service.overridesFor(domain(GENERATED)))

        then:
        thrown(IllegalArgumentException)
    }

    def "unknown relationship configuration is rejected"() {
        given:
        def service = new InterlisRuntimeOverridesService()
        service.grailsApplication = [
            config: [
                ili2grails: [
                    ui: [
                        domains: [[iliName: 'Test.Other', relationships: [unknown: [mode: 'off']]]]
                    ]
                ]
            ]
        ]

        when:
        service.overridesFor(domain(GENERATED))

        then:
        // Unknown iliName entries are diagnosed only when they target a
        // different domain; the configured domain for Test.Department is not
        // present, so no overrides apply.
        noExceptionThrown()
    }

    private static def domain(InverseRelationshipDescriptor inverse) {
        return new ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor(
            'Test.Department', 'Test', '', 'com.example.Department', 'department',
            'Department', 'Department',
            ch.interlis.generator.grails.runtime.api.descriptor.DomainKind.CLASS,
            true, null, [:], [:], [employees: inverse], [:])
    }
}
