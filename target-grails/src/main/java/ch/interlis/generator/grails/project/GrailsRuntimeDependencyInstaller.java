package ch.interlis.generator.grails.project;

import ch.interlis.generator.grails.project.plan.TextFileEdit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Installs or updates the {@code ili2grails-runtime} plugin dependency in the
 * application build file.
 *
 * <p>Idempotent: an existing dependency is detected and its version updated;
 * no second dependency line is added and no free regex replacement is applied
 * over the whole build file. The managed block markers are documented.</p>
 */
public final class GrailsRuntimeDependencyInstaller {

    private static final String DEPENDENCY_MARKER = "ili2grails-runtime";

    private final ch.interlis.generator.grails.GrailsBuildGradleUpdater buildGradleUpdater =
        new ch.interlis.generator.grails.GrailsBuildGradleUpdater();

    public DependencyUpdateResult install(Path buildFile, RuntimeCoordinates coordinates) throws IOException {
        Objects.requireNonNull(buildFile, "buildFile");
        Objects.requireNonNull(coordinates, "coordinates");

        if (!java.nio.file.Files.exists(buildFile)) {
            return new DependencyUpdateResult(false, coordinates.notation());
        }
        String content = java.nio.file.Files.readString(buildFile);
        boolean wasPresent = content.contains(coordinates.artifact());
        boolean versionMatches = content.contains(coordinates.notation());
        TextFileEdit edit = plan(Path.of("build.gradle"), content, coordinates);
        if (edit.changed()) {
            java.nio.file.Files.writeString(buildFile, edit.updatedContent());
        }
        return new DependencyUpdateResult(wasPresent && versionMatches, coordinates.notation());
    }

    /**
     * Reine Planungsfunktion (Spezifikation §41.5): kein Write.
     */
    public TextFileEdit plan(Path relativePath, String existingContent,
                             RuntimeCoordinates coordinates) {
        if (existingContent == null) {
            return new TextFileEdit(relativePath, null, false, "build.gradle missing");
        }
        List<String> lines = new java.util.ArrayList<>(List.of(existingContent.split("\\n", -1)));
        List<String> stripped = new java.util.ArrayList<>();
        boolean inBlock = false;
        for (String line : lines) {
            if (line.contains("// <" + DEPENDENCY_MARKER + ">")) {
                inBlock = true;
                continue;
            }
            if (line.contains("// </" + DEPENDENCY_MARKER + ">")) {
                inBlock = false;
                continue;
            }
            if (inBlock) {
                continue;
            }
            if (line.contains(coordinates.artifact())
                && line.contains("implementation")) {
                continue; // alte Version der Runtime-Dependency entfernen
            }
            stripped.add(line);
        }
        String content = String.join("\n", stripped);
        boolean blockPresent = content.contains(managedBlock(coordinates));
        if (!blockPresent) {
            content = content.replaceFirst("dependencies \\{",
                "dependencies {\n" + "    " + managedBlock(coordinates) + "\n");
        }
        return new TextFileEdit(relativePath, content, !content.equals(existingContent),
            "runtime plugin dependency");
    }

    static String managedBlock(RuntimeCoordinates coordinates) {
        return "// <ili2grails-runtime-dependency>\n"
            + "    implementation \"" + coordinates.notation() + "\"\n"
            + "    // </ili2grails-runtime-dependency>";
    }

    /**
     * Outcome of a dependency install.
     *
     * @param updated version was changed to the requested coordinates
     * @param notation installed coordinates
     */
    public record DependencyUpdateResult(boolean updated, String notation) {
    }
}
