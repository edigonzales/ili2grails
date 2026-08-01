# P1 – Fortschrittsdokument: Runtime-Plugin, immutable Core-IR, Reader-Zerlegung, typed Runtime

**Branch:** `refactor/p1-runtime-ir-architecture`
**Ausgangscommit:** `3b17dddb11f3426d6a0e7e3475f36958af1f12aa` (docs: complete P0 backend correctness acceptance)
**Spec:** `ili2grails-p1-coding-agent-spec.md` (Stand 2026-08-01)
**Begonnen:** 2026-08-01

Dieses Dokument ist Teil des P1-Ergebnisses und wird während der gesamten Arbeit fortlaufend aktualisiert.

---

## 1. Baseline

### Umgebung

| Komponente | Version / Pfad |
|---|---|
| Java (Default Shell) | OpenJDK 25.0.2 (Temurin) |
| Java (verwendet für Build) | OpenJDK 17.0.18-tem (`$HOME/.sdkman/candidates/java/17.0.18-tem`) |
| Gradle | 8.14.3 |
| Groovy (Build) | 3.0.24 (Gradle), Groovy 4.0.24 (Projekt-Test-Abhängigkeit) |
| Grails CLI | 7.0.6 (`$HOME/.sdkman/candidates/grails/current`) |
| ili2c | 5.6.8 |
| ili2pg | 5.5.1 (`/Users/stefan/apps/ili2pg-5.5.1`) |
| PostgreSQL/PostGIS | docker compose `edit-db` (sogis/postgis:16-3.5, Port 54321, Container `ili2grails-edit-db-1` läuft) |
| Docker | 29.6.2, Compose v5.3.1 |
| JUnit | 5.10.1 |

**Wichtiger Umgebungsbefund:** Unter JDK 25 schlagen die Groovy-`parseClass`-Tests in `target-grails` mit `GroovyBugError` fehl (Groovy 4.0.24 ist nicht JDK-25-kompatibel). Alle Gradle-Aufrufe in P1 laufen daher mit `JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.18-tem`. Dies ist eine vorbestehende Umgebungseinschränkung, kein P1-Defekt.

### P0-Verifikation (Code-Basis)

| P0-Invariante | Status | Nachweis |
|---|---|---|
| Deterministischer `MetadataMerger` | vorhanden | `core/.../metadata/merge/MetadataMerger.java` (Javadoc: kein first-match-wins, strukturierte Diagnostics) |
| Strukturierte Merge-Diagnostics | vorhanden | `MergeDiagnostic`, `MergeSeverity`, `MergeDiagnosticCode`, `RelationshipMergeReporter` |
| Sichere SQL-Identifier | vorhanden | `core/.../reader/sql/` (SqlIdentifier, SqlIdentifierRenderer, QualifiedSqlName) |
| Präzise ModelSelection | vorhanden | `core/.../metadata/selection/` (ModelSelection, ModelSelectionResolver) |
| Inverse MANY_TO_ONE ohne synthetisches GORM-hasMany | vorhanden | Commit `2d30a7f refactor(grails): separate GORM collections from inverse UI plans` |
| Realer Grails-/PostgreSQL-/ili2pg-Vertragstest | vorhanden und grün | `GrailsPostgresContractTest` (1 Test, grün, s. Baseline-Tabelle) |

### Baseline-Testresultate

Alle Befehle mit `JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.18-tem`.

| Befehl | Resultat | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| `./gradlew clean test --rerun-tasks --no-daemon` | BUILD SUCCESSFUL | 325 (core 166, target-grails 138, target-django 7, cli 14) | 0 | 0 | 0 |
| `grailsRuntimeSmokeTest` (ohne rerun) | BUILD SUCCESSFUL | 6 | 0 | 0 | 0 |
| `realIli2dbSmokeTest` (ohne rerun) | BUILD SUCCESSFUL | 9 | 0 | 0 | 0 |
| `grailsPostgresContractTest -PcontractTestRequired=true` (ohne rerun) | BUILD SUCCESSFUL | 1 | 0 | 0 | 0 |

Laufzeit: `clean test --rerun-tasks` ≈ 13 s (Kompilierung war teils gecached; erster Lauf ohne Cache ≈ 8 s Abbruch unter JDK 25, danach sauber unter JDK 17). Smoke-Tests ≈ 1:43 min, Contract-Test ≈ 44 s.

Skips: `browserE2eTest` nicht als Baseline ausgeführt (optional, benötigt Playwright-Browser und bootRun-Zyklus; wird in Phase 10 geprüft).

Vorbestehende Warnings: Gradle-Deprecation-Hinweise (Gradle 9 Inkompatibilität) bei `grailsRuntimeSmokeTest`/`realIli2dbSmokeTest` (unverändert, nicht P1-bezogen).

### Working-Tree-Status zu Beginn

Untracked im Root: `ili2grails-p0-coding-agent-spec.md`, `ili2grails-p1-coding-agent-spec.md` (die beiden Spezifikationsdokumente selbst – wurden nicht verändert, nicht gestasht).

---

## 2. Architektur-Inventar (Ist-Zustand vor P1)

- **Module:** `core`, `target-grails`, `target-django`, `cli` (siehe `settings.gradle`).
- **Overlay:** `target-grails/src/main/resources/grails/overlays/bootstrap-openlayers/` mit 72 verwalteten Dateien (Runtime-Support-Groovy, 4 Services, Controller, TagLib, 14 Views, i18n, 23 Assets/Fonts, 16 Scaffolding-Templates, `resources.groovy`). `GrailsTemplateOverlayInstaller` kopiert sie blind überschreibend ohne Hash-Prüfung.
- **Core-IR:** `ModelMetadata`, `ClassMetadata`, `AttributeMetadata`, `RelationshipMetadata`, `AssociationMetadata`, `AssociationRoleMetadata`, `EnumMetadata` – alle mutable, Getter geben Collections direkt zurück, `ModelMetadata.getAllRelationships()` dedupliziert bei jedem Aufruf (O(n²)), `AttributeMetadata.getJavaType()` kann lazy inferieren (muss geprüft werden).
- **Reader:** `Ili2dbMetadataReader` (1299 Zeilen) als Monolith; `reader/sql/` bereits herausgelöst (Wiederverwendung in P1-B vorgesehen).
- **Untypisierte Runtime-Verträge:** `Map<String,Object>`-Verträge in den 8 Runtime-Klassen und in den Generatoren `GrailsUiRegistryGenerator`, `GrailsAssociationRegistryGenerator`, `GrailsDomainGenerator`.

---

## 3. Architekturentscheidungen (laufend ergänzt)

### D-1 Reihenfolge nach Spezifikations-Abschnitt 11 (verbindlich)

Die Commit-Reihenfolge folgt Phasen 0–10 aus Abschnitt 11 der P1-Spec (Runtime-API → typed Registry-Generierung → Plugin → Customizer/Legacy → typed Services → Policies/Controller → immutable IR → Merger/Reader/Generator → Reader-Split → Contracts/Doku). Dies entspricht der verbindlichen Migrationsstrategie (Abschnitt 10.1).

### D-2 Modulnamen

Die Spezifikation schreibt die Module `grails-runtime-api` und `grails-runtime` vor (Abschnitt 5.2) sowie Pakete `ch.interlis.generator.grails.runtime.api.*` bzw. `ch.interlis.generator.grails.runtime.*`.

### D-3 Java 17 + Groovy

`grails-runtime-api` als `java-library` (Java 17, dependency-neutral). `grails-runtime` als Grails-7-Web-Plugin mit Groovy-Quellen.

### D-4 (offen, wird während der Umsetzung ergänzt)

---

## 4. Modulstruktur nach P1 (Ziel)

```text
core                      (unverändert als Modulname)
grails-runtime-api        (neu: typed Descriptor-/Registry-/Command-API, dependency-neutral)
grails-runtime            (neu: Grails-7-Plugin mit Services/Controller/TagLib/Views/Assets/i18n)
target-grails             (Generatoren; erzeugt Plugin-Dependency statt Overlay-Runtime)
target-django             (unverändert)
cli                       (unverändert)
```

### Overlay-Inventar nach Phase 4 (Klassifikation)

| Kategorie | Artefakte | Status |
|---|---|---|
| Runtime-Plugin | 14 Runtime-Support-Groovy, 4 Services, InterlisUiController, InterlisUiTagLib, 14 interlisUi-Views, layouts (main/ili2grails), i18n-Bundles, 4 JS, ili-modern.css, 7 Fonts, 2 OFL | nach `grails-runtime` verschoben (Phase 3) |
| Generator-managed | 22 Scaffolding-Templates, `grails-app/conf/spring/resources.groovy` (Locale), Asset-Requires, Plugin-Dependency-Block | verbleiben im Overlay bzw. im Customizer |
| Legacy-Runtime (Migration) | 49 Dateien unter `target-grails/src/main/resources/grails/migration/legacy-runtime-v1/**` (Stand c450bdd) | Hash-basierte Erkennung + sichere Löschung |
| Obsolet | LEGACY_FILES (ili-carbon-*, FiraSans-Bold) | wie bisher bereinigt |

---

## 5. API-Migrationsentscheidungen

### M-1 Legacy-Map-Adapter in der generierten Registry

Die generierten Registries `InterlisUiRegistry`/`InterlisAssociationRegistry` implementieren die typed Interfaces (`DomainRegistry`/`AssociationRegistry`) und halten immutable Deskriptoren. Für den Migrationszeitraum (bis die kopierte Runtime durch das Plugin ersetzt ist) stellen sie zusätzliche, als `@Deprecated(forRemoval = true)` markierte statische Legacy-Map-Methoden bereit, die über `LegacyDescriptorMapAdapter` (Paket `grails-runtime-api.api.compat`) in den alten Map-Vertrag konvertieren. Neue Runtime-Komponenten dürfen diese Methoden nicht verwenden.

Benennung der Legacy-Accessoren (vermeidet statisch/instanz-Konflikte mit den Interface-Methoden):
- `InterlisUiRegistry`: `legacyDomains()`, `domain(iliName)`, `domainForClassName(name)`, `domainsForModel(model)` (die ersten drei behalten ihre alten Namen; nur `domains()` musste wegen des Interface-Konflikts umbenannt werden).
- `InterlisAssociationRegistry`: `legacyAssociation(name)`, `legacyContext(id)`, `legacyContextsForParticipant(name)`, `legacyEntities()`, `legacyEntity(name)`, `legacyShowInNavigation(name)`.

### M-2 Zwischen-Bridge für die Smoke-/Contract-Tests

Die generierten Apps der Smoke-/Contract-Tests kompilieren die typed Registry und benötigen daher `grails-runtime-api` auf dem Klassenpfad. Bis Phase 4 (Plugin-Dependency-Installer) injiziert `RuntimeApiTestSupport` (nur Test-Harness) das gebaute API-JAR als `implementation files('libs/grails-runtime-api.jar')` in die temporäre App. Ersetzt durch Plugin-Coordinates in Phase 4.

### M-3 GrailsBuildGradleUpdater.ensureManagedDependency

Generischer, idempotenter Insert einer markierten Dependency-Zeile in den Top-Level-`dependencies`-Block der Ziel-App (keine Regex-Volltext-Ersetzung). Grundlage für den `GrailsRuntimeDependencyInstaller` (Phase 4).

### M-4 Layout-Vertrag des Plugins

Das Plugin liefert `grails-app/views/layouts/ili2grails.gsp` (das bisherige Overlay-`main.gsp`, unverändert) als kanonisches Default-Layout sowie `grails-app/views/layouts/main.gsp` als dünne Delegation (`<meta name="layout" content="ili2grails"/>`). Damit bleibt die gerenderte Ausgabe byte-identisch (bisher wurde das App-`main.gsp` durch den Overlay-Installer überschrieben), und die App kann per eigenem `main.gsp` das Layout überschreiben. Es gibt in Grails 7 keinen `g:applyLayout`-Tag; die Meta-Delegation ist der einzig robuste Mechanismus.

### M-5 Runtime-Klassen im Plugin

Die verschobenen Runtime-Klassen dürfen die generierten Registries der Host-App nicht zur Compilezeit importieren. `GeneratedRegistryAccessor` löst die Registry-Typen einmalig per Reflection; der fachliche Vertrag sind die API-Interfaces. Zusätzlich erhielten die Services/Controller/Plugin-Descriptor `@Slf4j`, weil `log` im Plugin-Kontext nicht per Artefakt-Konvention injiziert wird.

### M-6 Maven-Consumer-Pfad

`grails-runtime` (artifactId `ili2grails-runtime`) und `grails-runtime-api` publizieren nach `mavenLocal()`. Der Smoke-/Contract-Harness injiziert die Plugin-Coordinates plus `mavenLocal()`-Repository und `cacheChangingModulesFor 0` (nur Test-Harness). Das App-Gradle bricht die SNAPSHOT-Auflösung sonst 24h.

---

## 6. Ausgeführte Befehle

(wird laufend ergänzt; siehe Abschnitt 1 für die Baseline)

---

## 7. Testresultate

(wird laufend ergänzt)

---

## 8. Skips

- Browser-E2E: nicht als Baseline ausgeführt (optional). Wird in Phase 10 geprüft, sofern Umgebung (Playwright-Browser, bootRun) verfügbar ist.

---

## 9. Snapshot-Änderungen

| Snapshot | Änderung | Grund |
|---|---|---|
| `grails-snapshots/*/.../InterlisUiRegistry.groovy` (3 Fixtures) | komplett neuer typed Aufbau (`DomainDescriptor`-Liste, implements `DomainRegistry`, Legacy-Map-API) | P1-B Phase 2: typed Registry-Generierung |
| `grails-snapshots/association-cases/.../InterlisAssociationRegistry.groovy` | komplett neuer typed Aufbau (`AssociationDescriptor`-Maps, implements `AssociationRegistry`, Legacy-Map-API) | P1-B Phase 2 |

Domain-Snapshots und Enum-Snapshots sind unverändert (GrailsDomainGenerator unverändert in Phase 2).

---

## 10. Bekannte Risiken

1. **JDK-25/Groovy-4.0.24-Inkompatibilität:** Alle Tests müssen mit JDK 17 laufen (s. Baseline).
2. **Overlay-Testkopplung:** Smoke-/Contract-Tests prüfen derzeit Dateipfade im Overlay (z. B. `src/main/groovy/.../InterlisUiDescriptorSupport.groovy`); diese Tests müssen auf Plugin-Verträge umgestellt werden.
3. **Snapshot-Fixtures** (`target-grails/src/test/resources/grails-snapshots/*`): ändern sich bei typed Registries; jede Änderung wird einzeln dokumentiert.

---

## 11. Verbleibende Arbeit

(wird laufend ergänzt)

---

## 12. Commit-Liste

| Commit | Inhalt |
|---|---|
| `1a50e32` | docs: record P1 architecture baseline |
| `04278da` | feat(runtime-api): add typed descriptors and operation results (Phase 1) |
| `c450bdd` | refactor(grails): generate typed runtime registries (Phase 2) |
| `46ae8fa` | feat(runtime): add ili2grails Grails runtime plugin (Phase 3) |
| (folgt) | refactor(grails): replace runtime overlay with plugin dependency (Phase 4) |
| (folgt) | ... |
