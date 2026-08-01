package ch.interlis.generator.reader.ili2db;

import java.sql.SQLException;
import java.util.List;

/**
 * Wird im Strict-Modus geworfen, wenn blockierende Reader-Diagnostics
 * vorliegen. Erweitert {@link SQLException}, damit bestehende Aufrufer
 * unverändert bleiben.
 */
public final class Ili2dbReadException extends SQLException {

    private final List<Ili2dbDiagnostic> diagnostics;

    public Ili2dbReadException(String message, List<Ili2dbDiagnostic> diagnostics) {
        super(message);
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public List<Ili2dbDiagnostic> getDiagnostics() {
        return diagnostics;
    }
}
