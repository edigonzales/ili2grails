package ch.interlis.generator.grails.runtime.api.registry;

import java.util.Map;

/**
 * Single registry validation diagnostic.
 */
public record RegistryDiagnostic(
    RegistryDiagnosticCode code,
    String subject,
    String message,
    boolean blocking,
    Map<String, String> details
) {

    public RegistryDiagnostic {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
