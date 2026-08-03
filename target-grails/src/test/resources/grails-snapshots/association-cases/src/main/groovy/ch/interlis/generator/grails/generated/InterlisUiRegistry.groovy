package ch.interlis.generator.grails.generated

import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationRoleDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.DisplayDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.DomainKind
import ch.interlis.generator.grails.runtime.api.descriptor.FieldDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.FieldKind
import ch.interlis.generator.grails.runtime.api.descriptor.GeometryDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipMode
import ch.interlis.generator.grails.runtime.api.descriptor.RelationshipDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.RuntimeCoreType
import ch.interlis.generator.grails.runtime.api.registry.DomainRegistry

final class InterlisUiRegistry implements DomainRegistry {

    static final List<DomainDescriptor> DOMAINS = [
        new DomainDescriptor(
            'AssociationCases.Base.AssociationWithAttribute',
            'AssociationCases',
            'Base',
            'ch.example.association.domain.AssociationWithAttribute',
            'associationWithAttribute',
            'AssociationWithAttribute',
            'AssociationWithAttribute',
            DomainKind.ASSOCIATION,
            false,
            new DisplayDescriptor(
                null,
                ['roleNote'],
                ['roleNote']
            ),
            [
                'documentRoleId': new FieldDescriptor(
                    'documentRoleId',
                    'AssociationCases.Base.AssociationWithAttribute.DocumentRole',
                    'Document',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'DocumentRole',
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'personRoleId': new FieldDescriptor(
                    'personRoleId',
                    'AssociationCases.Base.AssociationWithAttribute.PersonRole',
                    'Person',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'PersonRole',
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'roleNote': new FieldDescriptor(
                    'roleNote',
                    'AssociationCases.Base.AssociationWithAttribute.RoleNote',
                    'String',
                    RuntimeCoreType.TEXT,
                    FieldKind.SCALAR,
                    'RoleNote',
                    false,
                    30,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            ],
            [
                'documentRoleId': new RelationshipDescriptor(
                    'documentRoleId',
                    'documentRoleId',
                    'ch.example.association.domain.Document',
                    'ASSOCIATION_ROLE',
                    'DocumentRole',
                    'document_role_id',
                    'DocumentRole',
                    false
                ),
                'personRoleId': new RelationshipDescriptor(
                    'personRoleId',
                    'personRoleId',
                    'ch.example.association.domain.Person',
                    'ASSOCIATION_ROLE',
                    'PersonRole',
                    'person_role_id',
                    'PersonRole',
                    false
                )
            ],
            [:],
            [:]
        ),
        new DomainDescriptor(
            'AssociationCases.Base.Building',
            'AssociationCases',
            'Base',
            'ch.example.association.domain.Building',
            'building',
            'Building',
            'Building',
            DomainKind.CLASS,
            true,
            new DisplayDescriptor(
                null,
                ['name'],
                ['name']
            ),
            [
                'name': new FieldDescriptor(
                    'name',
                    'AssociationCases.Base.Building.Name',
                    'String',
                    RuntimeCoreType.TEXT,
                    FieldKind.SCALAR,
                    'Name',
                    false,
                    40,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            ],
            [:],
            [:],
            [:]
        ),
        new DomainDescriptor(
            'AssociationCases.Base.Document',
            'AssociationCases',
            'Base',
            'ch.example.association.domain.Document',
            'document',
            'Document',
            'Document',
            DomainKind.CLASS,
            true,
            new DisplayDescriptor(
                null,
                ['title'],
                ['title']
            ),
            [
                'title': new FieldDescriptor(
                    'title',
                    'AssociationCases.Base.Document.Title',
                    'String',
                    RuntimeCoreType.TEXT,
                    FieldKind.SCALAR,
                    'Title',
                    true,
                    80,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            ],
            [:],
            [:],
            [:]
        ),
        new DomainDescriptor(
            'AssociationCases.Base.EmptyAssociation',
            'AssociationCases',
            'Base',
            'ch.example.association.domain.EmptyAssociation',
            'emptyAssociation',
            'EmptyAssociation',
            'EmptyAssociation',
            DomainKind.ASSOCIATION,
            false,
            new DisplayDescriptor(
                null,
                [],
                []
            ),
            [
                'parcelRoleId': new FieldDescriptor(
                    'parcelRoleId',
                    'AssociationCases.Base.EmptyAssociation.ParcelRole',
                    'Parcel',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'ParcelRole',
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'personRoleId': new FieldDescriptor(
                    'personRoleId',
                    'AssociationCases.Base.EmptyAssociation.PersonRole',
                    'Person',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'PersonRole',
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            ],
            [
                'parcelRoleId': new RelationshipDescriptor(
                    'parcelRoleId',
                    'parcelRoleId',
                    'ch.example.association.domain.Parcel',
                    'ASSOCIATION_ROLE',
                    'ParcelRole',
                    'parcel_role_id',
                    'ParcelRole',
                    false
                ),
                'personRoleId': new RelationshipDescriptor(
                    'personRoleId',
                    'personRoleId',
                    'ch.example.association.domain.Person',
                    'ASSOCIATION_ROLE',
                    'PersonRole',
                    'person_role_id',
                    'PersonRole',
                    false
                )
            ],
            [:],
            [:]
        ),
        new DomainDescriptor(
            'AssociationCases.Base.ExternalCompositeAssociation',
            'AssociationCases',
            'Base',
            'ch.example.association.domain.ExternalCompositeAssociation',
            'externalCompositeAssociation',
            'ExternalCompositeAssociation',
            'ExternalCompositeAssociation',
            DomainKind.ASSOCIATION,
            false,
            new DisplayDescriptor(
                null,
                [],
                []
            ),
            [
                'buildingId': new FieldDescriptor(
                    'buildingId',
                    'AssociationCases.Base.ExternalCompositeAssociation.Buildings',
                    'Building',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'Buildings',
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'ownerId': new FieldDescriptor(
                    'ownerId',
                    'AssociationCases.Base.ExternalCompositeAssociation.Owner',
                    'Person',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'Owner',
                    true,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            ],
            [
                'buildingId': new RelationshipDescriptor(
                    'buildingId',
                    'buildingId',
                    'ch.example.association.domain.Building',
                    'ASSOCIATION_ROLE',
                    'Buildings',
                    'building_id',
                    'Buildings',
                    false
                ),
                'ownerId': new RelationshipDescriptor(
                    'ownerId',
                    'ownerId',
                    'ch.example.association.domain.Person',
                    'ASSOCIATION_ROLE',
                    'Owner',
                    'owner_id',
                    'Owner',
                    true
                )
            ],
            [:],
            [:]
        ),
        new DomainDescriptor(
            'AssociationCases.Base.OrderedAssociation',
            'AssociationCases',
            'Base',
            'ch.example.association.domain.OrderedAssociation',
            'orderedAssociation',
            'OrderedAssociation',
            'OrderedAssociation',
            DomainKind.ASSOCIATION,
            false,
            new DisplayDescriptor(
                null,
                [],
                []
            ),
            [
                'docsId': new FieldDescriptor(
                    'docsId',
                    'AssociationCases.Base.OrderedAssociation.Docs',
                    'Document',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'Docs',
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'ownerId': new FieldDescriptor(
                    'ownerId',
                    'AssociationCases.Base.OrderedAssociation.Owner',
                    'Person',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'Owner',
                    true,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            ],
            [
                'docsId': new RelationshipDescriptor(
                    'docsId',
                    'docsId',
                    'ch.example.association.domain.Document',
                    'ASSOCIATION_ROLE',
                    'Docs',
                    'docs_id',
                    'Docs',
                    false
                ),
                'ownerId': new RelationshipDescriptor(
                    'ownerId',
                    'ownerId',
                    'ch.example.association.domain.Person',
                    'ASSOCIATION_ROLE',
                    'Owner',
                    'owner_id',
                    'Owner',
                    true
                )
            ],
            [:],
            [:]
        ),
        new DomainDescriptor(
            'AssociationCases.Base.Parcel',
            'AssociationCases',
            'Base',
            'ch.example.association.domain.Parcel',
            'parcel',
            'Parcel',
            'Parcel',
            DomainKind.CLASS,
            true,
            new DisplayDescriptor(
                null,
                ['ident'],
                ['ident']
            ),
            [
                'ident': new FieldDescriptor(
                    'ident',
                    'AssociationCases.Base.Parcel.Ident',
                    'String',
                    RuntimeCoreType.TEXT,
                    FieldKind.SCALAR,
                    'Ident',
                    true,
                    20,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            ],
            [:],
            [:],
            [:]
        ),
        new DomainDescriptor(
            'AssociationCases.Base.Person',
            'AssociationCases',
            'Base',
            'ch.example.association.domain.Person',
            'person',
            'Person',
            'Person',
            DomainKind.CLASS,
            true,
            new DisplayDescriptor(
                null,
                ['name'],
                ['name']
            ),
            [
                'name': new FieldDescriptor(
                    'name',
                    'AssociationCases.Base.Person.Name',
                    'String',
                    RuntimeCoreType.TEXT,
                    FieldKind.SCALAR,
                    'Name',
                    true,
                    50,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            ],
            [:],
            [:],
            [:]
        ),
        new DomainDescriptor(
            'AssociationCases.Base.PhysicalMismatchAssociation',
            'AssociationCases',
            'Base',
            'ch.example.association.domain.PhysicalMismatchAssociation',
            'physicalMismatchAssociation',
            'PhysicalMismatchAssociation',
            'PhysicalMismatchAssociation',
            DomainKind.ASSOCIATION,
            false,
            new DisplayDescriptor(
                null,
                [],
                []
            ),
            [
                'ownerFk': new FieldDescriptor(
                    'ownerFk',
                    'AssociationCases.Base.PhysicalMismatchAssociation.SemanticOwner',
                    'Person',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'SemanticOwner',
                    true,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'parcelFk': new FieldDescriptor(
                    'parcelFk',
                    'AssociationCases.Base.PhysicalMismatchAssociation.OwnedParcel',
                    'Parcel',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'OwnedParcel',
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            ],
            [
                'ownerFk': new RelationshipDescriptor(
                    'ownerFk',
                    'ownerFk',
                    'ch.example.association.domain.Person',
                    'ASSOCIATION_ROLE',
                    'SemanticOwner',
                    'owner_fk',
                    'SemanticOwner',
                    true
                ),
                'parcelFk': new RelationshipDescriptor(
                    'parcelFk',
                    'parcelFk',
                    'ch.example.association.domain.Parcel',
                    'ASSOCIATION_ROLE',
                    'OwnedParcel',
                    'parcel_fk',
                    'OwnedParcel',
                    false
                )
            ],
            [:],
            [:]
        ),
        new DomainDescriptor(
            'AssociationCases.Base.SameTargetAssociation',
            'AssociationCases',
            'Base',
            'ch.example.association.domain.SameTargetAssociation',
            'sameTargetAssociation',
            'SameTargetAssociation',
            'SameTargetAssociation',
            DomainKind.ASSOCIATION,
            false,
            new DisplayDescriptor(
                null,
                [],
                []
            ),
            [
                'primaryPersonId': new FieldDescriptor(
                    'primaryPersonId',
                    'AssociationCases.Base.SameTargetAssociation.PrimaryPerson',
                    'Person',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'PrimaryPerson',
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'secondaryPersonId': new FieldDescriptor(
                    'secondaryPersonId',
                    'AssociationCases.Base.SameTargetAssociation.SecondaryPerson',
                    'Person',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'SecondaryPerson',
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            ],
            [
                'primaryPersonId': new RelationshipDescriptor(
                    'primaryPersonId',
                    'primaryPersonId',
                    'ch.example.association.domain.Person',
                    'ASSOCIATION_ROLE',
                    'PrimaryPerson',
                    'primary_person_id',
                    'PrimaryPerson',
                    false
                ),
                'secondaryPersonId': new RelationshipDescriptor(
                    'secondaryPersonId',
                    'secondaryPersonId',
                    'ch.example.association.domain.Person',
                    'ASSOCIATION_ROLE',
                    'SecondaryPerson',
                    'secondary_person_id',
                    'SecondaryPerson',
                    false
                )
            ],
            [:],
            [:]
        ),
        new DomainDescriptor(
            'AssociationCases.Extended.ExtendedParcel',
            'AssociationCases',
            'Extended',
            'ch.example.association.domain.ExtendedParcel',
            'extendedParcel',
            'ExtendedParcel',
            'ExtendedParcel',
            DomainKind.CLASS,
            true,
            new DisplayDescriptor(
                null,
                ['extraCode'],
                ['extraCode']
            ),
            [
                'extraCode': new FieldDescriptor(
                    'extraCode',
                    'AssociationCases.Extended.ExtendedParcel.ExtraCode',
                    'String',
                    RuntimeCoreType.TEXT,
                    FieldKind.SCALAR,
                    'ExtraCode',
                    false,
                    20,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            ],
            [:],
            [:],
            [:]
        ),
        new DomainDescriptor(
            'AssociationCases.Extended.ExtendedTopicAssociation',
            'AssociationCases',
            'Extended',
            'ch.example.association.domain.ExtendedTopicAssociation',
            'extendedTopicAssociation',
            'ExtendedTopicAssociation',
            'ExtendedTopicAssociation',
            DomainKind.ASSOCIATION,
            false,
            new DisplayDescriptor(
                null,
                [],
                []
            ),
            [
                'extParcelId': new FieldDescriptor(
                    'extParcelId',
                    'AssociationCases.Extended.ExtendedTopicAssociation.ExtendedParcelRole',
                    'ExtendedParcel',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'ExtendedParcelRole',
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'extPersonId': new FieldDescriptor(
                    'extPersonId',
                    'AssociationCases.Extended.ExtendedTopicAssociation.ExtendedPersonRole',
                    'Person',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'ExtendedPersonRole',
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            ],
            [
                'extParcelId': new RelationshipDescriptor(
                    'extParcelId',
                    'extParcelId',
                    'ch.example.association.domain.ExtendedParcel',
                    'ASSOCIATION_ROLE',
                    'ExtendedParcelRole',
                    'ext_parcel_id',
                    'ExtendedParcelRole',
                    false
                ),
                'extPersonId': new RelationshipDescriptor(
                    'extPersonId',
                    'extPersonId',
                    'ch.example.association.domain.Person',
                    'ASSOCIATION_ROLE',
                    'ExtendedPersonRole',
                    'ext_person_id',
                    'ExtendedPersonRole',
                    false
                )
            ],
            [:],
            [:]
        ),
        new DomainDescriptor(
            'AssociationCases.Extended.TernaryAssociation',
            'AssociationCases',
            'Extended',
            'ch.example.association.domain.TernaryAssociation',
            'ternaryAssociation',
            'TernaryAssociation',
            'TernaryAssociation',
            DomainKind.ASSOCIATION,
            false,
            new DisplayDescriptor(
                null,
                ['note'],
                ['note']
            ),
            [
                'documentRoleId': new FieldDescriptor(
                    'documentRoleId',
                    'AssociationCases.Extended.TernaryAssociation.DocumentRole',
                    'Document',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'DocumentRole',
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'note': new FieldDescriptor(
                    'note',
                    'AssociationCases.Extended.TernaryAssociation.Note',
                    'String',
                    RuntimeCoreType.TEXT,
                    FieldKind.SCALAR,
                    'Note',
                    false,
                    50,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'parcelRoleId': new FieldDescriptor(
                    'parcelRoleId',
                    'AssociationCases.Extended.TernaryAssociation.ParcelRole',
                    'Parcel',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'ParcelRole',
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'personRoleId': new FieldDescriptor(
                    'personRoleId',
                    'AssociationCases.Extended.TernaryAssociation.PersonRole',
                    'Person',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'PersonRole',
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            ],
            [
                'documentRoleId': new RelationshipDescriptor(
                    'documentRoleId',
                    'documentRoleId',
                    'ch.example.association.domain.Document',
                    'ASSOCIATION_ROLE',
                    'DocumentRole',
                    'document_role_id',
                    'DocumentRole',
                    false
                ),
                'parcelRoleId': new RelationshipDescriptor(
                    'parcelRoleId',
                    'parcelRoleId',
                    'ch.example.association.domain.Parcel',
                    'ASSOCIATION_ROLE',
                    'ParcelRole',
                    'parcel_role_id',
                    'ParcelRole',
                    false
                ),
                'personRoleId': new RelationshipDescriptor(
                    'personRoleId',
                    'personRoleId',
                    'ch.example.association.domain.Person',
                    'ASSOCIATION_ROLE',
                    'PersonRole',
                    'person_role_id',
                    'PersonRole',
                    false
                )
            ],
            [:],
            [:]
        )
    ].asImmutable()

    static final InterlisUiRegistry INSTANCE = new InterlisUiRegistry(DOMAINS)

    private final Map<String, DomainDescriptor> byIliName
    private final Map<String, DomainDescriptor> byClassName
    private final Map<String, List<DomainDescriptor>> byModelName

    private InterlisUiRegistry(List<DomainDescriptor> domains) {
        Map<String, DomainDescriptor> iliNames = new LinkedHashMap<>()
        Map<String, DomainDescriptor> classNames = new LinkedHashMap<>()
        Map<String, List<DomainDescriptor>> modelNames = new LinkedHashMap<>()
        domains.each { DomainDescriptor domain ->
            iliNames.put(domain.iliName(), domain)
            if (domain.domainClassName() != null) {
                classNames.put(domain.domainClassName(), domain)
            }
            String model = domain.modelName() ?: ''
            modelNames.put(model, (modelNames[model] ?: []) + domain)
        }
        byIliName = Collections.unmodifiableMap(iliNames)
        byClassName = Collections.unmodifiableMap(classNames)
        byModelName = Collections.unmodifiableMap(modelNames)
    }

    @Override
    Collection<DomainDescriptor> domains() { DOMAINS }

    @Override
    Optional<DomainDescriptor> byIliName(String name) {
        return Optional.ofNullable(byIliName[name])
    }

    @Override
    Optional<DomainDescriptor> byDomainClassName(String qualifiedClassName) {
        return Optional.ofNullable(byClassName[qualifiedClassName])
    }

    @Override
    List<DomainDescriptor> byModel(String modelName) {
        return byModelName[modelName] ?: []
    }

}
