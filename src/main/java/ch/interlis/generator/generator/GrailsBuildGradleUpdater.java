package ch.interlis.generator.generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class GrailsBuildGradleUpdater {

    private static final String JTS_DEPENDENCY = "implementation \"org.locationtech.jts:jts-core:1.19.0\"";

    void ensureJtsDependency(Path buildGradlePath) throws IOException {
        if (!Files.exists(buildGradlePath)) {
            return;
        }
        List<String> lines = Files.readAllLines(buildGradlePath, StandardCharsets.UTF_8);
        if (containsJtsDependency(lines)) {
            return;
        }
        List<String> updated = insertDependency(lines);
        Files.write(buildGradlePath, updated, StandardCharsets.UTF_8);
    }

    private boolean containsJtsDependency(List<String> lines) {
        return lines.stream().anyMatch(line -> line.contains("org.locationtech.jts:jts-core"));
    }

    private List<String> insertDependency(List<String> lines) {
        int dependenciesBlockStart = -1;
        int dependenciesBlockEnd = -1;
        int braceDepth = 0;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (dependenciesBlockStart < 0 && trimmed.startsWith("dependencies")) {
                dependenciesBlockStart = i;
                braceDepth += countChar(lines.get(i), '{');
                braceDepth -= countChar(lines.get(i), '}');
                if (braceDepth == 0) {
                    dependenciesBlockStart = -1;
                }
                continue;
            }
            if (dependenciesBlockStart >= 0) {
                braceDepth += countChar(lines.get(i), '{');
                braceDepth -= countChar(lines.get(i), '}');
                if (braceDepth == 0) {
                    dependenciesBlockEnd = i;
                    break;
                }
            }
        }
        if (dependenciesBlockStart < 0 || dependenciesBlockEnd < 0) {
            return lines;
        }
        String indent = detectIndent(lines, dependenciesBlockStart);
        List<String> updated = new java.util.ArrayList<>(lines);
        updated.add(dependenciesBlockEnd, indent + JTS_DEPENDENCY);
        return updated;
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
