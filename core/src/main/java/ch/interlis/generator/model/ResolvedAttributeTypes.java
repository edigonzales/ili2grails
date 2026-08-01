package ch.interlis.generator.model;

/**
 * Aufgelöste Typ-Information eines Attributs, erzeugt durch
 * {@link AttributeTypeResolver} vor dem Freeze.
 */
public record ResolvedAttributeTypes(
    CoreType coreType,
    String javaType
) {
}
