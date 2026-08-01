package ch.interlis.generator.model;

import ch.interlis.generator.model.builder.AttributeMetadataBuilder;

import java.util.Locale;
import java.util.Objects;

/**
 * Löst Java- und Core-Typ eines Attributs vor dem Freeze auf.
 *
 * <p>Die Inferenz-Logik lebt ausschliesslich hier; die immutable
 * {@link AttributeMetadata} hat reine Getter ohne Lazy-Mutation.</p>
 */
public final class AttributeTypeResolver {

    public ResolvedAttributeTypes resolve(AttributeMetadataBuilder attribute) {
        Objects.requireNonNull(attribute, "attribute");
        return new ResolvedAttributeTypes(
            resolveCoreType(attribute),
            resolveJavaType(attribute)
        );
    }

    public static String inferJavaType(AttributeMetadataBuilder attribute) {
        String existing = attribute.javaType();
        if (existing != null) {
            return existing;
        }
        if (attribute.geometry()) {
            return "org.locationtech.jts.geom.Geometry";
        }
        if (attribute.enumType() != null) {
            return "String";
        }
        String iliType = attribute.iliType();
        if (iliType != null) {
            String upperIliType = iliType.toUpperCase(Locale.ROOT);
            return switch (upperIliType) {
                case "TEXT", "MTEXT" -> "String";
                case "BOOLEAN" -> "Boolean";
                case "DATE", "INTERLIS.XMLDATE" -> "java.time.LocalDate";
                case "DATETIME", "INTERLIS.XMLDATETIME" -> "java.time.LocalDateTime";
                case "TIME", "INTERLIS.XMLTIME" -> "java.time.LocalTime";
                default -> {
                    if (upperIliType.contains("COORD")
                        || upperIliType.contains("MULTICOORD")
                        || upperIliType.contains("POLYLINE")
                        || upperIliType.contains("LINE")
                        || upperIliType.contains("SURFACE")
                        || upperIliType.contains("AREA")) {
                        yield "org.locationtech.jts.geom.Geometry";
                    }
                    yield attribute.dbType() != null
                        ? inferJavaTypeFromDbType(attribute.dbType())
                        : "Object";
                }
            };
        }
        if (attribute.dbType() != null) {
            return inferJavaTypeFromDbType(attribute.dbType());
        }
        return "Object";
    }

    private static String inferJavaTypeFromDbType(String dbType) {
        String upperDbType = dbType.toUpperCase(Locale.ROOT);
        if (upperDbType.contains("VARCHAR") || upperDbType.contains("TEXT")
            || upperDbType.contains("CHAR")) {
            return "String";
        }
        if (upperDbType.contains("INT")) {
            return upperDbType.contains("BIGINT") ? "Long" : "Integer";
        }
        if (upperDbType.contains("DECIMAL") || upperDbType.contains("NUMERIC")) {
            return "java.math.BigDecimal";
        }
        if (upperDbType.contains("DOUBLE") || upperDbType.contains("FLOAT")) {
            return "Double";
        }
        if (upperDbType.contains("BOOL")) {
            return "Boolean";
        }
        if (upperDbType.contains("DATE")) {
            return upperDbType.contains("TIME")
                ? "java.time.LocalDateTime"
                : "java.time.LocalDate";
        }
        if (upperDbType.contains("GEOMETRY")) {
            return "org.locationtech.jts.geom.Geometry";
        }
        return "Object";
    }

    public static CoreType inferCoreType(AttributeMetadataBuilder attribute) {
        if (attribute.enumType() != null) {
            return CoreType.ENUM;
        }
        CoreType typeFromIli = inferCoreTypeFromIliType(attribute.iliType());
        if (typeFromIli != null) {
            return typeFromIli;
        }
        if (attribute.referencedClass() != null) {
            return CoreType.REFERENCE;
        }
        if (attribute.geometry()) {
            return inferGeometryCoreType(attribute.geometryKind());
        }
        CoreType typeFromDb = inferCoreTypeFromDbType(attribute.dbType());
        if (typeFromDb != null) {
            return typeFromDb;
        }
        CoreType typeFromJava = inferCoreTypeFromJavaType(attribute.javaType());
        if (typeFromJava != null) {
            return typeFromJava;
        }
        return CoreType.UNKNOWN;
    }

    private static CoreType resolveCoreType(AttributeMetadataBuilder attribute) {
        CoreType existing = attribute.coreType();
        return existing != null ? existing : inferCoreType(attribute);
    }

    private static String resolveJavaType(AttributeMetadataBuilder attribute) {
        return inferJavaType(attribute);
    }

    private static CoreType inferCoreTypeFromIliType(String iliType) {
        if (iliType == null || iliType.isBlank()) {
            return null;
        }
        String upperIliType = iliType.trim().toUpperCase(Locale.ROOT);
        return switch (upperIliType) {
            case "TEXT", "TEXTTYPE", "TEXTOIDTYPE" -> CoreType.TEXT;
            case "MTEXT" -> CoreType.MTEXT;
            case "NUMERIC", "NUMERICTYPE", "NUMERICALTYPE" -> CoreType.NUMERIC;
            case "BOOLEAN" -> CoreType.BOOLEAN;
            case "DATE", "XMLDATE", "INTERLIS.XMLDATE" -> CoreType.DATE;
            case "DATETIME", "XMLDATETIME", "INTERLIS.XMLDATETIME" -> CoreType.DATETIME;
            case "TIME", "XMLTIME", "INTERLIS.XMLTIME" -> CoreType.TIME;
            case "ENUM", "ENUMERATIONTYPE" -> CoreType.ENUM;
            case "COORD", "COORDTYPE", "MULTICOORDTYPE" -> CoreType.COORD;
            case "POLYLINE", "POLYLINETYPE", "LINETYPE", "MULTIPOLYLINETYPE" -> CoreType.POLYLINE;
            case "SURFACE", "SURFACETYPE", "AREATYPE", "MULTISURFACETYPE", "MULTIAREATYPE" -> CoreType.SURFACE;
            case "REFERENCE", "REFERENCETYPE" -> CoreType.REFERENCE;
            case "COMPOSITION", "COMPOSITIONTYPE" -> CoreType.COMPOSITION;
            case "OBJECT", "OBJECTTYPE" -> CoreType.OBJECT;
            default -> null;
        };
    }

    private static CoreType inferGeometryCoreType(GeometryKind geometryKind) {
        if (geometryKind == null) {
            return CoreType.UNKNOWN;
        }
        if (geometryKind == GeometryKind.POINT || geometryKind == GeometryKind.MULTIPOINT) {
            return CoreType.COORD;
        }
        if (geometryKind == GeometryKind.LINESTRING || geometryKind == GeometryKind.MULTILINESTRING) {
            return CoreType.POLYLINE;
        }
        if (geometryKind == GeometryKind.POLYGON || geometryKind == GeometryKind.MULTIPOLYGON) {
            return CoreType.SURFACE;
        }
        return CoreType.UNKNOWN;
    }

    private static CoreType inferCoreTypeFromDbType(String dbType) {
        if (dbType == null || dbType.isBlank()) {
            return null;
        }
        String upperDbType = dbType.toUpperCase(Locale.ROOT);
        if (upperDbType.contains("GEOMETRY")) {
            return CoreType.UNKNOWN;
        }
        if (upperDbType.contains("VARCHAR") || upperDbType.contains("TEXT")
            || upperDbType.contains("CHAR")) {
            return CoreType.TEXT;
        }
        if (upperDbType.contains("DECIMAL") || upperDbType.contains("NUMERIC")
            || upperDbType.contains("DOUBLE") || upperDbType.contains("FLOAT")
            || upperDbType.contains("REAL") || upperDbType.contains("INT")) {
            return CoreType.NUMERIC;
        }
        if (upperDbType.contains("BOOL") || upperDbType.contains("BIT")) {
            return CoreType.BOOLEAN;
        }
        if (upperDbType.contains("TIMESTAMP") || upperDbType.contains("DATETIME")) {
            return CoreType.DATETIME;
        }
        if (upperDbType.contains("TIME")) {
            return CoreType.TIME;
        }
        if (upperDbType.contains("DATE")) {
            return CoreType.DATE;
        }
        return null;
    }

    private static CoreType inferCoreTypeFromJavaType(String javaType) {
        if (javaType == null || javaType.isBlank()) {
            return null;
        }
        String simpleType = javaType;
        int lastDot = simpleType.lastIndexOf('.');
        if (lastDot >= 0) {
            simpleType = simpleType.substring(lastDot + 1);
        }
        return switch (simpleType) {
            case "String" -> CoreType.TEXT;
            case "Boolean" -> CoreType.BOOLEAN;
            case "LocalDate" -> CoreType.DATE;
            case "LocalDateTime" -> CoreType.DATETIME;
            case "LocalTime" -> CoreType.TIME;
            case "Integer", "Long", "BigDecimal", "Double", "Float" -> CoreType.NUMERIC;
            default -> null;
        };
    }
}
