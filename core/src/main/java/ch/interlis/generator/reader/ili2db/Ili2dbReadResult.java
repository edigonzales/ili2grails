package ch.interlis.generator.reader.ili2db;

import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogSnapshot;
import ch.interlis.generator.reader.ili2db.schema.GeometrySchemaSnapshot;
import ch.interlis.generator.reader.ili2db.schema.JdbcSchemaSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * Typisiertes Ergebnis eines ili2db-Lesedurchgangs; enthält ausschliesslich
 * immutable Objekte.
 *
 * <p>Vertrag (Spezifikation §14): FATAL ohne Metadata ist zulässig;
 * {@link #requireMetadata()} wirft {@link Ili2dbReadException}; keine
 * {@code NullPointerException}; keine fachliche {@code IllegalArgumentException}
 * am öffentlichen Reader-Rand.</p>
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

    public Optional<ModelMetadata> optionalMetadata() {
        return Optional.ofNullable(metadata);
    }

    /**
     * @throws Ili2dbReadException wenn keine Metadaten gebaut werden konnten
     */
    public ModelMetadata requireMetadata() throws Ili2dbReadException {
        if (metadata == null) {
            throw new Ili2dbReadException("No metadata available; blocking read diagnostics:\n  - "
                + blockingDiagnostics().stream()
                    .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message())
                    .reduce((left, right) -> left + "\n  - " + right)
                    .orElse("(no diagnostics recorded)"),
                diagnostics);
        }
        return metadata;
    }

    public boolean hasBlockingDiagnostics() {
        return diagnostics.stream().anyMatch(Ili2dbDiagnostic::isBlocking);
    }

    public List<Ili2dbDiagnostic> blockingDiagnostics() {
        return diagnostics.stream().filter(Ili2dbDiagnostic::isBlocking).toList();
    }

    public boolean hasFatalDiagnostics() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Ili2dbSeverity.FATAL);
    }

    public boolean hasErrorDiagnostics() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Ili2dbSeverity.ERROR);
    }

    /**
     * Nutzbares Resultat: Metadaten vorhanden und keine fatalen Diagnostics.
     */
    public boolean isUsable() {
        return metadata != null && !hasFatalDiagnostics();
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
