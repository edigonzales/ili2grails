package ch.interlis.generator.model;

import ch.interlis.generator.model.json.ModelMetadataJsonWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministischer SHA-256-Fingerprint der kanonischen Core-IR.
 *
 * <p>Basis ist die kanonische Metadata-JSON (UTF-8, deterministisch
 * sortiert). Timestamps, absolute Pfade und volatile Werte beeinflussen den
 * Fingerprint nicht (Spezifikation §39.4).</p>
 */
public final class ModelMetadataFingerprint {

    private ModelMetadataFingerprint() {
    }

    public static String of(ModelMetadata metadata) throws IOException {
        String canonicalJson = new ModelMetadataJsonWriter().toJson(metadata);
        return sha256(canonicalJson);
    }

    public static String sha256(String content) {
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
