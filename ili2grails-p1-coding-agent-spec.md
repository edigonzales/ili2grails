# ili2grails – P1-Spezifikation für einen LLM-Coding-Agenten

**Dokumenttyp:** Verbindliche Implementierungs-, Migrations- und Abnahmespezifikation  
**Ziel-Repository:** `edigonzales/ili2grails`  
**Zielbasis:** aktueller `main`-Stand **nach vollständig integriertem P0**  
**Stand der Spezifikation:** 2026-08-01  
**Primäre Implementierungssprachen:** Java 17, Groovy 4, Grails 7  
**Geltungsbereich:** Core-IR, ili2db-Reader, Grails-Runtime, Generator-/Runtime-Verträge, Tests und Buildstruktur  
**Nicht Gegenstand:** visuelles Redesign, neue fachliche CRUD-Funktionen, Unterstützung zusätzlicher Ziel-Frameworks

---

## 1. Auftrag an den Coding-Agenten

Implementiere die in diesem Dokument definierten P1-Arbeitspakete vollständig, schrittweise und mit überprüfbaren Zwischenständen:

1. **P1-A – Extraktion der Grails-Runtime in ein echtes Grails-Plugin**
2. **P1-B – Zerlegung des monolithischen `Ili2dbMetadataReader` in klar getrennte Reader-, Introspektions- und Assembly-Komponenten**
3. **P1-C – Einführung einer kanonischen, validierten und nach Erzeugung unveränderlichen Core-IR**
4. **P1-D – Ablösung untypisierter Runtime-Maps durch typisierte Deskriptoren, Registries und Command-Resultate**

Diese vier Arbeitspakete sind als zusammenhängende Architekturarbeit zu behandeln:

- Die Grails-Runtime darf nicht länger als kopierter Framework-Code in jeder generierten Anwendung leben.
- Der ili2db-Reader darf nicht gleichzeitig SQL-Katalogzugriff, JDBC-Schemaanalyse, PostGIS-Erkennung, Mapping und IR-Mutation verantworten.
- Die Core-IR darf nach Abschluss von Reading, Merge und Validation nicht mehr durch Getter, Lazy-Inference oder geteilte mutable Collections veränderbar sein.
- Generierte Runtime-Metadaten dürfen nicht mehr als verschachtelte `Map<String, Object>`-Strukturen zwischen Generator, Registry, Services und Controller weitergereicht werden.

P1 ist abgeschlossen, wenn die generierte Anwendung weiterhin dieselben fachlichen CRUD-, Association-, Inverse-Relationship-, Workspace- und UI-Funktionen bereitstellt, der Framework-Code aber über ein versioniertes Plugin kommt und die internen Verträge typisiert und validiert sind.

---

# 2. Verbindliche Voraussetzungen

## 2.1 P0 muss vollständig vorhanden sein

Der Agent muss vor der ersten P1-Änderung überprüfen, dass die P0-Invarianten tatsächlich im Arbeitsstand vorhanden sind.

Mindestens zu prüfen:

- deterministischer `MetadataMerger` mit strukturierter Diagnostik;
- keine First-Match-wins-Logik im Metadata-Merge;
- präzise `ModelSelection`;
- sichere SQL-Identifier-Behandlung;
- normale inverse `MANY_TO_ONE`-Beziehungen erzeugen kein synthetisches GORM-`hasMany`;
- realer Grails-/PostgreSQL-/ili2pg-Vertragstest existiert und ist grün.

Der Agent darf P1 nicht still auf einem Stand implementieren, der P0 nur teilweise enthält.

Falls P0 fehlt:

1. aktuellen Branch und Commit dokumentieren;
2. nach vorhandenem P0-Branch oder P0-Commit suchen;
3. P1 auf einer P0-vollständigen Basis starten;
4. nicht versuchen, P0 und P1 in einem untrennbaren Grosscommit nachzubauen.

## 2.2 Baseline

Vor Änderungen ausführen:

```bash
git switch main
git pull --ff-only
git status --short
./gradlew clean test --no-daemon
```

Danach, sofern verfügbar:

```bash
PATH="$HOME/.sdkman/candidates/grails/current/bin:$PATH" \
  ./gradlew :target-grails:grailsRuntimeSmokeTest --no-daemon

PATH="$HOME/.sdkman/candidates/grails/current/bin:$PATH" \
  ./gradlew :target-grails:realIli2dbSmokeTest --no-daemon

PATH="$HOME/.sdkman/candidates/grails/current/bin:$PATH" \
  ./gradlew :target-grails:grailsPostgresContractTest \
  -PcontractTestRequired=true --no-daemon
```

Neuen Branch anlegen:

```bash
git switch -c refactor/p1-runtime-ir-architecture
```

Fortschrittsdatei anlegen:

```text
docs/implementation/p1-runtime-ir-architecture-progress.md
```

Sie muss fortlaufend enthalten:

- Ausgangscommit;
- P0-Verifikation;
- Java-, Gradle-, Groovy-, Grails- und ili2pg-Version;
- Baseline-Testzahlen;
- Architekturentscheidungen;
- Migrationsentscheidungen;
- bewusst erhaltene Kompatibilitätsadapter;
- entfernte Legacy-Dateien;
- geänderte Snapshots;
- Testresultate;
- bekannte Restpunkte.

---

# 3. Verifizierter Ist-Zustand und Hauptprobleme

## 3.1 Buildstruktur

Aktuell existieren die Module:

```text
core
target-grails
target-django
cli
```

`target-grails` ist ein normales `java-library`-Modul. Die eigentliche Grails-Runtime liegt nicht als Pluginmodul vor, sondern als Ressourcenbaum innerhalb des Generators.

## 3.2 Overlay-Installer

Betroffene Klasse:

```text
target-grails/src/main/java/ch/interlis/generator/grails/
    GrailsTemplateOverlayInstaller.java
```

Sie kopiert derzeit unter anderem:

- `InterlisCrudControllerSupport.groovy`;
- Form-, Geometry-, List-, Message-, Navigation- und Workspace-Support;
- Association- und Inverse-Relationship-Support;
- vier Runtime-Services;
- Runtime-Controller und TagLib;
- `interlisUi`-Views;
- Runtime-JavaScript und CSS;
- Fonts;
- I18n-Bundles;
- Spring-`resources.groovy`;
- Scaffolding-Templates und Partials.

Dadurch wird Framework-Code in jede Anwendung kopiert und bei erneuter Generierung potentiell überschrieben.

## 3.3 Untypisierte Runtime-Verträge

Beispiele:

```groovy
Map<String, Object> descriptor
Map<String, Object> context
Map<String, Object> association
List<Map<String, Object>> roles
Map<String, Object> result
```

Aktuelle Hotspots:

```text
InterlisUiDescriptorSupport.groovy
InterlisAssociationRegistrySupport.groovy
InterlisInverseRelationshipSupport.groovy
InterlisAssociationQueryService.groovy
InterlisAssociationCommandService.groovy
InterlisInverseRelationshipQueryService.groovy
InterlisInverseRelationshipCommandService.groovy
InterlisCrudControllerSupport.groovy
```

Die generierten Registries erzeugen ebenfalls Maps:

```text
GrailsUiRegistryGenerator.java
GrailsAssociationRegistryGenerator.java
GrailsDomainGenerator.java
```

Aktuelle statische Domain-Metadaten:

```groovy
static final Map<String, Map<String, Object>> geometryMeta
static final Map<String, Map<String, Object>> interlisFieldMeta
static final Map<String, Object> interlisDisplayMeta
static final Map<String, Map<String, Object>> interlisRelationshipMeta
static final Map<String, Map<String, Object>> interlisInverseRelationshipMeta
```

## 3.4 Mutable Core-IR

Aktuelle Klassen:

```text
ModelMetadata
ClassMetadata
AttributeMetadata
RelationshipMetadata
AssociationMetadata
AssociationRoleMetadata
EnumMetadata
```

Probleme:

- öffentliche Setter;
- mutable Maps und Listen werden direkt zurückgegeben;
- `ModelMetadata.getAllRelationships()` baut bei jedem Aufruf eine deduplizierte Mischung aus globalen und klassenlokalen Relationships;
- `ModelMetadata.addRelationship()` mutiert gleichzeitig Root- und Class-Strukturen;
- `AttributeMetadata.getJavaType()` kann durch Lazy-Inference den Objektzustand verändern;
- `AttributeMetadata.getCoreType()` kann aus mutablem Zustand dynamisch ableiten;
- Generatoren und Reader können dieselben Objekte weiter verändern;
- es existiert keine klare Freeze-Grenze zwischen Reading/Merge und Generation.

## 3.5 Monolithischer ili2db-Reader

Betroffene Klasse:

```text
core/src/main/java/ch/interlis/generator/reader/
    Ili2dbMetadataReader.java
```

Sie verantwortet derzeit gleichzeitig:

- Settings;
- Modellauswahl beziehungsweise Datenbankabgleich;
- Klassenmapping;
- Attributmapping;
- Enum-Domain-Erkennung;
- Enum-Tabellenwerte;
- JDBC-Spaltentypen;
- Nullability;
- Precision/Scale;
- Primärschlüssel;
- SQLite-PRAGMA;
- PostgreSQL-/PostGIS-Geometrien;
- Vererbung;
- Column Properties;
- FK-Relationship-Ableitung;
- Association-Ableitung;
- SQL-Dialekterkennung;
- Fehlerbehandlung;
- direkte Mutation der IR.

Das führt zu schwer testbaren und vermischten Fehlerpfaden sowie wiederholter Schema-Introspektion pro Attribut.

---

# 4. Zielarchitektur nach P1

```text
core
├── immutable IR
├── IR builders
├── validation/indexing
├── MetadataMerger
└── ili2c reader

core.reader.ili2db
├── Ili2dbMetadataReader            facade
├── Ili2dbReadCoordinator
├── Ili2dbCatalogReader
├── JdbcSchemaIntrospector
├── PostgisGeometryIntrospector
├── SqliteSchemaIntrospector
├── Ili2dbEnumReader
├── Ili2dbMetadataAssembler
├── Ili2dbRelationshipDeriver
├── Ili2dbAssociationDeriver
└── typed diagnostics/results

grails-runtime-api
├── typed descriptor records
├── registry interfaces
├── command/query result records
├── override/config models
└── no Grails dependency

grails-runtime
├── Grails plugin descriptor
├── runtime services
├── controller support
├── taglib/controller
├── plugin views
├── assets/fonts/i18n
├── default policies
└── startup descriptor validation

target-grails
├── generators
├── planners
├── typed registry source generation
├── scaffolding template installation
├── generated-app dependency injection
└── legacy-runtime migration
```

Die zentrale Regel lautet:

> Reader und Merger dürfen mutable Builder verwenden. Nach `buildValidated()` existiert nur noch eine unveränderliche kanonische IR. Generatoren dürfen diese IR lesen, aber nicht verändern. Die Grails-Runtime konsumiert ausschliesslich typisierte, generierte Deskriptoren aus `grails-runtime-api`.

---

# 5. P1-A – Grails-Runtime als echtes Plugin

## 5.1 Ziel

Die generierte Grails-Anwendung soll den Runtime-Code als versionierte Plugin-Abhängigkeit verwenden.

Nach P1 darf der Generator folgende Klassen **nicht** mehr in die Zielanwendung kopieren:

```text
src/main/groovy/ch/interlis/generator/grails/runtime/**
grails-app/services/ch/interlis/generator/grails/runtime/**
grails-app/controllers/ch/interlis/generator/grails/runtime/InterlisUiController.groovy
grails-app/taglib/ch/interlis/generator/grails/runtime/InterlisUiTagLib.groovy
grails-app/views/interlisUi/**
grails-app/assets/javascripts/ili-*.js
grails-app/assets/stylesheets/ili-modern.css
grails-app/assets/fonts/**
```

Diese Dateien werden Plugin-Artefakte.

Scaffolding-Templates bleiben Generation-Time-Ressourcen und dürfen weiterhin kontrolliert in die Zielanwendung installiert werden.

---

## 5.2 Neue Module

`settings.gradle` ergänzen:

```groovy
include 'grails-runtime-api'
include 'grails-runtime'
```

### Modul `grails-runtime-api`

Pfad:

```text
grails-runtime-api/
```

Buildtyp:

```groovy
plugins {
    id 'java-library'
}
```

Eigenschaften:

- Java 17;
- keine Grails-, GORM-, Servlet- oder Spring-Abhängigkeit;
- Jackson-Annotations nur, wenn sie für JSON-Kompatibilität wirklich benötigt werden;
- `api` für alle Deskriptor- und Resulttypen;
- eigene Unit-Tests.

### Modul `grails-runtime`

Pfad:

```text
grails-runtime/
```

Es muss ein echtes Grails-Web-Plugin sein.

Der Agent soll die tatsächlich vom Repository erzeugte Grails-7-App beziehungsweise den Grails-7-Plugin-Scaffold als Versionsquelle verwenden. Keine zufällige Mischung aus Grails-6- und Grails-7-Gradle-Plugins.

Verbindliche Anforderungen:

- Plugin-Descriptor unter `src/main/groovy`;
- Plugin kann als JAR gebaut werden;
- `publishToMavenLocal` funktioniert;
- Plugin lässt sich als Abhängigkeit in eine frisch erzeugte Grails-7-Anwendung einbinden;
- Plugin enthält Services, Controller, TagLib, Views, Assets und I18n;
- Plugin enthält keine Test-/Demo-Anwendung im veröffentlichten JAR;
- Plugin hängt von `project(':grails-runtime-api')` ab.

Empfohlene Coordinates:

```text
groupId:    ch.interlis.generator
artifactId: ili2grails-runtime
version:    identisch zur Root-Projektversion
```

---

## 5.3 Plugin-Descriptor

Neue Klasse:

```text
grails-runtime/src/main/groovy/ch/interlis/generator/grails/runtime/
    Ili2grailsRuntimeGrailsPlugin.groovy
```

Mindestskelett:

```groovy
package ch.interlis.generator.grails.runtime

import grails.plugins.Plugin

class Ili2grailsRuntimeGrailsPlugin extends Plugin {

    def grailsVersion = '7.0.0 > *'
    def profiles = ['web']
    def title = 'ili2grails Runtime'
    def description = 'Runtime services and generic UI support for ili2grails generated applications.'

    Closure doWithSpring() {
        { ->
            // conditional/default beans
        }
    }

    void doWithApplicationContext() {
        // validate generated registries and descriptors once
    }
}
```

Der konkrete unterstützte Grails-Versionsbereich muss aus der im Projekt verwendeten Grails-Version abgeleitet und in Tests abgesichert werden.

### Verantwortlichkeiten

`doWithSpring()` registriert nur Default-Beans, wenn die Anwendung keine eigene Bean desselben Vertrags bereitstellt.

`doWithApplicationContext()`:

1. findet generierte Registry-Beans beziehungsweise Registry-Klassen;
2. baut einen `InterlisRuntimeRegistry`;
3. validiert alle Deskriptoren gegen `GrailsApplication.mappingContext`;
4. schreibt eine kompakte Startup-Zusammenfassung;
5. bricht im Strict-Modus bei fehlerhaften generierten Verträgen ab.

Kein aufwendiges per-request Reflection-Scanning.

---

## 5.4 Typisierte Plugin-Konfiguration

Neue Klasse:

```text
grails-runtime/src/main/groovy/ch/interlis/generator/grails/runtime/config/
    Ili2grailsRuntimeProperties.groovy
```

```groovy
package ch.interlis.generator.grails.runtime.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = 'ili2grails.runtime')
class Ili2grailsRuntimeProperties {
    boolean strictDescriptorValidation = true
    boolean applySecurityHeaders = true
    int defaultPageSize = 20
    int maximumPageSize = 100
    String defaultLocale = 'de-CH'
    UiProperties ui = new UiProperties()

    static class UiProperties {
        String appTitle = 'INTERLIS CRUD'
        String appLogo
        String appLogoIcon = 'grid'
    }
}
```

Bestehende Konfiguration unter `ili2grails.ui` darf nicht abrupt unbrauchbar werden.

Implementiere eine klar definierte Kompatibilitätsschicht:

```groovy
final class LegacyUiConfigurationAdapter {
    RuntimeUiOverrides adapt(Config config)
}
```

Priorität:

1. neue typisierte Runtime-Properties;
2. bestehende `ili2grails.ui`-Konfiguration als kompatibler Override-Pfad;
3. Plugin-Defaults.

Konflikte müssen diagnostiziert werden; nicht still unterschiedliche Werte verwenden.

---

## 5.5 Erweiterungspunkte statt Editieren kopierter Runtime-Dateien

### 5.5.1 Authorization Policy

Neue Schnittstelle in `grails-runtime-api`:

```java
package ch.interlis.generator.grails.runtime.api.security;

public interface InterlisAuthorizationPolicy {

    boolean canView(DomainOperationContext context);

    boolean canCreate(DomainOperationContext context);

    boolean canUpdate(DomainOperationContext context, Object instance);

    boolean canDelete(DomainOperationContext context, Object instance);

    boolean canCreateAssociation(
        AssociationOperationContext context,
        Object participant,
        Object target
    );

    boolean canDeleteAssociation(
        AssociationOperationContext context,
        Object participant,
        Object associationInstance
    );

    boolean canAssignInverseRelationship(
        InverseRelationshipOperationContext context,
        Object owner,
        Object related
    );

    boolean canReassignInverseRelationship(
        InverseRelationshipOperationContext context,
        Object owner,
        Object previousOwner,
        Object related
    );
}
```

Default-Implementierung im Plugin:

```groovy
final class AllowAllInterlisAuthorizationPolicy
        implements InterlisAuthorizationPolicy {
    // all methods return true
}
```

Die Default-Implementierung erhält das aktuelle Verhalten, aber die Services dürfen keine geschützten `can...()`-Methoden mehr als primären Customizing-Mechanismus anbieten.

### 5.5.2 Lifecycle Hooks

Neue Schnittstelle:

```java
public interface InterlisLifecycleHooks {

    default void beforeCreate(DomainOperationContext context, Object instance) {}

    default void afterCreate(DomainOperationContext context, Object instance) {}

    default void beforeUpdate(DomainOperationContext context, Object instance) {}

    default void afterUpdate(DomainOperationContext context, Object instance) {}

    default void beforeDelete(DomainOperationContext context, Object instance) {}

    default void afterDelete(DomainOperationContext context, Object identifier) {}

    default void beforeAssociationCreate(
        AssociationOperationContext context,
        Object participant,
        Object target
    ) {}

    default void afterAssociationCreate(
        AssociationOperationContext context,
        Object associationInstance
    ) {}
}
```

Default: No-op.

Hooks dürfen keine fehlenden Sicherheitsprüfungen ersetzen.

### 5.5.3 Display Label Resolver

Neue Schnittstelle:

```java
public interface InterlisDisplayLabelResolver {
    String labelFor(Object domainInstance, DomainDescriptor descriptor);
}
```

Default-Implementierung verwendet die generierten `displayFields` und fällt auf ID zurück.

---

## 5.6 Ownership-Matrix der Dateien

Der Agent muss eine explizite Manifestklasse einführen:

```text
target-grails/src/main/java/ch/interlis/generator/grails/project/
    GrailsProjectFileOwnership.java
```

```java
public enum GrailsProjectFileOwner {
    RUNTIME_PLUGIN,
    GENERATOR_MANAGED,
    APPLICATION_OWNED,
    LEGACY_RUNTIME
}
```

```java
public record GrailsProjectFileRule(
    String relativePath,
    GrailsProjectFileOwner owner,
    boolean overwriteAllowed,
    boolean deleteWhenMigrating
) {
}
```

### Plugin-owned

- Runtime-Groovy-Support;
- Runtime-Services;
- UI-Controller;
- TagLib;
- `interlisUi`-Views;
- Runtime-JS/CSS;
- Fonts;
- Default-I18n;
- Plugin-interne Spring-Beans.

### Generator-managed

- `src/main/templates/scaffolding/**`;
- generierte Domainklassen;
- generierte Controller;
- generierte Registries;
- generierte projektspezifische Konfiguration;
- Runtime-Plugin-Dependency-Eintrag;
- Asset-Require-Einträge, soweit die Host-App sie braucht.

### Application-owned

- eigene `application.yml` ausserhalb markierter Blöcke;
- eigene Security-Konfiguration;
- eigene Policy-Beans;
- eigene View-Overrides;
- eigenes `main.gsp`, sofern bewusst angelegt oder verändert;
- eigene Message-Overrides.

Kein application-owned File darf pauschal überschrieben werden.

---

## 5.7 Aufteilung des bisherigen Overlay-Installers

`GrailsTemplateOverlayInstaller` wird abgelöst durch kleine Komponenten:

```text
target-grails/src/main/java/ch/interlis/generator/grails/project/
    GrailsProjectCustomizer.java
    GrailsScaffoldingTemplateInstaller.java
    GrailsRuntimeDependencyInstaller.java
    GrailsAssetManifestUpdater.java
    GrailsApplicationConfigurationUpdater.java
    LegacyRuntimeScanner.java
    LegacyRuntimeMigrator.java
    ProjectCustomizationResult.java
    ProjectCustomizationDiagnostic.java
```

### `GrailsProjectCustomizer`

```java
public final class GrailsProjectCustomizer {

    public GrailsProjectCustomizer(
        GrailsScaffoldingTemplateInstaller templateInstaller,
        GrailsRuntimeDependencyInstaller dependencyInstaller,
        GrailsAssetManifestUpdater assetUpdater,
        GrailsApplicationConfigurationUpdater configUpdater,
        LegacyRuntimeMigrator legacyMigrator
    );

    public ProjectCustomizationResult customize(
        Path grailsProjectDir,
        GenerationConfig config,
        RuntimeCoordinates runtimeCoordinates
    ) throws IOException;
}
```

Ablauf:

1. Projektstruktur validieren;
2. Legacy-Scan;
3. sichere Legacy-Migration;
4. Plugin-Dependency installieren;
5. Generation-Time-Templates installieren;
6. Assets-Manifest anpassen;
7. minimale App-Konfiguration ergänzen;
8. Ergebnis mit Diagnostics zurückgeben.

### `RuntimeCoordinates`

```java
public record RuntimeCoordinates(
    String group,
    String artifact,
    String version
) {
    public String notation() {
        return group + ":" + artifact + ":" + version;
    }
}
```

Koordinaten dürfen nicht mehrfach als freie Strings im Generator vorkommen.

---

## 5.8 Build-Dependency-Injektion

### `GrailsRuntimeDependencyInstaller`

```java
public final class GrailsRuntimeDependencyInstaller {

    public DependencyUpdateResult install(
        Path buildFile,
        RuntimeCoordinates coordinates
    ) throws IOException;
}
```

Anforderungen:

- bestehende Dependency erkennen;
- idempotent;
- Version gezielt aktualisieren;
- keine zweite Dependency ergänzen;
- keine freien Regex-Ersetzungen über das ganze Buildfile;
- markierten Managed-Block verwenden, falls keine robuste Gradle-AST-Lösung vorhanden ist.

Empfohlener Block:

```groovy
// <ili2grails-runtime-dependency>
dependencies {
    implementation "ch.interlis.generator:ili2grails-runtime:1.0.0-SNAPSHOT"
}
// </ili2grails-runtime-dependency>
```

Falls die Anwendung bereits einen `dependencies`-Block besitzt, darf kein verschachtelter ungültiger Block erzeugt werden. Der Agent muss eine kleine strukturierte Gradle-Datei-Transformation implementieren und umfassend testen.

Besserer zulässiger Ansatz:

- vorhandenen Top-Level-`dependencies`-Block lokalisieren;
- Managed-Zeile innerhalb dieses Blocks einfügen;
- Marker nur um die einzelne Dependency-Zeile legen.

Beispiel:

```groovy
dependencies {
    // <ili2grails-runtime-dependency>
    implementation "ch.interlis.generator:ili2grails-runtime:1.0.0-SNAPSHOT"
    // </ili2grails-runtime-dependency>
}
```

Repository-Konfiguration:

- Release/Snapshot-Repository nur ergänzen, wenn erforderlich;
- `mavenLocal()` ausschliesslich in Test-/Development-Harness, nicht ungefragt in produktiv generierten Apps;
- Tests dürfen ein temporäres Maven-Repository verwenden.

---

## 5.9 Legacy-Runtime-Migration

### `LegacyRuntimeScanner`

```java
public final class LegacyRuntimeScanner {

    public LegacyRuntimeScanResult scan(Path projectDir) throws IOException;
}
```

```java
public record LegacyRuntimeScanResult(
    List<LegacyFileMatch> knownUnmodifiedFiles,
    List<LegacyFileMatch> modifiedFiles,
    List<Path> unknownRuntimeFiles
) {
    public boolean requiresManualIntervention();
}
```

```java
public record LegacyFileMatch(
    Path relativePath,
    String actualSha256,
    Set<String> knownSha256Values
) {
}
```

Der Generator muss bekannte Legacy-Runtime-Ressourcen für die Migration weiterhin in einem klar getrennten Ressourcenbereich aufbewahren:

```text
target-grails/src/main/resources/grails/migration/legacy-runtime-v1/**
```

### Regeln

- Datei fehlt → nichts tun.
- Datei entspricht exakt einem bekannten Generator-Stand → löschen.
- Datei ist verändert → nicht löschen, nicht überschreiben.
- Veränderte Datei → blockierende Diagnostic mit Pfad und Migrationshinweis.
- Kein automatisches Verschieben oder Überschreiben user-modifizierter Runtime-Dateien.
- Nach erfolgreicher Migration darf im Zielprojekt keine Klasse aus `ch.interlis.generator.grails.runtime` mehr lokal vorhanden sein.

### `LegacyRuntimeMigrator`

```java
public final class LegacyRuntimeMigrator {

    public LegacyMigrationResult migrate(
        Path projectDir,
        LegacyRuntimeScanResult scanResult,
        LegacyMigrationPolicy policy
    ) throws IOException;
}
```

```java
public enum LegacyMigrationPolicy {
    STRICT,
    REPORT_ONLY
}
```

Im normalen Generatorlauf: `STRICT`.

---

## 5.10 Plugin-Views, Assets und Overrides

### Views

Plugin liefert:

```text
grails-runtime/grails-app/views/interlisUi/**
```

Anwendungen dürfen durch gleichnamige Views unter `grails-app/views/interlisUi/**` überschreiben.

Die Runtime darf beim Rendern von Plugin-Templates nicht versehentlich ausschliesslich im App-Pfad suchen. Alle `g:render`-Aufrufe sind auf korrekte Plugin-/Fallback-Auflösung zu testen.

### Layout

Keine visuelle Änderung in P1.

Bevorzugte Lösung:

- Plugin liefert ein Default-Layout `layouts/ili2grails.gsp`;
- generierte App kann `main.gsp` behalten oder gezielt auf dieses Layout delegieren;
- bestehende Ausgabe muss snapshot-/browserseitig gleich bleiben.

### Assets

Plugin liefert:

```text
grails-runtime/grails-app/assets/javascripts/ili-geometry-editor.js
grails-runtime/grails-app/assets/javascripts/ili-form-ux.js
grails-runtime/grails-app/assets/javascripts/ili-notifications.js
grails-runtime/grails-app/assets/javascripts/ili-navigation.js
grails-runtime/grails-app/assets/stylesheets/ili-modern.css
grails-runtime/grails-app/assets/fonts/**
```

Der Generator ergänzt nur die notwendigen Require-Zeilen in `application.js` und `application.css`, falls das aktuelle Asset-Pipeline-Setup dies verlangt.

Tests müssen beweisen:

- Assets sind aus dem Plugin-JAR verfügbar;
- keine Duplikate im App-Projekt;
- Browser-E2E lädt JS/CSS ohne 404;
- Fonts werden korrekt ausgeliefert.

### I18n

Plugin liefert Default-Bundles:

```text
grails-runtime/grails-app/i18n/messages_de_CH.properties
grails-runtime/grails-app/i18n/messages_en.properties
```

App-Bundles dürfen dieselben Keys überschreiben.

Der bisherige Merge in die App-`messages.properties` ist nach P1 nicht mehr Standard.

---

## 5.11 Controller-Support zerlegen

`InterlisCrudControllerSupport<T>` bleibt aus Kompatibilitätsgründen die Basisklasse generierter Controller, wird aber zu einer dünnen Delegationsschicht.

Neue Plugin-Komponenten:

```text
grails-runtime/src/main/groovy/ch/interlis/generator/grails/runtime/controller/
    InterlisCrudControllerSupport.groovy
    InterlisListControllerFlow.groovy
    InterlisFormControllerFlow.groovy
    InterlisAssociationControllerFlow.groovy
    InterlisInverseRelationshipControllerFlow.groovy
    InterlisRelationshipOptionsControllerFlow.groovy
    InterlisControllerResponseSupport.groovy
    InterlisSecurityHeaderSupport.groovy
    InterlisControllerContext.groovy
```

### `InterlisControllerContext<T>`

```groovy
@CompileStatic
final class InterlisControllerContext<T> {
    Class<T> domainType
    Object crudService
    GrailsApplication grailsApplication
    InterlisRuntimeRegistry runtimeRegistry
    InterlisAuthorizationPolicy authorizationPolicy
}
```

### Basisklasse

Öffentliche Actions bleiben namentlich stabil:

```groovy
def index(Integer max, Integer offset)
def show(Long id)
def create()
def save()
def edit(Long id)
def update(Long id)
def delete(Long id)
def relationshipOptions()
def relationshipCollectionPage(Long id)
def relationshipCollectionOptions(Long id)
def relationshipAssign(Long id)
def associationPage(Long id)
def associationOptions(Long id)
def associationCreate(Long id)
def associationDelete(Long id)
```

Jede Action soll primär:

1. Security Headers anwenden;
2. Request in typisierten Request-/Context-Typ überführen;
3. Flow aufrufen;
4. typisiertes Ergebnis rendern.

Keine erneute vollständige Controller-Neugestaltung. URLs und Response-Codes bleiben stabil.

---

## 5.12 Plugin-Tests

Neue Testebenen:

### Unit

```text
grails-runtime/src/test/groovy/**
```

- Config Binding;
- Default Policies;
- Registry Validation;
- Support-Komponenten;
- Command-Result-Serialisierung;
- View-/Asset-Ressourcen vorhanden.

### Integration

```text
grails-runtime/src/integration-test/groovy/**
```

- Services als Spring Beans;
- Controller/TagLib registriert;
- Plugin-Views auflösbar;
- I18n-Override durch Host-App;
- eigene `InterlisAuthorizationPolicy` überschreibt Default;
- eigene Hooks werden injiziert.

### Consumer Contract

Der bestehende temporäre Grails-App-Test muss geändert werden:

1. `grails-runtime` in temporäres Maven-Repository publizieren;
2. generierte App mit Plugin-Dependency erstellen;
3. sicherstellen, dass keine lokalen Runtime-Klassen kopiert wurden;
4. App kompilieren;
5. Integrationstests ausführen;
6. Browser-E2E ausführen.

Obligatorische Assertion:

```text
find generated-app/src/main/groovy/ch/interlis/generator/grails/runtime -type f
```

muss leer sein beziehungsweise der Pfad darf nicht existieren.

---

# 6. P1-B – `Ili2dbMetadataReader` zerlegen

## 6.1 Ziel

`Ili2dbMetadataReader` bleibt als öffentliche Fassade erhalten, enthält aber keine SQL-Details, Dialektlogik oder IR-Mutation mehr.

Nach P1 soll die Klasse höchstens ungefähr 100–180 Zeilen umfassen und primär delegieren.

---

## 6.2 Neue Paketstruktur

```text
core/src/main/java/ch/interlis/generator/reader/ili2db/
    Ili2dbMetadataReader.java
    Ili2dbReadCoordinator.java
    Ili2dbReadRequest.java
    Ili2dbReadResult.java
    Ili2dbReadContext.java
    Ili2dbReadException.java
    Ili2dbDiagnostic.java
    Ili2dbDiagnosticCode.java
    Ili2dbSeverity.java

    catalog/
        Ili2dbCatalogReader.java
        Ili2dbCatalogSnapshot.java
        Ili2dbCatalogCapabilities.java
        Ili2dbTableRequirement.java
        Ili2dbTableAvailability.java
        ClassMappingRow.java
        AttributeMappingRow.java
        InheritanceRow.java
        ColumnPropertyRow.java
        TablePropertyRow.java
        ModelRow.java
        EnumDomainRow.java

    schema/
        DatabaseDialect.java
        DatabaseDialectDetector.java
        JdbcSchemaIntrospector.java
        JdbcSchemaSnapshot.java
        TableSchema.java
        ColumnSchema.java
        PrimaryKeySchema.java
        ForeignKeySchema.java
        SqliteSchemaIntrospector.java
        PostgisGeometryIntrospector.java
        GeometrySchemaSnapshot.java
        GeometryColumnSchema.java

    assemble/
        Ili2dbMetadataAssembler.java
        Ili2dbAttributeAssembler.java
        Ili2dbEnumAssembler.java
        Ili2dbRelationshipDeriver.java
        Ili2dbAssociationDeriver.java
        Ili2dbAssemblyResult.java
```

Falls P0 bereits SQL-Identifier-Klassen in einem anderen Paket eingeführt hat, sind diese wiederzuverwenden. Keine zweite Identifier-Abstraktion anlegen.

---

## 6.3 Öffentliche Fassade

Bestehende Importpfade möglichst kompatibel halten.

Falls die Klasse aktuell unter `ch.interlis.generator.reader.Ili2dbMetadataReader` liegt, soll eine delegierende Fassade dort verbleiben:

```java
package ch.interlis.generator.reader;

public final class Ili2dbMetadataReader {

    private final ch.interlis.generator.reader.ili2db.Ili2dbMetadataReader delegate;

    public Ili2dbMetadataReader(
        Connection connection,
        String schemaName
    ) throws SQLException;

    public ModelMetadata readMetadata(String modelName)
        throws SQLException;

    public ModelMetadata readMetadata(ModelSelection selection)
        throws SQLException;

    public Ili2dbReadResult read(
        Ili2dbReadRequest request
    ) throws SQLException;
}
```

Die neue primäre API ist `read(Ili2dbReadRequest)`.

---

## 6.4 Request, Context und Result

### `Ili2dbReadRequest`

```java
public record Ili2dbReadRequest(
    ModelSelection modelSelection,
    String schemaName,
    Ili2dbFailurePolicy failurePolicy,
    boolean includeEnumValues,
    boolean includeGeometryMetadata
) {
    public Ili2dbReadRequest {
        Objects.requireNonNull(modelSelection, "modelSelection");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
    }

    public static Ili2dbReadRequest strict(
        ModelSelection selection,
        String schemaName
    );
}
```

### `Ili2dbFailurePolicy`

```java
public enum Ili2dbFailurePolicy {
    STRICT,
    DIAGNOSTIC
}
```

### `Ili2dbReadContext`

```java
public record Ili2dbReadContext(
    Connection connection,
    ModelSelection modelSelection,
    SqlIdentifier schema,
    SqlIdentifierRenderer identifiers,
    DatabaseDialect dialect,
    Ili2dbFailurePolicy failurePolicy
) {
}
```

### `Ili2dbReadResult`

```java
public record Ili2dbReadResult(
    ModelMetadata metadata,
    Ili2dbCatalogSnapshot catalog,
    JdbcSchemaSnapshot schema,
    GeometrySchemaSnapshot geometry,
    List<Ili2dbDiagnostic> diagnostics
) {
    public boolean hasBlockingDiagnostics();
    public List<Ili2dbDiagnostic> blockingDiagnostics();
    public void throwIfBlocking();
}
```

Das Resultat enthält nur immutable Objekte.

---

## 6.5 Diagnostik

### Severity

```java
public enum Ili2dbSeverity {
    INFO,
    WARNING,
    ERROR,
    FATAL
}
```

### Codes

Mindestens:

```java
public enum Ili2dbDiagnosticCode {
    REQUIRED_META_TABLE_MISSING,
    OPTIONAL_META_TABLE_MISSING,
    META_TABLE_COLUMNS_UNSUPPORTED,
    REQUESTED_MODEL_MISSING,
    SELECTED_DEPENDENCY_MISSING,
    CLASS_MAPPING_INCOMPLETE,
    ATTRIBUTE_OWNER_UNRESOLVED,
    TARGET_CLASS_UNRESOLVED,
    COLUMN_SCHEMA_MISSING,
    ENUM_DOMAIN_UNRESOLVED,
    ENUM_TABLE_UNREADABLE,
    INHERITANCE_UNRESOLVED,
    GEOMETRY_METADATA_UNAVAILABLE,
    PRIMARY_KEY_ASSUMED,
    DUPLICATE_PHYSICAL_CLASS,
    DUPLICATE_PHYSICAL_COLUMN,
    ASSOCIATION_MAPPING_INCOMPLETE,
    DATABASE_DIALECT_UNSUPPORTED
}
```

### `Ili2dbDiagnostic`

```java
public record Ili2dbDiagnostic(
    Ili2dbSeverity severity,
    Ili2dbDiagnosticCode code,
    String message,
    String iliElement,
    String physicalElement,
    Map<String, String> details
) {
    public boolean isBlocking();
}
```

Logs sind Zusatzinformation. Fachliche Fehler dürfen nicht nur geloggt und vergessen werden.

---

## 6.6 `Ili2dbCatalogReader`

```java
public final class Ili2dbCatalogReader {

    public Ili2dbCatalogSnapshot read(
        Ili2dbReadContext context
    ) throws SQLException;

    Ili2dbCatalogCapabilities detectCapabilities(
        Ili2dbReadContext context
    ) throws SQLException;

    Map<String, String> readSettings(
        Ili2dbReadContext context,
        Ili2dbCatalogCapabilities capabilities
    ) throws SQLException;

    List<ModelRow> readModels(...);

    List<ClassMappingRow> readClasses(...);

    List<AttributeMappingRow> readAttributes(...);

    List<InheritanceRow> readInheritance(...);

    List<ColumnPropertyRow> readColumnProperties(...);

    List<EnumDomainRow> readEnumDomains(...);
}
```

### Keine IR-Erzeugung

Der CatalogReader erzeugt ausschliesslich typed Rows/Snapshots.

Verboten:

```java
new ClassMetadata(...)
new AttributeMetadata(...)
metadata.addClass(...)
```

### Capabilities

```java
public record Ili2dbCatalogCapabilities(
    Set<String> availableTables,
    Map<String, Set<String>> columnsByTable
) {
    public boolean hasTable(String name);
    public boolean hasColumn(String table, String column);
}
```

Capabilities werden einmal ermittelt und wiederverwendet.

### Table Requirements

```java
public enum Ili2dbTableRequirement {
    REQUIRED,
    OPTIONAL
}
```

Verbindliche Einordnung:

| Tabelle | Requirement |
|---|---|
| `t_ili2db_classname` | REQUIRED |
| `t_ili2db_attrname` | REQUIRED |
| `t_ili2db_table_prop` | REQUIRED für verlässliche Klassenart |
| `t_ili2db_settings` | OPTIONAL |
| `t_ili2db_model` | OPTIONAL, nur Validierung/Abgleich |
| `t_ili2db_inheritance` | OPTIONAL |
| `t_ili2db_column_prop` | OPTIONAL |
| `t_ili2db_trafo` | OPTIONAL, nur falls später genutzt |

Fehlt eine erforderliche Tabelle, entsteht `FATAL`.

---

## 6.7 Typed Catalog Rows

### `ClassMappingRow`

```java
public record ClassMappingRow(
    String iliName,
    String tableName,
    String tableKind
) {
}
```

### `AttributeMappingRow`

```java
public record AttributeMappingRow(
    String iliName,
    String sqlName,
    String owner,
    String target
) {
}
```

### `ColumnPropertyRow`

```java
public record ColumnPropertyRow(
    String ownerTable,
    String columnName,
    String tag,
    String setting
) {
}
```

### `EnumDomainRow`

```java
public record EnumDomainRow(
    String ownerTable,
    String columnName,
    String enumIliName,
    String enumTableName
) {
}
```

Alle Rows validieren nur grundlegende Null-/Blank-Invarianten. Fachliche Zusammenführung geschieht im Assembler.

---

## 6.8 JDBC-Schema-Introspektion

### Ziel

Keine Spalten-Introspektion pro Attribut.

Aktuell wird `resolveColumnInfo(table, column)` für viele Attribute einzeln aufgerufen. Nach P1 wird pro ausgewählter Tabelle ein Snapshot aufgebaut.

### `JdbcSchemaIntrospector`

```java
public interface JdbcSchemaIntrospector {

    JdbcSchemaSnapshot inspect(
        Ili2dbReadContext context,
        Collection<QualifiedSqlName> tables
    ) throws SQLException;
}
```

Default:

```java
public final class DefaultJdbcSchemaIntrospector
        implements JdbcSchemaIntrospector {

    public JdbcSchemaSnapshot inspect(...);

    TableSchema inspectTable(
        DatabaseMetaData metadata,
        QualifiedSqlName table
    ) throws SQLException;

    Map<String, ColumnSchema> inspectColumns(...);

    List<PrimaryKeySchema> inspectPrimaryKeys(...);

    List<ForeignKeySchema> inspectImportedKeys(...);
}
```

### `JdbcSchemaSnapshot`

```java
public final class JdbcSchemaSnapshot {

    private final Map<QualifiedSqlName, TableSchema> tables;

    public Optional<TableSchema> table(QualifiedSqlName name);

    public Optional<ColumnSchema> column(
        QualifiedSqlName table,
        String columnName
    );

    public Collection<TableSchema> tables();
}
```

Case-insensitive Lookup muss die echte Schreibweise erhalten.

### `TableSchema`

```java
public record TableSchema(
    QualifiedSqlName name,
    Map<String, ColumnSchema> columns,
    List<PrimaryKeySchema> primaryKeys,
    List<ForeignKeySchema> importedKeys
) {
    public Optional<ColumnSchema> column(String rawName);
    public boolean isPrimaryKey(String rawColumnName);
}
```

### `ColumnSchema`

```java
public record ColumnSchema(
    String name,
    Integer jdbcType,
    String databaseTypeName,
    boolean nullable,
    Integer size,
    Integer decimalDigits,
    Integer ordinalPosition
) {
}
```

Primärschlüssel nicht mehr ausschliesslich anhand `t_id` annehmen. `t_id` bleibt nur ein dokumentierter Fallback mit Diagnostic `PRIMARY_KEY_ASSUMED`.

---

## 6.9 Dialekte

### `DatabaseDialect`

```java
public enum DatabaseDialect {
    POSTGRESQL,
    H2,
    SQLITE,
    OTHER
}
```

### `DatabaseDialectDetector`

```java
public final class DatabaseDialectDetector {

    public DatabaseDialect detect(
        DatabaseMetaData metadata
    ) throws SQLException;
}
```

Keine wiederholten `isPostgreSql(connection)`-/`isSqlite(connection)`-Aufrufe.

### SQLite

SQLite-PRAGMA-Logik in:

```java
public final class SqliteSchemaIntrospector
        implements JdbcSchemaIntrospector
```

SQL-Identifier aus P0 wiederverwenden. Keine freie Stringkonkatenation.

---

## 6.10 PostGIS-Geometrie-Introspektion

### `PostgisGeometryIntrospector`

```java
public interface GeometryIntrospector {

    GeometrySchemaSnapshot inspect(
        Ili2dbReadContext context,
        Collection<QualifiedSqlName> selectedTables
    ) throws SQLException;
}
```

```java
public final class PostgisGeometryIntrospector
        implements GeometryIntrospector {

    public GeometrySchemaSnapshot inspect(...);
}
```

Anforderungen:

- genau eine oder wenige Batch-Queries gegen `geometry_columns`;
- kein `Find_SRID` pro Geometriespalte;
- ausgewählte Schemas/Tabellen filtern;
- Kind, SRID, Z und M erfassen;
- fehlende `geometry_columns` als WARNING, nicht zwingend FATAL;
- H2/SQLite erhalten leeren Snapshot über `NoOpGeometryIntrospector`.

### `GeometryColumnSchema`

```java
public record GeometryColumnSchema(
    QualifiedSqlName table,
    String columnName,
    GeometryKind kind,
    Integer srid,
    Boolean hasZ,
    Boolean hasM
) {
}
```

---

## 6.11 Enum Reader

### `Ili2dbEnumReader`

```java
public final class Ili2dbEnumReader {

    public EnumReadResult read(
        Ili2dbReadContext context,
        Ili2dbCatalogSnapshot catalog
    ) throws SQLException;
}
```

- Enum-Tabellenwerte einmal pro Tabelle lesen;
- Cache lokal pro Read-Lauf, nicht mutable Instanz-Map über mehrere Läufe;
- typed `EnumTableSnapshot`;
- unveränderliche Reihenfolge nach `seq`, dann Code;
- Fehler als Diagnostics;
- keine direkte Mutation von `AttributeMetadata`.

---

## 6.12 Assembler

### `Ili2dbMetadataAssembler`

```java
public final class Ili2dbMetadataAssembler {

    public Ili2dbAssemblyResult assemble(
        Ili2dbReadRequest request,
        Ili2dbCatalogSnapshot catalog,
        JdbcSchemaSnapshot schema,
        GeometrySchemaSnapshot geometry,
        EnumReadResult enums
    );
}
```

Erzeugt `ModelMetadataBuilder`, nicht unmittelbar fertige mutable IR.

Ablauf:

1. Modellbuilder anlegen;
2. Settings übernehmen;
3. Klassenbuilder aus `ClassMappingRow`;
4. Attribute über `Ili2dbAttributeAssembler`;
5. Vererbung anwenden;
6. Column Properties anwenden;
7. Relationships ableiten;
8. Associations ableiten;
9. Builder validieren und immutable IR bauen;
10. Diagnostics bündeln.

### `Ili2dbAttributeAssembler`

```java
public final class Ili2dbAttributeAssembler {

    public AttributeMetadataBuilder assemble(
        AttributeMappingRow row,
        ClassAssemblyContext owner,
        JdbcSchemaSnapshot schema,
        GeometrySchemaSnapshot geometry,
        EnumReadResult enums,
        DiagnosticCollector diagnostics
    );
}
```

### `Ili2dbRelationshipDeriver`

```java
public final class Ili2dbRelationshipDeriver {

    public List<RelationshipMetadataBuilder> derive(
        Collection<ClassMetadataBuilder> classes,
        DiagnosticCollector diagnostics
    );
}
```

Keine Association-Ableitung in dieser Klasse.

### `Ili2dbAssociationDeriver`

```java
public final class Ili2dbAssociationDeriver {

    public List<AssociationMetadataBuilder> derive(
        Collection<ClassMetadataBuilder> classes,
        Collection<RelationshipMetadataBuilder> relationships,
        DiagnosticCollector diagnostics
    );
}
```

---

## 6.13 Coordinator

```java
public final class Ili2dbReadCoordinator {

    public Ili2dbReadCoordinator(
        Ili2dbCatalogReader catalogReader,
        JdbcSchemaIntrospector schemaIntrospector,
        GeometryIntrospectorFactory geometryFactory,
        Ili2dbEnumReader enumReader,
        Ili2dbMetadataAssembler assembler
    );

    public Ili2dbReadResult read(
        Connection connection,
        Ili2dbReadRequest request
    ) throws SQLException;
}
```

Reihenfolge:

1. Context/Dialect/Identifier erstellen;
2. Catalog lesen;
3. ausgewählte physische Tabellen bestimmen;
4. Schema-Snapshot bauen;
5. Geometry-Snapshot bauen;
6. Enums lesen;
7. assemblieren;
8. Diagnostics sortieren;
9. Strict Policy anwenden;
10. Result zurückgeben.

---

## 6.14 Fehlerregeln

Verboten:

```java
catch (SQLException e) {
    logger.warn(...);
    return empty;
}
```

ohne Diagnostic.

Regeln:

- erforderliche Metatabelle nicht lesbar → FATAL;
- optionale Metatabelle nicht vorhanden → WARNING;
- einzelne Enum-Tabelle nicht lesbar → WARNING oder ERROR abhängig von Enum-Nutzung;
- physische Spalte für persistentes Attribut fehlt → ERROR;
- Geometry Metadata fehlt, JDBC-Spalte existiert → WARNING, allgemeiner Geometry-Fallback zulässig;
- Root-Modell fehlt → FATAL;
- unabhängige DB-Modelle → INFO, nicht einlesen.

---

## 6.15 Tests für P1-B

### Unit

- Catalog Reader pro Metatabelle;
- Capability Detection mit unterschiedlichen ili2db-Versionen;
- Required/Optional Failure Policy;
- JDBC Snapshot mit zwei Tabellen und vielen Spalten;
- Primary-Key-Erkennung;
- Foreign-Key-Erkennung;
- H2, SQLite und PostgreSQL-Dialekterkennung;
- PostGIS-Batch-Mapping;
- Enum-Cache pro Lauf;
- Assembler ohne JDBC;
- Relationship Deriver;
- Association Deriver.

### Query-Count-Test

Instrumentiere eine Connection/DataSource oder nutze einen Proxy, der `DatabaseMetaData`-/Statement-Aufrufe zählt.

Vertrag:

- Anzahl Schema-Introspektionsaufrufe skaliert mit Tabellen, nicht Attributen;
- Geometry Query Count konstant beziehungsweise batchweise;
- Enum-Tabelle höchstens einmal pro Lauf.

### Golden Contract

Für bestehende Testmodelle muss die neue Fassade semantisch dasselbe immutable Resultat liefern wie der P0-Reader.

Vergleich nicht über `toString()`, sondern über kanonische JSON-/Structural-Diff-Darstellung.

---

# 7. P1-C – Kanonische immutable Core-IR

## 7.1 Ziel

Nach Abschluss von Reading, Merge und Validation darf kein Generator oder Runtime-Generator die Core-IR mutieren können.

Es darf genau eine kanonische Relationship-Sammlung geben.

Abgeleitete Indizes dürfen mehrfach existieren, müssen aber unveränderlich sein und auf dieselben kanonischen Objekte zeigen.

---

## 7.2 Architekturentscheidung

Die bestehenden Modellklassen bleiben namentlich erhalten, werden jedoch zu finalen immutable Value Objects.

Neue Builder leben separat:

```text
core/src/main/java/ch/interlis/generator/model/builder/
    ModelMetadataBuilder.java
    ClassMetadataBuilder.java
    AttributeMetadataBuilder.java
    RelationshipMetadataBuilder.java
    AssociationMetadataBuilder.java
    AssociationRoleMetadataBuilder.java
    EnumMetadataBuilder.java
    EnumValueBuilder.java
```

Zusätzlich:

```text
core/src/main/java/ch/interlis/generator/model/
    ModelMetadataFactory.java
    ModelMetadataValidator.java
    ModelMetadataIndexes.java
    ModelMetadataValidationException.java
    ModelMetadataDiagnostic.java
    RelationshipIdentity.java
    AttributeTypeResolver.java
```

Keine dauerhafte parallele `ImmutableModelMetadata`-Hierarchie anlegen.

---

## 7.3 Freeze-Grenze

Pipeline:

```text
Reader → Builders
Merger → neue Builders
PostProcessor → Builders
Validator → Diagnostics
ModelMetadataFactory.buildValidated() → immutable ModelMetadata
Generatoren → read-only
```

Keine Setter nach `buildValidated()`.

---

## 7.4 `ModelMetadata`

Zielsignatur:

```java
public final class ModelMetadata {

    private final String modelName;
    private final String schemaName;
    private final Map<String, ClassMetadata> classes;
    private final Map<String, EnumMetadata> enums;
    private final Map<String, AssociationMetadata> associations;
    private final List<RelationshipMetadata> relationships;
    private final String iliVersion;
    private final String modelVersion;
    private final Instant importDate;
    private final String ili2dbVersion;
    private final Map<String, String> settings;
    private final ModelMetadataIndexes indexes;

    ModelMetadata(ModelMetadataBuilder builder, ModelMetadataIndexes indexes);

    public static ModelMetadataBuilder builder(String modelName);

    public ModelMetadataBuilder toBuilder();

    public String getModelName();

    public Optional<ClassMetadata> findClass(String name);

    public ClassMetadata getClass(String name);

    public Collection<ClassMetadata> getAllClasses();

    public Map<String, ClassMetadata> getClasses();

    public List<RelationshipMetadata> getAllRelationships();

    public List<RelationshipMetadata> relationshipsFrom(String sourceClass);

    public List<RelationshipMetadata> relationshipsTo(String targetClass);

    public Optional<RelationshipMetadata> relationship(RelationshipIdentity id);

    public Collection<AssociationMetadata> getAllAssociations();

    public Collection<EnumMetadata> getAllEnums();
}
```

### Regeln

- alle Collections defensive copied;
- `Map.copyOf()` nur verwenden, wenn Reihenfolge unerheblich ist; sonst `Collections.unmodifiableMap(new LinkedHashMap<>(...))`;
- `getAllRelationships()` gibt die kanonische Liste direkt als unmodifiable List zurück;
- kein erneutes Deduplizieren pro Aufruf;
- `Date` durch `Instant` ersetzen, aber JSON-Kompatibilität prüfen;
- keine Setter;
- kein `addClass`, `addRelationship`, `addAssociation`.

---

## 7.5 `ModelMetadataIndexes`

```java
public final class ModelMetadataIndexes {

    private final Map<String, List<RelationshipMetadata>> bySourceClass;
    private final Map<String, List<RelationshipMetadata>> byTargetClass;
    private final Map<RelationshipIdentity, RelationshipMetadata> byIdentity;
    private final Map<String, ClassMetadata> byPhysicalTable;

    public static ModelMetadataIndexes build(
        Collection<ClassMetadata> classes,
        Collection<RelationshipMetadata> relationships
    );

    public List<RelationshipMetadata> bySource(String className);

    public List<RelationshipMetadata> byTarget(String className);

    public Optional<RelationshipMetadata> byIdentity(RelationshipIdentity id);

    public Optional<ClassMetadata> byPhysicalTable(String tableName);
}
```

Indizes werden genau einmal beim Build erzeugt.

---

## 7.6 `RelationshipIdentity`

```java
public record RelationshipIdentity(
    String sourceClass,
    String targetClass,
    String sourceAttribute,
    String physicalName,
    String associationName,
    String targetRoleName,
    RelationshipMetadata.SemanticKind semanticKind
) {
    public RelationshipIdentity {
        Objects.requireNonNull(sourceClass, "sourceClass");
        Objects.requireNonNull(targetClass, "targetClass");
    }

    public static RelationshipIdentity of(
        RelationshipMetadata relationship
    );
}
```

Identität nicht nur anhand Relationship-Name.

---

## 7.7 `ClassMetadata`

```java
public final class ClassMetadata {

    private final String name;
    private final String simpleName;
    private final String topicName;
    private final String tableName;
    private final String sqlName;
    private final String documentation;
    private final boolean abstractClass;
    private final String baseClass;
    private final ClassKind kind;
    private final Map<String, AttributeMetadata> attributes;
    private final Map<String, String> labels;
    private final String inheritanceStrategy;

    public static ClassMetadataBuilder builder(String name);

    public ClassMetadataBuilder toBuilder();

    public Optional<AttributeMetadata> findAttribute(String name);

    public AttributeMetadata getAttribute(String name);

    public Collection<AttributeMetadata> getAllAttributes();

    public List<AttributeMetadata> getGeometryAttributes();

    public List<AttributeMetadata> getNonGeometryAttributes();
}
```

`ClassMetadata` speichert keine eigenständige Relationship-Liste mehr.

Alle Aufrufer migrieren zu:

```java
modelMetadata.relationshipsFrom(classMetadata.getName())
```

Ein temporärer deprecated Adapter darf während der Migration existieren, muss aber vor P1-Abschluss entfernt werden.

---

## 7.8 `AttributeMetadata`

Alle aktuellen Felder bleiben fachlich erhalten.

Ziel:

```java
public final class AttributeMetadata {

    private final String name;
    private final String qualifiedName;
    private final String columnName;
    private final String sqlName;
    private final String iliType;
    private final String domainName;
    private final CoreType coreType;
    private final String javaType;
    private final String dbType;
    private final boolean mandatory;
    private final boolean primaryKey;
    private final boolean foreignKey;
    private final boolean geometry;
    private final Integer geometrySrid;
    private final GeometryKind geometryKind;
    private final Boolean geometryHasZ;
    private final Boolean geometryHasM;
    private final Boolean allowEmptyGeometry;
    private final String documentation;
    private final AttributeConstraints constraints;
    private final String enumType;
    private final List<EnumMetadata.EnumValue> enumValues;
    private final String unit;
    private final String referencedClass;
    private final String referencedAttribute;
    private final Map<String, String> labels;

    public static AttributeMetadataBuilder builder(String name);

    public AttributeMetadataBuilder toBuilder();
}
```

### Keine Lazy Mutation

Verboten:

```java
public String getJavaType() {
    if (javaType == null) {
        inferJavaType();
    }
    return javaType;
}
```

Stattdessen:

```java
public final class AttributeTypeResolver {

    public ResolvedAttributeTypes resolve(
        AttributeMetadataBuilder attribute
    );
}
```

```java
public record ResolvedAttributeTypes(
    CoreType coreType,
    String javaType
) {
}
```

Type Resolution findet vor Freeze statt.

`getCoreType()` und `getJavaType()` sind reine Getter.

---

## 7.9 `RelationshipMetadata`

```java
public final class RelationshipMetadata {

    private final String name;
    private final String sourceClass;
    private final String targetClass;
    private final RelationType type;
    private final SemanticKind semanticKind;
    private final String sourceAttribute;
    private final String targetAttribute;
    private final String associationName;
    private final String sourceRoleName;
    private final String targetRoleName;
    private final String oppositeRoleName;
    private final Cardinality cardinality;
    private final boolean mandatory;
    private final boolean ordered;
    private final boolean external;
    private final boolean composition;
    private final String source;
    private final String physicalName;
    private final String semanticName;
    private final MergeReason mergeReason;
    private final MergeConfidence mergeConfidence;
    private final String mergeToken;

    public static RelationshipMetadataBuilder builder(String name);

    public RelationshipMetadataBuilder toBuilder();

    public RelationshipIdentity identity();
}
```

### Immutable Cardinality

```java
public record Cardinality(
    int minSource,
    int maxSource,
    int minTarget,
    int maxTarget
) {
    public Cardinality {
        validateBound(minSource, "minSource");
        validateBound(maxSource, "maxSource");
        validateBound(minTarget, "minTarget");
        validateBound(maxTarget, "maxTarget");
        validateRange(minSource, maxSource, "source");
        validateRange(minTarget, maxTarget, "target");
    }

    public boolean sourceUnbounded();
    public boolean targetUnbounded();
}
```

`-1` bleibt Sentinel für unbounded Max.

---

## 7.10 Association und Enum

`AssociationMetadata`, `AssociationRoleMetadata`, `EnumMetadata` und `EnumValue` werden ebenfalls immutable.

Wesentliche Regeln:

- Rollenliste immutable;
- Attributliste/-map immutable;
- Rollennamen innerhalb Association eindeutig;
- Enumwerte stabil nach Sequenz und Code;
- Labels immutable;
- keine `addRole`, `addAttribute`, `addEnumValue` nach Freeze.

---

## 7.11 Builder-Regeln

Builder sind mutable, aber:

- nicht thread-safe;
- nicht aus Generatoren verwenden;
- package beziehungsweise Modul klar dokumentieren;
- `build()` nicht öffentlich unvalidiert anbieten;
- primärer Abschluss über `ModelMetadataFactory.buildValidated()`.

### Beispiel `ClassMetadataBuilder`

```java
public final class ClassMetadataBuilder {

    public ClassMetadataBuilder name(String name);

    public ClassMetadataBuilder tableName(String tableName);

    public ClassMetadataBuilder kind(ClassKind kind);

    public ClassMetadataBuilder attribute(
        AttributeMetadataBuilder attribute
    );

    public ClassMetadataBuilder label(
        String language,
        String label
    );

    ClassMetadata buildUnchecked();
}
```

`buildUnchecked()` package-private.

### `ModelMetadataFactory`

```java
public final class ModelMetadataFactory {

    private final AttributeTypeResolver typeResolver;
    private final ModelMetadataValidator validator;

    public ModelBuildResult build(
        ModelMetadataBuilder builder,
        ModelBuildPolicy policy
    );

    public ModelMetadata buildValidated(
        ModelMetadataBuilder builder
    );
}
```

```java
public record ModelBuildResult(
    ModelMetadata metadata,
    List<ModelMetadataDiagnostic> diagnostics
) {
    public boolean hasBlockingDiagnostics();
}
```

---

## 7.12 Validator

P0-Validator an neue immutable Architektur anpassen; keine zweite konkurrierende Validierung einführen.

Zusätzliche Invarianten:

1. Mapschlüssel entsprechen Objektname.
2. kein leerer Modellname.
3. jede persistente Klasse hat eindeutigen physischen Tabellennamen.
4. Attribute pro Klasse haben eindeutigen Namen.
5. physische Spalten pro Klasse case-insensitiv eindeutig.
6. Relationship Identity eindeutig.
7. Source-/Target-Klassen existieren oder Relationship ist explizit external.
8. Association-Rollen verweisen auf kanonische Relationships beziehungsweise konsistente Zielklassen.
9. EnumType referenziert bekannten Enum, wenn enum-spezifische Klasse generiert werden soll.
10. `javaType` und `coreType` sind vor Freeze aufgelöst.
11. Geometry-Attribute haben konsistente Geometry-Felder.
12. Collections sind null-frei.
13. keine mutable Map/List wird in das Resultat übernommen.

---

## 7.13 P0-Merger migrieren

Der P0-`MetadataMerger` muss immutable Inputs erhalten und ein neues immutable Resultat liefern.

Ziel-API:

```java
public final class MetadataMerger {

    public MetadataMergeResult merge(
        ModelMetadata physical,
        ModelMetadata semantic
    );
}
```

Interne Arbeitsweise:

1. `physical.toBuilder()`;
2. eindeutige Matches anwenden;
3. keine Inputs mutieren;
4. Postprocessing auf Builder;
5. `ModelMetadataFactory.build(...)`;
6. Diagnostics kombinieren;
7. immutable Resultat.

Tests müssen beweisen:

- Inputs unverändert;
- Resultat unabhängig;
- Collections unmodifiable;
- keine Objektmutation durch Getter.

---

## 7.14 JSON-Kompatibilität

Der Agent muss bestehende Metadata-JSON-Tests und CLI-Ausgaben inventarisieren.

Neue Regel:

- Standardformat bleibt soweit möglich kompatibel;
- Beziehungen werden kanonisch nur einmal gespeichert;
- falls alte JSON-Ausgabe Relationships zusätzlich unter Klassen enthielt, wird Kompatibilität über einen Serializer/View hergestellt, nicht durch doppelte mutable Speicherung.

Empfohlene Klassen:

```text
core/src/main/java/ch/interlis/generator/model/json/
    ModelMetadataJsonWriter.java
    ModelMetadataJsonView.java
    LegacyModelMetadataJsonView.java
```

Optionaler Formatmarker:

```json
{
  "metadataFormatVersion": 2
}
```

Ein Formatbruch muss ausdrücklich dokumentiert und durch Golden-Tests abgesichert werden.

---

## 7.15 Immutable-Tests

Mindestens:

- alle zurückgegebenen Maps/Lists werfen bei Mutation `UnsupportedOperationException`;
- Builderänderung nach Build verändert Resultat nicht;
- `toBuilder()` erzeugt unabhängige Kopie;
- `getJavaType()` verändert Zustand nicht;
- `getCoreType()` verändert Zustand nicht;
- Relationship-Index zeigt auf dieselben kanonischen Instanzen;
- `getAllRelationships()` gibt bei wiederholtem Aufruf denselben unveränderten Inhalt ohne Deduplizierungsarbeit;
- parallele Generator-Lesezugriffe verändern nichts;
- Jackson Roundtrip, falls unterstützt;
- P0 Merger mutiert Inputs nicht.

---

# 8. P1-D – Typisierte Runtime-Deskriptoren

## 8.1 Ziel

Zwischen Generator, generierten Registries, Plugin-Services und Controller-Flows sollen keine unkontrollierten verschachtelten Maps mehr als fachlicher Vertrag verwendet werden.

Maps bleiben nur zulässig:

- an der finalen GSP-Model-Grenze;
- bei Framework-APIs, die zwingend Maps verlangen;
- bei kontrollierter Legacy-Konfigurationsaufnahme vor dem Parsing.

---

## 8.2 Runtime-API-Paketstruktur

```text
grails-runtime-api/src/main/java/ch/interlis/generator/grails/runtime/api/
    descriptor/
        DomainDescriptor.java
        DomainKind.java
        FieldDescriptor.java
        FieldKind.java
        GeometryDescriptor.java
        DisplayDescriptor.java
        RelationshipDescriptor.java
        InverseRelationshipDescriptor.java
        InverseRelationshipMode.java
        AssociationDescriptor.java
        AssociationStorageKind.java
        AssociationRoleDescriptor.java
        AssociationAttributeDescriptor.java
        AssociationContextDescriptor.java
        AssociationCreateMode.java
        EntityDescriptor.java
        FilterDescriptor.java
        FilterType.java
        SearchFieldDescriptor.java
        FormDescriptor.java
        FormSectionDescriptor.java
        DetailDescriptor.java
        UiDescriptor.java

    registry/
        InterlisRuntimeRegistry.java
        DomainRegistry.java
        AssociationRegistry.java
        RegistryValidationReport.java
        RegistryDiagnostic.java
        RegistryDiagnosticCode.java

    command/
        CommandStatus.java
        CommandCode.java
        FieldError.java
        AssociationCommandResult.java
        InverseRelationshipCommandResult.java

    query/
        PageRequest.java
        PageResult.java
        OptionItem.java
        OptionPage.java

    security/
        InterlisAuthorizationPolicy.java
        DomainOperationContext.java
        AssociationOperationContext.java
        InverseRelationshipOperationContext.java

    lifecycle/
        InterlisLifecycleHooks.java
```

---

## 8.3 Descriptor-Grundregeln

Alle Deskriptoren:

- immutable;
- constructor validation;
- defensive copies;
- stabile Equals/HashCode;
- keine Grails-Abhängigkeit;
- keine `Class<?>` in generierten statischen Source-Artefakten, wenn Classloading-Reihenfolge problematisch wäre;
- Domain-Klassen primär als qualifizierter Klassenname speichern;
- Runtime-Auflösung gecacht im Registry-Layer.

---

## 8.4 `DomainDescriptor`

```java
public record DomainDescriptor(
    String iliName,
    String modelName,
    String topicName,
    String domainClassName,
    String controllerName,
    String className,
    String label,
    DomainKind kind,
    boolean navigationVisible,
    DisplayDescriptor display,
    Map<String, FieldDescriptor> fields,
    Map<String, RelationshipDescriptor> relationships,
    Map<String, InverseRelationshipDescriptor> inverseRelationships,
    Map<String, GeometryDescriptor> geometries
) {
    public DomainDescriptor {
        requireText(iliName, "iliName");
        requireText(domainClassName, "domainClassName");
        fields = immutableLinkedMap(fields);
        relationships = immutableLinkedMap(relationships);
        inverseRelationships = immutableLinkedMap(inverseRelationships);
        geometries = immutableLinkedMap(geometries);
    }
}
```

`DomainKind`:

```java
public enum DomainKind {
    CLASS,
    STRUCTURE,
    ASSOCIATION
}
```

---

## 8.5 Field- und Geometry-Deskriptoren

### `FieldDescriptor`

```java
public record FieldDescriptor(
    String name,
    String iliName,
    String javaType,
    CoreType coreType,
    FieldKind kind,
    String label,
    boolean mandatory,
    Integer maxLength,
    String minValue,
    String maxValue,
    Integer precision,
    Integer scale,
    String unit,
    String enumType
) {
}
```

`grails-runtime-api` darf nicht zwingend von `core` abhängen, wenn dadurch Core-Modelltypen in die Runtime gezogen werden. Deshalb entweder:

- eigenen Runtime-`FieldCoreType` definieren; oder
- einen sehr kleinen, dependency-neutralen gemeinsamen Typmodul schaffen.

Bevorzugt: `grails-runtime-api` definiert `RuntimeCoreType` und der Generator mappt explizit.

Keine zyklische Modulabhängigkeit.

### `GeometryDescriptor`

```java
public record GeometryDescriptor(
    String fieldName,
    Integer srid,
    String kind,
    Boolean hasZ,
    Boolean hasM,
    Boolean allowEmpty
) {
}
```

---

## 8.6 Relationship-Deskriptoren

### `RelationshipDescriptor`

```java
public record RelationshipDescriptor(
    String name,
    String propertyName,
    String targetDomainClassName,
    String semanticKind,
    String label,
    String sourceAttribute,
    String targetRoleName,
    boolean mandatory
) {
}
```

### `InverseRelationshipDescriptor`

```java
public record InverseRelationshipDescriptor(
    String name,
    String label,
    String ownerIliClassName,
    String relatedIliClassName,
    String relatedDomainClassName,
    String relatedControllerName,
    String relatedPropertyName,
    String relatedLabel,
    boolean generatedWritable,
    boolean visible,
    InverseRelationshipMode mode
) {
    public boolean writable() {
        return generatedWritable
            && (mode == InverseRelationshipMode.AUTO
                || mode == InverseRelationshipMode.EDITABLE);
    }
}
```

```java
public enum InverseRelationshipMode {
    AUTO,
    EDITABLE,
    READ_ONLY,
    OFF
}
```

Runtime-Overrides erzeugen eine neue Descriptor-Instanz. Keine Map-Mutation.

---

## 8.7 Association-Deskriptoren

### `AssociationDescriptor`

```java
public record AssociationDescriptor(
    String associationName,
    String iliClassName,
    String domainClassName,
    String controllerName,
    String viewPath,
    String physicalTable,
    String physicalSqlName,
    AssociationStorageKind storageKind,
    boolean writable,
    boolean showInNavigation,
    List<AssociationRoleDescriptor> roles,
    List<AssociationAttributeDescriptor> attributes,
    List<String> diagnostics
) {
    public Optional<AssociationRoleDescriptor> role(String roleName);
}
```

### `AssociationRoleDescriptor`

```java
public record AssociationRoleDescriptor(
    String name,
    String label,
    String propertyName,
    String targetIliClassName,
    String targetDomainClassName,
    int minCardinality,
    int maxCardinality,
    boolean mandatory,
    boolean ordered,
    boolean external,
    boolean composition
) {
}
```

### `AssociationContextDescriptor`

```java
public record AssociationContextDescriptor(
    String id,
    String associationName,
    String participantDomainClassName,
    String fixedRoleName,
    String fixedPropertyName,
    List<String> editableRoleNames,
    List<String> editablePropertyNames,
    String defaultLabel,
    String messageCode,
    String presentation,
    AssociationCreateMode createMode,
    boolean writable,
    boolean removable,
    boolean showAssociationObjectLink,
    int perspectiveMin,
    int perspectiveMax,
    List<String> diagnostics
) {
}
```

---

## 8.8 Registries

### `DomainRegistry`

```java
public interface DomainRegistry {

    Collection<DomainDescriptor> domains();

    Optional<DomainDescriptor> byIliName(String iliName);

    Optional<DomainDescriptor> byDomainClassName(
        String qualifiedClassName
    );

    List<DomainDescriptor> byModel(String modelName);
}
```

### `AssociationRegistry`

```java
public interface AssociationRegistry {

    Collection<AssociationDescriptor> associations();

    Optional<AssociationDescriptor> association(String name);

    Optional<AssociationContextDescriptor> context(String id);

    List<AssociationContextDescriptor> contextsForParticipant(
        String domainClassName
    );
}
```

### `InterlisRuntimeRegistry`

```java
public final class InterlisRuntimeRegistry {

    private final DomainRegistry domainRegistry;
    private final AssociationRegistry associationRegistry;
    private final RuntimeClassResolver classResolver;

    public DomainDescriptor requireDomain(Class<?> domainType);

    public AssociationDescriptor requireAssociation(String name);

    public AssociationContextDescriptor requireContext(
        Class<?> participantType,
        String contextId
    );

    public Class<?> resolveDomainClass(String qualifiedName);

    public RegistryValidationReport validate(
        RuntimeMappingContext mappingContext
    );
}
```

Die Runtime-Services injizieren `InterlisRuntimeRegistry`; sie importieren nicht direkt statische generierte Maps.

---

## 8.9 Generierte Registry-Klassen

### `InterlisUiRegistry`

Nach P1 generiert:

```groovy
package ch.interlis.generator.grails.generated

import ch.interlis.generator.grails.runtime.api.descriptor.DomainDescriptor
import ch.interlis.generator.grails.runtime.api.registry.DomainRegistry

final class InterlisUiRegistry implements DomainRegistry {

    static final List<DomainDescriptor> DOMAINS = [
        new DomainDescriptor(...)
    ].asImmutable()

    static final InterlisUiRegistry INSTANCE =
        new InterlisUiRegistry(DOMAINS)

    private final Map<String, DomainDescriptor> byIliName
    private final Map<String, DomainDescriptor> byClassName

    private InterlisUiRegistry(List<DomainDescriptor> domains) {
        ...
    }

    @Override
    Collection<DomainDescriptor> domains() { DOMAINS }

    @Override
    Optional<DomainDescriptor> byIliName(String name) { ... }
}
```

Keine `List<Map<String,Object>>`.

### `InterlisAssociationRegistry`

Implementiert `AssociationRegistry` und enthält typed Descriptor-Instanzen.

### Generatoränderungen

`GrailsUiRegistryGenerator` und `GrailsAssociationRegistryGenerator` dürfen keine generischen `renderMap()`-/`renderValue()`-Renderer mehr als Hauptweg verwenden.

Neue Hilfsklasse:

```text
target-grails/src/main/java/ch/interlis/generator/grails/source/
    GroovySourceWriter.java
```

```java
public final class GroovySourceWriter {

    public String stringLiteral(String value);

    public String enumLiteral(Class<? extends Enum<?>> type, Enum<?> value);

    public String listOfStrings(Collection<String> values);

    public String nullableInteger(Integer value);
}
```

Descriptor-spezifische Renderer bleiben explizit und compile-time nachvollziehbar.

---

## 8.10 Domain-Metadaten

`GrailsDomainGenerator` erzeugt typed Metadaten:

```groovy
static final Map<String, FieldDescriptor> interlisFieldMeta
static final DisplayDescriptor interlisDisplayMeta
static final Map<String, RelationshipDescriptor> interlisRelationshipMeta
static final Map<String, InverseRelationshipDescriptor> interlisInverseRelationshipMeta
static final Map<String, GeometryDescriptor> geometryMeta
```

Oder, bevorzugt, alle Metadaten leben ausschliesslich in `InterlisUiRegistry`, damit Domainklassen keine zweite Registry tragen.

Verbindliche Entscheidung:

- Es darf am Ende keine voneinander unabhängige doppelte Wahrheit zwischen Registry und Domain-Statics geben.

Empfohlener Zielzustand:

1. `InterlisUiRegistry` enthält den vollständigen `DomainDescriptor`.
2. Domainklassen enthalten nur GORM-Mapping, Constraints und fachliche Properties.
3. Runtime fragt Registry, nicht statische Domain-Maps.
4. Für Übergang dürfen Domain-Statics generiert werden, müssen aber exakt dieselben Descriptor-Instanzen aus der Registry referenzieren.

Beispiel Übergang:

```groovy
static final DomainDescriptor interlisDescriptor =
    InterlisUiRegistry.INSTANCE
        .byDomainClassName(CurrentClass.name)
        .orElseThrow()
```

Keine erneute Map-Kopie.

---

## 8.11 Typed UI Descriptor

`InterlisUiDescriptorSupport.descriptor(...)` gibt neu `UiDescriptor` zurück:

```groovy
@CompileStatic
final class InterlisUiDescriptorService {

    UiDescriptor descriptor(Class domainType)

    DisplayDescriptor displayDescriptor(Class domainType)

    RuntimeUiOverrides overridesFor(DomainDescriptor domain)
}
```

Die aktuelle statische Helper-Klasse soll zu injizierbarem Service werden, weil sie benötigt:

- `GrailsApplication`;
- Registry;
- Runtime Properties;
- Overrides;
- Mapping Context.

### `UiDescriptor`

```java
public record UiDescriptor(
    DomainDescriptor domain,
    String appTitle,
    String appLogo,
    String appLogoIcon,
    ListDescriptor list,
    FormDescriptor form,
    DetailDescriptor detail
) {
}
```

Die Controller-Grenze darf daraus eine GSP-Map erzeugen:

```groovy
Map<String, Object> toViewModel(UiDescriptor descriptor)
```

Diese Konvertierung ist zentral und getestet.

---

## 8.12 Typed Command Results

### Gemeinsamer Status

```java
public enum CommandStatus {
    SUCCESS,
    CLIENT_ERROR,
    CONFLICT,
    FORBIDDEN,
    NOT_FOUND,
    VALIDATION_ERROR,
    SERVER_ERROR
}
```

### Codes

```java
public enum CommandCode {
    CREATED,
    DELETED,
    ASSIGNED,
    REASSIGNED,
    ALREADY_ASSIGNED,
    READ_ONLY,
    FORBIDDEN,
    OWNER_NOT_FOUND,
    TARGET_NOT_FOUND,
    ASSOCIATION_NOT_FOUND,
    CONTEXT_INVALID,
    OWNERSHIP_MISMATCH,
    TARGET_ROLE_INVALID,
    DUPLICATE_LINK,
    CARDINALITY_MAX_EXCEEDED,
    CARDINALITY_MIN_VIOLATED,
    REASSIGNMENT_CONFIRMATION_REQUIRED,
    VALIDATION_FAILED,
    DATA_INTEGRITY,
    CONCURRENT_MODIFICATION,
    CONFIGURATION_INVALID,
    INTERNAL_ERROR
}
```

### `FieldError`

```java
public record FieldError(
    String field,
    String code,
    String message
) {
}
```

### `AssociationCommandResult`

```java
public record AssociationCommandResult(
    boolean success,
    int httpStatus,
    CommandStatus status,
    CommandCode code,
    String messageCode,
    String message,
    String associationId,
    List<FieldError> fieldErrors
) {
    public static AssociationCommandResult created(...);
    public static AssociationCommandResult failure(...);
}
```

### `InverseRelationshipCommandResult`

Enthält zusätzlich Reassignment-Daten als typisierte optionale Felder beziehungsweise `ReassignmentConfirmation`.

```java
public record ReassignmentConfirmation(
    String relatedId,
    String relatedLabel,
    String previousOwnerId,
    String previousOwnerLabel,
    String newOwnerId,
    String newOwnerLabel,
    String targetTypeLabel
) {
}
```

Controller serialisiert Records direkt oder über zentralen Presenter.

Keine Result-Map mehr aus Services.

---

## 8.13 Service-Signaturen

### Association Command

```groovy
AssociationCommandResult createQuickLink(
    Class participantType,
    Serializable participantId,
    String contextId,
    String targetRoleName,
    Serializable targetId
)

AssociationCommandResult deleteLink(
    Class participantType,
    Serializable participantId,
    String contextId,
    Serializable associationId
)
```

### Inverse Command

```groovy
InverseRelationshipCommandResult assign(
    Class ownerType,
    Serializable ownerId,
    String relationshipName,
    Serializable relatedId,
    boolean confirmReassignment
)
```

### Query Services

```groovy
PageResult<AssociationRow> page(...)
OptionPage optionPage(...)
PageResult<RelatedRecordRow> inversePage(...)
```

View-/JSON-Modelle werden erst im Controller-Presenter zu Maps.

---

## 8.14 Authorization und Locking

Services injizieren:

```groovy
InterlisAuthorizationPolicy authorizationPolicy
InterlisLifecycleHooks lifecycleHooks
InterlisRuntimeRegistry runtimeRegistry
```

Breites Lock-Fallback wird korrigiert.

Neue Komponente:

```groovy
interface RuntimeRecordLoader {
    Object get(Class type, Serializable id)
    LockResult lock(Class type, Serializable id)
}
```

```java
public record LockResult(
    Object record,
    LockStatus status,
    Throwable failure
) {
}
```

```java
public enum LockStatus {
    LOCKED,
    NOT_FOUND,
    LOCK_UNSUPPORTED,
    LOCK_FAILED
}
```

Nur erwartetes `LOCK_UNSUPPORTED` darf auf `get()` zurückfallen. Unerwartete Lock-Fehler werden nicht verschluckt.

---

## 8.15 Runtime-Descriptor-Validierung beim Startup

Neue Klasse:

```text
grails-runtime/src/main/groovy/ch/interlis/generator/grails/runtime/registry/
    InterlisRuntimeRegistryValidator.groovy
```

```groovy
RegistryValidationReport validate(
    InterlisRuntimeRegistry registry,
    GrailsApplication grailsApplication
)
```

Prüft:

- Domainklasse existiert;
- Property existiert;
- Relationship target class existiert;
- Association-Domainklasse existiert;
- Rollenproperty existiert;
- Context fixed property existiert;
- editable role/property Listen sind konsistent;
- inverse related property existiert;
- Geometry field existiert;
- Filter-/Search-Feld existiert und hat passenden Typ;
- Controllername ist bekannt, soweit erforderlich;
- keine doppelten IDs/Namen.

Strict-Modus:

- Startup schlägt mit einer zusammengefassten Exception fehl.

Diagnostic-Modus:

- fehlerhafte schreibbare Funktion wird read-only/off geschaltet;
- vollständiger Report wird geloggt;
- nicht als Standard für Tests/Release verwenden.

---

## 8.16 Tests P1-D

### Generator

- generierte Registries kompilieren;
- keine `Map<String,Object>` in Registry-API;
- stabile Source-Ausgabe;
- Sonderzeichen korrekt escaped;
- Null/Unbounded korrekt;
- typed DomainDescriptor vollständig.

### Runtime API

- constructor validation;
- immutable Collections;
- Registry Lookup;
- duplicate detection;
- Command Result serialization;
- Override creates new descriptor.

### Plugin

- Services konsumieren typed descriptors;
- Policy Injection;
- invalid registry fails startup;
- query results korrekt serialisiert;
- HTTP-Status bleibt kompatibel;
- GSP-Model enthält erwartete Keys nach Presenter-Konvertierung.

### Static Guard

Füge einen Test oder eine ArchUnit-/Source-Assertion hinzu:

In folgenden Paketen sind fachliche `Map<String,Object>`-Signaturen verboten:

```text
ch.interlis.generator.grails.runtime.api.descriptor
ch.interlis.generator.grails.runtime.api.registry
ch.interlis.generator.grails.runtime.api.command
```

In Services sind Maps nur an klar markierten View-/Framework-Grenzen zulässig.

---

# 9. Übergreifende Generatoränderungen

## 9.1 `GrailsCrudGenerator`

Der Orchestrator muss gemeinsame Objekte genau einmal erzeugen:

```java
TargetNameRegistry nameRegistry;
GrailsRelationshipMapper relationshipMapper;
GrailsAssociationPlanner associationPlanner;
GrailsInverseRelationshipPlanner inversePlanner;
RuntimeDescriptorPlanner runtimeDescriptorPlanner;
```

Neue Reihenfolge:

1. immutable IR entgegennehmen;
2. Namen registrieren;
3. Persistenzpläne;
4. Runtime-Deskriptorpläne;
5. Domains/Enums;
6. typed Registries;
7. Controller/Services projektspezifisch generieren;
8. Runtime-Plugin-Dependency installieren;
9. Scaffolding-Templates installieren;
10. Projekt validieren.

## 9.2 `RuntimeDescriptorPlanner`

Neue Klasse:

```java
public final class RuntimeDescriptorPlanner {

    public RuntimeDescriptorPlan plan(
        ModelMetadata metadata,
        GenerationConfig config,
        TargetNameRegistry names,
        GrailsRelationshipMapper relationships,
        GrailsAssociationPlanner associations,
        GrailsInverseRelationshipPlanner inverses
    );
}
```

```java
public record RuntimeDescriptorPlan(
    List<DomainDescriptorPlan> domains,
    List<AssociationDescriptorPlan> associations,
    List<AssociationContextDescriptorPlan> contexts,
    List<RuntimeDescriptorDiagnostic> diagnostics
) {
}
```

Generatoren sollen nicht dieselben fachlichen Entscheidungen nochmals aus Core-Metadaten ableiten.

---

# 10. Migrations- und Kompatibilitätsstrategie

## 10.1 Kein Big Bang ohne Zwischenzustände

Verbindliche Phasen:

1. `grails-runtime-api` mit Typen einführen.
2. Generator kann parallel typed Registry erzeugen.
3. Runtime-Support kann typed Registry lesen, während Legacy-Map-Adapter existiert.
4. Pluginmodul einführen und Consumer-Test grün machen.
5. Runtime-Dateikopien entfernen.
6. Services auf typed Results migrieren.
7. Legacy-Map-Adapter entfernen.
8. Core-IR immutable schalten.
9. ili2db-Reader auf Builder/immutable Result umstellen.

Die konkrete Commit-Reihenfolge unten ist verbindlicher als die grobe technische Abhängigkeit, darf aber in zwei eng begründeten Commits angepasst werden, falls Kompilation sonst nicht möglich ist.

## 10.2 Temporäre Adapter

Zulässig während Migration:

```java
LegacyDescriptorMapAdapter
LegacyCommandResultMapAdapter
MutableMetadataCompatibilityAdapter
```

Regeln:

- mit `@Deprecated(forRemoval = true)`;
- nur in klar markiertem `compat`-Paket;
- jeder Adapter hat Removal-Test/Issue im Progress-Dokument;
- vor P1-Abschluss entfernen, ausser explizit für externe API-Kompatibilität dokumentiert.

---

# 11. Implementierungsreihenfolge und Commits

## Phase 0 – Baseline

Commit:

```text
docs: record P1 architecture baseline
```

## Phase 1 – Runtime API

Commit:

```text
feat(runtime-api): add typed descriptors and operation results
```

Tests nur dependency-neutral.

## Phase 2 – Typed Registry Generation

Commit:

```text
refactor(grails): generate typed runtime registries
```

Bestehende Runtime darf vorübergehend Adapter verwenden.

## Phase 3 – Grails Plugin Skeleton

Commit:

```text
feat(runtime): add ili2grails Grails runtime plugin
```

Plugin-JAR, Services/Views/Assets zunächst kopiert beziehungsweise verschoben, Consumer Smoke Test.

## Phase 4 – Project Customizer und Legacy Migration

Commit:

```text
refactor(grails): replace runtime overlay with plugin dependency
```

Danach dürfen keine Runtime-Klassen mehr in neue Apps kopiert werden.

## Phase 5 – Typed Runtime Services

Commit:

```text
refactor(runtime): replace map contracts with typed descriptors
```

## Phase 6 – Policy-/Hook-Injection und Controller-Flows

Commit:

```text
refactor(runtime): add injectable policies and split controller flows
```

## Phase 7 – Immutable IR Foundations

Commit:

```text
refactor(core): introduce immutable metadata builders and indexes
```

## Phase 8 – Merger/Reader/Generator auf immutable IR

Commit:

```text
refactor(core): make metadata pipeline immutable after validation
```

## Phase 9 – ili2db Reader Split

Commit:

```text
refactor(core): split ili2db catalog schema and assembly layers
```

Kann bei Bedarf vor Phase 8 vorbereitet werden, final aber nur mit immutable Output.

## Phase 10 – Full Contracts und Doku

Commits:

```text
test: verify plugin and immutable IR consumer contracts
docs: complete P1 runtime and IR migration
```

---

# 12. Vollständige Testmatrix

## 12.1 Standard

```bash
./gradlew clean test --rerun-tasks --no-daemon
```

## 12.2 Plugin

```bash
./gradlew :grails-runtime-api:test --rerun-tasks --no-daemon
./gradlew :grails-runtime:test --rerun-tasks --no-daemon
./gradlew :grails-runtime:integrationTest --rerun-tasks --no-daemon
./gradlew :grails-runtime:jar --rerun-tasks --no-daemon
./gradlew :grails-runtime:publishToMavenLocal --rerun-tasks --no-daemon
```

## 12.3 Grails Consumer

```bash
PATH="$HOME/.sdkman/candidates/grails/current/bin:$PATH" \
  ./gradlew :target-grails:grailsRuntimeSmokeTest \
  --rerun-tasks --no-daemon
```

## 12.4 Real ili2db/PostgreSQL

```bash
PATH="$HOME/.sdkman/candidates/grails/current/bin:$PATH" \
  ./gradlew :target-grails:realIli2dbSmokeTest \
  --rerun-tasks --no-daemon

PATH="$HOME/.sdkman/candidates/grails/current/bin:$PATH" \
  ./gradlew :target-grails:grailsPostgresContractTest \
  -PcontractTestRequired=true \
  --rerun-tasks --no-daemon
```

## 12.5 Browser

```bash
PATH="$HOME/.sdkman/candidates/grails/current/bin:$PATH" \
  ./gradlew :target-grails:browserE2eTest \
  --rerun-tasks --no-daemon
```

## 12.6 Publication Consumer Test

Neuer Task:

```text
:target-grails:runtimePluginConsumerTest
```

Er muss:

1. Plugin in temporäres Maven-Repo publizieren;
2. App generieren;
3. App-Dependency auf temporäres Repo zeigen lassen;
4. App kompilieren;
5. Integrationtests ausführen;
6. Plugin-Views/Assets prüfen;
7. lokale Runtime-Dateien ausschliessen.

---

# 13. Architektur- und Source-Guards

Neue Tests sollen dauerhaft folgende Regeln sichern.

## 13.1 Keine Runtime-Kopien

`GrailsTemplateOverlayInstaller` beziehungsweise Nachfolger darf keine Pfade unter lokalem Runtime-Package kopieren.

## 13.2 Keine mutable IR

Reflection-Test:

- keine öffentliche Methode `set*` auf finalen Metadata-Klassen;
- keine öffentliche `add*`-Mutation;
- Collection-Getter unmodifiable;
- Klassen final.

## 13.3 Keine doppelte Relationship-Wahrheit

- `ClassMetadata` enthält kein Relationship-Feld;
- Root-IR ist kanonisch;
- Indizes referenzieren Root-Objekte.

## 13.4 Keine Fach-Maps in Runtime API

- Descriptor-/Command-Pakete enthalten keine `Map<String,Object>`-Felder;
- Registry-Methoden liefern typed Objekte.

## 13.5 Reader-Grenzen

- Catalog-Paket importiert keine `ch.interlis.generator.model.*`-Builder/IR;
- Schema-Paket importiert keine IR;
- Assembly-Paket darf Catalog-/Schema-Snapshots und Builder importieren;
- Facade enthält keine SQL-Strings ausser trivialer Delegation.

ArchUnit ist zulässig, falls die neue Dependency gerechtfertigt ist. Alternativ reflektions-/sourcebasierte Tests ohne fragile Stringsuche.

---

# 14. Performance-Anforderungen

## 14.1 Reader

Für ein Modell mit vielen Attributen:

- JDBC-Spaltenmetadaten höchstens tabellenweise;
- kein linearer DB-Roundtrip pro Attribut;
- Enumwerte einmal pro Enumtabelle;
- Geometry-Metadaten batchweise;
- Relationship-Indizes einmal beim Freeze.

## 14.2 Runtime

- Deskriptoren einmal beim Startup validieren;
- Domainclass-Auflösung cachen;
- keine vollständige Registry-Suche pro Feld/Request;
- keine wiederholte Konvertierung derselben statischen Descriptor-Maps;
- Runtime-Overrides pro Domain cachen, sofern Konfiguration nach Startup unverändert ist.

Keine künstlichen Mikrobenchmark-Ziele. Query-/Lookup-Counts und grosse Fixture reichen als Regressionstest.

---

# 15. Sicherheits- und Robustheitsregeln

- Keine Authorization-Entscheidung anhand vom Client gesendeter Klassen-/Propertynamen.
- Alle Namen kommen aus validated typed descriptors.
- Policy wird serverseitig injiziert.
- Modified Legacy Runtime Files niemals automatisch löschen.
- Startup Validation fail-closed im Release-/Testmodus.
- Keine Secrets in Diagnostics/Reports.
- Command Results enthalten keine Exception-Stacktraces für Clients.
- Breite `catch (Exception)` nur an Controller-Grenzen mit Logging und sicherer Fehlermeldung.
- Lock-Fehler nicht still als „unsupported“ behandeln.
- GSP-Ausgaben weiterhin korrekt encoden.

---

# 16. Nicht-Ziele

P1 implementiert ausdrücklich nicht:

- neues UI-Design;
- SPA- oder JavaScript-Framework-Wechsel;
- neue INTERLIS-Compilerfunktionalität;
- neue Association-Speicherarten;
- Schreibsupport für bisher read-only Embedded Foreign Keys;
- vollständige generische Repository-Abstraktion über GORM;
- Multi-Tenant-Support;
- externe Authentisierung;
- Publikation in ein öffentliches Plugin-Portal;
- Removal aller Groovy-Dynamik;
- Rewrite der Generatoren in Kotlin;
- gleichzeitige Neugestaltung des Django-Targets.

Das Django-Target muss jedoch mit der immutable Core-IR weiter funktionieren.

---

# 17. Definition of Done

## P1-A Runtime Plugin

- [ ] `grails-runtime-api` existiert und ist dependency-neutral.
- [ ] `grails-runtime` ist ein echtes Grails-Plugin.
- [ ] Plugin-JAR kann gebaut und lokal publiziert werden.
- [ ] Generierte App verwendet Plugin-Dependency.
- [ ] Neue App enthält keine lokalen Runtime-Klassen/Services/Views/Assets.
- [ ] Scaffolding-Templates bleiben funktionsfähig.
- [ ] App kann Plugin-Views und I18n überschreiben.
- [ ] Assets werden ohne 404 geladen.
- [ ] Legacy Runtime Migration ist sicher und blockiert bei modifizierten Dateien.
- [ ] Default Policies erhalten aktuelles Verhalten.
- [ ] Eigene Policy-/Hook-Beans werden verwendet.

## P1-B ili2db Reader

- [ ] Fassade delegiert.
- [ ] Catalog Reader erzeugt nur typed Rows.
- [ ] Schema Introspector erzeugt immutable Snapshot.
- [ ] Geometry batchweise.
- [ ] Enumwerte einmal pro Tabelle/Lauf.
- [ ] Assembler ist allein für IR-Builder verantwortlich.
- [ ] Required/Optional Failures strukturiert diagnostiziert.
- [ ] Keine per-Attribut Schema-Roundtrips.
- [ ] H2, SQLite und PostgreSQL getestet.

## P1-C immutable IR

- [ ] Metadata-Klassen final.
- [ ] Keine öffentlichen Setter/Add-Mutatoren.
- [ ] Collections unmodifiable.
- [ ] Keine Lazy-Mutation in Gettern.
- [ ] eine kanonische Relationship-Liste.
- [ ] Indizes einmal aufgebaut.
- [ ] Reader und Merger liefern immutable Resultate.
- [ ] Generatoren kompilieren gegen immutable API.
- [ ] Django-Target weiterhin grün.
- [ ] JSON-Kompatibilität getestet/dokumentiert.

## P1-D typed Runtime

- [ ] Registries enthalten typed descriptors.
- [ ] Services verwenden typed Descriptor APIs.
- [ ] Command Services liefern typed Results.
- [ ] Controller wandelt erst an View-/JSON-Grenze um.
- [ ] Startup Registry Validation vorhanden.
- [ ] keine fachlichen `Map<String,Object>`-Verträge in Runtime API.
- [ ] Domain-Statics sind entfernt oder referenzieren kanonische Registry-Deskriptoren.
- [ ] zwei FKs, Associations, Inverse Relationships und Geometry weiterhin abgedeckt.

## Gesamt

- [ ] alle Standardtests grün;
- [ ] Plugin Unit/Integration grün;
- [ ] Consumer Contract grün;
- [ ] realer PostgreSQL-/ili2pg-Vertrag grün;
- [ ] Browser-E2E grün;
- [ ] keine unbeabsichtigten Snapshots;
- [ ] keine untracked generierten Dateien;
- [ ] `git diff --check` grün;
- [ ] Fortschrittsdokument vollständig;
- [ ] README/Architekturdoku aktualisiert.

---

# 18. Erwarteter Abschlussbericht des Coding-Agenten

Der Agent liefert:

```markdown
## Ergebnis

## Verifizierte P0-Basis

## Neue Module

## Runtime-Plugin-Migration

### Verschobene Artefakte
### Verbleibende Generator-Templates
### Legacy-Migration
### Plugin Coordinates

## Typed Runtime API

### Descriptoren
### Registries
### Command Results
### Policies und Hooks

## Immutable Core-IR

### Builder
### Freeze-Grenze
### Indizes
### JSON-Kompatibilität

## ili2db-Reader-Zerlegung

### Catalog
### JDBC Schema
### PostGIS
### Enum
### Assembly
### Diagnostics

## Geänderte Klassen und Methoden

## Entfernte Legacy-Klassen/Dateien

## Snapshot-Änderungen

## Performance-Verifikation

## Testresultate

## Nicht ausgeführte Tests und exakte Ursache

## Verbleibende Risiken

## Commit-Liste

## Definition of Done
```

Jede Testangabe enthält:

- exakten Befehl;
- Ergebnis;
- Anzahl Tests;
- Skips;
- relevante Laufzeit;
- Reportpfad bei Fehlern.

---

# 19. Abschliessende Leitlinie

P1 soll die bereits funktionierende Anwendung nicht neu erfinden. Der Umbau dient vier klaren Qualitätszielen:

1. **Framework-Code wird versioniert und geteilt statt kopiert.**
2. **Datenbanklesen wird in überprüfbare fachliche Schichten zerlegt.**
3. **Metadaten werden nach Validation unveränderlich und kanonisch.**
4. **Runtime-Verträge werden compile-time sichtbar statt als freie Maps interpretiert.**

Bei Unsicherheit gilt:

- eine kanonische Quelle statt doppelter Wahrheit;
- typed Descriptor statt Map;
- Builder vor Freeze, immutable danach;
- Plugin-Override statt kopierter Datei;
- strukturierte Diagnostic statt Warnlog;
- Consumer-/Realtest statt Annahme über Grails- oder GORM-Verhalten.
