package ch.interlis.generator.model;

import java.util.Locale;

/**
 * Framework-agnostischer Geometrietyp fuer Core-IR und Targets.
 */
public enum GeometryKind {
    POINT,
    MULTIPOINT,
    LINESTRING,
    MULTILINESTRING,
    POLYGON,
    MULTIPOLYGON,
    GEOMETRY;

    public boolean isMulti() {
        return switch (this) {
            case MULTIPOINT, MULTILINESTRING, MULTIPOLYGON -> true;
            default -> false;
        };
    }

    public boolean accepts(GeometryKind actual) {
        if (this == GEOMETRY || actual == null || actual == GEOMETRY) {
            return true;
        }
        return this == actual || (isMulti() && singleKind() == actual);
    }

    public GeometryKind singleKind() {
        return switch (this) {
            case MULTIPOINT -> POINT;
            case MULTILINESTRING -> LINESTRING;
            case MULTIPOLYGON -> POLYGON;
            default -> this;
        };
    }

    public static GeometryKind from(String rawKind) {
        if (rawKind == null || rawKind.isBlank()) {
            return null;
        }
        String normalized = rawKind.trim().toUpperCase(Locale.ROOT)
            .replace(" ", "")
            .replace("_", "");
        if (normalized.contains("MULTIPOINT")) {
            return MULTIPOINT;
        }
        if (normalized.contains("MULTILINE") || normalized.contains("MULTIPOLYLINE")) {
            return MULTILINESTRING;
        }
        if (normalized.contains("MULTIPOLYGON")
            || normalized.contains("MULTISURFACE")
            || normalized.contains("MULTIAREA")) {
            return MULTIPOLYGON;
        }
        if (normalized.contains("POINT") || normalized.contains("COORD")) {
            return POINT;
        }
        if (normalized.contains("LINE") || normalized.contains("POLYLINE")) {
            return LINESTRING;
        }
        if (normalized.contains("POLYGON") || normalized.contains("SURFACE") || normalized.contains("AREA")) {
            return POLYGON;
        }
        if (normalized.contains("GEOMETRY")) {
            return GEOMETRY;
        }
        return GEOMETRY;
    }
}
