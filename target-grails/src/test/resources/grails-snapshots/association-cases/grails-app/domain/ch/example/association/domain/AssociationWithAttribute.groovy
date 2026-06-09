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

    static final Map<String, Object> interlisDisplayMeta = [
        displayFields: ['roleNote'],
        searchFields: ['roleNote']
    ]

    static final Map<String, Map<String, Object>> interlisRelationshipMeta = [
        documentRoleId: [targetClass: 'Document', semanticKind: 'ASSOCIATION_ROLE', label: 'DocumentRole', sourceAttribute: 'document_role_id', targetRole: 'DocumentRole', association: 'AssociationCases.Base.AssociationWithAttribute', mandatory: false],
        personRoleId: [targetClass: 'Person', semanticKind: 'ASSOCIATION_ROLE', label: 'PersonRole', sourceAttribute: 'person_role_id', targetRole: 'PersonRole', association: 'AssociationCases.Base.AssociationWithAttribute', mandatory: false]
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
