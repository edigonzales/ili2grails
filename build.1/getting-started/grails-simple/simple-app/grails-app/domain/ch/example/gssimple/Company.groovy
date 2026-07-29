package ch.example.gssimple

class Company {

    String aname
    String legalid

    static final Map<String, Map<String, Object>> interlisFieldMeta = [
        aname: [label: 'Name', qualifiedName: 'GsSimpleModel.Organization.Company.Name'],
        legalid: [label: 'LegalId', qualifiedName: 'GsSimpleModel.Organization.Company.LegalId']
    ]

    static final Map<String, Object> interlisDisplayMeta = [
        displayFields: ['aname', 'legalid'],
        searchFields: ['aname', 'legalid']
    ]

    static hasMany = [departments: Department]

    static mapping = {
        table 'organization_company'
        id column: 't_id', generator: 'identity'
        version false
    }

    static constraints = {
        aname maxSize: 80
        legalid nullable: true, maxSize: 30
    }
}
