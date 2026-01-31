package ch.interlis.generator.generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class GrailsApplicationYamlUpdater {

    void ensureDevelopmentDataSourceUrl(Path applicationYamlPath, String jdbcUrl) throws IOException {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return;
        }
        if (!Files.exists(applicationYamlPath)) {
            return;
        }
        List<String> lines = Files.readAllLines(applicationYamlPath, StandardCharsets.UTF_8);
        List<String> updated = updateDevelopmentUrl(lines, jdbcUrl);
        if (updated != lines) {
            Files.write(applicationYamlPath, updated, StandardCharsets.UTF_8);
        }
    }

    private List<String> updateDevelopmentUrl(List<String> lines, String jdbcUrl) {
        int envIndex = findBlockStart(lines, "environments:");
        if (envIndex < 0) {
            return lines;
        }
        int devIndex = findChildBlockStart(lines, envIndex, "development:");
        if (devIndex < 0) {
            return lines;
        }
        int dataSourceIndex = findChildBlockStart(lines, devIndex, "dataSource:");
        if (dataSourceIndex < 0) {
            return lines;
        }
        int urlIndex = findChildKeyLine(lines, dataSourceIndex, "url:");
        if (urlIndex < 0) {
            return insertUrlLine(lines, dataSourceIndex, jdbcUrl);
        }
        String indent = leadingWhitespace(lines.get(urlIndex));
        String updatedLine = indent + "url: " + quoteYaml(jdbcUrl);
        if (lines.get(urlIndex).equals(updatedLine)) {
            return lines;
        }
        List<String> updated = new ArrayList<>(lines);
        updated.set(urlIndex, updatedLine);
        return updated;
    }

    private List<String> insertUrlLine(List<String> lines, int dataSourceIndex, String jdbcUrl) {
        int insertionIndex = dataSourceIndex + 1;
        String indent = leadingWhitespace(lines.get(dataSourceIndex)) + "  ";
        List<String> updated = new ArrayList<>(lines);
        updated.add(insertionIndex, indent + "url: " + quoteYaml(jdbcUrl));
        return updated;
    }

    private int findBlockStart(List<String> lines, String key) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private int findChildBlockStart(List<String> lines, int parentIndex, String key) {
        int parentIndent = indentationLevel(lines.get(parentIndex));
        for (int i = parentIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = indentationLevel(line);
            if (indent <= parentIndent) {
                return -1;
            }
            if (line.trim().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private int findChildKeyLine(List<String> lines, int parentIndex, String key) {
        int parentIndent = indentationLevel(lines.get(parentIndex));
        for (int i = parentIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = indentationLevel(line);
            if (indent <= parentIndent) {
                return -1;
            }
            if (line.trim().startsWith(key)) {
                return i;
            }
        }
        return -1;
    }

    private int indentationLevel(String line) {
        return line.length() - line.stripLeading().length();
    }

    private String leadingWhitespace(String line) {
        return line.substring(0, indentationLevel(line));
    }

    private String quoteYaml(String value) {
        String escaped = value.replace("'", "''");
        return "'" + escaped + "'";
    }
}
