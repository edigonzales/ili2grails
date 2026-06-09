package ch.example.association.domain

class AssociationWithAttribute {

    Document documentRoleId
    Person personRoleId
    String roleNote

    static mapping = {
        table 'associationwithattribute'
        id column: 't_id', generator: 'identity'
        version false
        columns {
            documentRoleId column: 'document_role_id'
            personRoleId column: 'person_role_id'
            roleNote column: 'role_note'
        }
    }

    static constraints = {
        documentRoleId nullable: true
        personRoleId nullable: true
        roleNote nullable: true, maxSize: 30
    }
}
