package ch.interlis.generator.model;

/**
 * Immutable Kardinalitäts-Grenzen einer Beziehung.
 *
 * <p>{@code -1} bleibt Sentinel für unbounded Max.</p>
 */
public record Cardinality(
    int minSource,
    int maxSource,
    int minTarget,
    int maxTarget
) {

    public Cardinality {
        validateBound(minSource, "minSource");
        validateBound(maxSource, "maxSource");
        validateBound(minTarget, "minTarget");
        validateBound(maxTarget, "maxTarget");
        validateRange(minSource, maxSource, "source");
        validateRange(minTarget, maxTarget, "target");
    }

    public static Cardinality of(int minSource, int maxSource, int minTarget, int maxTarget) {
        return new Cardinality(minSource, maxSource, minTarget, maxTarget);
    }

    public boolean sourceUnbounded() {
        return maxSource == -1;
    }

    public boolean targetUnbounded() {
        return maxTarget == -1;
    }

    private static void validateBound(int value, String fieldName) {
        if (value < -1) {
            throw new IllegalArgumentException(fieldName + " must not be smaller than -1");
        }
    }

    private static void validateRange(int min, int max, String side) {
        if (min >= 0 && max >= 0 && max < min) {
            throw new IllegalArgumentException(
                "max" + capitalize(side) + " must not be smaller than min" + capitalize(side));
        }
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
