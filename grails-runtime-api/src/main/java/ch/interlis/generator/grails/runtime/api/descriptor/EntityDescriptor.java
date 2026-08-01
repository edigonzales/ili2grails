package ch.interlis.generator.grails.runtime.api.descriptor;

/**
 * Immutable entry describing an association domain within the registry
 * (navigation and kind only).
 */
public record EntityDescriptor(
    String iliName,
    DomainKind kind,
    boolean showInNavigation
) {

    public EntityDescriptor {
        kind = kind == null ? DomainKind.ASSOCIATION : kind;
    }
}
