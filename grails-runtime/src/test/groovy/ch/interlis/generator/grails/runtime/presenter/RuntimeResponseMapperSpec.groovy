package ch.interlis.generator.grails.runtime.presenter

import ch.interlis.generator.grails.runtime.api.command.AssociationCommandResult
import ch.interlis.generator.grails.runtime.api.command.CommandCode
import ch.interlis.generator.grails.runtime.api.command.CommandStatus
import ch.interlis.generator.grails.runtime.api.command.FieldError
import ch.interlis.generator.grails.runtime.api.command.InverseRelationshipCommandResult
import ch.interlis.generator.grails.runtime.api.command.ReassignmentConfirmation
import spock.lang.Specification

class RuntimeResponseMapperSpec extends Specification {

    def "maps association created result to legacy json shape"() {
        given:
        def result = AssociationCommandResult.created('42', 'interlis.association.created',
            'Die Zuordnung wurde erstellt.')

        when:
        Map<String, Object> map = RuntimeResponseMapper.toMap(result)

        then:
        map.success == true
        map.status == 201
        map.code == 'CREATED'
        map.messageCode == 'interlis.association.created'
        map.associationId == '42'
        map.fieldErrors == [:]
    }

    def "maps association validation errors to legacy field errors map"() {
        given:
        def result = new AssociationCommandResult(
            false, 422, CommandStatus.VALIDATION_ERROR, CommandCode.VALIDATION_FAILED,
            'interlis.association.validationFailed', 'nicht gespeichert', null,
            [new FieldError('name', 'blank', 'Name fehlt')])

        when:
        Map<String, Object> map = RuntimeResponseMapper.toMap(result)

        then:
        map.success == false
        map.status == 422
        map.fieldErrors == [name: 'Name fehlt']
    }

    def "maps reassignment confirmation payload"() {
        given:
        def confirmation = new ReassignmentConfirmation(
            '7', 'Sieben', '3', 'Alt', '9', 'Neu', 'Zieltyp')
        def result = InverseRelationshipCommandResult.reassignmentRequired(confirmation)

        when:
        Map<String, Object> map = RuntimeResponseMapper.toMap(result)

        then:
        map.status == 409
        map.code == 'REASSIGNMENT_CONFIRMATION_REQUIRED'
        map.relatedId == '7'
        map.relatedLabel == 'Sieben'
        map.previousOwnerId == '3'
        map.previousOwnerLabel == 'Alt'
        map.newOwnerId == '9'
        map.newOwnerLabel == 'Neu'
        map.targetTypeLabel == 'Zieltyp'
    }

    def "maps null results to internal error fallback"() {
        when:
        Map<String, Object> map = RuntimeResponseMapper.toMap(null as AssociationCommandResult)

        then:
        map.success == false
        map.status == 500
        map.code == 'INTERNAL_ERROR'
    }
}
