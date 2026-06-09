package ch.example.association.domain

class SameTargetAssociation {

    Person primaryPersonId
    Person secondaryPersonId

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
