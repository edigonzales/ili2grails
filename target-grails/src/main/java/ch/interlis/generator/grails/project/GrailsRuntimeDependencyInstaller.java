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

    private static final String DEPENDENCY_MARKER = "ili2grails-runtime-dependency";

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
        // Managed-Block vorhanden: idempotent, nichts zu tun. Die Prüfung
        // erfolgt VOR dem Strippen, damit der Block nie doppelt eingefügt wird.
        if (existingContent.contains("// <" + DEPENDENCY_MARKER + ">")) {
            return new TextFileEdit(relativePath, existingContent, false,
                "runtime plugin dependency present");
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
        int insertAt = -1;
        int[] buildscriptRange = findBlockRange(content, "buildscript", null);
        int[] dependenciesRange = findBlockRange(content, "dependencies", buildscriptRange);
        insertAt = dependenciesRange != null ? dependenciesRange[0] : -1;
        if (insertAt < 0) {
            insertAt = content.indexOf("dependencies {");
        }
        if (insertAt < 0) {
            throw new IllegalStateException(
                "Cannot locate top-level dependencies block in build.gradle");
        }
        int brace = content.indexOf('{', insertAt);
        String before = content.substring(0, brace + 1);
        String after = content.substring(brace + 1);
        content = before + "\n    " + managedBlock(coordinates) + "\n" + after;
        return new TextFileEdit(relativePath, content, true, "runtime plugin dependency");
    }

    /**
     * Findet den Block-Range eines top-level Blocks ({@code name { ... }}).
     * Der buildscript-Block wird übersprungen, damit die Runtime-Dependency
     * nie im buildscript-dependencies-Block landet (P2-D013).
     */
    private static int[] findBlockRange(String content, String blockName,
                                        int[] skipRange) {
        int searchFrom = skipRange != null ? skipRange[1] : 0;
        int start = content.indexOf(blockName + " {", searchFrom);
        if (start < 0) {
            return null;
        }
        int brace = content.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return new int[] {start, i + 1};
                }
            }
        }
        return new int[] {start, content.length()};
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
