package ch.example.association.domain

class PhysicalMismatchAssociation {

    Person ownerFk
    Parcel parcelFk

    static final Map<String, Map<String, Object>> interlisFieldMeta = [
        ownerFk: [label: 'SemanticOwner', qualifiedName: 'AssociationCases.Base.PhysicalMismatchAssociation.SemanticOwner'],
        parcelFk: [label: 'OwnedParcel', qualifiedName: 'AssociationCases.Base.PhysicalMismatchAssociation.OwnedParcel']
    ]

    static mapping = {
        table 'physicalmismatchassociation'
        id column: 't_id', generator: 'identity'
        version false
        columns {
            ownerFk column: 'owner_fk'
            parcelFk column: 'parcel_fk'
        }
    }

    static constraints = {
        parcelFk nullable: true
    }
}
