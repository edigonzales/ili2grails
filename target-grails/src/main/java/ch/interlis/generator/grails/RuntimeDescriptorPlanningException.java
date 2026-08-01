package ch.interlis.generator.grails;

import java.util.List;

/**
 * Wird geworfen, wenn die Runtime-Descriptor-Planung blockierende
 * Diagnostics enthält. Die Generierung darf dann keine Datei schreiben
 * (Spezifikation §19.5).
 */
public final class RuntimeDescriptorPlanningException extends IllegalStateException {

    private final List<RuntimeDescriptorDiagnostic> diagnostics;

    public RuntimeDescriptorPlanningException(String message,
                                              List<RuntimeDescriptorDiagnostic> diagnostics) {
        super(message);
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public List<RuntimeDescriptorDiagnostic> getDiagnostics() {
        return diagnostics;
    }
}
