package ch.interlis.generator.reader.ili2db;

import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogSnapshot;
import ch.interlis.generator.reader.ili2db.schema.GeometrySchemaSnapshot;
import ch.interlis.generator.reader.ili2db.schema.JdbcSchemaSnapshot;

import java.util.List;

/**
 * Typisiertes Ergebnis eines ili2db-Lesedurchgangs; enthält ausschliesslich
 * immutable Objekte.
 */
public record Ili2dbReadResult(
    ModelMetadata metadata,
    Ili2dbCatalogSnapshot catalog,
    JdbcSchemaSnapshot schema,
    GeometrySchemaSnapshot geometry,
    List<Ili2dbDiagnostic> diagnostics
) {

    public Ili2dbReadResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean hasBlockingDiagnostics() {
        return diagnostics.stream().anyMatch(Ili2dbDiagnostic::isBlocking);
    }

    public List<Ili2dbDiagnostic> blockingDiagnostics() {
        return diagnostics.stream().filter(Ili2dbDiagnostic::isBlocking).toList();
    }

    public void throwIfBlocking() throws Ili2dbReadException {
        if (hasBlockingDiagnostics()) {
            String summary = blockingDiagnostics().stream()
                .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message())
                .reduce((left, right) -> left + "\n  - " + right)
                .orElse("unknown read failure");
            throw new Ili2dbReadException("Blocking ili2db read diagnostics:\n  - " + summary,
                diagnostics);
        }
    }
}
