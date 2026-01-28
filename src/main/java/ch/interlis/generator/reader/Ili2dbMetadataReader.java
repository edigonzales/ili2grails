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

    private static final String ATTR_OWNER_COLUMN = "colowner";
    private static final String ATTR_TARGET_COLUMN = "target";
    
    private final Connection connection;
    private String schemaName;
    
    public Ili2dbMetadataReader(Connection connection) {
        this.connection = connection;
    }
    
    public Ili2dbMetadataReader(Connection connection, String schemaName) {
        this.connection = connection;
        this.schemaName = schemaName;
    }
    
    /**
     * Liest die kompletten Metadaten für ein bestimmtes Modell.
     */
    public ModelMetadata readMetadata(String modelName) throws SQLException {
        logger.info("Reading ili2db metadata for model: {}", modelName);
        
        ModelMetadata metadata = new ModelMetadata(modelName);
        
        // Schema ermitteln falls nicht gesetzt
        if (schemaName == null) {
            schemaName = detectSchema();
        }
        metadata.setSchemaName(schemaName);
        
        // Settings lesen
        readSettings(metadata);
        
        // Klassen lesen
        readClasses(metadata, modelName);
        
        // Attribute lesen
        readAttributes(metadata);
        
        // Vererbung auflösen
        readInheritance(metadata);
        
        // Spalten-Properties lesen (Constraints, etc.)
        readColumnProperties(metadata);
        
        // Beziehungen ableiten
        deriveRelationships(metadata);
        
        logger.info("Metadata reading complete: {} classes, {} enums", 
            metadata.getClasses().size(), metadata.getEnums().size());
        
        return metadata;
    }
    
    /**
     * Erkennt das Schema, in dem die ili2db-Tabellen liegen.
     */
    private String detectSchema() throws SQLException {
        String sql = "SELECT table_schema FROM information_schema.tables " +
                     "WHERE table_name = 't_ili2db_classname' LIMIT 1";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            logger.warn("Could not detect schema, using 'public'", e);
        }
        return "public";
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
                
                if ("ch.ehi.ili2db.version".equals(tag)) {
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
    private void readClasses(ModelMetadata metadata, String modelName) throws SQLException {
        String sql = buildQuery(
            "SELECT iliname, sqlname FROM {schema}.t_ili2db_classname " +
            "WHERE iliname LIKE ? ORDER BY iliname"
        );
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, modelName + ".%");
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String iliName = rs.getString("iliname");
                    String sqlName = rs.getString("sqlname");
                    
                    ClassMetadata classMetadata = new ClassMetadata(iliName);
                    classMetadata.setTableName(sqlName);
                    classMetadata.setSqlName(schemaName + "." + sqlName);
                    
                    // Typ bestimmen (CLASS, STRUCTURE, ASSOCIATION)
                    // Das kann später aus dem ili2c-Modell verfeinert werden
                    classMetadata.setKind(ClassMetadata.ClassKind.CLASS);
                    
                    metadata.addClass(classMetadata);
                    
                    logger.debug("Found class: {} -> {}", iliName, sqlName);
                }
            }
        }
    }
    
    /**
     * Liest alle Attribute (Columns) für die Klassen.
     */
    private void readAttributes(ModelMetadata metadata) throws SQLException {
        String sql = buildQuery(String.format(
            "SELECT a.iliname, a.sqlname, a.%s AS owner, a.%s AS target " +
            "FROM {schema}.t_ili2db_attrname a " +
            "WHERE a.%s LIKE ? " +
            "ORDER BY a.%s, a.sqlname",
            ATTR_OWNER_COLUMN,
            ATTR_TARGET_COLUMN,
            ATTR_OWNER_COLUMN,
            ATTR_OWNER_COLUMN
        ));
        
        String modelPrefix = metadata.getModelName() + ".%";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, modelPrefix);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String iliName = rs.getString("iliname");
                    String sqlName = rs.getString("sqlname");
                    String owner = rs.getString("owner");
                    String target = rs.getString("target");
                    
                    ClassMetadata classMetadata = metadata.getClass(owner);
                    if (classMetadata == null) {
                        logger.warn("Attribute {} belongs to unknown class {}", iliName, owner);
                        continue;
                    }
                    
                    AttributeMetadata attrMetadata = new AttributeMetadata(iliName);
                    attrMetadata.setColumnName(sqlName);
                    attrMetadata.setSqlName(sqlName);
                    
                    // Ist es eine Beziehung (FK)?
                    if (target != null && !target.isEmpty()) {
                        attrMetadata.setForeignKey(true);
                        attrMetadata.setReferencedClass(target);
                    }
                    
                    // Datenbank-Typ und weitere Infos aus DB-Schema holen
                    enrichAttributeFromDbSchema(attrMetadata, classMetadata.getTableName(), sqlName);
                    
                    classMetadata.addAttribute(attrMetadata);
                    
                    logger.debug("  Attribute: {}.{} -> {}", 
                        classMetadata.getSimpleName(), iliName, sqlName);
                }
            }
        }
    }
    
    /**
     * Holt zusätzliche Informationen über ein Attribut aus dem DB-Schema.
     */
    private void enrichAttributeFromDbSchema(AttributeMetadata attr, String tableName, String columnName) 
            throws SQLException {
        String baseSql =
            "SELECT column_name, data_type, is_nullable, character_maximum_length, " +
            "       numeric_precision, numeric_scale%s " +
            "FROM information_schema.columns " +
            "WHERE table_schema = ? " +
            "  AND upper(table_name) = upper(?) " +
            "  AND upper(column_name) = upper(?)";

        boolean includeUdtName = true;
        boolean includeTypeName = true;
        SQLException lastError = null;

        for (int attempt = 0; attempt < 3; attempt++) {
            String extraColumns = "";
            if (includeUdtName) {
                extraColumns += ", udt_name";
            }
            if (includeTypeName) {
                extraColumns += ", type_name";
            }
            String sql = String.format(baseSql, extraColumns);
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

                        String typeName = includeTypeName ? rs.getString("type_name") : null;
                        String resolvedType = resolveDbType(dataType, typeName);
                        attr.setDbType(resolvedType);
                        attr.setMandatory("NO".equals(isNullable));
                        attr.setMaxLength(maxLength);

                        String udtName = includeUdtName ? rs.getString("udt_name") : null;
                        if ("geometry".equalsIgnoreCase(udtName) ||
                            "USER-DEFINED".equalsIgnoreCase(dataType)) {
                            attr.setGeometry(true);
                        }

                        attr.setPrimaryKey(isPrimaryKey(tableName, columnName));
                    }
                }
                return;
            } catch (SQLException e) {
                lastError = e;
                if (includeTypeName) {
                    includeTypeName = false;
                } else if (includeUdtName) {
                    includeUdtName = false;
                }
            }
        }

        if (lastError != null) {
            throw lastError;
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
    private void readInheritance(ModelMetadata metadata) throws SQLException {
        String sql = buildQuery(
            "SELECT thisclass, baseclass FROM {schema}.t_ili2db_inheritance " +
            "WHERE thisclass LIKE ?"
        );
        
        String modelPrefix = metadata.getModelName() + ".%";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, modelPrefix);
            
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
            case "ch.ehi.ili2db.enumDomain":
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
}
