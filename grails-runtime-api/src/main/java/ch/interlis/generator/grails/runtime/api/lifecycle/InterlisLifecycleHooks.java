package ch.interlis.generator.grails.runtime.api.lifecycle;

import ch.interlis.generator.grails.runtime.api.security.AssociationOperationContext;
import ch.interlis.generator.grails.runtime.api.security.DomainOperationContext;

/**
 * Optional lifecycle hooks for generated runtime operations. Hooks never
 * replace security checks; they only observe or extend the operation.
 */
public interface InterlisLifecycleHooks {

    default void beforeCreate(DomainOperationContext context, Object instance) {
    }

    default void afterCreate(DomainOperationContext context, Object instance) {
    }

    default void beforeUpdate(DomainOperationContext context, Object instance) {
    }

    default void afterUpdate(DomainOperationContext context, Object instance) {
    }

    default void beforeDelete(DomainOperationContext context, Object instance) {
    }

    default void afterDelete(DomainOperationContext context, Object identifier) {
    }

    default void beforeAssociationCreate(
        AssociationOperationContext context,
        Object participant,
        Object target
    ) {
    }

    default void afterAssociationCreate(
        AssociationOperationContext context,
        Object associationInstance
    ) {
    }
}
