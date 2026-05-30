package ch.example.simple.domain

class PersonAddress {

    Address addressId
    Person personId

    static mapping = {
        table 'personaddress'
        id column: 't_id', generator: 'identity'
        version false
        columns {
            addressId column: 'address_id'
            personId column: 'person_id'
        }
    }

    static constraints = {
        addressId nullable: true
        personId nullable: true
    }
}
