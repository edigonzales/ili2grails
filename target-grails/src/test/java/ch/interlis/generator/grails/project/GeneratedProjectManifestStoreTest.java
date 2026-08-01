package ch.interlis.generator.grails.project;

import ch.interlis.generator.grails.project.plan.GeneratedProjectManifest;
import ch.interlis.generator.grails.project.plan.GeneratedProjectManifestStore;
import ch.interlis.generator.grails.project.plan.ManagedFileManifestEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Manifest-Verträge (Spezifikation §40, §48).
 */
class GeneratedProjectManifestStoreTest {

    @TempDir
    Path tempDir;

    private static final String SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void writeAndReadRoundtrip() throws Exception {
        GeneratedProjectManifestStore store = new GeneratedProjectManifestStore();
        GeneratedProjectManifest manifest = new GeneratedProjectManifest(
            1, "1.0.0", "1.0.0", "Model", "model-fp", "config-fp",
            List.of(new ManagedFileManifestEntry("grails-app/domain/X.groovy",
                GrailsProjectFileOwner.GENERATOR_MANAGED, SHA)));

        store.writeAtomically(tempDir, manifest);
        Optional<GeneratedProjectManifest> read = store.read(tempDir);
        assertThat(read).isPresent();
        assertThat(read.get().modelName()).isEqualTo("Model");
        assertThat(read.get().files()).hasSize(1);
        assertThat(store.manifestPath(tempDir))
            .isEqualTo(tempDir.resolve(".ili2grails/generation-manifest.json"));
    }

    @Test
    void missingManifestReturnsEmpty() throws Exception {
        GeneratedProjectManifestStore store = new GeneratedProjectManifestStore();
        assertThat(store.read(tempDir)).isEmpty();
    }

    @Test
    void pathTraversalIsRejected() throws Exception {
        GeneratedProjectManifestStore store = new GeneratedProjectManifestStore();
        GeneratedProjectManifest invalid = new GeneratedProjectManifest(
            1, "1.0.0", "1.0.0", "Model", "fp", "fp",
            List.of(new ManagedFileManifestEntry("../escape.groovy",
                GrailsProjectFileOwner.GENERATOR_MANAGED, SHA)));
        assertThatThrownBy(() -> store.writeAtomically(tempDir, invalid))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("traversal");
    }

    @Test
    void absolutePathIsRejected() throws Exception {
        GeneratedProjectManifestStore store = new GeneratedProjectManifestStore();
        GeneratedProjectManifest invalid = new GeneratedProjectManifest(
            1, "1.0.0", "1.0.0", "Model", "fp", "fp",
            List.of(new ManagedFileManifestEntry("/etc/passwd",
                GrailsProjectFileOwner.GENERATOR_MANAGED, SHA)));
        assertThatThrownBy(() -> store.writeAtomically(tempDir, invalid))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("absolute");
    }

    @Test
    void duplicateEntriesAndInvalidShaAreRejected() throws Exception {
        GeneratedProjectManifestStore store = new GeneratedProjectManifestStore();
        GeneratedProjectManifest duplicate = new GeneratedProjectManifest(
            1, "1.0.0", "1.0.0", "Model", "fp", "fp",
            List.of(
                new ManagedFileManifestEntry("a.groovy", GrailsProjectFileOwner.GENERATOR_MANAGED, SHA),
                new ManagedFileManifestEntry("a.groovy", GrailsProjectFileOwner.GENERATOR_MANAGED, SHA)));
        assertThatThrownBy(() -> store.writeAtomically(tempDir, duplicate))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate");

        GeneratedProjectManifest invalidSha = new GeneratedProjectManifest(
            1, "1.0.0", "1.0.0", "Model", "fp", "fp",
            List.of(new ManagedFileManifestEntry("a.groovy",
                GrailsProjectFileOwner.GENERATOR_MANAGED, "not-a-sha")));
        assertThatThrownBy(() -> store.writeAtomically(tempDir, invalidSha))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sha256");
    }

    @Test
    void runtimePluginEntriesAreRejected() throws Exception {
        GeneratedProjectManifestStore store = new GeneratedProjectManifestStore();
        GeneratedProjectManifest invalid = new GeneratedProjectManifest(
            1, "1.0.0", "1.0.0", "Model", "fp", "fp",
            List.of(new ManagedFileManifestEntry("grails-app/views/interlisUi/index.gsp",
                GrailsProjectFileOwner.RUNTIME_PLUGIN, SHA)));
        assertThatThrownBy(() -> store.writeAtomically(tempDir, invalid))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("runtime-plugin");
    }

    @Test
    void unsupportedSchemaVersionIsRejected() throws Exception {
        GeneratedProjectManifestStore store = new GeneratedProjectManifestStore();
        GeneratedProjectManifest invalid = new GeneratedProjectManifest(
            99, "1.0.0", "1.0.0", "Model", "fp", "fp", List.of());
        assertThatThrownBy(() -> store.writeAtomically(tempDir, invalid))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("schema version");
    }

    @Test
    void manifestIsDeterministicWithoutVolatileData() throws Exception {
        GeneratedProjectManifestStore store = new GeneratedProjectManifestStore();
        GeneratedProjectManifest manifest = new GeneratedProjectManifest(
            1, "1.0.0", "1.0.0", "Model", "fp", "fp",
            List.of(new ManagedFileManifestEntry("a.groovy",
                GrailsProjectFileOwner.GENERATOR_MANAGED, SHA)));
        store.writeAtomically(tempDir, manifest);
        String first = Files.readString(store.manifestPath(tempDir));
        store.writeAtomically(tempDir, manifest);
        String second = Files.readString(store.manifestPath(tempDir));
        assertThat(second).isEqualTo(first);
        assertThat(first)
            .doesNotContain("timestamp")
            .doesNotContain("password")
            .doesNotContain(tempDir.toString());
    }
}
