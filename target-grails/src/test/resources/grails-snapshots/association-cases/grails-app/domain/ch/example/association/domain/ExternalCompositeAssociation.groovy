package ch.example.association.domain

class ExternalCompositeAssociation {

    Building buildingId
    Person ownerId

    static final Map<String, Map<String, Object>> interlisFieldMeta = [
        buildingId: [label: 'Buildings', qualifiedName: 'AssociationCases.Base.ExternalCompositeAssociation.Buildings'],
        ownerId: [label: 'Owner', qualifiedName: 'AssociationCases.Base.ExternalCompositeAssociation.Owner']
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
