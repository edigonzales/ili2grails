# ili2grails

ili2grails liest ein mit ili2db erzeugtes Datenbankschema und – sofern
verfügbar – das zugehörige INTERLIS-Modell. Daraus entsteht ein
framework-agnostisches Metamodell. Das Grails-Target erzeugt daraus Domains,
Enums, typisierte Runtime-Registries und optional eine lokale Bootstrap-CRUD-
Oberfläche. Ein kleines Django-Target zeigt, dass die Core-IR nicht an Grails
gebunden ist.

Die zentrale Architekturentscheidung ist der Hybrid-Ansatz:

- ili2db liefert die tatsächlich verwendeten Tabellen-, Spalten- und
  Mappingnamen sowie das reale Datenbankschema.
- ili2c liefert Semantik wie Rollen, Kardinalitäten, Constraints,
  Dokumentation, Units und Enumdefinitionen.
- Der `MetadataMerger` verbindet beide Quellen deterministisch zu
  `ModelMetadata`.

Warum beide Quellen nötig sind, erläutert
[Warum ili2grails das INTERLIS-Datenmodell braucht](docs/why-interlis-model.md).

## Voraussetzungen

- exakt **JDK 17** für Build, Tests und Generator
- eine ili2db-Datenbank; produktiv und in den erweiterten Tests wird ili2pg/
  PostgreSQL verwendet
- eine lokale `.ili`-Datei oder Zugriff auf ein INTERLIS-Modellrepository
- für ein neues oder lokal gestartetes Grails-Projekt: Grails 7 CLI

Prüfen:

```bash
java -version
./gradlew --version
```

Der Gradle-Build verweigert einen Daemon, der nicht auf Java 17 läuft. Falls
mehrere JDKs installiert sind:

```bash
export JAVA_HOME=/pfad/zu/jdk-17
```

## Build aus dem Source-Checkout

```bash
./gradlew build
```

Generierte Grails-Anwendungen beziehen die Snapshot-Runtime aus Maven Local.
Vor dem ersten Start einer solchen Anwendung werden Runtime-API und
Runtime-Plugin gemeinsam publiziert:

```bash
./gradlew prepareLocalRuntime
```

Die drei Grails-App-Testtasks, die diese Artefakte benötigen, hängen bereits
direkt von `prepareLocalRuntime` ab. Ein vorgängiger Smoke-Test ist nicht
nötig.

Für einen vollständigen Einstieg mit Docker, ili2pg, Beispieldaten und einer
Grails-App siehe [Getting Started](docs/getting-started.md). Der automatisierte
einfache Ablauf ist:

```bash
./scripts/getting-started.sh simple
```

## CLI

### Metadaten lesen

```bash
./gradlew :cli:run --args="read \
  'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=sa' \
  SimpleAddressModel \
  sa \
  --model-file test-models/SimpleAddressModel.ili"
```

Ohne lokale Datei kann ili2c ein Modellrepository verwenden:

```bash
./gradlew :cli:run --args="read \
  'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=public' \
  DM01AVCH24LV95D \
  public \
  --model-repos https://models.interlis.ch/"
```

### In ein bestehendes Grails-Projekt generieren

```bash
./gradlew prepareLocalRuntime

./gradlew :cli:run --args="generate \
  'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=sa' \
  SimpleAddressModel \
  sa \
  --target grails \
  --model-file test-models/SimpleAddressModel.ili \
  --grails-output /pfad/zur/grails-app \
  --grails-package ch.example.demo \
  --grails-ui-theme bootstrap \
  --grails-map-editor openlayers"
```

Ein neues Projekt kann mit `--grails-init [appName]` erzeugt werden.
`--grails-generate-all` führt anschließend für jede Domain das Grails-
Scaffolding aus und ist nur zusammen mit `--grails-init` zulässig.

Wichtige Grails-Optionen:

| Option | Werte/Zweck |
| --- | --- |
| `--grails-ui-theme` | `default` oder `bootstrap` |
| `--grails-map-editor` | `none` oder `openlayers` |
| `--grails-language` | `de-CH` oder `en` |
| `--grails-default-srid` | Default-SRID für Geometry-Binding |
| `--grails-association-ui` | `auto`, `off`, `read-only`, `editable` |
| `--grails-association-page-size` | Seitengröße 1–100 |
| `--grails-association-navigation` | `auto`, `show`, `hide` |
| `--grails-dry-run` | vollständiger Plan ohne Änderung des Zielprojekts |
| `--grails-plan-json` | Plan als JSON außerhalb des Zielprojekts schreiben |
| `--grails-plan-markdown` | Plan als Markdown außerhalb des Zielprojekts schreiben |

Für die Django-Ausgabe werden `--target django`, `--django-output` und
`--django-app` verwendet. Mehrere `--target`-Optionen werden in der angegebenen
Reihenfolge ausgeführt.

## Ein einziger atomarer Generatorpfad

Alle Grails-Generierungen – normal und Dry-run – verwenden ausschließlich
`GrailsCrudGenerator`:

```java
GenerationPlan plan = generator.plan(metadata, config); // schreibt nichts
GenerationExecutionResult result = generator.apply(plan, config);

// Kurzform mit demselben Plan-/Apply-Pfad:
generator.generate(metadata, config);
```

Der Planner ermittelt vor dem ersten Write Domains, Enums, Registries,
Konfiguration, Dependencies, Legacy-Dateien, Ownership und Runtime-Descriptor-
Diagnostics. Ein Plan bleibt auch bei Blockern vollständig inspizierbar.
`apply` beziehungsweise `generate` verändern bei einem Blocker keine Datei im
Zielprojekt.

`.ili2grails/generation-manifest.json` enthält Hash und Ownership aller
generatorverwalteten Dateien. Daraus folgen drei wichtige Regeln:

- Eine zweite identische Generierung ist idempotent.
- Benutzerveränderte verwaltete Dateien blockieren den gesamten Lauf.
- Ein Theme-Wechsel entfernt nur unveränderte, nicht mehr benötigte
  generatorverwaltete Dateien.

Bekannte alte Runtime-Kopien werden über
`grails/migration/legacy-runtime-v1.sha256` erkannt. Nur ein exakter Hash darf
automatisch gelöscht werden; geänderte oder unbekannte Runtime-Dateien
blockieren.

## Theme- und Dependency-Verhalten

| Bedingung | Geplante Artefakte/Dependencies |
| --- | --- |
| immer | Domains, Enums, typisierte Registries, Runtime-Plugin, PostgreSQL-Treiber, `application.yml`, Spring-Konfiguration |
| Theme `default` | keine ili2grails-Scaffolding-Templates, Views, UI-Assets oder Bootstrap-Dependency |
| Theme `bootstrap` | Bootstrap-Dependency, Scaffolding-Templates, lokale Grails-7-Views, Layout, JavaScript und Stylesheet |
| Map-Editor `openlayers` | OpenLayers und Proj4 |
| Modell mit Geometrie beziehungsweise Geometry aktiviert | JTS und Hibernate Spatial |

`build.gradle` erhält genau einen markierten ili2grails-Dependency-Block und,
falls noch nicht vorhanden, einen markierten `mavenLocal()`-Eintrag. Exakt
bekannte alte Generatorzeilen werden migriert. Abweichende, möglicherweise
benutzereigene Dependency-Versionen werden nicht entfernt.

Beim Bootstrap-Theme liegen die Grails-7-Scaffolding-Templates unter
`src/main/templates/scaffolding`. Die tatsächlich verwendeten App-Views und
Assets werden lokal nach `grails-app/views`, `grails-app/assets` und
`grails-app/conf` geschrieben. Die generierte Anwendung hängt damit für ihr
Layout nicht von einer versteckten Kopierphase oder von externen CDNs ab.

Die Datenbankkonfiguration verwendet in `development` die übergebene JDBC-URL,
entfernt darin enthaltene Credentials und schreibt stattdessen
`DB_USERNAME`/`DB_PASSWORD`-Platzhalter. `production` erwartet `DB_URL`,
`DB_USERNAME` und `DB_PASSWORD`. `dbCreate` bleibt `none`.

## Reader und Metamodell

Der physische Reader hat zwei öffentliche Pfade:

```java
Ili2dbReadResult diagnostic = reader.read(selection);
ModelMetadata strict = reader.readMetadata(selection);
```

`read(ModelSelection)` liefert Diagnostics und, soweit möglich, ein partielles
Ergebnis. `readMetadata(ModelSelection)` ist der strikte Komfortpfad und wirft
bei blockierenden Diagnostics. Enumwerte werden immer gelesen. Der Coordinator
erhält die Modellauswahl und das tatsächlich gewünschte Geometry-Flag direkt;
bei deaktivierter Geometry-Introspection werden keine Geometry-Metadaten
abgefragt.

Die Core-IR (`ModelMetadata`) ist unveränderlich und framework-agnostisch. IO,
Kataloglesen, Schema-Introspection, Assembly, semantischer Merge und
Target-Generierung sind getrennt. Merge-Entscheidungen können optional als
Markdown und JSON ausgegeben werden.

## Generierte Grails-Runtime

Der Generator erzeugt genau zwei typisierte Registry-Singletons:

- `InterlisUiRegistry` implementiert `DomainRegistry`.
- `InterlisAssociationRegistry` implementiert `AssociationRegistry`.

Beim Plugin-Startup werden beide `INSTANCE`-Registries einmal geladen und zur
injizierten `InterlisRuntimeRegistry` vereinigt. Navigation, Formulare,
Associations, inverse Beziehungen, Queries und Commands arbeiten danach nur
mit typisierten Deskriptoren. Maps entstehen erst an der GSP-/JSON-Grenze als
View- oder HTTP-Modell.

Die Runtime validiert die Registries beim Start. Im Standard-Strict-Modus
verhindern blockierende Diagnostics den Start. Bei ausdrücklich deaktiviertem
Strict-Modus startet die Runtime fail-closed und sperrt alle generischen
Schreiboperationen.

### Businesslogik und Erweiterungspunkte

Businesslogik gehört in normale Grails-Mechanismen:

- Grails-Services für Anwendungsfälle und Transaktionen
- GORM-Events für Persistenz-Lifecycle-Verhalten
- explizite projektspezifische Services und Controller
- eine anwendungseigene Bean für `InterlisAuthorizationPolicy`, wenn die
  Allow-all-Defaultpolicy ersetzt werden soll
- optional ein eigener Display-Label-Resolver oder Record-Loader als
  Spring-Bean mit dem bestehenden Bean-Namen

Es gibt keine zusätzliche generische Workflow-, Paging- oder Lifecycle-Hook-
Abstraktion. Die generischen Command-Services behandeln ausschließlich die aus
den validierten Deskriptoren ableitbaren CRUD-, Association- und inversen
Relationship-Operationen.

Die fachlichen Regeln und Grenzen der Association-UX stehen in
[docs/association-ux.md](docs/association-ux.md).

## Module

| Modul | Verantwortung |
| --- | --- |
| `core` | ili2db-/ili2c-Reader, Merge, Validierung und framework-agnostische IR |
| `grails-runtime-api` | kleine Grails-freie Descriptor-, Registry-, Command-, Persistence- und Security-Verträge |
| `grails-runtime` | Grails-Plugin, Runtime-Services, Controller-/GSP-Unterstützung |
| `target-grails` | Planer und Generatoren für Grails/GORM |
| `target-django` | kleiner Django/GeoDjango-Target-Spike |
| `cli` | Kommandozeile und Target-Orchestrierung |

Wichtige Architekturentscheidungen:

- [ADR 0001: Model corpus as support contract](docs/decisions/0001-model-corpus-as-support-contract.md)
- [ADR 0002: Plan-before-write generation](docs/decisions/0002-plan-before-write-generation.md)
- [ADR 0003: Runtime validation fail-closed](docs/decisions/0003-runtime-validation-fail-closed.md)

## Tests und Verifikation

Der schnelle, CI-taugliche Lauf enthält Unit-/Integrations-/Snapshot-Tests,
statische Corpus- und Ownership-Prüfungen, Java-17-Toolchain-Prüfung sowie die
fokussierten JaCoCo-Gates:

```bash
./gradlew verificationFast
```

Der lokale vollständige Source-Checkout-Lauf benötigt Grails 7, Docker,
ili2pg und Playwright:

```bash
PATH="/pfad/zu/grails/bin:$PATH" \
ILI2PG_HOME="/pfad/zu/ili2pg-5.5.1" \
./scripts/run-verification-full.sh
```

Die erweiterten Tasks können auch einzeln ausgeführt werden:

```bash
./gradlew :target-grails:grailsRuntimeSmokeTest
./gradlew :target-grails:realIli2dbSmokeTest
./gradlew :target-grails:grailsPostgresContractTest
./gradlew :target-grails:browserE2eTest
```

Die drei Grails-App-Tasks publizieren die lokale Runtime über ihre
`prepareLocalRuntime`-Abhängigkeit selbst. Details zu Required-/Skip-Semantik,
Reports, Modellkorpus und bekannten Einschränkungen stehen in
[docs/verification/README.md](docs/verification/README.md).

## Weitere Dokumentation

- [Getting Started](docs/getting-started.md): ausführbarer Einsteigerablauf
- [Association-UX](docs/association-ux.md): fachliche und technische Grenzen
- [Hybridansatz](docs/why-interlis-model.md): Zuständigkeit von ili2db und ili2c
- [Verifikation](docs/verification/README.md): Testprofile und Reports
- [Scripts](scripts/README.md): vorhandene Hilfsskripte

Die README beschreibt den aktuellen Produktvertrag. Git ist das Archiv für
frühere Implementierungspläne, Fortschrittsberichte und Abnahmeprotokolle.
