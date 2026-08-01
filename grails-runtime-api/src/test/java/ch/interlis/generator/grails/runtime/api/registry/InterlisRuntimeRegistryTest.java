package ch.interlis.generator.grails.runtime.api.registry;

import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationCreateMode;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationRoleDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationStorageKind;
import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor;
import ch.interlis.generator.grails.runtime.api.descriptor.DomainKind;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterlisRuntimeRegistryTest {

    private static final String ASSOC = "M.T.Assoc";
    private static final String PARTICIPANT = "com.example.P";
    private static final String TARGET = "com.example.T";

    private static DomainDescriptor participantDomain() {
        return new DomainDescriptor(
            "M.T.P", "M", "T", PARTICIPANT, "p", "P", "P", DomainKind.CLASS,
            true, null, Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static AssociationDescriptor association() {
        AssociationRoleDescriptor fixed = new AssociationRoleDescriptor(
            "roleP", "P", "propertyP", "M.T.P", PARTICIPANT, 0, 1, false, false, false, false);
        AssociationRoleDescriptor target = new AssociationRoleDescriptor(
            "roleT", "T", "propertyT", "M.T.T", TARGET, 0, -1, false, false, false, false);
        return new AssociationDescriptor(
            ASSOC, ASSOC, "com.example.Assoc", "assoc", "assoc",
            "assoc", "assoc", AssociationStorageKind.LINK_ENTITY, true, true,
            List.of(fixed, target), List.of(), List.of());
    }

    private static AssociationContextDescriptor context() {
        return new AssociationContextDescriptor(
            "ctx-p", ASSOC, PARTICIPANT, "roleP", "propertyP",
            List.of("roleT"), List.of("propertyT"), "Label", "code", "QUICK_LINK",
            AssociationCreateMode.QUICK, true, true, true, 0, -1, List.of());
    }

    private static final class SimpleDomainRegistry implements DomainRegistry {
        private final List<DomainDescriptor> domains;

        SimpleDomainRegistry(List<DomainDescriptor> domains) {
            this.domains = domains;
        }

        @Override
        public Collection<DomainDescriptor> domains() {
            return domains;
        }

        @Override
        public Optional<DomainDescriptor> byIliName(String iliName) {
            return domains.stream().filter(d -> iliName.equals(d.iliName())).findFirst();
        }

        @Override
        public Optional<DomainDescriptor> byDomainClassName(String qualifiedClassName) {
            return domains.stream()
                .filter(d -> qualifiedClassName.equals(d.domainClassName()))
                .findFirst();
        }

        @Override
        public List<DomainDescriptor> byModel(String modelName) {
            return domains.stream().filter(d -> modelName.equals(d.modelName())).toList();
        }
    }

    private static final class SimpleAssociationRegistry implements AssociationRegistry {
        private final List<AssociationDescriptor> associations;
        private final List<AssociationContextDescriptor> contexts;

        SimpleAssociationRegistry(List<AssociationDescriptor> associations,
                                  List<AssociationContextDescriptor> contexts) {
            this.associations = associations;
            this.contexts = contexts;
        }

        @Override
        public Collection<AssociationDescriptor> associations() {
            return associations;
        }

        @Override
        public Optional<AssociationDescriptor> association(String name) {
            return associations.stream()
                .filter(a -> name.equals(a.associationName()))
                .findFirst();
        }

        @Override
        public Collection<AssociationContextDescriptor> contexts() {
            return contexts;
        }

        @Override
        public Optional<AssociationContextDescriptor> context(String id) {
            return contexts.stream().filter(c -> id.equals(c.id())).findFirst();
        }

        @Override
        public List<AssociationContextDescriptor> contextsForParticipant(String domainClassName) {
            return contexts.stream()
                .filter(c -> domainClassName.equals(c.participantDomainClassName()))
                .toList();
        }
    }

    private static InterlisRuntimeRegistry registry(DomainRegistry domains,
                                                    AssociationRegistry associations) {
        return new InterlisRuntimeRegistry(domains, associations, name -> null);
    }

    @Test
    void resolvesDomainByIliNameAndClassName() {
        InterlisRuntimeRegistry registry = registry(
            new SimpleDomainRegistry(List.of(participantDomain())),
            new SimpleAssociationRegistry(List.of(), List.of()));
        assertThat(registry.domainByIliName("M.T.P")).isPresent();
        assertThat(registry.domainByClassName(PARTICIPANT)).isPresent();
        assertThat(registry.domainByIliName("unknown")).isEmpty();
    }

    @Test
    void rejectsDuplicateDomainIliNames() {
        assertThatThrownBy(() -> registry(
            new SimpleDomainRegistry(List.of(participantDomain(), participantDomain())),
            new SimpleAssociationRegistry(List.of(), List.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Duplicate domain iliName");
    }

    @Test
    void rejectsDuplicateContextIds() {
        assertThatThrownBy(() -> registry(
            new SimpleDomainRegistry(List.of(participantDomain())),
            new SimpleAssociationRegistry(List.of(association()), List.of(context(), context()))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Duplicate association context id");
    }

    @Test
    void rejectsContextReferencingUnknownAssociation() {
        assertThatThrownBy(() -> registry(
            new SimpleDomainRegistry(List.of(participantDomain())),
            new SimpleAssociationRegistry(List.of(), List.of(context()))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown association");
    }

    @Test
    void rejectsContextFixedRoleOutsideAssociation() {
        AssociationContextDescriptor broken = new AssociationContextDescriptor(
            "ctx-broken", ASSOC, PARTICIPANT, "unknownRole", "propertyP",
            List.of("roleT"), List.of("propertyT"), "Label", "code", "QUICK_LINK",
            AssociationCreateMode.QUICK, true, true, true, 0, -1, List.of());
        assertThatThrownBy(() -> registry(
            new SimpleDomainRegistry(List.of(participantDomain())),
            new SimpleAssociationRegistry(List.of(association()), List.of(broken))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("fixed role");
    }

    @Test
    void requireContextValidatesParticipantOwnership() {
        InterlisRuntimeRegistry registry = registry(
            new SimpleDomainRegistry(List.of(participantDomain())),
            new SimpleAssociationRegistry(List.of(association()), List.of(context())));
        Class<?> participantType = com.example.P.class;
        assertThat(registry.requireContext(participantType, "ctx-p")).isNotNull();
        assertThatThrownBy(() -> registry.requireContext(String.class, "ctx-p"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not belong");
        assertThatThrownBy(() -> registry.requireContext(participantType, "unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown association context");
    }

    @Test
    void contextsForParticipantAreSortedAndImmutable() {
        InterlisRuntimeRegistry registry = registry(
            new SimpleDomainRegistry(List.of(participantDomain())),
            new SimpleAssociationRegistry(List.of(association()), List.of(context())));
        List<AssociationContextDescriptor> contexts = registry.contextsForParticipant(PARTICIPANT);
        assertThat(contexts).hasSize(1);
        assertThatThrownBy(() -> contexts.add(context()))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requireAssociationFailsForUnknownName() {
        InterlisRuntimeRegistry registry = registry(
            new SimpleDomainRegistry(List.of(participantDomain())),
            new SimpleAssociationRegistry(List.of(association()), List.of(context())));
        assertThat(registry.association(ASSOC)).isPresent();
        assertThatThrownBy(() -> registry.requireAssociation("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown association");
    }
}
