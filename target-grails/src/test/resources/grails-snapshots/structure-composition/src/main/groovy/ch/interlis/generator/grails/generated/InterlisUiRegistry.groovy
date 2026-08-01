package ch.interlis.generator.grails.generated

import ch.interlis.generator.grails.runtime.api.compat.LegacyDescriptorMapAdapter
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
            'StructureCompositionCases.Cases.Asset',
            'StructureCompositionCases',
            'Cases',
            'ch.example.structure.domain.Asset',
            'asset',
            'Asset',
            'Asset',
            DomainKind.CLASS,
            true,
            new DisplayDescriptor(
                null,
                ['name'],
                ['name']
            ),
            [
                'mainInspection': new FieldDescriptor(
                    'mainInspection',
                    'StructureCompositionCases.Cases.Asset.MainInspection',
                    'Inspection',
                    RuntimeCoreType.COMPOSITION,
                    FieldKind.RELATIONSHIP,
                    'MainInspection',
                    true,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'name': new FieldDescriptor(
                    'name',
                    'StructureCompositionCases.Cases.Asset.Name',
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
                ),
                'optionalAttachment': new FieldDescriptor(
                    'optionalAttachment',
                    'StructureCompositionCases.Cases.Asset.OptionalAttachment',
                    'Attachment',
                    RuntimeCoreType.COMPOSITION,
                    FieldKind.RELATIONSHIP,
                    'OptionalAttachment',
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
                'mainInspection': new RelationshipDescriptor(
                    'mainInspection',
                    'mainInspection',
                    'ch.example.structure.domain.Inspection',
                    'COMPOSITION_ATTRIBUTE',
                    'MainInspection',
                    'MainInspection',
                    'MainInspection',
                    true
                ),
                'optionalAttachment': new RelationshipDescriptor(
                    'optionalAttachment',
                    'optionalAttachment',
                    'ch.example.structure.domain.Attachment',
                    'COMPOSITION_ATTRIBUTE',
                    'OptionalAttachment',
                    'OptionalAttachment',
                    'OptionalAttachment',
                    false
                )
            ],
            [:],
            [:]
        ),
        new DomainDescriptor(
            'StructureCompositionCases.Cases.Attachment',
            'StructureCompositionCases',
            'Cases',
            'ch.example.structure.domain.Attachment',
            'attachment',
            'Attachment',
            'Attachment',
            DomainKind.CLASS,
            true,
            new DisplayDescriptor(
                null,
                ['fileName'],
                ['fileName']
            ),
            [
                'fileName': new FieldDescriptor(
                    'fileName',
                    'StructureCompositionCases.Cases.Attachment.FileName',
                    'String',
                    RuntimeCoreType.TEXT,
                    FieldKind.SCALAR,
                    'FileName',
                    true,
                    100,
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
            'StructureCompositionCases.Cases.Document',
            'StructureCompositionCases',
            'Cases',
            'ch.example.structure.domain.Document',
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
                    'StructureCompositionCases.Cases.Document.Title',
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
            'StructureCompositionCases.Cases.Inspection',
            'StructureCompositionCases',
            'Cases',
            'ch.example.structure.domain.Inspection',
            'inspection',
            'Inspection',
            'Inspection',
            DomainKind.CLASS,
            true,
            new DisplayDescriptor(
                null,
                ['result'],
                ['result']
            ),
            [
                'result': new FieldDescriptor(
                    'result',
                    'StructureCompositionCases.Cases.Inspection.Result',
                    'String',
                    RuntimeCoreType.TEXT,
                    FieldKind.SCALAR,
                    'Result',
                    false,
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
            'StructureCompositionCases.Cases.Owner',
            'StructureCompositionCases',
            'Cases',
            'ch.example.structure.domain.Owner',
            'owner',
            'Owner',
            'Owner',
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
                    'StructureCompositionCases.Cases.Owner.Name',
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
            'StructureCompositionCases.Cases.Part',
            'StructureCompositionCases',
            'Cases',
            'ch.example.structure.domain.Part',
            'part',
            'Part',
            'Part',
            DomainKind.CLASS,
            true,
            new DisplayDescriptor(
                null,
                ['label'],
                ['label']
            ),
            [
                'label': new FieldDescriptor(
                    'label',
                    'StructureCompositionCases.Cases.Part.Label',
                    'String',
                    RuntimeCoreType.TEXT,
                    FieldKind.SCALAR,
                    'Label',
                    true,
                    50,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'ownerRef': new FieldDescriptor(
                    'ownerRef',
                    'StructureCompositionCases.Cases.Part.OwnerRef',
                    'Owner',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'OwnerRef',
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
                'ownerRef': new RelationshipDescriptor(
                    'ownerRef',
                    'ownerRef',
                    'ch.example.structure.domain.Owner',
                    'REFERENCE_ATTRIBUTE',
                    'OwnerRef',
                    'OwnerRef',
                    'OwnerRef',
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

    // ------------------------------------------------------------------
    // Legacy map API for the pre-plugin runtime (migration only).
    // ------------------------------------------------------------------

    @Deprecated(forRemoval = true)
    static List<Map<String, Object>> legacyDomains() {
        return DOMAINS.collect { DomainDescriptor d ->
            LegacyDescriptorMapAdapter.toLegacyDomainMap(d)
        }
    }

    @Deprecated(forRemoval = true)
    static Map<String, Object> domain(String iliName) {
        DomainDescriptor descriptor = INSTANCE.byIliName[iliName]
        return descriptor == null ? null : LegacyDescriptorMapAdapter.toLegacyDomainMap(descriptor)
    }

    @Deprecated(forRemoval = true)
    static Map<String, Object> domainForClassName(String domainClassName) {
        DomainDescriptor descriptor = INSTANCE.byClassName[domainClassName]
        return descriptor == null ? null : LegacyDescriptorMapAdapter.toLegacyDomainMap(descriptor)
    }

    @Deprecated(forRemoval = true)
    static List<Map<String, Object>> domainsForModel(String modelName) {
        return INSTANCE.byModel(modelName).collect { DomainDescriptor d ->
            LegacyDescriptorMapAdapter.toLegacyDomainMap(d)
        }
    }

}
