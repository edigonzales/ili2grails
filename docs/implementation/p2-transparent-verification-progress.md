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

- (Noch keine; erste Commits folgen ab Phase 1.)
