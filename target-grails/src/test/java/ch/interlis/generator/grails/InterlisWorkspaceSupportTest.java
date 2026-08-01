package ch.interlis.generator.grails;

import groovy.lang.GroovyClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InterlisWorkspaceSupportTest {

    private static final Path RUNTIME_SOURCE = RuntimeSourcePaths.runtimeSource("InterlisWorkspaceSupport");

    @Test
    void tableFactoriesCreateStableDisplayOnlyModels() throws Exception {
        Class<?> supportType = runtimeType();
        Map<String, Object> row = invoke(supportType, "tableRow",
            Map.of("name", "Haus A"),
            Map.of("name", Map.of("controller", "building", "action", "show", "id", 7L)));
        Map<String, Object> section = invoke(supportType, "tableSection",
            "buildings", "Gebäude",
            List.of(Map.of("key", "name", "label", "Name")),
            List.of(row), "Keine Gebäude");

        assertThat(section).containsEntry("key", "buildings")
            .containsEntry("title", "Gebäude")
            .containsEntry("count", 1)
            .containsEntry("emptyMessage", "Keine Gebäude");
        assertThat((List<Map<String, Object>>) section.get("columns"))
            .containsExactly(Map.of("key", "name", "label", "Name"));
        assertThat((List<Map<String, Object>>) section.get("rows")).containsExactly(row);
        assertThat((Map<String, Object>) row.get("values")).containsEntry("name", "Haus A");
        assertThat((Map<String, Object>) ((Map<String, Map<String, Object>>) row.get("links")).get("name"))
            .containsEntry("controller", "building")
            .containsEntry("action", "show")
            .containsEntry("id", 7L);
    }

    @Test
    void emptySectionsKeepExplicitEmptyState() throws Exception {
        Class<?> supportType = runtimeType();
        Map<String, Object> section = invoke(supportType, "tableSection",
            "owners", "Eigentümer", List.of(), List.of(), "Keine Eigentümer");

        assertThat((List<?>) section.get("rows")).isEmpty();
        assertThat(section.get("count")).isEqualTo(0);
        assertThat(section.get("emptyMessage")).isEqualTo("Keine Eigentümer");
    }

    @Test
    void workspacePresentationSupportDoesNotIntroduceQueriesOrAuditState() throws Exception {
        String source = Files.readString(RUNTIME_SOURCE);
        assertThat(source).doesNotContain(".list(", ".count(", "findAll(", "executeQuery",
            "audit", "history", "timeline", "restore");
    }

    private Class<?> runtimeType() throws Exception {
        GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader());
        classLoader.parseClass(
            Files.readString(RuntimeSourcePaths.generatedRegistryAccessorSource()),
            "GeneratedRegistryAccessor.groovy");
        classLoader.parseClass("""
            package ch.interlis.generator.grails.generated
            class InterlisUiRegistry {
                static final List DOMAINS = []
            }
            """, "InterlisUiRegistry.groovy");
        classLoader.parseClass("""
            package ch.interlis.generator.grails.runtime
            class InterlisRelationshipOptions {
                static String optionLabel(Object value) { value?.toString() }
                static String displayLabel(Object value) { value?.toString() }
            }
            """, "InterlisRelationshipOptions.groovy");
        classLoader.parseClass("""
            package ch.interlis.generator.grails.runtime
            class InterlisUiDescriptorSupport {
                static Map staticDomainMap(Class type, String name) { [:] }
            }
            """, "InterlisUiDescriptorSupport.groovy");
        return classLoader.parseClass(Files.readString(RUNTIME_SOURCE), "InterlisWorkspaceSupport.groovy");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(Class<?> type, String name, Object... arguments) throws Exception {
        Method method = List.of(type.getDeclaredMethods()).stream()
            .filter(candidate -> candidate.getName().equals(name)
                && candidate.getParameterCount() == arguments.length)
            .findFirst()
            .orElseThrow();
        return (Map<String, Object>) method.invoke(null, arguments);
    }
}
