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

    private static final Path RUNTIME_SOURCE = Path.of(
        "target-grails/src/main/resources/grails/overlays/bootstrap-openlayers/" +
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisUiDescriptorSupport.groovy"
    );

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
            .isEqualTo(List.of("id", "name", "description", "year", "active"));
        assertThat(defaultList.get("searchFields"))
            .isEqualTo(List.of("name", "description"));
        assertThat(defaultList.get("prominentFilters"))
            .isEqualTo(List.of("name", "description", "year"));
        assertThat(map(defaults.get("form")).get("sections").toString())
            .contains("Allgemein", "longText");

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
            .doesNotContain("longText");
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

    private GeneratedRuntime generatedRuntime() throws Exception {
        ModelMetadata metadata = new ModelMetadata("UiModel");
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
        TargetNameRegistry registry = TargetNameRegistry.forMetadata(metadata, config);
        GrailsRelationshipMapper mapper = GrailsRelationshipMapper.forMetadata(metadata, config, registry);
        GrailsAssociationPlanner planner =
            GrailsAssociationPlanner.forMetadata(metadata, config, registry, mapper);
        new GrailsUiRegistryGenerator().generate(metadata, config, registry, mapper, planner);

        GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader());
        Path registrySource = tempDir.resolve(
            "src/main/groovy/ch/interlis/generator/grails/generated/InterlisUiRegistry.groovy"
        );
        assertThat(Files.readString(registrySource))
            .contains("domainClassName: 'com.example.ui.Address'");
        classLoader.parseClass(registrySource.toFile());
        Class<?> domainType = classLoader.parseClass(domainSource(), "Address.groovy");
        Class<?> supportType = classLoader.parseClass(Files.readString(RUNTIME_SOURCE), "InterlisUiDescriptorSupport.groovy");
        return new GeneratedRuntime(supportType, domainType);
    }

    private AttributeMetadata attribute(String name, String javaType) {
        AttributeMetadata attribute = new AttributeMetadata(name);
        attribute.setJavaType(javaType);
        return attribute;
    }

    private String domainSource() {
        return """
            package com.example.ui

            class Address {
                Long id
                String name
                String description
                Integer year
                Boolean active
                String longText

                static constrainedProperties = [
                    id: [:],
                    name: [maxSize: 80],
                    description: [maxSize: 80],
                    year: [:],
                    active: [:],
                    longText: [maxSize: 1000]
                ]
                static interlisDisplayMeta = [
                    displayFields: ['name'],
                    searchFields: ['name', 'description']
                ]
                static interlisFieldMeta = [
                    name: [coreType: 'TEXT'],
                    description: [coreType: 'TEXT'],
                    year: [coreType: 'NUMERIC'],
                    active: [coreType: 'BOOLEAN'],
                    longText: [coreType: 'MTEXT']
                ]
                static interlisRelationshipMeta = [:]
                static geometryMeta = [:]
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

    private record GeneratedRuntime(Class<?> supportType, Class<?> domainType) {
    }
}
