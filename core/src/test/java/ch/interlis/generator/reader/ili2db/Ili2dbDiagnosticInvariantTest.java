package ch.interlis.generator.reader.ili2db;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Diagnostic-Invarianten (Spezifikation §62).
 */
class Ili2dbDiagnosticInvariantTest {

    @Test
    void diagnosticDetailsAreDeterministicallyOrdered() {
        Map<String, String> unordered = new LinkedHashMap<>();
        unordered.put("zeta", "1");
        unordered.put("alpha", "2");
        unordered.put("middle", "3");
        Ili2dbDiagnostic diagnostic = new Ili2dbDiagnostic(
            Ili2dbSeverity.WARNING, Ili2dbDiagnosticCode.OPTIONAL_META_TABLE_MISSING,
            "message", null, null, unordered);
        assertThat(diagnostic.details().keySet())
            .containsExactly("alpha", "middle", "zeta");
        assertThat(diagnostic.details()).isUnmodifiable();
    }

    @Test
    void diagnosticDetailsSurviveNullAndEmpty() {
        Ili2dbDiagnostic diagnostic = new Ili2dbDiagnostic(
            Ili2dbSeverity.WARNING, Ili2dbDiagnosticCode.OPTIONAL_META_TABLE_MISSING,
            "message", null, null, null);
        assertThat(diagnostic.details()).isEmpty();
    }

    /**
     * Explizite Test-Coverage-Map (Spezifikation §62): jeder
     * {@link Ili2dbDiagnosticCode} wird von mindestens einem fokussierten Test
     * abgedeckt. Der Test stellt sicher, dass die Map bei neuen Codes ergänzt
     * wird; die fachliche Erreichbarkeit beweisen die einzelnen Tests.
     */
    @Test
    void noReaderDiagnosticCodeIsUnreferencedByTests() {
        Map<Ili2dbDiagnosticCode, String> coveredByTest = new LinkedHashMap<>();
        coveredByTest.put(Ili2dbDiagnosticCode.REQUIRED_META_TABLE_MISSING,
            "Ili2dbCatalogReaderTest#missingRequiredMetaTableProducesFatalDiagnostic");
        coveredByTest.put(Ili2dbDiagnosticCode.OPTIONAL_META_TABLE_MISSING,
            "Ili2dbDiagnosticReachabilityTest#optionalMetaTableMissingProducesWarning");
        coveredByTest.put(Ili2dbDiagnosticCode.META_TABLE_COLUMNS_UNSUPPORTED,
            "Ili2dbDiagnosticReachabilityTest#metaTableColumnsUnsupportedProducesWarning");
        coveredByTest.put(Ili2dbDiagnosticCode.REQUESTED_MODEL_MISSING,
            "Ili2dbReadCoordinatorDiagnosticTest#missingRootModelProducesRequestedModelMissingDiagnostic");
        coveredByTest.put(Ili2dbDiagnosticCode.SELECTED_DEPENDENCY_MISSING,
            "Ili2dbReadCoordinatorDiagnosticTest#missingDependencyProducesSelectedDependencyMissingWarning");
        coveredByTest.put(Ili2dbDiagnosticCode.CLASS_MAPPING_INCOMPLETE,
            "Ili2dbDiagnosticReachabilityTest#classMappingIncompleteProducesDiagnostic");
        coveredByTest.put(Ili2dbDiagnosticCode.ATTRIBUTE_OWNER_UNRESOLVED,
            "Ili2dbDiagnosticReachabilityTest#attributeOwnerUnresolvedProducesDiagnostic");
        coveredByTest.put(Ili2dbDiagnosticCode.TARGET_CLASS_UNRESOLVED,
            "Ili2dbReadCoordinatorDiagnosticTest#strictPolicyRejectsErrorDiagnostics");
        coveredByTest.put(Ili2dbDiagnosticCode.COLUMN_SCHEMA_MISSING,
            "Ili2dbDiagnosticReachabilityTest#columnSchemaMissingProducesDiagnostic");
        coveredByTest.put(Ili2dbDiagnosticCode.ENUM_DOMAIN_UNRESOLVED,
            "Ili2dbDiagnosticReachabilityTest#enumDomainUnresolvedProducesDiagnostic");
        coveredByTest.put(Ili2dbDiagnosticCode.ENUM_TABLE_UNREADABLE,
            "Ili2dbEnumReaderTest#unreadableEnumTableProducesDiagnostic");
        coveredByTest.put(Ili2dbDiagnosticCode.INHERITANCE_UNRESOLVED,
            "Ili2dbDiagnosticReachabilityTest#inheritanceUnresolvedProducesDiagnostic");
        coveredByTest.put(Ili2dbDiagnosticCode.GEOMETRY_METADATA_UNAVAILABLE,
            "Ili2dbDiagnosticReachabilityTest#geometryMetadataUnavailableProducesWarning");
        coveredByTest.put(Ili2dbDiagnosticCode.PRIMARY_KEY_ASSUMED,
            "Ili2dbDiagnosticReachabilityTest#primaryKeyAssumedProducesWarning");
        coveredByTest.put(Ili2dbDiagnosticCode.ASSOCIATION_MAPPING_INCOMPLETE,
            "Ili2dbDiagnosticReachabilityTest#associationMappingIncompleteProducesDiagnostic");
        coveredByTest.put(Ili2dbDiagnosticCode.DATABASE_DIALECT_UNSUPPORTED,
            "Ili2dbDiagnosticReachabilityTest#unknownDialectProducesDialectUnsupportedWarning");

        assertThat(coveredByTest.keySet())
            .as("every diagnostic code must be covered by a focused test")
            .containsExactlyInAnyOrder(Ili2dbDiagnosticCode.values());
    }
}
