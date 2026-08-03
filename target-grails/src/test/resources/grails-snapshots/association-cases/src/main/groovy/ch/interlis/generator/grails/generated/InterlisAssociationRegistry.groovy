package ch.interlis.generator.grails.generated

import ch.interlis.generator.grails.runtime.api.descriptor.AssociationAttributeDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationCreateMode
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationRoleDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationStorageKind
import ch.interlis.generator.grails.runtime.api.descriptor.RuntimeCoreType
import ch.interlis.generator.grails.runtime.api.registry.AssociationRegistry

final class InterlisAssociationRegistry implements AssociationRegistry {

    static final Map<String, AssociationDescriptor> ASSOCIATIONS = [
        'AssociationCases.Base.AssociationWithAttribute': new AssociationDescriptor(
            'AssociationCases.Base.AssociationWithAttribute',
            'AssociationCases.Base.AssociationWithAttribute',
            'ch.example.association.domain.AssociationWithAttribute',
            'associationWithAttribute',
            'associationWithAttribute',
            'associationwithattribute',
            'associationwithattribute',
            AssociationStorageKind.LINK_ENTITY,
            true,
            false,
            [
                new AssociationRoleDescriptor(
                    'DocumentRole',
                    'AssociationCases.Base.AssociationWithAttribute.DocumentRole',
                    'documentRoleId',
                    'AssociationCases.Base.Document',
                    'ch.example.association.domain.Document',
                    0,
                    -1,
                    false,
                    false,
                    false,
                    false
                ),
                new AssociationRoleDescriptor(
                    'PersonRole',
                    'AssociationCases.Base.AssociationWithAttribute.PersonRole',
                    'personRoleId',
                    'AssociationCases.Base.Person',
                    'ch.example.association.domain.Person',
                    0,
                    -1,
                    false,
                    false,
                    false,
                    false
                )
            ],
            [
                new AssociationAttributeDescriptor(
                    'RoleNote',
                    'roleNote',
                    'String',
                    RuntimeCoreType.TEXT,
                    'RoleNote',
                    false,
                    30,
                    null,
                    null,
                    false
                )
            ],
            []
        ),
        'AssociationCases.Base.EmptyAssociation': new AssociationDescriptor(
            'AssociationCases.Base.EmptyAssociation',
            'AssociationCases.Base.EmptyAssociation',
            'ch.example.association.domain.EmptyAssociation',
            'emptyAssociation',
            'emptyAssociation',
            'emptyassociation',
            'emptyassociation',
            AssociationStorageKind.LINK_ENTITY,
            true,
            false,
            [
                new AssociationRoleDescriptor(
                    'ParcelRole',
                    'AssociationCases.Base.EmptyAssociation.ParcelRole',
                    'parcelRoleId',
                    'AssociationCases.Base.Parcel',
                    'ch.example.association.domain.Parcel',
                    0,
                    1,
                    false,
                    false,
                    false,
                    false
                ),
                new AssociationRoleDescriptor(
                    'PersonRole',
                    'AssociationCases.Base.EmptyAssociation.PersonRole',
                    'personRoleId',
                    'AssociationCases.Base.Person',
                    'ch.example.association.domain.Person',
                    0,
                    -1,
                    false,
                    false,
                    false,
                    false
                )
            ],
            [],
            []
        ),
        'AssociationCases.Base.ExternalCompositeAssociation': new AssociationDescriptor(
            'AssociationCases.Base.ExternalCompositeAssociation',
            'AssociationCases.Base.ExternalCompositeAssociation',
            'ch.example.association.domain.ExternalCompositeAssociation',
            'externalCompositeAssociation',
            'externalCompositeAssociation',
            'externalcompositeassociation',
            'externalcompositeassociation',
            AssociationStorageKind.LINK_ENTITY,
            true,
            false,
            [
                new AssociationRoleDescriptor(
                    'Buildings',
                    'AssociationCases.Base.ExternalCompositeAssociation.Buildings',
                    'buildingId',
                    'AssociationCases.Base.Building',
                    'ch.example.association.domain.Building',
                    0,
                    -1,
                    false,
                    false,
                    false,
                    false
                ),
                new AssociationRoleDescriptor(
                    'Owner',
                    'AssociationCases.Base.ExternalCompositeAssociation.Owner',
                    'ownerId',
                    'AssociationCases.Base.Person',
                    'ch.example.association.domain.Person',
                    1,
                    1,
                    true,
                    false,
                    true,
                    true
                )
            ],
            [],
            []
        ),
        'AssociationCases.Base.OrderedAssociation': new AssociationDescriptor(
            'AssociationCases.Base.OrderedAssociation',
            'AssociationCases.Base.OrderedAssociation',
            'ch.example.association.domain.OrderedAssociation',
            'orderedAssociation',
            'orderedAssociation',
            'orderedassociation',
            'orderedassociation',
            AssociationStorageKind.LINK_ENTITY,
            true,
            false,
            [
                new AssociationRoleDescriptor(
                    'Docs',
                    'AssociationCases.Base.OrderedAssociation.Docs',
                    'docsId',
                    'AssociationCases.Base.Document',
                    'ch.example.association.domain.Document',
                    0,
                    -1,
                    false,
                    true,
                    false,
                    false
                ),
                new AssociationRoleDescriptor(
                    'Owner',
                    'AssociationCases.Base.OrderedAssociation.Owner',
                    'ownerId',
                    'AssociationCases.Base.Person',
                    'ch.example.association.domain.Person',
                    1,
                    1,
                    true,
                    false,
                    false,
                    false
                )
            ],
            [],
            []
        ),
        'AssociationCases.Base.PhysicalMismatchAssociation': new AssociationDescriptor(
            'AssociationCases.Base.PhysicalMismatchAssociation',
            'AssociationCases.Base.PhysicalMismatchAssociation',
            'ch.example.association.domain.PhysicalMismatchAssociation',
            'physicalMismatchAssociation',
            'physicalMismatchAssociation',
            'physicalmismatchassociation',
            'physicalmismatchassociation',
            AssociationStorageKind.LINK_ENTITY,
            true,
            false,
            [
                new AssociationRoleDescriptor(
                    'OwnedParcel',
                    'AssociationCases.Base.PhysicalMismatchAssociation.OwnedParcel',
                    'parcelFk',
                    'AssociationCases.Base.Parcel',
                    'ch.example.association.domain.Parcel',
                    0,
                    -1,
                    false,
                    false,
                    false,
                    false
                ),
                new AssociationRoleDescriptor(
                    'SemanticOwner',
                    'AssociationCases.Base.PhysicalMismatchAssociation.SemanticOwner',
                    'ownerFk',
                    'AssociationCases.Base.Person',
                    'ch.example.association.domain.Person',
                    1,
                    1,
                    true,
                    false,
                    false,
                    false
                )
            ],
            [],
            []
        ),
        'AssociationCases.Base.SameTargetAssociation': new AssociationDescriptor(
            'AssociationCases.Base.SameTargetAssociation',
            'AssociationCases.Base.SameTargetAssociation',
            'ch.example.association.domain.SameTargetAssociation',
            'sameTargetAssociation',
            'sameTargetAssociation',
            'sametargetassociation',
            'sametargetassociation',
            AssociationStorageKind.LINK_ENTITY,
            true,
            false,
            [
                new AssociationRoleDescriptor(
                    'PrimaryPerson',
                    'AssociationCases.Base.SameTargetAssociation.PrimaryPerson',
                    'primaryPersonId',
                    'AssociationCases.Base.Person',
                    'ch.example.association.domain.Person',
                    0,
                    1,
                    false,
                    false,
                    false,
                    false
                ),
                new AssociationRoleDescriptor(
                    'SecondaryPerson',
                    'AssociationCases.Base.SameTargetAssociation.SecondaryPerson',
                    'secondaryPersonId',
                    'AssociationCases.Base.Person',
                    'ch.example.association.domain.Person',
                    0,
                    1,
                    false,
                    false,
                    false,
                    false
                )
            ],
            [],
            []
        ),
        'AssociationCases.Extended.ExtendedTopicAssociation': new AssociationDescriptor(
            'AssociationCases.Extended.ExtendedTopicAssociation',
            'AssociationCases.Extended.ExtendedTopicAssociation',
            'ch.example.association.domain.ExtendedTopicAssociation',
            'extendedTopicAssociation',
            'extendedTopicAssociation',
            'extendedtopicassociation',
            'extendedtopicassociation',
            AssociationStorageKind.LINK_ENTITY,
            true,
            false,
            [
                new AssociationRoleDescriptor(
                    'ExtendedParcelRole',
                    'AssociationCases.Extended.ExtendedTopicAssociation.ExtendedParcelRole',
                    'extParcelId',
                    'AssociationCases.Extended.ExtendedParcel',
                    'ch.example.association.domain.ExtendedParcel',
                    0,
                    1,
                    false,
                    false,
                    false,
                    false
                ),
                new AssociationRoleDescriptor(
                    'ExtendedPersonRole',
                    'AssociationCases.Extended.ExtendedTopicAssociation.ExtendedPersonRole',
                    'extPersonId',
                    'AssociationCases.Base.Person',
                    'ch.example.association.domain.Person',
                    0,
                    -1,
                    false,
                    false,
                    false,
                    false
                )
            ],
            [],
            []
        ),
        'AssociationCases.Extended.TernaryAssociation': new AssociationDescriptor(
            'AssociationCases.Extended.TernaryAssociation',
            'AssociationCases.Extended.TernaryAssociation',
            'ch.example.association.domain.TernaryAssociation',
            'ternaryAssociation',
            'ternaryAssociation',
            'ternaryassociation',
            'ternaryassociation',
            AssociationStorageKind.LINK_ENTITY,
            true,
            false,
            [
                new AssociationRoleDescriptor(
                    'DocumentRole',
                    'AssociationCases.Extended.TernaryAssociation.DocumentRole',
                    'documentRoleId',
                    'AssociationCases.Base.Document',
                    'ch.example.association.domain.Document',
                    0,
                    1,
                    false,
                    false,
                    false,
                    false
                ),
                new AssociationRoleDescriptor(
                    'ParcelRole',
                    'AssociationCases.Extended.TernaryAssociation.ParcelRole',
                    'parcelRoleId',
                    'AssociationCases.Base.Parcel',
                    'ch.example.association.domain.Parcel',
                    0,
                    -1,
                    false,
                    false,
                    false,
                    false
                ),
                new AssociationRoleDescriptor(
                    'PersonRole',
                    'AssociationCases.Extended.TernaryAssociation.PersonRole',
                    'personRoleId',
                    'AssociationCases.Base.Person',
                    'ch.example.association.domain.Person',
                    0,
                    -1,
                    false,
                    false,
                    false,
                    false
                )
            ],
            [
                new AssociationAttributeDescriptor(
                    'Note',
                    'note',
                    'String',
                    RuntimeCoreType.TEXT,
                    'Note',
                    false,
                    50,
                    null,
                    null,
                    false
                )
            ],
            []
        )
    ]

    static final Map<String, AssociationContextDescriptor> CONTEXTS = [
        'AssociationCases.Base.AssociationWithAttribute::DocumentRole': new AssociationContextDescriptor(
            'AssociationCases.Base.AssociationWithAttribute::DocumentRole',
            'AssociationCases.Base.AssociationWithAttribute',
            'ch.example.association.domain.Document',
            'DocumentRole',
            'documentRoleId',
            ['PersonRole'],
            ['personRoleId'],
            'Persons',
            'interlis.association.associationCasesBaseAssociationWithAttribute.documentRole.label',
            'CONTEXTUAL_FORM',
            AssociationCreateMode.CONTEXTUAL_FORM,
            true,
            true,
            true,
            0,
            -1,
            []
        ),
        'AssociationCases.Base.AssociationWithAttribute::PersonRole': new AssociationContextDescriptor(
            'AssociationCases.Base.AssociationWithAttribute::PersonRole',
            'AssociationCases.Base.AssociationWithAttribute',
            'ch.example.association.domain.Person',
            'PersonRole',
            'personRoleId',
            ['DocumentRole'],
            ['documentRoleId'],
            'Documents',
            'interlis.association.associationCasesBaseAssociationWithAttribute.personRole.label',
            'CONTEXTUAL_FORM',
            AssociationCreateMode.CONTEXTUAL_FORM,
            true,
            true,
            true,
            0,
            -1,
            []
        ),
        'AssociationCases.Base.EmptyAssociation::ParcelRole': new AssociationContextDescriptor(
            'AssociationCases.Base.EmptyAssociation::ParcelRole',
            'AssociationCases.Base.EmptyAssociation',
            'ch.example.association.domain.Parcel',
            'ParcelRole',
            'parcelRoleId',
            ['PersonRole'],
            ['personRoleId'],
            'Persons',
            'interlis.association.associationCasesBaseEmptyAssociation.parcelRole.label',
            'RELATED_LIST',
            AssociationCreateMode.QUICK,
            true,
            true,
            true,
            0,
            -1,
            []
        ),
        'AssociationCases.Base.EmptyAssociation::PersonRole': new AssociationContextDescriptor(
            'AssociationCases.Base.EmptyAssociation::PersonRole',
            'AssociationCases.Base.EmptyAssociation',
            'ch.example.association.domain.Person',
            'PersonRole',
            'personRoleId',
            ['ParcelRole'],
            ['parcelRoleId'],
            'Parcel',
            'interlis.association.associationCasesBaseEmptyAssociation.personRole.label',
            'RELATED_TO_ONE',
            AssociationCreateMode.QUICK,
            true,
            true,
            true,
            0,
            1,
            []
        ),
        'AssociationCases.Base.ExternalCompositeAssociation::Buildings': new AssociationContextDescriptor(
            'AssociationCases.Base.ExternalCompositeAssociation::Buildings',
            'AssociationCases.Base.ExternalCompositeAssociation',
            'ch.example.association.domain.Building',
            'Buildings',
            'buildingId',
            ['Owner'],
            ['ownerId'],
            'Person',
            'interlis.association.associationCasesBaseExternalCompositeAssociation.buildings.label',
            'RELATED_TO_ONE',
            AssociationCreateMode.CONTEXTUAL_FORM,
            true,
            true,
            true,
            1,
            1,
            []
        ),
        'AssociationCases.Base.ExternalCompositeAssociation::Owner': new AssociationContextDescriptor(
            'AssociationCases.Base.ExternalCompositeAssociation::Owner',
            'AssociationCases.Base.ExternalCompositeAssociation',
            'ch.example.association.domain.Person',
            'Owner',
            'ownerId',
            ['Buildings'],
            ['buildingId'],
            'Buildings',
            'interlis.association.associationCasesBaseExternalCompositeAssociation.owner.label',
            'RELATED_LIST',
            AssociationCreateMode.CONTEXTUAL_FORM,
            true,
            true,
            true,
            0,
            -1,
            []
        ),
        'AssociationCases.Base.OrderedAssociation::Docs': new AssociationContextDescriptor(
            'AssociationCases.Base.OrderedAssociation::Docs',
            'AssociationCases.Base.OrderedAssociation',
            'ch.example.association.domain.Document',
            'Docs',
            'docsId',
            ['Owner'],
            ['ownerId'],
            'Person',
            'interlis.association.associationCasesBaseOrderedAssociation.docs.label',
            'RELATED_TO_ONE',
            AssociationCreateMode.CONTEXTUAL_FORM,
            true,
            true,
            true,
            1,
            1,
            []
        ),
        'AssociationCases.Base.OrderedAssociation::Owner': new AssociationContextDescriptor(
            'AssociationCases.Base.OrderedAssociation::Owner',
            'AssociationCases.Base.OrderedAssociation',
            'ch.example.association.domain.Person',
            'Owner',
            'ownerId',
            ['Docs'],
            ['docsId'],
            'Documents',
            'interlis.association.associationCasesBaseOrderedAssociation.owner.label',
            'RELATED_LIST',
            AssociationCreateMode.CONTEXTUAL_FORM,
            true,
            true,
            true,
            0,
            -1,
            []
        ),
        'AssociationCases.Base.PhysicalMismatchAssociation::OwnedParcel': new AssociationContextDescriptor(
            'AssociationCases.Base.PhysicalMismatchAssociation::OwnedParcel',
            'AssociationCases.Base.PhysicalMismatchAssociation',
            'ch.example.association.domain.Parcel',
            'OwnedParcel',
            'parcelFk',
            ['SemanticOwner'],
            ['ownerFk'],
            'Person',
            'interlis.association.associationCasesBasePhysicalMismatchAssociation.ownedParcel.label',
            'RELATED_TO_ONE',
            AssociationCreateMode.QUICK,
            true,
            true,
            true,
            1,
            1,
            []
        ),
        'AssociationCases.Base.PhysicalMismatchAssociation::SemanticOwner': new AssociationContextDescriptor(
            'AssociationCases.Base.PhysicalMismatchAssociation::SemanticOwner',
            'AssociationCases.Base.PhysicalMismatchAssociation',
            'ch.example.association.domain.Person',
            'SemanticOwner',
            'ownerFk',
            ['OwnedParcel'],
            ['parcelFk'],
            'Parcels',
            'interlis.association.associationCasesBasePhysicalMismatchAssociation.semanticOwner.label',
            'RELATED_LIST',
            AssociationCreateMode.QUICK,
            true,
            true,
            true,
            0,
            -1,
            []
        ),
        'AssociationCases.Base.SameTargetAssociation::PrimaryPerson': new AssociationContextDescriptor(
            'AssociationCases.Base.SameTargetAssociation::PrimaryPerson',
            'AssociationCases.Base.SameTargetAssociation',
            'ch.example.association.domain.Person',
            'PrimaryPerson',
            'primaryPersonId',
            ['SecondaryPerson'],
            ['secondaryPersonId'],
            'Person',
            'interlis.association.associationCasesBaseSameTargetAssociation.primaryPerson.label',
            'RELATED_TO_ONE',
            AssociationCreateMode.QUICK,
            true,
            true,
            true,
            0,
            1,
            []
        ),
        'AssociationCases.Base.SameTargetAssociation::SecondaryPerson': new AssociationContextDescriptor(
            'AssociationCases.Base.SameTargetAssociation::SecondaryPerson',
            'AssociationCases.Base.SameTargetAssociation',
            'ch.example.association.domain.Person',
            'SecondaryPerson',
            'secondaryPersonId',
            ['PrimaryPerson'],
            ['primaryPersonId'],
            'Person',
            'interlis.association.associationCasesBaseSameTargetAssociation.secondaryPerson.label',
            'RELATED_TO_ONE',
            AssociationCreateMode.QUICK,
            true,
            true,
            true,
            0,
            1,
            []
        ),
        'AssociationCases.Extended.ExtendedTopicAssociation::ExtendedParcelRole': new AssociationContextDescriptor(
            'AssociationCases.Extended.ExtendedTopicAssociation::ExtendedParcelRole',
            'AssociationCases.Extended.ExtendedTopicAssociation',
            'ch.example.association.domain.ExtendedParcel',
            'ExtendedParcelRole',
            'extParcelId',
            ['ExtendedPersonRole'],
            ['extPersonId'],
            'Persons',
            'interlis.association.associationCasesExtendedExtendedTopicAssociation.extendedParcelRole.label',
            'RELATED_LIST',
            AssociationCreateMode.QUICK,
            true,
            true,
            true,
            0,
            -1,
            []
        ),
        'AssociationCases.Extended.ExtendedTopicAssociation::ExtendedPersonRole': new AssociationContextDescriptor(
            'AssociationCases.Extended.ExtendedTopicAssociation::ExtendedPersonRole',
            'AssociationCases.Extended.ExtendedTopicAssociation',
            'ch.example.association.domain.Person',
            'ExtendedPersonRole',
            'extPersonId',
            ['ExtendedParcelRole'],
            ['extParcelId'],
            'ExtendedParcel',
            'interlis.association.associationCasesExtendedExtendedTopicAssociation.extendedPersonRole.label',
            'RELATED_TO_ONE',
            AssociationCreateMode.QUICK,
            true,
            true,
            true,
            0,
            1,
            []
        ),
        'AssociationCases.Extended.TernaryAssociation::DocumentRole': new AssociationContextDescriptor(
            'AssociationCases.Extended.TernaryAssociation::DocumentRole',
            'AssociationCases.Extended.TernaryAssociation',
            'ch.example.association.domain.Document',
            'DocumentRole',
            'documentRoleId',
            ['ParcelRole', 'PersonRole'],
            ['parcelRoleId', 'personRoleId'],
            'AssociationCases.Extended.TernaryAssociation.DocumentRole',
            'interlis.association.associationCasesExtendedTernaryAssociation.documentRole.label',
            'NARY_CONTEXTUAL_FORM',
            AssociationCreateMode.CONTEXTUAL_FORM,
            true,
            true,
            true,
            0,
            -1,
            []
        ),
        'AssociationCases.Extended.TernaryAssociation::ParcelRole': new AssociationContextDescriptor(
            'AssociationCases.Extended.TernaryAssociation::ParcelRole',
            'AssociationCases.Extended.TernaryAssociation',
            'ch.example.association.domain.Parcel',
            'ParcelRole',
            'parcelRoleId',
            ['DocumentRole', 'PersonRole'],
            ['documentRoleId', 'personRoleId'],
            'AssociationCases.Extended.TernaryAssociation.ParcelRole',
            'interlis.association.associationCasesExtendedTernaryAssociation.parcelRole.label',
            'NARY_CONTEXTUAL_FORM',
            AssociationCreateMode.CONTEXTUAL_FORM,
            true,
            true,
            true,
            0,
            -1,
            []
        ),
        'AssociationCases.Extended.TernaryAssociation::PersonRole': new AssociationContextDescriptor(
            'AssociationCases.Extended.TernaryAssociation::PersonRole',
            'AssociationCases.Extended.TernaryAssociation',
            'ch.example.association.domain.Person',
            'PersonRole',
            'personRoleId',
            ['DocumentRole', 'ParcelRole'],
            ['documentRoleId', 'parcelRoleId'],
            'AssociationCases.Extended.TernaryAssociation.PersonRole',
            'interlis.association.associationCasesExtendedTernaryAssociation.personRole.label',
            'NARY_CONTEXTUAL_FORM',
            AssociationCreateMode.CONTEXTUAL_FORM,
            true,
            true,
            true,
            0,
            -1,
            []
        )
    ]

    static final Map<String, List<String>> CONTEXT_IDS_BY_PARTICIPANT = [
        'ch.example.association.domain.Building': ['AssociationCases.Base.ExternalCompositeAssociation::Buildings'],
        'ch.example.association.domain.Document': ['AssociationCases.Base.AssociationWithAttribute::DocumentRole', 'AssociationCases.Base.OrderedAssociation::Docs', 'AssociationCases.Extended.TernaryAssociation::DocumentRole'],
        'ch.example.association.domain.ExtendedParcel': ['AssociationCases.Extended.ExtendedTopicAssociation::ExtendedParcelRole'],
        'ch.example.association.domain.Parcel': ['AssociationCases.Base.EmptyAssociation::ParcelRole', 'AssociationCases.Base.PhysicalMismatchAssociation::OwnedParcel', 'AssociationCases.Extended.TernaryAssociation::ParcelRole'],
        'ch.example.association.domain.Person': ['AssociationCases.Base.AssociationWithAttribute::PersonRole', 'AssociationCases.Base.EmptyAssociation::PersonRole', 'AssociationCases.Base.ExternalCompositeAssociation::Owner', 'AssociationCases.Base.OrderedAssociation::Owner', 'AssociationCases.Base.PhysicalMismatchAssociation::SemanticOwner', 'AssociationCases.Base.SameTargetAssociation::PrimaryPerson', 'AssociationCases.Base.SameTargetAssociation::SecondaryPerson', 'AssociationCases.Extended.ExtendedTopicAssociation::ExtendedPersonRole', 'AssociationCases.Extended.TernaryAssociation::PersonRole']
    ]

    static final InterlisAssociationRegistry INSTANCE = new InterlisAssociationRegistry(ASSOCIATIONS, CONTEXTS)

    private final Map<String, AssociationDescriptor> associationsByName
    private final Map<String, AssociationContextDescriptor> contextsById

    private InterlisAssociationRegistry(Map<String, AssociationDescriptor> associations, Map<String, AssociationContextDescriptor> contexts) {
        associationsByName = Collections.unmodifiableMap(new LinkedHashMap<>(associations))
        contextsById = Collections.unmodifiableMap(new LinkedHashMap<>(contexts))
    }

    @Override
    Collection<AssociationDescriptor> associations() { ASSOCIATIONS.values() }

    @Override
    Optional<AssociationDescriptor> association(String name) {
        return Optional.ofNullable(associationsByName[name])
    }

    @Override
    Collection<AssociationContextDescriptor> contexts() { CONTEXTS.values() }

    @Override
    Optional<AssociationContextDescriptor> context(String id) {
        return Optional.ofNullable(contextsById[id])
    }

    @Override
    List<AssociationContextDescriptor> contextsForParticipant(String domainClassName) {
        return (CONTEXT_IDS_BY_PARTICIPANT[domainClassName] ?: [])
            .collect { String id -> contextsById[id] }
            .findAll { it != null }
    }

}
