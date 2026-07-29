package ch.interlis.generator.grails.generated

final class InterlisUiRegistry {

    static final List<Map<String, Object>> DOMAINS = [
        [
            domainClassName: 'ch.example.gssimple.Company',
            controller: 'company',
            iliName: 'GsSimpleModel.Organization.Company',
            modelName: 'GsSimpleModel',
            topicName: 'Organization',
            className: 'Company',
            label: 'Company',
            navigationVisible: true,
            associationDomain: false
        ],
        [
            domainClassName: 'ch.example.gssimple.Department',
            controller: 'department',
            iliName: 'GsSimpleModel.Organization.Department',
            modelName: 'GsSimpleModel',
            topicName: 'Organization',
            className: 'Department',
            label: 'Department',
            navigationVisible: true,
            associationDomain: false
        ],
        [
            domainClassName: 'ch.example.gssimple.Employee',
            controller: 'employee',
            iliName: 'GsSimpleModel.Organization.Employee',
            modelName: 'GsSimpleModel',
            topicName: 'Organization',
            className: 'Employee',
            label: 'Employee',
            navigationVisible: true,
            associationDomain: false
        ]
    ]

    static final Map<String, Map<String, Object>> BY_ILI_NAME = DOMAINS.collectEntries { [(it.iliName): it] }

    static List<Map<String, Object>> domains() {
        return DOMAINS
    }

    static Map<String, Object> domain(String iliName) {
        return iliName == null ? null : BY_ILI_NAME[iliName]
    }

    static Map<String, Object> domainForClassName(String domainClassName) {
        return domainClassName == null ? null : DOMAINS.find { it.domainClassName == domainClassName }
    }

    static List<Map<String, Object>> domainsForModel(String modelName) {
        return modelName == null ? [] : DOMAINS.findAll { it.modelName == modelName }
    }

    private InterlisUiRegistry() {
    }
}
