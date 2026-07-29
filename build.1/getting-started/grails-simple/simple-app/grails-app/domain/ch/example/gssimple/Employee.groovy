package ch.example.gssimple

class Employee {

    Department department
    String email
    String firstname
    String lastname

    static final Map<String, Map<String, Object>> interlisFieldMeta = [
        department: [label: 'Department', qualifiedName: 'GsSimpleModel.Organization.DepartmentEmployee.Department'],
        email: [label: 'Email', qualifiedName: 'GsSimpleModel.Organization.Employee.Email'],
        firstname: [label: 'FirstName', qualifiedName: 'GsSimpleModel.Organization.Employee.FirstName'],
        lastname: [label: 'LastName', qualifiedName: 'GsSimpleModel.Organization.Employee.LastName']
    ]

    static final Map<String, Object> interlisDisplayMeta = [
        displayFields: ['email', 'firstname'],
        searchFields: ['email', 'firstname', 'lastname']
    ]

    static final Map<String, Map<String, Object>> interlisRelationshipMeta = [
        department: [targetClass: 'Department', semanticKind: 'ILI2DB_FK', label: 'Department', sourceAttribute: 'department', targetRole: 'Department', mandatory: true]
    ]

    static mapping = {
        table 'organization_employee'
        id column: 't_id', generator: 'identity'
        version false
        columns {
            department column: 'department'
        }
    }

    static constraints = {
        department maxSize: 19
        email nullable: true, maxSize: 120
        firstname maxSize: 50
        lastname maxSize: 50
    }
}
