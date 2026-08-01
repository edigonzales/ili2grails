package ch.interlis.generator.grails.runtime.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source guard: the runtime API packages must not use raw
 * {@code Map<String,Object>} signatures as business contracts. Maps are only
 * allowed in clearly bounded adapter positions and in validation detail maps.
 */
class RuntimeApiStaticGuardTest {

    private static final List<String> GUARDED_PACKAGES = List.of(
        "descriptor",
        "registry",
        "command"
    );

    private static final Pattern MAP_SIGNATURE = Pattern.compile(
        "Map\\s*<\\s*String\\s*,\\s*Object\\s*>");

    @Test
    void descriptorRegistryAndCommandPackagesDoNotUseBusinessMapStringObjectSignatures()
        throws IOException, URISyntaxException {
        List<String> violations = new ArrayList<>();
        for (String packageName : GUARDED_PACKAGES) {
            Path sourceRoot = sourceRoot("descriptor");
            Path packageDir = sourceRoot.resolve(packageName);
            if (!Files.isDirectory(packageDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(packageDir)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    violations.addAll(checkFile(file));
                }
            }
        }
        assertThat(violations).as("Map<String,Object> business signatures in runtime API").isEmpty();
    }

    private List<String> checkFile(Path file) throws IOException {
        List<String> violations = new ArrayList<>();
        List<String> lines = Files.readAllLines(file);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            Matcher matcher = MAP_SIGNATURE.matcher(line);
            while (matcher.find()) {
                String subject = line.substring(Math.max(0, matcher.start() - 60),
                    Math.min(line.length(), matcher.end() + 60)).trim();
                violations.add(file.getFileName() + ":" + (index + 1) + " -> " + subject);
            }
        }
        return violations;
    }

    private static Path sourceRoot(String packageName) throws URISyntaxException {
        Class<?> probe = RuntimeApiStaticGuardTest.class;
        URL resource = probe.getClassLoader().getResource(
            probe.getPackageName().replace('.', '/'));
        if (resource == null) {
            throw new IllegalStateException("Could not locate test classes directory");
        }
        Path classesDir = Path.of(resource.toURI());
        Path moduleRoot = classesDir.getParent().getParent().getParent();
        return moduleRoot.resolve("src/main/java/ch/interlis/generator/grails/runtime/api");
    }
}
