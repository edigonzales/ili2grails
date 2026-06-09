package ch.example.association.domain

class ExternalCompositeAssociation {

    Building buildingId
    Person ownerId

    static final Map<String, Map<String, Object>> interlisFieldMeta = [
        buildingId: [label: 'Buildings', qualifiedName: 'AssociationCases.Base.ExternalCompositeAssociation.Buildings'],
        ownerId: [label: 'Owner', qualifiedName: 'AssociationCases.Base.ExternalCompositeAssociation.Owner']
    ]

    static final Map<String, Map<String, Object>> interlisRelationshipMeta = [
        buildingId: [targetClass: 'Building', semanticKind: 'ASSOCIATION_ROLE', label: 'Buildings', sourceAttribute: 'building_id', targetRole: 'Buildings', association: 'AssociationCases.Base.ExternalCompositeAssociation', mandatory: false],
        ownerId: [targetClass: 'Person', semanticKind: 'ASSOCIATION_ROLE', label: 'Owner', sourceAttribute: 'owner_id', targetRole: 'Owner', association: 'AssociationCases.Base.ExternalCompositeAssociation', mandatory: true]
    ]

    static mapping = {
        table 'externalcompositeassociation'
        id column: 't_id', generator: 'identity'
        version false
        columns {
            buildingId column: 'building_id'
            ownerId column: 'owner_id'
        }
    }

    static constraints = {
        buildingId nullable: true
    }
}
