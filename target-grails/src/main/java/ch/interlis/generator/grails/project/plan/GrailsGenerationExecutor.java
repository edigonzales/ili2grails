package ch.interlis.generator.grails.project.plan;

import ch.interlis.generator.grails.runtime.api.RuntimeVersionContract;
import ch.interlis.generator.grails.project.GrailsProjectFileOwner;
import ch.interlis.generator.grails.project.ProjectCustomizationDiagnostic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Führt einen {@link GenerationPlan} aus (Spezifikation §45):
 *
 * <ol>
 *   <li>bei Blocking-Diagnostic sofort zurückkehren - keine Datei verändert;</li>
 *   <li>Parent-Verzeichnisse vorbereiten;</li>
 *   <li>Dateien über temporäre Datei im selben Verzeichnis schreiben,
 *       atomarer Move;</li>
 *   <li>Deletes erst nach erfolgreichen Creates/Updates;</li>
 *   <li>Manifest zuletzt atomar schreiben;</li>
 *   <li>bei Fehler verständliche Diagnostic und Exception - keine stillen
 *       Partial-Success-Meldungen.</li>
 * </ol>
 */
public final class GrailsGenerationExecutor {

    private final GeneratedProjectManifestStore manifestStore = new GeneratedProjectManifestStore();

    public GenerationExecutionResult apply(Path projectRoot, GenerationPlan plan)
        throws IOException {
        if (plan.hasBlockingDiagnostics()) {
            throw new IllegalStateException(
                "Generation plan has blocking diagnostics; no project files were changed.");
        }
        List<Path> writtenFiles = new ArrayList<>();
        List<Path> deletedFiles = new ArrayList<>();

        for (ProjectChange change : plan.mutatingChanges()) {
            if (change.type() == ProjectChangeType.DELETE) {
                continue; // Deletes zuletzt
            }
            writeFile(projectRoot, change);
            writtenFiles.add(change.relativePath());
        }
        for (ProjectChange change : plan.mutatingChanges()) {
            if (change.type() == ProjectChangeType.DELETE) {
                Path target = projectRoot.resolve(change.relativePath());
                if (Files.exists(target)) {
                    Files.delete(target);
                    deletedFiles.add(change.relativePath());
                }
            }
        }

        GeneratedProjectManifest manifest = buildManifest(plan);
        manifestStore.writeAtomically(projectRoot, manifest);
        return new GenerationExecutionResult(plan, writtenFiles, deletedFiles, true);
    }

    private void writeFile(Path projectRoot, ProjectChange change) throws IOException {
        Path target = projectRoot.resolve(change.relativePath());
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Path temp = target.resolveSibling(target.getFileName() + ".ili2grails-tmp");
        Files.write(temp, change.plannedContent());
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private GeneratedProjectManifest buildManifest(GenerationPlan plan) {
        List<ManagedFileManifestEntry> files = new ArrayList<>();
        for (ProjectChange change : plan.changes()) {
            if (change.owner() != GrailsProjectFileOwner.GENERATOR_MANAGED) {
                continue;
            }
            if (change.type() == ProjectChangeType.DELETE) {
                continue;
            }
            String sha = change.plannedSha256() != null ? change.plannedSha256()
                : ch.interlis.generator.model.ModelMetadataFingerprint.sha256(
                    change.plannedContent() == null ? new byte[0] : change.plannedContent());
            files.add(new ManagedFileManifestEntry(
                change.relativePath().toString(), change.owner(), sha));
        }
        files.sort(java.util.Comparator.comparing(ManagedFileManifestEntry::path));
        ch.interlis.generator.grails.project.RuntimeCoordinates runtime =
            ch.interlis.generator.grails.project.RuntimeCoordinates.ili2grailsRuntime();
        return new GeneratedProjectManifest(
            GeneratedProjectManifest.CURRENT_SCHEMA_VERSION,
            runtime.version(),
            ch.interlis.generator.grails.runtime.api.RuntimeVersionContract.RUNTIME_API_VERSION,
            plan.modelName(),
            plan.modelFingerprint(),
            plan.configFingerprint(),
            files
        );
    }
}
