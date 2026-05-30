package ch.example.structure.domain

class Asset {

    String name
    Inspection mainInspection
    Attachment optionalAttachment

    static hasMany = [parts: Part]

    static mapping = {
        version false
    }

    static constraints = {
        name maxSize: 50
        optionalAttachment nullable: true
    }
}
