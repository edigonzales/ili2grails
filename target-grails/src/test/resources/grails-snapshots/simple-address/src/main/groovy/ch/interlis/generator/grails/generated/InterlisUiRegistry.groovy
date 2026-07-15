package ch.interlis.generator.grails.generated

final class InterlisUiRegistry {

    static final List<Map<String, Object>> DOMAINS = [
        [
            domainClassName: 'ch.example.simple.domain.Address',
            controller: 'address',
            iliName: 'SimpleAddressModel.Addresses.Address',
            modelName: 'SimpleAddressModel',
            topicName: 'Addresses',
            className: 'Address',
            label: 'Address',
            navigationVisible: true,
            associationDomain: false
        ],
        [
            domainClassName: 'ch.example.simple.domain.Person',
            controller: 'person',
            iliName: 'SimpleAddressModel.Addresses.Person',
            modelName: 'SimpleAddressModel',
            topicName: 'Addresses',
            className: 'Person',
            label: 'Person',
            navigationVisible: true,
            associationDomain: false
        ],
        [
            domainClassName: 'ch.example.simple.domain.PersonAddress',
            controller: 'personAddress',
            iliName: 'SimpleAddressModel.Addresses.PersonAddress',
            modelName: 'SimpleAddressModel',
            topicName: 'Addresses',
            className: 'PersonAddress',
            label: 'PersonAddress',
            navigationVisible: false,
            associationDomain: true
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
