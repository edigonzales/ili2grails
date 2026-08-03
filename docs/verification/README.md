# Verifikation

Dieses Dokument ist die Detailreferenz für die reproduzierbaren
Verifikations-Einstiegspunkte. Der aktuelle Produkt- und Buildvertrag steht in
der [README](../../README.md).

## Zwei Profile

Es gibt genau zwei Verifikationsprofile:

### `verificationFast`

```bash
./gradlew verificationFast --no-daemon
```

Enthält alle Unit-Test-Tasks (`core`, `grails-runtime-api`, `grails-runtime`,
`target-grails`, `target-django`, `cli`), die statische Corpus-Verifikation
(`verifyModelCorpusManifest`), die Ownership-Regel-Verifikation
(`verifyGenerationOwnershipRules`), die fokussierten JaCoCo-Class-Gates und
den Java-17-Toolchain-Check. Keine Docker-, Grails- oder ili2pg-Pflicht.
Zielzeit auf einem normalen CI-Runner: deutlich unter zehn Minuten.

### `verificationFull`

```bash
PATH="/pfad/zu/grails/bin:$PATH" \
ILI2PG_HOME="/pfad/zu/ili2pg-5.5.1" \
./scripts/run-verification-full.sh
```

Enthält `verificationFast` plus:

- `:target-grails:grailsRuntimeSmokeTest`
- `:target-grails:realIli2dbSmokeTest`
- `:target-grails:grailsPostgresContractTest`
- `:target-grails:browserE2eTest`
- `:target-grails:writeVerificationSummary`

Alle erweiterten Tasks laufen im Required-Modus (`-P...Required=true`).
`verificationFull` verweigert den Lauf, wenn eine Required-Property fehlt.

## Benötigte Tools

| Tool | für | Bezug |
|---|---|---|
| JDK 17 | alles | Gradle-Toolchain (automatisch) |
| Grails CLI | Smoke/Contract/E2E | `PATH` |
| Docker + Compose | Real-ili2db/Contract/E2E | `docker compose up -d edit-db` |
| ili2pg 5.5.1 | Real-ili2db/Contract/E2E | `-Pili2pgHome=...` oder `ILI2PG_HOME` |
| Playwright-Chromium | Browser-E2E | `~/Library/Caches/ms-playwright` (macOS) bzw. `~/.cache/ms-playwright` |

Die ili2pg-Konfigurationsreihenfolge ist verbindlich:

1. `-Pili2pgHome=...`;
2. `ILI2PG_HOME`;
3. kein lokaler Default.

## Required- und Skip-Semantik

Jeder erweiterte Test besitzt eine Required-Property:

```text
-PgrailsRuntimeSmokeRequired=true
-PrealIli2dbRequired=true
-PcontractTestRequired=true
-PbrowserE2eRequired=true
```

- `required=true`: fehlende Infrastruktur ist ein Testfehler
  (`FAILED_INFRASTRUCTURE`), kein `TestAbortedException`.
- `required=false`: sauberer Skip (`SKIPPED_INFRASTRUCTURE`) mit exakter
  Ursache.

## Reportpfade

| Report | Pfad |
|---|---|
| Verification-Summary | `build/reports/ili2grails-verification/summary.json`, `summary.md` |
| Modellkorpus | `build/reports/model-corpus/corpus-results.json`, `corpus-results.md` |
| Feature-Matrix (committed) | `docs/verification/interlis-feature-matrix.md` |
| Mapping-Contract pro Szenario | `target-grails/build/reports/grails-postgres-contract/<szenario>/` |
| Real-ili2db-Smoke | `build/reports/real-ili2db-smoke/` (Reader-Diagnostics, Query-Metrics) |
| Generationsplan (CLI-Dry-run) | über `--grails-plan-json` / `--grails-plan-markdown` |

## Modellkorpus

`verification/model-corpus.yaml` ist die einzige Wahrheit für unterstützte
Modell-Szenarien: jedes Szenario referenziert ein reales Modell, eine
Feature-Menge und Erwartungen. `verifyModelCorpusManifest` validiert die
Datei statisch (Schema-Version, Eindeutigkeit, Pfadtraversal, widersprüchliche
Erwartungen) und läuft in `verificationFast`.

## Feature-Matrix

`docs/verification/interlis-feature-matrix.md` wird aus dem Corpus generiert.
Ein Persistenzfeature ist nur `SUPPORTED`, wenn ein Szenario mit
`database.required=true` und `mappingContract=true` (ohne
`allowedDifferences`) es belegt. Snapshots allein genügen nicht.
`verifyFeatureMatrixUpToDate` vergleicht die committed Matrix mit der
generierten Ausgabe.

## Mapping-Contract

Der `grailsPostgresContractTest` vergleicht drei unabhängige Sichten pro
Szenario: erwartetes Mapping (Core-IR + Grails-Planer), tatsächliches
Hibernate-Mapping der gestarteten App und reales PostgreSQL-Schema. Ein
unerklärter Mismatch ist ein Testfehler. Einzige Toleranz sind dokumentierte
`allowedDifferences` im Corpus (exakte Code/Entity/Property-Treffer mit
Begründung).

## Generation-Manifest

Jede Generierung schreibt `.ili2grails/generation-manifest.json` im
Zielprojekt (deterministisch, ohne Timestamps/Pfade/Credentials). Das
Manifest ist die Wahrheit für generatorverwaltete Dateien; es wird zuletzt
atomar geschrieben. Zweite identische Generationen sind vollständig
idempotent; benutzerveränderte verwaltete Dateien blockieren den gesamten
Apply.

## Dry-run

```bash
java -jar ... generate <jdbc> <modell> --target grails \
  --grails-output <projekt> \
  --grails-dry-run \
  --grails-plan-json plan.json \
  --grails-plan-markdown plan.md
```

Der Dry-run verändert das Zielprojekt nicht; er schreibt nur die explizit
angeforderten Reportdateien. Bei Blockern ist der Exit-Code ungleich null.

## Umgang mit User-modified Files

- Benutzerveränderte, manifest-verwaltete Dateien blockieren den gesamten
  Apply (`USER_MODIFIED_MANAGED_FILE`).
- `grails-app/views/layouts/main.gsp` ist application-owned. Ein unverändertes
  Grails-Scaffold-Layout darf durch eine kleine Delegation zum app-lokalen
  `ili2grails`-Layout ersetzt werden; Views und Assets des Bootstrap-Themes
  bleiben generatorverwaltet in der Anwendung.
- Legacy-Runtime-Dateien werden nur bei exakt bekanntem SHA-256 gelöscht.
- Ein einzelner Blocker bedeutet: keine Projektdatei wird verändert.

## Bekannte Einschränkungen

- Ternäre Association-Rollen mit erweiterten Zielklassen: der
  Rolle-zu-Attribut-Match löst die ili2pg-Spalte `parcelrole_base_parcel`
  nicht auf (dokumentiert als `allowedDifference` im Corpus).
- Eingebettete STRUCTURE-Referenzen (`BAG {1} OF Structure`) besitzen keine
  physische FK-Spalte; die generierte Property nutzt den GORM-Default-Namen
  (dokumentiert).
- Der Browser-E2E erfordert lokale Playwright-Browser.
