package ch.example.gssimple

class Department {

    String aname
    Company company

    static final Map<String, Map<String, Object>> interlisFieldMeta = [
        aname: [label: 'Name', qualifiedName: 'GsSimpleModel.Organization.Department.Name'],
        company: [label: 'Company', qualifiedName: 'GsSimpleModel.Organization.CompanyDepartment.Company']
    ]

    static final Map<String, Object> interlisDisplayMeta = [
        displayFields: ['aname'],
        searchFields: ['aname']
    ]

    static final Map<String, Map<String, Object>> interlisRelationshipMeta = [
        company: [targetClass: 'Company', semanticKind: 'ILI2DB_FK', label: 'Company', sourceAttribute: 'company', targetRole: 'Company', mandatory: true]
    ]

    static hasMany = [employees: Employee]

    static mapping = {
        table 'organization_department'
        id column: 't_id', generator: 'identity'
        version false
        columns {
            company column: 'company'
        }
    }

    static constraints = {
        aname maxSize: 80
        company maxSize: 19
    }
}
