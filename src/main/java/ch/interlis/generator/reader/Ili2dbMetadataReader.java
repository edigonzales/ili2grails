package ch.interlis.generator.reader;

import ch.interlis.generator.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

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
 */
public class Ili2dbMetadataReader {
    
    private static final Logger logger = LoggerFactory.getLogger(Ili2dbMetadataReader.class);

    private static final String ATTR_ILINAME_COLUMN = "iliname";
    private static final String ATTR_OWNER_COLUMN = "colowner";
    private static final String ATTR_TARGET_COLUMN = "target";
    private static final String ATTR_ILINAME_REF = "a." + ATTR_ILINAME_COLUMN;
    private static final String ATTR_OWNER_REF = "a." + ATTR_OWNER_COLUMN;
    private static final String ENUM_DOMAIN_TAG = "ch.ehi.ili2db.enumDomain";
    
    private final Connection connection;
    private String schemaName;
    private final Map<String, List<EnumMetadata.EnumValue>> enumValueCache = new HashMap<>();
    
    public Ili2dbMetadataReader(Connection connection, String schemaName) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.schemaName = schemaName;
    }
    
    /**
     * Liest die kompletten Metadaten für ein bestimmtes Modell.
     */
    public ModelMetadata readMetadata(String modelName) throws SQLException {
        logger.info("Reading ili2db metadata for model: {}", modelName);
        
        ModelMetadata metadata = new ModelMetadata(modelName);
        
        ensureSchemaName();
        metadata.setSchemaName(schemaName);
        
        // Settings lesen
        readSettings(metadata);
        
        Set<String> modelNames = resolveRelevantModelNames(modelName);

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
        
        logger.info("Metadata reading complete: {} classes, {} enums", 
            metadata.getClasses().size(), metadata.getEnums().size());
        
        return metadata;
    }
    
    /**
     * Liest die ili2db Settings.
     */
    private void readSettings(ModelMetadata metadata) throws SQLException {
        String sql = buildQuery("SELECT tag, setting FROM {schema}.t_ili2db_settings");
        
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
        String sql = buildQuery(
            "SELECT tp.tablename, tp.setting, c.iliname " +
            "FROM {schema}.t_ili2db_table_prop tp " +
            "LEFT JOIN {schema}.t_ili2db_classname c " +
            "  ON upper(tp.tablename) = upper(c.sqlname) " +
            "WHERE " + buildLikeClause("c.iliname", prefixes.size()) + " " +
            "ORDER BY c.iliname"
        );
        
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
                    classMetadata.setSqlName(schemaName + "." + tableName);
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
        String sql = buildQuery(String.format(
            "SELECT " + ATTR_ILINAME_REF + ", a.sqlname, a.%s AS owner, a.%s AS target " +
            "FROM {schema}.t_ili2db_attrname a " +
            "WHERE " + whereClause + " " +
            "ORDER BY a.%s, a.sqlname",
            ATTR_OWNER_COLUMN,
            ATTR_TARGET_COLUMN,
            ATTR_OWNER_COLUMN
        ));
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            bindAttributeFilters(pstmt, prefixes, tableNames);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String iliName = rs.getString("iliname");
                    String sqlName = rs.getString("sqlname");
                    String owner = rs.getString("owner");
                    String target = rs.getString("target");

                    String ownerClassName = extractOwnerClassName(iliName);
                    ClassMetadata classMetadata = metadata.getClass(ownerClassName);
                    if (classMetadata == null && owner != null && !owner.isBlank()) {
                        classMetadata = metadata.getClass(owner);
                        if (classMetadata != null) {
                            ownerClassName = owner;
                        } else {
                            classMetadata = findClassByTableName(metadata, owner);
                            if (classMetadata != null) {
                                ownerClassName = classMetadata.getName();
                            }
                        }
                    }
                    if (classMetadata == null) {
                        logger.warn("Attribute {} belongs to unknown class {} (owner table: {})",
                            iliName, ownerClassName, owner);
                        continue;
                    }
                    
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

                    EnumDomainInfo enumDomain = enumDomains.get(new EnumColumnKey(classMetadata.getTableName(), sqlName));
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
    }

    private Map<EnumColumnKey, EnumDomainInfo> loadEnumDomains() throws SQLException {
        ColumnPropColumns columns = resolveColumnPropColumns();
        if (columns == null) {
            return Collections.emptyMap();
        }
        String sql = buildQuery(String.format(
            "SELECT cp.%s AS owner, cp.%s AS columnname, cp.setting AS enumIliName, cn.sqlname AS enumTable " +
                "FROM {schema}.t_ili2db_column_prop cp " +
                "LEFT JOIN {schema}.t_ili2db_classname cn ON cp.setting = cn.iliname " +
                "WHERE cp.tag = ?",
            columns.ownerColumn(),
            columns.columnColumn()
        ));
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
                    enumDomains.put(new EnumColumnKey(owner, columnName),
                        new EnumDomainInfo(enumIliName, enumTable));
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not read enum domains from column properties", e);
        }
        return enumDomains;
    }

    private ColumnPropColumns resolveColumnPropColumns() throws SQLException {
        String sql = buildQuery("SELECT * FROM {schema}.t_ili2db_column_prop WHERE 1=0");
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
        String sql = buildQuery("SELECT * FROM {schema}." + enumTableName);
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
        String sql =
            "SELECT column_name, data_type, is_nullable, character_maximum_length, " +
            "       numeric_precision, numeric_scale, type_name " +
            "FROM information_schema.columns " +
            "WHERE upper(table_schema) = upper(?) " +
            "  AND upper(table_name) = upper(?) " +
            "  AND upper(column_name) = upper(?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, schemaName);
            pstmt.setString(2, tableName);
            pstmt.setString(3, columnName);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String dataType = rs.getString("data_type");
                    String isNullable = rs.getString("is_nullable");
                    Integer maxLength = rs.getInt("character_maximum_length");
                    if (rs.wasNull()) {
                        maxLength = null;
                    }

                    String typeName = rs.getString("type_name");
                    String resolvedType = resolveDbType(dataType, typeName);
                    attr.setDbType(resolvedType);
                    attr.setMandatory("NO".equals(isNullable));
                    attr.setMaxLength(maxLength);

                    if ("GEOMETRY".equalsIgnoreCase(typeName)) {
                        attr.setGeometry(true);
                    }

                    attr.setPrimaryKey(isPrimaryKey(tableName, columnName));
                }
            }
        }
    }

    private String resolveDbType(String dataType, String typeName) {
        if (dataType == null || dataType.isBlank()) {
            return typeName;
        }
        if (typeName != null && !typeName.isBlank() && dataType.matches("\\d+")) {
            return typeName;
        }
        if (dataType.matches("\\d+")) {
            String mappedType = mapSqlTypeCode(dataType, typeName);
            if (mappedType != null) {
                return mappedType;
            }
        }
        return dataType;
    }

    private String mapSqlTypeCode(String dataType, String typeName) {
        int typeCode;
        try {
            typeCode = Integer.parseInt(dataType);
        } catch (NumberFormatException e) {
            return null;
        }
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
        String sql =
            "SELECT 1 FROM information_schema.key_column_usage " +
            "WHERE table_schema = ? " +
            "  AND upper(table_name) = upper(?) " +
            "  AND upper(column_name) = upper(?) " +
            "  AND constraint_name LIKE '%_pkey'";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, schemaName);
            pstmt.setString(2, tableName);
            pstmt.setString(3, columnName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    /**
     * Liest die Vererbungshierarchie.
     */
    private void readInheritance(ModelMetadata metadata, Collection<String> modelNames) throws SQLException {
        List<String> prefixes = buildModelPrefixes(metadata, modelNames);
        String sql = buildQuery(
            "SELECT thisclass, baseclass FROM {schema}.t_ili2db_inheritance " +
            "WHERE " + buildLikeClause("thisclass", prefixes.size())
        );
        
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
        String sql = buildQuery(
            "SELECT tablename, columnname, tag, setting " +
            "FROM {schema}.t_ili2db_column_prop " +
            "ORDER BY tablename, columnname"
        );
        
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
            if (tableName.equals(clazz.getTableName())) {
                return clazz;
            }
        }
        return null;
    }
    
    private AttributeMetadata findAttributeByColumnName(ClassMetadata classMetadata, String columnName) {
        for (AttributeMetadata attr : classMetadata.getAllAttributes()) {
            if (columnName.equals(attr.getColumnName())) {
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
                    rel.setSourceAttribute(attr.getName());
                    rel.setTargetAttribute("T_Id"); // ili2db Standard
                    rel.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
                    rel.setMandatory(attr.isMandatory());
                    
                    classMetadata.addRelationship(rel);
                }
            }
        }
    }
    
    private String buildQuery(String template) {
        return template.replace("{schema}", schemaName);
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

    private Set<String> resolveRelevantModelNames(String requestedModel) {
        Set<String> modelNames = new LinkedHashSet<>();
        if (requestedModel != null && !requestedModel.isBlank()) {
            modelNames.add(requestedModel);
        }

        String sql = buildQuery("SELECT * FROM {schema}.t_ili2db_model");
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            String modelColumn = findColumn(meta, "modelname", "model", "name");
            if (modelColumn == null) {
                logger.warn("Could not detect model name column in t_ili2db_model, using requested model only.");
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
                modelNames.add(modelName);
                logger.debug("Detected ili2db model: {}", modelName);
            }
        } catch (SQLException e) {
            logger.warn("Could not read model list from t_ili2db_model, using requested model only.", e);
        }

        return modelNames;
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

    private void ensureSchemaName() {
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException("schemaName must be provided for ili2db metadata lookup.");
        }
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
    }

    private record EnumDomainInfo(String enumIliName, String enumTableName) {
    }

    private record ColumnPropColumns(String ownerColumn, String columnColumn) {
    }
}
