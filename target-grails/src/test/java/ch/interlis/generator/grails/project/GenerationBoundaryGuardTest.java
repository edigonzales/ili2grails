package ch.interlis.generator.grails.project;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generation-Boundary-Guard (Spezifikation §55.2): Nach Abschluss der
 * Plan-before-write-Migration dürfen direkte Produktions-Schreibzugriffe
 * (Files.writeString / Files.write / Files.delete / Files.deleteIfExists)
 * nur in den dafür vorgesehenen Writer-/Executor-Klassen vorkommen.
 *
 * <p>Klar begründete Ausnahmen:</p>
 * <ul>
 *   <li>Kompatibilitäts-Wrapper der einzelnen Generatoren
 *       ({@code generate(...)}-Methoden) schreiben über die
 *       {@code plan()}-Ergebnisse - sie sind die dokumentierte
 *       Legacy-Ausnahme, der {@code GrailsCrudGenerator} nutzt sie nicht
 *       mehr.</li>
 *   <li>{@code GrailsGenerationExecutor} und
 *       {@code GeneratedProjectManifestStore} sind die einzigen
 *       produktiven Write-Orte.</li>
 *   <li>{@code GrailsProjectCustomizer} und
 *       {@code GrailsTemplateOverlayInstaller} sind die deprecated
 *       Legacy-Orchestratoren (P1-Ära) - dokumentierte Ausnahme.</li>
 * </ul>
 */
class GenerationBoundaryGuardTest {

    private static final List<String> ALLOWED_WRITE_CLASSES = List.of(
        "GrailsGenerationExecutor.java",
        "GeneratedProjectManifestStore.java",
        "GrailsDomainGenerator.java",
        "GrailsEnumGenerator.java",
        "GrailsAssociationRegistryGenerator.java",
        "GrailsUiRegistryGenerator.java",
        "GrailsBuildGradleUpdater.java",
        "GrailsApplicationYamlUpdater.java",
        "GrailsRuntimeDependencyInstaller.java",
        "GrailsAssetManifestUpdater.java",
        "GrailsApplicationConfigurationUpdater.java",
        "GrailsScaffoldingTemplateInstaller.java",
        "GrailsProjectCustomizer.java",
        "GrailsTemplateOverlayInstaller.java",
        "GrailsViewGenerator.java",
        "GrailsControllerGenerator.java",
        "LegacyRuntimeMigrator.java",
        "GenerationPlanReportWriter.java"
    );

    private static final List<String> WRITE_PATTERNS = List.of(
        "Files.writeString",
        "Files.write(",
        "Files.delete",
        "Files.deleteIfExists"
    );

    @Test
    void directWritesOnlyInWriterAndExecutorClasses() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Path file : productionSources()) {
            String fileName = file.getFileName().toString();
            if (!fileName.endsWith(".java")) {
                continue;
            }
            if (ALLOWED_WRITE_CLASSES.contains(fileName)) {
                continue;
            }
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (String pattern : WRITE_PATTERNS) {
                    if (line.contains(pattern)) {
                        violations.add(fileName + ":" + (i + 1) + " " + pattern);
                    }
                }
            }
        }
        assertThat(violations)
            .as("direct file writes are only allowed in the executor/manifest "
                + "store and the documented compat wrappers")
            .isEmpty();
    }

    private static List<Path> productionSources() throws IOException {
        Path sourceRoot = Path.of("target-grails/src/main/java/ch/interlis/generator/grails");
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            return files.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .toList();
        }
    }
}
