package ch.example.association.domain

class PhysicalMismatchAssociation {

    Person ownerFk
    Parcel parcelFk

    static final Map<String, Map<String, Object>> interlisFieldMeta = [
        ownerFk: [label: 'SemanticOwner', qualifiedName: 'AssociationCases.Base.PhysicalMismatchAssociation.SemanticOwner'],
        parcelFk: [label: 'OwnedParcel', qualifiedName: 'AssociationCases.Base.PhysicalMismatchAssociation.OwnedParcel']
    ]

    static final Map<String, Map<String, Object>> interlisRelationshipMeta = [
        ownerFk: [targetClass: 'Person', semanticKind: 'ASSOCIATION_ROLE', label: 'SemanticOwner', sourceAttribute: 'owner_fk', targetRole: 'SemanticOwner', association: 'AssociationCases.Base.PhysicalMismatchAssociation', mandatory: true],
        parcelFk: [targetClass: 'Parcel', semanticKind: 'ASSOCIATION_ROLE', label: 'OwnedParcel', sourceAttribute: 'parcel_fk', targetRole: 'OwnedParcel', association: 'AssociationCases.Base.PhysicalMismatchAssociation', mandatory: false]
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
