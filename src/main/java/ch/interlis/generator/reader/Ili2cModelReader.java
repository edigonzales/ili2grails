package ch.interlis.generator.reader;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.Ili2c;
import ch.interlis.ili2c.Ili2cFailure;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.config.FileEntry;
import ch.interlis.ili2c.config.FileEntryKind;
import ch.interlis.ili2c.metamodel.*;
import ch.interlis.generator.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * Liest ein INTERLIS-Modell mit ili2c und extrahiert semantische Metadaten.
 * Diese können verwendet werden, um die Informationen aus ili2db anzureichern.
 */
public class Ili2cModelReader {
    
    private static final Logger logger = LoggerFactory.getLogger(Ili2cModelReader.class);
    
    private final File modelFile;
    private final List<String> modelDirs;
    private TransferDescription td;
    
    public Ili2cModelReader(File modelFile) {
        this.modelFile = modelFile;
        this.modelDirs = new ArrayList<>();
        // Standard INTERLIS-Repository
        this.modelDirs.add("http://models.interlis.ch/");
    }
    
    public Ili2cModelReader(File modelFile, List<String> modelDirs) {
        this.modelFile = modelFile;
        this.modelDirs = new ArrayList<>(modelDirs);
        if (!this.modelDirs.contains("http://models.interlis.ch/")) {
            this.modelDirs.add("http://models.interlis.ch/");
        }
    }
    
    /**
     * Kompiliert das INTERLIS-Modell und erstellt eine TransferDescription.
     */
    public TransferDescription compileModel() throws Ili2cFailure {
        logger.info("Compiling INTERLIS model: {}", modelFile.getAbsolutePath());
        
        Configuration config = new Configuration();
        
        // Modell-Datei hinzufügen
        FileEntry fileEntry = new FileEntry(
            modelFile.getAbsolutePath(), 
            FileEntryKind.ILIMODELFILE
        );
        config.addFileEntry(fileEntry);
        
        // Modell-Verzeichnisse hinzufügen
        for (String dir : modelDirs) {
            config.addFileEntry(new FileEntry(dir, FileEntryKind.ILIMODELFILE));
        }
        
        // Kompilieren
        td = Ili2c.runCompiler(config);
        
        if (td == null) {
            throw new Ili2cFailure("Failed to compile INTERLIS model");
        }
        
        logger.info("Model compilation successful");
        return td;
    }
    
    /**
     * Liest Metadaten aus dem kompilierten Modell.
     */
    public ModelMetadata readMetadata(String modelName) throws Ili2cFailure {
        if (td == null) {
            compileModel();
        }
        
        logger.info("Reading metadata from ili2c model: {}", modelName);
        
        Model model = (Model) td.getElement(Model.class, modelName);
        if (model == null) {
            throw new IllegalArgumentException("Model not found: " + modelName);
        }
        
        ModelMetadata metadata = new ModelMetadata(modelName);
        metadata.setIliVersion(model.getIliVersion());
        
        // Topics durchgehen
        Iterator<?> topicIterator = model.iterator();
        while (topicIterator.hasNext()) {
            Object element = topicIterator.next();
            
            if (element instanceof Topic) {
                processTopic(metadata, (Topic) element);
            } else if (element instanceof Domain) {
                processDomain(metadata, (Domain) element);
            }
        }
        
        logger.info("ili2c metadata reading complete: {} classes", metadata.getClasses().size());
        return metadata;
    }
    
    /**
     * Verarbeitet ein Topic und extrahiert Klassen.
     */
    private void processTopic(ModelMetadata metadata, Topic topic) {
        logger.debug("Processing topic: {}", topic.getName());
        
        Iterator<?> iterator = topic.iterator();
        while (iterator.hasNext()) {
            Object element = iterator.next();
            
            if (element instanceof Table) {
                processTable(metadata, (Table) element);
            } else if (element instanceof Domain) {
                processDomain(metadata, (Domain) element);
            }
        }
    }
    
    /**
     * Verarbeitet eine Tabelle/Klasse.
     */
    private void processTable(ModelMetadata metadata, Table table) {
        String qualifiedName = table.getScopedName(null);
        logger.debug("Processing table: {}", qualifiedName);
        
        // Klasse aus ili2db-Metadaten holen oder neu erstellen
        ClassMetadata classMetadata = metadata.getClass(qualifiedName);
        if (classMetadata == null) {
            classMetadata = new ClassMetadata(qualifiedName);
            metadata.addClass(classMetadata);
        }
        
        // Typ setzen
        if (table instanceof AssociationDef) {
            classMetadata.setKind(ClassMetadata.ClassKind.ASSOCIATION);
        } else {
            classMetadata.setKind(ClassMetadata.ClassKind.CLASS);
        }
        
        // Abstract
        classMetadata.setAbstract(table.isAbstract());
        
        // Dokumentation
        if (table.getDocumentation() != null) {
            classMetadata.setDocumentation(table.getDocumentation());
        }
        
        // Vererbung
        if (table.getExtending() != null) {
            String baseClassName = table.getExtending().getScopedName(null);
            classMetadata.setBaseClass(baseClassName);
        }
        
        // Attribute verarbeiten
        Iterator<?> attrIterator = table.getAttributes();
        while (attrIterator.hasNext()) {
            AttributeDef attrDef = (AttributeDef) attrIterator.next();
            processAttribute(classMetadata, attrDef);
        }
    }
    
    /**
     * Verarbeitet ein Attribut.
     */
    private void processAttribute(ClassMetadata classMetadata, AttributeDef attrDef) {
        String attrName = attrDef.getName();
        logger.debug("  Processing attribute: {}", attrName);
        
        // Attribute aus ili2db-Metadaten holen oder neu erstellen
        AttributeMetadata attrMetadata = classMetadata.getAttribute(attrName);
        if (attrMetadata == null) {
            attrMetadata = new AttributeMetadata(attrName);
            classMetadata.addAttribute(attrMetadata);
        }
        
        // Dokumentation
        if (attrDef.getDocumentation() != null) {
            attrMetadata.setDocumentation(attrDef.getDocumentation());
        }
        
        // Typ-Informationen
        Type type = attrDef.getDomain();
        if (type != null) {
            processType(attrMetadata, type);
        }
        
        // Mandatory
        attrMetadata.setMandatory(!attrDef.isDomainBoolean());
    }
    
    /**
     * Verarbeitet Typ-Informationen.
     */
    private void processType(AttributeMetadata attr, Type type) {
        if (type instanceof TypeAlias) {
            // Alias auflösen
            Type aliasing = ((TypeAlias) type).getAliasing();
            processType(attr, aliasing);
            return;
        }
        
        // INTERLIS-Typ setzen
        String typeName = type.getClass().getSimpleName();
        attr.setIliType(typeName);
        
        if (type instanceof TextType) {
            TextType textType = (TextType) type;
            if (textType.getMaxLength() > 0) {
                attr.setMaxLength(textType.getMaxLength());
            }
        } else if (type instanceof NumericType) {
            NumericType numType = (NumericType) type;
            if (numType.getMinimum() != null) {
                attr.setMinValue(numType.getMinimum().toString());
            }
            if (numType.getMaximum() != null) {
                attr.setMaxValue(numType.getMaximum().toString());
            }
        } else if (type instanceof EnumerationType) {
            EnumerationType enumType = (EnumerationType) type;
            // Enum-Name speichern
            if (enumType.getConsolidatedEnumeration() != null) {
                attr.setEnumType(enumType.getConsolidatedEnumeration().getName());
            }
        } else if (type instanceof CoordType || type instanceof MultiCoordType) {
            attr.setGeometry(true);
        } else if (type instanceof LineType || type instanceof PolylineType || 
                   type instanceof SurfaceType || type instanceof AreaType) {
            attr.setGeometry(true);
        }
        
        // Unit
        if (type instanceof NumericalType) {
            NumericalType numType = (NumericalType) type;
            if (numType.getUnit() != null) {
                attr.setUnit(numType.getUnit().getName());
            }
        }
    }
    
    /**
     * Verarbeitet eine Domain (z.B. Enumerationen).
     */
    private void processDomain(ModelMetadata metadata, Domain domain) {
        Type type = domain.getType();
        
        if (type instanceof EnumerationType) {
            processEnumeration(metadata, domain.getScopedName(null), (EnumerationType) type);
        }
    }
    
    /**
     * Verarbeitet eine Enumeration.
     */
    private void processEnumeration(ModelMetadata metadata, String name, EnumerationType enumType) {
        logger.debug("Processing enumeration: {}", name);
        
        EnumMetadata enumMetadata = new EnumMetadata(name);
        enumMetadata.setExtendable(enumType.isExtendable());
        
        // Enum-Werte extrahieren
        Enumeration enumeration = enumType.getConsolidatedEnumeration();
        if (enumeration != null) {
            extractEnumValues(enumMetadata, enumeration, 0);
        }
        
        metadata.addEnum(enumMetadata);
    }
    
    /**
     * Extrahiert Enum-Werte rekursiv.
     */
    private int extractEnumValues(EnumMetadata enumMetadata, Enumeration enumeration, int seq) {
        Iterator<?> iterator = enumeration.getElements();
        
        while (iterator.hasNext()) {
            Enumeration.Element element = (Enumeration.Element) iterator.next();
            String name = element.getName();
            
            EnumMetadata.EnumValue value = new EnumMetadata.EnumValue(name, seq++);
            enumMetadata.addValue(value);
            
            // Hierarchische Enums rekursiv verarbeiten
            if (element.getSubEnumeration() != null && 
                element.getSubEnumeration().getElements().hasNext()) {
                seq = extractEnumValues(enumMetadata, element.getSubEnumeration(), seq);
            }
        }
        
        return seq;
    }
    
    /**
     * Gibt die TransferDescription zurück (nach dem Kompilieren).
     */
    public TransferDescription getTransferDescription() {
        return td;
    }
}
