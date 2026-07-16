package ch.interlis.generator.grails;

import groovy.lang.GroovyClassLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InterlisListQuerySupportTest {

    private static final Path OVERLAY = Path.of(
        "target-grails/src/main/resources/grails/overlays/bootstrap-openlayers/" +
            "src/main/groovy/ch/interlis/generator/grails/runtime/"
    );

    @Test
    void parsesTypedFiltersRangesAndSafeSorts() throws Exception {
        Class<?> support = supportType();
        Map<String, Object> descriptor = descriptor();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", "  Bern ");
        params.put("max", "50");
        params.put("offset", "10");
        params.put("sort", "year");
        params.put("order", "desc");
        params.put("filter", Map.of(
            "name", "  Bahnhof ",
            "status", "ACTIVE",
            "active", "false",
            "year", Map.of("min", "2020", "max", "2024"),
            "validFrom", Map.of("from", "2024-01-01", "to", "2024-12-31")
        ));

        Map<String, Object> query = invokeParse(support, params, descriptor);

        assertThat(query.get("q")).isEqualTo("Bern");
        assertThat(query.get("max")).isEqualTo(50);
        assertThat(query.get("offset")).isEqualTo(10);
        assertThat(query.get("sort")).isEqualTo("year");
        assertThat(query.get("order")).isEqualTo("desc");
        Map<String, Map<String, Object>> filters = map(query.get("filters"));
        assertThat(filters.get("name").get("value")).isEqualTo("Bahnhof");
        assertThat(filters.get("status").get("value")).isEqualTo(TestStatus.ACTIVE);
        assertThat(filters.get("active").get("value")).isEqualTo(false);
        assertThat(filters.get("year").get("min")).isEqualTo(2020);
        assertThat(filters.get("year").get("max")).isEqualTo(2024);
        assertThat(filters.get("validFrom").get("from")).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(filters.get("validFrom").get("to")).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThat(query.get("warnings")).isEqualTo(List.of());
    }

    @Test
    void rejectsUnknownAndMalformedRequestKeysWithoutExposingThemToCriteriaModel() throws Exception {
        Class<?> support = supportType();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("sort", "name;delete from Address");
        params.put("filter", Map.of(
            "name;delete from Address", "x",
            "active", "yes",
            "year", Map.of("min", "2025", "max", "2020"),
            "validFrom", Map.of("from", "not-a-date")
        ));

        Map<String, Object> query = invokeParse(support, params, descriptor());
        Map<String, Map<String, Object>> filters = map(query.get("filters"));

        assertThat(filters).doesNotContainKey("name;delete from Address");
        assertThat(filters).doesNotContainKey("active");
        assertThat(filters).doesNotContainKey("year");
        assertThat(filters).doesNotContainKey("validFrom");
        assertThat(query.get("sort")).isEqualTo("id");
        assertThat(query.get("warnings").toString())
            .contains("Unbekannter Filter")
            .contains("Ungültige Sortierung")
            .contains("Ungültiger Wert für Filter 'active'")
            .contains("Ungültiger Bereich für Filter 'year'")
            .contains("Ungültige Bereichsgrenze für Filter 'validFrom'");
        assertThat(query.get("params").toString()).doesNotContain("delete from Address");
    }

    @Test
    void buildsFilterRemovalSortAndPagingUrlsWithActiveFilters() throws Exception {
        Class<?> support = supportType();
        Map<String, Object> query = invokeParse(support, Map.of(
            "q", "alpha",
            "max", "10",
            "filter", Map.of("name", "one", "year", Map.of("min", "2020")),
            "sort", "name",
            "order", "asc"
        ), descriptor());

        Map<String, Object> remove = invokeMap(support, "removeFilterParams", query, "name");
        Map<String, Object> sort = invokeMap(support, "sortParams", query, "name");
        Map<String, Object> pages = invokeMap(support, "paginationModel", query, 42);

        assertThat(remove).containsEntry("q", "alpha").containsEntry("filter.year.min", "2020")
            .doesNotContainKey("filter.name").containsEntry("offset", 0);
        assertThat(sort).containsEntry("sort", "name").containsEntry("order", "desc")
            .containsEntry("filter.name", "one").containsEntry("offset", 0);
        assertThat(pages.get("lastPage")).isEqualTo(5);
        assertThat(((List<?>) pages.get("pages"))).hasSize(5);
    }

    @Test
    void doesNotExposeEmptyRangeDefinitionsAsActiveFilters() throws Exception {
        Class<?> support = supportType();
        Map<String, Object> query = invokeParse(support,
            Map.of("filter.status", "ACTIVE"), descriptor());

        assertThat(map(query.get("filters"))).containsOnlyKeys("status");
        assertThat(query.get("chips").toString()).contains("status").doesNotContain("year", "validFrom");
    }

    @Test
    void coercesOnlyContractValues() throws Exception {
        Class<?> support = supportType();
        assertThat(invokeCoerce(support, "false", definition("boolean", Boolean.class))).isEqualTo(false);
        assertThat(invokeCoerce(support, "TRUE", definition("boolean", Boolean.class))).isNull();
        assertThat(invokeCoerce(support, "2024-04-03", definition("date", LocalDate.class)))
            .isEqualTo(LocalDate.of(2024, 4, 3));
        assertThat(invokeCoerce(support, "7.5", definition("number", Double.class))).isEqualTo(7.5d);
        assertThat(invokeCoerce(support, "1 OR 1=1", definition("number", Integer.class))).isNull();
    }

    @Test
    void clampsPagingToFiniteServerSideBounds() throws Exception {
        Class<?> support = supportType();
        Map<String, Object> query = invokeParse(support, Map.of(
            "max", "999999",
            "offset", "-1"
        ), descriptor());

        assertThat((Integer) query.get("max")).isLessThanOrEqualTo(100);
        assertThat(query.get("offset")).isEqualTo(0);
        assertThat(query.get("warnings").toString())
            .contains("Ungültiger Wert für max", "Ungültiger Wert für offset");
    }

    private Class<?> supportType() throws Exception {
        GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader());
        loader.parseClass(Files.readString(OVERLAY.resolve("InterlisTableModel.groovy")), "InterlisTableModel.groovy");
        loader.parseClass(Files.readString(OVERLAY.resolve("InterlisRelationshipOptions.groovy")), "InterlisRelationshipOptions.groovy");
        return loader.parseClass(Files.readString(OVERLAY.resolve("InterlisListQuerySupport.groovy")), "InterlisListQuerySupport.groovy");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeParse(Class<?> support, Map<String, Object> params,
                                             Map<String, Object> descriptor) throws Exception {
        return (Map<String, Object>) support.getMethod("parse", Object.class, Map.class)
            .invoke(null, params, descriptor);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeMap(Class<?> support, String method, Object... args) throws Exception {
        for (var candidate : support.getMethods()) {
            if (candidate.getName().equals(method) && candidate.getParameterCount() == args.length) {
                return (Map<String, Object>) candidate.invoke(null, args);
            }
        }
        throw new NoSuchMethodException(method);
    }

    private Object invokeCoerce(Class<?> support, Object raw, Map<String, Object> definition) throws Exception {
        return support.getMethod("coerceFilterValue", Object.class, Map.class)
            .invoke(null, raw, definition);
    }

    private Map<String, Object> descriptor() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("name", definition("text", String.class));
        filters.put("status", Map.of(
            "name", "status", "type", "enum", "propertyType", TestStatus.class,
            "options", List.of(Map.of("value", "ACTIVE"), Map.of("value", "ARCHIVED")), "label", "Status"
        ));
        filters.put("active", definition("boolean", Boolean.class));
        filters.put("year", definition("number", Integer.class));
        filters.put("validFrom", definition("date", LocalDate.class));
        Map<String, Object> list = new LinkedHashMap<>();
        list.put("filters", filters);
        list.put("sortableColumns", List.of("id", "name", "year"));
        list.put("searchDefinitions", List.of(Map.of("path", "name", "criteriaPath", "name")));
        return Map.of("list", list);
    }

    private Map<String, Object> definition(String type, Class<?> propertyType) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("name", type.equals("date") ? "validFrom" : type.equals("number") ? "year" : type);
        definition.put("type", type);
        definition.put("propertyType", propertyType);
        definition.put("className", propertyType.getName());
        definition.put("label", type);
        return definition;
    }

    @SuppressWarnings("unchecked")
    private <T> Map<String, T> map(Object value) {
        return (Map<String, T>) value;
    }

    private enum TestStatus { ACTIVE, ARCHIVED }
}
