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
        assertThat((Boolean) pages.get("showResultRange")).isTrue();
        assertThat(pages.get("resultStart")).isEqualTo(1);
        assertThat(pages.get("resultEnd")).isEqualTo(10);
        Map<?, ?> pageSizeParams = (Map<?, ?>) pages.get("pageSizeParams");
        assertThat(pageSizeParams.get("offset")).isEqualTo(0);
        assertThat(pageSizeParams.containsKey("max")).isFalse();
    }

    @Test
    void derivesCompactAndRangedResultSummariesFromTheFilteredTotal() throws Exception {
        Class<?> support = supportType();

        Map<String, Object> onePageQuery = invokeParse(support, Map.of("max", "25"), descriptor());
        Map<String, Object> onePage = invokeMap(support, "paginationModel", onePageQuery, 25);
        assertThat((Boolean) onePage.get("showResultRange")).isFalse();
        assertThat(onePage.get("resultStart")).isEqualTo(1);
        assertThat(onePage.get("resultEnd")).isEqualTo(25);

        Map<String, Object> emptyPage = invokeMap(support, "paginationModel", onePageQuery, 0);
        assertThat((Boolean) emptyPage.get("showResultRange")).isFalse();
        assertThat(emptyPage.get("resultStart")).isEqualTo(0);
        assertThat(emptyPage.get("resultEnd")).isEqualTo(0);

        Map<String, Object> secondPageQuery = invokeParse(support,
            Map.of("max", "25", "offset", "25"), descriptor());
        Map<String, Object> secondPage = invokeMap(support, "paginationModel", secondPageQuery, 238);
        assertThat((Boolean) secondPage.get("showResultRange")).isTrue();
        assertThat(secondPage.get("resultStart")).isEqualTo(26);
        assertThat(secondPage.get("resultEnd")).isEqualTo(50);
    }

    @Test
    void resolvesRelationshipChipLabelsAndPreservesFallbackAndOtherFilterValues() throws Exception {
        Class<?> support = supportType();
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("department", Map.of(
            "raw", "4",
            "definition", Map.of("type", "relationship", "label", "Department")
        ));
        filters.put("name", Map.of(
            "raw", "Clara",
            "definition", Map.of("type", "text", "label", "Name")
        ));
        filters.put("year", Map.of(
            "minRaw", "2020",
            "definition", Map.of("type", "number", "label", "Year")
        ));
        filters.put("validFrom", Map.of(
            "fromRaw", "2024-01-01",
            "definition", Map.of("type", "date", "label", "Valid from")
        ));
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("filters", filters);

        Map<String, Object> filterOptions = Map.of(
            "department", Map.of("results", List.of(Map.of("id", "4", "label", "Planning")))
        );
        List<Map<String, Object>> labeledChips = invokeList(
            support, "activeFilterChips", query, filterOptions);

        assertThat(labeledChips).extracting(chip -> chip.get("value"))
            .containsExactlyInAnyOrder("Planning", "Clara", "ab 2020", "ab 2024-01-01");

        List<Map<String, Object>> fallbackChips = invokeList(support, "activeFilterChips", query);
        assertThat(fallbackChips.stream()
            .filter(chip -> "department".equals(chip.get("field")))
            .findFirst()
            .orElseThrow()
            .get("value")).isEqualTo("4");
    }

    @Test
    void limitsDirectPageNumbersAndAddsEllipsesAtTheEdgesAndInTheMiddle() throws Exception {
        Class<?> support = supportType();
        Map<String, Object> query = invokeParse(support, Map.of(
            "q", "alpha",
            "max", "10",
            "filter.name", "one",
            "sort", "name",
            "order", "desc"
        ), descriptor());

        List<?> firstPage = (List<?>) invokeMap(support, "paginationModel", query, 250).get("pages");
        assertThat(firstPage).hasSize(6);
        assertThat(firstPage.stream()
            .filter(item -> !Boolean.TRUE.equals(((Map<?, ?>) item).get("ellipsis"))))
            .hasSize(5);
        assertThat(firstPage.get(4)).isEqualTo(Map.of("ellipsis", true));
        assertThat(((Map<?, ?>) firstPage.get(5)).get("number")).isEqualTo(25);

        Map<String, Object> middleQuery = invokeParse(support,
            Map.of("max", "10", "offset", "100"), descriptor());
        List<?> middlePage = (List<?>) invokeMap(support, "paginationModel", middleQuery, 250).get("pages");
        assertThat(middlePage).hasSize(7);
        assertThat(middlePage.stream()
            .filter(item -> Boolean.TRUE.equals(((Map<?, ?>) item).get("ellipsis"))))
            .hasSize(2);
        assertThat(((Map<?, ?>) middlePage.get(3)).get("number")).isEqualTo(11);

        Map<String, Object> lastQuery = invokeParse(support,
            Map.of("max", "10", "offset", "240"), descriptor());
        List<?> lastPage = (List<?>) invokeMap(support, "paginationModel", lastQuery, 250).get("pages");
        assertThat(lastPage).hasSize(6);
        assertThat(lastPage.get(1)).isEqualTo(Map.of("ellipsis", true));
        assertThat(((Map<?, ?>) lastPage.get(5)).get("number")).isEqualTo(25);

        Map<String, Object> pageSizeParams = map(
            invokeMap(support, "paginationModel", query, 250).get("pageSizeParams"));
        assertThat(pageSizeParams).containsEntry("q", "alpha")
            .containsEntry("filter.name", "one")
            .containsEntry("sort", "name")
            .containsEntry("order", "desc")
            .containsEntry("offset", 0)
            .doesNotContainKey("max");
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> invokeList(Class<?> support, String method, Object... args) throws Exception {
        for (var candidate : support.getMethods()) {
            if (candidate.getName().equals(method) && candidate.getParameterCount() == args.length) {
                return (List<Map<String, Object>>) candidate.invoke(null, args);
            }
        }
        throw new NoSuchMethodException(method);
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
