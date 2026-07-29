package ch.interlis.generator.grails.generated

final class InterlisAssociationRegistry {

    static final Map<String, Map<String, Object>> ASSOCIATIONS = [
        'GsSimpleModel.Organization.CompanyDepartment': [
            associationName: 'GsSimpleModel.Organization.CompanyDepartment',
            iliClassName: 'GsSimpleModel.Organization.CompanyDepartment',
            domainClassName: 'CompanyDepartment',
            domainClassQualifiedName: 'ch.example.gssimple.CompanyDepartment',
            controllerName: 'companyDepartment',
            viewPath: 'companyDepartment',
            physicalTable: null,
            physicalSqlName: null,
            storageKind: 'EMBEDDED_FOREIGN_KEY',
            writable: false,
            showInNavigation: false,
            roles: [
                [
                    name: 'Company',
                    label: 'GsSimpleModel.Organization.CompanyDepartment.Company',
                    property: null,
                    targetIliClass: 'GsSimpleModel.Organization.Company',
                    targetDomainClass: 'ch.example.gssimple.Company',
                    min: 1,
                    max: 1,
                    mandatory: true,
                    ordered: false,
                    external: false,
                    composition: false
                ],
                [
                    name: 'Departments',
                    label: 'GsSimpleModel.Organization.CompanyDepartment.Departments',
                    property: null,
                    targetIliClass: 'GsSimpleModel.Organization.Department',
                    targetDomainClass: 'ch.example.gssimple.Department',
                    min: 0,
                    max: -1,
                    mandatory: false,
                    ordered: false,
                    external: false,
                    composition: false
                ]
            ],
            attributes: [],
            diagnostics: [
                'EMBEDDED_FK_ASSOCIATION',
                'MERGE_CONFIDENCE_NONE:Company',
                'MERGE_CONFIDENCE_NONE:Departments'
            ]
        ],
        'GsSimpleModel.Organization.DepartmentEmployee': [
            associationName: 'GsSimpleModel.Organization.DepartmentEmployee',
            iliClassName: 'GsSimpleModel.Organization.DepartmentEmployee',
            domainClassName: 'DepartmentEmployee',
            domainClassQualifiedName: 'ch.example.gssimple.DepartmentEmployee',
            controllerName: 'departmentEmployee',
            viewPath: 'departmentEmployee',
            physicalTable: null,
            physicalSqlName: null,
            storageKind: 'EMBEDDED_FOREIGN_KEY',
            writable: false,
            showInNavigation: false,
            roles: [
                [
                    name: 'Department',
                    label: 'GsSimpleModel.Organization.DepartmentEmployee.Department',
                    property: null,
                    targetIliClass: 'GsSimpleModel.Organization.Department',
                    targetDomainClass: 'ch.example.gssimple.Department',
                    min: 1,
                    max: 1,
                    mandatory: true,
                    ordered: false,
                    external: false,
                    composition: false
                ],
                [
                    name: 'Employees',
                    label: 'GsSimpleModel.Organization.DepartmentEmployee.Employees',
                    property: null,
                    targetIliClass: 'GsSimpleModel.Organization.Employee',
                    targetDomainClass: 'ch.example.gssimple.Employee',
                    min: 0,
                    max: -1,
                    mandatory: false,
                    ordered: false,
                    external: false,
                    composition: false
                ]
            ],
            attributes: [],
            diagnostics: [
                'EMBEDDED_FK_ASSOCIATION',
                'MERGE_CONFIDENCE_NONE:Department',
                'MERGE_CONFIDENCE_NONE:Employees'
            ]
        ]
    ]

    static final Map<String, Map<String, Object>> CONTEXTS = [
        'GsSimpleModel.Organization.CompanyDepartment::Company': [
            id: 'GsSimpleModel.Organization.CompanyDepartment::Company',
            associationName: 'GsSimpleModel.Organization.CompanyDepartment',
            participantDomainClass: 'ch.example.gssimple.Company',
            fixedRole: 'Company',
            fixedProperty: null,
            editableRoles: [
                'Departments'
            ],
            editableProperties: [],
            defaultLabel: 'Departments',
            messageCode: 'interlis.association.gsSimpleModelOrganizationCompanyDepartment.company.label',
            presentation: 'READ_ONLY',
            createMode: 'NONE',
            writable: false,
            removable: false,
            showAssociationObjectLink: true,
            perspectiveMin: 0,
            perspectiveMax: -1,
            diagnostics: [
                'ROLE_PROPERTY_NOT_FOUND'
            ]
        ],
        'GsSimpleModel.Organization.CompanyDepartment::Departments': [
            id: 'GsSimpleModel.Organization.CompanyDepartment::Departments',
            associationName: 'GsSimpleModel.Organization.CompanyDepartment',
            participantDomainClass: 'ch.example.gssimple.Department',
            fixedRole: 'Departments',
            fixedProperty: null,
            editableRoles: [
                'Company'
            ],
            editableProperties: [],
            defaultLabel: 'Company',
            messageCode: 'interlis.association.gsSimpleModelOrganizationCompanyDepartment.departments.label',
            presentation: 'READ_ONLY',
            createMode: 'NONE',
            writable: false,
            removable: false,
            showAssociationObjectLink: true,
            perspectiveMin: 1,
            perspectiveMax: 1,
            diagnostics: [
                'ROLE_PROPERTY_NOT_FOUND'
            ]
        ],
        'GsSimpleModel.Organization.DepartmentEmployee::Department': [
            id: 'GsSimpleModel.Organization.DepartmentEmployee::Department',
            associationName: 'GsSimpleModel.Organization.DepartmentEmployee',
            participantDomainClass: 'ch.example.gssimple.Department',
            fixedRole: 'Department',
            fixedProperty: null,
            editableRoles: [
                'Employees'
            ],
            editableProperties: [],
            defaultLabel: 'Employees',
            messageCode: 'interlis.association.gsSimpleModelOrganizationDepartmentEmployee.department.label',
            presentation: 'READ_ONLY',
            createMode: 'NONE',
            writable: false,
            removable: false,
            showAssociationObjectLink: true,
            perspectiveMin: 0,
            perspectiveMax: -1,
            diagnostics: [
                'ROLE_PROPERTY_NOT_FOUND'
            ]
        ],
        'GsSimpleModel.Organization.DepartmentEmployee::Employees': [
            id: 'GsSimpleModel.Organization.DepartmentEmployee::Employees',
            associationName: 'GsSimpleModel.Organization.DepartmentEmployee',
            participantDomainClass: 'ch.example.gssimple.Employee',
            fixedRole: 'Employees',
            fixedProperty: null,
            editableRoles: [
                'Department'
            ],
            editableProperties: [],
            defaultLabel: 'Department',
            messageCode: 'interlis.association.gsSimpleModelOrganizationDepartmentEmployee.employees.label',
            presentation: 'READ_ONLY',
            createMode: 'NONE',
            writable: false,
            removable: false,
            showAssociationObjectLink: true,
            perspectiveMin: 1,
            perspectiveMax: 1,
            diagnostics: [
                'ROLE_PROPERTY_NOT_FOUND'
            ]
        ]
    ]

    static final Map<String, List<String>> CONTEXT_IDS_BY_PARTICIPANT = [
        'ch.example.gssimple.Company': [
            'GsSimpleModel.Organization.CompanyDepartment::Company'
        ],
        'ch.example.gssimple.Department': [
            'GsSimpleModel.Organization.CompanyDepartment::Departments',
            'GsSimpleModel.Organization.DepartmentEmployee::Department'
        ],
        'ch.example.gssimple.Employee': [
            'GsSimpleModel.Organization.DepartmentEmployee::Employees'
        ]
    ]

    static final Map<String, Map<String, Object>> ENTITIES = [
        'ch.example.gssimple.CompanyDepartment': [
            iliName: 'GsSimpleModel.Organization.CompanyDepartment',
            kind: 'ASSOCIATION',
            showInNavigation: false
        ],
        'ch.example.gssimple.DepartmentEmployee': [
            iliName: 'GsSimpleModel.Organization.DepartmentEmployee',
            kind: 'ASSOCIATION',
            showInNavigation: false
        ]
    ]

    static Map<String, Object> association(String associationName) {
        return ASSOCIATIONS[associationName]
    }

    static Map<String, Object> context(String contextId) {
        return CONTEXTS[contextId]
    }

    static List<Map<String, Object>> contextsForParticipant(String domainClassName) {
        return (CONTEXT_IDS_BY_PARTICIPANT[domainClassName] ?: [])
            .collect { String id -> CONTEXTS[id] }
            .findAll { it != null }
    }

    static boolean showInNavigation(String domainClassName) {
        Map entity = ENTITIES[domainClassName]
        return entity == null || entity.showInNavigation != false
    }

    private InterlisAssociationRegistry() {
    }
}
