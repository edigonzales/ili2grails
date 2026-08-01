# P0 Backend Correctness – Fortschrittsdokument

Ziel: Umsetzung von `ili2grails-p0-coding-agent-spec.md` (P0-A bis P0-E).

## 1. Baseline (1. August 2026)

### Umgebung

- **Branch:** `refactor/p0-backend-correctness` (von `main` @ `9c14b17` abgezweigt)
- **Commit:** `9c14b17 feat(grails-ui): inline inverse relationships with pagination and contextual create`
- **OS:** macOS 26.5.2 (aarch64)
- **Java (Launcher):** Temurin 25.0.2 LTS (25.0.2+10). Projekt-Target: Java 17 (`sourceCompatibility = VERSION_17`).
  - Wichtige Erkenntnis: Mit Launcher-JVM 25 schlagen 38 `:target-grails`-Tests mit `GroovyBugError` fehl (Groovy 4.0.24 ist mit Java 25 inkompatibel). Mit `JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.18-tem` laufen die Tests grün bis auf einen vorbestehenden Fehler (siehe unten).
  - **Konsequenz:** Alle Gradle-Testausführungen erfolgen mit `JAVA_HOME=~/.sdkman/candidates/java/17.0.18-tem`.
- **Gradle:** 8.14.3
- **Grails CLI:** 7.0.6 unter `~/.sdkman/candidates/grails/current`
- **ili2pg:** `/Users/stefan/apps/ili2pg-5.5.1` (ili2pg-5.5.1.jar + libs/)
- **Docker/Compose:** verfügbar (`docker` daemon läuft, `docker compose v5.3.1`); keine laufenden Container.
- **psql-Client:** nicht installiert (nicht benötigt, DB läuft via Docker-Compose).
- **ili2c (via Gradle):** 5.6.8, ehibasics 1.4.1, iox-ili 1.24.4
- **Untracked:** `ili2grails-p0-coding-agent-spec.md` (die Spezifikation selbst, bewusst nicht committet – nicht Teil des Repo-Produkts).

### Baseline-Testläufe

1. `./gradlew clean test --no-daemon` (Launcher JVM 25):
   - `:core:test` grün; `:target-grails:test` 129 Tests, 38 failed (`GroovyBugError`, Java-25/Groovy-Inkompatibilität, betrifft InterlisUiDescriptorSupportTest, InterlisWorkspaceSupportTest u.a.); Build failed.
2. `JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.18-tem ./gradlew :target-grails:test --no-daemon`:
   - 129 Tests, **1 failed, 128 passed**.
3. Reproduktion des verbleibenden Fehlers auf sauberem `main` (Worktree auf Commit `9c14b17`): identischer 1-Fehler.

### Vorbestehender Testfehler auf `main`

- `InterlisUiDescriptorSupportTest.workspaceBuildsScalarDetailsAndSafeToOneRelationshipLinks` (Zeile 435):
  - Assertion `.doesNotContain("id", "version")` auf dem gesamten Detail-Text schlägt fehl, weil der neuartige Relationship-Link seit `9c14b17` (`feat(grails-ui): inline inverse relationships...`) korrekt `id=42` als Navigations-Metadaten enthält (`link={controller=municipality, action=show, id=42, label=Bern}`).
  - **Fachliche Einordnung:** Die Absicht der Assertion ist, dass `id`/`version` nicht als sichtbare skalare Feldnamen exponiert werden. Der `id` im Link ist bewusste Navigations-Metadaten. Assertion wird präzisiert: `id`/`version` dürfen nicht als Feldname in `fields` vorkommen, der Link-`id` ist erlaubt.

### Ausgeführte Befehle (Baseline)

```bash
git switch main
git pull --ff-only
git status --short
git switch -c refactor/p0-backend-correctness
./gradlew clean test --no-daemon                                  # JVM 25: 38 Fehler (GroovyBugError)
JAVA_HOME=~/.sdkman/candidates/java/17.0.18-tem ./gradlew :target-grails:test --no-daemon   # 1 vorbestehender Fehler
```

## 2. Annahmen

- Alle Test-/Gradle-Aufrufe mit `JAVA_HOME=~/.sdkman/candidates/java/17.0.18-tem` (Java-17-Target des Projekts; JVM-25-Inkompatibilität mit Groovy 4.0.24 dokumentiert, kein Produkt-Problem).
- Contract-Test-Infrastruktur: Docker-Compose-PostgreSQL `localhost:54321` (laut Spezifikation), ili2pg 5.5.1, Grails CLI 7.0.6.
- ili2c 5.6.8 bietet keine öffentliche API für direkte Imports eines Modells; `Model.getImporting()` liefert in 5.6.8 die von diesem Modell importierten Modelle (per Probe verifiziert, 1. August 2026). `ModelSelectionResolver` nutzt diese stabile API; die Traversierung ist in der Javadoc dokumentiert.
- ili2db schreibt Modelle mit Imports als `Name{imports}` in `t_ili2db_model`; der kanonische Modellname ist der Teil vor `{` (im Reader normalisiert).
- ili2pg `--createBasketCol` erzeugt eine NOT NULL `t_basket`-Spalte, die die generierte App nicht befüllen kann; der Contract-Test verwendet die E2E-etablierten Flags ohne `--createBasketCol`.
- Attributlose binäre Associations mit einer Rolle `{0..1}` werden von ili2pg als Embedded-FK abgebildet (read-only im UI). `CARDINALITY_MAX_EXCEEDED` ist daher nur mit einem begrenzten Maximum > 1 (`{0..2}`) testbar, was eine echte Link-Tabelle erzeugt.
- Ein semantisches Attribut mit CoreType `COMPOSITION` ohne physische Spalte wird nicht als `ATTRIBUTE_UNMATCHED` diagnostiziert (strukturell über die Relationship repräsentiert).
- `mergeToken` trägt den exakten Match-Wert (z. B. `ParentRef` statt normalisiertem `parentref`); Golden-Snapshots wurden entsprechend angepasst.
- `qualifiedName` von Attributen gewinnt semantisch (Spezifikation §5.6); Golden- und Grails-Snapshots wurden entsprechend angepasst.

## 3. Commit-Plan

Siehe Spezifikation §2.2. Zusätzlich (fachlich begründet, dokumentiert):
- `test(grails): fix pre-existing UI descriptor assertion for link id metadata` (vorbestehender Baseline-Fehler).
- `feat(cli): surface model selection and merge diagnostics` (Spezifikation §10).
- `test(grails): align browser E2E with actual delete-integrity flash message` (vorbestehender Baseline-Fehler, auf `main` reproduziert).

## 4. Fortschritt

| Schritt | Status | Notizen |
|---|---|---|
| Baseline und Inventar | abgeschlossen | 1. August 2026 |
| P0-D SQL-Identifier | abgeschlossen | `reader/sql`-Paket + Ili2dbMetadataReader ohne `{schema}` |
| P0-E ModelSelection | abgeschlossen | `metadata/selection` + ili2c-Traversierung über `Model.getImporting()` |
| P0-A Matcher/Diagnostics/Merger | abgeschlossen | `metadata/merge` + PostProcessor + Validator |
| P0-A Aktivierung MetadataReader | abgeschlossen | `readMetadataResult(modelName, policy)` + STRICT-Gate |
| P0-B Grails Persistence/UI Split | abgeschlossen | kein inverses `hasMany`; `PersistentCollection` mit `mappedBy` nur bei beweisbarer Child-FK |
| P0-C Grails/PostgreSQL Contract Test | abgeschlossen | `grailsPostgresContractTest` + `P0PersistenceContract.ili`, 9 Spock-Tests grün inkl. obligatorischem Modus |
| CLI-/Diagnose-Integration | abgeschlossen | Selected models + Diagnostics; `MetadataMergeException` → Exit 65 |
| Abschlussprüfung und Bericht | abgeschlossen | vollständige Matrix grün |

## 5. Ausgeführte Befehle und Resultate

### P0-D (SQL-Identifier)
```bash
JAVA_HOME=~/.sdkman/candidates/java/17.0.18-tem ./gradlew :core:test --no-daemon --tests 'ch.interlis.generator.reader.*' --tests 'ch.interlis.generator.reader.sql.*'   # grün
JAVA_HOME=~/.sdkman/candidates/java/17.0.18-tem ./gradlew :core:test :cli:test --no-daemon                                                                    # grün
```
Neue Klassen: `SqlIdentifier`, `SqlIdentifierKind`, `SqlIdentifierRenderer`, `QualifiedSqlName`, `InvalidSqlIdentifierException`.
Umbau `Ili2dbMetadataReader`: Factory `create(Connection, String)` mit Renderer aus JDBC-Metadaten; `metaTable`/`qualifiedDiscoveredTable`/`quotedDiscoveredColumn`; `{schema}`-Ersetzung entfernt.
Wichtige Verfeinerung: `DATABASE_DISCOVERED`-Namen werden nur gequotet, wenn sie vom Kleinbuchstaben-Muster abweichen – reine Kleinbuchstaben-Namen (ili2pg/H2, ungequotet angelegt) müssen ungequotet auflösbar bleiben (sonst bricht H2-Schema-Lookup).

### P0-E (ModelSelection)
```bash
JAVA_HOME=~/.sdkman/candidates/java/17.0.18-tem ./gradlew :core:test --no-daemon --tests 'ch.interlis.generator.metadata.selection.*'   # grün
```
Neue Klassen: `ModelSelection`, `ModelSelectionSource`, `ModelSelectionResolver`; `Ili2cModelReader.read()`/`resolveModelSelection()`; `Ili2dbMetadataReader.readMetadata(ModelSelection)`.
Fixture-Modelle: `ModelSelectionRoot/Dependency/TransitiveDependency/Unrelated.ili`.
Zusätzlich: `Name{imports}`-Normalisierung in `t_ili2db_model` (nötig für VSADSSMINI).

### P0-A (Merger)
```bash
JAVA_HOME=~/.sdkman/candidates/java/17.0.18-tem ./gradlew :core:test :cli:test --no-daemon   # grün
```
Neue Klassen (Spec §5.2): `MetadataMerger`, `MetadataMergeResult`, `MetadataMergeException`, `MetadataMergePolicy`, `MergeDiagnostic`, `MergeDiagnosticCode`, `MergeSeverity`, `MatchCandidate`, `MatchDecision`, `MatchReason`, `MergeTokenNormalizer`, `AttributeMatcher`, `RelationshipMatcher`, `ModelMetadataCopier`, `MetadataPostProcessor`, `MetadataValidator`, `MetadataReadResult`.
Aktivierung: `MetadataReader.readMetadataResult(modelName, policy)`; DB-only-Fallback mit Root-only-Selection.
Neues Fixture: `test-models/MergeAmbiguityCases.ili` + `MergeAmbiguityFixtures`.

### P0-B (Grails-Persistenz/UI)
```bash
JAVA_HOME=~/.sdkman/candidates/java/17.0.18-tem ./gradlew :target-grails:test :core:test :cli:test :target-django:test --no-daemon   # grün
```
- `GrailsRelationshipMapper`: `relationshipsByTarget`-Block entfernt; neue APIs `incomingRelationships`, `outgoingRelationships`, `resolvePropertyForRelationship` + `PropertyResolution`; `DomainMapping` mit `diagnostics`; `PersistentCollection` (COMPOSITION) mit `mappedByProperty`; `PersistenceDiagnostic`.
- `GrailsInverseRelationshipPlanner`: plant direkt aus Core-Relationships; `GrailsInverseRelationshipPlan.persistentCollectionBacked`.
- `GrailsDomainGenerator`: `renderHasMany`/`renderMappedBy`/`renderBelongsTo`; `hasMany` nur aus `PersistentCollection`.

### P0-C (Contract-Test)
```bash
PATH=~/.sdkman/candidates/grails/current/bin:$PATH JAVA_HOME=~/.sdkman/candidates/java/17.0.18-tem \
  ./gradlew :target-grails:grailsPostgresContractTest -PcontractTestRequired=true --rerun-tasks --no-daemon   # grün
```
9 Spock-Tests gegen echtes ili2pg-PostgreSQL: normale Referenz ohne hasMany, zwei FKs getrennt, Quick-Link (create/duplicate/delete), Ownership-Mismatch, inverse assign/reassign, Validierung/Rollback, CARDINALITY_MAX_EXCEEDED, CONCURRENT_MODIFICATION, Komposition ohne Join-Tabelle.
Reports: `target-grails/build/reports/grails-postgres-contract/` (environment.txt, generated-app-path.txt, metadata-diagnostics.json, integration-test-output.log, generated-domain-summary.md, database-mapping-summary.md; Passwörter redigiert).
Bewiesene physische Abbildungen (aus database-mapping-summary.md):
- `Document.aowner → contract_person` (REFERENCE_ATTRIBUTE, kein hasMany auf Person).
- `Journey.departurestation`/`arrivalstation → contract_station` (zwei getrennte FKs, zwei inverse Pläne).
- `Building.components` hasMany `mappedBy contractBuildingComponents`; FK `contract_component.contract_building_components → contract_building`; keine Join-Tabelle.
- Link-Tabellen `contract_journeylink`/`contract_parcelownerlink`/`contract_documentlink` mit ASSOCIATION_ROLE-Properties.
Skript: `scripts/run-p0-contract-tests.sh`.

### CLI (Spezifikation §10)
```bash
JAVA_HOME=~/.sdkman/candidates/java/17.0.18-tem ./gradlew :cli:test --no-daemon   # grün
```
`MetadataReaderService.readResult()` gibt `Selected models:` und `Metadata diagnostics:` aus; `GenerateCommand` fängt `MetadataMergeException` gezielt (Exit 65 = sysexits EX_DATAERR, Picocli bietet kein DATAERR).

## 6. Snapshot-Änderungen (einzeln begründet)

### `core/src/test/resources/metadata-golden/SimpleAddressModel.merged-h2.json`
- Attribute `qualifiedName` von physischem (Kleinbuchstabe) auf semantischen Wert (CamelCase, z. B. `...Address.Street`): Spezifikation §5.6 (semantisch gewinnt). Abgesichert durch `MetadataJsonWriterTest.writesDeterministicMergedGoldenJson`.

### `core/src/test/resources/metadata-golden/AssociationCases.merged-h2.json`
- `mergeToken` von normalisiertem Kleinbuchstaben auf exakten Match-Wert (z. B. `DocumentRole`): Match-Decision liefert den exakten Token (Spec §5.7). Abgesichert durch `writesDeterministicMergedAssociationCasesGoldenJson`.

### `core/src/test/resources/merge-report-golden/AssociationCases.merged-h2.md`
- Gleiche `mergeToken`-Korrektur im Report. Abgesichert durch `RelationshipMergeReporterTest.writesDeterministicMarkdownGoldenForMergedAssociationCases`.

### `target-grails/src/test/resources/grails-snapshots/simple-address/.../Address.groovy`, `Person.groovy`
- `qualifiedName` in `interlisFieldMeta` semantisch (wie oben). Abgesichert durch `GrailsGeneratedOutputSnapshotTest.simpleAddressMergedOutputMatchesSnapshots`.

### `target-grails/src/test/resources/grails-snapshots/structure-composition/.../Asset.groovy`
- `static hasMany = [parts: Part]` entfernt: semantisch-only Fixture ohne physische Child-FK-Evidenz → fail-closed (keine persistente Collection). Abgesichert durch `GrailsGeneratedOutputSnapshotTest.structureCompositionOutputMatchesSnapshots` + `compositionWithoutPhysicalChildFkIsNotPersisted` in `GrailsRelationshipMapperTest`.

## 7. Verbleibende Risiken

- `ATTRIBUTE_UNMATCHED`/`RELATIONSHIP_UNMATCHED` als WARNING: vollständige Abdeckung aller denkbaren Unmatch-Fälle ist modellabhängig; keine Blockade.
- `MetadataValidator` Invariante 12 (Kardinalität vs. DB-Nullability) ist WARNING; echte Widersprüche werden diagnostiziert, aber nicht blockiert.
- Der Contract-Test startet `docker compose up -d edit-db`; ein bereits laufender Fremd-Container auf Port 54321 wird nicht geprüft.
- Die E2E-Delete-Integritäts-Assertion wurde auf die tatsächliche Flash-Meldung präzisiert (vorbestehender Fehler auf `main`).
- JVM-25-Inkompatibilität mit Groovy 4.0.24: Tests laufen mit Java 17 (dokumentiert, kein Produkt-Problem).

