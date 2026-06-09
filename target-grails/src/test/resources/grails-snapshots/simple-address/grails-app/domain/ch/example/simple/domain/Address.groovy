package ch.example.simple.domain

class Address {

    String astreet
    String housenumber
    String postalcode

    static final Map<String, Map<String, Object>> interlisFieldMeta = [
        astreet: [label: 'street', qualifiedName: 'SimpleAddressModel.Addresses.Address.street'],
        housenumber: [label: 'houseNumber', qualifiedName: 'SimpleAddressModel.Addresses.Address.houseNumber'],
        postalcode: [label: 'postalCode', qualifiedName: 'SimpleAddressModel.Addresses.Address.postalCode']
    ]

    static final Map<String, Object> interlisDisplayMeta = [
        displayFields: ['astreet', 'housenumber'],
        searchFields: ['astreet', 'housenumber', 'postalcode']
    ]

    static mapping = {
        table 'address'
        id column: 't_id', generator: 'identity'
        version false
    }

    static constraints = {
        astreet maxSize: 100
        housenumber nullable: true, maxSize: 10
        postalcode maxSize: 10
    }
}
