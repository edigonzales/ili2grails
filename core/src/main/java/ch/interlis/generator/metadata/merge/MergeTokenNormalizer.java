package ch.interlis.generator.metadata.merge;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Normalisiert Namen für deterministisches Matching zwischen physischen
 * (ili2db) und semantischen (ili2c) Elementen.
 *
 * <p>Regeln:</p>
 * <ol>
 *   <li>{@code null}/blank → leere Menge.</li>
 *   <li>{@code trim()}.</li>
 *   <li>Lowercase mit {@link Locale#ROOT}.</li>
 *   <li>Bindestrich zu Unterstrich.</li>
 *   <li>Vollständigen Wert aufnehmen ({@link MatchReason#NORMALIZED_FULL_TOKEN}).</li>
 *   <li>Bei qualifiziertem Namen letztes Punktsegment aufnehmen
 *       ({@link MatchReason#NORMALIZED_FULL_TOKEN}).</li>
 *   <li>{@code _id}-Suffix entfernen mit Herkunft
 *       {@link MatchReason#NORMALIZED_ID_SUFFIX}.</li>
 *   <li>CamelCase-{@code Id} nur nach einer klaren Wortgrenze behandeln
 *       (Original endet auf {@code Id} mit vorangehendem Kleinbuchstaben).</li>
 *   <li>Ein normales Wort, das zufällig auf {@code id} endet (z.&nbsp;B.
 *       {@code grid}, {@code invalid}), wird nicht abgeschnitten.</li>
 *   <li>Letztes Unterstrichsegment nur als schwachen Token aufnehmen
 *       ({@link MatchReason#NORMALIZED_ID_SUFFIX}).</li>
 *   <li>Leere Tokens verwerfen.</li>
 *   <li>Stabile Reihenfolge und keine Duplikate.</li>
 * </ol>
 */
public final class MergeTokenNormalizer {

    /**
     * Liefert die normalisierten Tokens eines Werts in stabiler Reihenfolge.
     */
    public Set<NormalizedToken> tokens(String value) {
        Set<NormalizedToken> result = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        String trimmed = value.trim();
        String normalized = normalizeExact(trimmed);

        result.add(new NormalizedToken(normalized, MatchReason.NORMALIZED_FULL_TOKEN));

        int lastDot = normalized.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < normalized.length() - 1) {
            result.add(new NormalizedToken(
                normalized.substring(lastDot + 1), MatchReason.NORMALIZED_FULL_TOKEN));
        }

        addIdSuffixTokens(trimmed, normalized, result);

        int lastUnderscore = normalized.lastIndexOf('_');
        if (lastUnderscore >= 0 && lastUnderscore < normalized.length() - 1) {
            String segment = normalized.substring(lastUnderscore + 1);
            if (!segment.isEmpty()) {
                result.add(new NormalizedToken(
                    segment, MatchReason.NORMALIZED_ID_SUFFIX));
            }
        }
        return result;
    }

    private void addIdSuffixTokens(String original, String normalized, Set<NormalizedToken> result) {
        if (normalized.endsWith("_id") && normalized.length() > 3) {
            result.add(new NormalizedToken(
                normalized.substring(0, normalized.length() - 3),
                MatchReason.NORMALIZED_ID_SUFFIX));
            return;
        }
        if (isCamelCaseIdSuffix(original) && original.length() > 2) {
            result.add(new NormalizedToken(
                normalizeExact(original.substring(0, original.length() - 2)),
                MatchReason.NORMALIZED_ID_SUFFIX));
        }
    }

    private boolean isCamelCaseIdSuffix(String original) {
        return original.endsWith("Id")
            && Character.isLowerCase(original.charAt(original.length() - 3));
    }

    /**
     * Exakte Normalisierung ohne Token-Splitting: trim, Lowercase mit
     * {@link Locale#ROOT}, Bindestrich zu Unterstrich.
     */
    public String normalizeExact(String value) {
        if (value == null) {
            return null;
        }
        return value.trim()
            .replace('-', '_')
            .toLowerCase(Locale.ROOT);
    }

    /**
     * Ein normalisiertes Token mit seiner Herkunft.
     */
    public record NormalizedToken(
        String value,
        MatchReason reason
    ) {

        public NormalizedToken {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
