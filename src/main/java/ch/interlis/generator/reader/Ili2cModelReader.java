package ch.interlis.generator.reader;

import ch.interlis.ili2c.Ili2c;
import ch.interlis.ili2c.Ili2cFailure;
import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.config.FileEntry;
import ch.interlis.ili2c.config.FileEntryKind;
import ch.interlis.ili2c.metamodel.AbstractClassDef;
import ch.interlis.ili2c.metamodel.AreaType;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.CoordType;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.LineType;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.MultiCoordType;
import ch.interlis.ili2c.metamodel.NumericType;
import ch.interlis.ili2c.metamodel.NumericalType;
import ch.interlis.ili2c.metamodel.PolylineType;
import ch.interlis.ili2c.metamodel.SurfaceType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.TextType;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.TypeAlias;
import ch.interlis.generator.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
        this.modelDirs.add("https://models.interlis.ch/");
    }
    
    public Ili2cModelReader(File modelFile, List<String> modelDirs) {
        this.modelFile = modelFile;
        this.modelDirs = new ArrayList<>(modelDirs);
        if (!this.modelDirs.contains("https://models.interlis.ch/")) {
            this.modelDirs.add("https://models.interlis.ch/");
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
//        for (String dir : modelDirs) {
//            config.addFileEntry(new FileEntry(dir, FileEntryKind.ILIMODELFILE));
//        }
        
        
        Ili2cSettings set = new Ili2cSettings();
        ch.interlis.ili2c.Main.setDefaultIli2cPathMap(set);
        set.setIlidirs(Ili2cSettings.DEFAULT_ILIDIRS);

//        String repos = settings.getModelRepositories();
//        if (repos != null && !repos.isBlank()) {
//            set.setIlidirs(repos);
//        } else {
//            set.setIlidirs(Ili2cSettings.DEFAULT_ILIDIRS);
//        }
        
        config.setAutoCompleteModelList(true);
        config.setGenerateWarnings(true);
        
        // Kompilieren
        td = ch.interlis.ili2c.Main.runCompiler(config, set, null);
        
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
                processClassDef(metadata, (AbstractClassDef<?>) element);
            } else if (element instanceof ch.interlis.ili2c.metamodel.AssociationDef) {
                processClassDef(metadata, (AbstractClassDef<?>) element);
            } else if (element instanceof Domain) {
                processDomain(metadata, (Domain) element);
            }
        }
    }
    
    /**
     * Verarbeitet eine Tabelle/Klasse.
     */
    private void processClassDef(ModelMetadata metadata, AbstractClassDef<?> classDef) {
        String qualifiedName = classDef.getScopedName(null);
        logger.debug("Processing table: {}", qualifiedName);
        
        // Klasse aus ili2db-Metadaten holen oder neu erstellen
        ClassMetadata classMetadata = metadata.getClass(qualifiedName);
        if (classMetadata == null) {
            classMetadata = new ClassMetadata(qualifiedName);
            metadata.addClass(classMetadata);
        }
        
        // Typ setzen
        if (classDef instanceof ch.interlis.ili2c.metamodel.AssociationDef) {
            classMetadata.setKind(ClassMetadata.ClassKind.ASSOCIATION);
        } else {
            classMetadata.setKind(ClassMetadata.ClassKind.CLASS);
        }
        
        // Abstract
        classMetadata.setAbstract(classDef.isAbstract());
        
        // Dokumentation
        if (classDef.getDocumentation() != null) {
            classMetadata.setDocumentation(classDef.getDocumentation());
        }
        
        // Vererbung
        if (classDef.getExtending() != null) {
            String baseClassName = classDef.getExtending().getScopedName(null);
            classMetadata.setBaseClass(baseClassName);
        }
        
        // Attribute verarbeiten
        Iterator<?> attrIterator = classDef.getAttributes();
        while (attrIterator.hasNext()) {
            Object attribute = attrIterator.next();
            if (attribute instanceof AttributeDef attrDef) {
                processAttribute(classMetadata, attrDef);
            }
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
        if (attrDef.getCardinality() != null) {
            attrMetadata.setMandatory(attrDef.getCardinality().getMinimum() > 0);
        }
    }
    
    /**
     * Verarbeitet Typ-Informationen.
     */
    private void processType(AttributeMetadata attr, Type type) {
        if (type instanceof TypeAlias) {
            // Alias auflösen
            Domain aliasing = ((TypeAlias) type).getAliasing();
            if (aliasing != null) {
                if (aliasing.getType() instanceof EnumerationType) {
                    attr.setEnumType(aliasing.getScopedName(null));
                }
                processType(attr, aliasing.getType());
            }
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
                attr.setEnumType(attr.getEnumType());
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
            processEnumeration(metadata, domain, (EnumerationType) type);
        }
    }
    
    /**
     * Verarbeitet eine Enumeration.
     */
    private void processEnumeration(ModelMetadata metadata, Domain domain, EnumerationType enumType) {
        String name = domain.getScopedName(null);
        logger.debug("Processing enumeration: {}", name);

        EnumMetadata enumMetadata = new EnumMetadata(name);
        enumMetadata.setExtendable(!domain.isFinal());
        if (domain.getExtending() != null) {
            enumMetadata.setBaseEnum(domain.getExtending().getScopedName(null));
        }
        
        // Enum-Werte extrahieren
        ch.interlis.ili2c.metamodel.Enumeration enumeration = enumType.getConsolidatedEnumeration();
        if (enumeration != null) {
            extractEnumValues(enumMetadata, enumeration, 0);
        }
        
        metadata.addEnum(enumMetadata);
    }
    
    /**
     * Extrahiert Enum-Werte rekursiv.
     */
    private int extractEnumValues(EnumMetadata enumMetadata,
                                  ch.interlis.ili2c.metamodel.Enumeration enumeration,
                                  int seq) {
        Iterator<?> iterator = enumeration.getElements();
        
        while (iterator.hasNext()) {
            ch.interlis.ili2c.metamodel.Enumeration.Element element =
                (ch.interlis.ili2c.metamodel.Enumeration.Element) iterator.next();
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
