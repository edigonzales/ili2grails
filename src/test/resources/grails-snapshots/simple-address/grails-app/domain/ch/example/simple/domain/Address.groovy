package ch.example.simple.domain

class Address {

    Object astreet
    Object housenumber
    Object postalcode

    static mapping = {
        table 'address'
        id column: 't_id', generator: 'identity'
        version false
    }

    static constraints = {
        astreet nullable: true
        housenumber nullable: true
        postalcode nullable: true
    }
}
