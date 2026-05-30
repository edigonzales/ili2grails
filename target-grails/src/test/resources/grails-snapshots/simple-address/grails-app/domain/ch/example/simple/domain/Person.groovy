package ch.example.simple.domain

class Person {

    Object birthdate
    Object firstname
    Object lastname

    static mapping = {
        table 'person'
        id column: 't_id', generator: 'identity'
        version false
    }

    static constraints = {
        birthdate nullable: true
        firstname nullable: true
        lastname nullable: true
    }
}
