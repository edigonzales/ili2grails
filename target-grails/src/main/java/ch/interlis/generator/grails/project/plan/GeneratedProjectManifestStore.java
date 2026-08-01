package ch.interlis.generator.grails.project.plan;

import ch.interlis.generator.grails.project.GrailsProjectFileOwner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Liest und schreibt das Generations-Manifest (Spezifikation §40).
 * Schreiben ist atomar: temporäre Datei im selben Verzeichnis, dann Move.
 * Das Manifest wird zuletzt geschrieben; scheitert ein vorheriger Write,
 * wird kein neues Manifest publiziert.
 */
public final class GeneratedProjectManifestStore {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public Optional<GeneratedProjectManifest> read(Path projectRoot) throws IOException {
        Path manifestPath = manifestPath(projectRoot);
        if (!Files.isRegularFile(manifestPath)) {
            return Optional.empty();
        }
        GeneratedProjectManifest manifest =
            JSON_MAPPER.readValue(manifestPath.toFile(), GeneratedProjectManifest.class);
        validate(manifest);
        return Optional.of(manifest);
    }

    public void writeAtomically(Path projectRoot, GeneratedProjectManifest manifest)
        throws IOException {
        validate(manifest);
        Path manifestPath = manifestPath(projectRoot);
        if (manifestPath.getParent() != null) {
            Files.createDirectories(manifestPath.getParent());
        }
        String json = JSON_MAPPER.writeValueAsString(manifest) + System.lineSeparator();
        Path temp = manifestPath.resolveSibling(manifestPath.getFileName() + ".tmp");
        Files.writeString(temp, json, StandardCharsets.UTF_8);
        try {
            Files.move(temp, manifestPath, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, manifestPath, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public Path manifestPath(Path projectRoot) {
        return projectRoot.resolve(".ili2grails/generation-manifest.json");
    }

    public void validate(GeneratedProjectManifest manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("manifest is null");
        }
        if (manifest.schemaVersion() != GeneratedProjectManifest.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                "unsupported manifest schema version " + manifest.schemaVersion());
        }
        Set<String> seen = new HashSet<>();
        for (ManagedFileManifestEntry entry : manifest.files()) {
            if (entry.path() == null || entry.path().isBlank()) {
                throw new IllegalArgumentException("manifest entry without path");
            }
            Path path = Path.of(entry.path());
            if (path.isAbsolute()) {
                throw new IllegalArgumentException("absolute manifest path: " + entry.path());
            }
            for (Path part : path) {
                if (part.toString().equals("..")) {
                    throw new IllegalArgumentException("manifest path traversal: " + entry.path());
                }
            }
            if (!seen.add(entry.path())) {
                throw new IllegalArgumentException("duplicate manifest entry: " + entry.path());
            }
            if (entry.owner() == null) {
                throw new IllegalArgumentException("manifest entry without owner: " + entry.path());
            }
            if (entry.sha256() == null || !SHA256_PATTERN.matcher(entry.sha256()).matches()) {
                throw new IllegalArgumentException(
                    "invalid sha256 in manifest entry: " + entry.path());
            }
            if (entry.owner() == GrailsProjectFileOwner.RUNTIME_PLUGIN) {
                throw new IllegalArgumentException(
                    "runtime-plugin files must not appear in the manifest: " + entry.path());
            }
        }
    }
}
