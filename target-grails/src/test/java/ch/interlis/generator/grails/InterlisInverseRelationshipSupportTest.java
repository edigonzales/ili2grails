package ch.interlis.generator.grails;

import groovy.lang.GroovyClassLoader;
import groovy.util.Expando;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterlisInverseRelationshipSupportTest {

    private static final Path SUPPORT_SOURCE = Path.of(
        "target-grails/src/main/resources/grails/overlays/bootstrap-openlayers/"
            + "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisInverseRelationshipSupport.groovy"
    );

    @Test
    void appliesLabelAndModeOverridesWithoutUpgradingUnsafeMetadata() throws Exception {
        RuntimeFixture fixture = runtimeFixture(true);

        List<Map<String, Object>> defaults = fixture.descriptors(Map.of());
        assertThat(defaults)
            .singleElement()
            .satisfies(descriptor -> {
                assertThat(descriptor.get("label")).isEqualTo("Employees");
                assertThat(descriptor.get("writable")).isEqualTo(true);
            });

        List<Map<String, Object>> readOnly = fixture.descriptors(Map.of(
            "relationships", Map.of(
                "employees", Map.of("label", "Mitarbeitende", "mode", "read-only")
            )
        ));
        assertThat(readOnly)
            .singleElement()
            .satisfies(descriptor -> {
                assertThat(descriptor.get("label")).isEqualTo("Mitarbeitende");
                assertThat(descriptor.get("writable")).isEqualTo(false);
            });

        assertThat(fixture.descriptors(Map.of(
            "relationships", Map.of("employees", Map.of("mode", "off"))
        ))).isEmpty();

        RuntimeFixture unsafe = runtimeFixture(false);
        assertThat(unsafe.descriptors(Map.of(
            "relationships", Map.of("employees", Map.of("mode", "editable"))
        )))
            .singleElement()
            .satisfies(descriptor -> assertThat(descriptor.get("writable")).isEqualTo(false));
    }

    @Test
    void rejectsUnknownRelationshipAndInvalidMode() throws Exception {
        RuntimeFixture fixture = runtimeFixture(true);

        assertThatThrownBy(() -> fixture.descriptors(Map.of(
            "relationships", Map.of("unknown", Map.of("mode", "auto"))
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown relationship 'unknown'");

        assertThatThrownBy(() -> fixture.descriptors(Map.of(
            "relationships", Map.of("employees", Map.of("mode", "force"))
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid relationship mode 'force'");
    }

    private RuntimeFixture runtimeFixture(boolean generatedWritable) throws Exception {
        GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader());
        loader.parseClass("""
            package ch.interlis.generator.grails.generated
            class InterlisUiRegistry {
                static final List DOMAINS = [[
                    domainClassName: 'com.example.Department',
                    controller: 'department',
                    iliName: 'Test.Department'
                ]]
            }
            """, "InterlisUiRegistry.groovy");
        loader.parseClass("""
            package ch.interlis.generator.grails.runtime
            final class InterlisUiDescriptorSupport {
                static Map configuredDomainForType(def app, Class type) {
                    app.configuredDomain ?: [:]
                }
                static Map staticDomainMap(Class type, String name) {
                    try {
                        return type."${name}" as Map
                    } catch (Exception ignored) {
                        return [:]
                    }
                }
            }
            """, "InterlisUiDescriptorSupport.groovy");
        loader.parseClass("""
            package com.example
            class Department {
                static interlisInverseRelationshipMeta = [
                    employees: [
                        relatedDomainClass: 'com.example.Employee',
                        relatedProperty: 'department',
                        label: 'Employees',
                        relatedLabel: 'Employee',
                        writable: %s
                    ]
                ]
            }
            class Employee { }
            """.formatted(generatedWritable), "Domains.groovy");
        Class<?> support = loader.parseClass(
            Files.readString(SUPPORT_SOURCE),
            "InterlisInverseRelationshipSupport.groovy"
        );
        Class<?> owner = loader.loadClass("com.example.Department");
        return new RuntimeFixture(support, owner);
    }

    private record RuntimeFixture(Class<?> support, Class<?> owner) {

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> descriptors(Map<String, Object> configuredDomain) throws Exception {
            Expando app = new Expando();
            app.setProperty("configuredDomain", configuredDomain);
            Method method = support.getDeclaredMethod("descriptors", Object.class, Class.class);
            method.setAccessible(true);
            try {
                return (List<Map<String, Object>>) method.invoke(null, app, owner);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                throw e;
            }
        }
    }
}
