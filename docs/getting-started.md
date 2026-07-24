# Getting Started: INTERLIS CRUD Generator mit Grails

Diese Anleitung ist ein durchgehendes Beginner-Tutorial. Sie startet bei einer
lokalen Docker-Datenbank, erzeugt ili2pg-Schemas, importiert optional Demodaten
und generiert daraus eine lauffaehige Grails-CRUD-App.

Das Getting-Started-Schema wird bewusst ohne technische Basket-Spalten
erzeugt. Die generierte Grails-CRUD-App verwaltet aktuell keinen Basket-Kontext;
ohne diese technischen Spalten bleiben einfache
Create- und Update-Vorgaenge direkt schreibbar. Basket-faehige Schemas bleiben
fuer die spezialisierten ili2db-Smoke-Tests und produktive Integrationen
vorbehalten.

Es gibt zwei Faelle:

- **Fall 1: Simple** - 3 Klassen, zwei 1:n-Beziehungen, keine Geometrien.
- **Fall 2: Advanced** - Structures, BAG/LIST OF Structures, n:m-Beziehung,
  Enumeration, Datums-/Numeriktypen, Referenzen aus Structures und
  Geometrieattribute (`COORD`, `POLYLINE`, `SURFACE`).

Alle Befehle werden aus dem Repo-Root ausgefuehrt. Lege zuerst eine Variable auf
den Repo-Root, damit Gradle-Tasks und CLI-Aufrufe dieselben Pfade verwenden:

```bash
export REPO_ROOT="$(pwd)"
test -f "$REPO_ROOT/settings.gradle"
```

## Voraussetzungen pruefen

```bash
java -version
docker compose version
grails --version
```

Empfohlen ist Java 17 oder 21. Falls lokal mehrere JDKs installiert sind, ist
Java 21 fuer dieses Repo aktuell der robuste Default:

```bash
export JAVA_HOME=/Users/stefan/.sdkman/candidates/java/21.0.7-tem
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

Dieses Tutorial verwendet ili2pg 5.5.1. Der lokale Default-Pfad ist:

```bash
export ILI2PG_HOME="${ILI2PG_HOME:-/Users/stefan/apps/ili2pg-5.5.1}"
test -f "$ILI2PG_HOME/ili2pg-5.5.1.jar"
test -d "$ILI2PG_HOME/libs"
```

Hilfsfunktion fuer alle ili2pg-Aufrufe:

```bash
run_ili2pg() {
  java -cp "$ILI2PG_HOME/ili2pg-5.5.1.jar:$ILI2PG_HOME/libs/*" ch.ehi.ili2pg.PgMain "$@"
}
```

Auf Windows muss der Classpath-Separator in der Hilfsfunktion von `:` auf `;`
gewechselt werden.

## Generator bauen

```bash
./gradlew test
```

Optional kann die CLI-Distribution gebaut werden:

```bash
./gradlew :cli:installDist
```

Die folgenden Beispiele nutzen trotzdem `./gradlew :cli:run --args="..."`, damit
keine installierten CLI-Pfade vorausgesetzt werden.

## Docker-Datenbank starten

Das Repo verwendet in `docker-compose.yml` das Image `sogis/postgis:16-3.5`.
Die Datenbank heisst `edit`, der Benutzer ist `postgres`, das Passwort ist
`secret`, und PostgreSQL ist lokal auf Port `54321` erreichbar.

```bash
docker compose up -d edit-db
docker compose ps edit-db
```

Die JDBC-URL fuer dieses Tutorial:

```text
jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret
```

## Automatisierter kompletter Ablauf

Der komplette Ablauf kann direkt mit dem mitgelieferten Bash-Skript ausgefuehrt
werden. Die Variante `simple` oder `advanced` waehlt das jeweilige Modell. Das
Skript startet die Docker-Datenbank selbst, importiert Schema und Demodaten,
liest die Metadaten und erzeugt die Grails-App. `bootRun` wird nicht automatisch
gestartet.

```bash
./scripts/getting-started.sh simple
```

Fuer das Advanced-Modell:

```bash
./scripts/getting-started.sh advanced
```

Ein erneuter Aufbau erfordert bewusst die explizite Option `--reset`. Sie loescht
nur das ausgewaehlte `gs_*`-Schema und die dazugehoerigen Artefakte unter
`build/getting-started/`:

```bash
./scripts/getting-started.sh simple --reset
```

Wenn ili2pg nicht unter dem lokalen Default-Pfad installiert ist, kann der Pfad
ueber `ILI2PG_HOME` gesetzt werden:

```bash
ILI2PG_HOME=/opt/ili2pg-5.5.1 ./scripts/getting-started.sh simple
```

Die folgenden Abschnitte zeigen denselben Ablauf noch einmal manuell und sind
hilfreich, wenn einzelne Schritte oder ili2pg-Optionen angepasst werden sollen.

## Fall 1: Simples Modell ohne Geometrien

Das Modell liegt in
`docs/getting-started/models/GsSimpleModel.ili`.

Fachlich enthaelt es:

- `Company`
- `Department`
- `Employee`
- 1:n `Company -> Department`
- 1:n `Department -> Employee`

### Variante A: Nur leeres Schema erzeugen

```bash
docker compose exec -T edit-db psql -U postgres -d edit -c "DROP SCHEMA IF EXISTS gs_simple CASCADE"
```

```bash
run_ili2pg \
  --dbhost localhost \
  --dbport 54321 \
  --dbdatabase edit \
  --dbusr postgres \
  --dbpwd secret \
  --defaultSrsCode 2056 \
  --createFk \
  --nameByTopic \
  --strokeArcs \
  --smart2Inheritance \
  --createEnumTabs \
  --modeldir "$REPO_ROOT/docs/getting-started/models" \
  --models GsSimpleModel \
  --dbschema gs_simple \
  --schemaimport
```

### Variante B: Schema erzeugen und Demodaten importieren

```bash
docker compose exec -T edit-db psql -U postgres -d edit -c "DROP SCHEMA IF EXISTS gs_simple CASCADE"
```

```bash
run_ili2pg \
  --dbhost localhost \
  --dbport 54321 \
  --dbdatabase edit \
  --dbusr postgres \
  --dbpwd secret \
  --defaultSrsCode 2056 \
  --createFk \
  --nameByTopic \
  --strokeArcs \
  --smart2Inheritance \
  --createEnumTabs \
  --modeldir "$REPO_ROOT/docs/getting-started/models" \
  --models GsSimpleModel \
  --dbschema gs_simple \
  --schemaimport
```

```bash
run_ili2pg \
  --dbhost localhost \
  --dbport 54321 \
  --dbdatabase edit \
  --dbusr postgres \
  --dbpwd secret \
  --defaultSrsCode 2056 \
  --createFk \
  --nameByTopic \
  --strokeArcs \
  --smart2Inheritance \
  --createEnumTabs \
  --modeldir "$REPO_ROOT/docs/getting-started/models" \
  --models GsSimpleModel \
  --dbschema gs_simple \
  --import "$REPO_ROOT/docs/getting-started/data/GsSimpleModel.xtf"
```

Kurze Kontrolle:

```bash
docker compose exec -T edit-db psql -U postgres -d edit -c "SELECT count(*) AS employees FROM gs_simple.organization_employee"
```

### Metadaten lesen

```bash
./gradlew :cli:run --args="read \
  'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=gs_simple' \
  GsSimpleModel \
  gs_simple \
  --model-file '$REPO_ROOT/docs/getting-started/models/GsSimpleModel.ili' \
  --metadata-json '$REPO_ROOT/build/getting-started/gs-simple/metadata.json' \
  --merge-report '$REPO_ROOT/build/getting-started/gs-simple/merge-report'"
```

### Grails-App erzeugen

Der folgende Befehl erstellt eine neue Grails-App, schreibt Domains/Enums,
kopiert das Bootstrap-Scaffolding und ruft `generate-all` fuer alle generierten
Domains auf.

```bash
rm -rf "$REPO_ROOT/build/getting-started/grails-simple"
```

```bash
./gradlew :cli:run --args="generate \
  'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=gs_simple' \
  GsSimpleModel \
  gs_simple \
  --target grails \
  --model-file '$REPO_ROOT/docs/getting-started/models/GsSimpleModel.ili' \
  --grails-output '$REPO_ROOT/build/getting-started/grails-simple' \
  --grails-init simple-app \
  --grails-package ch.example.gssimple \
  --grails-ui-theme bootstrap \
  --grails-map-editor none \
  --grails-generate-all"
```

Starten:

```bash
cd "$REPO_ROOT/build/getting-started/grails-simple/simple-app"
DB_USERNAME=postgres DB_PASSWORD=secret ./gradlew bootRun
```

Danach im Browser oeffnen:

```text
http://localhost:8080
```

Zurueck ins Repo-Root:

```bash
cd -
```

## Fall 2: Advanced-Modell mit INTERLIS-Sonderfaellen

Das Modell liegt in
`docs/getting-started/models/GsAdvancedModel.ili`.

Fachlich enthaelt es:

- `Owner`, `Asset`, `ServiceRecord`, `Tag`
- `STRUCTURE Inspection`, `Attachment`, `MaintenancePart`
- `LIST {0..*} OF Inspection`
- `BAG {0..*} OF Attachment`
- `BAG {1..*} OF MaintenancePart`
- 1:n `Owner -> Asset`
- 1:n `Asset -> ServiceRecord`
- n:m `Asset <-> Tag`
- `COORD`, `POLYLINE`, `SURFACE`

### Variante A: Nur leeres Schema erzeugen

```bash
docker compose exec -T edit-db psql -U postgres -d edit -c "DROP SCHEMA IF EXISTS gs_advanced CASCADE"
```

```bash
run_ili2pg \
  --dbhost localhost \
  --dbport 54321 \
  --dbdatabase edit \
  --dbusr postgres \
  --dbpwd secret \
  --defaultSrsCode 2056 \
  --createFk \
  --nameByTopic \
  --strokeArcs \
  --smart2Inheritance \
  --createEnumTabs \
  --modeldir "$REPO_ROOT/docs/getting-started/models" \
  --models GsAdvancedModel \
  --dbschema gs_advanced \
  --schemaimport
```

### Variante B: Schema erzeugen und Demodaten importieren

```bash
docker compose exec -T edit-db psql -U postgres -d edit -c "DROP SCHEMA IF EXISTS gs_advanced CASCADE"
```

```bash
run_ili2pg \
  --dbhost localhost \
  --dbport 54321 \
  --dbdatabase edit \
  --dbusr postgres \
  --dbpwd secret \
  --defaultSrsCode 2056 \
  --createFk \
  --nameByTopic \
  --strokeArcs \
  --smart2Inheritance \
  --createEnumTabs \
  --modeldir "$REPO_ROOT/docs/getting-started/models" \
  --models GsAdvancedModel \
  --dbschema gs_advanced \
  --schemaimport
```

```bash
run_ili2pg \
  --dbhost localhost \
  --dbport 54321 \
  --dbdatabase edit \
  --dbusr postgres \
  --dbpwd secret \
  --defaultSrsCode 2056 \
  --createFk \
  --nameByTopic \
  --strokeArcs \
  --smart2Inheritance \
  --createEnumTabs \
  --modeldir "$REPO_ROOT/docs/getting-started/models" \
  --models GsAdvancedModel \
  --dbschema gs_advanced \
  --import "$REPO_ROOT/docs/getting-started/data/GsAdvancedModel.xtf"
```

Kurze Kontrolle:

```bash
docker compose exec -T edit-db psql -U postgres -d edit -c "SELECT count(*) AS assets FROM gs_advanced.inventory_asset"
docker compose exec -T edit-db psql -U postgres -d edit -c "SELECT count(*) AS asset_tags FROM gs_advanced.inventory_assettag"
```

### Metadaten lesen

```bash
./gradlew :cli:run --args="read \
  'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=gs_advanced' \
  GsAdvancedModel \
  gs_advanced \
  --model-file '$REPO_ROOT/docs/getting-started/models/GsAdvancedModel.ili' \
  --metadata-json '$REPO_ROOT/build/getting-started/gs-advanced/metadata.json' \
  --merge-report '$REPO_ROOT/build/getting-started/gs-advanced/merge-report'"
```

### Grails-App erzeugen

```bash
rm -rf "$REPO_ROOT/build/getting-started/grails-advanced"
```

```bash
./gradlew :cli:run --args="generate \
  'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=gs_advanced' \
  GsAdvancedModel \
  gs_advanced \
  --target grails \
  --model-file '$REPO_ROOT/docs/getting-started/models/GsAdvancedModel.ili' \
  --grails-output '$REPO_ROOT/build/getting-started/grails-advanced' \
  --grails-init advanced-app \
  --grails-package ch.example.gsadvanced \
  --grails-ui-theme bootstrap \
  --grails-map-editor openlayers \
  --grails-default-srid 2056 \
  --grails-generate-all"
```

Starten:

```bash
cd "$REPO_ROOT/build/getting-started/grails-advanced/advanced-app"
DB_USERNAME=postgres DB_PASSWORD=secret ./gradlew bootRun
```

Danach im Browser oeffnen:

```text
http://localhost:8080
```

Zurueck ins Repo-Root:

```bash
cd -
```

## Ergebnisdateien

Nach den Befehlen liegen die wichtigsten Artefakte hier:

- `build/getting-started/gs-simple/metadata.json`
- `build/getting-started/gs-simple/merge-report/`
- `build/getting-started/grails-simple/simple-app/`
- `build/getting-started/gs-advanced/metadata.json`
- `build/getting-started/gs-advanced/merge-report/`
- `build/getting-started/grails-advanced/advanced-app/`

## Aufraeumen

Schemas loeschen:

```bash
docker compose exec -T edit-db psql -U postgres -d edit -c "DROP SCHEMA IF EXISTS gs_simple CASCADE"
docker compose exec -T edit-db psql -U postgres -d edit -c "DROP SCHEMA IF EXISTS gs_advanced CASCADE"
```

Grails-/Report-Artefakte entfernen:

```bash
rm -rf "$REPO_ROOT/build/getting-started"
```

Docker-Container stoppen:

```bash
docker compose down
```
