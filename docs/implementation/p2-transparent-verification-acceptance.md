# P2 Abschlussbericht

## Ergebnis

P2 wurde vollständig umgesetzt: reproduzierbare Verifikationsprofile,
vollständige Reader-Diagnostics, wirksame Runtime-Descriptor-Diagnostics,
fail-closed Runtime-Safety, versionierter Modellkorpus, Mapping-Consistency
gegen Hibernate und PostgreSQL, Plan-before-write-Generierung mit Manifest,
Dry-run, Reader-Query-Budgets, Guards, JaCoCo-Class-Gates und
GitHub-Fast-Check. `verificationFast` ist grün; alle `verificationFull`-Tasks
sind grün bis auf zwei vorbestehende Browser-E2E-Tests (siehe unten).

## Ausgangscommit und Umgebung

- Ausgangscommit: `e75fb93240967876d42dba9ad1e095ea4ac13402`
  (P1-Mergecommit, identisch mit der Spezifikation).
- Arbeitsbranch: `test/p2-transparent-verification`.
- Umgebung: macOS 26.5.2 aarch64; Shell-JDK 25.0.2, Java-17-Toolchain
  (Temurin 17.0.18); Gradle 8.14.3; Grails 7.0.6; ili2pg 5.5.1;
  Docker 29.6.2 + Compose v5.3.1; Playwright-Chromium 1223.
- Wichtiger Baseline-Befund: `clean test` schlägt mit dem Shell-JDK 25 in
  Groovy-Supportklassen fehl (GroovyBugError); mit der Java-17-Toolchain ist
  die Baseline grün (403 Tests). Der Befund begründet die Toolchain-Phase.

## Warum diese Arbeiten notwendig waren

- Reader-Diagnostics waren deklariert, aber nicht verdrahtet (fehlendes
  Root-Modell war eine fachliche IllegalArgumentException).
- Der Runtime-Descriptor-Planner initialisierte eine Diagnostic-Liste ohne
  je Diagnostics zu erzeugen; der Non-strict-Modus behauptete ein
  Write-Downgrade, das technisch nicht erzwungen war.
- Der Generator schrieb vor der Planung; blockierende Legacy-Diagnostics
  stoppten den Schreibvorgang nicht.
- main.gsp hatte zwei widersprüchliche Owner.
- Es gab keine versionierte Quelle für unterstützte Modell-Szenarien und
  keinen Vergleich IR-GORM-Datenbank.
- Die erweiterten Tests waren nicht reproduzierbar (absolute lokale
  ili2pg-Defaults, kein Required-Modus).
- Der Browser-E2E lief beim P1-Mergecommit nie; Grails 7 kann Plugin-JAR-
  Views/Assets im Dev-Modus nicht auflösen (durch P2-D014/D015 behoben).

## Architekturentscheidungen

Siehe Fortschrittsdokument, P2-D001 bis P2-D015 (Toolchain, nur zwei
Verifikationsprofile, main.gsp application-owned, Reader-Diagnostic-Codes
ohne Duplikat, allowedDifferences, Legacy-Scan-Semantik, Grails-7-Dev-Mode
app-lokale Views/Assets/Layout u.a.).

## Reader-Diagnostics

- `Ili2dbReadContext` trägt nur die JDBC-Umgebung; `Ili2dbReadRequest` nur
  die fachliche Anfrage (Modellauswahl, Policy, Flags).
- Fehlendes Root-Modell → FATAL `REQUESTED_MODEL_MISSING`; fehlende
  Dependency → WARNING `SELECTED_DEPENDENCY_MISSING`; keine fachliche
  `IllegalArgumentException` am Reader-Rand.
- `t_ili2db_table_prop` ist über `Ili2dbTableRequirementResolver` (einzige
  Wahrheit) REQUIRED.
- Capability-Detection: ein `getTables`- und ein `getColumns`-Durchlauf,
  case-insensitive Normalisierung, echte Datenbanknamen erhalten.
- Jeder `Ili2dbDiagnosticCode` hat einen erreichbaren, fokussierten Test
  (Coverage-Map + Reachability-Tests).

## Runtime-Descriptor-Diagnostics

- `RuntimeDescriptorDiagnostic` mit Severity/Details/blocking(); neue Codes
  für Duplikate und inkonsistente Deskriptoren.
- `RuntimeDescriptorPlanner` erzeugt echte Diagnostics: unresolved writable
  Relationship/Inverse → ERROR, read-only external/abstrakt → WARNING,
  Duplikate → ERROR.
- `RuntimeDescriptorPlan.throwIfBlocking()`; `GrailsCrudGenerator` führt das
  Gate vor jeder Dateiplanung aus.

## Runtime-Safety-State

- `InterlisRuntimeSafetyState`: gültige Registry → Schreiben erlaubt;
  Strict + ungültig → Startup-Fehler; Non-strict + ungültig → technisch
  read-only. Command-Services liefern `RUNTIME_DESCRIPTOR_INVALID`; Flows und
  Views bieten Schreibaktionen nicht an. Logging beschreibt das tatsächliche
  Verhalten (kein Per-Feature-Downgrade).

## Modellkorpus

- `verification/model-corpus.yaml` (Schema 1): 27 Features, 8 Szenarien
  (Relationships, Associations, Composition, Geometry, ModelSelection,
  Merge-Ambiguität, VSADSSMINI). Loader/Validator/Runner implementiert;
  `verifyModelCorpusManifest` läuft in `verificationFast`.

## INTERLIS-Feature-Matrix

- `docs/verification/interlis-feature-matrix.md` wird generiert;
  `SUPPORTED` nur mit realem DB-/Mapping-Vertrag ohne allowedDifferences;
  `verifyFeatureMatrixUpToDate` vergleicht committed vs. generiert.
  Zwei dokumentierte Einschränkungen (ternäre Rollen-FK-Namensgebung,
  eingebettete STRUCTURE-Referenzen) machen die betroffenen Features
  ehrlich `PARTIAL`.

## Mapping-Consistency

- `grailsPostgresContractTest` ist corpus-gesteuert (4 Szenarien): erwartetes
  Mapping (Core-IR + Grails-Planer) vs. Hibernate-Persister-Snapshot der
  gestarteten App vs. reales PostgreSQL-Schema; Validator prüft Tabellen,
  Spalten, ID, Version, FKs, Nullability, Collections, Join-Tables,
  Association-Storage, Geometry-Typ/SRID. Pro Szenario entstehen
  expected/hibernate/database-mapping.json, mapping-comparison.json/md,
  metadata-diagnostics.json, integration-test-output.log. Keine ungeklärten
  Mismatches.

## Generation Plan und Manifest

- `GrailsGenerationPlanner` plant alles vor dem Write; `GrailsGenerationExecutor`
  schreibt atomar, löscht zuletzt, Manifest zuletzt; ein Blocker → null
  Projektänderungen. Alle Generatoren haben reine `plan()`-Funktionen.
- Manifest `.ili2grails/generation-manifest.json`: deterministisch, keine
  Timestamps/Pfade/Credentials, SHA-256-Ownership, Pfadtraversal-Rejection.
- Zweite identische Generation ist vollständig idempotent (auch im realen
  Grails-Smoke-App-Lauf nachgewiesen).

## Dry-run

- CLI: `--grails-dry-run`, `--grails-plan-json`, `--grails-plan-markdown`;
  Konsolenausgabe CREATE/UPDATE/DELETE/UNCHANGED/BLOCKED; Exit ungleich null
  bei Blockern; kein Projekt-Write.

## Schutz benutzerveränderter Dateien

- User-modified Managed Files → `USER_MODIFIED_MANAGED_FILE` → gesamter Apply
  blockiert. main.gsp ist immer APPLICATION_OWNED; nur das byte-identische
  Grails-Scaffold wird einmalig auf das Shell-Layout angehoben
  (P2-D015); Legacy-Dateien nur bei bekanntem SHA-256 gelöscht.

## Reader-Query-Budgets

- `Ili2dbQueryBudgetTest`: Capability-Detection genau einmal,
  keine Attribut-N+1-Introspection, Enum-Werte pro Tabelle und Lauf einmal,
  Deriver/Assembler ohne JDBC. `realIli2dbSmokeTest` schreibt
  query-metrics.json/md.

## Verifikationsprofile

- `verificationFast` (grün) und `verificationFull` (bis auf 2 vorbestehende
  E2E-Tests grün); keine weiteren Profile.

## GitHub Actions

- `.github/workflows/verification-fast.yml`: push auf main + pull_request,
  Temurin 17, Gradle-Cache, `verificationFast`, Report-Upload, minimale
  Permissions, Concurrency mit Cancel.
- `scripts/run-verification-full.sh`: set -euo pipefail, prüft Grails,
  Docker Compose und ILI2PG_HOME, aktiviert alle Required-Modi, nennt
  Reportpfade.

## Neue Klassen

Reader (core): Ili2dbReadCoordinatorDiagnosticTest, Ili2dbQueryBudgetTest,
Ili2dbDiagnosticInvariantTest, Ili2dbDiagnosticReachabilityTest,
ModelMetadataFingerprint(+Test), metrics (CountingJdbcProxy,
CountingConnection, JdbcInvocationKind, JdbcInvocationSummary).
Runtime-API: RuntimeDescriptorSeverity, CommandCode.RUNTIME_DESCRIPTOR_INVALID.
Runtime-Plugin: InterlisRuntimeSafetyState (+Spec), RegistryDiagnosticCode.
Generator (target-grails): RuntimeDescriptorPlanningException,
GrailsGenerationBlockedException, plan-Paket (ProjectChangeType,
ProjectFileOwner, PlannedProjectFile, ProjectChange, GenerationDiagnosticCode,
GenerationDiagnostic, GenerationPlan(+Summary), GeneratedProjectManifest,
ManagedFileManifestEntry, GeneratedProjectManifestStore,
GrailsGenerationPlanner, GrailsGenerationExecutor, GenerationExecutionResult,
GenerationOwnershipValidator, TextFileEdit, GenerationPlanReportWriter).
Verifikation (test): environment/report/corpus/contract/mapping-Pakete,
4 Guard-Tests, Regenerations-/Manifest-/Planner-Tests, Corpus-Tests,
UI-Views/-Assets-Overlays.

## Geänderte Klassen

Reader (core): Ili2dbReadContext/Request/Result, Ili2dbReadCoordinator,
Ili2dbCatalogReader, Ili2dbTableRequirementResolver, Ili2dbMetadataReader,
Ili2dbMetadataAssembler, Ili2dbAssociationDeriver, Ili2dbDiagnostic,
Ili2dbDiagnosticCode, GeometryIntrospectorFactory, JdbcSchemaSnapshot.
Generator: GrailsCrudGenerator, GrailsDomainGenerator, GrailsEnumGenerator,
GrailsAssociationRegistryGenerator, GrailsUiRegistryGenerator,
GrailsBuildGradleUpdater, GrailsApplicationYamlUpdater,
GrailsRuntimeDependencyInstaller, GrailsAssetManifestUpdater,
GrailsApplicationConfigurationUpdater, GrailsScaffoldingTemplateInstaller,
GrailsProjectFileOwnership, RuntimeDescriptorPlanner/Diagnostic/Plan/Code.
Runtime-Plugin: Ili2grailsRuntimeGrailsPlugin, InterlisCrudControllerSupport,
Form/List/Association/Inverse-Flows, Association/Inverse-CommandServices,
GSP-Views. CLI: GrailsCliOptions, GrailsCliTarget. Build: Root- und
target-grails-build.gradle, settings.gradle, JaCoCo-Gates.

## Entfernte oder deprecated Klassen

- `Ili2dbDiagnosticCode.DUPLICATE_PHYSICAL_CLASS/COLUMN` (Duplikate der
  Modell-Validator-Codes, P2-D005).
- `RegistryDiagnosticCode.SAFE_DOWNGRADE_VIOLATED` (unbenutzt; die Safety
  wird jetzt technisch erzwungen).

## Tests

- `verificationFast` (grün): core 213, grails-runtime-api 32,
  grails-runtime 25, target-grails 213, target-django 7, cli 15;
  dazu Corpus-Manifest, Ownership-Regeln, JaCoCo-Class-Gates,
  Toolchain-Assertion.
- `grailsRuntimeSmokeTest` 7/7 grün (inkl. E2E-Regenerationsvertrag).
- `realIli2dbSmokeTest` grün (inkl. Query-Metrics).
- `grailsPostgresContractTest` 4/4 grün (Mapping-Consistency).
- `browserE2eTest` 4/6 grün; 2 vorbestehende Failures (siehe unten).
- Reports: build/reports/ili2grails-verification/summary.json|md,
  build/reports/model-corpus/, target-grails/build/reports/
  grails-postgres-contract/<szenario>/, build/reports/real-ili2db-smoke/.

## Nicht ausgeführte Prüfungen

| Prüfung | Grund | vorgesehener Befehl | nicht bewiesene Aussage |
|---|---|---|---|
| Browser-E2E Association-Tests (2/6) | vorbestehendes P1-Problem (nie ausgeführt); Sections-Pfad rendert leer; identisches Fehlerbild am P1-Baseline-Commit (Separate-Worktree-Lauf) | `./scripts/run-verification-full.sh` bzw. `:target-grails:browserE2eTest -PbrowserE2eRequired=true` | „alle Browser-Pfade grün“; Quick-Link-/Contextual-Form-Pfade |
| GitHub-Workflow-Lauf | kein Push (nur lokale Arbeit) | Workflow `verification-fast.yml` | „CI auf GitHub grün“ |
| War-Deployment-Rendering | nicht Teil von P2 | – | Plugin-JAR-Views im War-Modus |

## Abweichungen von der Spezifikation

- `RuntimeDescriptorSeverity`/`RuntimeDescriptorPlanningException`-Modul-
  Zuordnung (P2-D006, begründet).
- `GenerationPlan` trägt zusätzlich den Modellnamen (P2-D011).
- Browser-E2E nicht vollständig grün (2 vorbestehende Failures, siehe oben).

## Verbleibende Risiken

- Association-Sections-Rendering im Browser (vorbestehend, P1-Ära):
  Die Registry-Erzeugung ist gegen den realen Import nachgewiesen korrekt;
  der Fehler liegt im Runtime-Section-Pfad der gestarteten App und braucht
  eine Laufzeit-Debugging-Sitzung mit der laufenden App.
- VSADSSMINI-Szenario hängt vom externen Modell-Repository ab
  (Infrastruktur-Skip bei Nichterreichbarkeit).
- Der GitHub-Fast-Check ist konfiguriert, aber noch nicht auf einem
  Push/PR gelaufen.

## Definition of Done

- Build/Transparenz: Java-17-Toolchain aktiv und per Assertion geprüft;
  keine absoluten ili2pg-Defaults; verificationFast grün; Fast-Workflow
  vorhanden; Fortschrittsdokument vollständig.
- Reader: Request/Context entkoppelt; Root-/Dependency-Fehler als
  Diagnostics; Capability single-pass; t_ili2db_table_prop required;
  Query-Budgets grün; keine fachlichen IllegalArgumentException-Leaks.
- Runtime: Planner-Diagnostics wirksam; Gate vor Dateiplanung; Strict-Mode
  fail-closed; Non-strict technisch read-only; Command-Services blockieren;
  Logs stimmen.
- Modellkorpus: versioniert, validiert, Runner, Feature-Matrix aktuell.
- Mapping: erwartetes/Hibernate/DB-Mapping verglichen, keine ungeklärten
  Mismatches, Reports vorhanden.
- Regeneration: Plan vor Write, Dry-run, Manifest, Idempotenz,
  User-modified-Blocking, application-owned-Schutz, Manifest zuletzt.
- Tests: Standardtests, Runtime-Smoke, Real-ili2db-Smoke und
  PostgreSQL-Contract grün; Browser-E2E 4/6 grün (2 vorbestehende
  Failures dokumentiert); Verification-Summary vorhanden; keine Secrets
  in Reports (Guard).
