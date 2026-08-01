package ch.interlis.generator.metadata.guard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source guard 13.5: Reader-Schichtgrenzen.
 *
 * <ul>
 *   <li>catalog und schema dürfen keine IR-Builder importieren und keine
 *       IR-Container-Klassen (ModelMetadata etc.). Einzige Ausnahme ist
 *       der gemeinsame semantische Typ {@code GeometryKind}.</li>
 *   <li>assemble darf Catalog-/Schema-Snapshots und Builder importieren.</li>
 *   <li>Die Fassade {@code Ili2dbMetadataReader} enthält keine SQL-Strings
 *       (reine Delegation).</li>
 * </ul>
 */
class ReaderBoundaryGuardTest {

    private static final Pattern SQL_STRING = Pattern.compile(
        "\"(SELECT|FROM|JOIN|PRAGMA|WHERE|INSERT|UPDATE)\\s");

    private static final Pattern IR_CONTAINER_IMPORT = Pattern.compile(
        "import ch\\.interlis\\.generator\\.model\\.(ModelMetadata|ClassMetadata|AttributeMetadata"
            + "|RelationshipMetadata|AssociationMetadata|AssociationRoleMetadata|EnumMetadata)\\.");

    private static final Pattern IR_BUILDER_IMPORT = Pattern.compile(
        "import ch\\.interlis\\.generator\\.model\\.builder\\.");

    @Test
    void catalogAndSchemaPackagesDoNotImportIrBuildersOrContainers() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String packageDir : List.of("catalog", "schema")) {
            Path dir = readerRoot().resolve(packageDir);
            for (Path file : javaFiles(dir)) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (IR_BUILDER_IMPORT.matcher(line).find()) {
                        violations.add(file.getFileName() + ":" + (i + 1) + " builder import");
                    }
                    if (IR_CONTAINER_IMPORT.matcher(line).find()) {
                        violations.add(file.getFileName() + ":" + (i + 1) + " IR container import");
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void facadeContainsNoSqlStrings() throws Exception {
        List<String> violations = new ArrayList<>();
        Path facade = readerRoot().getParent().resolve("Ili2dbMetadataReader.java");
        List<String> lines = Files.readAllLines(facade);
        for (int i = 0; i < lines.size(); i++) {
            if (SQL_STRING.matcher(lines.get(i)).find()) {
                violations.add("Ili2dbMetadataReader.java:" + (i + 1));
            }
        }
        assertThat(violations).isEmpty();
    }

    private static Path readerRoot() throws URISyntaxException {
        Class<?> probe = ReaderBoundaryGuardTest.class;
        URL resource = probe.getClassLoader().getResource(
            probe.getPackageName().replace('.', '/'));
        if (resource == null) {
            throw new IllegalStateException("Could not locate test classes directory");
        }
        Path classesDir = Path.of(resource.toURI());
        Path moduleRoot = classesDir.getParent().getParent().getParent()
            .getParent().getParent().getParent().getParent().getParent().getParent();
        return moduleRoot.resolve("src/main/java/ch/interlis/generator/reader/ili2db");
    }

    private List<Path> javaFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .toList();
        }
    }
}
