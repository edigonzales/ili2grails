package ch.interlis.generator.grails;

/**
 * Diagnostic raised while planning typed runtime descriptors.
 */
public record RuntimeDescriptorDiagnostic(
    RuntimeDescriptorDiagnosticCode code,
    String subject,
    String message
) {
}
