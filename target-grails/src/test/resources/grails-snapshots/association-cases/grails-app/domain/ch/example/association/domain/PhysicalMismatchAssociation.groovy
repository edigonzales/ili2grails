package ch.example.association.domain

class PhysicalMismatchAssociation {

    Person ownerFk
    Parcel parcelFk

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
