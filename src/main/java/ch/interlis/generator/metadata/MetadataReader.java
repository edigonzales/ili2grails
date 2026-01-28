package ch.interlis.generator.metadata;

import ch.interlis.generator.model.*;
import ch.interlis.generator.reader.Ili2cModelReader;
import ch.interlis.generator.reader.Ili2dbMetadataReader;
import ch.interlis.ili2c.Ili2cFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Kombiniert Metadaten aus ili2db-Datenbank und ili2c-Modell.
 * 
 * Strategie:
 * 1. Basis-Struktur aus ili2db-Metatabellen lesen
 * 2. Semantische Informationen aus ili2c-Modell anreichern
 */
public class MetadataReader {
    
    private static final Logger logger = LoggerFactory.getLogger(MetadataReader.class);
    
    private final Connection connection;
    private final File modelFile;
    private final String schemaName;
    private final List<String> modelDirs;
    
    public MetadataReader(Connection connection, File modelFile) {
        this(connection, modelFile, null, null);
    }
    
    public MetadataReader(Connection connection, File modelFile, String schemaName, 
                         List<String> modelDirs) {
        this.connection = connection;
        this.modelFile = modelFile;
        this.schemaName = schemaName;
        this.modelDirs = modelDirs;
    }
    
    /**
     * Liest vollständige Metadaten für ein Modell.
     * Kombiniert ili2db-Datenbank und ili2c-Modell.
     */
    public ModelMetadata readMetadata(String modelName) throws SQLException, Ili2cFailure {
        logger.info("Reading combined metadata for model: {}", modelName);
        
        // ili2db Metatabellen lesen (Basis-Struktur)
        logger.info("Reading ili2db metadata from database");
        Ili2dbMetadataReader ili2dbReader = new Ili2dbMetadataReader(connection, schemaName);
        ModelMetadata metadata = ili2dbReader.readMetadata(modelName);
        
        // ili2c Modell lesen (Semantische Anreicherung)
        if (modelFile != null && modelFile.exists()) {
            logger.info("Enriching with ili2c model information");
            enrichFromIli2cModel(metadata, modelName);
        } else {
            logger.warn("No model file provided or file does not exist. " +
                       "Skipping ili2c enrichment.");
        }
        
        // Nachbearbeitung
        logger.info("Post-processing metadata");
        postProcess(metadata);
        
        logger.info("Metadata reading complete");
        return metadata;
    }
    
    /**
     * Reichert die Metadaten mit Informationen aus dem ili2c-Modell an.
     */
    private void enrichFromIli2cModel(ModelMetadata metadata, String modelName) 
            throws Ili2cFailure {
        
        Ili2cModelReader ili2cReader = modelDirs != null ? 
            new Ili2cModelReader(modelFile, modelDirs) : 
            new Ili2cModelReader(modelFile);
        
        ModelMetadata ili2cMetadata = ili2cReader.readMetadata(modelName);
        
        // ILI-Version
        if (ili2cMetadata.getIliVersion() != null) {
            metadata.setIliVersion(ili2cMetadata.getIliVersion());
        }
        
        // Klassen anreichern
        for (ClassMetadata ili2cClass : ili2cMetadata.getAllClasses()) {
            ClassMetadata dbClass = metadata.getClass(ili2cClass.getName());
            
            if (dbClass != null) {
                // Informationen von ili2c übernehmen
                enrichClass(dbClass, ili2cClass);
            } else {
                // Klasse existiert nur im Modell (z.B. abstrakte Klasse ohne Tabelle)
                logger.debug("Class {} exists in model but not in database (abstract?)", 
                    ili2cClass.getName());
            }
        }
        
        // Enumerationen übernehmen
        for (EnumMetadata enumMetadata : ili2cMetadata.getAllEnums()) {
            metadata.addEnum(enumMetadata);
        }
    }
    
    /**
     * Reichert eine Klasse mit ili2c-Informationen an.
     */
    private void enrichClass(ClassMetadata dbClass, ClassMetadata ili2cClass) {
        // Dokumentation
        if (ili2cClass.getDocumentation() != null) {
            dbClass.setDocumentation(ili2cClass.getDocumentation());
        }
        
        // Typ (CLASS, STRUCTURE, ASSOCIATION)
        if (ili2cClass.getKind() != null) {
            dbClass.setKind(ili2cClass.getKind());
        }
        
        // Abstract
        dbClass.setAbstract(ili2cClass.isAbstract());
        
        // Labels
        dbClass.getLabels().putAll(ili2cClass.getLabels());
        
        // Attribute anreichern
        for (AttributeMetadata ili2cAttr : ili2cClass.getAllAttributes()) {
            AttributeMetadata dbAttr = findAttribute(dbClass, ili2cAttr);
            
            if (dbAttr != null) {
                enrichAttribute(dbAttr, ili2cAttr);
            } else {
                String displayName = ili2cAttr.getQualifiedName() != null
                    ? ili2cAttr.getQualifiedName()
                    : ili2cAttr.getName();
                logger.debug("Attribute {} exists in model but not in database", displayName);
            }
        }
    }

    private AttributeMetadata findAttribute(ClassMetadata dbClass, AttributeMetadata ili2cAttr) {
        String qualifiedName = ili2cAttr.getQualifiedName();
        if (qualifiedName != null) {
            for (AttributeMetadata dbAttr : dbClass.getAllAttributes()) {
                if (qualifiedName.equals(dbAttr.getQualifiedName())) {
                    return dbAttr;
                }
            }
        }
        String simpleName = ili2cAttr.getName();
        if (simpleName != null) {
            AttributeMetadata direct = dbClass.getAttribute(simpleName);
            if (direct != null) {
                return direct;
            }
        }
        return null;
    }
    
    /**
     * Reichert ein Attribut mit ili2c-Informationen an.
     */
    private void enrichAttribute(AttributeMetadata dbAttr, AttributeMetadata ili2cAttr) {
        // Dokumentation
        if (ili2cAttr.getDocumentation() != null) {
            dbAttr.setDocumentation(ili2cAttr.getDocumentation());
        }
        
        // ILI-Typ
        if (ili2cAttr.getIliType() != null) {
            dbAttr.setIliType(ili2cAttr.getIliType());
        }
        
        // Constraints
        if (ili2cAttr.getMaxLength() != null && dbAttr.getMaxLength() == null) {
            dbAttr.setMaxLength(ili2cAttr.getMaxLength());
        }
        
        if (ili2cAttr.getMinValue() != null) {
            dbAttr.setMinValue(ili2cAttr.getMinValue());
        }
        
        if (ili2cAttr.getMaxValue() != null) {
            dbAttr.setMaxValue(ili2cAttr.getMaxValue());
        }
        
        // Enum-Typ
        if (ili2cAttr.getEnumType() != null) {
            dbAttr.setEnumType(ili2cAttr.getEnumType());
        }
        
        // Unit
        if (ili2cAttr.getUnit() != null) {
            dbAttr.setUnit(ili2cAttr.getUnit());
        }
        
        // Geometrie
        if (ili2cAttr.isGeometry()) {
            dbAttr.setGeometry(true);
        }
        
        // Labels
        dbAttr.getLabels().putAll(ili2cAttr.getLabels());
    }
    
    /**
     * Nachbearbeitung: Java-Typen ableiten, Validierung, etc.
     */
    private void postProcess(ModelMetadata metadata) {
        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            for (AttributeMetadata attr : classMetadata.getAllAttributes()) {
                // Java-Typ ableiten falls noch nicht gesetzt
                if (attr.getJavaType() == null) {
                    attr.inferJavaType();
                }
            }
        }
        
        logger.debug("Post-processing complete");
    }
}
