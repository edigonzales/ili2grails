package ch.interlis.generator.grails;

import groovy.lang.GroovyClassLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InterlisFormSupportTest {

    private static final Path SOURCE = Path.of(
        "target-grails/src/main/resources/grails/overlays/bootstrap-openlayers/" +
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisFormSupport.groovy"
    );

    @Test
    void acceptsOnlyExplicitSubmitModesAndFallsBackSafely() throws Exception {
        Class<?> support = supportType();
        assertThat(support.getMethod("submitMode", Object.class).invoke(null, "save"))
            .isEqualTo("save");
        assertThat(support.getMethod("submitMode", Object.class).invoke(null, "saveAndContinue"))
            .isEqualTo("saveAndContinue");
        assertThat(support.getMethod("submitMode", Object.class).invoke(null, "delete"))
            .isEqualTo("save");
        assertThat(support.getMethod("submitMode", Object.class).invoke(null, (Object) null))
            .isEqualTo("save");
    }

    @Test
    @SuppressWarnings("unchecked")
    void copiesSectionsAndContextForAContinuePrg() throws Exception {
        Class<?> support = supportType();
        Map<String, Object> descriptor = Map.of(
            "form", Map.of("sections", List.of(
                Map.of("title", "Allgemein", "fields", List.of("name")),
                Map.of("title", "Weitere Felder", "fields", List.of("description"))
            ))
        );
        Map<String, Object> viewModel = (Map<String, Object>) support
            .getMethod("formViewModel", Map.class, Map.class)
            .invoke(null, descriptor, Map.of("relationshipValues", Map.of("owner", "9")));

        assertThat(viewModel.get("formSections").toString())
            .contains("Allgemein", "Weitere Felder", "description");
        assertThat(viewModel.get("relationshipValues").toString()).contains("owner", "9");

        Object instance = new Object() {
            public Long getId() {
                return 17L;
            }
        };
        Map<String, Object> redirect = (Map<String, Object>) support
            .getMethod("continueRedirect", Object.class, Map.class)
            .invoke(null, instance, Map.of("contextId", "association", "ownerId", 3L));
        assertThat(redirect).containsEntry("action", "edit").containsEntry("id", 17L);
        assertThat((Map<Object, Object>) redirect.get("params"))
            .containsEntry("associationContext", "association")
            .containsEntry("associationOwnerId", 3L);
    }

    private Class<?> supportType() throws Exception {
        GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader());
        return loader.parseClass(Files.readString(SOURCE), "InterlisFormSupport.groovy");
    }
}
