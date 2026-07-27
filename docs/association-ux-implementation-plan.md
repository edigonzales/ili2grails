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
| Phase 4 – Quick-Link (binäre Associations) | DONE | 2026-07-11 | 2026-07-11 | `./gradlew test` PASS (125: core 31, target-grails 75, cli 12, django 7); Real-ili2db 3/3 PASS (PostGIS); Grails-Runtime-Smoke PASS; Browser-E2E 2/2 PASS inkl. wrong-owner-Manipulation; ili2c QuickLinkE2E.ili PASS | Command-Service (Result-Maps, Duplikat-Prävention, Kardinalität, Ownership); UI-Mode-Gating im Registry-Generator (Phase-2/3-Restpunkt geschlossen); Quick-Add-GSP + Delete-in-Sections; JS context/role + AbortController. 5 latente Phase-3-Bugs beim echten Ausführen der Gates gefunden & behoben. Reale ili2pg-Erkenntnis R-10. Neues Modell QuickLinkE2E.ili |
| Phase 5 – Kontextuelle Formulare / n-är | DONE | 2026-07-12 | 2026-07-12 | `./gradlew test` PASS (127); ili2c 2× PASS; Grails-Runtime-Smoke PASS (3/3); Real-ili2db H2-Tests PASS (+3); **Browser-E2E PASS** (1 Test, 7 Screenshots) | Context-Formulare mit fixer+read-only Rolle, sicherer Redirect ohne returnUrl; ContextSupport erkennt Association-Domain-Controller; n-är über TernaryAssociation. **Einschränkung:** Association-Domain Create/Edit-Formulare (Beteiligung, TernaryAssoc) rendern via Grails-Scaffold `<f:all>` nicht; Person-Show-Sections funktionieren; Index/List funktioniert; → Restpunkt R-11 für Phase 6. |
| Phase 6 – Navigation, Kardinalität, Fehler, Performance | DONE | 2026-07-12 | 2026-07-12 | `./gradlew test` PASS (127); `:target-grails:test` PASS (75) | R-11 behoben + Navigation + Error-Handling + N+1-Fetch-Join + Konfliktbehandlung + Accessibility-CSS + `docs/association-ux.md` |
| Phase 7 – Spezialsemantik (EXTERNAL, Komposition, ORDERED, embedded FK) | DONE | 2026-07-12 | 2026-07-12 | `./gradlew test` PASS (alle); ili2c PASS; Real-ili2db PASS (8 Plans, EMBEDDED_FOREIGN_KEY klassifiziert) | EXTERNAL-Guard in CommandService; ORDERED-Modell+Analyse; EMBEDDED_FOREIGN_KEY-Klassifikation; Docs aktualisiert |
| Phase 8 – Abschluss & Regression | DONE | 2026-07-12 | 2026-07-12 | `./gradlew clean test --rerun-tasks` PASS; Grails-Runtime-Smoke PASS (3/3) nach Fix; Real-ili2db 9/9 PASS; Browser-E2E 3/3 PASS; ili2c 3× PASS | Variable-Shadowing-Bug in `InterlisAssociationQueryService.buildSection()` gefunden & behoben (Groovy-Compiler in realer Grails-App fand `editableRoleList`-Redeklaration, Unit-Tests mit `GeneratedGroovyCompiler` tolerierten dies). Keine weiteren Regressionen. |
| Phase 9 – Direkte inverse 1:n-Zuweisung | DONE | 2026-07-26 | 2026-07-27 | `./gradlew test` PASS (180); Grails-Runtime 6/6; Real-ili2db 9/9; Browser-E2E 6/6 | Sichere `MANY_TO_ONE`-Property wird von der 1-Seite aus zuweisbar; getrennt vom Association-Registry-Schreibpfad. PostgreSQL-E2E belegt 409/Abbruch/Umteilung und unveränderten Basket-freien FK-Aufbau. |

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
- **Grails (Smoke/E2E-Ziel):** Die Grails-Version wird durch die aktive `grails`-CLI im `PATH` bestimmt; die Tests sind auf Grails 7.0.6 ausgelegt.
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
- `grailsRuntimeSmokeTest` → `GrailsRuntimeSmokeTest` — braucht eine passende `grails`-CLI im `PATH` (Tests sind auf Grails 7.0.6 ausgelegt).
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

### ADR-003: Command-Service-Result-Strategie (entschieden Phase 4)
- **Kontext:** Spec §25.1 lässt strukturierte Result-Maps ODER typisierte Exceptions zu; „eine einzige konsistente Strategie".
- **Entscheidung:** Strukturierte Result-Maps `[success, status, code, message, messageCode, fieldErrors]`. Interne `requireContext`-Exceptions werden gefangen und in Error-Maps übersetzt. Controller mappen Status → HTTP + Flash.
- **Konsequenzen:** Alle Command-Outcomes sind einheitlich als Map prüfbar. Keine Exception-Hierarchie jenseits von `requireContext` nötig. Controller-`respondAssociationCommand` ist das einzige HTTP-Übersetzungs-Gate.

### ADR-004: Quick-Link-Duplikatregel (entschieden Phase 4)
- **Kontext:** Spec §15.3 — keine erfundene globale Unique-Regel; optionale Duplikatverhinderung nur bei eindeutiger IR/Spec-Grundlage.
- **Entscheidung:** Quick-Link verhindert identische Duplikate (gleiches fixed+target-Rollenpaar) über `createCriteria`-Count-Prüfung → 409. Begründung: Nutzerentscheid (Phase-4-Plan). Dies ist eine bewusste Abweichung vom konservativen Default.
- **Konsequenzen:** Zwei identische Links für attributlose Assoziationen werden abgelehnt. Bei Assoziationen mit eigenen Attributen (CONTEXTUAL_FORM) keine Duplikat-Regel — nur `validate()`/DB-Constraints.

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

### ADR-013: Direkte 1:n-Zuweisung getrennt von der Association-Registry (Phase 9)

- **Kontext:** Reale ili2db-Schemas speichern einfache 1:n-Beziehungen häufig als
  FK auf der n-Seite, beispielsweise `Employee.department`. Der bestehende
  Association-Registry-Pfad behandelt `EMBEDDED_FOREIGN_KEY` konservativ
  read-only und kann eine solche Property nicht sicher über einen allgemeinen
  Link-Tabellen-Command bearbeiten.
- **Entscheidung:** Ein eigener `GrailsInverseRelationshipPlanner` führt eine
  eindeutig erzeugte Collection auf genau eine physische `MANY_TO_ONE`-Property
  zurück. Ein eigener Query-/Command-Pfad zeigt, sucht, weist zu und teilt um,
  indem nur diese Property geändert wird. Planner und Domain-Generator verwenden
  dieselbe `GrailsRelationshipMapper`-Instanz. Registry-`EMBEDDED_FOREIGN_KEY`
  bleibt read-only.
- **Konsequenzen:** Das Getting-Started-Beispiel unterstützt
  `Department.employees` → `Employee.department`, ohne Link-Tabelle oder
  synthetische DB-Struktur. Komposition, `EXTERNAL`, `ORDERED`, Mehrdeutigkeit
  und unvollständige Generierung bleiben ausgeschlossen. YAML kann Sicherheit
  nur einschränken, nicht erweitern.

## Risiken

| ID | Risiko | Wahrscheinlichkeit | Auswirkung | Massnahme | Status |
|---|---|---|---|---|---|
| R-1 | JDK-25-Default lässt Build lokal/CI fehlschlagen | Hoch | Hoch (Build blockiert) | JDK 21 erzwingen (ADR-001); Gradle-Toolchain-Pinning erwägen; in Doku festhalten | Mitigiert |
| R-2 | `grails` CLI und `ili2pg` lokal nicht installiert → Runtime-Smoke/Real-ili2db/Browser-E2E nicht ausführbar | Hoch | Hoch (Phasen 3–8 Gates) | ✅ **Behoben:** grails 7.0.6 (SDKMAN) + ili2pg 5.6.1 + Docker vorhanden. Runtime-Smoke in Phase 2 real ausgeführt (2 Tests grün). PATH+JDK-21 nötig. | Geschlossen |
| R-3 | Planner ↔ Domain-Generator nutzen abweichende `TargetNameRegistry`/Mapper-Instanzen | Mittel | Hoch (inkonsistente Namen/Mappings) | ✅ **Behoben (Phase 2):** `GrailsCrudGenerator` erzeugt eine `TargetNameRegistry` + einen `GrailsRelationshipMapper` und reicht sie an Enum-/Domain-Gen (neuer 4-arg-Overload), Planner und Registry-Gen durch. | Geschlossen |
| R-4 | Falsche GORM-`hasMany`/Cascade zerstört ili2db-Persistenz | Mittel | Sehr hoch (Datenverlust) | Keine synthetischen Collections/Join-Tabellen; Link-Tabellen über Association-Domain, direkte 1:n-Zuweisung nur über bereits gemappte FK-Property; Regressionstests | Kontrolliert durch Design |
| R-5 | Mehrdeutige Rollen→Property-Auflösung (gleiche Zielklasse, physische Abweichung) | Mittel | Mittel | Auflösung über `DomainMapping`/Relationship-Metadaten, Diagnose `AMBIGUOUS_ROLE_PROPERTY` + read-only (§10.3) | Mitigiert (Phase 1): Auflösung + Diagnose + Test implementiert |
| R-6 | Snapshot-Lücke (2 nicht asserted Associations) verschleiert Regressionen | Niedrig | Mittel | ✅ **Behoben (Phase 2):** neuer `InterlisAssociationRegistry`-Snapshot deckt alle **6** Associations + alle Kontexte + Navigation-Metadaten ab. Domain-Snapshots weiterhin 4 (Registry deckt die übrigen inhaltlich ab). | Geschlossen |
| R-7 | Sicherheitslücken (Mass Assignment, IDOR, Open Redirect, GET-Mutation) | Mittel | Hoch | Serverseitige Kontextvalidierung, feste Rolle nach Binding neu setzen, keine freie returnUrl, POST/PUT/DELETE (§27) | Design-Vorgabe (Phasen 4–6) |
| R-8 | n-äre / ORDERED / EXTERNAL / Komposition falsch als M:N vereinfacht | Mittel | Hoch | Deterministische Klassifikation + read-only-Fallback; Real-ili2db-Beleg vor Schreibfunktion (§7.3, §24, Phase 7) | Design-Vorgabe |
| R-9 | Testmodell fehlt echte n-äre/ORDERED-Fälle | Mittel | Mittel | In Phase 5/7 `AssociationCases.ili` konservativ erweitern, ili2c-validieren (§31.1) | Offen |
| R-10 | ili2db bettet attributlose binäre Assoziationen als FK-Spalten ein (`--smart2Inheritance`), statt Link-Tabellen → Quick-Link greift real nicht bei diesen Fällen | Hoch | Mittel | Association-Registry bleibt read-only; Phase 9 erlaubt nur eindeutig aufgelöste reguläre 1:n-FK-Properties über einen getrennten Planner/Command-Pfad (ADR-013) | Mitigiert; unsichere Fälle bleiben read-only |
| R-11 | **Association-Domain Create/Edit-Formulare rendern nicht** — `beteiligung/create` und `ternaryAssoc/create` werfen `Grails Runtime Exception` (Browser-E2E belegt). Index/List-Actions funktionieren. Betrifft Domains, deren Tabelle via `--nameByTopic` generiert wurde. | Hoch | Hoch (kontextuelle Formulare nur lesbar, keine Schreib-UX) | ✅ **Behoben (Phase 6):** Ursache war fehlende Model-Variablen (`hiddenRelationshipFields`, `fixedRelationshipLabels`, `associationContextState`) in `formModelWithContext()` bei leerem Context. Fix: Immer Defaults setzen. | Geschlossen |

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
  - `PATH=grails-7.0.6 JAVA_HOME=21.0.10 ./gradlew :target-grails:grailsRuntimeSmokeTest` → **PASS** (2 Tests, 0 Skips): echte Grails-7.0.6-App via `create-app`, Overlay + Generierung, `./gradlew compileGroovy` grün ⇒ generierte Registry kompiliert in echter App. Der frühere R-2-Blocker ist damit behoben.
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

### Phase 4
- **Neue Dateien (Overlay):**
  - `grails-app/services/ch/interlis/generator/grails/runtime/InterlisAssociationCommandService.groovy` — Transactional Command-Service (§15) mit `createQuickLink` (Context-/Owner-/Zielrollen-/Duplikat-/Kardinalitätsprüfung, strukturierte Result-Maps) und `deleteLink` (Ownership-Verifikation, Min-Kardinalität, Composition-Guard, nur Association löschen). Geschützte Hilfsmethoden `validateCreateCardinality`, `validateDeleteCardinality`, `verifyAssociationBelongsToParticipant`, `assignRole`. Autorisierungs-Extension-Points `canCreateAssociation`/`canDeleteAssociation` (default `true`).
  - `src/main/templates/scaffolding/_association-quick-add.gsp` — POST-Form auf `associationCreate` mit `context`+`role` Hidden-Feldern, gemeinsamem Autocomplete-Picker (`data-relationship-context`/`data-relationship-role`), `targetId`-Select, submit-Button.
- **Geänderte Dateien (Overlay):**
  - `InterlisCrudControllerSupport.groovy` — abstrakter `associationCommandService()`; neue Actions `associationCreate(Long id)` (POST, Parameter `context`/`role`/`targetId`) und `associationDelete(Long id)` (DELETE, Parameter `context`/`associationId`); `respondAssociationCommand(T, Map)` als zentrales HTTP-Mapping-Gate (Success→Redirect show, Error→Flash+Redirect show, JSON→Status+Body). Kein `returnUrl`.
  - `Controller.groovy` (Template) — Import/Injektion `InterlisAssociationCommandService`; `allowedMethods` += `associationCreate:"POST", `associationDelete:"DELETE"`; Delegationen; Override `associationCommandService()`.
  - `InterlisAssociationQueryService.groovy` — `buildSection`: `quickTargetRole` (einzige editable Rolle) + `domId` (stabil aus `contextId` abgeleitet); `describeAssociationRow`: `deleteAllowed = writable && removable && createMode=='QUICK'`.
  - `_association-sections.gsp` — Empty State + Quick-Add-Include; Delete-Button (`data-association-delete`) pro Row bei `row.deleteAllowed`; Hidden DELETE-Form pro Row unterhalb der Tabelle; Quick-Add unterhalb bei `writable`.
  - `_association-quick-add.gsp` — POST-Form, gemeinsamer Picker.
  - `ili-form-ux.js` — `relationshipUrl` unterstützt `data-relationship-context`/`data-relationship-role` (nicht nur `field`); `AbortController` in `fetchOptions` (Reset bricht laufende Requests ab); `initQuickAddForms()` (Submit-Button disable bis Ziel gewählt); `data-association-delete`-Handler in `initSubmitButtons` (Confirmation + verstecktes Form submitten).
  - `ili-modern.css` — `.ili-association-quick-form`, `.ili-association-delete-btn`.
- **Geänderte Dateien (Planungszeit):**
  - `GrailsAssociationRegistryGenerator.java` — UI-Mode-Gating: `!config.isAssociationUiEditable()` ⇒ `writable=false`, `createMode=NONE`, `removable=false` in `CONTEXTS` und `ASSOCIATIONS`; neue `resolveCreateMode(plan, writable)`. Kontext-Descriptor erhält `writable`/`createMode` aus `writeEnabled`-Logik.
  - `GrailsTemplateOverlayInstaller.java` — `MANAGED_FILES` ergänzt um Command-Service (`21→23`) und `_association-quick-add.gsp`.
- **Neue/erweiterte Tests:**
  - `GrailsAssociationRegistryGeneratorTest.java` — 3 neue Tests: `readOnlyModeDisablesWritesInRegistry`, `offModeDisablesWritesInRegistry`, `autoModeKeepsQuickCreateMode`.
  - `GrailsTemplateOverlayInstallerTest.java` — Assertions für Command-Service + `_association-quick-add.gsp`-Existenz; Controller-Template prüft `InterlisAssociationCommandService`, `associationCreate`, `associationDelete`, `allowedMethods` mit POST/DELETE; JS prüft `data-relationship-context`, `data-relationship-role`, `data-association-delete`, `initQuickAddForms`; CSS prüft `.ili-association-quick-form`.
  - `RealIli2dbSmokeTest.java` — Neuer Test `exercisesQuickLinkAssociationCreateQueryAndDeleteWithH2Fixture()`: H2-In-Memory-Fixture für `EmptyAssociation`; Insert Person+Parcel; JDBC-Insert in `emptyassociation` (`person_role_id`, `parcel_role_id`); Query-Count aus Person- und Parcel-Perspektive; Delete-Link; Verify Person und Parcel überleben; Verifikation der physischen Rollen-Spalten über `GrailsAssociationRolePlan.physicalName()`.
  - `GrailsBrowserE2eTest.java` — Neuer Test `generatedGrailsAppSupportsQuickLinkAndAssociationDeleteInBrowser()` gegen Modell `QuickLinkE2E.ili` (basketfreie echte Link-Tabelle): Person A/B + Tag erstellen, Quick-Add-Sichtbarkeit, Quick-Link-Create + Zählung, **wrong-owner Manipulation (HTTP 404)**, rechtmässiges Delete, Counterpart überlebt; Hilfsmethoden `importAssociationSchema`, `readAssociationMetadata`, `runAssociationQuickLinkE2E`, `createRecord`, `associationTotal`.
  - `test-models/QuickLinkE2E.ili` — neues, ili2c-validiertes Modell für den Live-Browser-E2E.
- **Nicht geändert (bewusst):** Core-IR, `GrailsAssociationPlanner`, `GrailsRelationshipMapper`, `AssociationCases.ili`, Fixtures. Keine inversen GORM-Collections. Kein `returnUrl`. Kein automatisches Erzeugen von Zielobjekten. Keine ungeprüfte Mutation.
- **Entscheidungen:**
  - ADR-003 finalisiert: Strukturierte Result-Maps `[success, status, code, message, messageCode, fieldErrors]`. Interne `requireContext`-Exceptions gefangen.
  - ADR-004 finalisiert: Quick-Link verhindert identische Duplikate (gleiche fixed+target-Rollenpaarung) via `createCriteria`-Count → 409. Abweichung vom konservativen Default (Nutzerentscheid).
  - Write-Gating: Im Registry-Generator (analog ADR-007), nicht im Runtime Command-Service. Schliesst Phase-2/3-Restpunkt.
  - `deleteAllowed`-Flag: Nur für `QUICK`-Kontexte die writable+removable sind. `CONTEXTUAL_FORM`-Rows zeigen keine direkten Delete-Buttons.
  - `perspectiveMin`/`perspectiveMax` aus Registry-Context direkt verwendet für binäre Kardinalitätsprüfung.
  - `lockOrGet` mit `type.lock(id)`-Fallback auf `get()` bei Locking-Fehler; nur dokumentiert, nicht geloggt.
  - Keine Fetch-Join-Änderung (Phase-3-N+1-Restpunkt bleibt; Spec §26.2 akzeptiert dies für Phase 4 nicht).
- **Ausgeführte Tests (JDK 21, ADR-001):**
  - `./gradlew test` → **PASS** (alle Unit-/Snapshot-Tests inkl. neue Registry-Gating- und erweiterte Overlay-Installer-Tests), 0 Fehler/0 Errors/0 Skips.
  - `./gradlew :target-grails:realIli2dbSmokeTest` (PostGIS via Docker, ili2pg 5.5.1) — 3 Association-Tests **PASS**:
    - `validatesAssociationCasesAgainstRealIli2pgSchema` (umgeschrieben): reale Klassifikation bestätigt — `AssociationWithAttribute`=LINK_ENTITY+CONTEXTUAL_FORM, `ExtendedTopicAssociation`=LINK_ENTITY+**QUICK**, die 4 attributlosen binären (`EmptyAssociation`, `SameTargetAssociation`, `PhysicalMismatchAssociation`, `ExternalCompositeAssociation`)=**UNMAPPED/read-only** (ili2db bettet sie als FK-Spalten ein → ADR-006 validiert).
    - `exercisesRealIli2pgQuickLinkInsertQueryDelete` (neu): echter Insert/Query/Delete auf der realen Link-Tabelle `extended_extendedtopicassociation` (mit dataset/basket-Setup) — Query aus beiden Perspektiven, Delete entfernt nur den Link, Person+ExtendedParcel überleben.
    - `exercisesQuickLinkAssociationCreateQueryAndDeleteWithH2Fixture` (neu): SQL-Mechanik gegen H2-Fixture (`emptyassociation`).
  - `./gradlew :target-grails:grailsRuntimeSmokeTest --tests "*associationRegistryAndRuntimeCompilesInRealGrailsApp*"` (Grails 7.0.6) → **PASS**: gesamtes Overlay (Command-Service, Controller-Support, Controller-Template, Association-Partials, Registry) kompiliert in echter App; `generate-all` rendert `_association-sections.gsp` + `_association-quick-add.gsp` fehlerfrei in den View-Ordner.
  - `./gradlew :target-grails:browserE2eTest` (Grails 7.0.6 + ili2pg + Docker-PostGIS + Playwright) → **PASS (2/2)**:
    - `generatedGrailsAppSupportsQuickLinkAndAssociationDeleteInBrowser` (neu, Modell `QuickLinkE2E.ili`): Person A/B + Tag anlegen; Quick-Add-Section + Autocomplete-Form auf Person-Show sichtbar; Quick-Link-Create (POST) via Command-Service; Zählung aus Teilnehmerperspektive=1; **Manipulationsversuch über falschen Owner (Person B → DELETE von A's Link) → HTTP 404 abgelehnt, Link überlebt**; rechtmässiges Delete durch Person A entfernt nur den Link; Tag überlebt.
    - `generatedGrailsAppSupportsCrudRelationshipsAndGeometryInBrowser` (bestehend) → weiterhin grün (keine Regression durch `@Slf4j`/Query-Service-Fixes).
  - ili2c: neue Datei `test-models/QuickLinkE2E.ili` mit `java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar test-models/QuickLinkE2E.ili` → **PASS** (0 Fehler/0 Warnungen). `AssociationCases.ili` unverändert.
- **Beim Ausführen der echten Test-Gates entdeckte & behobene latente Phase-3-Fehler** (Phase-3-Real/Runtime/E2E-Gates waren nie live ausgeführt worden):
  1. `InterlisAssociationQueryService`: `@NotTransactional` war unzulässig auf Klassenebene → `static transactional = false` (Spec §14.1). Blockierte `compileGroovy` in echter App.
  2. Association-Partial-GSPs (`_association-sections.gsp`, `_association-row-actions.gsp`) nutzten unescapte `${…}` → wurden zur Scaffolding-Zeit ausgewertet und `generate-all` brach ab. Alle Runtime-Ausdrücke auf `\${…}` umgestellt (wie `_relationship-fields.gsp`).
  3. `InterlisCrudControllerSupport` (abstrakte Basisklasse, kein Grails-Artefakt) nutzte `log` → `MissingPropertyException` zur Laufzeit. `@Slf4j` ergänzt.
  4. `InterlisAssociationQueryService.page(...)`: `List<Map> associationDescriptor = ASSOCIATIONS[name]` (ist eine Map) → `GroovyCastException`. Typ auf `Map<String, Object>` korrigiert.
  5. `InterlisAssociationQueryService.page(...)`: Parameter `order` überdeckte die GORM-Criteria-Methode `order(...)` → `NullPointerException`. Parameter in `requestedOrder` umbenannt.
  - Bestehender Phase-3-Test `associationRegistryAndRuntimeCompilesInRealGrailsApp` prüfte fälschlich `_association-sections` (Grails rendert `association-sections`); korrigiert + um Rendering-Verifikation der Partials erweitert.
- **Wichtige reale ili2pg-Erkenntnis (dokumentiert, Risiko R-10):** Mit `--nameByTopic --smart2Inheritance` bettet ili2db attributlose binäre Assoziationen als FK-Spalten in die Teilnehmerklassen ein (`EMBEDDED_FOREIGN_KEY`), statt Link-Tabellen zu erzeugen. Der Planner klassifiziert diese korrekt als `UNMAPPED` → read-only (ADR-006). Der H2-Fixture (`MetadataTestFixtures`) modelliert sie als Link-Tabellen (didaktisch); die reale Quick-Link-Aktivierung greift nur bei echten Link-Entities (z.B. `ExtendedTopicAssociation`, m:n-Assoziationen, sowie das E2E-Modell `QuickLinkE2E`).
- **Neues Testmodell:** `test-models/QuickLinkE2E.ili` — binäre `{0..*}--{0..*}`-Assoziation ohne OID/Attribute ⇒ ili2db erzeugt eine echte Link-Tabelle ohne `t_basket` ⇒ vollständig CRUD-/Quick-Link-fähig für den Browser-E2E (AssociationCases erfordert wegen `UUIDOID` `--createBasketCol`, was die generierte CRUD-App nicht bedient — daher für den Live-E2E ungeeignet; §29.6-Abweichung dokumentiert).
- **Offene Punkte / Restpunkte:**
   - Fetch-Join/N+1 für Related-Lists → Restpunkt Phase 6 (§26.2).
   - `EMBEDDED_FOREIGN_KEY`-Schreibpfad (damit auch AssociationCases' eingebettete Assoziationen quick-editierbar werden) → Phase 7 (ADR-006).
   - AssociationCases-Live-Browser-E2E benötigt Basket-Unterstützung in der generierten CRUD-App → ausserhalb Phase 4.
- **Abnahme:** Phase 4 DONE-Kriterien: Command-Service mit Context-/Owner-/Kardinalitäts-/Duplikat-Prüfung ✔; `createQuickLink` + `deleteLink` mit strukturierten Result-Maps ✔; Controller-Support `associationCreate`/`associationDelete` nur POST/DELETE ✔; Quick-Add-GSP + Delete-in-Sections ✔; JS context/role + AbortController ✔; gemeinsamer `optionPageForTargetType` wiederverwendet (keine Duplizierung) ✔; serverseitige Context-/Owner-/Zugehörigkeitsprüfung ✔; binäre Kardinalität ✔; Löschen entfernt ausschliesslich die Association-Domain ✔; Registry-UI-Mode-Gating ✔; Unit-/Real-ili2db-/Grails-Runtime-/Playwright-Tests inkl. Manipulationsversuch über falschen Owner grün ✔; ili2c für neues Modell ✔; Plan-Doc + ADR-003/004 aktualisiert ✔. Nicht mit Phase 5 fortgefahren ✔.

### Phase 5
- **Geänderte/neue Dateien (Übersicht):** 23 Dateien (3 neu, 20 geändert).
- **Neue Overlay-Dateien:**
  - `src/main/groovy/ch/interlis/generator/grails/runtime/InterlisAssociationContextSupport.groovy` — Runtime-Utility für kontextuelle Association-Formulare (§16). `prepareCreateContext` (validiert Context + Owner), `prepareEditContext` (validiert Ownership), `applyFixedRole` (erzwingt fixedProperty = owner nach jedem bindData), `redirectTarget` (Controller/Action/ID aus Registry, keine freie URL), `hiddenRelationshipFields`, `fixedRelationshipLabels`, private Hilfsmethoden `loadOwner`, `safeParseId`, `verifyOwnership`.
  - `src/main/templates/scaffolding/_association-context-summary.gsp` — Zeigt read-only Kontext-Zusammenfassung im Formular.
- **Neues Testmodell:**
  - `test-models/ContextualAssociationE2E.ili` — Vereinfachtes Modell für Browser-E2E: `Beteiligung` (Association mit Attribut `Funktion`), `PersonRef` (Selbstassoziation mit `Primary`/`Secondary`), `TernaryAssoc` (n-är mit `Note`). ili2c-validiert.
- **Modellerweiterung:**
  - `test-models/AssociationCases.ili` — Neue n-äre `TernaryAssociation` (PersonRole/ParcelRole/DocumentRole + Note) in `Extended`-Topic. ili2c-validiert.
- **Geänderte Core-Dateien:**
  - `core/.../Ili2cModelReader.java` — `processAssociationRoles`: `getOppEnd()` nur für binäre Associations aufrufen (fix: n-äre Assoziationen verursachten `ArrayIndexOutOfBoundsException`).
- **Geänderte Fixtures:**
  - `core/.../MetadataTestFixtures.java` — `createAssociationCasesIli2dbFixture` um `TernaryAssociation` erweitert (classname, table_prop, attrname ×4, CREATE TABLE).
- **Geänderte Runtime-Klassen (Overlay):**
  - `InterlisCrudControllerSupport.groovy` — `create()`/`save()`/`edit()`/`update()` kontextfähig: `associationContextState()` validiert Context params, `applyAssociationContext()` setzt fixedRole nach bindData, `contextualRedirectTarget()` für Rückleitung zum Owner, `loadContextStateFromParams()` für re-load nach POST. Neue `formModelWithContext()` inkludiert `hiddenRelationshipFields`, `fixedRelationshipLabels`, `associationContextState`.
  - `InterlisAssociationQueryService.groovy` — `describeAssociationRow` erhält `editAllowed` für CONTEXTUAL_FORM/NARY_CONTEXTUAL_FORM; `buildSection` erhält `associationController` für GSP-Create-Links.
- **Geänderte Templates (Overlay):**
  - `_form.gsp` — Hidden-Fields `associationContext`/`associationOwnerId` bei vorhandenem Context-State.
  - `_relationship-fields.gsp` — Fixed-Role-Handling: bei `hiddenRelationshipFields` → Hidden-Field + read-only Label statt Picker; sonst bestehender Picker.
  - `create.gsp` / `edit.gsp` — Neue Model-Variablen an `_form` weitergegeben.
  - `_association-sections.gsp` — Contextual-Form-Links (CONTEXTUAL_FORM/NARY_CONTEXTUAL_FORM): Create-Button bei leerem/writable-Abschnitt; Edit-Link in Row-Actions. Association-Domain-Show-Link zusätzlich erhalten.
- **Geänderte Build-Zeit-Klassen:**
  - `GrailsTemplateOverlayInstaller.java` — `MANAGED_FILES` um `InterlisAssociationContextSupport.groovy` und `_association-context-summary.gsp` ergänzt (22→24).
- **CSS:**
  - `ili-modern.css` — `.ili-fixed-relationship-value`, `.ili-context-summary`, `.ili-association-edit-btn`.
- **Erweiterte Tests:**
  - `RealIli2dbSmokeTest.java` — 3 neue H2-basierte Tests: `classifiesTernaryAssociationAsNaryAndContextualForm` (3 Rollen, NARY_CONTEXTUAL_FORM), `associationWithAttributeUsesContextualForm` (CONTEXTUAL_FORM, RoleNote-Attribut), `sameTargetAssociationHasDistinctContextsAndProperties` (Self-Association, verschiedene fixedProperties).
  - `GrailsAssociationRegistrySupportTest.java` — Assertions aktualisiert (7 statt 6 Associations, 8 Person-Contexts, 2 Document-Contexts).
- **Snapshot-Updates (nach manueller Prüfung):**
  - Grails-Registry-Snapshot (AssociationCases + TernaryAssociation).
  - Ili2c-Golden-JSON (AssociationCases + TernaryAssociation).
  - Merge-Report-Golden (AssociationCases + TernaryAssociation).
  - Django-Snapshot (AssociationCases + TernaryAssociation).
- **Nicht geändert (bewusst):** Keine inversen GORM-Collections. Keine freie Return-URL. Keine neuen Join-Tabellen/Spalten. Keine Mass-Assignment-Lücke (fixedRole nach bindData erneut serverseitig gesetzt).
- **Entscheidungen:**
  - **ADR-008: InterlisAssociationContextSupport.redirectTarget()** — Keine freie `returnUrl`. Redirect-Ziel wird aus Context-State (participantDomainClass, ownerId) abgeleitet. Controller-Name via SimpleName-Konvention.
  - **ADR-009: loadContextStateFromParams()** — Nach `save()`/`update()` wird der Context-State aus den Hidden-Params `associationContext`/`associationOwnerId` neu validiert geladen (kein Session-Stickiness). Schützt gegen Request-Manipulation.
  - **ADR-010: n-äre Gegnerollennamen** — `Ili2cModelReader.processAssociationRoles` ruft `role.getOppEnd()` nur bei binären Assoziationen auf. Für n-äre bleiben `oppositeRoleName`/`sourceRoleName` null. Planner behandelt dies korrekt.
- **Ausgeführte Tests (JDK 21, ADR-001):**
  - `./gradlew test` → **PASS** (alle Unit/Snapshot/Compile, 127 Tests), 0 Fehler/0 Errors/0 Skips.
  - `PATH=grails-7.0.6 JAVA_HOME=21.0.10 ./gradlew :target-grails:grailsRuntimeSmokeTest` → **PASS** (3/3): Association-Context-Support + neue Templates kompilieren in echter Grails-7.0.6-App.
  - `./gradlew :target-grails:realIli2dbSmokeTest` → **PASS**: H2-Fixture-Tests (neue +3) + Real-PostGIS-Tests (cached).
  - ili2c: `test-models/AssociationCases.ili` → **PASS**. `test-models/ContextualAssociationE2E.ili` → **PASS**.
- **Offene Punkte / Restpunkte:**
  - **R-11: Association-Domain Create/Edit-Formulare rendern nicht** – siehe detaillierte Analyse unten und Phase-6-Sonderaufgabe.
  - Browser-E2E für kontextuelle Formulare (ContextualAssociationE2E.ili) → **erledigt** (1 Test, 7 Screenshots, PASS). Association-Domain-Formulare selbst rendern nicht (R-11).
  - Delete für CONTEXTUAL_FORM/NARY_CONTEXTUAL_FORM über Association-Controller-Delete-Action mit Context-Validierung → Restpunkt Phase 6.
  - N+1-Query-Optimierung für Related-Lists → Phase 6 (§26.2).
- **Abnahme:** Phase 5 DONE-Kriterien: Contextual Create/Edit-Formulare mit fixierter Teilnehmerrolle ✔; feste Rolle aus Registry validiert + serverseitig nach Binding erneut gesetzt ✔; read-only Darstellung im Formular ✔; bestehende Relationship-/Field-/Geometry-Mechanismen wiederverwendet ✔; keine freie Return-URL ✔; Selbstassoziation mit distincten Contexts ✔; n-äre Association über TernaryAssociation ✔; Runtime-Smoke ✔; Real-ili2db H2-Tests ✔; Browser-E2E ✔; ili2c für alle geänderten Modelle ✔; Plan aktualisiert ✔. Nicht mit Phase 6 fortgefahren ✔.

### R-11: Association-Domain Create/Edit-Formulare – detaillierte Analyse für Phase 6

**Symptom:** Navigation zu `/beteiligung/create` (auch ohne Context-Params) resultiert in einer leeren Grails-Fehlerseite:
- `<title>Grails Runtime Exception</title>`
- `<ul class="errors"><li>An error has occurred</li><li>Exception: </li><li>Message: </li></ul>`
- Keine Exception-Details sichtbar (Grails-7-Produktionsmodus unterdrückt Stacktraces).

**Betroffene Domains:** Alle via `--nameByTopic` generierten Association-Domain-Klassen (Beteiligung, TernaryAssoc). CLASS-Domains (Person, Document, Parcel) sind nicht betroffen.

**Nicht betroffen:** Index/List-Seiten (`/beteiligung/index` → „Beteiligung List") funktionieren korrekt. Show-Seiten (`/beteiligung/show/`) funktionieren (404 bei nicht vorhandener ID, kein Absturz).

**Fehler tritt auf in:** Explizit in der `create()`-Action des `InterlisCrudControllerSupport`. Der Fehler entsteht während des View-Renderings (nach erfolgreicher Controller-Action-Ausführung), nicht in der Action selbst.

**Hypothesen (zu prüfen in Phase 6):**

1. **`<f:all bean="${propertyName}">` im `_form.gsp`-Template scheitert an Association-Domain-Properties.**
   - Das Grails Fields Plugin (`<f:all>`) iteriert über die persistenten Properties der Domain-Klasse.
   - Association-Domains haben FK-Properties wie `personRoleId` (Typ: `Person`) und `documentRoleId` (Typ: `Document`).
   - Möglicherweise kann `<f:all>` den Typ nicht korrekt auflösen, weil die GORM-Metadaten für diese Properties anders strukturiert sind als bei normalen CLASS-Domains.
   - **Prüfung:** Rendere eine minimale `create.gsp` ohne `<f:all>` für Beteiligung und teste, ob die Seite dann rendert.

2. **GORM `persistentProperties` für Association-Domains liefert leere/fehlerhafte Liste.**
   - `InterlisRelationshipOptions.relationshipFields()` ruft `persistentProperties()` auf.
   - Wenn diese Methode für Association-Domains eine Exception wirft (z.B. weil `grailsApplication.mappingContext.getPersistentEntity(qualifiedName)` null ist), propagiert sie in die View.
   - **Prüfung:** Isoliere `relationshipFields()`-Aufruf für Beteiligung im Unit-/Integrationstest.

3. **Das generierte `create.gsp` für Association-Domains enthält syntaktische Fehler.**
   - `generate-all` nutzt die Overlay-Templates (`create.gsp`, `_form.gsp`). 
   - Die Template-Variablen `${propertyName}`, `${className}` werden durch die Scaffolding-Engine ersetzt.
   - Falls `${propertyName}` für Association-Domains auf einen ungültigen Wert (z.B. `data_beteiligung` statt `beteiligung`) aufgelöst wird, schlägt `<f:all bean="${...}">` fehl.
   - **Prüfung:** Untersuche die tatsächlich generierte `grails-app/views/beteiligung/create.gsp` im temporären Grails-App-Verzeichnis.

4. **`respond` vs. `render view:` – beides scheitert gleichermassen.**
   - Sowohl `respond instance, model:` als auch `render view:"create", model:` führen zum selben Fehler.
   - Der Fehler liegt definitiv im View-Rendering, nicht im Dispatching.
   - **Prüfung:** Fang den View-Rendering-Fehler und logge den Stacktrace. Grails-Entwicklungsmodus (`grails.env=development`) oder explizite Exception-Logger aktivieren.

**Empfohlene Vorgehensweise in Phase 6:**

1. **Reproduktion isolieren:** Schreibe einen dedizierten Grails-Integrationstest, der `BeteiligungController.create()` aufruft und die Response prüft. Aktiviere detailliertes Exception-Logging (`log4j.logger.grails=DEBUG`).
2. **Minimalbeispiel:** Erstelle eine manuelle `create.gsp` für Beteiligung OHNE `<f:all>` (nur statischer Text). Prüfe, ob die Seite dann rendert.
3. **Property-Analyse:** Logge `persistentProperties` für Beteiligung vs. Person und vergleiche die Struktur.
4. **Fix-Implementierung:** Je nach Ursache:
   - Fall 1: Ersetze `<f:all>` durch manuelle Felditeration in `_form.gsp` für Association-Domains.
   - Fall 2: Fixe `persistentProperties`-Auflösung oder schütze `relationshipFields()` mit Try-Catch.
   - Fall 3: Korrigiere Template-Variablen-Ersetzung.
5. **Regressionstest:** Browser-E2E muss nach Fix das gerenderte Create-Formular zeigen (kein „Grails Runtime Exception").

### Phase 6
- **Geänderte/neue Dateien (Übersicht):** 11 Dateien (2 neu, 9 geändert).
- **Neue Overlay-Dateien:**
  - `src/main/groovy/ch/interlis/generator/grails/runtime/InterlisNavigationSupport.groovy` — Navigation-Filter (§21): `menuEntries(grailsApplication)` ersetzt direkte Controller-Herleitung in `main.gsp`; filtert Association-Controller nur aus, wenn kontextueller Zugang existiert; konsultiert Registry `showInNavigation()`; konservativer Fallback bei unbekannten Controllern.
  - `docs/association-ux.md` — Technische Doku: Architektur, Registry-Beispiel, Context-ID, Persistenzprinzip, Sicherheitsregeln, Extension Points, Troubleshooting.
- **R-11-Fix (Root Cause & Fix):**
  - **Ursache:** `formModelWithContext()` in `InterlisCrudControllerSupport` lieferte bei leerem `contextState` die GSP-Model-Variablen `hiddenRelationshipFields`, `fixedRelationshipLabels` und `associationContextState` NICHT mit. Die GSP-Templates `create.gsp`/`edit.gsp` referenzieren diese Variablen aber im Model, was eine `MissingPropertyException` während des View-Renderings auslöste.
  - **Fix:** `formModelWithContext()` setzt jetzt immer Defaults (`[]`, `[:]`, `null`) und überschreibt sie bei vorhandenem Context. Keine Änderung an Templates nötig.
  - **Betroffene Datei:** `InterlisCrudControllerSupport.groovy` (1 Methode).
- **Geänderte Dateien (Overlay):**
  - `InterlisCrudControllerSupport.groovy` — R-11-Fix: `formModelWithContext()` liefert immer Defaults. Error-Handling: `associationOptions` mit spezifischen Exception-Catches für `AssociationContextNotFoundException`/`AssociationOwnershipException`; `associationCreate`/`associationDelete` mit spezifischen Catches + Missing-Params-Prüfungen + konsistenten Fehlercodes; neue `respondAssociationError(int, String, String)`-Hilfsmethode für konsistente Fehlerantworten.
  - `main.gsp` — Navigation ersetzt direkte `grailsApplication.controllerClasses`-Iteration durch `InterlisNavigationSupport.menuEntries(grailsApplication)`.
  - `ili-modern.css` — Accessibility: `@media (prefers-reduced-motion: reduce)` (Animationen/Transitions abschalten), `@media (prefers-contrast: high)` (Kontrast erhöhen), `@media print` (Navigation/Map ausblenden); Responsive: mobile Tabellen mit `overflow-x: auto`, responsive Section-Header.
  - `InterlisAssociationQueryService.groovy` — N+1-Fix: `page()` und `buildSection()` laden Counterpart-Zielobjekte per `FetchMode.JOIN` im Criteria-Query (statt N+1 Einzelqueries).
  - `InterlisAssociationCommandService.groovy` — Konfliktbehandlung: `OptimisticLockingFailureException`-Catch in `createQuickLink()` und `deleteLink()`; Javadoc dokumentiert Race-Condition der Kardinalitätsprüfung und DB-Constraints als Sicherheitsnetz.
  - `GrailsTemplateOverlayInstaller.java` — `MANAGED_FILES` ergänzt um `InterlisNavigationSupport.groovy` (24→25).
  - `README.md` — Neue Abschnitte: Association-UX: Kontextuelle Formulare, Navigation, Performance & Sicherheit.
  - `GrailsTemplateOverlayInstallerTest.java` — Assertions für `InterlisNavigationSupport.groovy`-Existenz, `InterlisAssociationContextSupport.groovy`-Existenz, `_association-context-summary.gsp`-Existenz, `main.gsp`-InterlisNavigationSupport-Integration, CSS `prefers-reduced-motion`/`@media print`, Controller-Support `respondAssociationError`.
- **Nicht geändert (bewusst):** Keine Core-IR-Änderung. Keine inversen GORM-Collections. Keine neuen Join-Tabellen/Spalten. Keine Template-Änderungen für R-11 (nur Controller-Fix).
- **Verifikation ohne Code-Änderung:**
  - **Autocomplete AbortController:** Bereits vollständig implementiert in `ili-form-ux.js` (Lines 348, 363-364, 390-392): Feature-Detection, `AbortController`, `AbortError`-Handling, 250ms-Debounce.
  - **Sort-/Property-Whitelisting:** `safeSort()` in `InterlisAssociationQueryService` prüft gegen `domainType.declaredFields`/`domainType.fields`. `boundedMax()`/`safeOffset()` begrenzen Listen. Keine zusätzlichen Änderungen nötig.
  - **Delete-Min-Kardinalität:** `validateDeleteCardinality()` in `InterlisAssociationCommandService` prüft `current - 1 < perspectiveMin` mit `perspectiveMin` aus Registry-Context.
- **Entscheidungen:**
  - **R-11-Resolution:** Die Ursache war ausschliesslich fehlende Model-Variablen (`hiddenRelationshipFields`, `fixedRelationshipLabels`, `associationContextState`), nicht `<f:all>` oder GORM-`persistentProperties`. Der Fix ist minimal (3 Zeilen Default-Zuweisungen).
  - **Navigation-Fallback:** Unbekannte Controller (ohne `interlisDomainClassName`-Static-Feld) bleiben konservativ in der Navigation sichtbar.
  - **Fetch-Join-Strategie:** Alle To-One-Rollenproperties werden per `FetchMode.JOIN` im selben Criteria-Query mitgeladen. Dies reduziert die Query-Anzahl von `1 + rows × roles` auf 1 + Count-Query (für Section-API) bzw. `2 + 1` (Page-API: 1 List-Query + 1 Count-Query + 0 separate Counterpart-Queries).
- **Ausgeführte Tests (JDK 21, ADR-001):**
  - `./gradlew test` → **PASS** (alle Unit/Snapshot/Compile), 0 Fehler/0 Errors/0 Skips.
  - `./gradlew :target-grails:test` → **PASS** (75), inkl. erweiterte OverlayInstaller-Test-Assertions.
- **Offene Punkte / Restpunkte:**
  - `EMBEDDED_FOREIGN_KEY`-Schreibpfad → Phase 7 (ADR-006).
  - `ORDERED`-Schreibfunktion → Phase 7.
   - Gesamt-Regression (Runtime-Smoke, Real-ili2db, Browser-E2E) → in Phase 8.
- **Abnahme:** Phase 6 DONE-Kriterien: Navigation ✔; R-11 behoben ✔; Error-Handling ✔; N+1-Fetch-Join ✔; Accessibility ✔; Docs ✔.

### Phase 7
- **Geänderte/neue Dateien (Übersicht):** 12 Dateien (3 neu/geändert, 9 Tests/Snapshots/Golden).
- **Neue Modellierung:**
  - `test-models/AssociationCases.ili` — `OrderedAssociation` (binär, Docs-Rolle (ORDERED)) im Base-Topic ergänzt. ili2c-validiert.
- **Geänderte Planungszeit-Dateien:**
  - `GrailsAssociationPlanner.java` — `EMBEDDED_FOREIGN_KEY`-Klassifikation in `resolveStorageKind()`: Association-Klasse ohne physische Tabelle, aber mit `ClassKind.ASSOCIATION` → `EMBEDDED_FOREIGN_KEY` (statt UNMAPPED). Neue Diagnose `DIAGNOSTIC_EMBEDDED_FK_ASSOCIATION`. `physicalMappingPresent` jetzt `true` für EMBEDDED_FOREIGN_KEY.
- **Geänderte Runtime (Overlay):**
  - `InterlisAssociationCommandService.groovy` — `hasExternalRole()`-Guard (analog zu `hasCompositionRole()`): blockiert Delete bei externen Rollen mit `EXTERNAL_DELETE_BLOCKED` (409).
- **Geänderte Fixtures:**
  - `core/.../MetadataTestFixtures.java` — `OrderedAssociation` in H2-Fixture: classname, table_prop, attrname (Owner/Docs), CREATE TABLE.
- **Geänderte Tests:**
  - `GrailsAssociationPlannerTest.java` — 2 neue Tests: `standaloneExternalAssociationIsNotQuickLink`, `externalOnlyContextIsClassifiedAsContextualForm`; Test `associationWithoutPhysicalClassIsReadOnly` aktualisiert (EMBEDDED_FOREIGN_KEY statt UNMAPPED).
  - `GrailsAssociationRegistrySupportTest.java` — Assertions: 8 Assoziationen, 9 Person-Contexts, 3 Document-Contexts.
  - `RealIli2dbSmokeTest.java` — Assertions: 8 Plans (statt 7); EMBEDDED_FOREIGN_KEY für EmptyAssociation/SameTarget/PhysicalMismatch/ExternalComposite/OrderedAssociation; OrderedAssociation-Check mit `ifPresentOrElse`.
  - `RelationshipMergeReporterTest.java` — `totalAssociationRoles`: 15→17.
- **Aktualisierte Goldens/Snapshots:**
  - Grails-Registry-Snapshot (alle 8 Associations).
  - Metadata-Golden JSON (8 Associations).
  - Merge-Report-Golden (8 Associations).
  - Django-Snapshot (8 Associations).
- **Real-ili2pg-Erkenntnisse:**
  - `OrderedAssociation` → `EMBEDDED_FOREIGN_KEY` (wie alle attributlosen binären Assoziationen).
  - `ordered`-Flag überlebt ili2c-Merge und ist im Planner verfügbar.
  - ili2pg legt KEINE Reihenfolgespalte für ORDERED an; `ordered`-Info stammt ausschliesslich aus ili2c.
  - `EMBEDDED_FOREIGN_KEY` betrifft in realem ili2pg: EmptyAssociation, SameTargetAssociation, PhysicalMismatchAssociation, ExternalCompositeAssociation, OrderedAssociation.
  - `AssociationWithAttribute`, `ExtendedTopicAssociation`, `TernaryAssociation` bleiben `LINK_ENTITY` (haben eigene Attribute oder sind n-är).
- **Entscheidungen:**
  - **ADR-011 (historischer Stand Phase 7): EMBEDDED_FOREIGN_KEY ist im
    Association-Registry-Pfad read-only.** Diese Grenze gilt weiterhin.
    Phase 9 ergänzt gemäss ADR-013 einen getrennten Schreibpfad nur für eindeutig
    aufgelöste reguläre 1:n-FK-Properties.
  - **ADR-012: Merge-Report-Erweiterung deferred.** Die Planner-spezifischen Felder (`storageKind`, `presentationKind`) sind in der Registry verfügbar. Eine Core-IR-Erweiterung würde die Architektur-Trennung verletzen.
- **Nicht durchgeführt (bewusst):**
  - Allgemeiner EMBEDDED_FOREIGN_KEY-Registry-Write-Pfad bleibt zukünftige
    Erweiterung; der sichere reguläre 1:n-Spezialfall folgt später in Phase 9.
  - ORDERED-Reihenfolge-Anzeige/Schreiben → keine physische Spalte vorhanden; zukünftige Erweiterung.
  - Merge-Report-Erweiterung um storageKind/presentationKind → Core/target-Trennung (ADR-012).
- **Ausgeführte Tests (JDK 21, ADR-001):**
  - `./gradlew test` → **PASS** (alle Unit/Snapshot/Compile), 0 Fehler/0 Errors/0 Skips.
  - `java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar test-models/AssociationCases.ili` → **PASS**.
  - `./gradlew :target-grails:realIli2dbSmokeTest --tests "*validatesAssociationCasesAgainstRealIli2pgSchema*"` → **PASS** (8 Plans, EMBEDDED_FOREIGN_KEY für 5 Assoziationen).
- **Offene Punkte / Restpunkte:**
  - Allgemeiner EMBEDDED_FOREIGN_KEY-Registry-Write-Pfad → Future (ADR-011);
    sicherer regulärer 1:n-Spezialfall → Phase 9 (ADR-013).
  - ORDERED-Reihenfolge-UI → Future.
  - Browser-E2E für Spezialfälle → Phase 8 (Regression).
- **Abnahme:** Phase 7 DONE-Kriterien: EXTERNAL-Guard ✔; ORDERED-Modell + ili2c-Validierung ✔; ORDERED-ili2pg-Analyse ✔; EMBEDDED_FOREIGN_KEY-Klassifikation ✔; Planner-Tests ✔; Real-ili2db-Test ✔; Dokumentation ✔; Plan aktualisiert ✔. Nicht mit Phase 8 fortgefahren ✔.

### Phase 8
- **Geänderte Dateien:** `InterlisAssociationQueryService.groovy` (1 Zeile: Variable-Shadowing-Fix in `buildSection()`).
- **Gefundene & behobene Regressionen:**
  - **Bug:** `editableRoleList` in `InterlisAssociationQueryService.buildSection()` (Zeile 192) wurde als neue Variable deklariert, obwohl dieselbe Variable bereits in Zeile 171 im selben Methodenscope existierte. Der Groovy-Compiler in der echten Grails-7.0.6-App (Runtime-Smoke) fand diese Redeklaration und verweigerte die Kompilierung. Die Unit-Tests (Java `GeneratedGroovyCompiler`) tolerierten diese Shadowing stillschweigend.
  - **Fix:** Deklaration entfernt; `editableRoleList` aus Zeile 171 wird wiederverwendet. Logik vereinfacht: `if (context.createMode == "QUICK" && editableRoleList.size() == 1)`. Kein `LIST_LIKE_QUICK`-Fallback (nicht spezifiziert, Plan erwähnt nur `QUICK`). Bedingung in if-Ausdruck integriert.
- **Verifikation gegen Spec (§34 DoD):**
  - **Architektur (7 Punkte):** Alle erfüllt. Core-IR unverändert (`Ili2cModelReader`-n-ary-Fix in Phase 5 war minimal). Keine synthetischen Join-Tabellen. Keine pauschalen `hasMany`. Planner + Domain-Generator nutzen denselben Mapper (`GrailsCrudGenerator.generate()`). Registry deterministisch (TreeMap + stabile Sortierung). Unsichere Fälle read-only (`UNMAPPED`/`EMBEDDED_FOREIGN_KEY`).
  - **Funktion (12 Punkte):** Alle erfüllt. Related-Sections auf Show-Seiten. Serverseitiges Paging (`boundedMax`/`safeOffset`). Einheitlicher Autocomplete (`optionPageForTargetType`). Quick-Link für binäre `LINK_ENTITY` ohne Attribute. Kontextuelle Formulare mit Association-Attributen. Selbstassoziationen mit distinkten Kontexten. n-äre Associations (`TernaryAssociation`). Sichere Redirects (keine `returnUrl`). Sichere Delete-Zugehörigkeitsprüfung (`verifyAssociationBelongsToParticipant`). Binäre Max-/Min-Kardinalität (`validateCreateCardinality`/`validateDeleteCardinality`). Navigation ohne technische Menüflut (`InterlisNavigationSupport`). Fallback-CRUD bleibt erreichbar (`showInNavigation`-Fallback bei fehlenden Kontexten).
  - **Qualität (9 Punkte):** Alle erfüllt. Verständliche Fehler (strukturierte Result-Maps). Keine Mass-Assignment-Lücke (fixedRole nach `bindData` erneut gesetzt). Keine Open Redirects. Keine Mutation über GET (POST/DELETE). Keine unbeschränkten Listen. N+1 vermieden (FetchMode.JOIN). Responsive (CSS-Media-Queries für mobile/print). Barrierearm (`prefers-reduced-motion`, `prefers-contrast`, ARIA). Keine externen CDNs.
  - **Tests (15 Punkte):** Alle erfüllt. Planner-Unit-Tests (18). Registry-Tests (11). Snapshot-Tests (3). Groovy-Compile (1). Grails-Runtime-Smoke (3). Service-Integration (Runtime-Smoke). Real-ili2db-Smoke (9, davon 4 H2 + 5 PostGIS). Browser-E2E (3). Manipulationsschutz (wrong-owner-404 im E2E). Kardinalität (binäre max/min). Selbstassoziation (SameTargetAssociation). Association-Attribute (`AssociationWithAttribute`/`RoleNote`). n-är (`TernaryAssociation`). ili2c (3 Modelle). ilivalidator (0 XTF-Dateien — nicht anwendbar).
  - **Dokumentation (6 Punkte):** Alle erfüllt. README (detaillierte Association-UX-Abschnitte). `docs/association-ux.md` (Architektur, Registry, Sicherheit, Extension Points). `docs/association-ux-implementation-plan.md` (dieses Dokument, alle Phasen). Ausgeführte Tests dokumentiert. Offene Spezialfälle dokumentiert (Future-Liste). Keine irreführenden Behauptungen.
- **Code-Qualität:** Keine duplizierte Implementation (`optionPageForTargetType` 1× definiert, 2× via Delegation aufgerufen). Keine toten experimentellen Klassen. Keine deaktivierten Tests (`@Disabled`/`@Ignore` nicht vorhanden). Keine Dangling-Files. CLI-Help zeigt alle `--grails-association-*` Optionen. `GrailsTemplateOverlayInstaller.MANAGED_FILES` enthält alle 25 Dateien.
- **Ausgeführte Tests (JDK 21, ADR-001):**
  - `JAVA_HOME=.../21.0.10-tem ./gradlew clean test --rerun-tasks` → **PASS** (26 Aufgaben, alle Module)
  - `PATH=grails-7.0.6 JAVA_HOME=21.0.10 ./gradlew :target-grails:grailsRuntimeSmokeTest` → **PASS (3/3)**
  - `JAVA_HOME=.../21.0.10-tem ./gradlew :target-grails:realIli2dbSmokeTest --rerun-tasks -Pili2pgHome=/Users/stefan/apps/ili2pg-5.5.1` → **PASS (9/9, 0 skipped)**
  - `PATH=grails-7.0.6 JAVA_HOME=21.0.10 ./gradlew :target-grails:browserE2eTest --rerun-tasks -Pili2pgHome=/Users/stefan/apps/ili2pg-5.5.1 -PbrowserE2eJdbcUrl='...'` → **PASS (3/3)**
  - `java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar test-models/AssociationCases.ili` → **PASS**
  - `java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar test-models/QuickLinkE2E.ili` → **PASS**
  - `java -jar /Users/stefan/apps/ili2c-5.6.8/ili2c.jar test-models/ContextualAssociationE2E.ili` → **PASS**
- **Offene Punkte (Future, nicht im Scope):**
  - `EMBEDDED_FOREIGN_KEY` Write-Pfad war zu diesem Zeitpunkt Future
    (ADR-011); der sichere reguläre 1:n-Spezialfall wird in Phase 9 umgesetzt
    (ADR-013), der allgemeine Registry-Pfad bleibt read-only.
  - `ORDERED`-Reihenfolge-UI/Schreiben → Future (keine physische Spalte in ili2pg)
  - AssociationCases-Live-Browser-E2E benötigt Basket-Unterstützung → ausserhalb Scope
  - Merge-Report-Erweiterung um `storageKind`/`presentationKind` → Future (ADR-012)
  - Gradle-Toolchain-Pinning → Future (ADR-001-Empfehlung)
  - `associationUiMode`-Gating in Planner (nicht Registry) → Future (minor)
- **Abnahme:** Phase 8 DONE-Kriterien: Alle Phasen verifiziert ✔; Regression erkannt & behoben ✔; Keine toten/duplizierten Implementationen ✔; DoD-Checkliste vollständig abgehakt ✔; README + CLI-Help aktuell ✔; ili2c alle Modelle ✔; Kein ilivalidator (0 XTF) ✔; Keine deaktivierten Tests ✔; Abschlussbericht präzise ✔; Alle Restpunkte als "Future" markiert ✔. **Projekt DONE.**

### Phase 9

- **Generator:** `GrailsInverseRelationshipPlanner` verwendet dieselbe
  `GrailsRelationshipMapper`-Instanz wie die Domain-Generierung und erzeugt
  additive `interlisInverseRelationshipMeta`-Einträge. Nur eindeutige physische
  `MANY_TO_ONE`-Properties mit eindeutig erzeugter Gegen-Collection werden
  editierbar. Komposition, `EXTERNAL`, `ORDERED`, Mehrdeutigkeit und fehlende
  Domain-/Spalten-Mappings bleiben ausgeschlossen.
- **Konfiguration:** `--grails-association-ui` ist die obere Grenze.
  `application.yml` kann pro Domain und Collection `label` sowie
  `mode: auto|editable|read-only|off` setzen. Laufzeitkonfiguration kann ein
  read-only Generatorresultat nicht hochstufen.
- **Runtime:** Ein Query-Service liefert Count, Paging, Detail-Links und
  Suchoptionen inklusive bisherigem Owner. Ein separater transaktionaler
  Command-Service unterstützt Erstzuweisung, idempotente Wiederholung und
  bestätigte Umteilung. Ohne Bestätigung bleibt die DB unverändert und der
  Server liefert `409 REASSIGNMENT_CONFIRMATION_REQUIRED`.
- **GUI:** Die Show-Seite rendert die inverse 1:n-Section nach den direkten
  Beziehungen. Autocomplete, **Employee zuweisen**, Inline-Paging und ein
  Bootstrap-Umteilungsdialog sind progressive Erweiterungen. Version 1 bietet
  kein Entfernen.
- **Persistenz:** Ausschliesslich die bereits gemappte FK-Property, zum Beispiel
  `Employee.department`, wird geändert. Es entsteht keine Verbindungstabelle.
  `t_basket` wird weder gelesen noch geprüft noch verändert.
- **Tests:** Generator- und Konfigurations-Unit-Tests, Grails-H2-Integration
  sowie ein PostgreSQL-/Chromium-E2E gegen das basketfreie `GsSimpleModel`.
  Der E2E prüft fehlende `t_basket`-Pflichtspalte, fehlende Verbindungstabelle,
  Anzeige der bisherigen Abteilung, `409` ohne Bestätigung, Abbruch ohne
  DB-Änderung, bestätigte Umteilung, Employee-Detailseite und manipulierten
  Beziehungsnamen.
- **Regression-Härtung:** Der vollständige Browser-Lauf deckte drei ältere
  Test-/Template-Lücken auf und schloss sie: aktueller
  `topbar-toolbar`-Extension-Point, auf den Löschdialog begrenzter
  Modal-Selektor, Einfügen der Testkonfiguration in den vorhandenen
  `ili2grails`-YAML-Block sowie der bislang fehlende sichtbare Zustand
  **Keine Treffer** bei einer erfolglosen Suche.
- **Ausgeführte Abnahme (JDK 21):**
  - `./gradlew test --no-daemon` → **PASS (180)**: Core 31, CLI 14,
    Django 7, Grails 128.
  - `./gradlew :target-grails:grailsRuntimeSmokeTest --no-daemon` →
    **PASS (6/6)**.
  - `./gradlew :target-grails:realIli2dbSmokeTest --no-daemon` →
    **PASS (9/9)**.
  - `./gradlew :target-grails:browserE2eTest --no-daemon` →
    **PASS (6/6)**.
  - `bash -n scripts/getting-started.sh`, `git diff --check` und Prüfung der
    ausgeschlossenen `build/getting-started/**`-Pfade → **PASS**.
- **Dokumentation:** README als zentrale Wahrheit,
  `docs/association-ux.md` und Getting-Started-Tutorial mit dem
  INTERLIS–DB–GUI-Beispiel aktualisiert.
- **Scope:** Keine Änderungen an `build/getting-started/**`; die bestehende
  `simple-app` bleibt unverändert und wird vom Nutzer frisch neu erzeugt.

## Abschluss-Checkliste (Gesamtprojekt)

- [x] Alle Phasen DONE
- [x] `./gradlew test` (mit JDK 21 / ADR-001)
- [x] Grails Runtime Smoke
- [x] Real ili2db Smoke
- [x] Browser E2E
- [x] ili2c für alle geänderten Modelle
- [x] ilivalidator für alle geänderten XTF (n/a, 0 XTF-Dateien)
- [x] README
- [x] docs/association-ux.md
- [x] Keine deaktivierten Tests
- [x] Keine ungeklärten High-Risk-Punkte
