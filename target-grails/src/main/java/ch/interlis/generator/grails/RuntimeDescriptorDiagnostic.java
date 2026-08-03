package ch.interlis.generator.grails;

import ch.interlis.generator.grails.runtime.api.descriptor.RuntimeDescriptorSeverity;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

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
        TreeMap<String, String> sorted = new TreeMap<>();
        if (details != null) {
            sorted.putAll(details);
        }
        details = Collections.unmodifiableMap(sorted);
    }

    public boolean blocking() {
        return severity == RuntimeDescriptorSeverity.ERROR;
    }
}
