package ch.interlis.generator.reader;

import ch.interlis.ili2c.Ili2cException;
import ch.interlis.ili2c.Ili2cFailure;
import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.config.FileEntry;
import ch.interlis.ili2c.config.FileEntryKind;
import ch.interlis.ili2c.metamodel.AbstractClassDef;
import ch.interlis.ili2c.metamodel.AreaType;
import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Cardinality;
import ch.interlis.ili2c.metamodel.CoordType;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.FormattedType;
import ch.interlis.ili2c.metamodel.LineType;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.MultiAreaType;
import ch.interlis.ili2c.metamodel.MultiCoordType;
import ch.interlis.ili2c.metamodel.MultiPolylineType;
import ch.interlis.ili2c.metamodel.MultiSurfaceType;
import ch.interlis.ili2c.metamodel.NumericType;
import ch.interlis.ili2c.metamodel.NumericalType;
import ch.interlis.ili2c.metamodel.ObjectType;
import ch.interlis.ili2c.metamodel.PolylineType;
import ch.interlis.ili2c.metamodel.PredefinedModel;
import ch.interlis.ili2c.metamodel.PrecisionDecimal;
import ch.interlis.ili2c.metamodel.ReferenceType;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.RoleDef;
import ch.interlis.ili2c.metamodel.SurfaceType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.TextType;
import ch.interlis.ili2c.metamodel.TextOIDType;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.TypeAlias;
import ch.interlis.generator.metadata.selection.ModelSelection;
import ch.interlis.generator.metadata.selection.ModelSelectionResolver;
import ch.interlis.generator.model.*;
import ch.interlis.ilirepository.IliManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Liest ein INTERLIS-Modell mit ili2c und extrahiert semantische Metadaten.
 * Diese können verwendet werden, um die Informationen aus ili2db anzureichern.
 */
public class Ili2cModelReader {
    
    private static final Logger logger = LoggerFactory.getLogger(Ili2cModelReader.class);
    private static final List<String> DEFAULT_MODEL_REPOSITORIES = List.of("https://models.interlis.ch/");
    
    private final File modelFile;
    private final List<String> modelDirs;
    private TransferDescription td;
    
    public Ili2cModelReader(File modelFile) {
        this(modelFile, null);
    }
    
    public Ili2cModelReader(File modelFile, List<String> modelDirs) {
        this.modelFile = modelFile;
        this.modelDirs = modelDirs != null ? new ArrayList<>(modelDirs) : null;
    }
    
    /**
     * Kompiliert das INTERLIS-Modell und erstellt eine TransferDescription.
     */
    public TransferDescription compileModel(String modelName) throws Ili2cFailure {
        if (modelFile != null && modelFile.exists()) {
            return compileModelFromFile();
        }
        return compileModelFromRepository(modelName);
    }

    private TransferDescription compileModelFromFile() throws Ili2cFailure {
        if (modelFile == null) {
            throw new Ili2cFailure("Model file is not set");
        }
        logger.info("Compiling INTERLIS model from file: {}", modelFile.getAbsolutePath());

        Configuration config = new Configuration();

        // Modell-Datei hinzufügen
        FileEntry fileEntry = new FileEntry(
            modelFile.getAbsolutePath(),
            FileEntryKind.ILIMODELFILE
        );
        config.addFileEntry(fileEntry);

        Ili2cSettings set = new Ili2cSettings();
        ch.interlis.ili2c.Main.setDefaultIli2cPathMap(set);
        String repos = resolveModelRepositories();
        if (repos != null && !repos.isBlank()) {
            set.setIlidirs(repos);
        } else {
            set.setIlidirs(Ili2cSettings.DEFAULT_ILIDIRS);
        }

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

    private TransferDescription compileModelFromRepository(String modelName) throws Ili2cFailure {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName");
        }
        List<String> repositories = resolveModelRepositoriesList();
        logger.info("Compiling INTERLIS model from repositories: {} (model: {})",
            repositories, modelName);

        IliManager iliManager = new IliManager();
        if (!repositories.isEmpty()) {
            iliManager.setRepositories(repositories.toArray(new String[0]));
        }

        ArrayList<String> models = new ArrayList<>();
        models.add(modelName);

        Configuration config;
        try {
            config = iliManager.getConfig(models, 0.0);
        } catch (Ili2cException e) {
            throw new Ili2cFailure("Failed to resolve model from repositories: " + modelName, e);
        }

        config.setAutoCompleteModelList(true);
        config.setGenerateWarnings(true);

        Ili2cSettings set = new Ili2cSettings();
        ch.interlis.ili2c.Main.setDefaultIli2cPathMap(set);
        String repos = resolveModelRepositories();
        if (repos != null && !repos.isBlank()) {
            set.setIlidirs(repos);
        } else {
            set.setIlidirs(Ili2cSettings.DEFAULT_ILIDIRS);
        }

        td = ch.interlis.ili2c.Main.runCompiler(config, set, null);

        if (td == null) {
            throw new Ili2cFailure("Failed to compile INTERLIS model from repositories");
        }

        logger.info("Model compilation successful");
        return td;
    }
    
    /**
     * Resultat eines kompletten ili2c-Lesedurchgangs: semantische Metadaten,
     * präzise Modellauswahl und die TransferDescription.
     */
    public record Ili2cReadResult(
        ModelMetadata metadata,
        ModelSelection modelSelection,
        TransferDescription transferDescription
    ) {
    }

    /**
     * Liest das Modell mit genau einem Kompilierdurchgang.
     * Das Modell wird nur einmal kompiliert; die TransferDescription wird nicht
     * doppelt aufgelöst.
     */
    public Ili2cReadResult read(String modelName) throws Ili2cFailure {
        if (td == null) {
            compileModel(modelName);
        }
        ModelSelection selection = new ModelSelectionResolver()
            .fromTransferDescription(td, modelName);
        ModelMetadata metadata = readMetadata(modelName);
        return new Ili2cReadResult(metadata, selection, td);
    }

    /**
     * Bestimmt die Modellauswahl aus der TransferDescription (kompiliert bei Bedarf).
     */
    public ModelSelection resolveModelSelection(String modelName) throws Ili2cFailure {
        if (td == null) {
            compileModel(modelName);
        }
        return new ModelSelectionResolver().fromTransferDescription(td, modelName);
    }

    /**
     * Liest Metadaten aus dem kompilierten Modell.
     */
    public ModelMetadata readMetadata(String modelName) throws Ili2cFailure {
        if (td == null) {
            compileModel(modelName);
        }
        
        logger.info("Reading metadata from ili2c model: {}", modelName);
        
        Model model = resolveModel(td, modelName);
        if (model == null) {
            throw new IllegalArgumentException("Model not found: " + modelName);
        }
        
        ModelMetadata metadata = new ModelMetadata(modelName);
        metadata.setIliVersion(model.getIliVersion());
        metadata.setModelVersion(model.getModelVersion());
        
        // Topics durchgehen
        Set<String> processedTopics = new HashSet<>();
        Set<String> processedClasses = new HashSet<>();
        Iterator<?> topicIterator = model.iterator();
        while (topicIterator.hasNext()) {
            Object element = topicIterator.next();
            
            if (element instanceof Topic) {
                processTopic(metadata, (Topic) element, processedTopics, processedClasses);
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
    private void processTopic(ModelMetadata metadata, Topic topic,
                              Set<String> processedTopics,
                              Set<String> processedClasses) {
        Objects.requireNonNull(topic, "topic");
        String topicName = topic.getScopedName(null);
        if (topicName != null && processedTopics.contains(topicName)) {
            return;
        }
        if (topicName != null) {
            processedTopics.add(topicName);
        }

        Object extending = topic.getExtending();
        if (extending instanceof Topic) {
            processTopic(metadata, (Topic) extending, processedTopics, processedClasses);
        }

        logger.debug("Processing topic: {}", topic.getName());
        
        Iterator<?> iterator = topic.iterator();
        while (iterator.hasNext()) {
            Object element = iterator.next();

            if (element instanceof Table) {
                processClassDef(metadata, (AbstractClassDef<?>) element, processedClasses);
            } else if (element instanceof AssociationDef) {
                processClassDef(metadata, (AbstractClassDef<?>) element, processedClasses);
            } else if (element instanceof Domain) {
                processDomain(metadata, (Domain) element);
            }
        }
    }
    
    /**
     * Verarbeitet eine Tabelle/Klasse.
     */
    private void processClassDef(ModelMetadata metadata,
                                 AbstractClassDef<?> classDef,
                                 Set<String> processedClasses) {
        String qualifiedName = classDef.getScopedName(null);
        if (qualifiedName != null && processedClasses.contains(qualifiedName)) {
            return;
        }
        AbstractClassDef<?> baseClass = null;
        Object extending = classDef.getExtending();
        if (extending instanceof AbstractClassDef<?>) {
            baseClass = (AbstractClassDef<?>) extending;
            processClassDef(metadata, baseClass, processedClasses);
        }
        if (qualifiedName != null) {
            processedClasses.add(qualifiedName);
        }
        logger.debug("Processing table: {}", qualifiedName);
        
        // Klasse aus ili2db-Metadaten holen oder neu erstellen
        ClassMetadata classMetadata = metadata.getClass(qualifiedName);
        if (classMetadata == null) {
            classMetadata = new ClassMetadata(qualifiedName);
            metadata.addClass(classMetadata);
        }
        
        // Typ setzen
        if (classDef instanceof AssociationDef) {
            classMetadata.setKind(ClassMetadata.ClassKind.ASSOCIATION);
        } else if (classDef instanceof Table table && !table.isIdentifiable()) {
            classMetadata.setKind(ClassMetadata.ClassKind.STRUCTURE);
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
        if (baseClass != null) {
            String baseClassName = baseClass.getScopedName(null);
            classMetadata.setBaseClass(baseClassName);
        }
        
        // Attribute verarbeiten
        Iterator<?> attrIterator = classDef.getAttributes();
        while (attrIterator.hasNext()) {
            Object attribute = attrIterator.next();
            if (attribute instanceof AttributeDef attrDef) {
                processAttribute(metadata, classMetadata, attrDef);
            }
        }

        if (classDef instanceof AssociationDef associationDef) {
            processAssociationRoles(metadata, classMetadata, associationDef);
        }
    }
    
    /**
     * Verarbeitet ein Attribut.
     */
    private void processAttribute(ModelMetadata metadata, ClassMetadata classMetadata, AttributeDef attrDef) {
        String attrName = attrDef.getName();
        String qualifiedName = attrDef.getScopedName(null);
        logger.debug("  Processing attribute: {}", attrName);
        
        // Attribute aus ili2db-Metadaten holen oder neu erstellen
        AttributeMetadata attrMetadata = classMetadata.getAttribute(attrName);
        if (attrMetadata == null) {
            attrMetadata = new AttributeMetadata(attrName);
            classMetadata.addAttribute(attrMetadata);
        }
        if (qualifiedName != null) {
            attrMetadata.setQualifiedName(qualifiedName);
        }
        
        // Dokumentation
        if (attrDef.getDocumentation() != null) {
            attrMetadata.setDocumentation(attrDef.getDocumentation());
        }
        
        // Typ-Informationen
        Type type = attrDef.getDomain();
        if (type != null) {
            processType(metadata, classMetadata, attrMetadata, attrDef, type);
        }
        
        // Mandatory
        if (attrDef.getCardinality() != null) {
            attrMetadata.setMandatory(attrDef.getCardinality().getMinimum() > 0);
            attrMetadata.setCardinalityMin(toCardinalityBound(attrDef.getCardinality().getMinimum()));
            attrMetadata.setCardinalityMax(toCardinalityBound(attrDef.getCardinality().getMaximum()));
        }
    }
    
    /**
     * Verarbeitet Typ-Informationen.
     */
    private void processType(ModelMetadata metadata,
                             ClassMetadata classMetadata,
                             AttributeMetadata attr,
                             AttributeDef attrDef,
                             Type type) {
        if (type instanceof TypeAlias) {
            // Alias auflösen
            Domain aliasing = ((TypeAlias) type).getAliasing();
            if (aliasing != null) {
                attr.setDomainName(aliasing.getScopedName(null));
                if (aliasing.getType() instanceof EnumerationType) {
                    attr.setEnumType(aliasing.getScopedName(null));
                }
                processType(metadata, classMetadata, attr, attrDef, aliasing.getType());
            }
            return;
        }
        
        // INTERLIS-Typ setzen
        String typeName = type.getClass().getSimpleName();
        attr.setIliType(typeName);
        attr.setOrdered(type.isOrdered());
        if (type.getCardinality() != null) {
            attr.setCardinalityMin(toCardinalityBound(type.getCardinality().getMinimum()));
            attr.setCardinalityMax(toCardinalityBound(type.getCardinality().getMaximum()));
        }
        
        if (type instanceof TextType) {
            TextType textType = (TextType) type;
            if (textType.getMaxLength() > 0) {
                attr.setMaxLength(textType.getMaxLength());
            }
            attr.setCoreType(textType.isNormalized() ? CoreType.TEXT : CoreType.MTEXT);
            attr.setJavaType("String");
        } else if (type instanceof NumericType) {
            NumericType numType = (NumericType) type;
            if (numType.getMinimum() != null) {
                attr.setMinValue(numType.getMinimum().toString());
            }
            if (numType.getMaximum() != null) {
                attr.setMaxValue(numType.getMaximum().toString());
            }
            attr.setPrecision(resolveNumericPrecision(numType));
            attr.setScale(resolveNumericScale(numType));
            attr.setCoreType(CoreType.NUMERIC);
            attr.setJavaType(resolveNumericJavaType(numType));
        } else if (type instanceof EnumerationType) {
            EnumerationType enumType = (EnumerationType) type;
            // Enum-Name speichern
            if (enumType.getConsolidatedEnumeration() != null) {
                attr.setEnumType(attr.getEnumType());
            }
            if (attrDef != null && attrDef.isDomainBoolean()) {
                attr.setCoreType(CoreType.BOOLEAN);
                attr.setJavaType("Boolean");
            } else {
                attr.setCoreType(CoreType.ENUM);
                attr.setJavaType("String");
            }
        } else if (type instanceof FormattedType formattedType) {
            attr.setCoreType(resolveFormattedCoreType(formattedType));
            attr.setJavaType(resolveFormattedJavaType(formattedType));
        } else if (type instanceof ObjectType) {
            attr.setCoreType(CoreType.OBJECT);
            attr.setJavaType("Object");
        } else if (type instanceof CoordType || type instanceof MultiCoordType) {
            attr.setGeometry(true);
            attr.setGeometryKind(type instanceof MultiCoordType ? GeometryKind.MULTIPOINT : GeometryKind.POINT);
            setGeometryDimensionHints(attr, type);
            attr.setAllowEmptyGeometry(false);
            attr.setCoreType(CoreType.COORD);
            attr.setJavaType("org.locationtech.jts.geom.Geometry");
        } else if (type instanceof LineType || type instanceof PolylineType || 
                   type instanceof SurfaceType || type instanceof AreaType) {
            attr.setGeometry(true);
            attr.setGeometryKind(type instanceof SurfaceType || type instanceof AreaType
                ? GeometryKind.POLYGON
                : GeometryKind.LINESTRING);
            setGeometryDimensionHints(attr, type);
            attr.setAllowEmptyGeometry(false);
            attr.setCoreType(type instanceof SurfaceType || type instanceof AreaType
                ? CoreType.SURFACE
                : CoreType.POLYLINE);
            attr.setJavaType("org.locationtech.jts.geom.Geometry");
        } else if (type instanceof MultiPolylineType || type instanceof MultiSurfaceType
                   || type instanceof MultiAreaType) {
            attr.setGeometry(true);
            if (type instanceof MultiSurfaceType || type instanceof MultiAreaType) {
                attr.setGeometryKind(GeometryKind.MULTIPOLYGON);
                attr.setCoreType(CoreType.SURFACE);
            } else {
                attr.setGeometryKind(GeometryKind.MULTILINESTRING);
                attr.setCoreType(CoreType.POLYLINE);
            }
            setGeometryDimensionHints(attr, type);
            attr.setAllowEmptyGeometry(false);
            attr.setJavaType("org.locationtech.jts.geom.Geometry");
        } else if (type instanceof ReferenceType referenceType) {
            attr.setCoreType(CoreType.REFERENCE);
            AbstractClassDef target = referenceType.getReferred();
            if (target != null) {
                attr.setJavaType(target.getName());
                attr.setReferencedClass(target.getScopedName(null));
                addReferenceRelationship(metadata, classMetadata, attr, attrDef, target, referenceType);
            }
        } else if (type instanceof CompositionType compositionType) {
            attr.setCoreType(CoreType.COMPOSITION);
            AbstractClassDef target = compositionType.getComponentType();
            if (target != null) {
                attr.setJavaType(target.getName());
                attr.setReferencedClass(target.getScopedName(null));
                addCompositionRelationship(metadata, classMetadata, attr, attrDef, target, compositionType);
            }
        } else if (type instanceof TextOIDType) {
            attr.setCoreType(CoreType.TEXT);
            attr.setJavaType("String");
        }
        
        // Unit
        if (type instanceof NumericalType) {
            NumericalType numType = (NumericalType) type;
            if (numType.getUnit() != null) {
                attr.setUnit(numType.getUnit().getName());
            }
        }
    }

    private void setGeometryDimensionHints(AttributeMetadata attr, Type type) {
        Boolean hasZ = geometryHasZ(type);
        if (hasZ != null) {
            attr.setGeometryHasZ(hasZ);
        }
    }

    private Boolean geometryHasZ(Type type) {
        if (type instanceof ch.interlis.ili2c.metamodel.AbstractCoordType coordType) {
            return coordType.getDimensions() != null && coordType.getDimensions().length >= 3;
        }
        if (type instanceof LineType lineType) {
            Domain controlPointDomain = lineType.getControlPointDomain();
            Type controlPointType = controlPointDomain != null ? controlPointDomain.getType() : null;
            return geometryHasZ(controlPointType);
        }
        return null;
    }

    private void processAssociationRoles(ModelMetadata metadata,
                                         ClassMetadata associationMetadata,
                                         AssociationDef associationDef) {
        AssociationMetadata association = new AssociationMetadata(associationDef.getScopedName(null));
        association.setAssociationClass(associationMetadata.getName());
        for (AttributeMetadata attribute : associationMetadata.getAllAttributes()) {
            association.addAttribute(attribute);
        }

        Iterator<RoleDef> roles = associationDef.getRolesIterator();
        while (roles.hasNext()) {
            RoleDef role = roles.next();
            AbstractClassDef destination = role.getDestination();
            if (destination == null) {
                continue;
            }

            RelationshipMetadata relationship = new RelationshipMetadata(
                associationMetadata.getName() + "." + role.getName()
            );
            relationship.setSourceClass(associationMetadata.getName());
            relationship.setTargetClass(destination.getScopedName(null));
            relationship.setType(RelationshipMetadata.RelationType.ASSOCIATION);
            relationship.setSemanticKind(RelationshipMetadata.SemanticKind.ASSOCIATION_ROLE);
            relationship.setSource("ili2c");
            relationship.setSemanticName(relationship.getName());
            relationship.setMergeReason(RelationshipMetadata.MergeReason.ILI2C_ONLY);
            relationship.setMergeConfidence(RelationshipMetadata.MergeConfidence.NONE);
            relationship.setAssociationName(associationDef.getScopedName(null));
            relationship.setTargetRoleName(role.getName());
            int roleCount = countRoles(associationDef);
            if (roleCount == 2) {
                RoleDef oppositeRole = role.getOppEnd();
                if (oppositeRole != null) {
                    relationship.setOppositeRoleName(oppositeRole.getName());
                    relationship.setSourceRoleName(oppositeRole.getName());
                }
            }
            Cardinality cardinality = role.getCardinality();
            relationship.setCardinality(toRelationshipCardinality(cardinality));
            relationship.setMandatory(cardinality != null && cardinality.getMinimum() > 0);
            relationship.setOrdered(role.isOrdered());
            relationship.setExternal(role.isExternal());
            relationship.setComposition(role.getKind() == RoleDef.Kind.eCOMPOSITE);

            metadata.addRelationship(relationship);
            association.addRole(toAssociationRole(relationship));
        }
        metadata.addAssociation(association);
    }

    private static int countRoles(AssociationDef associationDef) {
        int count = 0;
        Iterator<?> iter = associationDef.getRolesIterator();
        while (iter.hasNext()) {
            iter.next();
            count++;
        }
        return count;
    }

    private AssociationRoleMetadata toAssociationRole(RelationshipMetadata relationship) {
        String roleName = relationship.getTargetRoleName() != null
            ? relationship.getTargetRoleName()
            : relationship.getName();
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

    private void addReferenceRelationship(ModelMetadata metadata,
                                          ClassMetadata classMetadata,
                                          AttributeMetadata attr,
                                          AttributeDef attrDef,
                                          AbstractClassDef target,
                                          ReferenceType referenceType) {
        RelationshipMetadata relationship = new RelationshipMetadata(
            classMetadata.getName() + "." + attr.getName()
        );
        relationship.setSourceClass(classMetadata.getName());
        relationship.setTargetClass(target.getScopedName(null));
        relationship.setType(RelationshipMetadata.RelationType.MANY_TO_ONE);
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.REFERENCE_ATTRIBUTE);
        relationship.setSource("ili2c");
        relationship.setSemanticName(semanticAttributeName(classMetadata, attr));
        relationship.setMergeReason(RelationshipMetadata.MergeReason.ILI2C_ONLY);
        relationship.setMergeConfidence(RelationshipMetadata.MergeConfidence.NONE);
        relationship.setSourceAttribute(attr.getName());
        relationship.setTargetRoleName(attr.getName());
        relationship.setExternal(referenceType.isExternal());
        Cardinality cardinality = attrDef.getCardinality();
        relationship.setCardinality(toRelationshipCardinality(cardinality));
        relationship.setMandatory(cardinality != null && cardinality.getMinimum() > 0);
        metadata.addRelationship(relationship);
    }

    private void addCompositionRelationship(ModelMetadata metadata,
                                            ClassMetadata classMetadata,
                                            AttributeMetadata attr,
                                            AttributeDef attrDef,
                                            AbstractClassDef target,
                                            CompositionType compositionType) {
        RelationshipMetadata relationship = new RelationshipMetadata(
            classMetadata.getName() + "." + attr.getName()
        );
        relationship.setSourceClass(classMetadata.getName());
        relationship.setTargetClass(target.getScopedName(null));
        relationship.setType(RelationshipMetadata.RelationType.ONE_TO_MANY);
        relationship.setSemanticKind(RelationshipMetadata.SemanticKind.COMPOSITION_ATTRIBUTE);
        relationship.setSource("ili2c");
        relationship.setSemanticName(semanticAttributeName(classMetadata, attr));
        relationship.setMergeReason(RelationshipMetadata.MergeReason.ILI2C_ONLY);
        relationship.setMergeConfidence(RelationshipMetadata.MergeConfidence.NONE);
        relationship.setSourceAttribute(attr.getName());
        relationship.setTargetRoleName(attr.getName());
        relationship.setOrdered(compositionType.isOrdered());
        relationship.setComposition(true);
        Cardinality cardinality = compositionType.getCardinality() != null
            ? compositionType.getCardinality()
            : attrDef.getCardinality();
        relationship.setCardinality(toRelationshipCardinality(cardinality));
        relationship.setMandatory(cardinality != null && cardinality.getMinimum() > 0);
        metadata.addRelationship(relationship);
    }

    private String semanticAttributeName(ClassMetadata classMetadata, AttributeMetadata attr) {
        if (attr.getQualifiedName() != null && !attr.getQualifiedName().isBlank()) {
            return attr.getQualifiedName();
        }
        return classMetadata.getName() + "." + attr.getName();
    }

    private RelationshipMetadata.Cardinality toRelationshipCardinality(Cardinality cardinality) {
        int minTarget = cardinality != null ? toCardinalityBound(cardinality.getMinimum()) : 0;
        int maxTarget = cardinality != null ? toCardinalityBound(cardinality.getMaximum()) : 1;
        return new RelationshipMetadata.Cardinality(1, 1, minTarget, maxTarget);
    }

    private String resolveNumericJavaType(NumericType numType) {
        boolean hasDecimal = hasDecimalDigits(numType.getMinimum())
            || hasDecimalDigits(numType.getMaximum());
        if (hasDecimal) {
            return "java.math.BigDecimal";
        }
        if (numType.getMinimum() != null || numType.getMaximum() != null) {
            return "Integer";
        }
        return "java.math.BigDecimal";
    }

    private boolean hasDecimalDigits(PrecisionDecimal value) {
        return value != null && value.getAccuracy() > 0;
    }

    private Integer resolveNumericPrecision(NumericType numType) {
        Integer minPrecision = precision(numType.getMinimum());
        Integer maxPrecision = precision(numType.getMaximum());
        if (minPrecision == null) {
            return maxPrecision;
        }
        if (maxPrecision == null) {
            return minPrecision;
        }
        return Math.max(minPrecision, maxPrecision);
    }

    private Integer resolveNumericScale(NumericType numType) {
        Integer minScale = scale(numType.getMinimum());
        Integer maxScale = scale(numType.getMaximum());
        if (minScale == null) {
            return maxScale;
        }
        if (maxScale == null) {
            return minScale;
        }
        return Math.max(minScale, maxScale);
    }

    private Integer precision(PrecisionDecimal value) {
        BigDecimal decimal = decimal(value);
        if (decimal == null) {
            return null;
        }
        return decimal.precision();
    }

    private Integer scale(PrecisionDecimal value) {
        BigDecimal decimal = decimal(value);
        if (decimal == null) {
            return null;
        }
        return Math.max(decimal.scale(), value.getAccuracy());
    }

    private BigDecimal decimal(PrecisionDecimal value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String resolveFormattedJavaType(FormattedType formattedType) {
        return switch (resolveFormattedCoreType(formattedType)) {
            case DATE -> "java.time.LocalDate";
            case DATETIME -> "java.time.LocalDateTime";
            case TIME -> "java.time.LocalTime";
            default -> "String";
        };
    }

    private CoreType resolveFormattedCoreType(FormattedType formattedType) {
        Domain baseDomain = formattedType.getDefinedBaseDomain();
        if (baseDomain == PredefinedModel.getInstance().XmlDate) {
            return CoreType.DATE;
        }
        if (baseDomain == PredefinedModel.getInstance().XmlDateTime) {
            return CoreType.DATETIME;
        }
        if (baseDomain == PredefinedModel.getInstance().XmlTime) {
            return CoreType.TIME;
        }
        String format = formattedType.getFormat();
        if (format == null) {
            return CoreType.TEXT;
        }
        if (format.contains("Year") && format.contains("Month") && format.contains("Day")) {
            return format.contains("Hours") ? CoreType.DATETIME : CoreType.DATE;
        }
        if (format.contains("Hours") && format.contains("Minutes") && format.contains("Seconds")) {
            return CoreType.TIME;
        }
        return CoreType.TEXT;
    }

    private int toCardinalityBound(long value) {
        if (value == Cardinality.UNBOUND) {
            return -1;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
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

    private String resolveModelRepositories() {
        List<String> repos = resolveModelRepositoriesList();
        if (repos.isEmpty()) {
            return null;
        }
        return repos.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(dir -> !dir.isEmpty())
            .collect(Collectors.joining(";"));
    }

    private List<String> resolveModelRepositoriesList() {
        List<String> repos = (modelDirs == null || modelDirs.isEmpty())
            ? DEFAULT_MODEL_REPOSITORIES
            : modelDirs;
        return repos.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(dir -> !dir.isEmpty())
            .collect(Collectors.toList());
    }

    private Model resolveModel(TransferDescription td, String modelName) {
        if (modelName != null && !modelName.isBlank()) {
            Element element = td.getElement(modelName);
            if (element instanceof Model) {
                return (Model) element;
            }
        }
        return td.getLastModel();
    }
}
