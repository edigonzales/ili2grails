package ch.interlis.generator.reader.ili2db.assemble;

import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.GeometryKind;
import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ClassMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;
import ch.interlis.generator.reader.ili2db.Ili2dbDiagnostic;
import ch.interlis.generator.reader.ili2db.Ili2dbDiagnosticCode;
import ch.interlis.generator.reader.ili2db.Ili2dbEnumReader;
import ch.interlis.generator.reader.ili2db.Ili2dbReadContext;
import ch.interlis.generator.reader.ili2db.Ili2dbSeverity;
import ch.interlis.generator.reader.ili2db.catalog.AttributeMappingRow;
import ch.interlis.generator.reader.ili2db.catalog.ClassMappingRow;
import ch.interlis.generator.reader.ili2db.catalog.ColumnPropertyRow;
import ch.interlis.generator.reader.ili2db.catalog.EnumDomainRow;
import ch.interlis.generator.reader.ili2db.catalog.Ili2dbCatalogSnapshot;
import ch.interlis.generator.reader.ili2db.catalog.InheritanceRow;
import ch.interlis.generator.reader.ili2db.schema.ColumnSchema;
import ch.interlis.generator.reader.ili2db.schema.GeometryColumnSchema;
import ch.interlis.generator.reader.ili2db.schema.GeometrySchemaSnapshot;
import ch.interlis.generator.reader.ili2db.schema.JdbcSchemaSnapshot;
import ch.interlis.generator.reader.ili2db.schema.TableSchema;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Einziger Verantwortlicher für die IR-Builder-Erzeugung aus Catalog-,
 * Schema- und Geometry-Snapshots. Keine SQL-Zugriffe; die Enum-Werte kommen
 * fertig vom {@link Ili2dbEnumReader}.
 */
public final class Ili2dbMetadataAssembler {

    public static final String PRIMARY_KEY_COLUMN = "t_id";
    private static final String ENUM_DOMAIN_TAG = "ch.ehi.ili2db.enumDomain";
    private static final String ILI2DB_SENDER_SETTING = "ch.ehi.ili2db.sender";

    private final Ili2dbEnumReader enumReader;

    public Ili2dbMetadataAssembler() {
        this(new Ili2dbEnumReader());
    }

    public Ili2dbMetadataAssembler(Ili2dbEnumReader enumReader) {
        this.enumReader = Objects.requireNonNull(enumReader, "enumReader");
    }

    public ModelMetadataBuilder assemble(Ili2dbReadContext context,
                                         Ili2dbCatalogSnapshot catalog,
                                         JdbcSchemaSnapshot schema,
                                         GeometrySchemaSnapshot geometry,
                                         List<Ili2dbDiagnostic> diagnostics) {
        ModelMetadataBuilder builder = ModelMetadataBuilder.model(
            context.modelSelection().rootModelName());
        SqlSchemaLookup schemaLookup = new SqlSchemaLookup(schema);
        GeometryLookup geometryLookup = new GeometryLookup(geometry);

        if (context.schema() != null) {
            builder.schemaName(context.schema().value());
        }

        applySettings(builder, catalog);

        for (ClassMappingRow row : catalog.classes()) {
            assembleClass(builder, row, diagnostics);
        }

        Map<EnumColumnKey, EnumDomainRow> enumDomains = indexEnumDomains(catalog.enumDomains());
        for (AttributeMappingRow row : catalog.attributes()) {
            assembleAttribute(context, builder, row, schemaLookup, geometryLookup,
                enumDomains, diagnostics);
        }

        for (InheritanceRow row : catalog.inheritance()) {
            builder.findClassBuilder(row.thisClass())
                .ifPresent(classMetadata -> classMetadata.baseClass(row.baseClass()));
        }

        for (ColumnPropertyRow row : catalog.columnProperties()) {
            applyColumnProperty(context, builder, row);
        }

        ensurePrimaryKeyAttributes(builder, schemaLookup, geometryLookup, diagnostics);
        return builder;
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    private void applySettings(ModelMetadataBuilder builder, Ili2dbCatalogSnapshot catalog) {
        catalog.settings().forEach((tag, setting) -> {
            builder.setting(tag, setting);
            if (ILI2DB_SENDER_SETTING.equals(tag)) {
                builder.ili2dbVersion(setting);
            }
        });
    }

    // ------------------------------------------------------------------
    // Classes
    // ------------------------------------------------------------------

    private void assembleClass(ModelMetadataBuilder builder,
                               ClassMappingRow row,
                               List<Ili2dbDiagnostic> diagnostics) {
        Optional<ClassMetadata.ClassKind> kind = mapClassKind(row.tableKind());
        if (kind.isEmpty()) {
            return;
        }
        if (row.tableName() == null || row.tableName().isBlank()) {
            return;
        }
        ClassMetadataBuilder classMetadata = builder.classBuilder(row.iliName());
        classMetadata.tableName(row.tableName());
        classMetadata.sqlName(qualifyTableName(builder, row.tableName()));
        classMetadata.kind(kind.get());
    }

    private Optional<ClassMetadata.ClassKind> mapClassKind(String setting) {
        if (setting == null) {
            return Optional.empty();
        }
        switch (setting.trim().toUpperCase(Locale.ROOT)) {
            case "CLASS":
                return Optional.of(ClassMetadata.ClassKind.CLASS);
            case "STRUCTURE":
                return Optional.of(ClassMetadata.ClassKind.STRUCTURE);
            case "ASSOCIATION":
                return Optional.of(ClassMetadata.ClassKind.ASSOCIATION);
            default:
                return Optional.empty();
        }
    }

    // ------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------

    private void assembleAttribute(Ili2dbReadContext context,
                                   ModelMetadataBuilder builder,
                                   AttributeMappingRow row,
                                   SqlSchemaLookup schemaLookup,
                                   GeometryLookup geometryLookup,
                                   Map<EnumColumnKey, EnumDomainRow> enumDomains,
                                   List<Ili2dbDiagnostic> diagnostics) {
        String ownerClassName = extractOwnerClassName(row.iliName());
        ClassMetadataBuilder classMetadata = resolveAttributeOwner(builder, ownerClassName, row.owner());
        if (classMetadata == null) {
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.WARNING,
                Ili2dbDiagnosticCode.ATTRIBUTE_OWNER_UNRESOLVED,
                "Attribute " + row.iliName() + " belongs to unknown class " + ownerClassName
                    + " (owner table: " + row.owner() + ")",
                row.iliName(), row.owner(), Map.of()));
            return;
        }
        ownerClassName = classMetadata.name();

        String simpleName = extractSimpleName(row.iliName());
        AttributeMetadataBuilder attrMetadata = classMetadata.findAttributeBuilder(simpleName)
            .orElseGet(() -> {
                AttributeMetadataBuilder created =
                    new AttributeMetadataBuilder(simpleName);
                classMetadata.attribute(created);
                return created;
            });
        String qualifiedName = row.iliName();
        if ((qualifiedName == null || !qualifiedName.contains(".")) && ownerClassName != null) {
            qualifiedName = ownerClassName + "." + simpleName;
        }
        attrMetadata.qualifiedName(qualifiedName);
        attrMetadata.columnName(row.sqlName());
        attrMetadata.sqlName(row.sqlName());

        if (row.target() != null && !row.target().isEmpty()) {
            attrMetadata.foreignKey(true);
            attrMetadata.referencedClass(resolveTargetClass(builder, row.target()));
        }

        enrichAttributeFromSchema(attrMetadata, classMetadata.tableName(), row.sqlName(),
            schemaLookup, geometryLookup, diagnostics);

        EnumColumnKey enumKey = EnumColumnKey.normalized(classMetadata.tableName(), row.sqlName());
        EnumDomainRow enumDomain = enumDomains.get(enumKey);
        if (enumDomain != null) {
            attrMetadata.enumType(enumDomain.enumIliName());
            enumReader.valuesOf(context, enumDomain.enumTableName(), diagnostics)
                .forEach(attrMetadata::enumValue);
        }
    }

    private ClassMetadataBuilder resolveAttributeOwner(ModelMetadataBuilder builder,
                                                       String ownerClassName,
                                                       String ownerTableName) {
        if (ownerTableName != null && !ownerTableName.isBlank()) {
            ClassMetadataBuilder byTable = findClassByTableName(builder, ownerTableName);
            if (byTable != null) {
                return byTable;
            }
        }
        if (ownerClassName != null) {
            ClassMetadataBuilder byIliName = builder.findClassBuilder(ownerClassName).orElse(null);
            if (byIliName != null) {
                return byIliName;
            }
        }
        if (ownerTableName != null && !ownerTableName.isBlank()) {
            ClassMetadataBuilder byClassName = builder.findClassBuilder(ownerTableName).orElse(null);
            if (byClassName != null) {
                return byClassName;
            }
        }
        return null;
    }

    private String resolveTargetClass(ModelMetadataBuilder builder, String target) {
        if (target == null || target.isBlank()) {
            return target;
        }
        if (builder.findClassBuilder(target).isPresent()) {
            return target;
        }
        ClassMetadataBuilder classMetadata = findClassByTableName(builder, target);
        if (classMetadata != null) {
            return classMetadata.name();
        }
        return target;
    }

    // ------------------------------------------------------------------
    // Schema enrichment (Snapshot-basiert, keine per-Attribut Roundtrips)
    // ------------------------------------------------------------------

    private void enrichAttributeFromSchema(AttributeMetadataBuilder attr,
                                           String tableName,
                                           String columnName,
                                           SqlSchemaLookup schemaLookup,
                                           GeometryLookup geometryLookup,
                                           List<Ili2dbDiagnostic> diagnostics) {
        Optional<ColumnSchema> column = schemaLookup.column(tableName, columnName);
        if (column.isEmpty()) {
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.WARNING,
                Ili2dbDiagnosticCode.COLUMN_SCHEMA_MISSING,
                "Column schema not found for " + tableName + "." + columnName,
                attr.qualifiedName(), tableName + "." + columnName, Map.of()));
            return;
        }
        ColumnSchema columnSchema = column.get();

        attr.dbType(resolveDbType(columnSchema));
        attr.mandatory(!columnSchema.nullable());
        Integer maxLength = columnSchema.size();
        if (maxLength != null && maxLength == 0) {
            maxLength = null;
        }
        attr.maxLength(maxLength);
        if (isNumericColumn(columnSchema)) {
            attr.precision(maxLength);
            attr.scale(columnSchema.decimalDigits());
        }

        if (columnSchema.databaseTypeName() != null
            && "GEOMETRY".equalsIgnoreCase(columnSchema.databaseTypeName())) {
            attr.geometry(true);
            attr.allowEmptyGeometry(false);
            GeometryColumnSchema geometryColumn = geometryLookup.column(tableName, columnName);
            if (geometryColumn != null) {
                if (geometryColumn.kind() != null) {
                    attr.geometryKind(geometryColumn.kind());
                    if (geometryColumn.hasZ() != null) {
                        attr.geometryHasZ(geometryColumn.hasZ());
                    }
                    if (geometryColumn.hasM() != null) {
                        attr.geometryHasM(geometryColumn.hasM());
                    }
                } else if (attr.geometryKind() == null) {
                    attr.geometryKind(GeometryKind.GEOMETRY);
                }
                attr.geometrySrid(geometryColumn.srid());
            }
        }

        boolean isPrimaryKey = schemaLookup.isPrimaryKey(tableName, columnName)
            || equalsIgnoreCase(PRIMARY_KEY_COLUMN, columnName);
        if (isPrimaryKey && !schemaLookup.isPrimaryKey(tableName, columnName)) {
            diagnostics.add(new Ili2dbDiagnostic(
                Ili2dbSeverity.WARNING,
                Ili2dbDiagnosticCode.PRIMARY_KEY_ASSUMED,
                "Column " + tableName + "." + columnName
                    + " is not declared as primary key; assuming ili2db default",
                attr.qualifiedName(), tableName + "." + columnName, Map.of()));
        }
        attr.primaryKey(isPrimaryKey);
    }

    private String resolveDbType(ColumnSchema column) {
        if (column.jdbcType() == null) {
            return column.databaseTypeName();
        }
        String mappedType = mapSqlTypeCode(column.jdbcType(), column.databaseTypeName());
        if (mappedType != null) {
            return mappedType;
        }
        return column.databaseTypeName() != null && !column.databaseTypeName().isBlank()
            ? column.databaseTypeName()
            : column.jdbcType().toString();
    }

    private boolean isNumericColumn(ColumnSchema column) {
        Integer dataType = column.jdbcType();
        if (dataType != null) {
            return switch (dataType) {
                case Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT,
                    Types.DECIMAL, Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL -> true;
                default -> false;
            };
        }
        String typeName = column.databaseTypeName();
        if (typeName == null || typeName.isBlank()) {
            return false;
        }
        String upperType = typeName.toUpperCase(Locale.ROOT);
        return upperType.contains("INT")
            || upperType.contains("DECIMAL")
            || upperType.contains("NUMERIC")
            || upperType.contains("DOUBLE")
            || upperType.contains("FLOAT")
            || upperType.contains("REAL");
    }

    private String mapSqlTypeCode(int typeCode, String typeName) {
        switch (typeCode) {
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
            case Types.NCHAR:
            case Types.NVARCHAR:
            case Types.LONGNVARCHAR:
                return "VARCHAR";
            case Types.INTEGER:
                return "INTEGER";
            case Types.BIGINT:
                return "BIGINT";
            case Types.DECIMAL:
            case Types.NUMERIC:
                return "DECIMAL";
            case Types.DOUBLE:
            case Types.FLOAT:
            case Types.REAL:
                return "DOUBLE";
            case Types.BOOLEAN:
            case Types.BIT:
                return "BOOLEAN";
            case Types.DATE:
                return "DATE";
            case Types.TIME:
            case Types.TIMESTAMP:
                return "TIMESTAMP";
            default:
                return typeName;
        }
    }

    // ------------------------------------------------------------------
    // Primary keys
    // ------------------------------------------------------------------

    private void ensurePrimaryKeyAttributes(ModelMetadataBuilder builder,
                                            SqlSchemaLookup schemaLookup,
                                            GeometryLookup geometryLookup,
                                            List<Ili2dbDiagnostic> diagnostics) {
        for (ClassMetadataBuilder classMetadata : builder.classBuilders().values()) {
            String tableName = classMetadata.tableName();
            if (tableName == null || tableName.isBlank()) {
                continue;
            }
            AttributeMetadataBuilder attribute =
                findAttributeByColumnName(classMetadata, PRIMARY_KEY_COLUMN);
            if (attribute == null) {
                attribute = findAttributeByName(classMetadata, PRIMARY_KEY_COLUMN);
            }
            if (attribute == null) {
                attribute = new AttributeMetadataBuilder(PRIMARY_KEY_COLUMN);
                attribute.qualifiedName(classMetadata.name() + "." + PRIMARY_KEY_COLUMN);
                attribute.columnName(PRIMARY_KEY_COLUMN);
                attribute.sqlName(PRIMARY_KEY_COLUMN);
                classMetadata.attribute(attribute);
            } else {
                if (attribute.columnName() == null) {
                    attribute.columnName(PRIMARY_KEY_COLUMN);
                }
                if (attribute.sqlName() == null && attribute.columnName() != null) {
                    attribute.sqlName(attribute.columnName());
                }
                if (attribute.qualifiedName() == null && classMetadata.name() != null) {
                    attribute.qualifiedName(classMetadata.name() + "." + attribute.name());
                }
            }
            attribute.primaryKey(true);
            if (attribute.columnName() != null) {
                enrichAttributeFromSchema(attribute, tableName, attribute.columnName(),
                    schemaLookup, geometryLookup, diagnostics);
            }
        }
    }

    private AttributeMetadataBuilder findAttributeByName(ClassMetadataBuilder classMetadata,
                                                         String attributeName) {
        for (AttributeMetadataBuilder attr : classMetadata.attributeBuilders().values()) {
            if (equalsIgnoreCase(attributeName, attr.name())) {
                return attr;
            }
        }
        return null;
    }

    private AttributeMetadataBuilder findAttributeByColumnName(ClassMetadataBuilder classMetadata,
                                                               String columnName) {
        for (AttributeMetadataBuilder attr : classMetadata.attributeBuilders().values()) {
            if (equalsIgnoreCase(columnName, attr.columnName())) {
                return attr;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Column properties
    // ------------------------------------------------------------------

    private void applyColumnProperty(Ili2dbReadContext context,
                                     ModelMetadataBuilder builder,
                                     ColumnPropertyRow row) {
        ClassMetadataBuilder classMetadata = findClassByTableName(builder, row.ownerTable());
        if (classMetadata == null) {
            return;
        }
        AttributeMetadataBuilder attr = classMetadata.findAttributeBuilder(row.columnName())
            .orElseGet(() -> findAttributeByColumnName(classMetadata, row.columnName()));
        if (attr == null) {
            return;
        }
        switch (row.tag()) {
            case "ch.ehi.ili2db.unit":
                attr.unit(row.setting());
                break;
            case ENUM_DOMAIN_TAG:
                attr.enumType(row.setting());
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    private static final class SqlSchemaLookup {
        private final JdbcSchemaSnapshot schema;

        private SqlSchemaLookup(JdbcSchemaSnapshot schema) {
            this.schema = schema;
        }

        private Optional<ColumnSchema> column(String tableName, String columnName) {
            Optional<TableSchema> table = schema.tableByRawName(tableName);
            if (table.isEmpty()) {
                return Optional.empty();
            }
            return table.get().column(columnName);
        }

        private boolean isPrimaryKey(String tableName, String columnName) {
            Optional<TableSchema> table = schema.tableByRawName(tableName);
            return table.isPresent() && table.get().isPrimaryKey(columnName);
        }
    }

    private static final class GeometryLookup {
        private final GeometrySchemaSnapshot geometry;

        private GeometryLookup(GeometrySchemaSnapshot geometry) {
            this.geometry = geometry;
        }

        private GeometryColumnSchema column(String tableName, String columnName) {
            for (GeometryColumnSchema column : geometry.columns()) {
                String candidateTable = column.table() != null && column.table().object() != null
                    ? column.table().object().value() : null;
                if (equalsIgnoreCase(candidateTable, tableName)
                    && equalsIgnoreCase(column.columnName(), columnName)) {
                    return column;
                }
            }
            return null;
        }
    }

    private Map<EnumColumnKey, EnumDomainRow> indexEnumDomains(Collection<EnumDomainRow> rows) {
        Map<EnumColumnKey, EnumDomainRow> indexed = new LinkedHashMap<>();
        for (EnumDomainRow row : rows) {
            indexed.put(EnumColumnKey.normalized(row.ownerTable(), row.columnName()), row);
        }
        return indexed;
    }

    private record EnumColumnKey(String owner, String columnName) {
        static EnumColumnKey normalized(String owner, String columnName) {
            return new EnumColumnKey(normalize(owner), normalize(columnName));
        }

        private static String normalize(String value) {
            return value == null ? null : value.toLowerCase(Locale.ROOT);
        }
    }

    private ClassMetadataBuilder findClassByTableName(ModelMetadataBuilder builder,
                                                      String tableName) {
        for (ClassMetadataBuilder clazz : builder.classBuilders().values()) {
            if (equalsIgnoreCase(tableName, clazz.tableName())) {
                return clazz;
            }
        }
        return null;
    }

    private String extractSimpleName(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }

    private String extractOwnerClassName(String attributeQualifiedName) {
        if (attributeQualifiedName == null) {
            return null;
        }
        int lastDot = attributeQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? attributeQualifiedName.substring(0, lastDot) : attributeQualifiedName;
    }

    /**
     * Raw SQL-Name für die Core-IR (Schema + Tabelle, ungequotet).
     * Quoting ist eine reine SQL-Rendering-Aufgabe und findet hier bewusst nicht statt.
     */
    private String qualifyTableName(ModelMetadataBuilder builder, String tableName) {
        if (builder.schemaName() == null) {
            return tableName;
        }
        return builder.schemaName() + "." + tableName;
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }
}
