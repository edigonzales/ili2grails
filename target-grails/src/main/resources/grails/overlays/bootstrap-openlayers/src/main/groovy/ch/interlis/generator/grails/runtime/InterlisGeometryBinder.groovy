package ch.interlis.generator.grails.runtime

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.operation.valid.IsValidOp

final class InterlisGeometryBinder {

    private static final int DEFAULT_MAX_WKT_LENGTH = 1_048_576
    private static final int DEFAULT_MAX_VERTEX_COUNT = 50_000

    private InterlisGeometryBinder() {
    }

    static void bindGeometryFromParams(Object instance,
                                       def params,
                                       Map<String, Map<String, Object>> geometryMeta,
                                       def grailsApplication,
                                       def controller) {
        if (instance == null || geometryMeta == null || geometryMeta.isEmpty()) {
            return
        }
        WKTReader wktReader = new WKTReader()
        geometryMeta.keySet().collect { it.toString() }.sort().each { String field ->
            String paramName = field + "Wkt"
            if (!params.containsKey(paramName)) {
                return
            }
            String wktValue = params.get(paramName)
            if (wktValue == null || wktValue.trim().isEmpty()) {
                instance."${field}" = null
                return
            }
            int maxWktLength = maxWktLength(grailsApplication)
            if (wktValue.length() > maxWktLength) {
                rejectValue(
                    instance,
                    field,
                    "ili2grails.geometry.error.wktSize",
                    [field, maxWktLength] as Object[],
                    "Geometrie fuer ${field} ist zu gross. Maximal ${maxWktLength} WKT-Zeichen sind erlaubt."
                )
                return
            }
            try {
                Geometry geometry = wktReader.read(wktValue)
                Integer srid = geometrySrid(field, geometryMeta, grailsApplication)
                if (srid != null) {
                    geometry.setSRID(srid)
                }
                String expectedKind = geometryKind(field, geometryMeta)
                Geometry normalized = normalizeGeometry(geometry, expectedKind)
                if (normalized == null) {
                    rejectValue(
                        instance,
                        field,
                        "ili2grails.geometry.error.type",
                        [field, expectedKind, geometry.getGeometryType()] as Object[],
                        "Ungueltiger Geometrietyp fuer ${field}. Erwartet ${expectedKind}, erhalten ${geometry.getGeometryType()}."
                    )
                    return
                }
                if (srid != null) {
                    normalized.setSRID(srid)
                }
                if (!allowEmpty(field, geometryMeta) && normalized.isEmpty()) {
                    rejectValue(
                        instance,
                        field,
                        "ili2grails.geometry.error.empty",
                        [field] as Object[],
                        "Leere Geometrien sind fuer ${field} nicht erlaubt."
                    )
                    return
                }
                int maxVertices = maxVertexCount(grailsApplication)
                int vertices = vertexCount(normalized)
                if (vertices > maxVertices) {
                    rejectValue(
                        instance,
                        field,
                        "ili2grails.geometry.error.vertexCount",
                        [field, maxVertices, vertices] as Object[],
                        "Geometrie fuer ${field} hat zu viele Stuetzpunkte. Maximal ${maxVertices} sind erlaubt."
                    )
                    return
                }
                IsValidOp validator = new IsValidOp(normalized)
                if (!validator.isValid()) {
                    String reason = validator.getValidationError()?.message ?: "unbekannter Validierungsfehler"
                    rejectValue(
                        instance,
                        field,
                        "ili2grails.geometry.error.topology",
                        [field, reason] as Object[],
                        "Ungueltige Geometrie fuer ${field}: ${reason}."
                    )
                    return
                }
                instance."${field}" = normalized
            } catch (Exception ignored) {
                rejectValue(
                    instance,
                    field,
                    "ili2grails.geometry.error.invalid",
                    [field] as Object[],
                    "Ungueltige Geometrie fuer ${field}."
                )
            }
        }
    }

    private static void rejectValue(Object instance,
                                    String field,
                                    String code,
                                    Object[] args,
                                    String defaultMessage) {
        instance.errors.rejectValue(field, code, args, defaultMessage)
    }

    private static Integer geometrySrid(String field,
                                        Map<String, Map<String, Object>> geometryMeta,
                                        def grailsApplication) {
        Object configuredSrid = geometryMeta[field]?.get("srid")
        if (configuredSrid instanceof Number) {
            return ((Number) configuredSrid).intValue()
        }
        return grailsApplication?.config?.getProperty("interlis.geometry.defaultSrid", Integer, 2056)
    }

    private static String geometryKind(String field, Map<String, Map<String, Object>> geometryMeta) {
        Object configuredKind = geometryMeta[field]?.get("kind")
        return configuredKind != null ? configuredKind.toString() : "GEOMETRY"
    }

    private static boolean allowEmpty(String field, Map<String, Map<String, Object>> geometryMeta) {
        return geometryMeta[field]?.get("allowEmpty") == true
    }

    private static int maxWktLength(def grailsApplication) {
        return grailsApplication?.config?.getProperty(
            "interlis.geometry.maxWktLength",
            Integer,
            DEFAULT_MAX_WKT_LENGTH
        ) ?: DEFAULT_MAX_WKT_LENGTH
    }

    private static int maxVertexCount(def grailsApplication) {
        return grailsApplication?.config?.getProperty(
            "interlis.geometry.maxVertices",
            Integer,
            DEFAULT_MAX_VERTEX_COUNT
        ) ?: DEFAULT_MAX_VERTEX_COUNT
    }

    private static int vertexCount(Geometry geometry) {
        if (geometry == null) {
            return 0
        }
        return geometry.coordinates?.length ?: 0
    }

    private static Geometry normalizeGeometry(Geometry geometry, String expectedKind) {
        String expected = normalizeKind(expectedKind)
        if (expected == "GEOMETRY") {
            return geometry
        }
        String actual = normalizeKind(geometry.getGeometryType())
        if (expected == actual) {
            return geometry
        }
        if (!isExpectedMulti(expected)) {
            return null
        }
        return convertSingleToMulti(geometry, expected, actual)
    }

    private static Geometry convertSingleToMulti(Geometry geometry, String expected, String actual) {
        if (expected == "MULTIPOINT" && actual == "POINT" && geometry instanceof Point) {
            return geometry.factory.createMultiPoint([geometry] as Point[])
        }
        if (expected == "MULTILINESTRING" && actual == "LINESTRING" && geometry instanceof LineString) {
            return geometry.factory.createMultiLineString([geometry] as LineString[])
        }
        if (expected == "MULTIPOLYGON" && actual == "POLYGON" && geometry instanceof Polygon) {
            return geometry.factory.createMultiPolygon([geometry] as Polygon[])
        }
        return null
    }

    private static boolean isExpectedMulti(String expected) {
        return expected == "MULTIPOINT" || expected == "MULTILINESTRING" || expected == "MULTIPOLYGON"
    }

    private static String normalizeKind(String rawKind) {
        if (rawKind == null || rawKind.isBlank()) {
            return "GEOMETRY"
        }
        String normalized = rawKind.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "")
        if (normalized.contains("MULTIPOINT")) {
            return "MULTIPOINT"
        }
        if (normalized.contains("MULTILINE") || normalized.contains("MULTIPOLYLINE")) {
            return "MULTILINESTRING"
        }
        if (normalized.contains("MULTIPOLYGON") || normalized.contains("MULTISURFACE") || normalized.contains("MULTIAREA")) {
            return "MULTIPOLYGON"
        }
        if (normalized.contains("POINT") || normalized.contains("COORD")) {
            return "POINT"
        }
        if (normalized.contains("LINE") || normalized.contains("POLYLINE")) {
            return "LINESTRING"
        }
        if (normalized.contains("POLYGON") || normalized.contains("SURFACE") || normalized.contains("AREA")) {
            return "POLYGON"
        }
        return "GEOMETRY"
    }
}
