package ch.interlis.generator.grails.runtime.registry

import ch.interlis.generator.grails.runtime.api.registry.RegistryValidationReport

/**
 * Globaler Runtime-Safety-State (Spezifikation §16).
 *
 * <p>Semantik:</p>
 * <ul>
 *   <li>gültige Registry: Schreiben erlaubt;</li>
 *   <li>ungültige Registry und Strict-Modus: Startup-Fehler
 *       ({@code initialize} wirft);</li>
 *   <li>ungültige Registry und Non-strict-Modus: Anwendung startet
 *       read-only ({@code writeAllowed=false}).</li>
 * </ul>
 *
 * <p>Alle generierten Schreiboperationen sind technisch blockiert, nicht
 * nur die UI-Buttons: Command-Services rufen {@link #requireWriteAllowed()}
 * vor jeder Schreiboperation auf. Die fachliche
 * {@code InterlisAuthorizationPolicy} bleibt davon getrennt.</p>
 */
final class InterlisRuntimeSafetyState {

    private volatile RegistryValidationReport report = new RegistryValidationReport([])

    private volatile boolean writeAllowed = true

    synchronized void initialize(RegistryValidationReport validationReport, boolean strict) {
        this.report = validationReport == null
            ? new RegistryValidationReport([])
            : validationReport
        boolean blocking = this.report.hasBlockingDiagnostics()
        if (blocking && strict) {
            String summary = this.report.blockingDiagnostics()
                .collect { "${it.code().name()}: ${it.message()}" }
                .join('\n  - ')
            throw new IllegalStateException(
                "Invalid ili2grails generated registry descriptors (strict " +
                "descriptor validation):\n  - ${summary}")
        }
        this.writeAllowed = !blocking
    }

    boolean isWriteAllowed() {
        return writeAllowed
    }

    RegistryValidationReport report() {
        return report
    }

    void requireWriteAllowed() {
        if (!writeAllowed) {
            throw new IllegalStateException(
                "Runtime descriptor validation has blocking diagnostics; " +
                "all generated write operations are disabled (read-only mode).")
        }
    }
}
