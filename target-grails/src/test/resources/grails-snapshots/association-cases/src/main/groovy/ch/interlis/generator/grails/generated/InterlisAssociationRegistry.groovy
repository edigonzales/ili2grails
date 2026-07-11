package ch.interlis.generator.grails.generated

final class InterlisAssociationRegistry {

    static final Map<String, Map<String, Object>> ASSOCIATIONS = [
        'AssociationCases.Base.AssociationWithAttribute': [
            associationName: 'AssociationCases.Base.AssociationWithAttribute',
            iliClassName: 'AssociationCases.Base.AssociationWithAttribute',
            domainClassName: 'AssociationWithAttribute',
            domainClassQualifiedName: 'ch.example.association.domain.AssociationWithAttribute',
            controllerName: 'associationWithAttribute',
            viewPath: 'associationWithAttribute',
            physicalTable: 'associationwithattribute',
            physicalSqlName: 'associationwithattribute',
            storageKind: 'LINK_ENTITY',
            writable: true,
            showInNavigation: false,
            roles: [
                [
                    name: 'DocumentRole',
                    label: 'AssociationCases.Base.AssociationWithAttribute.DocumentRole',
                    property: 'documentRoleId',
                    targetIliClass: 'AssociationCases.Base.Document',
                    targetDomainClass: 'ch.example.association.domain.Document',
                    min: 0,
                    max: -1,
                    mandatory: false,
                    ordered: false,
                    external: false,
                    composition: false
                ],
                [
                    name: 'PersonRole',
                    label: 'AssociationCases.Base.AssociationWithAttribute.PersonRole',
                    property: 'personRoleId',
                    targetIliClass: 'AssociationCases.Base.Person',
                    targetDomainClass: 'ch.example.association.domain.Person',
                    min: 0,
                    max: -1,
                    mandatory: false,
                    ordered: false,
                    external: false,
                    composition: false
                ]
            ],
            attributes: [
                [
                    iliName: 'RoleNote',
                    property: 'roleNote',
                    type: 'String',
                    coreType: 'TEXT',
                    label: 'RoleNote',
                    mandatory: false,
                    maxLength: 30,
                    unit: null,
                    enumType: null,
                    geometry: false
                ]
            ],
            diagnostics: []
        ],
        'AssociationCases.Base.EmptyAssociation': [
            associationName: 'AssociationCases.Base.EmptyAssociation',
            iliClassName: 'AssociationCases.Base.EmptyAssociation',
            domainClassName: 'EmptyAssociation',
            domainClassQualifiedName: 'ch.example.association.domain.EmptyAssociation',
            controllerName: 'emptyAssociation',
            viewPath: 'emptyAssociation',
            physicalTable: 'emptyassociation',
            physicalSqlName: 'emptyassociation',
            storageKind: 'LINK_ENTITY',
            writable: true,
            showInNavigation: false,
            roles: [
                [
                    name: 'ParcelRole',
                    label: 'AssociationCases.Base.EmptyAssociation.ParcelRole',
                    property: 'parcelRoleId',
                    targetIliClass: 'AssociationCases.Base.Parcel',
                    targetDomainClass: 'ch.example.association.domain.Parcel',
                    min: 0,
                    max: 1,
                    mandatory: false,
                    ordered: false,
                    external: false,
                    composition: false
                ],
                [
                    name: 'PersonRole',
                    label: 'AssociationCases.Base.EmptyAssociation.PersonRole',
                    property: 'personRoleId',
                    targetIliClass: 'AssociationCases.Base.Person',
                    targetDomainClass: 'ch.example.association.domain.Person',
                    min: 0,
                    max: -1,
                    mandatory: false,
                    ordered: false,
                    external: false,
                    composition: false
                ]
            ],
            attributes: [],
            diagnostics: []
        ],
        'AssociationCases.Base.ExternalCompositeAssociation': [
            associationName: 'AssociationCases.Base.ExternalCompositeAssociation',
            iliClassName: 'AssociationCases.Base.ExternalCompositeAssociation',
            domainClassName: 'ExternalCompositeAssociation',
            domainClassQualifiedName: 'ch.example.association.domain.ExternalCompositeAssociation',
            controllerName: 'externalCompositeAssociation',
            viewPath: 'externalCompositeAssociation',
            physicalTable: 'externalcompositeassociation',
            physicalSqlName: 'externalcompositeassociation',
            storageKind: 'LINK_ENTITY',
            writable: true,
            showInNavigation: false,
            roles: [
                [
                    name: 'Buildings',
                    label: 'AssociationCases.Base.ExternalCompositeAssociation.Buildings',
                    property: 'buildingId',
                    targetIliClass: 'AssociationCases.Base.Building',
                    targetDomainClass: 'ch.example.association.domain.Building',
                    min: 0,
                    max: -1,
                    mandatory: false,
                    ordered: false,
                    external: false,
                    composition: false
                ],
                [
                    name: 'Owner',
                    label: 'AssociationCases.Base.ExternalCompositeAssociation.Owner',
                    property: 'ownerId',
                    targetIliClass: 'AssociationCases.Base.Person',
                    targetDomainClass: 'ch.example.association.domain.Person',
                    min: 1,
                    max: 1,
                    mandatory: true,
                    ordered: false,
                    external: true,
                    composition: true
                ]
            ],
            attributes: [],
            diagnostics: []
        ],
        'AssociationCases.Base.PhysicalMismatchAssociation': [
            associationName: 'AssociationCases.Base.PhysicalMismatchAssociation',
            iliClassName: 'AssociationCases.Base.PhysicalMismatchAssociation',
            domainClassName: 'PhysicalMismatchAssociation',
            domainClassQualifiedName: 'ch.example.association.domain.PhysicalMismatchAssociation',
            controllerName: 'physicalMismatchAssociation',
            viewPath: 'physicalMismatchAssociation',
            physicalTable: 'physicalmismatchassociation',
            physicalSqlName: 'physicalmismatchassociation',
            storageKind: 'LINK_ENTITY',
            writable: true,
            showInNavigation: false,
            roles: [
                [
                    name: 'OwnedParcel',
                    label: 'AssociationCases.Base.PhysicalMismatchAssociation.OwnedParcel',
                    property: 'parcelFk',
                    targetIliClass: 'AssociationCases.Base.Parcel',
                    targetDomainClass: 'ch.example.association.domain.Parcel',
                    min: 0,
                    max: -1,
                    mandatory: false,
                    ordered: false,
                    external: false,
                    composition: false
                ],
                [
                    name: 'SemanticOwner',
                    label: 'AssociationCases.Base.PhysicalMismatchAssociation.SemanticOwner',
                    property: 'ownerFk',
                    targetIliClass: 'AssociationCases.Base.Person',
                    targetDomainClass: 'ch.example.association.domain.Person',
                    min: 1,
                    max: 1,
                    mandatory: true,
                    ordered: false,
                    external: false,
                    composition: false
                ]
            ],
            attributes: [],
            diagnostics: []
        ],
        'AssociationCases.Base.SameTargetAssociation': [
            associationName: 'AssociationCases.Base.SameTargetAssociation',
            iliClassName: 'AssociationCases.Base.SameTargetAssociation',
            domainClassName: 'SameTargetAssociation',
            domainClassQualifiedName: 'ch.example.association.domain.SameTargetAssociation',
            controllerName: 'sameTargetAssociation',
            viewPath: 'sameTargetAssociation',
            physicalTable: 'sametargetassociation',
            physicalSqlName: 'sametargetassociation',
            storageKind: 'LINK_ENTITY',
            writable: true,
            showInNavigation: false,
            roles: [
                [
                    name: 'PrimaryPerson',
                    label: 'AssociationCases.Base.SameTargetAssociation.PrimaryPerson',
                    property: 'primaryPersonId',
                    targetIliClass: 'AssociationCases.Base.Person',
                    targetDomainClass: 'ch.example.association.domain.Person',
                    min: 0,
                    max: 1,
                    mandatory: false,
                    ordered: false,
                    external: false,
                    composition: false
                ],
                [
                    name: 'SecondaryPerson',
                    label: 'AssociationCases.Base.SameTargetAssociation.SecondaryPerson',
                    property: 'secondaryPersonId',
                    targetIliClass: 'AssociationCases.Base.Person',
                    targetDomainClass: 'ch.example.association.domain.Person',
                    min: 0,
                    max: 1,
                    mandatory: false,
                    ordered: false,
                    external: false,
                    composition: false
                ]
            ],
            attributes: [],
            diagnostics: []
        ],
        'AssociationCases.Extended.ExtendedTopicAssociation': [
            associationName: 'AssociationCases.Extended.ExtendedTopicAssociation',
            iliClassName: 'AssociationCases.Extended.ExtendedTopicAssociation',
            domainClassName: 'ExtendedTopicAssociation',
            domainClassQualifiedName: 'ch.example.association.domain.ExtendedTopicAssociation',
            controllerName: 'extendedTopicAssociation',
            viewPath: 'extendedTopicAssociation',
            physicalTable: 'extendedtopicassociation',
            physicalSqlName: 'extendedtopicassociation',
            storageKind: 'LINK_ENTITY',
            writable: true,
            showInNavigation: false,
            roles: [
                [
                    name: 'ExtendedParcelRole',
                    label: 'AssociationCases.Extended.ExtendedTopicAssociation.ExtendedParcelRole',
                    property: 'extParcelId',
                    targetIliClass: 'AssociationCases.Extended.ExtendedParcel',
                    targetDomainClass: 'ch.example.association.domain.ExtendedParcel',
                    min: 0,
                    max: 1,
                    mandatory: false,
                    ordered: false,
                    external: false,
                    composition: false
                ],
                [
                    name: 'ExtendedPersonRole',
                    label: 'AssociationCases.Extended.ExtendedTopicAssociation.ExtendedPersonRole',
                    property: 'extPersonId',
                    targetIliClass: 'AssociationCases.Base.Person',
                    targetDomainClass: 'ch.example.association.domain.Person',
                    min: 0,
                    max: -1,
                    mandatory: false,
                    ordered: false,
                    external: false,
                    composition: false
                ]
            ],
            attributes: [],
            diagnostics: []
        ]
    ]

    static final Map<String, Map<String, Object>> CONTEXTS = [
        'AssociationCases.Base.AssociationWithAttribute::DocumentRole': [
            id: 'AssociationCases.Base.AssociationWithAttribute::DocumentRole',
            associationName: 'AssociationCases.Base.AssociationWithAttribute',
            participantDomainClass: 'ch.example.association.domain.Document',
            fixedRole: 'DocumentRole',
            fixedProperty: 'documentRoleId',
            editableRoles: [
                'PersonRole'
            ],
            editableProperties: [
                'personRoleId'
            ],
            defaultLabel: 'Persons',
            messageCode: 'interlis.association.associationCasesBaseAssociationWithAttribute.documentRole.label',
            presentation: 'CONTEXTUAL_FORM',
            createMode: 'CONTEXTUAL_FORM',
            writable: true,
            removable: true,
            showAssociationObjectLink: true,
            perspectiveMin: 0,
            perspectiveMax: -1,
            diagnostics: []
        ],
        'AssociationCases.Base.AssociationWithAttribute::PersonRole': [
            id: 'AssociationCases.Base.AssociationWithAttribute::PersonRole',
            associationName: 'AssociationCases.Base.AssociationWithAttribute',
            participantDomainClass: 'ch.example.association.domain.Person',
            fixedRole: 'PersonRole',
            fixedProperty: 'personRoleId',
            editableRoles: [
                'DocumentRole'
            ],
            editableProperties: [
                'documentRoleId'
            ],
            defaultLabel: 'Documents',
            messageCode: 'interlis.association.associationCasesBaseAssociationWithAttribute.personRole.label',
            presentation: 'CONTEXTUAL_FORM',
            createMode: 'CONTEXTUAL_FORM',
            writable: true,
            removable: true,
            showAssociationObjectLink: true,
            perspectiveMin: 0,
            perspectiveMax: -1,
            diagnostics: []
        ],
        'AssociationCases.Base.EmptyAssociation::ParcelRole': [
            id: 'AssociationCases.Base.EmptyAssociation::ParcelRole',
            associationName: 'AssociationCases.Base.EmptyAssociation',
            participantDomainClass: 'ch.example.association.domain.Parcel',
            fixedRole: 'ParcelRole',
            fixedProperty: 'parcelRoleId',
            editableRoles: [
                'PersonRole'
            ],
            editableProperties: [
                'personRoleId'
            ],
            defaultLabel: 'Persons',
            messageCode: 'interlis.association.associationCasesBaseEmptyAssociation.parcelRole.label',
            presentation: 'RELATED_LIST',
            createMode: 'QUICK',
            writable: true,
            removable: true,
            showAssociationObjectLink: true,
            perspectiveMin: 0,
            perspectiveMax: -1,
            diagnostics: []
        ],
        'AssociationCases.Base.EmptyAssociation::PersonRole': [
            id: 'AssociationCases.Base.EmptyAssociation::PersonRole',
            associationName: 'AssociationCases.Base.EmptyAssociation',
            participantDomainClass: 'ch.example.association.domain.Person',
            fixedRole: 'PersonRole',
            fixedProperty: 'personRoleId',
            editableRoles: [
                'ParcelRole'
            ],
            editableProperties: [
                'parcelRoleId'
            ],
            defaultLabel: 'Parcel',
            messageCode: 'interlis.association.associationCasesBaseEmptyAssociation.personRole.label',
            presentation: 'RELATED_TO_ONE',
            createMode: 'QUICK',
            writable: true,
            removable: true,
            showAssociationObjectLink: true,
            perspectiveMin: 0,
            perspectiveMax: 1,
            diagnostics: []
        ],
        'AssociationCases.Base.ExternalCompositeAssociation::Buildings': [
            id: 'AssociationCases.Base.ExternalCompositeAssociation::Buildings',
            associationName: 'AssociationCases.Base.ExternalCompositeAssociation',
            participantDomainClass: 'ch.example.association.domain.Building',
            fixedRole: 'Buildings',
            fixedProperty: 'buildingId',
            editableRoles: [
                'Owner'
            ],
            editableProperties: [
                'ownerId'
            ],
            defaultLabel: 'Person',
            messageCode: 'interlis.association.associationCasesBaseExternalCompositeAssociation.buildings.label',
            presentation: 'RELATED_TO_ONE',
            createMode: 'CONTEXTUAL_FORM',
            writable: true,
            removable: true,
            showAssociationObjectLink: true,
            perspectiveMin: 1,
            perspectiveMax: 1,
            diagnostics: []
        ],
        'AssociationCases.Base.ExternalCompositeAssociation::Owner': [
            id: 'AssociationCases.Base.ExternalCompositeAssociation::Owner',
            associationName: 'AssociationCases.Base.ExternalCompositeAssociation',
            participantDomainClass: 'ch.example.association.domain.Person',
            fixedRole: 'Owner',
            fixedProperty: 'ownerId',
            editableRoles: [
                'Buildings'
            ],
            editableProperties: [
                'buildingId'
            ],
            defaultLabel: 'Buildings',
            messageCode: 'interlis.association.associationCasesBaseExternalCompositeAssociation.owner.label',
            presentation: 'RELATED_LIST',
            createMode: 'CONTEXTUAL_FORM',
            writable: true,
            removable: true,
            showAssociationObjectLink: true,
            perspectiveMin: 0,
            perspectiveMax: -1,
            diagnostics: []
        ],
        'AssociationCases.Base.PhysicalMismatchAssociation::OwnedParcel': [
            id: 'AssociationCases.Base.PhysicalMismatchAssociation::OwnedParcel',
            associationName: 'AssociationCases.Base.PhysicalMismatchAssociation',
            participantDomainClass: 'ch.example.association.domain.Parcel',
            fixedRole: 'OwnedParcel',
            fixedProperty: 'parcelFk',
            editableRoles: [
                'SemanticOwner'
            ],
            editableProperties: [
                'ownerFk'
            ],
            defaultLabel: 'Person',
            messageCode: 'interlis.association.associationCasesBasePhysicalMismatchAssociation.ownedParcel.label',
            presentation: 'RELATED_TO_ONE',
            createMode: 'QUICK',
            writable: true,
            removable: true,
            showAssociationObjectLink: true,
            perspectiveMin: 1,
            perspectiveMax: 1,
            diagnostics: []
        ],
        'AssociationCases.Base.PhysicalMismatchAssociation::SemanticOwner': [
            id: 'AssociationCases.Base.PhysicalMismatchAssociation::SemanticOwner',
            associationName: 'AssociationCases.Base.PhysicalMismatchAssociation',
            participantDomainClass: 'ch.example.association.domain.Person',
            fixedRole: 'SemanticOwner',
            fixedProperty: 'ownerFk',
            editableRoles: [
                'OwnedParcel'
            ],
            editableProperties: [
                'parcelFk'
            ],
            defaultLabel: 'Parcels',
            messageCode: 'interlis.association.associationCasesBasePhysicalMismatchAssociation.semanticOwner.label',
            presentation: 'RELATED_LIST',
            createMode: 'QUICK',
            writable: true,
            removable: true,
            showAssociationObjectLink: true,
            perspectiveMin: 0,
            perspectiveMax: -1,
            diagnostics: []
        ],
        'AssociationCases.Base.SameTargetAssociation::PrimaryPerson': [
            id: 'AssociationCases.Base.SameTargetAssociation::PrimaryPerson',
            associationName: 'AssociationCases.Base.SameTargetAssociation',
            participantDomainClass: 'ch.example.association.domain.Person',
            fixedRole: 'PrimaryPerson',
            fixedProperty: 'primaryPersonId',
            editableRoles: [
                'SecondaryPerson'
            ],
            editableProperties: [
                'secondaryPersonId'
            ],
            defaultLabel: 'Person',
            messageCode: 'interlis.association.associationCasesBaseSameTargetAssociation.primaryPerson.label',
            presentation: 'RELATED_TO_ONE',
            createMode: 'QUICK',
            writable: true,
            removable: true,
            showAssociationObjectLink: true,
            perspectiveMin: 0,
            perspectiveMax: 1,
            diagnostics: []
        ],
        'AssociationCases.Base.SameTargetAssociation::SecondaryPerson': [
            id: 'AssociationCases.Base.SameTargetAssociation::SecondaryPerson',
            associationName: 'AssociationCases.Base.SameTargetAssociation',
            participantDomainClass: 'ch.example.association.domain.Person',
            fixedRole: 'SecondaryPerson',
            fixedProperty: 'secondaryPersonId',
            editableRoles: [
                'PrimaryPerson'
            ],
            editableProperties: [
                'primaryPersonId'
            ],
            defaultLabel: 'Person',
            messageCode: 'interlis.association.associationCasesBaseSameTargetAssociation.secondaryPerson.label',
            presentation: 'RELATED_TO_ONE',
            createMode: 'QUICK',
            writable: true,
            removable: true,
            showAssociationObjectLink: true,
            perspectiveMin: 0,
            perspectiveMax: 1,
            diagnostics: []
        ],
        'AssociationCases.Extended.ExtendedTopicAssociation::ExtendedParcelRole': [
            id: 'AssociationCases.Extended.ExtendedTopicAssociation::ExtendedParcelRole',
            associationName: 'AssociationCases.Extended.ExtendedTopicAssociation',
            participantDomainClass: 'ch.example.association.domain.ExtendedParcel',
            fixedRole: 'ExtendedParcelRole',
            fixedProperty: 'extParcelId',
            editableRoles: [
                'ExtendedPersonRole'
            ],
            editableProperties: [
                'extPersonId'
            ],
            defaultLabel: 'Persons',
            messageCode: 'interlis.association.associationCasesExtendedExtendedTopicAssociation.extendedParcelRole.label',
            presentation: 'RELATED_LIST',
            createMode: 'QUICK',
            writable: true,
            removable: true,
            showAssociationObjectLink: true,
            perspectiveMin: 0,
            perspectiveMax: -1,
            diagnostics: []
        ],
        'AssociationCases.Extended.ExtendedTopicAssociation::ExtendedPersonRole': [
            id: 'AssociationCases.Extended.ExtendedTopicAssociation::ExtendedPersonRole',
            associationName: 'AssociationCases.Extended.ExtendedTopicAssociation',
            participantDomainClass: 'ch.example.association.domain.Person',
            fixedRole: 'ExtendedPersonRole',
            fixedProperty: 'extPersonId',
            editableRoles: [
                'ExtendedParcelRole'
            ],
            editableProperties: [
                'extParcelId'
            ],
            defaultLabel: 'ExtendedParcel',
            messageCode: 'interlis.association.associationCasesExtendedExtendedTopicAssociation.extendedPersonRole.label',
            presentation: 'RELATED_TO_ONE',
            createMode: 'QUICK',
            writable: true,
            removable: true,
            showAssociationObjectLink: true,
            perspectiveMin: 0,
            perspectiveMax: 1,
            diagnostics: []
        ]
    ]

    static final Map<String, List<String>> CONTEXT_IDS_BY_PARTICIPANT = [
        'ch.example.association.domain.Building': [
            'AssociationCases.Base.ExternalCompositeAssociation::Buildings'
        ],
        'ch.example.association.domain.Document': [
            'AssociationCases.Base.AssociationWithAttribute::DocumentRole'
        ],
        'ch.example.association.domain.ExtendedParcel': [
            'AssociationCases.Extended.ExtendedTopicAssociation::ExtendedParcelRole'
        ],
        'ch.example.association.domain.Parcel': [
            'AssociationCases.Base.EmptyAssociation::ParcelRole',
            'AssociationCases.Base.PhysicalMismatchAssociation::OwnedParcel'
        ],
        'ch.example.association.domain.Person': [
            'AssociationCases.Base.AssociationWithAttribute::PersonRole',
            'AssociationCases.Base.EmptyAssociation::PersonRole',
            'AssociationCases.Base.ExternalCompositeAssociation::Owner',
            'AssociationCases.Base.PhysicalMismatchAssociation::SemanticOwner',
            'AssociationCases.Base.SameTargetAssociation::PrimaryPerson',
            'AssociationCases.Base.SameTargetAssociation::SecondaryPerson',
            'AssociationCases.Extended.ExtendedTopicAssociation::ExtendedPersonRole'
        ]
    ]

    static final Map<String, Map<String, Object>> ENTITIES = [
        'ch.example.association.domain.AssociationWithAttribute': [
            iliName: 'AssociationCases.Base.AssociationWithAttribute',
            kind: 'ASSOCIATION',
            showInNavigation: false
        ],
        'ch.example.association.domain.EmptyAssociation': [
            iliName: 'AssociationCases.Base.EmptyAssociation',
            kind: 'ASSOCIATION',
            showInNavigation: false
        ],
        'ch.example.association.domain.ExtendedTopicAssociation': [
            iliName: 'AssociationCases.Extended.ExtendedTopicAssociation',
            kind: 'ASSOCIATION',
            showInNavigation: false
        ],
        'ch.example.association.domain.ExternalCompositeAssociation': [
            iliName: 'AssociationCases.Base.ExternalCompositeAssociation',
            kind: 'ASSOCIATION',
            showInNavigation: false
        ],
        'ch.example.association.domain.PhysicalMismatchAssociation': [
            iliName: 'AssociationCases.Base.PhysicalMismatchAssociation',
            kind: 'ASSOCIATION',
            showInNavigation: false
        ],
        'ch.example.association.domain.SameTargetAssociation': [
            iliName: 'AssociationCases.Base.SameTargetAssociation',
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
