package ch.interlis.generator.grails.generated

final class InterlisUiRegistry {

    static final List<Map<String, Object>> DOMAINS = [
        [
            domainClassName: 'ch.example.structure.domain.Asset',
            controller: 'asset',
            iliName: 'StructureCompositionCases.Cases.Asset',
            modelName: 'StructureCompositionCases',
            topicName: 'Cases',
            className: 'Asset',
            label: 'Asset',
            navigationVisible: true,
            associationDomain: false
        ],
        [
            domainClassName: 'ch.example.structure.domain.Attachment',
            controller: 'attachment',
            iliName: 'StructureCompositionCases.Cases.Attachment',
            modelName: 'StructureCompositionCases',
            topicName: 'Cases',
            className: 'Attachment',
            label: 'Attachment',
            navigationVisible: true,
            associationDomain: false
        ],
        [
            domainClassName: 'ch.example.structure.domain.Document',
            controller: 'document',
            iliName: 'StructureCompositionCases.Cases.Document',
            modelName: 'StructureCompositionCases',
            topicName: 'Cases',
            className: 'Document',
            label: 'Document',
            navigationVisible: true,
            associationDomain: false
        ],
        [
            domainClassName: 'ch.example.structure.domain.Inspection',
            controller: 'inspection',
            iliName: 'StructureCompositionCases.Cases.Inspection',
            modelName: 'StructureCompositionCases',
            topicName: 'Cases',
            className: 'Inspection',
            label: 'Inspection',
            navigationVisible: true,
            associationDomain: false
        ],
        [
            domainClassName: 'ch.example.structure.domain.Owner',
            controller: 'owner',
            iliName: 'StructureCompositionCases.Cases.Owner',
            modelName: 'StructureCompositionCases',
            topicName: 'Cases',
            className: 'Owner',
            label: 'Owner',
            navigationVisible: true,
            associationDomain: false
        ],
        [
            domainClassName: 'ch.example.structure.domain.Part',
            controller: 'part',
            iliName: 'StructureCompositionCases.Cases.Part',
            modelName: 'StructureCompositionCases',
            topicName: 'Cases',
            className: 'Part',
            label: 'Part',
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
