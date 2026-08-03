package ch.interlis.generator.grails;

import ch.interlis.generator.grails.project.RuntimeCoordinates;
import ch.interlis.generator.grails.project.plan.TextFileEdit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Plans all ili2grails-owned changes to a generated application's build.gradle. */
public final class GrailsBuildGradleUpdater {

    static final String DEPENDENCY_MARKER = "ili2grails-dependencies";
    static final String REPOSITORY_MARKER = "ili2grails-runtime-repository";
    private static final String OLD_RUNTIME_MARKER = "ili2grails-runtime-dependency";

    private static final String JTS_DEPENDENCY =
        "implementation \"org.locationtech.jts:jts-core:1.19.0\"";
    private static final String POSTGRES_JDBC_DEPENDENCY =
        "implementation \"org.postgresql:postgresql:42.7.7\"";
    private static final String HIBERNATE_SPATIAL_DEPENDENCY =
        "implementation \"org.hibernate:hibernate-spatial:5.6.15.Final\"";
    private static final String LEGACY_HIBERNATE_SPATIAL_DEPENDENCY =
        "implementation \"org.hibernate.orm:hibernate-spatial\"";
    private static final String BOOTSTRAP_WEBJAR_DEPENDENCY =
        "implementation \"org.webjars:bootstrap:5.3.3\"";
    private static final String OPENLAYERS_WEBJAR_DEPENDENCY =
        "implementation \"org.webjars.npm:ol:9.2.4\"";
    private static final String PROJ4_WEBJAR_DEPENDENCY =
        "implementation \"org.webjars.npm:proj4:2.11.0\"";

    private static final Set<String> KNOWN_UNMANAGED_DEPENDENCIES = Set.of(
        JTS_DEPENDENCY,
        POSTGRES_JDBC_DEPENDENCY,
        HIBERNATE_SPATIAL_DEPENDENCY,
        LEGACY_HIBERNATE_SPATIAL_DEPENDENCY,
        BOOTSTRAP_WEBJAR_DEPENDENCY,
        OPENLAYERS_WEBJAR_DEPENDENCY,
        PROJ4_WEBJAR_DEPENDENCY
    );

    /** Pure planning function; project files are written only by the generation executor. */
    public TextFileEdit plan(Path relativePath,
                             String existingContent,
                             GenerationConfig config,
                             RuntimeCoordinates runtimeCoordinates) {
        if (existingContent == null) {
            return new TextFileEdit(relativePath, null, false, "build.gradle missing");
        }

        List<String> lines = new ArrayList<>(List.of(existingContent.split("\n", -1)));
        lines = stripManagedBlock(lines, DEPENDENCY_MARKER);
        lines = stripManagedBlock(lines, OLD_RUNTIME_MARKER);
        lines = stripManagedBlock(lines, REPOSITORY_MARKER);
        lines = removeKnownUnmanagedDependencies(lines, runtimeCoordinates);
        lines = addRuntimeRepository(lines);
        lines = addManagedDependencies(lines, dependencies(config, runtimeCoordinates));

        String updatedContent = String.join("\n", lines);
        return new TextFileEdit(
            relativePath,
            updatedContent,
            !updatedContent.equals(existingContent),
            "managed ili2grails repository and dependencies"
        );
    }

    private List<String> dependencies(GenerationConfig config, RuntimeCoordinates runtimeCoordinates) {
        List<String> dependencies = new ArrayList<>();
        dependencies.add("implementation \"" + runtimeCoordinates.notation() + "\"");
        dependencies.add(POSTGRES_JDBC_DEPENDENCY);
        if (config.isGeometryEnabled()) {
            dependencies.add(JTS_DEPENDENCY);
            dependencies.add(HIBERNATE_SPATIAL_DEPENDENCY);
        }
        if (GenerationConfig.UI_THEME_BOOTSTRAP.equals(config.getUiTheme())) {
            dependencies.add(BOOTSTRAP_WEBJAR_DEPENDENCY);
        }
        if (GenerationConfig.MAP_EDITOR_OPENLAYERS.equals(config.getMapEditor())) {
            dependencies.add(OPENLAYERS_WEBJAR_DEPENDENCY);
            dependencies.add(PROJ4_WEBJAR_DEPENDENCY);
        }
        return List.copyOf(dependencies);
    }

    private List<String> removeKnownUnmanagedDependencies(List<String> lines,
                                                           RuntimeCoordinates runtimeCoordinates) {
        List<String> result = new ArrayList<>();
        String knownRuntimeDependency = "implementation \"" + runtimeCoordinates.notation() + "\"";
        for (String line : lines) {
            String trimmed = line.trim();
            boolean knownRuntimeLine = trimmed.equals(knownRuntimeDependency);
            if (!knownRuntimeLine && !KNOWN_UNMANAGED_DEPENDENCIES.contains(trimmed)) {
                result.add(line);
            }
        }
        return result;
    }

    private List<String> addRuntimeRepository(List<String> lines) {
        if (lines.stream().anyMatch(line -> line.trim().startsWith("mavenLocal()"))) {
            return lines;
        }
        int[] buildscript = findBlockRange(lines, "buildscript", null);
        int[] repositories = findBlockRange(lines, "repositories", buildscript);
        List<String> result = new ArrayList<>(lines);
        if (repositories != null) {
            String indent = detectIndent(lines, repositories[0]);
            result.addAll(repositories[1], List.of(
                indent + "// <" + REPOSITORY_MARKER + ">",
                indent + "mavenLocal()",
                indent + "// </" + REPOSITORY_MARKER + ">"
            ));
            return result;
        }

        int[] dependencies = findBlockRange(lines, "dependencies", buildscript);
        int insertAt = dependencies == null ? lines.size() : dependencies[0];
        result.addAll(insertAt, List.of(
            "repositories {",
            "    // <" + REPOSITORY_MARKER + ">",
            "    mavenLocal()",
            "    // </" + REPOSITORY_MARKER + ">",
            "}",
            ""
        ));
        return result;
    }

    private List<String> addManagedDependencies(List<String> lines, List<String> dependencies) {
        int[] buildscript = findBlockRange(lines, "buildscript", null);
        int[] dependencyBlock = findBlockRange(lines, "dependencies", buildscript);
        if (dependencyBlock == null) {
            throw new IllegalStateException("Cannot locate top-level dependencies block in build.gradle");
        }
        String indent = detectIndent(lines, dependencyBlock[0]);
        List<String> block = new ArrayList<>();
        block.add(indent + "// <" + DEPENDENCY_MARKER + ">");
        for (String dependency : dependencies) {
            block.add(indent + dependency);
        }
        block.add(indent + "// </" + DEPENDENCY_MARKER + ">");
        List<String> result = new ArrayList<>(lines);
        result.addAll(dependencyBlock[1], block);
        return result;
    }

    private List<String> stripManagedBlock(List<String> lines, String marker) {
        List<String> result = new ArrayList<>();
        boolean inside = false;
        for (String line : lines) {
            if (line.contains("// <" + marker + ">")) {
                inside = true;
                continue;
            }
            if (line.contains("// </" + marker + ">")) {
                inside = false;
                continue;
            }
            if (!inside) {
                result.add(line);
            }
        }
        return result;
    }

    private int[] findBlockRange(List<String> lines, String blockName, int[] excludedRange) {
        int blockStart = -1;
        int braceDepth = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (excludedRange != null && i >= excludedRange[0] && i <= excludedRange[1]) {
                continue;
            }
            String trimmed = lines.get(i).trim();
            if (blockStart < 0 && trimmed.startsWith(blockName + " ") && trimmed.contains("{")) {
                blockStart = i;
                braceDepth = countChar(lines.get(i), '{') - countChar(lines.get(i), '}');
                if (braceDepth == 0) {
                    return new int[] {blockStart, i};
                }
                continue;
            }
            if (blockStart >= 0) {
                braceDepth += countChar(lines.get(i), '{') - countChar(lines.get(i), '}');
                if (braceDepth == 0) {
                    return new int[] {blockStart, i};
                }
            }
        }
        return null;
    }

    private String detectIndent(List<String> lines, int blockStart) {
        for (int i = blockStart + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.trim().startsWith("}")) {
                continue;
            }
            int leadingSpaces = line.indexOf(line.trim());
            if (leadingSpaces > 0) {
                return line.substring(0, leadingSpaces);
            }
        }
        return "    ";
    }

    private int countChar(String line, char token) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == token) {
                count++;
            }
        }
        return count;
    }
}
