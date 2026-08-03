package ch.interlis.generator.grails.runtime.registry

import ch.interlis.generator.grails.runtime.api.registry.RegistryDiagnostic
import ch.interlis.generator.grails.runtime.api.registry.RegistryDiagnosticCode
import ch.interlis.generator.grails.runtime.api.registry.RegistryValidationReport
import spock.lang.Specification

/**
 * Runtime-Safety-Contract (Spezifikation §63):
 *
 * - gültige Registry: Schreiben erlaubt;
 * - ungültige Registry und Strict-Modus: Startup-Fehler;
 * - ungültige Registry und Non-strict-Modus: Anwendung startet read-only;
 * - alle generierten Schreiboperationen sind technisch blockiert;
 * - technische Safety ist unabhängig von der fachlichen Authorization-Policy.
 */
class InterlisRuntimeSafetyStateSpec extends Specification {

    private static RegistryValidationReport validReport() {
        return new RegistryValidationReport([])
    }

    private static RegistryValidationReport invalidReport() {
        return new RegistryValidationReport([
            new RegistryDiagnostic(RegistryDiagnosticCode.UNKNOWN_DOMAIN_CLASS,
                "com.example.Person", "unknown domain class", true, [:])
        ])
    }

    def "valid registry keeps writes enabled"() {
        given:
        def state = new InterlisRuntimeSafetyState()

        when:
        state.initialize(validReport(), true)

        then:
        state.writeAllowed
        state.requireWriteAllowed() == null
    }

    def "strict invalid registry fails startup"() {
        given:
        def state = new InterlisRuntimeSafetyState()

        when:
        state.initialize(invalidReport(), true)

        then:
        thrown(IllegalStateException)
    }

    def "non-strict invalid registry starts read-only and blocks every write"() {
        given:
        def state = new InterlisRuntimeSafetyState()

        when:
        state.initialize(invalidReport(), false)

        then:
        !state.writeAllowed
        state.report().hasBlockingDiagnostics()

        when:
        state.requireWriteAllowed()

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains("write operations are disabled")
    }

    def "valid report in non-strict mode keeps writes enabled"() {
        given:
        def state = new InterlisRuntimeSafetyState()

        when:
        state.initialize(validReport(), false)

        then:
        state.writeAllowed
    }

    def "safety state defaults to write-allowed before initialization"() {
        given:
        def state = new InterlisRuntimeSafetyState()

        expect:
        state.writeAllowed
        state.report().diagnostics().isEmpty()
    }

    def "safety is independent from authorization policy"() {
        given:
        def state = new InterlisRuntimeSafetyState()
        def policy = new ch.interlis.generator.grails.runtime.policy.AllowAllInterlisAuthorizationPolicy()

        when:
        state.initialize(invalidReport(), false)
        // Die fachliche Policy erlaubt weiterhin - aber die technische Safety
        // blockiert trotzdem jeden Write (Command-Services prüfen zuerst den
        // Safety-State, nicht die Policy).
        boolean policyWouldAllow = policy.canCreate(null)

        then:
        policyWouldAllow
        !state.writeAllowed

        when:
        state.requireWriteAllowed()

        then:
        thrown(IllegalStateException)
    }
}
