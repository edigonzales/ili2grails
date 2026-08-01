package ch.interlis.generator.grails.verification.mapping;

import ch.interlis.generator.grails.GenerationConfig;
import ch.interlis.generator.grails.GrailsAssociationPlan;
import ch.interlis.generator.grails.GrailsAssociationPlanner;
import ch.interlis.generator.grails.GrailsRelationshipMapper;
import ch.interlis.generator.grails.TargetNameRegistry;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bildet die erwartete fachliche Persistenz-Planung aus Core-IR und
 * Grails-Planern ab (Spezifikation §31.6). Diese Klasse parst keine
 * generierten Groovy-Dateien; sie reproduziert dieselben Entscheidungen,
 * die {@code GrailsDomainGenerator} beim Schreiben trifft.
 */
public final class ExpectedPersistenceMappingPlanner {

    public static final String ID_PROPERTY = "id";
    public static final String ID_COLUMN = "t_id";
    public static final String ID_GENERATOR = "identity";

    public ExpectedPersistenceMapping plan(ModelMetadata metadata,
                                           GenerationConfig config,
                                           TargetNameRegistry names,
                                           GrailsRelationshipMapper relationships,
                                           GrailsAssociationPlanner associations) {
        Map<String, ExpectedEntityMapping> entities = new LinkedHashMap<>();
        List<ExpectedAssociationMapping> associationMappings = new ArrayList<>();

        List<ClassMetadata> classes = new ArrayList<>(relationships.generatedClasses());
        classes.sort(Comparator.comparing(ClassMetadata::getName, Comparator.nullsLast(String::compareTo)));
        for (ClassMetadata classMetadata : classes) {
            entities.put(classMetadata.getName(), planEntity(classMetadata, names, relationships));
        }

        List<GrailsAssociationPlan> plans = new ArrayList<>(associations.plans());
        plans.sort(Comparator.comparing(GrailsAssociationPlan::associationName,
            Comparator.nullsLast(String::compareTo)));
        for (GrailsAssociationPlan plan : plans) {
            associationMappings.add(new ExpectedAssociationMapping(
                plan.associationName(),
                plan.associationDomainQualifiedName(),
                plan.physicalTable(),
                plan.storageKind() == null ? null : plan.storageKind().name()));
        }
        return new ExpectedPersistenceMapping(entities, associationMappings);
    }

    private ExpectedEntityMapping planEntity(ClassMetadata classMetadata,
                                             TargetNameRegistry names,
                                             GrailsRelationshipMapper relationships) {
        GrailsRelationshipMapper.DomainMapping mapping = relationships.map(classMetadata);
        String domainClassName = names.domainPackage() + "." + names.className(classMetadata);

        Map<String, ExpectedPropertyMapping> properties = new LinkedHashMap<>();
        for (GrailsRelationshipMapper.DomainProperty property : mapping.properties()) {
            boolean relationship = property.relationship() != null;
            String targetDomainClass = null;
            String targetTable = null;
            String foreignKeyColumn = null;
            if (relationship && property.relationship().getTargetClass() != null) {
                ClassMetadata target = relationships.generatedClasses().stream()
                    .filter(candidate -> candidate.getName()
                        .equals(property.relationship().getTargetClass()))
                    .findFirst()
                    .orElse(null);
                if (target != null) {
                    targetDomainClass = names.domainPackage() + "." + names.className(target);
                    targetTable = target.getTableName();
                }
                foreignKeyColumn = property.columnName() != null
                    ? property.columnName()
                    : defaultForeignKeyColumn(property.name());
            }
            properties.put(property.name(), new ExpectedPropertyMapping(
                property.name(),
                relationship && property.columnName() == null ? foreignKeyColumn : property.columnName(),
                property.type(),
                property.nullable(),
                relationship,
                targetDomainClass,
                targetTable,
                foreignKeyColumn,
                property.geometry(),
                property.geometrySrid()
            ));
        }

        Map<String, ExpectedCollectionMapping> collections = new LinkedHashMap<>();
        for (GrailsRelationshipMapper.PersistentCollection collection : mapping.collections()) {
            collections.put(collection.name(), new ExpectedCollectionMapping(
                collection.name(),
                collection.elementType(),
                collection.mappedByProperty(),
                true
            ));
        }

        boolean versioned = hasVersionColumn(mapping);
        return new ExpectedEntityMapping(
            classMetadata.getName(),
            domainClassName,
            classMetadata.getTableName(),
            new ExpectedIdMapping(ID_PROPERTY, ID_COLUMN, ID_GENERATOR),
            properties,
            collections,
            versioned
        );
    }

    private boolean hasVersionColumn(GrailsRelationshipMapper.DomainMapping mapping) {
        for (GrailsRelationshipMapper.DomainProperty property : mapping.properties()) {
            if (property.attribute() != null && property.attribute().getName() != null
                && property.attribute().getName().equalsIgnoreCase("version")) {
                return true;
            }
            if (property.columnName() != null && property.columnName().equalsIgnoreCase("version")) {
                return true;
            }
        }
        return false;
    }

    /**
     * GORM-/Hibernate-Default für FK-Spalten ohne expliziten Spaltennamen:
     * snake_case(PropertyName) + "_id" (beobachtet bei eingebetteten
     * Struktur-Referenzen wie MainInspection -> main_inspection_id).
     */
    private String defaultForeignKeyColumn(String propertyName) {
        StringBuilder snake = new StringBuilder();
        for (int i = 0; i < propertyName.length(); i++) {
            char c = propertyName.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                snake.append('_');
            }
            snake.append(Character.toLowerCase(c));
        }
        return snake + "_id";
    }
}
