package ch.interlis.generator.grails.runtime.display

import ch.interlis.generator.grails.runtime.api.descriptor.DisplayDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.DomainKind
import spock.lang.Specification

class DefaultInterlisDisplayLabelResolverSpec extends Specification {

    private static final DomainDescriptor DOMAIN = new DomainDescriptor(
        'M.T.P', 'M', 'T', 'com.example.P', 'p', 'P', 'P', DomainKind.CLASS,
        true, new DisplayDescriptor(null, ['name', 'code'], []), [:], [:], [:], [:])

    def "uses the first non-blank display field"() {
        given:
        def resolver = new DefaultInterlisDisplayLabelResolver()
        def instance = new Person(name: 'Anna', code: 'A-1')

        expect:
        resolver.labelFor(instance, DOMAIN) == 'Anna'
    }

    def "falls back to the record id"() {
        given:
        def resolver = new DefaultInterlisDisplayLabelResolver()
        def instance = new Person(id: 42)

        expect:
        resolver.labelFor(instance, DOMAIN) == '42'
    }

    def "returns empty label for null instance"() {
        expect:
        new DefaultInterlisDisplayLabelResolver().labelFor(null, DOMAIN) == ''
    }

    static class Person {
        Long id
        String name
        String code
    }
}
