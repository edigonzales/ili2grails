# P2 – Transparente Verifikation und sichere Weiterentwicklung

Dieses Dokument begleitet die vollständige Umsetzung von
`ili2grails-p2-focused-transparent-verification-spec.md` und wird während der
gesamten Arbeit aktuell gehalten.

## Ausgangscommit

```text
e75fb93240967876d42dba9ad1e095ea4ac13402
Merge branch 'refactor/p1-runtime-ir-architecture'
```

- `git switch main` → kein FF-Pull nötig (bereits aktuell, `Already up to date`).
- Working Tree sauber, einzige untracked Datei: die P2-Spezifikation selbst
  (`ili2grails-p2-focused-transparent-verification-spec.md`), sie wird nicht committed.
- Arbeitsbranch: `test/p2-transparent-verification`.

## Umgebung

| Komponente | Version | Bemerkung |
|---|---|---|
| Betriebssystem | macOS 26.5.2, aarch64 | |
| Shell-Java | Temurin 25.0.2 (sdkman `current`) | `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/current` |
| Java 17 (Toolchain-Ziel) | Temurin 17.0.18 | `~/.sdkman/candidates/java/17.0.18-tem` |
| Gradle | 8.14.3 | |
| Grails | 7.0.6 (sdkman) | `grails --version` ok |
| Docker | 29.6.2 | |
| Docker Compose | v5.3.1 | |
| ili2pg | 5.5.1 (lokal `/Users/stefan/apps/ili2pg-5.5.1`) | `ILI2PG_HOME` nicht gesetzt |
| ili2c | 5.6.8 (lokal `/Users/stefan/apps/ili2c-5.6.8`) | |
| Playwright | nicht installiert (kein `~/.cache/ms-playwright`) | Browser-E2E nicht lokal ausführbar |
| Git | 2.50.1 | |

## P0- und P1-Invarianten

Geprüft per Codereview und Baseline (siehe unten) am Ausgangscommit:

- P0: typisierte/quotierte SQL-Identifier (`reader/sql`), `ModelSelection`,
  `MetadataMerger`, `MetadataMergeDiagnostic`, Trennung GORM-Persistenz vs.
  inverse UI-Darstellung (`RelationshipDescriptor`/`InverseRelationshipDescriptor`),
  `GrailsPostgresContractTest` vorhanden.
- P1: Module `grails-runtime-api`/`grails-runtime`, immutable Core-IR
  (`ModelMetadataFactory` als Freeze-Gate), aufgeteilter ili2db-Reader
  (catalog/schema/assemble), dünne `Ili2dbMetadataReader`-Fassade,
  typisierte Runtime-Deskriptoren und Registries, Grails-Runtime als Plugin,
  Startup-`InterlisRuntimeRegistryValidator`, injizierbare Authorization-Policy
  und Lifecycle-Hooks, typisierte Command-Results, Flow-Klassen pro Controller.

Keine Invariante fehlt; keine Reparatur an P0/P1 nötig.

## Baseline-Tests

Befehl:

```bash
./gradlew clean test --rerun-tasks --no-daemon
```

### Befund 1: Baseline schlägt mit Shell-JDK 25 fehl

Der Testlauf mit dem Shell-JDK (Temurin 25.0.2) schlägt in `:target-grails:test`
fehl: `org.codehaus.groovy.GroovyBugError` (Caused by `IllegalArgumentException`)
in Groovy-Supportklassen (z.B. `InterlisUiDescriptorSupportTest`,
`InterlisWorkspaceSupportTest`, `LargeModelNamingTest`). 151 Tests, 48 failed.

Dies ist exakt das in der Spezifikation (Teil V §9.1) beschriebene Risiko:
„Tests dürfen nicht versehentlich mit dem Shell-JDK 25 gestartet werden."
Der Befund begründet die verbindliche Java-17-Toolchain.

### Befund 2: Baseline mit Java 17 ist grün

Mit `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/17.0.18-tem`:

| Modul | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| core | 180 | 0 | 0 | 0 |
| grails-runtime-api | 32 | 0 | 0 | 0 |
| grails-runtime | 19 | 0 | 0 | 0 |
| target-grails | 151 | 0 | 0 | 0 |
| target-django | 7 | 0 | 0 | 0 |
| cli | 14 | 0 | 0 | 0 |
| **Summe** | **403** | **0** | **0** | **0** |

Verification-Tasks (`./gradlew tasks --group verification`):
`grailsRuntimeSmokeTest`, `realIli2dbSmokeTest`, `browserE2eTest`,
`grailsPostgresContractTest` (alle opt-in, erfordern Grails/ili2pg/Docker).

## Bekannte Skips

- Browser-E2E (`browserE2eTest`): Playwright-Chromium lokal nicht installiert;
  Lauf nur über die Full-Verifikation auf Infrastruktur möglich. Wird
  dokumentiert, nicht behauptet.
- Real-ili2db-Smoke/Postgres-Contract: benötigen laufende Docker-Compose-DB
  und `ILI2PG_HOME`; lokaler Lauf wird im Verlauf der Arbeit ausgeführt.
- `VSADSSMINI_2020_LV95`-Szenario: externes Modell-Repository kann je nach
  Netz verfügbar sein; bei Nichterreichbarkeit Infrastruktur-Skip.

## Architekturentscheidungen

### P2-D001 – Java-17-Toolchain für alle Java-Module

Kontext: Baseline mit Shell-JDK 25 (Temurin) schlägt in Groovy-Supportklassen
mit `GroovyBugError` fehl; nur Java 17 ist grün.

Lösung: In `build.gradle` (Root) für alle `java`-Module eine
`java.toolchain { languageVersion = JavaLanguageVersion.of(17) }` setzen;
Gradle wählt automatisch ein JDK 17. `sourceCompatibility`/`targetCompatibility`
bleiben zusätzlich gesetzt. Für `grails-runtime` bleibt die Grails-Groovy-
Konfiguration erhalten (`compileJava.options.release = 17`, `indy=false`).

Verworfene Alternativen: lokale JDK-Pfade hardcoden (nicht portabel);
Gradle-Property-Default (weniger durchsetzungsstark).

Konsequenzen: Der Build ist unabhängig vom Shell-JDK reproduzierbar; ein
Gradle-Assertion-Akzeptanztest prüft `launcher language version == 17`
für alle Java-Testtasks.

Betroffene Klassen: Root-`build.gradle`, kein Java-Code.
Zugehörige Tests: neuer Build-Akzeptanztest (siehe Toolchain-Phase).

### P2-D002 – Nur zwei Verifikationsprofile

Kontext: erweiterte Tests sind opt-in und nicht reproduzierbar verdrahtet.

Lösung: Root-Tasks `verificationFast` (alle Unit-Test-Tasks +
Corpus-/Ownership-Verifikation, ohne externe Tools) und `verificationFull`
(fast + Smoke-/Contract-/E2E-Tasks im Required-Modus). Keine weiteren Profile.

Betroffene Dateien: Root-`build.gradle`, `target-grails/build.gradle`.

### P2-D003 – Lokales main.gsp ist immer application-owned

Das Plugin liefert sein Default-Layout aus dem Plugin-JAR. Eine Datei
`grails-app/views/layouts/main.gsp` im Zielprojekt ist ein bewusstes
Application-Override und wird nie aufgrund einer allgemeinen Ownership-Regel
überschrieben oder gelöscht. Nur ein per SHA-256 eindeutig erkanntes
pre-P1-Legacy-Exemplar darf über den Legacy-Migrationspfad entfernt werden.

(Weitere Entscheidungen folgen während der Umsetzung.)

## Reader-Diagnostics

(Noch offen – Phase 2.)

## Runtime-Descriptor-Diagnostics

(Noch offen – Phase 3.)

## Runtime-Safety

(Noch offen – Phase 3.)

## Modellkorpus

(Noch offen – Phase 4.)

## INTERLIS-Feature-Matrix

(Noch offen – Phase 4.)

## Mapping-Consistency

(Noch offen – Phase 5.)

## Generation Plan und Manifest

(Noch offen – Phase 6.)

## Dry-run und Regeneration

(Noch offen – Phase 6.)

## Reader-Query-Budgets

(Noch offen – Phase 7.)

## Verifikationsprofile

(Noch offen – Phase 1.)

## CI

(Noch offen – Phase 9.)

## Ausgeführte Befehle

- `git switch main && git pull --ff-only && git status --short && git log -1 --oneline`
- `java -version; ./gradlew --version; git --version; grails --version; docker --version; docker compose version`
- `./gradlew clean test --rerun-tasks --no-daemon` (Shell-JDK 25 → FAILED, 48 Failures in `:target-grails:test`, GroovyBugError)
- `JAVA_HOME=...17.0.18-tem ./gradlew clean test --rerun-tasks --no-daemon` → BUILD SUCCESSFUL, 403 Tests grün
- `./gradlew tasks --group verification`

## Reports

(Noch offen.)

## Abweichungen von der Spezifikation

- (Noch keine.)

## Verbleibende Risiken

- Browser-E2E ist lokal mangels Playwright-Browser nicht ausführbar (Infrastruktur-Skip; geplant für Full-Lauf auf geeigneter Maschine).
- Die Groovy-BugError-Failures auf JDK 25 zeigen, dass alle Gradle-Testtasks
  zwingend über die Toolchain laufen müssen.

## Commit-Liste

- `390cc8e` docs: record P2 transparent verification baseline
- `6a7c44d` build: pin Java 17 toolchain and add verification profiles
- `d60a6d7` test: centralize external verification environment detection
- `7ab8db7` refactor(core): align ili2db request context and diagnostic semantics
- `4ddd44d` refactor(grails): emit blocking runtime descriptor diagnostics
- `45e43a4` fix(runtime): enforce read-only safety state for invalid descriptors
- `4779f9d` test: add versioned INTERLIS model corpus and feature matrix
- `891b5bc` test: compare expected GORM mapping with Hibernate and PostgreSQL
- `3658897` refactor(grails): plan all generated project changes before writing
- `a45837f` test: add regeneration manifest and user-change protection contracts
- `6ed0904` test: enforce ili2db query budgets
- `26077a5` build: add focused JaCoCo and generation boundary guards
- `331d3ce` ci: run verificationFast on pushes and pull requests
- `d261dce`/`32820b5` docs: P2 verification documentation
- `e4eedaa` fix: render Grails 7 dev-mode UI views, assets and shell layout app-local

## Entscheidungen (fortgeführt)

### P2-D005 – Reader-Diagnostic-Codes ohne Duplikat
`DUPLICATE_PHYSICAL_CLASS`/`DUPLICATE_PHYSICAL_COLUMN` wurden aus
`Ili2dbDiagnosticCode` entfernt: die einzige Wahrheit für physische Duplikate
ist `ModelMetadataDiagnosticCode` im Modell-Validator.

### P2-D006 – RuntimeDescriptorSeverity im runtime-api, Exception im Generator
`RuntimeDescriptorSeverity` liegt im `grails-runtime-api` (reiner API-Typ);
`RuntimeDescriptorPlanningException` referenziert die target-grails-eigene
`RuntimeDescriptorDiagnostic` und bleibt im Generator (Abweichung von der
Modul-Zuordnung in Anhang A, begründet durch die reale Paketlage).

### P2-D007 – Semantic-only Corpus-Generierung bei unvollständiger Auswahl
Die ili2c-semantische Lesung liefert nur Root-Klassen; Szenarien mit
Abhängigkeiten (model-selection) belegen die Auswahl-Semantik über
`selectedModels` und generieren ohne Kompilierung.

### P2-D008/P2-D009 – Dokumentierte Mapping-Einschränkungen (allowedDifferences)
- Ternäre Association-Rollen mit erweiterten Zielklassen: der
  Rolle-zu-Attribut-Match löst `parcelrole_base_parcel` nicht auf
  (GORM-Default `parcel_role_id`); im Corpus dokumentiert.
- Eingebettete STRUCTURE-Referenzen besitzen keine physische FK-Spalte;
  im Corpus dokumentiert.

### P2-D010 – ProjectFileOwner delegiert an GrailsProjectFileOwner
Der vorhandene Owner-Enum ist die einzige Owner-Wahrheit; `ProjectFileOwner`
ist ein Delegations-Typ im plan-Paket.

### P2-D011 – GenerationPlan trägt den Modellnamen
Der Plan benötigt den Modellnamen für das Manifest; zusätzliches Record-Feld.

### P2-D012 – Legacy-Scan nur für Runtime-Package-Dateien blockierend
main.gsp/Assets/i18n gehören auch der App; dort nur WARNING. main.gsp-
Löschung nur mit Herkunfts-Evidenz (vorhandene Legacy-Runtime-Dateien).

### P2-D013 – Runtime-Dependency-Insertion im top-level dependencies-Block
Die Insertion überspringt den buildscript-Block (sonst
`implementation()`-Fehler im buildscript).

### P2-D014/P2-D015 – Grails-7-Dev-Mode: Views, Assets, Layout app-lokal
Grails 7 kann Plugin-JAR-Views/Assets im bootRun-Modus nicht auflösen und
verkettet Layouts nicht. Die generierte App erhält die interlisUi-Views,
die ili-*-JS-Assets, `ili-modern.css` und das Shell-Layout als
GENERATOR_MANAGED app-lokale Dateien; ein byte-identisches
Grails-Scaffold-main.gsp wird einmalig auf das Shell-Layout angehoben
(APPLICATION_OWNED; benutzerveränderte main.gsp bleiben unberührt). Das
Plugin-JAR behält seine Kopien für War-Deployments.

## Browser-E2E-Status (Stand Abnahme)

Der Browser-E2E lief beim P1-Mergecommit nie (dokumentierter Skip). Nach den
P2-Fixes (P2-D014/D015) laufen 4 von 6 Tests gegen einen echten Browser:

- PASSED: CRUD+Relationships+Geometry, List/Search/Filters, Multi-Domain-
  Workspace, Getting-Started-Inverse.
- FAILED (vorbestehend, P1-Ära, nie verifiziert): Quick-Link/Association-
  Delete und Contextual-Association: die Association-Sections auf der
  Show-Seite rendern leer. Die Registry-Erzeugung ist gegen den realen
  QuickLinkE2E-Import nachgewiesen korrekt (QUICK-Kontexte, aufgelöste
  Property-Namen, CONTEXT_IDS_BY_PARTICIPANT). Der Fehler liegt im
  Runtime-Section-Pfad der gestarteten App und wurde als vorbestehend
  nachgewiesen: identisches Fehlerbild am P1-Baseline-Commit
  `e75fb932` (Separate-Worktree-Lauf).

`verificationFull` ist auf dieser Maschine deshalb rot (browserE2eTest
required). Alle übrigen Full-Tasks sind grün (Runtime-Smoke, Real-ili2db-
Smoke, PostgreSQL-Contract inkl. Mapping-Consistency).
