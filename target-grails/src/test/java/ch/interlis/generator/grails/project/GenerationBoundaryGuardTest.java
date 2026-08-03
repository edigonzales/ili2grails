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
 * <p>{@code GrailsGenerationExecutor} und
 * {@code GeneratedProjectManifestStore} sind die einzigen produktiven
 * Schreiborte innerhalb des Zielprojekts. Der Report-Writer schreibt nur
 * explizit angeforderte Dateien ausserhalb des Zielprojekts.</p>
 */
class GenerationBoundaryGuardTest {

    private static final List<String> ALLOWED_WRITE_CLASSES = List.of(
        "GrailsGenerationExecutor.java",
        "GeneratedProjectManifestStore.java",
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
            .as("direct file writes are only allowed in executor, manifest store, and report writer")
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
