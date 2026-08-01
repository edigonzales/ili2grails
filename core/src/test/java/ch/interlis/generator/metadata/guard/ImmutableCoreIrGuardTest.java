package ch.interlis.generator.metadata.guard;

import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.Cardinality;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.ModelMetadataIndexes;
import ch.interlis.generator.model.RelationshipIdentity;
import ch.interlis.generator.model.RelationshipMetadata;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source guard 13.2/13.3: die Core-IR ist nach dem Freeze immutable —
 * keine öffentlichen Set-/Add-Mutatoren, Klassen final, und die
 * Relationship-Wahrheit liegt kanonisch auf der Root-IR (kein
 * Relationship-Feld auf Klassen).
 */
class ImmutableCoreIrGuardTest {

    private static final List<Class<?>> VALUE_OBJECTS = List.of(
        ModelMetadata.class,
        ClassMetadata.class,
        AttributeMetadata.class,
        RelationshipMetadata.class,
        AssociationMetadata.class,
        AssociationRoleMetadata.class,
        EnumMetadata.class,
        EnumMetadata.EnumValue.class,
        Cardinality.class,
        ModelMetadataIndexes.class,
        RelationshipIdentity.class
    );

    @Test
    void metadataClassesAreFinal() {
        for (Class<?> type : VALUE_OBJECTS) {
            assertThat(Modifier.isFinal(type.getModifiers()))
                .as(type.getName() + " is final")
                .isTrue();
        }
    }

    @Test
    void metadataClassesExposeNoPublicMutators() {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : VALUE_OBJECTS) {
            for (Method method : type.getMethods()) {
                String name = method.getName();
                boolean mutator = name.startsWith("set")
                    || name.startsWith("add")
                    || name.startsWith("put")
                    || name.startsWith("remove");
                if (mutator && method.getDeclaringClass() == type) {
                    violations.add(type.getSimpleName() + "." + name + "()");
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void classMetadataHasNoRelationshipField() {
        for (Method method : ClassMetadata.class.getMethods()) {
            assertThat(method.getName())
                .as("ClassMetadata must not expose relationships (canonical root list)")
                .isNotEqualTo("getRelationships")
                .isNotEqualTo("getRelationshipList");
        }
    }
}
