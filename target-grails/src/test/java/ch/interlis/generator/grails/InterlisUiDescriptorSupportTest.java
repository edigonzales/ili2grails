package ch.interlis.generator.grails;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.model.RelationshipMetadata;
import groovy.lang.GroovyClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InterlisUiDescriptorSupportTest {

    private static final Path RUNTIME_SOURCE = RuntimeSourcePaths.runtimeSource("InterlisUiDescriptorSupport");
    private static final Path RELATIONSHIP_OPTIONS_SOURCE = RuntimeSourcePaths.runtimeSource("InterlisRelationshipOptions");
    private static final Path TABLE_MODEL_SOURCE = RuntimeSourcePaths.runtimeSource("InterlisTableModel");
    private static final Path WORKSPACE_SOURCE = RuntimeSourcePaths.runtimeSource("InterlisWorkspaceSupport");

    @TempDir
    Path tempDir;

    @Test
    void derivesDefaultsAndMergesNativeConfig() throws Exception {
        GeneratedRuntime runtime = generatedRuntime();

        Map<String, Object> defaults = invokeDescriptor(runtime, Map.of(
            "config", Map.of()
        ));
        Map<String, Object> defaultList = map(defaults.get("list"));

        assertThat(defaults.get("label")).isEqualTo("Address");
        assertThat(defaultList.get("columns"))
            .isEqualTo(List.of("id", "name", "description", "year", "active", "status"));
        assertThat(defaultList.get("searchFields"))
            .isEqualTo(List.of("name", "description"));
        assertThat(defaultList.get("displayFields"))
            .isEqualTo(List.of("name"));
        assertThat(defaultList.get("prominentFilters"))
            .isEqualTo(List.of());
        List<?> defaultFormSections = (List<?>) map(defaults.get("form")).get("sections");
        assertThat(defaultFormSections).hasSize(1);
        assertThat(defaultFormSections.get(0).toString()).contains("title=Basisdaten", "name", "longText");
        assertThat(defaultFormSections.toString())
            .contains("Basisdaten", "longText", "municipality")
            .doesNotContain("Allgemein");
        assertThat(map(defaults.get("form")).get("sections").toString())
            .doesNotContain("position");
        assertThat(map(defaults.get("fieldMeta")).get("description").toString())
            .contains("Dokumentation", "m");
        assertThat(map(defaults.get("detail")).get("sections").toString())
            .contains("name", "longText", "municipality")
            .doesNotContain("id", "version");

        Map<String, Object> explicitlyCollapsed = invokeDescriptor(runtime, Map.of(
            "config", Map.of(
                "ili2grails", Map.of(
                    "ui", Map.of(
                        "domains", List.of(Map.of(
                            "iliName", "UiModel.Topic.Address",
                            "list", Map.of("prominentFilters", List.of())
                        ))
                    )
                )
            )
        ));
        assertThat(map(explicitlyCollapsed.get("list")).get("prominentFilters"))
            .isEqualTo(List.of());

        Map<String, Object> configuredDomain = new LinkedHashMap<>();
        configuredDomain.put("iliName", "UiModel.Topic.Address");
        configuredDomain.put("label", "Adresse");
        configuredDomain.put("list", Map.of(
            "columns", List.of("id", "year"),
            "searchFields", List.of("name"),
            "prominentFilters", List.of("active")
        ));
        configuredDomain.put("form", Map.of(
            "sections", List.of(Map.of(
                "title", "Allgemein",
                "fields", List.of("name", "active")
            ))
        ));
        Map<String, Object> configured = invokeDescriptor(runtime, Map.of(
            "config", Map.of(
                "ili2grails", Map.of(
                    "ui", Map.of(
                        "appTitle", "Fachdaten",
                        "domains", List.of(configuredDomain)
                    )
                )
            )
        ));

        assertThat(configured.get("label")).isEqualTo("Adresse");
        assertThat(configured.get("appTitle")).isEqualTo("Fachdaten");
        assertThat(map(configured.get("list")).get("columns"))
            .isEqualTo(List.of("id", "year"));
        assertThat(map(configured.get("list")).get("searchFields"))
            .isEqualTo(List.of("name"));
        assertThat(map(configured.get("list")).get("prominentFilters"))
            .isEqualTo(List.of("active"));
        assertThat(map(configured.get("form")).get("sections").toString())
            .contains("name", "active")
            .contains("Weitere Felder", "description", "year", "status", "municipality", "longText");
        assertThat(map(configured.get("detail")).get("sections").toString())
            .contains("name", "active", "longText", "municipality")
            .doesNotContain("id", "version");
    }

    @Test
    void usesDefaultAppTitleWhenGrailsConfigDoesNotDefineOne() throws Exception {
        GeneratedRuntime runtime = generatedRuntime();
        Method appTitle = List.of(runtime.supportType.getMethods()).stream()
            .filter(method -> method.getName().equals("appTitle"))
            .findFirst()
            .orElseThrow();

        assertThat(appTitle.invoke(null, Map.of("config", Map.of())))
            .isEqualTo("INTERLIS CRUD");
    }

    @Test
    void appLogoReturnsNullWhenNotConfigured() throws Exception {
        GeneratedRuntime runtime = generatedRuntime();
        Method appLogo = List.of(runtime.supportType.getMethods()).stream()
            .filter(method -> method.getName().equals("appLogo"))
            .findFirst()
            .orElseThrow();

        assertThat(appLogo.invoke(null, Map.of("config", Map.of())))
            .isNull();
    }

    @Test
    void appLogoReturnsConfiguredPath() throws Exception {
        GeneratedRuntime runtime = generatedRuntime();
        Method appLogo = List.of(runtime.supportType.getMethods()).stream()
            .filter(method -> method.getName().equals("appLogo"))
            .findFirst()
            .orElseThrow();
        Map<String, Object> application = Map.of(
            "config", Map.of(
                "ili2grails", Map.of(
                    "ui", Map.of("appLogo", "my-logo.svg")
                )
            )
        );

        assertThat(appLogo.invoke(null, application))
            .isEqualTo("my-logo.svg");
    }

    @Test
    void appLogoReturnsNullWhenConfiguredBlank() throws Exception {
        GeneratedRuntime runtime = generatedRuntime();
        Method appLogo = List.of(runtime.supportType.getMethods()).stream()
            .filter(method -> method.getName().equals("appLogo"))
            .findFirst()
            .orElseThrow();
        Map<String, Object> application = Map.of(
            "config", Map.of(
                "ili2grails", Map.of(
                    "ui", Map.of("appLogo", "   ")
                )
            )
        );

        assertThat(appLogo.invoke(null, application))
            .isNull();
    }

    @Test
    void appLogoIconDefaultsToGrid() throws Exception {
        GeneratedRuntime runtime = generatedRuntime();
        Method appLogoIcon = List.of(runtime.supportType.getMethods()).stream()
            .filter(method -> method.getName().equals("appLogoIcon"))
            .findFirst()
            .orElseThrow();

        assertThat(appLogoIcon.invoke(null, Map.of("config", Map.of())))
            .isEqualTo("grid");
    }

    @Test
    void appLogoIconReturnsNullWhenAppLogoSet() throws Exception {
        GeneratedRuntime runtime = generatedRuntime();
        Method appLogoIcon = List.of(runtime.supportType.getMethods()).stream()
            .filter(method -> method.getName().equals("appLogoIcon"))
            .findFirst()
            .orElseThrow();
        Map<String, Object> application = Map.of(
            "config", Map.of(
                "ili2grails", Map.of(
                    "ui", Map.of("appLogo", "logo.png")
                )
            )
        );

        assertThat(appLogoIcon.invoke(null, application))
            .isNull();
    }

    @Test
    void appLogoIconReturnsConfiguredIconName() throws Exception {
        GeneratedRuntime runtime = generatedRuntime();
        Method appLogoIcon = List.of(runtime.supportType.getMethods()).stream()
            .filter(method -> method.getName().equals("appLogoIcon"))
            .findFirst()
            .orElseThrow();
        Map<String, Object> application = Map.of(
            "config", Map.of(
                "ili2grails", Map.of(
                    "ui", Map.of("appLogoIcon", "house")
                )
            )
        );

        assertThat(appLogoIcon.invoke(null, application))
            .isEqualTo("house");
    }

    @Test
    void appLogoIconDefaultsToGridWhenConfiguredBlank() throws Exception {
        GeneratedRuntime runtime = generatedRuntime();
        Method appLogoIcon = List.of(runtime.supportType.getMethods()).stream()
            .filter(method -> method.getName().equals("appLogoIcon"))
            .findFirst()
            .orElseThrow();
        Map<String, Object> application = Map.of(
            "config", Map.of(
                "ili2grails", Map.of(
                    "ui", Map.of("appLogoIcon", "   ")
                )
            )
        );

        assertThat(appLogoIcon.invoke(null, application))
            .isEqualTo("grid");
    }

    @Test
    void reportsUnknownConfiguredFieldsWithDomainAndSection() throws Exception {
        GeneratedRuntime runtime = generatedRuntime();
        Map<String, Object> configuredDomain = new LinkedHashMap<>();
        configuredDomain.put("iliName", "UiModel.Topic.Address");
        configuredDomain.put("list", Map.of("columns", List.of("missingField")));
        Map<String, Object> application = Map.of(
            "config", Map.of(
                "ili2grails", Map.of(
                    "ui", Map.of("domains", List.of(configuredDomain))
                )
            )
        );

        InvocationTargetException thrown = invocationFailure(runtime, application);

        assertThat(thrown.getCause())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("UiModel.Topic.Address")
            .hasMessageContaining("missingField")
            .hasMessageContaining("list.columns");
    }

    @Test
    void reportsUnknownConfiguredDomains() throws Exception {
        GeneratedRuntime runtime = generatedRuntime();
        Map<String, Object> configuredDomain = Map.of("iliName", "UiModel.Topic.Missing");
        Map<String, Object> application = Map.of(
            "config", Map.of(
                "ili2grails", Map.of(
                    "ui", Map.of("domains", List.of(configuredDomain))
                )
            )
        );

        InvocationTargetException thrown = invocationFailure(runtime, application);

        assertThat(thrown.getCause())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("UiModel.Topic.Missing")
            .hasMessageContaining("ili2grails.ui.domains");
    }

    @Test
    void exposesEnumOptionsRelationshipsAndWhitelistedSearchPaths() throws Exception {
        GeneratedRuntime runtime = generatedRuntime();
        Map<String, Object> configuredDomain = new LinkedHashMap<>();
        configuredDomain.put("iliName", "UiModel.Topic.Address");
        configuredDomain.put("list", Map.of(
            "searchFields", List.of("municipality.name"),
            "sortableColumns", List.of("year", "status"),
            "displayField", "name",
            "displayFields", List.of("name", "description"),
            "filters", Map.of("status", Map.of("label", "Bearbeitungsstatus"))
        ));
        Map<String, Object> configured = invokeDescriptor(runtime, Map.of(
            "config", Map.of("ili2grails", Map.of("ui", Map.of("domains", List.of(configuredDomain))))
        ));

        Map<String, Object> list = map(configured.get("list"));
        assertThat(list.get("displayField")).isEqualTo("name");
        assertThat(list.get("displayFields")).isEqualTo(List.of("name", "description"));
        assertThat(list.get("sortableColumns")).isEqualTo(List.of("id", "year", "status"));
        assertThat(list.get("searchFields")).isEqualTo(List.of("municipality.name"));
        assertThat(list.get("searchDefinitions").toString()).contains("municipalitySearch.name");
        Map<String, Object> filters = map(list.get("filters"));
        assertThat(map(filters.get("status")).get("type")).isEqualTo("enum");
        assertThat(map(filters.get("status")).get("options").toString())
            .contains("ACTIVE", "ARCHIVED");
        assertThat(map(filters.get("status")).get("label")).isEqualTo("Bearbeitungsstatus");
        assertThat(map(filters.get("municipality")).get("type")).isEqualTo("relationship");
    }

    @Test
    void rejectsUnsafeSearchPathConfiguration() throws Exception {
        GeneratedRuntime runtime = generatedRuntime();
        Map<String, Object> configuredDomain = new LinkedHashMap<>();
        configuredDomain.put("iliName", "UiModel.Topic.Address");
        configuredDomain.put("list", Map.of("searchFields", List.of("municipality.name.code")));
        InvocationTargetException thrown = invocationFailure(runtime, Map.of(
            "config", Map.of("ili2grails", Map.of("ui", Map.of("domains", List.of(configuredDomain))))
        ));

        assertThat(thrown.getCause())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("municipality.name.code")
            .hasMessageContaining("exactly one whitelisted relationship hop");
    }

    @Test
    void rejectsInvalidDisplayFieldConfiguration() throws Exception {
        GeneratedRuntime runtime = generatedRuntime();

        Map<String, Object> unknownFieldDomain = new LinkedHashMap<>();
        unknownFieldDomain.put("iliName", "UiModel.Topic.Address");
        unknownFieldDomain.put("list", Map.of("displayFields", List.of("missingField")));
        InvocationTargetException unknownField = invocationFailure(runtime, Map.of(
            "config", Map.of("ili2grails", Map.of("ui", Map.of("domains", List.of(unknownFieldDomain))))
        ));
        assertThat(unknownField.getCause())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missingField")
            .hasMessageContaining("list.displayFields");

        Map<String, Object> relationshipFieldDomain = new LinkedHashMap<>();
        relationshipFieldDomain.put("iliName", "UiModel.Topic.Address");
        relationshipFieldDomain.put("list", Map.of("displayFields", List.of("municipality")));
        InvocationTargetException relationshipField = invocationFailure(runtime, Map.of(
            "config", Map.of("ili2grails", Map.of("ui", Map.of("domains", List.of(relationshipFieldDomain))))
        ));
        assertThat(relationshipField.getCause())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("municipality")
            .hasMessageContaining("direct scalar field");

        Map<String, Object> tooManyFieldsDomain = new LinkedHashMap<>();
        tooManyFieldsDomain.put("iliName", "UiModel.Topic.Address");
        tooManyFieldsDomain.put("list", Map.of("displayFields", List.of("name", "description", "year")));
        InvocationTargetException tooManyFields = invocationFailure(runtime, Map.of(
            "config", Map.of("ili2grails", Map.of("ui", Map.of("domains", List.of(tooManyFieldsDomain))))
        ));
        assertThat(tooManyFields.getCause())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("one or two fields");
    }

    @Test
    void workspaceUsesDisplayLabelAndIdFallback() throws Exception {
        GeneratedRuntime runtime = generatedRuntime();
        Object address = runtime.domainType.getDeclaredConstructor().newInstance();
        runtime.domainType.getMethod("setId", Long.class).invoke(address, 7L);
        runtime.domainType.getMethod("setName", String.class).invoke(address, "Bahnhofstrasse");

        Method displayLabel = runtime.workspaceType.getMethod("displayLabel", Object.class);
        assertThat(displayLabel.invoke(null, address)).isEqualTo("Bahnhofstrasse");

        Class<?> municipalityType = runtime.classLoader.loadClass("com.example.ui.Municipality");
        Object municipality = municipalityType.getDeclaredConstructor().newInstance();
        municipalityType.getMethod("setId", Long.class).invoke(municipality, 42L);
        assertThat(displayLabel.invoke(null, municipality)).isEqualTo("#42");
    }

    @Test
    void workspaceBuildsScalarDetailsAndSafeToOneRelationshipLinks() throws Exception {
        GeneratedRuntime runtime = generatedRuntime();
        Object address = runtime.domainType.getDeclaredConstructor().newInstance();
        runtime.domainType.getMethod("setId", Long.class).invoke(address, 7L);
        runtime.domainType.getMethod("setName", String.class).invoke(address, "Bahnhofstrasse");
        runtime.domainType.getMethod("setDescription", String.class).invoke(address, "Zentrum");

        Class<?> municipalityType = runtime.classLoader.loadClass("com.example.ui.Municipality");
        Object municipality = municipalityType.getDeclaredConstructor().newInstance();
        municipalityType.getMethod("setId", Long.class).invoke(municipality, 42L);
        municipalityType.getMethod("setName", String.class).invoke(municipality, "Bern");
        runtime.domainType.getMethod("setMunicipality", municipalityType).invoke(address, municipality);

        Map<String, Object> descriptor = invokeDescriptor(runtime, Map.of(
            "config", Map.of("ili2grails", Map.of("ui", Map.of(
                "domains", List.of(Map.of(
                    "iliName", "UiModel.Topic.Address",
                    "list", Map.of("displayFields", List.of("name", "description"))
                ))
            )))
        ));
        Method showModel = runtime.workspaceType.getMethod(
            "showModel", Object.class, Class.class, Object.class, Map.class
        );
        Map<String, Object> model = map(showModel.invoke(null, Map.of(), runtime.domainType, address, descriptor));

        assertThat(model.get("workspaceDisplayLabel")).isEqualTo("Bahnhofstrasse Zentrum");
        String detailText = model.get("workspaceDetailSections").toString();
        assertThat(detailText)
            .contains("name", "description", "municipality", "42", "controller", "Bern");
        assertThat(workspaceFieldNames(detailText))
            .contains("name", "description", "municipality")
            .doesNotContain("id", "version");
        assertThat(model.get("workspaceRelationshipLinks").toString())
            .doesNotContain("municipality", "42", "Bern")
            .doesNotContain("java.util.ArrayList");

        runtime.domainType.getMethod("setMunicipality", municipalityType).invoke(address, new Object[] {null});
        Map<String, Object> emptyModel = map(showModel.invoke(null, Map.of(), runtime.domainType, address, descriptor));
        assertThat(emptyModel.get("workspaceDetailSections").toString())
            .contains("municipality", "value=");
    }

    /**
     * Extrahiert die sichtbaren Feldnamen aus dem Workspace-Detail-Abschnitt.
     * Die Link-Metadaten enthalten bewusst eine {@code id} für die Navigation;
     * sie dürfen aber nicht als sichtbare skalare Feldnamen exponiert werden.
     */
    private static List<String> workspaceFieldNames(String detailText) {
        java.util.regex.Pattern fieldPattern =
            java.util.regex.Pattern.compile("\\{name=([^,}{]+), label=");
        java.util.regex.Matcher matcher = fieldPattern.matcher(detailText);
        List<String> names = new java.util.ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(1).trim());
        }
        return names;
    }

    private GeneratedRuntime generatedRuntime() throws Exception {
        ModelMetadata metadata = new ModelMetadata("UiModel");
        ClassMetadata municipality = new ClassMetadata("UiModel.Topic.Municipality");
        municipality.addAttribute(attribute("name", "String"));
        metadata.addClass(municipality);
        ClassMetadata address = new ClassMetadata("UiModel.Topic.Address");
        address.addAttribute(attribute("name", "String"));
        address.addAttribute(attribute("description", "String"));
        address.addAttribute(attribute("year", "Integer"));
        address.addAttribute(attribute("active", "Boolean"));
        address.addAttribute(attribute("longText", "String"));
        metadata.addClass(address);

        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example")
            .domainPackage("com.example.ui")
            .enumPackage("com.example.enums")
            .build();
        new GrailsUiRegistryGenerator().generate(
            GrailsUiRegistryGeneratorTest.plan(metadata, config),
            config,
            TargetNameRegistry.forMetadata(metadata, config)
        );

        GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader());
        classLoader.parseClass(
            Files.readString(RuntimeSourcePaths.generatedRegistryAccessorSource()),
            "GeneratedRegistryAccessor.groovy");
        Path registrySource = tempDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/generated/InterlisUiRegistry.groovy"
        );
        assertThat(Files.readString(registrySource))
            .contains("'com.example.ui.Address'");
        classLoader.parseClass(registrySource.toFile());
        classLoader.parseClass(domainSource(), "Address.groovy");
        Class<?> domainType = classLoader.loadClass("com.example.ui.Address");
        Class<?> municipalityType = classLoader.loadClass("com.example.ui.Municipality");
        assertThat(Files.readString(registrySource))
            .contains("'com.example.ui.Municipality'");
        classLoader.parseClass(Files.readString(TABLE_MODEL_SOURCE), "InterlisTableModel.groovy");
        Class<?> supportType = classLoader.parseClass(Files.readString(RUNTIME_SOURCE), "InterlisUiDescriptorSupport.groovy");
        classLoader.parseClass(Files.readString(RELATIONSHIP_OPTIONS_SOURCE), "InterlisRelationshipOptions.groovy");
        Class<?> workspaceType = classLoader.parseClass(Files.readString(WORKSPACE_SOURCE), "InterlisWorkspaceSupport.groovy");
        return new GeneratedRuntime(supportType, domainType, municipalityType, workspaceType, classLoader);
    }

    private AttributeMetadata attribute(String name, String javaType) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setJavaType(javaType);
        return attribute;
    }

    private String domainSource() {
        return """
            package com.example.ui

            enum AddressStatus { ACTIVE, ARCHIVED }

            class Municipality {
                Long id
                String name
                static constrainedProperties = [id: [:], name: [maxSize: 80]]
                static interlisDisplayMeta = [displayFields: ['name'], searchFields: ['name']]
                static interlisFieldMeta = [name: [coreType: 'TEXT']]
                static interlisRelationshipMeta = [:]
                static geometryMeta = [:]
            }

            class Address {
                Long id
                String name
                String description
                Integer year
                Boolean active
                AddressStatus status
                Municipality municipality
                String longText
                String position

                static constrainedProperties = [
                    id: [:],
                    name: [maxSize: 80],
                    description: [maxSize: 80],
                    year: [:],
                    active: [:],
                    status: [:],
                    municipality: [nullable: true],
                    longText: [maxSize: 1000],
                    position: [nullable: true]
                ]
                static interlisDisplayMeta = [
                    displayFields: ['name'],
                    searchFields: ['name', 'description']
                ]
                static interlisFieldMeta = [
                    name: [coreType: 'TEXT'],
                    description: [coreType: 'TEXT', documentation: 'Dokumentation', unit: 'm'],
                    year: [coreType: 'NUMERIC'],
                    active: [coreType: 'BOOLEAN'],
                    status: [coreType: 'ENUM'],
                    longText: [coreType: 'MTEXT']
                ]
                static interlisRelationshipMeta = [municipality: [targetClass: 'com.example.ui.Municipality']]
                static geometryMeta = [position: [kind: 'POINT']]
            }
            """;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeDescriptor(GeneratedRuntime runtime,
                                                  Map<String, Object> application)
        throws Exception {
        Method descriptor = List.of(runtime.supportType.getMethods()).stream()
            .filter(method -> method.getName().equals("descriptor"))
            .findFirst()
            .orElseThrow();
        return (Map<String, Object>) descriptor.invoke(null, application, runtime.domainType);
    }

    private InvocationTargetException invocationFailure(GeneratedRuntime runtime,
                                                        Map<String, Object> application)
        throws Exception {
        try {
            invokeDescriptor(runtime, application);
        } catch (InvocationTargetException exception) {
            return exception;
        }
        throw new AssertionError("Expected descriptor invocation to fail");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private record GeneratedRuntime(Class<?> supportType,
                                    Class<?> domainType,
                                    Class<?> municipalityType,
                                    Class<?> workspaceType,
                                    GroovyClassLoader classLoader) {
    }
}
