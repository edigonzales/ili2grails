package ch.interlis.generator.reader;

import ch.interlis.generator.metadata.selection.ModelSelection;
import ch.interlis.generator.model.*;
import ch.interlis.generator.reader.sql.QualifiedSqlName;
import ch.interlis.generator.reader.sql.SqlIdentifier;
import ch.interlis.generator.reader.sql.SqlIdentifierKind;
import ch.interlis.generator.reader.sql.SqlIdentifierRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest Metadaten aus den ili2db Metatabellen einer Datenbank.
 * 
 * ili2db erstellt folgende Metatabellen:
 * - t_ili2db_classname: Mapping INTERLIS-Klasse -> DB-Tabelle
 * - t_ili2db_attrname: Mapping INTERLIS-Attribut -> DB-Spalte
 * - t_ili2db_trafo: Transformationsparameter
 * - t_ili2db_inheritance: Vererbungshierarchie
 * - t_ili2db_settings: Generelle Einstellungen
 * - t_ili2db_model: Importierte Modelle
 * - t_ili2db_table_prop: Tabellen-Properties
 * - t_ili2db_column_prop: Spalten-Properties (u.a. Constraints)
 *
 * <p>Alle dynamischen Schema-, Tabellen- und Spaltennamen werden als
 * {@link SqlIdentifier} modelliert und beim SQL-Aufbau über den
 * {@link SqlIdentifierRenderer} gequotet. Es gibt keine {@code {schema}}-Ersetzung
 * und keine ungeprüfte String-Konkatenation dynamischer Identifier.</p>
 */
public class Ili2dbMetadataReader {
    
    private static final Logger logger = LoggerFactory.getLogger(Ili2dbMetadataReader.class);

    private static final String ATTR_ILINAME_COLUMN = "iliname";
    private static final String ATTR_OWNER_COLUMN = "colowner";
    private static final String ATTR_TARGET_COLUMN = "target";
    private static final String ATTR_ILINAME_REF = "a." + ATTR_ILINAME_COLUMN;
    private static final String ATTR_OWNER_REF = "a." + ATTR_OWNER_COLUMN;
    private static final String ENUM_DOMAIN_TAG = "ch.ehi.ili2db.enumDomain";
    private static final String PRIMARY_KEY_COLUMN = "t_id";
    
    private final Connection connection;
    private final SqlIdentifier schema;
    private final SqlIdentifierRenderer identifierRenderer;
    private final Map<String, List<EnumMetadata.EnumValue>> enumValueCache = new HashMap<>();
    
    /**
     * Kompatibilitäts-Konstruktor ohne SQLException. Die Renderer-Initialisierung
     * läuft über die JDBC-Metadaten; bei nicht lesbaren Metadaten wird ein
     * warnender Fallback auf PostgreSQL-Quoting verwendet.
     *
     * <p>Bevorzugt ist die Factory {@link #create(Connection, String)}, die Fehler
     * strikt weiterreicht.</p>
     */
    public Ili2dbMetadataReader(Connection connection, String schemaName) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.schema = toSchemaIdentifier(schemaName);
        this.identifierRenderer = defaultRenderer(connection);
    }

    private Ili2dbMetadataReader(Connection connection,
                                 SqlIdentifier schema,
                                 SqlIdentifierRenderer identifierRenderer) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.schema = schema;
        this.identifierRenderer = Objects.requireNonNull(identifierRenderer, "identifierRenderer");
    }

    /**
     * Factory mit strikter Renderer-Initialisierung über JDBC-Metadaten.
     */
    public static Ili2dbMetadataReader create(Connection connection, String schemaName)
            throws SQLException {
        return new Ili2dbMetadataReader(
            Objects.requireNonNull(connection, "connection"),
            toSchemaIdentifier(schemaName),
            SqlIdentifierRenderer.from(connection.getMetaData())
        );
    }

    private static SqlIdentifier toSchemaIdentifier(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            return null;
        }
        return SqlIdentifier.userSupplied(schemaName);
    }

    private static SqlIdentifierRenderer defaultRenderer(Connection connection) {
        try {
            return SqlIdentifierRenderer.from(connection.getMetaData());
        } catch (SQLException e) {
            logger.warn("Could not read identifier quote string from JDBC metadata; "
                + "falling back to PostgreSQL quoting ('\"')", e);
            return SqlIdentifierRenderer.fromQuoteString("\"");
        }
    }

    /**
     * Qualifizierte, gequotete Meta-Tabelle (interner konstanter Name).
     */
    private String metaTable(String tableName) {
        return identifierRenderer.qualify(schema, SqlIdentifier.internal(tableName));
    }

    /**
     * Qualifizierte, gequotete dynamisch entdeckte Tabelle (z.&nbsp;B. Enum-Tabellen).
     */
    private String qualifiedDiscoveredTable(String tableName) {
        return identifierRenderer.qualify(schema, SqlIdentifier.discovered(tableName));
    }

    /**
     * Quotet eine dynamisch entdeckte Spalte.
     */
    private String quotedDiscoveredColumn(String columnName) {
        return identifierRenderer.quote(SqlIdentifier.discovered(columnName));
    }
    
    /**
     * Kompatibilitäts-API: liest nur das Root-Modell
     * ({@link ModelSelection#rootOnly(String)}).
     */
    public ModelMetadata readMetadata(String modelName) throws SQLException {
        return readMetadata(ModelSelection.rootOnly(modelName));
    }

    /**
     * Liest die kompletten Metadaten für die übergebene Modellauswahl.
     *
     * <p>Gelesen wird nur die Schnittmenge aus {@link ModelSelection#includedModelNames()}
     * und den in {@code t_ili2db_model} verfügbaren Modellen. Unabhängige Modelle des
     * Schemas werden nie hinzugefügt. Das Root-Modell muss in der Datenbank vorhanden
     * sein.</p>
     */
    public ModelMetadata readMetadata(ModelSelection selection) throws SQLException {
        Objects.requireNonNull(selection, "selection");
        String modelName = selection.rootModelName();
        logger.info("Reading ili2db metadata for selection: {} -> {}", modelName,
            selection.includedModelNames());

        ModelMetadata metadata = new ModelMetadata(modelName);

        metadata.setSchemaName(schema != null ? schema.value() : null);

        // Settings lesen
        readSettings(metadata);

        Set<String> modelNames = effectiveModelNames(selection, availableDatabaseModels());

        // Klassen lesen
        readClasses(metadata, modelNames);

        // Attribute lesen
        readAttributes(metadata, modelNames);

        // Vererbung auflösen
        readInheritance(metadata, modelNames);

        // Spalten-Properties lesen (Constraints, etc.)
        readColumnProperties(metadata);

        // Beziehungen ableiten
        deriveRelationships(metadata);

        // Association-Klassen als eigene Core-IR vorbereiten
        deriveAssociations(metadata);

        logger.info("Metadata reading complete: {} classes, {} enums",
            metadata.getClasses().size(), metadata.getEnums().size());

        return metadata;
    }
    
    /**
     * Liest die ili2db Settings.
     */
    private void readSettings(ModelMetadata metadata) throws SQLException {
        String sql = "SELECT tag, setting FROM " + metaTable("t_ili2db_settings");
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String tag = rs.getString("tag");
                String setting = rs.getString("setting");
                metadata.getSettings().put(tag, setting);
                
                if ("ch.ehi.ili2db.sender".equals(tag)) {
                    metadata.setIli2dbVersion(setting);
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not read settings", e);
        }
    }
    
    /**
     * Liest alle Klassen (Tables) für das gegebene Modell.
     */
    private void readClasses(ModelMetadata metadata, Collection<String> modelNames) throws SQLException {
        List<String> prefixes = buildModelPrefixes(metadata, modelNames);
        String sql =
            "SELECT tp.tablename, tp.setting, c.iliname " +
            "FROM " + metaTable("t_ili2db_table_prop") + " tp " +
            "LEFT JOIN " + metaTable("t_ili2db_classname") + " c " +
            "  ON upper(tp.tablename) = upper(c.sqlname) " +
            "WHERE " + buildLikeClause("c.iliname", prefixes.size()) + " " +
            "ORDER BY c.iliname"
        ;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            bindLikePrefixes(pstmt, prefixes);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("tablename");
                    String setting = rs.getString("setting");
                    String iliName = rs.getString("iliname");
                    Optional<ClassMetadata.ClassKind> kind = mapClassKind(setting);
                    if (kind.isEmpty()) {
                        if (setting != null && !setting.isBlank()) {
                            logger.debug("Skipping table {} with unsupported type {}", tableName, setting);
                        }
                        continue;
                    }
                    if (iliName == null || iliName.isBlank()) {
                        logger.warn("Skipping table {} because no ili name mapping was found.", tableName);
                        continue;
                    }

                    ClassMetadata classMetadata = new ClassMetadata(iliName);
                    classMetadata.setTableName(tableName);
                    classMetadata.setSqlName(qualifyTableName(tableName));
                    classMetadata.setKind(kind.get());

                    metadata.addClass(classMetadata);

                    logger.debug("Found class: {} -> {} ({})", iliName, tableName, setting);
                }
            }
        }
    }
    
    /**
     * Liest alle Attribute (Columns) für die Klassen.
     */
    private void readAttributes(ModelMetadata metadata, Collection<String> modelNames) throws SQLException {
        List<String> prefixes = buildModelPrefixes(metadata, modelNames);
        List<String> tableNames = metadata.getAllClasses().stream()
            .map(ClassMetadata::getTableName)
            .filter(Objects::nonNull)
            .filter(name -> !name.isBlank())
            .distinct()
            .toList();
        Map<EnumColumnKey, EnumDomainInfo> enumDomains = loadEnumDomains();
        String whereClause = buildAttributeWhereClause(prefixes.size(), tableNames.size());
        String sql =
            "SELECT " + ATTR_ILINAME_REF + ", a.sqlname, a." + ATTR_OWNER_COLUMN + " AS owner, a."
                + ATTR_TARGET_COLUMN + " AS target " +
            "FROM " + metaTable("t_ili2db_attrname") + " a " +
            "WHERE " + whereClause + " " +
            "ORDER BY a." + ATTR_OWNER_COLUMN + ", a.sqlname"
        ;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            bindAttributeFilters(pstmt, prefixes, tableNames);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String iliName = rs.getString("iliname");
                    String sqlName = rs.getString("sqlname");
                    String owner = rs.getString("owner");
                    String target = rs.getString("target");

                    String ownerClassName = extractOwnerClassName(iliName);
                    ClassMetadata classMetadata = resolveAttributeOwner(metadata, ownerClassName, owner);
                    if (classMetadata == null) {
                        logger.warn("Attribute {} belongs to unknown class {} (owner table: {})",
                            iliName, ownerClassName, owner);
                        continue;
                    }
                    ownerClassName = classMetadata.getName();
                    
                    String simpleName = extractSimpleName(iliName);
                    AttributeMetadata attrMetadata = new AttributeMetadata(simpleName);
                    String qualifiedName = iliName;
                    if ((qualifiedName == null || !qualifiedName.contains(".")) && ownerClassName != null) {
                        qualifiedName = ownerClassName + "." + simpleName;
                    }
                    attrMetadata.setQualifiedName(qualifiedName);
                    attrMetadata.setColumnName(sqlName);
                    attrMetadata.setSqlName(sqlName);
                                        
                    // Ist es eine Beziehung (FK)?
                    if (target != null && !target.isEmpty()) {
                        attrMetadata.setForeignKey(true);
                        String resolvedTarget = resolveTargetClass(metadata, target);
                        attrMetadata.setReferencedClass(resolvedTarget);
                    }
                    
                    // Datenbank-Typ und weitere Infos aus DB-Schema holen
                    enrichAttributeFromDbSchema(attrMetadata, classMetadata.getTableName(), sqlName);

                    EnumDomainInfo enumDomain = enumDomains.get(
                        EnumColumnKey.normalized(classMetadata.getTableName(), sqlName)
                    );
                    if (enumDomain != null) {
                        attrMetadata.setEnumType(enumDomain.enumIliName());
                        List<EnumMetadata.EnumValue> values = loadEnumValues(enumDomain.enumTableName());
                        values.forEach(attrMetadata::addEnumValue);
                    }
                    
                    classMetadata.addAttribute(attrMetadata);
                    
                    logger.debug("  Attribute: {}.{} -> {}", 
                        classMetadata.getSimpleName(), iliName, sqlName);
                }
            }
        }
        ensurePrimaryKeyAttributes(metadata);
    }

    private Map<EnumColumnKey, EnumDomainInfo> loadEnumDomains() throws SQLException {
        ColumnPropColumns columns = resolveColumnPropColumns();
        if (columns == null) {
            return Collections.emptyMap();
        }
        String sql =
            "SELECT cp." + quotedDiscoveredColumn(columns.ownerColumn()) + " AS owner, "
                + "cp." + quotedDiscoveredColumn(columns.columnColumn()) + " AS columnname, "
                + "cp.setting AS enumIliName, cn.sqlname AS enumTable " +
            "FROM " + metaTable("t_ili2db_column_prop") + " cp " +
            "LEFT JOIN " + metaTable("t_ili2db_classname") + " cn ON cp.setting = cn.iliname " +
            "WHERE cp.tag = ?"
        ;
        Map<EnumColumnKey, EnumDomainInfo> enumDomains = new HashMap<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ENUM_DOMAIN_TAG);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String owner = rs.getString("owner");
                    String columnName = rs.getString("columnname");
                    String enumIliName = rs.getString("enumIliName");
                    String enumTable = rs.getString("enumTable");
                    if (owner == null || columnName == null || enumIliName == null || enumTable == null) {
                        continue;
                    }
                    enumDomains.put(EnumColumnKey.normalized(owner, columnName),
                        new EnumDomainInfo(enumIliName, enumTable));
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not read enum domains from column properties", e);
        }
        return enumDomains;
    }

    private ColumnPropColumns resolveColumnPropColumns() throws SQLException {
        String sql = "SELECT * FROM " + metaTable("t_ili2db_column_prop") + " WHERE 1=0";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            String ownerColumn = findColumn(meta, "colowner", "tablename");
            String columnColumn = findColumn(meta, "columnname", "sqlname");
            if (ownerColumn == null || columnColumn == null) {
                logger.warn("Could not determine column owner/column name fields in t_ili2db_column_prop.");
                return null;
            }
            return new ColumnPropColumns(ownerColumn, columnColumn);
        }
    }

    private List<EnumMetadata.EnumValue> loadEnumValues(String enumTableName) {
        if (enumTableName == null || enumTableName.isBlank()) {
            return List.of();
        }
        return enumValueCache.computeIfAbsent(enumTableName, this::readEnumTableValues);
    }

    private List<EnumMetadata.EnumValue> readEnumTableValues(String enumTableName) {
        List<EnumMetadata.EnumValue> values = new ArrayList<>();
        String sql = "SELECT * FROM " + qualifiedDiscoveredTable(enumTableName);
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            String iliCodeColumn = findColumn(meta, "ilicode");
            String dispNameColumn = findColumn(meta, "dispname");
            String seqColumn = findColumn(meta, "seq");
            while (rs.next()) {
                String iliCode = iliCodeColumn != null ? rs.getString(iliCodeColumn) : null;
                if (iliCode == null) {
                    continue;
                }
                int seq = seqColumn != null ? rs.getInt(seqColumn) : values.size();
                EnumMetadata.EnumValue value = new EnumMetadata.EnumValue(iliCode, seq);
                String dispName = dispNameColumn != null ? rs.getString(dispNameColumn) : null;
                if (dispName == null || dispName.isBlank()) {
                    dispName = iliCode;
                }
                value.setDispName(dispName);
                values.add(value);
            }
        } catch (SQLException e) {
            logger.warn("Could not read enum table values from {}", enumTableName, e);
        }
        return values;
    }
    
    /**
     * Holt zusätzliche Informationen über ein Attribut aus dem DB-Schema.
     */
    private void enrichAttributeFromDbSchema(AttributeMetadata attr, String tableName, String columnName)
        throws SQLException {
        ColumnInfo columnInfo = resolveColumnInfo(tableName, columnName);
        if (columnInfo == null) {
            return;
        }

        String resolvedType = resolveDbType(columnInfo.dataType(), columnInfo.typeName());
        attr.setDbType(resolvedType);
        if (columnInfo.nullable() != null) {
            attr.setMandatory(ResultSetMetaData.columnNoNulls == columnInfo.nullable());
        }
        Integer maxLength = columnInfo.columnSize();
        if (maxLength != null && maxLength == 0) {
            maxLength = null;
        }
        attr.setMaxLength(maxLength);
        if (isNumericColumn(columnInfo)) {
            attr.setPrecision(maxLength);
            attr.setScale(columnInfo.decimalDigits());
        }

        if (columnInfo.typeName() != null && "GEOMETRY".equalsIgnoreCase(columnInfo.typeName())) {
            attr.setGeometry(true);
            String resolvedGeometryKind = resolveGeometryKind(tableName, columnName);
            if (resolvedGeometryKind != null && !resolvedGeometryKind.isBlank()) {
                attr.setGeometryKind(resolvedGeometryKind);
                attr.setGeometryHasZ(geometryTypeHasZ(resolvedGeometryKind));
                attr.setGeometryHasM(geometryTypeHasM(resolvedGeometryKind));
            } else if (attr.getGeometryKind() == null || attr.getGeometryKind().isBlank()) {
                attr.setGeometryKind(GeometryKind.GEOMETRY);
            }
            attr.setAllowEmptyGeometry(false);
            attr.setGeometrySrid(resolveGeometrySrid(tableName, columnName));
        }

        attr.setPrimaryKey(isPrimaryKey(tableName, columnName));
    }

    private Integer resolveGeometrySrid(String tableName, String columnName) {
        try {
            if (!isPostgreSql(connection)) {
                return null;
            }
            String resolvedSchema = schema != null ? schema.value() : null;
            if (resolvedSchema == null || resolvedSchema.isBlank()) {
                resolvedSchema = "public";
            }
            String sql = "SELECT Find_SRID(?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, resolvedSchema);
                stmt.setString(2, tableName);
                stmt.setString(3, columnName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int srid = rs.getInt(1);
                        if (!rs.wasNull() && srid > 0) {
                            return srid;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.debug("Could not resolve SRID for {}.{} ({})", tableName, columnName, schema, e);
        }
        return null;
    }

    private String resolveGeometryKind(String tableName, String columnName) {
        try {
            if (!isPostgreSql(connection)) {
                return null;
            }
            String resolvedSchema = schema != null ? schema.value() : null;
            if (resolvedSchema == null || resolvedSchema.isBlank()) {
                resolvedSchema = "public";
            }

            String sqlWithSchema =
                "SELECT type FROM geometry_columns " +
                    "WHERE lower(f_table_schema) = lower(?) " +
                    "AND lower(f_table_name) = lower(?) " +
                    "AND lower(f_geometry_column) = lower(?)";
            try (PreparedStatement stmt = connection.prepareStatement(sqlWithSchema)) {
                stmt.setString(1, resolvedSchema);
                stmt.setString(2, tableName);
                stmt.setString(3, columnName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString(1);
                    }
                }
            }

            String sqlWithoutSchema =
                "SELECT type FROM geometry_columns " +
                    "WHERE lower(f_table_name) = lower(?) " +
                    "AND lower(f_geometry_column) = lower(?)";
            try (PreparedStatement stmt = connection.prepareStatement(sqlWithoutSchema)) {
                stmt.setString(1, tableName);
                stmt.setString(2, columnName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString(1);
                    }
                }
            }
        } catch (SQLException e) {
            logger.debug("Could not resolve geometry kind for {}.{} ({})", tableName, columnName, schema, e);
        }
        return null;
    }

    private boolean geometryTypeHasZ(String rawKind) {
        String normalized = normalizeGeometryTypeSuffix(rawKind);
        return normalized.endsWith("Z") || normalized.endsWith("ZM");
    }

    private boolean geometryTypeHasM(String rawKind) {
        String normalized = normalizeGeometryTypeSuffix(rawKind);
        return normalized.endsWith("M") || normalized.endsWith("ZM");
    }

    private String normalizeGeometryTypeSuffix(String rawKind) {
        if (rawKind == null) {
            return "";
        }
        return rawKind.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
    }

    private ColumnInfo resolveColumnInfo(String tableName, String columnName) throws SQLException {
        if (isSqlite(connection)) {
            ColumnInfo sqliteInfo = readSqliteColumnInfo(tableName, columnName);
            if (sqliteInfo != null) {
                return sqliteInfo;
            }
        }
        DatabaseMetaData meta = connection.getMetaData();
        ColumnInfo direct = findColumnInfo(meta, tableName, columnName, schemaNameValue());
        if (direct != null) {
            return direct;
        }
        return findColumnInfo(meta, tableName, columnName, null);
    }

    private String schemaNameValue() {
        return schema != null ? schema.value() : null;
    }

    private ColumnInfo findColumnInfo(DatabaseMetaData meta, String tableName, String columnName, String schema)
        throws SQLException {
        try (ResultSet rs = meta.getColumns(null, schema, tableName, null)) {
            while (rs.next()) {
                String resolvedTable = rs.getString("TABLE_NAME");
                String resolvedColumn = rs.getString("COLUMN_NAME");
                if (equalsIgnoreCase(resolvedTable, tableName) && equalsIgnoreCase(resolvedColumn, columnName)) {
                    Integer dataType = rs.getInt("DATA_TYPE");
                    if (rs.wasNull()) {
                        dataType = null;
                    }
                    String typeName = rs.getString("TYPE_NAME");
                    Integer nullable = rs.getInt("NULLABLE");
                    if (rs.wasNull()) {
                        nullable = null;
                    }
                    Integer size = rs.getInt("COLUMN_SIZE");
                    if (rs.wasNull()) {
                        size = null;
                    }
                    Integer decimalDigits = rs.getInt("DECIMAL_DIGITS");
                    if (rs.wasNull()) {
                        decimalDigits = null;
                    }
                    return new ColumnInfo(dataType, typeName, nullable, size, decimalDigits);
                }
            }
        }
        return null;
    }

    private ColumnInfo readSqliteColumnInfo(String tableName, String columnName) throws SQLException {
        String escapedTable = tableName.replace("'", "''");
        String sql = "PRAGMA table_info('" + escapedTable + "')";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (!equalsIgnoreCase(name, columnName)) {
                    continue;
                }
                String typeName = rs.getString("type");
                Integer nullable = rs.getInt("notnull") == 1
                    ? ResultSetMetaData.columnNoNulls
                    : ResultSetMetaData.columnNullable;
                Integer columnSize = parseColumnSize(typeName);
                Integer decimalDigits = parseDecimalDigits(typeName);
                return new ColumnInfo(null, typeName, nullable, columnSize, decimalDigits);
            }
        }
        return null;
    }

    private Integer parseColumnSize(String typeName) {
        if (typeName == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\\((\\d+)\\)").matcher(typeName);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private Integer parseDecimalDigits(String typeName) {
        if (typeName == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\\(\\d+\\s*,\\s*(\\d+)\\)").matcher(typeName);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private String resolveDbType(Integer dataType, String typeName) {
        if (dataType == null) {
            return typeName;
        }
        String mappedType = mapSqlTypeCode(dataType, typeName);
        if (mappedType != null) {
            return mappedType;
        }
        return typeName != null && !typeName.isBlank() ? typeName : dataType.toString();
    }

    private boolean isNumericColumn(ColumnInfo columnInfo) {
        if (columnInfo == null) {
            return false;
        }
        Integer dataType = columnInfo.dataType();
        if (dataType != null) {
            return switch (dataType) {
                case Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT,
                    Types.DECIMAL, Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL -> true;
                default -> false;
            };
        }
        String typeName = columnInfo.typeName();
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
    
    /**
     * Prüft ob eine Spalte ein Primary Key ist.
     */
    private boolean isPrimaryKey(String tableName, String columnName) throws SQLException {
        return equalsIgnoreCase(PRIMARY_KEY_COLUMN, columnName);
    }

    private void ensurePrimaryKeyAttributes(ModelMetadata metadata) throws SQLException {
        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            String tableName = classMetadata.getTableName();
            if (tableName == null || tableName.isBlank()) {
                continue;
            }
            AttributeMetadata attribute = findAttributeByColumnName(classMetadata, PRIMARY_KEY_COLUMN);
            if (attribute == null) {
                attribute = findAttributeByName(classMetadata, PRIMARY_KEY_COLUMN);
            }
            if (attribute == null) {
                attribute = new AttributeMetadata(PRIMARY_KEY_COLUMN);
                attribute.setQualifiedName(classMetadata.getName() + "." + PRIMARY_KEY_COLUMN);
                attribute.setColumnName(PRIMARY_KEY_COLUMN);
                attribute.setSqlName(PRIMARY_KEY_COLUMN);
                classMetadata.addAttribute(attribute);
            } else {
                if (attribute.getColumnName() == null) {
                    attribute.setColumnName(PRIMARY_KEY_COLUMN);
                }
                if (attribute.getSqlName() == null && attribute.getColumnName() != null) {
                    attribute.setSqlName(attribute.getColumnName());
                }
                if (attribute.getQualifiedName() == null && classMetadata.getName() != null) {
                    attribute.setQualifiedName(classMetadata.getName() + "." + attribute.getName());
                }
            }
            attribute.setPrimaryKey(true);
            if (attribute.getColumnName() != null) {
                enrichAttributeFromDbSchema(attribute, tableName, attribute.getColumnName());
            }
        }
    }

    private AttributeMetadata findAttributeByName(ClassMetadata classMetadata, String attributeName) {
        for (AttributeMetadata attr : classMetadata.getAllAttributes()) {
            if (equalsIgnoreCase(attributeName, attr.getName())) {
                return attr;
            }
        }
        return null;
    }
    
    /**
     * Liest die Vererbungshierarchie.
     */
    private void readInheritance(ModelMetadata metadata, Collection<String> modelNames) throws SQLException {
        List<String> prefixes = buildModelPrefixes(metadata, modelNames);
        String sql =
            "SELECT thisclass, baseclass FROM " + metaTable("t_ili2db_inheritance") + " " +
            "WHERE " + buildLikeClause("thisclass", prefixes.size())
        ;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            bindLikePrefixes(pstmt, prefixes);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String thisClass = rs.getString("thisclass");
                    String baseClass = rs.getString("baseclass");
                    
                    ClassMetadata classMetadata = metadata.getClass(thisClass);
                    if (classMetadata != null) {
                        classMetadata.setBaseClass(baseClass);
                        logger.debug("Inheritance: {} extends {}", thisClass, baseClass);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not read inheritance information", e);
        }
    }
    
    /**
     * Liest Spalten-Properties (Constraints, etc.).
     */
    private void readColumnProperties(ModelMetadata metadata) throws SQLException {
        String sql =
            "SELECT tablename, columnname, tag, setting " +
            "FROM " + metaTable("t_ili2db_column_prop") + " " +
            "ORDER BY tablename, columnname"
        ;
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String tableName = rs.getString("tablename");
                String columnName = rs.getString("columnname");
                String tag = rs.getString("tag");
                String setting = rs.getString("setting");
                
                // Finde die entsprechende Klasse
                ClassMetadata classMetadata = findClassByTableName(metadata, tableName);
                if (classMetadata == null) continue;
                
                AttributeMetadata attr = classMetadata.getAttribute(columnName);
                if (attr == null) {
                    // Suche nach Spaltenname statt INTERLIS-Name
                    attr = findAttributeByColumnName(classMetadata, columnName);
                }
                
                if (attr != null) {
                    applyColumnProperty(attr, tag, setting);
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not read column properties", e);
        }
    }
    
    private ClassMetadata findClassByTableName(ModelMetadata metadata, String tableName) {
        for (ClassMetadata clazz : metadata.getAllClasses()) {
            if (equalsIgnoreCase(tableName, clazz.getTableName())) {
                return clazz;
            }
        }
        return null;
    }
    
    private AttributeMetadata findAttributeByColumnName(ClassMetadata classMetadata, String columnName) {
        for (AttributeMetadata attr : classMetadata.getAllAttributes()) {
            if (equalsIgnoreCase(columnName, attr.getColumnName())) {
                return attr;
            }
        }
        return null;
    }
    
    private void applyColumnProperty(AttributeMetadata attr, String tag, String setting) {
        switch (tag) {
            case "ch.ehi.ili2db.unit":
                attr.setUnit(setting);
                break;
            case ENUM_DOMAIN_TAG:
                attr.setEnumType(setting);
                break;
            case "ch.ehi.ili2db.dispName":
                // Display name könnte für Labels verwendet werden
                break;
            // Weitere Properties können hier ergänzt werden
        }
    }
    
    /**
     * Leitet Beziehungen aus Foreign Keys ab.
     */
    private void deriveRelationships(ModelMetadata metadata) {
        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            for (AttributeMetadata attr : classMetadata.getAllAttributes()) {
                if (attr.isForeignKey() && attr.getReferencedClass() != null) {
                    RelationshipMetadata rel = new RelationshipMetadata(
                        classMetadata.getName() + "_" + attr.getName()
                    );
                    rel.setSourceClass(classMetadata.getName());
                    rel.setTargetClass(attr.getReferencedClass());
                    String sourceAttribute = attr.getSqlName() != null ? attr.getSqlName() : attr.getName();
                    rel.setSourceAttribute(sourceAttribute);
                    rel.setTargetAttribute("T_Id"); // ili2db Standard
                    rel.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
                    rel.setSemanticKind(RelationshipMetadata.SemanticKind.ILI2DB_FK);
                    rel.setSource("ili2db");
                    rel.setPhysicalName(sourceAttribute);
                    rel.setMergeReason(RelationshipMetadata.MergeReason.ILI2DB_ONLY);
                    rel.setMergeConfidence(RelationshipMetadata.MergeConfidence.NONE);
                    rel.setTargetRoleName(attr.getName());
                    rel.setCardinality(new RelationshipMetadata.Cardinality(
                        1,
                        1,
                        attr.isMandatory() ? 1 : 0,
                        1
                    ));
                    rel.setMandatory(attr.isMandatory());

                    metadata.addRelationship(rel);
                }
            }
        }
    }

    private void deriveAssociations(ModelMetadata metadata) {
        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            if (classMetadata.getKind() != ClassMetadata.ClassKind.ASSOCIATION) {
                continue;
            }

            AssociationMetadata association = new AssociationMetadata(classMetadata.getName());
            association.setAssociationClass(classMetadata.getName());
            association.setPhysicalTable(classMetadata.getTableName());
            association.setPhysicalSqlName(classMetadata.getSqlName());

            List<RelationshipMetadata> associationRelationships = metadata.getAllRelationships().stream()
                .filter(relationship -> Objects.equals(relationship.getSourceClass(), classMetadata.getName()))
                .toList();
            for (RelationshipMetadata relationship : associationRelationships) {
                association.addRole(toAssociationRole(relationship));
            }
            for (AttributeMetadata attribute : classMetadata.getAllAttributes()) {
                if (!isAssociationRoleAttribute(attribute, associationRelationships)) {
                    association.addAttribute(attribute);
                }
            }

            metadata.addAssociation(association);
        }
    }

    private AssociationRoleMetadata toAssociationRole(RelationshipMetadata relationship) {
        String roleName = relationship.getTargetRoleName() != null
            ? relationship.getTargetRoleName()
            : relationship.getSourceAttribute();
        if (roleName == null || roleName.isBlank()) {
            roleName = relationship.getName();
        }
        AssociationRoleMetadata role = new AssociationRoleMetadata(roleName);
        role.setTargetClass(relationship.getTargetClass());
        role.setOppositeRoleName(relationship.getOppositeRoleName());
        role.setCardinality(relationship.getCardinality());
        role.setMandatory(relationship.isMandatory());
        role.setOrdered(relationship.isOrdered());
        role.setExternal(relationship.isExternal());
        role.setComposition(relationship.isComposition());
        role.setSourceAttribute(relationship.getSourceAttribute());
        role.setTargetAttribute(relationship.getTargetAttribute());
        role.setPhysicalName(relationship.getPhysicalName());
        role.setSemanticName(relationship.getSemanticName());
        role.setSource(relationship.getSource());
        role.setMergeReason(relationship.getMergeReason());
        role.setMergeConfidence(relationship.getMergeConfidence());
        role.setMergeToken(relationship.getMergeToken());
        return role;
    }

    private boolean isAssociationRoleAttribute(AttributeMetadata attribute,
                                               List<RelationshipMetadata> relationships) {
        if (attribute.isPrimaryKey() || attribute.isForeignKey()) {
            return true;
        }
        for (RelationshipMetadata relationship : relationships) {
            if (equalsAny(attribute.getName(), relationship.getSourceAttribute(), relationship.getPhysicalName())
                || equalsAny(attribute.getColumnName(), relationship.getSourceAttribute(), relationship.getPhysicalName())
                || equalsAny(attribute.getSqlName(), relationship.getSourceAttribute(), relationship.getPhysicalName())) {
                return true;
            }
        }
        return false;
    }

    private boolean equalsAny(String value, String... candidates) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate != null && value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String buildLikeClause(String columnName, int paramCount) {
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < paramCount; i++) {
            if (i > 0) {
                builder.append(" OR ");
            }
            builder.append(columnName).append(" LIKE ?");
        }
        builder.append(")");
        return builder.toString();
    }

    private void bindLikePrefixes(PreparedStatement pstmt, List<String> prefixes) throws SQLException {
        for (int i = 0; i < prefixes.size(); i++) {
            pstmt.setString(i + 1, prefixes.get(i));
        }
    }

    private String buildAttributeWhereClause(int prefixCount, int tableCount) {
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < prefixCount; i++) {
            if (i > 0) {
                builder.append(" OR ");
            }
            builder.append("(")
                .append(ATTR_ILINAME_REF)
                .append(" LIKE ? OR ")
                .append(ATTR_OWNER_REF)
                .append(" LIKE ?)");
        }
        builder.append(")");
        if (tableCount > 0) {
            builder.append(" OR (");
            for (int i = 0; i < tableCount; i++) {
                if (i > 0) {
                    builder.append(" OR ");
                }
                builder.append(ATTR_OWNER_REF).append(" = ?");
            }
            builder.append(")");
        }
        return builder.toString();
    }

    private void bindAttributeFilters(PreparedStatement pstmt, List<String> prefixes, List<String> tableNames)
            throws SQLException {
        int index = 1;
        for (String prefix : prefixes) {
            pstmt.setString(index++, prefix);
            pstmt.setString(index++, prefix);
        }
        for (String tableName : tableNames) {
            pstmt.setString(index++, tableName);
        }
    }

    /**
     * Bestimmt die Owner-Klasse eines Attributs.
     *
     * <p>Reihenfolge: (1) der COLOWNER als Tabellenname (physische Wahrheit: die
     * Spalte liegt in dieser Tabelle — bei Kompositions-FKs weicht die Tabelle
     * vom iliname-Präfix ab), (2) der iliname-Präfix als Klassenname, (3) der
     * COLOWNER als Klassenname.</p>
     */
    private ClassMetadata resolveAttributeOwner(ModelMetadata metadata,
                                                String ownerClassName,
                                                String ownerTableName) {
        if (ownerTableName != null && !ownerTableName.isBlank()) {
            ClassMetadata byTable = findClassByTableName(metadata, ownerTableName);
            if (byTable != null) {
                return byTable;
            }
        }
        if (ownerClassName != null) {
            ClassMetadata byIliName = metadata.getClass(ownerClassName);
            if (byIliName != null) {
                return byIliName;
            }
        }
        if (ownerTableName != null && !ownerTableName.isBlank()) {
            ClassMetadata byClassName = metadata.getClass(ownerTableName);
            if (byClassName != null) {
                return byClassName;
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

    private List<String> buildModelPrefixes(ModelMetadata metadata, Collection<String> modelNames) {
        Set<String> uniqueNames = new LinkedHashSet<>();
        if (modelNames != null) {
            for (String name : modelNames) {
                if (name != null && !name.isBlank()) {
                    uniqueNames.add(name);
                }
            }
        }
        if (uniqueNames.isEmpty()) {
            if (metadata.getModelName() != null && !metadata.getModelName().isBlank()) {
                uniqueNames.add(metadata.getModelName());
            }
        }
        List<String> prefixes = new ArrayList<>();
        if (uniqueNames.isEmpty()) {
            prefixes.add("%");
        } else {
            for (String name : uniqueNames) {
                prefixes.add(name + ".%");
            }
        }
        return prefixes;
    }

    /**
     * Ermittelt die in {@code t_ili2db_model} verfügbaren Modelle.
     * Nicht lesbare oder fehlende Metatabelle liefert eine leere Menge mit Warnung.
     */
    private Set<String> availableDatabaseModels() throws SQLException {
        Set<String> modelNames = new LinkedHashSet<>();
        String sql = "SELECT * FROM " + metaTable("t_ili2db_model");
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            String modelColumn = findColumn(meta, "modelname", "model", "name");
            if (modelColumn == null) {
                logger.warn("Could not detect model name column in t_ili2db_model.");
                return modelNames;
            }
            String contentColumn = findColumn(meta, "content");

            while (rs.next()) {
                String modelName = rs.getString(modelColumn);
                if (modelName == null || modelName.isBlank()) {
                    continue;
                }
                if (contentColumn != null && isTypeModel(rs.getString(contentColumn))) {
                    continue;
                }
                modelNames.add(canonicalModelName(modelName));
                logger.debug("Detected ili2db model: {}", modelName);
            }
        } catch (SQLException e) {
            logger.warn("Could not read model list from t_ili2db_model; "
                + "falling back to root-only selection.", e);
            return modelNames;
        }
        return modelNames;
    }

    /**
     * ili2db schreibt für Modelle mit Imports den Namen als
     * {@code Name{imports}} in {@code t_ili2db_model}. Der kanonische
     * Modellname ist der Teil vor der geschweiften Klammer.
     */
    private String canonicalModelName(String modelName) {
        int braceIndex = modelName.indexOf('{');
        if (braceIndex < 0) {
            return modelName;
        }
        String canonical = modelName.substring(0, braceIndex).trim();
        return canonical.isBlank() ? modelName : canonical;
    }

    /**
     * Effektive Modellnamen = Auswahl ∩ verfügbare DB-Modelle.
     *
     * <p>Das Root-Modell muss in der Datenbank vorhanden sein (sonst Fehler).
     * Fehlende benötigte Dependencies werden übersprungen und gewarnt;
     * unabhängige DB-Modelle werden nie hinzugefügt. Wenn die Metatabelle nicht
     * lesbar ist (leere Verfügbarkeitsmenge), wird Root-only gelesen.</p>
     */
    private Set<String> effectiveModelNames(
        ModelSelection selection,
        Set<String> availableDatabaseModels
    ) {
        if (availableDatabaseModels.isEmpty()) {
            logger.warn("Dependency graph models not verifiable against t_ili2db_model; "
                + "reading root model only: {}", selection.rootModelName());
            return new LinkedHashSet<>(Set.of(selection.rootModelName()));
        }
        if (!availableDatabaseModels.contains(selection.rootModelName())) {
            throw new IllegalArgumentException(
                "Root model not found in t_ili2db_model: " + selection.rootModelName());
        }
        Set<String> effective = new LinkedHashSet<>();
        for (String name : selection.includedModelNames()) {
            if (availableDatabaseModels.contains(name)) {
                effective.add(name);
            } else {
                logger.warn("Requested model {} is not present in t_ili2db_model; skipping.", name);
            }
        }
        return effective;
    }

    private String findColumn(ResultSetMetaData meta, String... candidates) throws SQLException {
        Map<String, String> available = new HashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String label = meta.getColumnLabel(i);
            String name = meta.getColumnName(i);
            if (label != null) {
                available.putIfAbsent(label.toLowerCase(Locale.ROOT), label);
            }
            if (name != null) {
                available.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
            }
        }
        for (String candidate : candidates) {
            String matched = available.get(candidate.toLowerCase(Locale.ROOT));
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    private boolean isTypeModel(String content) {
        if (content == null) {
            return false;
        }
        return content.matches("(?s).*TYPE\\s+MODEL.*");
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
            case "ENUM":
                return Optional.empty();
            default:
                return Optional.empty();
        }
    }

    private String resolveTargetClass(ModelMetadata metadata, String target) {
        if (target == null || target.isBlank()) {
            return target;
        }
        if (metadata.getClass(target) != null) {
            return target;
        }
        ClassMetadata classMetadata = findClassByTableName(metadata, target);
        if (classMetadata != null) {
            return classMetadata.getName();
        }
        return target;
    }

    private record EnumColumnKey(String owner, String columnName) {
        static EnumColumnKey normalized(String owner, String columnName) {
            return new EnumColumnKey(normalize(owner), normalize(columnName));
        }

        private static String normalize(String value) {
            return value == null ? null : value.toLowerCase(Locale.ROOT);
        }
    }

    private record EnumDomainInfo(String enumIliName, String enumTableName) {
    }

    private record ColumnPropColumns(String ownerColumn, String columnColumn) {
    }

    private record ColumnInfo(
        Integer dataType,
        String typeName,
        Integer nullable,
        Integer columnSize,
        Integer decimalDigits
    ) {
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    /**
     * Raw SQL-Name für die Core-IR (Schema + Tabelle, ungequotet).
     * Quoting ist eine reine SQL-Rendering-Aufgabe und findet hier bewusst nicht statt.
     */
    private String qualifyTableName(String tableName) {
        if (schema == null) {
            return tableName;
        }
        return schema.value() + "." + tableName;
    }

    private boolean isSqlite(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName();
        return productName != null && productName.toLowerCase(Locale.ROOT).contains("sqlite");
    }

    private boolean isPostgreSql(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName();
        return productName != null && productName.toLowerCase(Locale.ROOT).contains("postgresql");
    }
}
