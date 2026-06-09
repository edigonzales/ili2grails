package ch.interlis.generator.grails;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class GrailsBuildGradleUpdater {

    private static final String JTS_DEPENDENCY = "implementation \"org.locationtech.jts:jts-core:1.19.0\"";
    private static final String POSTGRES_JDBC_DEPENDENCY =
        "implementation \"org.postgresql:postgresql:42.7.7\"";
    private static final String HIBERNATE_SPATIAL_DEPENDENCY =
        "implementation \"org.hibernate:hibernate-spatial:5.6.15.Final\"";
    private static final String BOOTSTRAP_WEBJAR_DEPENDENCY =
        "implementation \"org.webjars:bootstrap:5.3.3\"";
    private static final String OPENLAYERS_WEBJAR_DEPENDENCY =
        "implementation \"org.webjars.npm:ol:9.2.4\"";
    private static final String PROJ4_WEBJAR_DEPENDENCY =
        "implementation \"org.webjars.npm:proj4:2.11.0\"";

    void ensureJtsDependency(Path buildGradlePath) throws IOException {
        ensureDependencies(buildGradlePath, false);
    }

    void ensureDependencies(Path buildGradlePath, boolean geometryEnabled) throws IOException {
        if (!Files.exists(buildGradlePath)) {
            return;
        }
        List<String> lines = Files.readAllLines(buildGradlePath, StandardCharsets.UTF_8);
        List<String> updated = ensureDependencies(lines, geometryEnabled);
        if (!updated.equals(lines)) {
            Files.write(buildGradlePath, updated, StandardCharsets.UTF_8);
        }
    }

    private List<String> ensureDependencies(List<String> lines, boolean geometryEnabled) {
        List<String> updated = removeLegacySpatialDependency(lines);
        updated = insertDependencyIfMissing(updated, "org.locationtech.jts:jts-core", JTS_DEPENDENCY);
        updated = insertDependencyIfMissing(updated, "org.postgresql:postgresql", POSTGRES_JDBC_DEPENDENCY);
        updated = insertDependencyIfMissing(updated, "org.webjars:bootstrap", BOOTSTRAP_WEBJAR_DEPENDENCY);
        updated = insertDependencyIfMissing(updated, "org.webjars.npm:ol", OPENLAYERS_WEBJAR_DEPENDENCY);
        updated = insertDependencyIfMissing(updated, "org.webjars.npm:proj4", PROJ4_WEBJAR_DEPENDENCY);
        if (geometryEnabled) {
            updated = insertDependencyIfMissing(updated, "org.hibernate:hibernate-spatial", HIBERNATE_SPATIAL_DEPENDENCY);
        }
        return updated;
    }

    private List<String> removeLegacySpatialDependency(List<String> lines) {
        List<String> updated = new java.util.ArrayList<>();
        for (String line : lines) {
            if (line.contains("org.hibernate.orm:hibernate-spatial")) {
                continue;
            }
            updated.add(line);
        }
        return updated;
    }

    private List<String> insertDependencyIfMissing(List<String> lines, String marker, String dependency) {
        if (lines.stream().anyMatch(line -> line.contains(marker))) {
            return lines;
        }
        int[] buildscriptRange = findBlockRange(lines, "buildscript", null);
        int[] dependenciesRange = findBlockRange(lines, "dependencies", buildscriptRange);
        if (dependenciesRange == null) {
            return lines;
        }
        String indent = detectIndent(lines, dependenciesRange[0]);
        List<String> updated = new java.util.ArrayList<>(lines);
        updated.add(dependenciesRange[1], indent + dependency);
        return updated;
    }

    private int[] findBlockRange(List<String> lines, String blockName, int[] excludeRange) {
        int blockStart = -1;
        int braceDepth = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (excludeRange != null && i >= excludeRange[0] && i <= excludeRange[1]) {
                continue;
            }
            String trimmed = lines.get(i).trim();
            if (blockStart < 0 && trimmed.startsWith(blockName)) {
                blockStart = i;
                braceDepth += countChar(lines.get(i), '{');
                braceDepth -= countChar(lines.get(i), '}');
                if (braceDepth == 0) {
                    blockStart = -1;
                }
                continue;
            }
            if (blockStart >= 0) {
                braceDepth += countChar(lines.get(i), '{');
                braceDepth -= countChar(lines.get(i), '}');
                if (braceDepth == 0) {
                    return new int[] { blockStart, i };
                }
            }
        }
        return null;
    }

    private String detectIndent(List<String> lines, int dependenciesBlockStart) {
        for (int i = dependenciesBlockStart + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            int leadingSpaces = line.indexOf(line.trim());
            if (leadingSpaces > 0) {
                return line.substring(0, leadingSpaces);
            }
            if (line.trim().startsWith("}")) {
                break;
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
