package ch.interlis.generator.generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class GrailsBuildGradleUpdater {

    private static final String JTS_DEPENDENCY = "implementation \"org.locationtech.jts:jts-core:1.19.0\"";
    private static final String SQLITE_JDBC_DEPENDENCY =
        "implementation \"org.xerial:sqlite-jdbc:3.43.0.0\"";
    private static final String SQLITE_DIALECT_DEPENDENCY =
        "implementation \"com.zsoltfabok:sqlite-dialect:1.0\"";

    void ensureJtsDependency(Path buildGradlePath) throws IOException {
        if (!Files.exists(buildGradlePath)) {
            return;
        }
        List<String> lines = Files.readAllLines(buildGradlePath, StandardCharsets.UTF_8);
        List<String> updated = ensureDependencies(lines);
        if (!updated.equals(lines)) {
            Files.write(buildGradlePath, updated, StandardCharsets.UTF_8);
        }
    }

    private List<String> ensureDependencies(List<String> lines) {
        List<String> updated = new java.util.ArrayList<>(lines);
        updated = insertDependencyIfMissing(updated, "org.locationtech.jts:jts-core", JTS_DEPENDENCY);
        updated = insertDependencyIfMissing(updated, "org.xerial:sqlite-jdbc", SQLITE_JDBC_DEPENDENCY);
        updated = insertDependencyIfMissing(updated, "com.zsoltfabok:sqlite-dialect", SQLITE_DIALECT_DEPENDENCY);
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
