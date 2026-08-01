package ch.interlis.generator.grails;

import groovy.lang.GroovyClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterlisNavigationSupportTest {

    private static final Path RUNTIME_SOURCE = Path.of(
        "target-grails/src/main/resources/grails/overlays/bootstrap-openlayers/" +
            "src/main/groovy/ch/interlis/generator/grails/runtime/InterlisNavigationSupport.groovy"
    );

    @Test
    void groupsVisibleRegistryDomainsAndKeepsFallbackControllersSafe() throws Exception {
        Class<?> supportType = runtimeType();
        Object application = fakeApplication(supportType.getClassLoader());

        Map<String, Object> model = invoke(supportType, "navigationModel", application);
        List<Map<String, Object>> domains = list(model.get("domains"));

        assertThat(domains).extracting(entry -> entry.get("controller"))
            .containsExactly("address", "zebra");
        assertThat(domains).extracting(entry -> entry.get("label"))
            .containsExactly("Adresse", "Zonenobjekt");
        assertThat(domains).noneMatch(entry -> "hiddenAssociation".equals(entry.get("controller")));
        assertThat(domains).noneMatch(entry -> "missing".equals(entry.get("controller")));

        List<Map<String, Object>> models = list(model.get("models"));
        assertThat(models).extracting(entry -> entry.get("name"))
            .containsExactly("AlphaModel", "ZetaModel");
        assertThat(list(models.get(0).get("topics"))).extracting(entry -> entry.get("label"))
            .containsExactly("Addresses");

        List<Map<String, Object>> fallback = list(model.get("fallback"));
        assertThat(fallback).extracting(entry -> entry.get("controller"))
            .containsExactly("backoffice");
        assertThat(fallback.get(0).get("fallback")).isEqualTo(true);
    }

    @Test
    void searchesLabelClassTopicAndModelAndIsDeterministic() throws Exception {
        Class<?> supportType = runtimeType();
        Object application = fakeApplication(supportType.getClassLoader());
        Map<String, Object> model = invoke(supportType, "navigationModel", application);

        assertThat(list(invokeRaw(supportType, "searchDomains", model, "Adresse")))
            .extracting(entry -> entry.get("controller"))
            .containsExactly("address");
        assertThat(list(invokeRaw(supportType, "searchDomains", model, "Zebra")))
            .extracting(entry -> entry.get("controller"))
            .containsExactly("zebra");
        assertThat(list(invokeRaw(supportType, "searchDomains", model, "Addresses")))
            .extracting(entry -> entry.get("controller"))
            .containsExactly("address");
        assertThat(list(invokeRaw(supportType, "searchDomains", model, "zetamodel")))
            .extracting(entry -> entry.get("controller"))
            .containsExactly("zebra");
        assertThat(list(invokeRaw(supportType, "searchDomains", model, "")))
            .extracting(entry -> entry.get("controller"))
            .containsExactly("address", "zebra");

        Map<String, Object> secondRun = invoke(supportType, "navigationModel", application);
        assertThat(secondRun).isEqualTo(model);
    }

    @Test
    void exposesConfiguredWorkspacesAsSeparateNavigationGroup() throws Exception {
        Class<?> supportType = runtimeType();
        Object application = workspaceApplication(supportType.getClassLoader(), List.of(
            Map.of("id", "parcel-workspace", "label", "Parzellen-Workspace",
                "controller", "parcelWorkspace", "action", "index")
        ));

        Map<String, Object> model = invoke(supportType, "navigationModel", application);
        List<Map<String, Object>> workspaces = list(model.get("workspaces"));

        assertThat(workspaces).singleElement().satisfies(workspace -> {
            assertThat(workspace).containsEntry("kind", "workspace")
                .containsEntry("id", "parcel-workspace")
                .containsEntry("controller", "parcelWorkspace")
                .containsEntry("action", "index")
                .doesNotContainKeys("iliName", "domainClassName");
        });
        assertThat(list(model.get("fallback"))).extracting(entry -> entry.get("controller"))
            .doesNotContain("parcelWorkspace");
        assertThat(list(model.get("allEntries"))).extracting(entry -> entry.get("controller"))
            .contains("parcelWorkspace");
    }

    @Test
    void rejectsInvalidWorkspaceConfigurationWithContext() throws Exception {
        Class<?> supportType = runtimeType();
        Object application = workspaceApplication(supportType.getClassLoader(), List.of(
            Map.of("id", "broken-workspace", "label", "Broken",
                "controller", "doesNotExist", "action", "index")
        ));

        assertThatThrownBy(() -> invoke(supportType, "navigationModel", application))
            .hasRootCauseInstanceOf(IllegalArgumentException.class)
            .hasRootCauseMessage("Ungültige ili2grails.ui.workspaces-Konfiguration " +
                "(id=broken-workspace, controller=doesNotExist): Controller ist nicht registriert");
    }

    @Test
    void treatsMissingWorkspaceConfigurationAsUnconfigured() throws Exception {
        Class<?> supportType = runtimeType();
        Map<String, Object> model = invoke(supportType, "navigationModel", baseApplication(supportType));

        assertThat(list(model.get("workspaces"))).isEmpty();
    }

    private Class<?> runtimeType() throws Exception {
        GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader());
        classLoader.parseClass("""
            package ch.interlis.generator.grails.generated
            class InterlisAssociationRegistry {
                static final Map ENTITIES = [:]
                static Map<String, Object> legacyEntity(String name) { ENTITIES[name] }
            }
            """, "InterlisAssociationRegistry.groovy");
        classLoader.parseClass("""
            package ch.interlis.generator.grails.generated
            class InterlisUiRegistry {
                static final List DOMAINS = [
                    [controller: 'zebra', modelName: 'ZetaModel', topicName: 'ZetaModel.Zones',
                     label: 'Zonenobjekt', className: 'Zebra', iliName: 'ZetaModel.Zones.Animal',
                     navigationVisible: true, associationDomain: false],
                    [controller: 'address', modelName: 'AlphaModel', topicName: 'AlphaModel.Addresses',
                     label: 'Adresse', className: 'Address', iliName: 'AlphaModel.Addresses.Address',
                     navigationVisible: true, associationDomain: false],
                    [controller: 'hiddenAssociation', modelName: 'AlphaModel', topicName: 'AlphaModel.Addresses',
                     label: 'Technical Link', className: 'HiddenAssociation',
                     iliName: 'AlphaModel.Addresses.HiddenAssociation',
                     navigationVisible: false, associationDomain: true],
                    [controller: 'missing', modelName: 'AlphaModel', topicName: 'AlphaModel.Addresses',
                     label: 'Missing', className: 'Missing', iliName: 'AlphaModel.Addresses.Missing',
                     navigationVisible: true, associationDomain: false]
                ]
                static List legacyDomains() { DOMAINS }
            }
            """, "InterlisUiRegistry.groovy");
        classLoader.parseClass("""
            package ch.interlis.generator.grails.runtime
            abstract class InterlisCrudControllerSupport<T> { }
            """, "InterlisCrudControllerSupport.groovy");
        classLoader.parseClass("""
            package ch.interlis.generator.grails.runtime
            class InterlisAssociationRegistrySupport {
                static boolean showInNavigation(Class domainType) { true }
            }
            """, "InterlisAssociationRegistrySupport.groovy");
        return classLoader.parseClass(Files.readString(RUNTIME_SOURCE), "InterlisNavigationSupport.groovy");
    }

    private Object fakeApplication(ClassLoader classLoader) throws Exception {
        Class<?> artefactType = ((GroovyClassLoader) classLoader).parseClass("""
            class FakeArtefact {
                String logicalPropertyName
                String namespace
                String shortName
                Class clazz
            }
            """, "FakeArtefact.groovy");
        Class<?> applicationType = ((GroovyClassLoader) classLoader).parseClass("""
            class FakeApplication {
                List controllerClasses
                Object config
            }
            """, "FakeApplication.groovy");

        Object address = artefactType.getConstructor().newInstance();
        setString(artefactType, address, "logicalPropertyName", "address");
        setString(artefactType, address, "shortName", "AddressController");
        Object zebra = artefactType.getConstructor().newInstance();
        setString(artefactType, zebra, "logicalPropertyName", "zebra");
        setString(artefactType, zebra, "shortName", "ZebraController");
        Object hidden = artefactType.getConstructor().newInstance();
        setString(artefactType, hidden, "logicalPropertyName", "hiddenAssociation");
        setString(artefactType, hidden, "shortName", "HiddenAssociationController");
        Object fallback = artefactType.getConstructor().newInstance();
        setString(artefactType, fallback, "logicalPropertyName", "backoffice");
        setString(artefactType, fallback, "shortName", "BackofficeController");
        Object ui = artefactType.getConstructor().newInstance();
        setString(artefactType, ui, "logicalPropertyName", "interlisUi");
        setString(artefactType, ui, "shortName", "InterlisUiController");

        Object application = applicationType.getConstructor().newInstance();
        applicationType.getMethod("setControllerClasses", List.class).invoke(application,
            List.of(address, zebra, hidden, fallback, ui));
        return application;
    }

    private Object workspaceApplication(ClassLoader classLoader, List<Map<String, String>> workspaces)
        throws Exception {
        GroovyClassLoader groovyClassLoader = (GroovyClassLoader) classLoader;
        Class<?> artefactType = groovyClassLoader.parseClass("""
            class WorkspaceFakeArtefact {
                String logicalPropertyName
                String namespace
                String shortName
                Class clazz
            }
            """, "WorkspaceFakeArtefact.groovy");
        Class<?> applicationType = groovyClassLoader.parseClass("""
            class WorkspaceFakeApplication {
                List controllerClasses
                Object config
            }
            """, "WorkspaceFakeApplication.groovy");
        Object application = applicationType.getConstructor().newInstance();
        Object workspace = artefactType.getConstructor().newInstance();
        setString(artefactType, workspace, "logicalPropertyName", "parcelWorkspace");
        setString(artefactType, workspace, "shortName", "ParcelWorkspaceController");
        applicationType.getMethod("setControllerClasses", List.class).invoke(application, List.of(workspace));
        applicationType.getMethod("setConfig", Object.class).invoke(application,
            Map.of("ili2grails", Map.of("ui", Map.of("workspaces", workspaces))));
        return application;
    }

    private Object baseApplication(Class<?> supportType) throws Exception {
        GroovyClassLoader groovyClassLoader = (GroovyClassLoader) supportType.getClassLoader();
        Class<?> applicationType = groovyClassLoader.parseClass("""
            class EmptyWorkspaceFakeApplication {
                List controllerClasses
                Object config
            }
            """, "EmptyWorkspaceFakeApplication.groovy");
        Object application = applicationType.getConstructor().newInstance();
        applicationType.getMethod("setControllerClasses", List.class).invoke(application, List.of());
        applicationType.getMethod("setConfig", Object.class).invoke(application,
            Map.of("ili2grails", Map.of("ui", Map.of("workspaces", Map.of()))));
        return application;
    }

    private void setString(Class<?> type, Object target, String property, String value) throws Exception {
        String setter = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        type.getMethod(setter, String.class).invoke(target, value);
    }

    private Object invokeRaw(Class<?> type, String name, Object... arguments) throws Exception {
        Method method = List.of(type.getDeclaredMethods()).stream()
            .filter(candidate -> candidate.getName().equals(name)
                && candidate.getParameterCount() == arguments.length)
            .findFirst()
            .orElseThrow();
        return method.invoke(null, arguments);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(Class<?> type, String name, Object... arguments) throws Exception {
        return (Map<String, Object>) invokeRaw(type, name, arguments);
    }

    @SuppressWarnings("unchecked")
    private <T> List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
