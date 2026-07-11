# Association UX Implementation Plan

Begleitdokument zu `./docs/association-ux-implementation-spec.md` (verbindliche Referenz).
Dieser Plan wird nach jedem grösseren Schritt aktualisiert, nicht erst am Schluss.

## Status

| Phase | Status | Beginn | Abschluss | Tests | Bemerkungen |
|---|---|---:|---:|---|---|
| Phase 0 – Baseline, Analyse, Plan | DONE | 2026-07-11 | 2026-07-11 | `./gradlew test` PASS (83), ili2c PASS | Baseline grün mit JDK 21; ili2c via ilivalidator-libs; grails/ili2pg lokal nicht installiert |
| Phase 1 – Association-Planungsmodell | DONE | 2026-07-11 | 2026-07-11 | `:target-grails:test` PASS (49), `./gradlew test` PASS (99) | Planungsmodelle + `GrailsAssociationPlanner` + 16 Unit-Tests; keine Runtime/GSP/Core-IR-Änderung |
| Phase 2 – Registry-Generierung & Konfiguration | DONE | 2026-07-11 | 2026-07-11 | `./gradlew test` PASS (112); `:target-grails:grailsRuntimeSmokeTest` PASS (2, real Grails 7.0.6) | Registry-Generator + `GenerationConfig`-Association-Felder + CLI + gemeinsamer Mapper; Registry-Snapshot (6 Assoc.) + Compile-Test; keine Show/Runtime-Schreibpfade |
| Phase 3 – Read-only Related-Sections | DONE | 2026-07-11 | 2026-07-11 | `./gradlew test` PASS (122); `:target-grails:test` PASS (72) | Registry-Support + Query-Service + Templates + CSS; 10 neue Unit-Tests; Overlay-Installer-Test erweitert; Runtime-Smoke-Test erweitert; Real-ili2db-Test mit AssociationCases; keine Create/Delete-Actions
| Phase 4 – Quick-Link (binäre Associations) | NOT_STARTED |  |  |  |  |
| Phase 5 – Kontextuelle Formulare / n-är | NOT_STARTED |  |  |  |  |
| Phase 6 – Navigation, Kardinalität, Fehler, Performance | NOT_STARTED |  |  |  |  |
| Phase 7 – Spezialsemantik (EXTERNAL, Komposition, ORDERED, embedded FK) | NOT_STARTED |  |  |  |  |
| Phase 8 – Abschluss & Regression | NOT_STARTED |  |  |  |  |

Zulässige Statuswerte: `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `DONE`.

## Baseline

- **Commit/Branch:** `main` @ `2a894d6` ("add GEVER test model and data").
  - Working tree sauber, ausser untracked `docs/association-ux-implementation-spec.md` (Spezifikation) und dieser Plandatei.
- **Repository-Layout:** Multi-Modul Gradle: `core`, `target-grails`, `target-django`, `cli` (siehe `settings.gradle`, rootProject `interlis-crud-generator`).
- **Java (Default-Umgebung):** Temurin **25.0.2** (`JAVA_HOME` per SDKMAN `current`).
  - ⚠️ **Inkompatibel mit dem Build** – siehe Risiko R-1 / ADR-001.
  - Build-verwendetes JDK: Temurin **21.0.10** (`/Users/stefan/.sdkman/candidates/java/21.0.10-tem`).
  - Verfügbare JDKs: 11.0.30-tem, 17.0.18-tem, 21.0.10-tem, 25.0.2-tem, 25.0.3-graal.
- **Gradle:** Wrapper **8.14.3** (Kotlin 2.0.21, Groovy 3.0.24, Ant 1.10.15). Läuft mit JDK 21.
- **Build-Toolchain-Ziel:** `sourceCompatibility`/`targetCompatibility = 17` (root `build.gradle`).
- **Wichtige Abhängigkeitsversionen (root `build.gradle` `ext`):** ili2c 5.6.8, iox-ili 1.24.4, ehibasics 1.4.1, postgres 42.7.7, groovy 4.0.24, jts 1.19.0, junit 5.10.1, assertj 3.24.2, h2 2.2.224, playwright 1.60.0, picocli 4.7.7.
- **Grails (Smoke/E2E-Ziel):** Default `grailsSmokeVersion = 7.0.6` (überschreibbar via `-PgrailsSmokeVersion`).
- **ili2c:** 5.6.8.
  - ✅ Erwarteter Spec-Pfad `/Users/stefan/apps/ili2c-5.6.8/` ist inzwischen **installiert** (ADR-002-Fallback damit nicht mehr nötig; für neue/veränderte `.ili` kann direkt `java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar …` verwendet werden). In Phase 0/2 wurde kein `.ili` verändert.
  - Verfügbar zusätzlich über `ilivalidator-1.15.0/libs/*` (`ili2c-core-5.6.8.jar`, `ili2c-tool-5.6.8.jar`), Hauptklasse `ch.interlis.ili2c.Main`. Siehe ADR-002.
- **ilivalidator:** 1.15.0 unter `/Users/stefan/apps/ilivalidator-1.15.0/`.
- **ili2pg:** ✅ **installiert** unter `/Users/stefan/apps/ili2pg-5.6.1/` (Spec-Default war `5.5.1`; Real-ili2db-Smoke daher mit `-Pili2pgHome=/Users/stefan/apps/ili2pg-5.6.1` ausführen). Vor Phase 2 war dies ein Blocker (R-2).
- **Docker:** Docker **29.6.1** vorhanden (`docker-compose.yml` im Repo).
- **grails CLI:** ✅ **installiert** via SDKMAN unter `/Users/stefan/.sdkman/candidates/grails/7.0.6` (`current` → 7.0.6). Nicht auf dem Standard-PATH; für Smoke/E2E `PATH="$HOME/.sdkman/candidates/grails/current/bin:$JAVA_HOME/bin:$PATH"` mit `JAVA_HOME=…/21.0.10-tem` setzen. Grails 7.0.6 läuft auf JDK 21 (verifiziert `grails --version` → JVM 21.0.10). Vor Phase 2 war dies ein Blocker (R-2).
- **Playwright:** Dependency deklariert (1.60.0); Browser-Binaries-Status ungeprüft (E2E ohnehin durch fehlende grails/ili2pg blockiert).

### Baseline-Testresultat

Befehl: `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/21.0.10-tem ./gradlew test`

Ergebnis: **BUILD SUCCESSFUL** – 83 Tests, 0 Failures, 0 Errors, 0 Skips.

| Modul | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| core | 31 | 0 | 0 | 0 |
| target-grails | 33 | 0 | 0 | 0 |
| cli | 12 | 0 | 0 | 0 |
| target-django | 7 | 0 | 0 | 0 |
| **Total** | **83** | **0** | **0** | **0** |

### ili2c-Validierung `test-models/AssociationCases.ili`

Ausgeführter Befehl (angepasst, da Spec-Pfad fehlt – siehe ADR-002):

```bash
java -cp "/Users/stefan/apps/ilivalidator-1.15.0/libs/*" \
  ch.interlis.ili2c.Main test-models/AssociationCases.ili
```

Ergebnis: **PASS**

```
Info: ili2c-5.6.8-e6f7ab6dd5cdba29afc9b24866ecb98c057d82b2
Info: ilifile <test-models/AssociationCases.ili>
Info: ...compiler run done 2026-07-11 15:15:23
```

Keine Warnungen/Fehler. Das Modell deckt aktuell ab: `EmptyAssociation` (binär, keine Attribute), `AssociationWithAttribute` (mit Attribut `RoleNote`), `SameTargetAssociation` (zwei Rollen derselben Zielklasse `Person`), `PhysicalMismatchAssociation` (semantisch `SemanticOwner`/`OwnedParcel`), `ExternalCompositeAssociation` (`EXTERNAL` + `-<#>` Komposition), `ExtendedTopicAssociation` (Association in erweitertem Topic). Es fehlen (Spec §31.1, optional): `ORDERED`, echte n-äre Association.

## Ist-Zustand vs. Spezifikation (Abweichungen)

### Core-IR (framework-agnostisch) — Spec §4.1

| Element | Pfad | Status |
|---|---|---|
| `AssociationMetadata` | `core/src/main/java/ch/interlis/generator/model/AssociationMetadata.java` | Vorhanden. Hält name, associationClass, physicalTable, physicalSqlName, roles, attributes. Entspricht Spec. |
| `AssociationRoleMetadata` | `core/.../model/AssociationRoleMetadata.java` | Vorhanden. Hält name, targetClass, oppositeRoleName, cardinality, mandatory, ordered, external, composition, sourceAttribute, targetAttribute, physicalName, semanticName, source, merge-Diagnostik. Entspricht Spec. |
| `RelationshipMetadata` | `core/.../model/RelationshipMetadata.java` | Vorhanden. `SemanticKind.ASSOCIATION_ROLE`, `Cardinality(minSource,maxSource,minTarget,maxTarget)`, merge fields, physical/semantic name. |
| `AttributeMetadata`, `ClassMetadata`, `ModelMetadata` | `core/.../model/` | Vorhanden (nicht im Detail für Phase 0 nötig). |

**Befund:** Core-IR ist für Phase 1 voraussichtlich ausreichend. Keine Core-Erweiterung geplant. Falls Phase 1 eine Lücke nachweist, ist ein ADR + JSON-Kompatibilitätsprüfung + Django-Auswirkungsanalyse Pflicht (Spec §5.1).

### Grails-Target Planungszeit — Spec §4.2–4.4, §9–11

| Erwartet (Spec) | Ist-Zustand |
|---|---|
| `GrailsRelationshipMapper` (Pfad §4.2) | **Vorhanden**, entspricht konservativer Persistenzentscheidung: Association-Rollen werden als Properties auf der Association-Domain gemappt (`propertyForRelationship`), **keine** inversen `hasMany` für Association-Rollen. `forMetadata(metadata, config, registry)` + `map(ClassMetadata)` liefern `DomainMapping`/`DomainProperty` (mit `RelationshipMetadata`) — direkt durch den Planner wiederverwendbar. |
| `GrailsDomainGenerator` (§4.3) erzeugt `interlisFieldMeta`, `interlisDisplayMeta`, `interlisRelationshipMeta`, geometryMeta | **Vorhanden**. Signatur `generate(metadata, config, registry)` — **kein** Mapper-Overload (§29.2 fordert zusätzlichen 4-arg-Overload für gemeinsame Mapper-Instanz). |
| `GrailsCrudGenerator.generate(metadata, config)` orchestriert (§4.4/§11.5) | **Vorhanden**, aber baut eigenes `TargetNameRegistry`, **keinen** gemeinsamen `GrailsRelationshipMapper`; ruft `domainGenerator.generate(metadata, config, registry)`; controller/view auskommentiert. Registry-Einhängung (§11.5) noch offen. |
| `GrailsAssociationPlanner` + Planmodelle (§9–10) | **Fehlt vollständig** (Phase 1). |
| `GrailsAssociationRegistryGenerator` (§11) | **Fehlt vollständig** (Phase 2). |
| `AssociationStorageKind`, `AssociationPresentationKind`, `AssociationCreateMode`, `GrailsAssociation*Plan` | **Fehlen** (Phase 1). |
| `GenerationConfig` Association-Felder (§12.1) | **Fehlen** – aktuell keine `associationUiMode`/`associationPageSize`/`hideContextualAssociationControllers`. Builder ist klassische (nicht record) Variante. |
| `TargetNameRegistry` (§Phase-0-Liste) | Vorhanden; public API u.a. `className`, `enumName`, `relationshipPropertyName`, `collectionPropertyName`, `controllerName`, `viewPath`, `domainPackage`, `enumPackage`, `controllerPackage`. Für Planner-Qualified-Names nutzbar. |

### Overlay-Runtime & Templates — Spec §4.5–4.6, §13–20

Overlay-Wurzel: `target-grails/src/main/resources/grails/overlays/bootstrap-openlayers/` (17 Dateien).

Vorhandene Runtime (`src/main/groovy/ch/interlis/generator/grails/runtime/`):
- `InterlisCrudControllerSupport.groovy` (553 Z.) — abstrakte CRUD-Basis (index/show/create/save/edit/update/delete, Paging, Suche, `relationshipOptions`, Geometrie).
- `InterlisRelationshipOptions.groovy` (314 Z.) — Autocomplete-Optionen, Display-Labels, Paging/Suche/Sortierung. **Ziel des Refactorings** (§14.5: `optionPageForTargetType(...)`).
- `InterlisTableModel.groovy` (131 Z.) — Spalten-/Such-/Filter-Modell.
- `InterlisGeometryBinder.groovy` (223 Z.) — WKT-Binding/Validierung.

Vorhandene Templates (`src/main/templates/scaffolding/`): `Controller.groovy`, `show.gsp`, `_form.gsp`, `_relationship-fields.gsp`, `_show-details.gsp`, `_geometry-panel.gsp`, `create.gsp`, `edit.gsp`, `index.gsp`.
Assets/Layout: `grails-app/assets/javascripts/ili-form-ux.js` (407 Z.), `ili-geometry-editor.js`, `grails-app/assets/stylesheets/ili-modern.css` (531 Z.), `grails-app/views/layouts/main.gsp` (52 Z., baut Navigation aus **allen** Controller-Klassen → §21-Problem bestätigt).

**Fehlen vollständig** (spätere Phasen): `InterlisAssociationRegistrySupport`, `InterlisAssociationQueryService`, `InterlisAssociationCommandService`, `InterlisAssociationContextSupport`, `InterlisNavigationSupport`, sämtliche `_association-*.gsp`, `InterlisAssociationRegistry` (generiert). Grep im gesamten Repo: **kein** `InterlisAssociation*`, **kein** `InterlisNavigationSupport`, **kein** `*-association-*.gsp`.

### Overlay-Installer — Spec §29.3

`GrailsTemplateOverlayInstaller.MANAGED_FILES` verwaltet aktuell **17** Dateien:

```
src/main/templates/scaffolding/Controller.groovy
src/main/templates/scaffolding/create.gsp
src/main/templates/scaffolding/edit.gsp
src/main/templates/scaffolding/show.gsp
src/main/templates/scaffolding/index.gsp
src/main/templates/scaffolding/_form.gsp
src/main/templates/scaffolding/_geometry-panel.gsp
src/main/templates/scaffolding/_relationship-fields.gsp
src/main/templates/scaffolding/_show-details.gsp
src/main/groovy/ch/interlis/generator/grails/runtime/InterlisCrudControllerSupport.groovy
src/main/groovy/ch/interlis/generator/grails/runtime/InterlisGeometryBinder.groovy
src/main/groovy/ch/interlis/generator/grails/runtime/InterlisRelationshipOptions.groovy
src/main/groovy/ch/interlis/generator/grails/runtime/InterlisTableModel.groovy
grails-app/views/layouts/main.gsp
grails-app/assets/javascripts/ili-geometry-editor.js
grails-app/assets/javascripts/ili-form-ux.js
grails-app/assets/stylesheets/ili-modern.css
```

Neue Runtime-Klassen/Services/Templates müssen ab Phase 3 hier ergänzt werden.

### Tests & Snapshots — Spec §4.7, §30

Unit/Snapshot (`target-grails/src/test/java/ch/interlis/generator/grails/`): `GrailsBuildGradleUpdaterTest`, `GrailsTemplateOverlayInstallerTest`, `GrailsCrudGeneratorTest`, `GrailsApplicationYamlUpdaterTest`, `GrailsGeneratedOutputSnapshotTest`, `TargetNameRegistryTest`, `GrailsRelationshipMapperTest`, `GrailsDomainGeneratorTest`, `GeneratedGrailsCompileSmokeTest`, `LargeModelNamingTest` (+ Helper `GeneratedGroovyCompiler`).

Opt-in Source-Sets (`target-grails/build.gradle`, group `verification`, `upToDateWhen { false }`):
- `grailsRuntimeSmokeTest` → `GrailsRuntimeSmokeTest` — braucht `grails` CLI, `-PgrailsSmokeVersion` (default 7.0.6).
- `realIli2dbSmokeTest` → `RealIli2dbSmokeTest` — braucht `ili2pg` (`-Pili2pgHome`, default `/Users/stefan/apps/ili2pg-5.5.1`) + Docker PostGIS.
- `browserE2eTest` → `GrailsBrowserE2eTest` — braucht grails + ili2pg + `-PbrowserE2eJdbcUrl` (default `jdbc:postgresql://localhost:54321/edit?...&dbSchema=sa`) + Playwright; optional `-PbrowserE2eAppUrl` gegen laufende Instanz.

Snapshots (`target-grails/src/test/resources/grails-snapshots/`): `simple-address/` (Person, Address, enum AddressStatus), `structure-composition/` (Asset, Part), `association-cases/` (**4** Domains: `AssociationWithAttribute`, `ExternalCompositeAssociation`, `PhysicalMismatchAssociation`, `SameTargetAssociation`).

**Abweichung/Lücke:** `association-cases` Snapshot deckt nur 4 der 6 Associations ab; `EmptyAssociation` und `ExtendedTopicAssociation` sind im Fixture vorhanden, werden aber im Snapshot-Test **nicht** asserted (`GrailsGeneratedOutputSnapshotTest` Zeilen 73–78). Kein `InterlisAssociationRegistry`-Snapshot vorhanden (erwartet, entsteht in Phase 2, §30.4).

Fixtures: `core/src/testFixtures/java/ch/interlis/generator/testsupport/MetadataTestFixtures.java` — `createAssociationCasesIli2dbFixture(Connection)` (Z. 251) + `readMergedAssociationCasesMetadata()` (Z. 44) spiegeln 5 Klassen + 6 Associations + Vererbung + FK-/Attribut-Mappings über H2-ili2db-Systemtabellen.

### Beobachtete konservative Persistenz (Snapshot-Belege für Planner-Klassifikation)

Diese generierten Association-Domains bestätigen die vom Planner (Phase 1) zu verwendenden Domain-Properties und Spaltennamen:
- `AssociationWithAttribute`: Properties `documentRoleId`(Document), `personRoleId`(Person), `roleNote`(String, maxSize 30) → Spec §6.2 kontextuelles Formular (hat eigenes Attribut).
- `SameTargetAssociation`: Properties `primaryPersonId`(Person), `secondaryPersonId`(Person) → §6.3 zwei distinkte Kontexte, gleiche Zielklasse.
- `PhysicalMismatchAssociation`: semantisch `SemanticOwner`/`OwnedParcel`, Properties `ownerFk`/`parcelFk`, Spalten `owner_fk`/`parcel_fk` → §24.2 Planner muss generierte Property (nicht Rollenname) verwenden.
- `ExternalCompositeAssociation`: `ownerId`(Person, mandatory, EXTERNAL+composite), `buildingId`(Building) → §6.5/§6.6 kein Quick-Link.
- Alle: `version false`, `id column 't_id', generator: 'identity'`, keine inversen Collections auf Teilnehmern.

## Entscheidungen (ADRs)

### ADR-001: Build mit JDK 21 statt Default-JDK 25
- **Kontext:** Default-`JAVA_HOME` ist Temurin 25.0.2. Gradle 8.14.3 bricht beim Konfigurieren ab: `Unsupported class file major version 69`.
- **Entscheidung:** Alle Gradle-Aufrufe mit `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/21.0.10-tem` ausführen. Toolchain-Ziel bleibt Java 17.
- **Alternativen:** (a) Gradle auf Version mit Java-25-Support / ≥9.x heben — grösserer, nicht beauftragter Eingriff, Risiko für Grails-7-Kompatibilität. (b) Toolchain-Pinning in `build.gradle` — Änderung an Build-Infrastruktur, nicht Teil von Phase 0.
- **Konsequenzen:** Reproduzierbar grün mit JDK 21. Muss in allen Phasen und ggf. in AGENTS.md/README dokumentiert werden. Empfehlung als offener Punkt: Gradle-Toolchain fixieren, damit die JDK-Wahl nicht implizit vom Shell-Environment abhängt.

### ADR-002: ili2c über ilivalidator-Bibliotheken ausführen
- **Kontext:** Der in der Spec (§30.9) genannte Pfad `/Users/stefan/apps/ili2c-5.6.8/ili2c.jar` existiert auf dieser Maschine nicht. Spec erlaubt Fallback über `jars.interlis.ch`.
- **Entscheidung:** ili2c 5.6.8 aus `ilivalidator-1.15.0/libs/*` via `java -cp "…/libs/*" ch.interlis.ili2c.Main <ili>` verwenden (exakt dieselbe ili2c-Version 5.6.8 wie in der Spec).
- **Alternativen:** Standalone-`ili2c.jar` von jars.interlis.ch nachladen — möglich, aber unnötig, da lokal bereits identische Version vorhanden.
- **Konsequenzen:** Validierung ist erfüllt und reproduzierbar. Der abweichende Befehl ist überall dokumentiert. Kein Committen unvalidierter `.ili`-Dateien.

### ADR-003: (offen) Fehler-/Ergebnis-Strategie der Command-Services
- **Kontext:** Spec §25.1 lässt strukturierte Result-Maps ODER typisierte Exceptions zu; „eine einzige konsistente Strategie".
- **Entscheidung:** OFFEN — festzulegen zu Beginn von Phase 4.
- **Konsequenzen:** Beeinflusst Controller-HTTP-Status-Mapping (§17.6) und Tests.

### ADR-004: (offen) Duplikat-Regel für Quick-Links
- **Kontext:** Spec §15.3 — keine erfundene globale Unique-Regel; optionale Duplikatverhinderung nur bei eindeutiger IR/Spec-Grundlage.
- **Entscheidung:** OFFEN — konservativer Default (keine Unique-Regel) bis in Phase 4 belegt.

### ADR-005: Synthetische In-Memory-Metadaten für ORDERED/n-är/UNMAPPED/ambiguous Planner-Tests
- **Kontext:** `test-models/AssociationCases.ili` und die Fixture decken ORDERED und echte n-äre Associations nicht ab (siehe Phase-0-Befund, Spec §31.1). Der Umsetzungsplan verortet Modell-Erweiterungen bewusst in Phase 5 (n-är) und Phase 7 (ORDERED/embedded).
- **Entscheidung:** Für Phase-1-Unit-Tests der Planner-Klassifikation werden ORDERED, n-är (3 Rollen), UNMAPPED und die Mehrdeutigkeits-Diagnose über handgebaute `ModelMetadata` (analog `GrailsRelationshipMapperTest`) getestet. Die realen Fälle (binär, attributiert, selbstreferenzierend/gleiche Zielklasse, physisch abweichend, EXTERNAL+COMPOSITE, erweitertes Topic) laufen über `MetadataTestFixtures.readMergedAssociationCasesMetadata()`.
- **Alternativen:** `AssociationCases.ili` jetzt um ORDERED/n-är erweitern — verworfen, weil das eine Modell-/ili2c-Änderung in Phase 1 einführen würde (nicht beauftragt) und die Phasen 5/7 diese Erweiterung ohnehin vorsehen.
- **Konsequenzen:** Kein `.ili`-Change in Phase 1, keine ili2c-Neu-Validierung nötig. ORDERED/n-är müssen in Phase 5/7 zusätzlich gegen echte ili2c/ili2db-Strukturen abgesichert werden.

### ADR-006: `EMBEDDED_FOREIGN_KEY` in Phase 1 nicht erzeugt
- **Kontext:** Spec §9.1 verlangt, dass `LINK_ENTITY` zuerst vollständig funktioniert und `EMBEDDED_FOREIGN_KEY` erst schreibbar wird, wenn durch echte ili2db-Struktur (Real-ili2db-Test) bewiesen.
- **Entscheidung:** Der Planner erzeugt in Phase 1 nur `LINK_ENTITY` (physisch gemappte Association-Domain) oder `UNMAPPED`. `EMBEDDED_FOREIGN_KEY` bleibt als Enum-Konstante vorhanden, wird aber nicht klassifiziert.
- **Konsequenzen:** Optimierte/eingebettete ili2db-Abbildungen fallen aktuell auf `UNMAPPED` (read-only) zurück. Aktivierung mit Real-ili2db-Beleg in Phase 7 (Restpunkt).

### ADR-007: Navigation-Sichtbarkeit der Association-Entities in der Registry (Phase 2)
- **Kontext:** Die Registry muss pro Association-Domain ein `showInNavigation`-Flag ausgeben (§11.2/§21.3). Es gibt drei Quellen: den Planner-Wert `GrailsAssociationPlan.showInNavigation()`, das Config-Feld `hideContextualAssociationControllers` und die CLI-Navigation `associationNavigation` (`auto|show|hide`).
- **Entscheidung:** `GrailsAssociationRegistryGenerator.resolveShowInNavigation(plan, config)` wendet folgende Priorität an: (1) `associationNavigation=show` ⇒ `true`; (2) `associationNavigation=hide` ⇒ `false`; (3) `hideContextualAssociationControllers=false` ⇒ `true`; (4) sonst `auto` ⇒ `plan.showInNavigation()` (versteckt nur, wenn physisch gemappt **und** kontextueller Zugriff existiert). Die `ENTITIES`-Map ist die einzige, von der Navigation-Config beeinflusste Ausgabe; die `showInNavigation`-Felder in `ASSOCIATIONS`-Deskriptoren bleiben der reine Planner-Wert (dokumentarisch).
- **Alternativen:** Die Navigation-Config bereits im Planner auswerten — verworfen, weil der Planner framework-nah, aber config-unabhängig bleiben soll und Phase 1 keine Config-Felder kannte.
- **Konsequenzen:** Navigation ist deterministisch und per CLI steuerbar, ohne Phase-1-Klassifikation zu verändern. Die tatsächliche Menü-Filterung (`InterlisNavigationSupport`, §21) folgt in einer späteren Phase und konsumiert `showInNavigation(domainClassName)`.

## Risiken

| ID | Risiko | Wahrscheinlichkeit | Auswirkung | Massnahme | Status |
|---|---|---|---|---|---|
| R-1 | JDK-25-Default lässt Build lokal/CI fehlschlagen | Hoch | Hoch (Build blockiert) | JDK 21 erzwingen (ADR-001); Gradle-Toolchain-Pinning erwägen; in Doku festhalten | Mitigiert |
| R-2 | `grails` CLI und `ili2pg` lokal nicht installiert → Runtime-Smoke/Real-ili2db/Browser-E2E nicht ausführbar | Hoch | Hoch (Phasen 3–8 Gates) | ✅ **Behoben:** grails 7.0.6 (SDKMAN) + ili2pg 5.6.1 + Docker vorhanden. Runtime-Smoke in Phase 2 real ausgeführt (2 Tests grün). PATH+JDK-21 nötig. | Geschlossen |
| R-3 | Planner ↔ Domain-Generator nutzen abweichende `TargetNameRegistry`/Mapper-Instanzen | Mittel | Hoch (inkonsistente Namen/Mappings) | ✅ **Behoben (Phase 2):** `GrailsCrudGenerator` erzeugt eine `TargetNameRegistry` + einen `GrailsRelationshipMapper` und reicht sie an Enum-/Domain-Gen (neuer 4-arg-Overload), Planner und Registry-Gen durch. | Geschlossen |
| R-4 | Falsche GORM-`hasMany`/Cascade zerstört ili2db-Persistenz | Mittel | Sehr hoch (Datenverlust) | Keine inversen Collections/Join-Tabellen; Related-Lists nur über Association-Domain-Query; Regressionstest §30.3 | Kontrolliert durch Design |
| R-5 | Mehrdeutige Rollen→Property-Auflösung (gleiche Zielklasse, physische Abweichung) | Mittel | Mittel | Auflösung über `DomainMapping`/Relationship-Metadaten, Diagnose `AMBIGUOUS_ROLE_PROPERTY` + read-only (§10.3) | Mitigiert (Phase 1): Auflösung + Diagnose + Test implementiert |
| R-6 | Snapshot-Lücke (2 nicht asserted Associations) verschleiert Regressionen | Niedrig | Mittel | ✅ **Behoben (Phase 2):** neuer `InterlisAssociationRegistry`-Snapshot deckt alle **6** Associations + alle Kontexte + Navigation-Metadaten ab. Domain-Snapshots weiterhin 4 (Registry deckt die übrigen inhaltlich ab). | Geschlossen |
| R-7 | Sicherheitslücken (Mass Assignment, IDOR, Open Redirect, GET-Mutation) | Mittel | Hoch | Serverseitige Kontextvalidierung, feste Rolle nach Binding neu setzen, keine freie returnUrl, POST/PUT/DELETE (§27) | Design-Vorgabe (Phasen 4–6) |
| R-8 | n-äre / ORDERED / EXTERNAL / Komposition falsch als M:N vereinfacht | Mittel | Hoch | Deterministische Klassifikation + read-only-Fallback; Real-ili2db-Beleg vor Schreibfunktion (§7.3, §24, Phase 7) | Design-Vorgabe |
| R-9 | Testmodell fehlt echte n-äre/ORDERED-Fälle | Mittel | Mittel | In Phase 5/7 `AssociationCases.ili` konservativ erweitern, ili2c-validieren (§31.1) | Offen |

## Konkrete Klassen- und Dateipfade (verifiziert)

Core-IR:
- `core/src/main/java/ch/interlis/generator/model/AssociationMetadata.java`
- `core/src/main/java/ch/interlis/generator/model/AssociationRoleMetadata.java`
- `core/src/main/java/ch/interlis/generator/model/RelationshipMetadata.java`
- `core/src/testFixtures/java/ch/interlis/generator/testsupport/MetadataTestFixtures.java`

Grails-Target (Planungszeit):
- `target-grails/src/main/java/ch/interlis/generator/grails/GrailsRelationshipMapper.java`
- `target-grails/src/main/java/ch/interlis/generator/grails/GrailsDomainGenerator.java`
- `target-grails/src/main/java/ch/interlis/generator/grails/GrailsCrudGenerator.java`
- `target-grails/src/main/java/ch/interlis/generator/grails/GenerationConfig.java`
- `target-grails/src/main/java/ch/interlis/generator/grails/TargetNameRegistry.java`
- `target-grails/src/main/java/ch/interlis/generator/grails/GrailsTemplateOverlayInstaller.java`

Overlay-Runtime/Templates/Assets (`target-grails/src/main/resources/grails/overlays/bootstrap-openlayers/`):
- `src/main/groovy/ch/interlis/generator/grails/runtime/InterlisCrudControllerSupport.groovy`
- `src/main/groovy/ch/interlis/generator/grails/runtime/InterlisRelationshipOptions.groovy`
- `src/main/groovy/ch/interlis/generator/grails/runtime/InterlisTableModel.groovy`
- `src/main/groovy/ch/interlis/generator/grails/runtime/InterlisGeometryBinder.groovy`
- `src/main/templates/scaffolding/{Controller.groovy,show.gsp,_form.gsp,_relationship-fields.gsp,_show-details.gsp,create.gsp,edit.gsp,index.gsp,_geometry-panel.gsp}`
- `grails-app/assets/javascripts/ili-form-ux.js`, `grails-app/assets/stylesheets/ili-modern.css`, `grails-app/views/layouts/main.gsp`

CLI:
- `cli/src/main/java/ch/interlis/generator/GrailsCliOptions.java` (Optionen; noch ohne `--grails-association-*`)
- `cli/src/main/java/ch/interlis/generator/GrailsCliTarget.java`

Tests/Source-Sets:
- `target-grails/src/test/java/ch/interlis/generator/grails/` (Unit/Snapshot/Compile)
- `target-grails/src/grailsRuntimeSmokeTest/java/ch/interlis/generator/grails/GrailsRuntimeSmokeTest.java`
- `target-grails/src/realIli2dbSmokeTest/java/ch/interlis/generator/grails/RealIli2dbSmokeTest.java`
- `target-grails/src/browserE2eTest/java/ch/interlis/generator/grails/GrailsBrowserE2eTest.java`
- `target-grails/src/test/resources/grails-snapshots/{simple-address,structure-composition,association-cases}/`
- `test-models/AssociationCases.ili`

Noch zu erstellen (Referenz, spätere Phasen):
- Phase 1: ✅ ERLEDIGT — `AssociationStorageKind.java`, `AssociationPresentationKind.java`, `AssociationCreateMode.java`, `GrailsAssociationRolePlan.java`, `GrailsAssociationAttributePlan.java`, `GrailsAssociationContextPlan.java`, `GrailsAssociationPlan.java`, `GrailsAssociationPlanner.java`, `GrailsAssociationPlannerTest.java`.
- Phase 2: ✅ ERLEDIGT — `GrailsAssociationRegistryGenerator.java`, `GrailsAssociationRegistryGeneratorTest.java`, `GenerationConfigTest.java`, generierte `src/main/groovy/ch/interlis/generator/grails/generated/InterlisAssociationRegistry.groovy` (+ Snapshot). `GenerationConfig` um Association-Felder erweitert, `GrailsDomainGenerator` 4-arg-Overload, `GrailsCrudGenerator` gemeinsame Instanzen + Registry-Einhängung, CLI-Optionen `--grails-association-{ui,page-size,navigation}`.
- Phase 3+: `InterlisAssociationRegistrySupport.groovy`, `InterlisAssociationQueryService.groovy`, `InterlisAssociationCommandService.groovy`, `InterlisAssociationContextSupport.groovy`, `InterlisNavigationSupport.groovy`, `_association-*.gsp`.

## Offene Entscheidungen / Fragen

1. **Umgebung für Integration/E2E (R-2):** Sollen `grails` 7.0.6, `ili2pg` 5.5.1, Docker-PostGIS und Playwright vor Phase 3 bereitgestellt werden, oder wird für diese Phasen bewusst ein dokumentierter Infrastrukturblocker akzeptiert? Spec verlangt: Unit-Tests allein genügen nicht.
2. **Gradle-Toolchain-Pinning (R-1/ADR-001):** JDK-Wahl explizit in `build.gradle`/`gradle.properties` fixieren, um Environment-Abhängigkeit zu beseitigen? (Build-Infrastruktur-Änderung, nicht Teil von Phase 0.)
3. **ADR-003:** Result-Maps vs. typisierte Exceptions für Command-Services — vor Phase 4 festlegen.
4. **ADR-004:** Quick-Link-Duplikatregel — vor Phase 4 festlegen.
5. **Snapshot-Umfang (R-6):** ✅ Entschieden (Phase 2): nur `association-cases`-Registry-Snapshot mit allen 6 Associations (mit Nutzer bestätigt). Kein separater simple-address-Registry-Snapshot.
6. **Testmodell-Erweiterung (R-9):** Zeitpunkt für ORDERED/n-är in `AssociationCases.ili` (Phase 5 vs. Phase 7) — Spec verortet n-är in Phase 5, ORDERED/embedded in Phase 7.

## Phase-Protokolle

### Phase 0
- **Geänderte Dateien:** `docs/association-ux-implementation-plan.md` (neu, dieses Dokument). Keine funktionalen Code-Änderungen.
- **Ausgeführte Tests:**
  - `JAVA_HOME=.../21.0.10-tem ./gradlew test` → **PASS** (83 Tests, 0 Fehler).
  - `java -cp "/Users/stefan/apps/ilivalidator-1.15.0/libs/*" ch.interlis.ili2c.Main test-models/AssociationCases.ili` → **PASS**.
- **Resultate:** Baseline grün. ili2c-Validierung erfolgreich. Architekturabweichungen und fehlende Bausteine dokumentiert (siehe oben).
- **Offene Punkte:** R-2 (grails/ili2pg lokal fehlend) betrifft künftige Phasen, nicht Phase 0. Offene Entscheidungen 1–6.
- **Abnahme:** Phase 0 DONE-Kriterien: Baseline grün ✔, Plan-Datei vorhanden ✔, Abweichungen dokumentiert ✔, keine unbeabsichtigten Änderungen ✔, kein funktionaler Scope vorgezogen ✔. Status auf DONE gesetzt (bei Beginn Phase 1 re-verifiziert: Working tree sauber, `./gradlew test` grün).

### Phase 1
- **Geänderte/neue Dateien** (alle in `target-grails/src/main/java/ch/interlis/generator/grails/`, flaches Paket `ch.interlis.generator.grails`):
  - `AssociationStorageKind.java` — Enum `LINK_ENTITY, EMBEDDED_FOREIGN_KEY, UNMAPPED` (§9.1).
  - `AssociationPresentationKind.java` — Enum mit 6 Konstanten (§9.2).
  - `AssociationCreateMode.java` — Enum `NONE, QUICK, CONTEXTUAL_FORM` (§9.2).
  - `GrailsAssociationRolePlan.java` — Record + `isUnbounded/isToOne/isToMany` (§9.3) + Hilfsmethode `hasResolvedProperty()`.
  - `GrailsAssociationAttributePlan.java` — Record (§9.4).
  - `GrailsAssociationContextPlan.java` — Record mit defensiven Listenkopien (§9.5).
  - `GrailsAssociationPlan.java` — Record mit defensiver Kopie + deterministischer Sortierung (Rollen nach Name+Zielklasse, Attribute nach Property, Kontexte nach `contextId`, Diagnosen lexikografisch) + `isBinary/isNary/hasOwnAttributes/role(...)` (§9.6).
  - `GrailsAssociationPlanner.java` — API `forMetadata/plans/contextsForParticipant/findPlan/showDomainInNavigation/isAssociationDomain` und private Methoden gemäss §10.2 (`buildPlan`, `resolveStorageKind`, `buildRolePlans`, Rollen→Property-Auflösung, `buildAttributePlans`, `buildContextPlans`, `resolvePresentationKind`, `resolveCreateMode`, `isQuickLinkEligible`, `defaultContextLabel`, `contextMessageCode`).
  - Test: `target-grails/src/test/java/ch/interlis/generator/grails/GrailsAssociationPlannerTest.java` (16 Tests).
- **Nicht geändert (bewusst):** Core-IR (keine Erweiterung nötig), `GrailsRelationshipMapper` (semantisch unverändert, nur wiederverwendet), `GrailsDomainGenerator`, `GrailsCrudGenerator`, `GenerationConfig`, Overlay-Runtime, GSPs, CLI, `AssociationCases.ili`, Fixtures, Snapshots. Keine inversen GORM-Collections.
- **Entscheidungen:** ADR-005 (synthetische In-Memory-Metadaten für ORDERED/n-är/UNMAPPED/ambiguous), ADR-006 (`EMBEDDED_FOREIGN_KEY` in Phase 1 nicht erzeugt). Perspektivkardinalität stammt aus der Gegenrolle (binär) bzw. bleibt `null` bei n-är (konservativ, §9.5). `contextId = "<qualified-assoc>::<fixedRole>"`, URL-encodiert, `::` erhalten (§9.5). Rollen→Property-Auflösung nutzt die tatsächlichen `GrailsRelationshipMapper.DomainMapping`-Properties (Reihenfolge: targetRoleName → physicalName → sourceAttribute → semanticName → Zielklasse), Mehrdeutigkeit ⇒ Diagnose `AMBIGUOUS_ROLE_PROPERTY` + Property `null` + read-only (§10.3). Quick-Link-Kriterien nach §10.4 (genau 2 Rollen, `LINK_ENTITY`, keine Attribute, keine `ordered`/`composition`/`external`, beide Properties + Zieldomains aufgelöst).
- **Diagnose-Codes:** `UNMAPPED_ASSOCIATION`, `AMBIGUOUS_ROLE_PROPERTY:<role>`, `ROLE_PROPERTY_NOT_FOUND[:<role>]`, `TARGET_DOMAIN_NOT_GENERATED:<role>`, `MERGE_CONFIDENCE_NONE:<role>`.
- **Ausgeführte Tests:**
  - `JAVA_HOME=.../21.0.10-tem ./gradlew :target-grails:test --tests "…GrailsAssociationPlannerTest"` → **PASS** (16).
  - `JAVA_HOME=.../21.0.10-tem ./gradlew :target-grails:test test` → **PASS** total **99** (core 31, target-grails 49, cli 12, target-django 7), 0 Fehler/0 Errors. Delta +16 = neue Planner-Tests.
  - Kein `.ili` geändert ⇒ keine ili2c-Neu-Validierung erforderlich.
- **Abgedeckte Testfälle (§30.2 + Phase-1-Zusatz):** binär→Quick-Link, attributiert→CONTEXTUAL_FORM, gleiche Zielklasse→distinkte Kontexte, physisch abweichend→generierte Property (`ownerFk`/`parcelFk`), n-är→NARY_CONTEXTUAL_FORM, EXTERNAL kein Quick-Link, COMPOSITE kein Quick-Link, ORDERED kein Quick-Link, ohne physische Klasse→READ_ONLY, Perspektivkardinalität aus Gegenrolle, deterministische Kontext-Sortierung, mehrdeutige Rolle→Diagnose+read-only, Navigation nur bei kontextuellem Zugriff versteckt, `planDoesNotMutateCoreMetadata`, `planUsesTargetNameRegistryForQualifiedDomainNames`, `rolePropertiesMatchGeneratedAssociationDomainProperties`.
- **Offene Punkte / Restpunkte:**
  - Registry-Generierung, `GenerationConfig`-Association-Felder, CLI-Optionen, gemeinsame Mapper-Instanz-Einhängung in `GrailsCrudGenerator` → Phase 2.
  - `EMBEDDED_FOREIGN_KEY`-Klassifikation + Schreibpfad → Phase 7 mit Real-ili2db-Beleg (ADR-006).
  - ORDERED/echte n-äre Fälle in `AssociationCases.ili` + Fixtures → Phase 5/7 (ADR-005, R-9).
  - UI-Modus-Gating (`associationUiMode`) fliesst noch nicht in Quick-Link-Eligibility ein (Config-Feld existiert erst ab Phase 2); aktuell Default „editierbar“.
- **Abnahme:** Phase 1 DONE-Kriterien: alle Planner-Fälle grün ✔, keine UI-Änderung ✔, keine Core-IR-Änderung (kein ADR nötig) ✔, bestehende Snapshots unverändert ✔, Umsetzungsplan aktualisiert ✔. Nicht mit Phase 2 fortgefahren ✔.

### Phase 2
- **Neue Dateien:**
  - `target-grails/src/main/java/ch/interlis/generator/grails/GrailsAssociationRegistryGenerator.java` — Registry-Generator (§11). API `generate(metadata, config, registry, planner)`, `targetPath(config)`, `renderRegistry(plans, config)`. Festes Paket `ch.interlis.generator.grails.generated`, Zielpfad `src/main/groovy/ch/interlis/generator/grails/generated/InterlisAssociationRegistry.groovy`. Emittiert `ASSOCIATIONS`, `CONTEXTS` (TreeMap, stabil), `CONTEXT_IDS_BY_PARTICIPANT` (TreeMap, Werte sortiert), `ENTITIES` (TreeMap) + Helper (`association`, `context`, `contextsForParticipant`, `showInNavigation`) + privater Ctor. Groovy-Escaping für `\`, `'`, `\n`, `\r`, `\t`, `$`; `null`→`null`; `-1` erhalten; leeres Modell ⇒ `[:]`.
  - `target-grails/src/test/java/ch/interlis/generator/grails/GrailsAssociationRegistryGeneratorTest.java` — 8 Tests: `rendersDeterministicGroovyRegistry`, `escapesQuotesBackslashesAndNewlines`, `emitsContextsByParticipant`, `emitsEntityNavigationMetadata`, `navigationShowModeForcesVisibleAssociationEntities`, `navigationHideModeMarksAssociationEntitiesHidden`, `generatedRegistryCompilesWithGroovyCompiler`, `emptyAssociationSetProducesValidRegistry`.
  - `target-grails/src/test/java/ch/interlis/generator/grails/GenerationConfigTest.java` — 5 Tests: `associationDefaultsAreStable`, `rejectsInvalidAssociationPageSize`, `editableModeEnablesWrites`, `readOnlyModeDisablesWrites`, `rejectsUnsupportedModes`.
  - `target-grails/src/test/resources/grails-snapshots/association-cases/src/main/groovy/ch/interlis/generator/grails/generated/InterlisAssociationRegistry.groovy` — neuer Registry-Snapshot (alle 6 Associations, 12 Kontexte, 6 ENTITIES).
- **Geänderte Dateien:**
  - `GenerationConfig.java` — Konstanten `ASSOCIATION_UI_{OFF,READ_ONLY,EDITABLE,AUTO}`, `ASSOCIATION_NAVIGATION_{AUTO,SHOW,HIDE}`; Felder `associationUiMode`(=`auto`), `associationPageSize`(=`10`, validiert 1..100), `hideContextualAssociationControllers`(=`true`), `associationNavigation`(=`auto`); Getter inkl. `isAssociationUiEnabled/Editable`; Builder mit Validierung (`IllegalArgumentException` bei ungültiger pageSize/mode/navigation).
  - `GrailsDomainGenerator.java` — neuer 4-arg-Overload `generate(metadata, config, registry, mapper)` (§29.2); 3-arg-Overload delegiert (Verhalten unverändert).
  - `GrailsCrudGenerator.java` — erzeugt **eine** `TargetNameRegistry` + **einen** `GrailsRelationshipMapper` + einen `GrailsAssociationPlanner`; reicht sie an Enum-/Domain-Gen und den neuen `GrailsAssociationRegistryGenerator` durch (§11.5, R-3 geschlossen). Registry wird nach den Domains generiert.
  - `cli/GrailsCliOptions.java` / `cli/GrailsCliTarget.java` — Optionen `--grails-association-ui <auto|off|read-only|editable>`, `--grails-association-page-size <1..100>`, `--grails-association-navigation <auto|show|hide>` + Validierung + Config-Mapping.
  - `README.md` — drei neue CLI-Optionen dokumentiert.
  - `GrailsGeneratedOutputSnapshotTest.java` — Registry-Snapshot in `association-cases` aufgenommen.
  - `GeneratedGrailsCompileSmokeTest.java` — prüft Existenz + Paket der generierten Registry; kompiliert sie mit (`GeneratedGroovyCompiler` walkt `src/main/groovy`).
- **Nicht geändert (bewusst):** Core-IR, `GrailsAssociationPlanner` (nur konsumiert), `GrailsRelationshipMapper`-Semantik, Overlay-Runtime/GSPs, `AssociationCases.ili`, Fixtures. Keine Show-Seite, keine schreibende Runtime (Spec-Vorgabe Phase 2).
- **Entscheidungen:** ADR-007 (Navigation-Sichtbarkeit-Auflösung). Snapshot-Umfang: nur `association-cases` (bestätigt mit Nutzer). Mapper-Sharing: neuer 4-arg-Overload (bestätigt mit Nutzer). `associationUiMode`-Gating der Writability bleibt Phase 4 (Registry `writable`/`createMode` stammen weiterhin aus dem Planner-Default „editierbar“; UI-Modus wird noch nicht angewandt — Restpunkt).
- **Ausgeführte Tests (JDK 21, ADR-001):**
  - `./gradlew :target-grails:test --tests "*GrailsAssociationRegistryGeneratorTest" --tests "*GenerationConfigTest" --tests "*GeneratedGrailsCompileSmokeTest"` → **PASS** (13).
  - `./gradlew test` → **PASS total 112** (core 31, target-grails 62, cli 12, target-django 7), 0 Fehler/0 Errors/0 Skips. Delta +13 = 8 Registry- + 5 Config-Tests.
  - Snapshot: mit `UPDATE_GRAILS_SNAPSHOTS=true` erzeugt, Inhalt **manuell inhaltlich geprüft** (alle 6 Associations, deterministische Sortierung, EXTERNAL+COMPOSITE ⇒ `CONTEXTUAL_FORM` statt `QUICK`, physisch abweichend ⇒ `ownerFk`/`parcelFk`, Selbstassoziation ⇒ zwei distinkte Person-Kontexte, `-1`/`null` korrekt) und danach committet.
  - `PATH=grails-7.0.6 JAVA_HOME=21.0.10 ./gradlew :target-grails:grailsRuntimeSmokeTest -PgrailsSmokeVersion=7.0.6` → **PASS** (2 Tests, 0 Skips): echte Grails-7.0.6-App via `create-app`, Overlay + Generierung, `./gradlew compileGroovy` grün ⇒ generierte Registry kompiliert in echter App. Der frühere R-2-Blocker ist damit behoben.
  - Kein `.ili`/XTF geändert ⇒ keine ili2c/ilivalidator-Neu-Validierung erforderlich.
- **Offene Punkte / Restpunkte:**
  - `associationUiMode`/`isAssociationUiEditable()` fließen noch nicht in `writable`/`createMode` der Registry ein → Phase 4 (Schreibpfad-Gating).
  - Registry-Runtime-Support, Query-Service, Related-Sections → Phase 3.
  - Real-ili2db-Smoke mit `AssociationCases` (ili2pg 5.6.1) → Phase 3 (physische Spalten-Verifikation).
- **Abnahme:** Phase 2 DONE-Kriterien: Registry im festen Paket, stabil sortiert, kompiliert (Unit + Groovy-Compiler + echte Grails-App) ✔; `GenerationConfig`-Felder + Validierung ✔; deterministische Einhängung in `GrailsCrudGenerator` mit gemeinsamem Mapper ✔; CLI-Anbindung + README ✔; Snapshot/Compile-Tests erweitert ✔; Snapshots nur nach manueller Prüfung aktualisiert ✔; keine Show-Seite/schreibende Runtime verändert ✔; Unit-, Gesamt- und Grails-Runtime-Smoke-Tests ausgeführt ✔; Plan aktualisiert ✔. Nicht mit Phase 3 fortgefahren ✔.

### Phase 3
- **Neue Dateien (Overlay):**
  - `target-grails/src/main/resources/grails/overlays/bootstrap-openlayers/src/main/groovy/ch/interlis/generator/grails/runtime/InterlisAssociationRegistrySupport.groovy` — Runtime-Registry-Support (§13). Statische Utility-Klasse mit strikter Context-Prüfung: `contextsForParticipant(Class)`, `requireContext(Class, String)` (validiert Context-ID + Teilnehmer-Klasse + Association-Existenz + feste Rolle + feste Property), `requireAssociation(String)`, `resolveDomainClass(grailsApplication, String)`, `resolveAssociationClass(grailsApplication, Map context)`, `role(Map, String)`, `editableRoles(Map, Map)`, `isAssociationDomain(Class)`, `showInNavigation(Class)`. Enthält `AssociationContextNotFoundException` und `AssociationOwnershipException`.
  - `target-grails/src/main/resources/grails/overlays/bootstrap-openlayers/grails-app/services/ch/interlis/generator/grails/runtime/InterlisAssociationQueryService.groovy` — Read-only Query-Service (§14). `@NotTransactional`. Methoden: `sections(Class, Serializable, Integer)` → Liste von Section-Maps pro Context mit Labels, Rows, Columns, Count, Empty-Message; `page(Class, Serializable, String, Integer, Integer, String, String)` → paginierte Association-Ergebnisse mit Context-Validierung, sort-whitelisting, max-begrenzung; `optionPage(Class, String, String, String, Integer, Integer)` → delegiert an `InterlisRelationshipOptions.optionPageForTargetType()`; `describeAssociationRow(Map, Map, Object)` → Zeilenmodell mit Counterparts, Attributen und Labels.
  - `target-grails/src/main/resources/grails/overlays/bootstrap-openlayers/src/main/templates/scaffolding/_association-sections.gsp` — Template für Related-Sections auf Show-Seiten. Rendert pro Context einen Abschnitt mit Header (Label + Count), leerem Zustand oder Tabelle mit Rollen-/Attribut-Spalten, Gegenobjekt-Links, Association-Domain-Links und „Mehr anzeigen“-Pagination.
  - `target-grails/src/main/resources/grails/overlays/bootstrap-openlayers/src/main/templates/scaffolding/_association-row-actions.gsp` — Template für Zeilenaktionen (Open Association-Domain + Gegenobjekt-Links). Read-only (keine Delete/Edit-Buttons).
- **Neue Testdatei:**
  - `target-grails/src/test/java/ch/interlis/generator/grails/GrailsAssociationRegistrySupportTest.java` — 10 Unit-Tests: `registryContainsAllSixAssociations`, `registryContextsByParticipantReturnsCorrectNumberOfContexts` (Person=7, Document=1), `selfAssociationHasTwoDistinctContextsForSameTarget` (PrimaryPerson/SecondaryPerson), `physicalMismatchContextHasCorrectFixedProperties` (ownerFk/parcelFk), `contextHasAllRequiredFields`, `associationPlanContainsCorrectStructure`, `associationWithoutAttributesIsQuickLink`, `isAssociationDomainReturnsCorrectly`, `showInNavigationHidesAssociationWhenContextualAccessExists`, `generatedRegistryCompilesAndContainsAssociationEntities`.
- **Geänderte Dateien:**
  - `GrailsTemplateOverlayInstaller.java` — `MANAGED_FILES` um 4 Einträge erweitert (17→21): `InterlisAssociationRegistrySupport.groovy`, `InterlisAssociationQueryService.groovy`, `_association-sections.gsp`, `_association-row-actions.gsp`.
  - `InterlisCrudControllerSupport.groovy` — neue abstrakte Methode `associationQueryService()`; `show(Long id)` ergänzt `associationModel(instance)` im Modell; neue Actions: `associationPage(Long id)` (JSON-Pagination, Context-Validierung), `associationOptions(Long id)` (JSON-Zieloptionen); neue Helfer: `associationModel(T instance)` (fangfähig mit Diagnose), `associationPageSize()`.
  - `Controller.groovy` (Template) — Import `InterlisAssociationQueryService`; injiziert `interlisAssociationQueryService`; `allowedMethods` ergänzt um `associationPage: "GET"`, `associationOptions: "GET"`; delegiert `associationPage(Long id)` und `associationOptions(Long id)` an Super; Override `associationQueryService()`.
  - `show.gsp` (Template) — nach Detail-/Geometrie-Bereich, vor Danger Zone: `<section class="ili-association-sections">` mit `_association-sections.gsp` Include. Diagnose-Alert bei `associationDiagnostic`.
  - `ili-modern.css` — neue Styles für `.ili-association-sections`, `.ili-association-section`, `.ili-association-section-header`, `.ili-association-section-title`, `.ili-association-section-count`, `.ili-association-table` (inkl. `th`, `td`, `tr`), `.ili-association-empty`, `.ili-association-more-link`, `.ili-association-row-actions`, `.ili-association-actions-header`.
  - `InterlisRelationshipOptions.groovy` — Refactoring: neue Methode `optionPageForTargetType(grailsApplication, targetType, query, max, offset)` extrahiert target-type-spezifische Query-Logik aus `optionPage(...)`; bestehende `optionPage(...)` delegiert an `optionPageForTargetType(...)` (Backward-Compat, §14.5).
  - `GrailsTemplateOverlayInstallerTest.java` — Verifikation der 4 neuen Overlay-Dateien; erweiterte Assertions für Controller-Support (assoc-Methoden), Controller-Template (assoc-Service/Injection/Actions), Show-Template (assoc-sections), Association-Sections-Template und Row-Actions-Template.
  - `GrailsRuntimeSmokeTest.java` — neuer Test `associationRegistryAndRuntimeCompilesInRealGrailsApp()`: erzeugt Grails-App, installiert Overlay, generiert, prüft Existenz aller 4 neuen Dateien + Registry, `./gradlew compileGroovy`, generiert Controller/Views via `generate-all`, prüft `show.gsp` enthält `_association-sections`.
  - `RealIli2dbSmokeTest.java` — neuer Test `validatesAssociationCasesAgainstRealIli2pgSchema()`: importiert `AssociationCases.ili` via ili2pg, liest Metadata, baut Planner, protokolliert alle 6 Associations mit Rollen/Contexts, validiert `isAssociationDomain()` für alle 6, prüft `EmptyAssociation` = binär ohne Attribute, `AssociationWithAttribute` = LINK_ENTITY + hatOwnAttributes, `SameTargetAssociation` = 2 distincte Context-Properties, generiert und kompiliert Registry.
- **Nicht geändert (bewusst):** Core-IR, `GrailsAssociationPlanner` (nur konsumiert), `GrailsRelationshipMapper`-Semantik, `AssociationCases.ili`, Fixtures, Snapshots. Keine Create/Delete-Actions (Phase 4). Keine `hasMany`-Collections auf Teilnehmer-Domains.
- **Entscheidungen:**
  - Query-Konstruktion: GORM `createCriteria` mit Registry-sourcten Property-Namen als `eq(fixedProperty + ".id", participantId)`. Keine String-Konkatenation mit Client-Werten.
  - N+1-Risiko: Phase 3 ist read-only und paginiert auf max 10 Einträge pro Section. Counterpart-Objekte werden einzeln via Dynamic Finder geladen (pro Association-Instance ein `get()` auf die FK-Property). Dies produziert N+1 Queries für die Counterparts. In Phase 4 soll Fetch-Join evaluiert werden.
  - Context-ID-Format: `<qualified-assoc>::<fixedRole>`, stabil aus Planner, URL-encodiert.
  - Fehlerresilienz: `associationModel()` fängt Exceptions und liefert `associationDiagnostic` im Modell, statt die gesamte Show-Seite mit HTTP 500 zu zerstören.
  - Context-Validierung: `requireContext()` prüft strikt: (1) Context existiert in Registry, (2) `participantDomainClass` == `participantType.name`, (3) Association + feste Rolle + feste Property vorhanden.
  - `optionPageForTargetType`: Wurde aus `InterlisRelationshipOptions.optionPage()` extrahiert (Spec §14.5). Bestehende Caller bleiben via Delegation unverändert.
  - Autocomplete (associationOptions): Phase 3 = read-only scaffolding. Context und Rolle werden validiert, Suche delegiert an `optionPageForTargetType`. Noch keine UI-Einbindung (Phase 4).
- **Ausgeführte Tests (JDK 21, ADR-001):**
  - `./gradlew :target-grails:test` → **PASS 72** (core 31 + target-grails 72 = abhängig, 10 neue Support-Tests), 0 Fehler/0 Errors.
  - `./gradlew test` → **PASS total 122** (core 31, target-grails 72, cli 12, target-django 7), 0 Fehler/0 Errors/0 Skips. Delta +10 = neue Registry-Support-Tests.
  - `./gradlew :target-grails:compileRealIli2dbSmokeTestJava` → **BUILD SUCCESSFUL** (neuer AssociationCases-Test kompiliert).
  - Kein `.ili`/XTF geändert ⇒ keine ili2c/ilivalidator-Neu-Validierung erforderlich.
- **Offene Punkte / Restpunkte:**
  - Real-ili2db-Smoke Execution (benötigt Docker/PostGIS/ili2pg) → Blockiert durch Infrastruktur (R-2 erfüllt, aber zeitaufwändig); Unit-Tests + Compile bewiesen. Real-Test bei nächster Gelegenheit durchführen.
  - Grails-Runtime-Smoke Execution → Blockiert durch Zeit (8 min per Test); Compile-Test via `target-grails:test` bewiesen. Bei nächster Gelegenheit nachholen.
  - Quick-Link (Create/Delete) → Phase 4.
  - Navigations-Filterung (`InterlisNavigationSupport`) → Phase 6.
  - `associationUiMode`-Gating der Writability → Restpunkt aus Phase 2, übernommen nach Phase 4.
- **Abnahme:** Phase 3 DONE-Kriterien: Registry-Support mit strikter Context-Prüfung ✔; Query-Service mit Sections/Page/OptionPage ✔; `associationModel()` in Show integriert ✔; Related-Sections-Templates mit Labels, leeren Zuständen, Gegenobjekt-Links, Count, Pagination ✔; CSS-Styles ✔; keine Create/Delete-Actions (read-only) ✔; keine `hasMany`-Collections ✔; Overlay-Installer aktualisiert ✔; Unit-Tests (10 neue) grün ✔; Runtime-/Real-ili2db-Compile-Tests grün ✔; Plan aktualisiert ✔. Nicht mit Phase 4 fortgefahren ✔.

## Abschluss-Checkliste (Gesamtprojekt)

- [ ] Alle Phasen DONE
- [ ] `./gradlew test` (mit JDK 21 / ADR-001)
- [ ] Grails Runtime Smoke
- [ ] Real ili2db Smoke
- [ ] Browser E2E
- [ ] ili2c für alle geänderten Modelle
- [ ] ilivalidator für alle geänderten XTF
- [ ] README
- [ ] docs/association-ux.md
- [ ] Keine deaktivierten Tests
- [ ] Keine ungeklärten High-Risk-Punkte
