package ch.interlis.generator.reader.sql;

import java.util.Objects;

/**
 * Qualifizierter SQL-Name aus Schema- und Objektsegment.
 * Das Rendering erfolgt ausschliesslich über einen {@link SqlIdentifierRenderer}.
 */
public record QualifiedSqlName(
    SqlIdentifier schema,
    SqlIdentifier object
) {

    public QualifiedSqlName {
        Objects.requireNonNull(object, "object");
    }

    public String render(SqlIdentifierRenderer renderer) {
        Objects.requireNonNull(renderer, "renderer");
        return renderer.qualify(schema, object);
    }
}
