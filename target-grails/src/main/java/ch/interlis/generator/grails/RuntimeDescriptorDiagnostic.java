package ch.interlis.generator.grails;

import ch.interlis.generator.grails.runtime.api.descriptor.RuntimeDescriptorSeverity;

import java.util.Map;

/**
 * Diagnostic raised while planning typed runtime descriptors
 * (Spezifikation §18).
 */
public record RuntimeDescriptorDiagnostic(
    RuntimeDescriptorSeverity severity,
    RuntimeDescriptorDiagnosticCode code,
    String subject,
    String message,
    Map<String, String> details
) {

    public RuntimeDescriptorDiagnostic {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public boolean blocking() {
        return severity == RuntimeDescriptorSeverity.ERROR;
    }
}
