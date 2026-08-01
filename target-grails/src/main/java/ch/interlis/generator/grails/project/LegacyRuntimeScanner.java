package ch.interlis.generator.grails.project;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Scans a Grails project for pre-P1 runtime copies.
 *
 * <p>Files are matched against the known generator states stored under
 * {@code grails/migration/legacy-runtime-v1}. Only files that match a known
 * state exactly are candidates for automatic deletion; modified files always
 * require manual intervention.</p>
 */
public final class LegacyRuntimeScanner {

    static final String LEGACY_RESOURCE_ROOT = "grails/migration/legacy-runtime-v1/";

    private static final List<String> RUNTIME_PACKAGE_DIRS = List.of(
        "src/main/groovy/ch/interlis/generator/grails/runtime",
        "grails-app/services/ch/interlis/generator/grails/runtime",
        "grails-app/controllers/ch/interlis/generator/grails/runtime",
        "grails-app/taglib/ch/interlis/generator/grails/runtime"
    );

    public LegacyRuntimeScanResult scan(Path projectDir) throws IOException {
        Objects.requireNonNull(projectDir, "projectDir");

        List<String> legacyResources = legacyResources();
        List<LegacyFileMatch> knownUnmodified = new ArrayList<>();
        List<LegacyFileMatch> modified = new ArrayList<>();

        for (String resourcePath : legacyResources) {
            String relativePath = resourcePath.substring(LEGACY_RESOURCE_ROOT.length());
            Path file = projectDir.resolve(relativePath);
            if (!Files.exists(file)) {
                continue;
            }
            String actualSha256 = sha256(file);
            Set<String> knownSha256 = Set.of(sha256OfResource(resourcePath));
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
                        boolean known = legacyResources.stream()
                            .map(resource -> resource.substring(LEGACY_RESOURCE_ROOT.length()))
                            .anyMatch(relative::equals);
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

    static List<String> legacyResources() throws IOException {
        String listingResource = LEGACY_RESOURCE_ROOT + "INDEX";
        try (InputStream listing = LegacyRuntimeScanner.class.getClassLoader()
            .getResourceAsStream(listingResource)) {
            if (listing == null) {
                throw new IOException("Missing legacy runtime index resource: " + listingResource);
            }
            String content = new String(listing.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return content.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(line -> LEGACY_RESOURCE_ROOT + line)
                .toList();
        }
    }

    private static String sha256OfResource(String resourcePath) throws IOException {
        try (InputStream inputStream = LegacyRuntimeScanner.class.getClassLoader()
            .getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Missing legacy runtime resource: " + resourcePath);
            }
            return sha256(inputStream.readAllBytes());
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
