package ch.interlis.generator.grails.project;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Scans a Grails project for pre-P1 runtime copies.
 *
 * <p>Files are matched against SHA-256 hashes of known generator states. Only
 * exact matches are candidates for automatic deletion; modified files always
 * require manual intervention.</p>
 */
public final class LegacyRuntimeScanner {

    static final String LEGACY_HASH_RESOURCE = "grails/migration/legacy-runtime-v1.sha256";

    private static final List<String> RUNTIME_PACKAGE_DIRS = List.of(
        "src/main/groovy/ch/interlis/generator/grails/runtime",
        "grails-app/services/ch/interlis/generator/grails/runtime",
        "grails-app/controllers/ch/interlis/generator/grails/runtime",
        "grails-app/taglib/ch/interlis/generator/grails/runtime"
    );

    public LegacyRuntimeScanResult scan(Path projectDir) throws IOException {
        Objects.requireNonNull(projectDir, "projectDir");

        Map<String, Set<String>> legacyHashes = legacyHashes();
        List<LegacyFileMatch> knownUnmodified = new ArrayList<>();
        List<LegacyFileMatch> modified = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : legacyHashes.entrySet()) {
            String relativePath = entry.getKey();
            Path file = projectDir.resolve(relativePath);
            if (!Files.exists(file)) {
                continue;
            }
            String actualSha256 = sha256(file);
            Set<String> knownSha256 = entry.getValue();
            LegacyFileMatch match = new LegacyFileMatch(
                Path.of(relativePath), actualSha256, knownSha256);
            if (knownSha256.contains(actualSha256)) {
                knownUnmodified.add(match);
            } else {
                modified.add(match);
            }
        }

        List<Path> unknownRuntimeFiles = new ArrayList<>();
        for (String runtimeDir : RUNTIME_PACKAGE_DIRS) {
            Path dir = projectDir.resolve(runtimeDir);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dir)) {
                files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".groovy"))
                    .forEach(path -> {
                        String relative = projectDir.relativize(path).toString()
                            .replace(java.io.File.separatorChar, '/');
                        boolean known = legacyHashes.containsKey(relative);
                        if (!known) {
                            unknownRuntimeFiles.add(Path.of(relative));
                        }
                    });
            }
        }
        unknownRuntimeFiles.sort(Comparator.comparing(Path::toString));

        knownUnmodified.sort(Comparator.comparing(match -> match.relativePath().toString()));
        modified.sort(Comparator.comparing(match -> match.relativePath().toString()));
        return new LegacyRuntimeScanResult(
            List.copyOf(knownUnmodified),
            List.copyOf(modified),
            List.copyOf(unknownRuntimeFiles)
        );
    }

    static Map<String, Set<String>> legacyHashes() throws IOException {
        try (InputStream manifest = LegacyRuntimeScanner.class.getClassLoader()
            .getResourceAsStream(LEGACY_HASH_RESOURCE)) {
            if (manifest == null) {
                throw new IOException("Missing legacy runtime hash resource: " + LEGACY_HASH_RESOURCE);
            }
            return parseLegacyHashes(new String(manifest.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    static Map<String, Set<String>> parseLegacyHashes(String content) throws IOException {
        Objects.requireNonNull(content, "content");
        Map<String, Set<String>> hashesByPath = new LinkedHashMap<>();
        int lineNumber = 0;
        for (String line : content.split("\\R", -1)) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^([0-9a-f]{64})  (\\S(?:.*\\S)?)$")
                .matcher(line);
            if (!matcher.matches()) {
                throw new IOException("Invalid legacy runtime hash entry at line " + lineNumber);
            }
            String hash = matcher.group(1);
            String relativePath = matcher.group(2);
            validateRelativePath(relativePath, lineNumber);
            hashesByPath.computeIfAbsent(relativePath, ignored -> new LinkedHashSet<>()).add(hash);
        }
        if (hashesByPath.isEmpty()) {
            throw new IOException("Legacy runtime hash resource is empty");
        }
        Map<String, Set<String>> result = new LinkedHashMap<>();
        hashesByPath.forEach((path, hashes) -> result.put(path, Set.copyOf(hashes)));
        return Map.copyOf(result);
    }

    private static void validateRelativePath(String relativePath, int lineNumber) throws IOException {
        if (relativePath.indexOf('\\') >= 0) {
            throw new IOException("Invalid legacy runtime path at line " + lineNumber);
        }
        final Path path;
        try {
            path = Path.of(relativePath);
        } catch (RuntimeException invalidPath) {
            throw new IOException("Invalid legacy runtime path at line " + lineNumber, invalidPath);
        }
        if (path.isAbsolute() || relativePath.startsWith("/")
            || relativePath.equals("..") || relativePath.startsWith("../")
            || relativePath.contains("/../") || relativePath.contains("/./")
            || !path.normalize().toString().replace(java.io.File.separatorChar, '/')
                .equals(relativePath)
            || relativePath.equals(".")) {
            throw new IOException("Invalid legacy runtime path at line " + lineNumber);
        }
    }

    private static String sha256(Path file) throws IOException {
        return sha256(Files.readAllBytes(file));
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 not available", impossible);
        }
    }
}
