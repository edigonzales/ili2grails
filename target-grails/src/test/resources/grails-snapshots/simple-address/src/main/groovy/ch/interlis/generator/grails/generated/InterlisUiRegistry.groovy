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
            'SimpleAddressModel.Addresses.Address',
            'SimpleAddressModel',
            'Addresses',
            'ch.example.simple.domain.Address',
            'address',
            'Address',
            'Address',
            DomainKind.CLASS,
            true,
            new DisplayDescriptor(
                null,
                ['astreet', 'housenumber'],
                ['astreet', 'housenumber', 'postalcode']
            ),
            [
                'astreet': new FieldDescriptor(
                    'astreet',
                    'SimpleAddressModel.Addresses.Address.Street',
                    'String',
                    RuntimeCoreType.TEXT,
                    FieldKind.SCALAR,
                    'street',
                    true,
                    100,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'housenumber': new FieldDescriptor(
                    'housenumber',
                    'SimpleAddressModel.Addresses.Address.HouseNumber',
                    'String',
                    RuntimeCoreType.TEXT,
                    FieldKind.SCALAR,
                    'houseNumber',
                    false,
                    10,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'postalcode': new FieldDescriptor(
                    'postalcode',
                    'SimpleAddressModel.Addresses.Address.PostalCode',
                    'String',
                    RuntimeCoreType.TEXT,
                    FieldKind.SCALAR,
                    'postalCode',
                    true,
                    10,
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
            'SimpleAddressModel.Addresses.Person',
            'SimpleAddressModel',
            'Addresses',
            'ch.example.simple.domain.Person',
            'person',
            'Person',
            'Person',
            DomainKind.CLASS,
            true,
            new DisplayDescriptor(
                null,
                ['firstname', 'lastname'],
                ['firstname', 'lastname']
            ),
            [
                'birthdate': new FieldDescriptor(
                    'birthdate',
                    'SimpleAddressModel.Addresses.Person.BirthDate',
                    'LocalDate',
                    RuntimeCoreType.DATE,
                    FieldKind.SCALAR,
                    'birthDate',
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'firstname': new FieldDescriptor(
                    'firstname',
                    'SimpleAddressModel.Addresses.Person.FirstName',
                    'String',
                    RuntimeCoreType.TEXT,
                    FieldKind.SCALAR,
                    'firstName',
                    true,
                    50,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'lastname': new FieldDescriptor(
                    'lastname',
                    'SimpleAddressModel.Addresses.Person.LastName',
                    'String',
                    RuntimeCoreType.TEXT,
                    FieldKind.SCALAR,
                    'lastName',
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
            'SimpleAddressModel.Addresses.PersonAddress',
            'SimpleAddressModel',
            'Addresses',
            'ch.example.simple.domain.PersonAddress',
            'personAddress',
            'PersonAddress',
            'PersonAddress',
            DomainKind.ASSOCIATION,
            false,
            new DisplayDescriptor(
                null,
                [],
                []
            ),
            [
                'addressId': new FieldDescriptor(
                    'addressId',
                    'SimpleAddressModel.Addresses.PersonAddress.address',
                    'Address',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'address',
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                'personId': new FieldDescriptor(
                    'personId',
                    'SimpleAddressModel.Addresses.PersonAddress.person',
                    'Person',
                    RuntimeCoreType.REFERENCE,
                    FieldKind.RELATIONSHIP,
                    'person',
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
                'addressId': new RelationshipDescriptor(
                    'addressId',
                    'addressId',
                    'ch.example.simple.domain.Address',
                    'ASSOCIATION_ROLE',
                    'Address',
                    'address_id',
                    'Address',
                    false
                ),
                'personId': new RelationshipDescriptor(
                    'personId',
                    'personId',
                    'ch.example.simple.domain.Person',
                    'ASSOCIATION_ROLE',
                    'Person',
                    'person_id',
                    'Person',
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
