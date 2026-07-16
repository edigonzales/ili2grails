# Skripte

## Getting Started

`getting-started.sh` automatisiert den kompletten Getting-Started-Ablauf fuer
ein Beispielmodell:

```bash
./scripts/getting-started.sh simple
./scripts/getting-started.sh advanced
```

Das Skript startet `edit-db`, importiert das ili2pg-Schema und die Demodaten,
liest die Metadaten und erzeugt anschliessend eine Grails-App. Die Anwendung
wird nicht automatisch gestartet.

Ein erneuter Lauf muss bewusst mit `--reset` angefordert werden:

```bash
./scripts/getting-started.sh simple --reset
```

Dabei werden nur das gewaehlte `gs_*`-Schema und die zugehoerigen Artefakte
unter `build/getting-started/` geloescht.

Der erwartete ili2pg-Pfad ist standardmaessig:

```text
/Users/stefan/apps/ili2pg-5.5.1
```

Eine andere Installation kann ueber `ILI2PG_HOME` verwendet werden:

```bash
ILI2PG_HOME=/opt/ili2pg-5.5.1 ./scripts/getting-started.sh simple
```

## Grails-App aus einer bestehenden Datenbank

`create-grails-app.sh` erzeugt eine Grails-App aus einem bereits importierten
INTERLIS-/ili2db-Schema:

```bash
docker compose up -d edit-db
./scripts/create-grails-app.sh styling-lab
```

Anschliessend kann die erzeugte App gestartet werden:

```bash
cd generated-grails/styling-lab
DB_USERNAME=postgres DB_PASSWORD=secret ./gradlew bootRun
```
