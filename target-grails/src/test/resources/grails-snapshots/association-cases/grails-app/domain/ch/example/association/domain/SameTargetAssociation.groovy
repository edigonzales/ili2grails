package ch.example.association.domain

class SameTargetAssociation {

    Person primaryPersonId
    Person secondaryPersonId

    static final Map<String, Map<String, Object>> interlisFieldMeta = [
        primaryPersonId: [label: 'PrimaryPerson', qualifiedName: 'AssociationCases.Base.SameTargetAssociation.PrimaryPerson'],
        secondaryPersonId: [label: 'SecondaryPerson', qualifiedName: 'AssociationCases.Base.SameTargetAssociation.SecondaryPerson']
    ]

    static final Map<String, Map<String, Object>> interlisRelationshipMeta = [
        primaryPersonId: [targetClass: 'Person', semanticKind: 'ASSOCIATION_ROLE', label: 'PrimaryPerson', sourceAttribute: 'primary_person_id', targetRole: 'PrimaryPerson', association: 'AssociationCases.Base.SameTargetAssociation', mandatory: false],
        secondaryPersonId: [targetClass: 'Person', semanticKind: 'ASSOCIATION_ROLE', label: 'SecondaryPerson', sourceAttribute: 'secondary_person_id', targetRole: 'SecondaryPerson', association: 'AssociationCases.Base.SameTargetAssociation', mandatory: false]
    ]

    static mapping = {
        table 'sametargetassociation'
        id column: 't_id', generator: 'identity'
        version false
        columns {
            primaryPersonId column: 'primary_person_id'
            secondaryPersonId column: 'secondary_person_id'
        }
    }

    static constraints = {
        primaryPersonId nullable: true
        secondaryPersonId nullable: true
    }
}
