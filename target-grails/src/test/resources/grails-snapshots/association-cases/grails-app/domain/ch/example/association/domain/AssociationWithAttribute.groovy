package ch.example.association.domain

class AssociationWithAttribute {

    Document documentRoleId
    Person personRoleId
    String roleNote

    static final Map<String, Map<String, Object>> interlisFieldMeta = [
        documentRoleId: [label: 'DocumentRole', qualifiedName: 'AssociationCases.Base.AssociationWithAttribute.DocumentRole'],
        personRoleId: [label: 'PersonRole', qualifiedName: 'AssociationCases.Base.AssociationWithAttribute.PersonRole'],
        roleNote: [label: 'RoleNote', qualifiedName: 'AssociationCases.Base.AssociationWithAttribute.RoleNote']
    ]

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
