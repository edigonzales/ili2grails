package ch.interlis.generator.grails.verification;

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
 * Verification-Secret-Guard (Spezifikation §55.4): Reports und committed
 * Verification-Dateien enthalten keine Secrets.
 *
 * <p>False Positives durch Testbeispiele werden gezielt ausgenommen, nicht
 * global: Testquellen dürfen Passwort-Fixtures enthalten, damit die
 * Redaction/Blocking-Pfade testbar bleiben.</p>
 */
class VerificationSecretGuardTest {

    private static final List<String> SECRET_PATTERNS = List.of(
        "password=",
        "dbpwd",
        "Authorization:",
        "Bearer ",
        "/private/"
    );

    @Test
    void committedVerificationFilesContainNoSecrets() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Path file : committedVerificationFiles()) {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (String pattern : SECRET_PATTERNS) {
                    if (line.toLowerCase().contains(pattern.toLowerCase())) {
                        violations.add(file + ":" + (i + 1) + " " + pattern);
                    }
                }
            }
        }
        assertThat(violations)
            .as("committed verification files must not contain secrets")
            .isEmpty();
    }

    @Test
    void generatedReportsAreRedacted() throws Exception {
        Path reportDir = Path.of("build/reports");
        if (!Files.isDirectory(reportDir)) {
            return; // keine Reports in diesem Lauf
        }
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(reportDir)) {
            for (Path file : files.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".json")
                    || path.toString().endsWith(".md"))
                .toList()) {
                String content = Files.readString(file);
                for (String pattern : SECRET_PATTERNS) {
                    if (content.toLowerCase().contains(pattern.toLowerCase())) {
                        violations.add(file + " " + pattern);
                    }
                }
            }
        }
        assertThat(violations)
            .as("verification reports must be redacted")
            .isEmpty();
    }

    private List<Path> committedVerificationFiles() throws IOException, URISyntaxException {
        Class<?> probe = VerificationSecretGuardTest.class;
        URL resource = probe.getClassLoader().getResource(
            probe.getPackageName().replace('.', '/'));
        if (resource == null) {
            throw new IllegalStateException("Could not locate test classes directory");
        }
        Path classesDir = Path.of(resource.toURI());
        Path moduleRoot = classesDir.getParent().getParent().getParent()
            .getParent().getParent().getParent().getParent().getParent().getParent();
        List<Path> files = new ArrayList<>();
        Path docsVerification = moduleRoot.resolve("docs/verification");
        if (Files.isDirectory(docsVerification)) {
            try (Stream<Path> stream = Files.walk(docsVerification)) {
                stream.filter(Files::isRegularFile).forEach(files::add);
            }
        }
        Path corpus = moduleRoot.resolve("verification/model-corpus.yaml");
        if (Files.isRegularFile(corpus)) {
            files.add(corpus);
        }
        return files;
    }
}
