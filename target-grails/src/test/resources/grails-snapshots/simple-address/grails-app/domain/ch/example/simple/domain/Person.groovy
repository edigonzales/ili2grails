package ch.example.simple.domain

import java.time.LocalDate

class Person {

    LocalDate birthdate
    String firstname
    String lastname

    static final Map<String, Map<String, Object>> interlisFieldMeta = [
        birthdate: [label: 'birthDate', qualifiedName: 'SimpleAddressModel.Addresses.Person.BirthDate'],
        firstname: [label: 'firstName', documentation: 'Vorname', qualifiedName: 'SimpleAddressModel.Addresses.Person.FirstName'],
        lastname: [label: 'lastName', documentation: 'Nachname', qualifiedName: 'SimpleAddressModel.Addresses.Person.LastName']
    ]

    static final Map<String, Object> interlisDisplayMeta = [
        displayFields: ['firstname', 'lastname'],
        searchFields: ['firstname', 'lastname']
    ]

    static mapping = {
        table 'person'
        id column: 't_id', generator: 'identity'
        version false
    }

    static constraints = {
        birthdate nullable: true
        firstname maxSize: 50
        lastname maxSize: 50
    }
}
