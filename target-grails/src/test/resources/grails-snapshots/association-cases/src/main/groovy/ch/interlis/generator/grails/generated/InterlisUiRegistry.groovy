package ch.interlis.generator.grails.generated

final class InterlisUiRegistry {

    static final List<Map<String, Object>> DOMAINS = [
        [
            domainClassName: 'ch.example.association.domain.AssociationWithAttribute',
            controller: 'associationWithAttribute',
            iliName: 'AssociationCases.Base.AssociationWithAttribute',
            modelName: 'AssociationCases',
            topicName: 'Base',
            className: 'AssociationWithAttribute',
            label: 'AssociationWithAttribute',
            navigationVisible: false,
            associationDomain: true
        ],
        [
            domainClassName: 'ch.example.association.domain.Building',
            controller: 'building',
            iliName: 'AssociationCases.Base.Building',
            modelName: 'AssociationCases',
            topicName: 'Base',
            className: 'Building',
            label: 'Building',
            navigationVisible: true,
            associationDomain: false
        ],
        [
            domainClassName: 'ch.example.association.domain.Document',
            controller: 'document',
            iliName: 'AssociationCases.Base.Document',
            modelName: 'AssociationCases',
            topicName: 'Base',
            className: 'Document',
            label: 'Document',
            navigationVisible: true,
            associationDomain: false
        ],
        [
            domainClassName: 'ch.example.association.domain.EmptyAssociation',
            controller: 'emptyAssociation',
            iliName: 'AssociationCases.Base.EmptyAssociation',
            modelName: 'AssociationCases',
            topicName: 'Base',
            className: 'EmptyAssociation',
            label: 'EmptyAssociation',
            navigationVisible: false,
            associationDomain: true
        ],
        [
            domainClassName: 'ch.example.association.domain.ExternalCompositeAssociation',
            controller: 'externalCompositeAssociation',
            iliName: 'AssociationCases.Base.ExternalCompositeAssociation',
            modelName: 'AssociationCases',
            topicName: 'Base',
            className: 'ExternalCompositeAssociation',
            label: 'ExternalCompositeAssociation',
            navigationVisible: false,
            associationDomain: true
        ],
        [
            domainClassName: 'ch.example.association.domain.OrderedAssociation',
            controller: 'orderedAssociation',
            iliName: 'AssociationCases.Base.OrderedAssociation',
            modelName: 'AssociationCases',
            topicName: 'Base',
            className: 'OrderedAssociation',
            label: 'OrderedAssociation',
            navigationVisible: false,
            associationDomain: true
        ],
        [
            domainClassName: 'ch.example.association.domain.Parcel',
            controller: 'parcel',
            iliName: 'AssociationCases.Base.Parcel',
            modelName: 'AssociationCases',
            topicName: 'Base',
            className: 'Parcel',
            label: 'Parcel',
            navigationVisible: true,
            associationDomain: false
        ],
        [
            domainClassName: 'ch.example.association.domain.Person',
            controller: 'person',
            iliName: 'AssociationCases.Base.Person',
            modelName: 'AssociationCases',
            topicName: 'Base',
            className: 'Person',
            label: 'Person',
            navigationVisible: true,
            associationDomain: false
        ],
        [
            domainClassName: 'ch.example.association.domain.PhysicalMismatchAssociation',
            controller: 'physicalMismatchAssociation',
            iliName: 'AssociationCases.Base.PhysicalMismatchAssociation',
            modelName: 'AssociationCases',
            topicName: 'Base',
            className: 'PhysicalMismatchAssociation',
            label: 'PhysicalMismatchAssociation',
            navigationVisible: false,
            associationDomain: true
        ],
        [
            domainClassName: 'ch.example.association.domain.SameTargetAssociation',
            controller: 'sameTargetAssociation',
            iliName: 'AssociationCases.Base.SameTargetAssociation',
            modelName: 'AssociationCases',
            topicName: 'Base',
            className: 'SameTargetAssociation',
            label: 'SameTargetAssociation',
            navigationVisible: false,
            associationDomain: true
        ],
        [
            domainClassName: 'ch.example.association.domain.ExtendedParcel',
            controller: 'extendedParcel',
            iliName: 'AssociationCases.Extended.ExtendedParcel',
            modelName: 'AssociationCases',
            topicName: 'Extended',
            className: 'ExtendedParcel',
            label: 'ExtendedParcel',
            navigationVisible: true,
            associationDomain: false
        ],
        [
            domainClassName: 'ch.example.association.domain.ExtendedTopicAssociation',
            controller: 'extendedTopicAssociation',
            iliName: 'AssociationCases.Extended.ExtendedTopicAssociation',
            modelName: 'AssociationCases',
            topicName: 'Extended',
            className: 'ExtendedTopicAssociation',
            label: 'ExtendedTopicAssociation',
            navigationVisible: false,
            associationDomain: true
        ],
        [
            domainClassName: 'ch.example.association.domain.TernaryAssociation',
            controller: 'ternaryAssociation',
            iliName: 'AssociationCases.Extended.TernaryAssociation',
            modelName: 'AssociationCases',
            topicName: 'Extended',
            className: 'TernaryAssociation',
            label: 'TernaryAssociation',
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
