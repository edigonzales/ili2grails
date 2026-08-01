package ch.interlis.generator.grails.runtime.registry

import ch.interlis.generator.grails.runtime.api.descriptor.AssociationContextDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.AssociationRoleDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.GeometryDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.InverseRelationshipDescriptor
import ch.interlis.generator.grails.runtime.api.descriptor.RelationshipDescriptor
import ch.interlis.generator.grails.runtime.api.registry.InterlisRuntimeRegistry
import ch.interlis.generator.grails.runtime.api.registry.RegistryDiagnostic
import ch.interlis.generator.grails.runtime.api.registry.RegistryDiagnosticCode
import ch.interlis.generator.grails.runtime.api.registry.RegistryValidationReport
import groovy.util.logging.Slf4j

/**
 * Validates all generated descriptors once at startup against the Grails
 * mapping context, so that invalid generated contracts fail early instead of
 * surfacing during user actions.
 *
 * <p>Strict mode: the startup fails with a summarized exception. Diagnostic
 * mode: broken writable functions are downgraded and the report is logged.</p>
 */
@Slf4j
final class InterlisRuntimeRegistryValidator {

    def grailsApplication

    RegistryValidationReport validate(InterlisRuntimeRegistry registry) {
        List<RegistryDiagnostic> diagnostics = []
        def mappingContext = grailsApplication?.mappingContext

        registry.domains().each { DomainDescriptor domain ->
            diagnostics.addAll(validateDomain(domain, mappingContext))
        }
        registry.associations().each { AssociationDescriptor association ->
            diagnostics.addAll(validateAssociation(association, mappingContext))
        }
        registry.contexts().each { AssociationContextDescriptor context ->
            diagnostics.addAll(validateContext(context, registry, mappingContext))
        }
        return new RegistryValidationReport(diagnostics)
    }

    private List<RegistryDiagnostic> validateDomain(DomainDescriptor domain, def mappingContext) {
        List<RegistryDiagnostic> diagnostics = []
        String domainClass = domain.domainClassName()
        if (domainClass != null && !domainClass.isBlank()
            && !hasDomainClass(mappingContext, domainClass)) {
            diagnostics << diagnostic(RegistryDiagnosticCode.UNKNOWN_DOMAIN_CLASS,
                domainClass, "Domain class '${domainClass}' (iliName '${domain.iliName()}') not found in the mapping context", true)
        }
        domain.fields().each { String fieldName, def field ->
            if (field.kind() == ch.interlis.generator.grails.runtime.api.descriptor.FieldKind.GEOMETRY) {
                // geometry fields are validated through the geometry map below
            }
        }
        domain.geometries().each { String fieldName, GeometryDescriptor geometry ->
            if (!hasProperty(mappingContext, domainClass, fieldName)) {
                diagnostics << diagnostic(RegistryDiagnosticCode.UNKNOWN_GEOMETRY_FIELD,
                    domain.iliName() + '.' + fieldName,
                    "Geometry field '${fieldName}' does not exist on '${domainClass}'", true)
            }
        }
        domain.relationships().each { String name, RelationshipDescriptor relationship ->
            String target = relationship.targetDomainClassName()
            if (target != null && !target.isBlank() && !hasDomainClass(mappingContext, target)) {
                diagnostics << diagnostic(RegistryDiagnosticCode.UNKNOWN_RELATIONSHIP_TARGET,
                    relationship.name(),
                    "Relationship '${relationship.name()}' of '${domain.iliName()}' targets unknown class '${target}'", true)
            }
        }
        domain.inverseRelationships().each { String name, InverseRelationshipDescriptor inverse ->
            if (!hasProperty(mappingContext, inverse.relatedDomainClassName(), inverse.relatedPropertyName())) {
                diagnostics << diagnostic(RegistryDiagnosticCode.UNKNOWN_INVERSE_RELATED_PROPERTY,
                    inverse.name(),
                    "Inverse relationship '${inverse.name()}' of '${domain.iliName()}' references unknown property " +
                        "'${inverse.relatedPropertyName()}' on '${inverse.relatedDomainClassName()}'",
                    inverse.writable())
            }
        }
        return diagnostics
    }

    private List<RegistryDiagnostic> validateAssociation(AssociationDescriptor association, def mappingContext) {
        List<RegistryDiagnostic> diagnostics = []
        String domainClass = association.domainClassName()
        if (domainClass != null && !domainClass.isBlank()
            && !hasDomainClass(mappingContext, domainClass)) {
            diagnostics << diagnostic(RegistryDiagnosticCode.UNKNOWN_ASSOCIATION_DOMAIN_CLASS,
                association.associationName(),
                "Association domain '${domainClass}' not found in the mapping context", association.writable())
        }
        association.roles().each { AssociationRoleDescriptor role ->
            String target = role.targetDomainClassName()
            if (target != null && !target.isBlank() && !hasDomainClass(mappingContext, target)) {
                diagnostics << diagnostic(RegistryDiagnosticCode.UNKNOWN_RELATIONSHIP_TARGET,
                    association.associationName() + '::' + role.name(),
                    "Role '${role.name()}' of '${association.associationName()}' targets unknown class '${target}'", true)
            }
            if (role.propertyName() != null && !hasProperty(mappingContext, domainClass, role.propertyName())) {
                diagnostics << diagnostic(RegistryDiagnosticCode.UNKNOWN_ROLE_PROPERTY,
                    association.associationName() + '::' + role.name(),
                    "Role property '${role.propertyName()}' does not exist on '${domainClass}'",
                    association.writable())
            }
        }
        return diagnostics
    }

    private List<RegistryDiagnostic> validateContext(AssociationContextDescriptor context,
                                                     InterlisRuntimeRegistry registry,
                                                     def mappingContext) {
        List<RegistryDiagnostic> diagnostics = []
        AssociationDescriptor association = registry.association(context.associationName()).orElse(null)
        if (association == null) {
            diagnostics << diagnostic(RegistryDiagnosticCode.UNRESOLVED_CONTEXT_ASSOCIATION,
                context.id(),
                "Context '${context.id()}' references unknown association '${context.associationName()}'", true)
            return diagnostics
        }
        if (context.fixedRoleName() != null && association.role(context.fixedRoleName()).isEmpty()) {
            diagnostics << diagnostic(RegistryDiagnosticCode.UNKNOWN_CONTEXT_FIXED_PROPERTY,
                context.id(),
                "Context '${context.id()}' fixed role '${context.fixedRoleName()}' is not a role of " +
                    "'${context.associationName()}'", context.writable())
        }
        if (context.fixedPropertyName() != null
            && !hasProperty(mappingContext, association.domainClassName(), context.fixedPropertyName())) {
            diagnostics << diagnostic(RegistryDiagnosticCode.UNKNOWN_CONTEXT_FIXED_PROPERTY,
                context.id(),
                "Context '${context.id()}' fixed property '${context.fixedPropertyName()}' does not exist on " +
                    "'${association.domainClassName()}' (the association domain)", context.writable())
        }
        for (String editableRole : context.editableRoleNames()) {
            if (association.role(editableRole).isEmpty()) {
                diagnostics << diagnostic(RegistryDiagnosticCode.INCONSISTENT_EDITABLE_ROLES,
                    context.id(),
                    "Context '${context.id()}' editable role '${editableRole}' is not a role of " +
                        "'${context.associationName()}'", context.writable())
            }
        }
        return diagnostics
    }

    private static boolean hasDomainClass(def mappingContext, String className) {
        if (className == null || className.isBlank()) {
            return true
        }
        try {
            def entity = mappingContext?.getPersistentEntity(className)
            return entity != null
        } catch (Exception ignored) {
            return false
        }
    }

    private static boolean hasProperty(def mappingContext, String className, String propertyName) {
        if (className == null || className.isBlank() || propertyName == null || propertyName.isBlank()) {
            return true
        }
        try {
            def entity = mappingContext?.getPersistentEntity(className)
            if (entity == null) {
                return false
            }
            if (entity.hasProperty(propertyName)) {
                return true
            }
            if (entity.getPropertyByName(propertyName) != null) {
                return true
            }
            return entity.javaClass.declaredFields.any { it.name == propertyName }
        } catch (Exception ignored) {
            return false
        }
    }

    private static RegistryDiagnostic diagnostic(RegistryDiagnosticCode code,
                                                 String subject,
                                                 String message,
                                                 boolean blocking) {
        return new RegistryDiagnostic(code, subject, message, blocking, [:])
    }
}
