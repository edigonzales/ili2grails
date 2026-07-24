# INTERLIS CRUD Generator – Metadata Reader

Der **INTERLIS CRUD Generator** liest Metadaten aus einer ili2db-Datenbank und einem INTERLIS-Modell, baut daraus ein internes Metamodell auf und liefert zusätzlich eine **Beispielimplementierung für Grails** (Domains, Enums). Ein kleiner Django/GeoDjango-Target-Spike validiert die Framework-Unabhängigkeit der Core-IR und kann per CLI eine `models.py` erzeugen. Die Software bleibt jedoch im Kern **software- und framework-agnostisch** – das Metamodell dient als Basis für weitere Generatoren und Integrationen.

## Inhalt
- [Ziel & Funktionsumfang](#ziel--funktionsumfang)
- [Voraussetzungen](#voraussetzungen)
- [Installation & Build](#installation--build)
- [Getting Started Tutorial](#getting-started-tutorial)
- [Schnellstart (CLI)](#schnellstart-cli)
- [Grails-Projekt starten](#grails-projekt-starten)
- [Benutzeranleitung (Detail)](#benutzeranleitung-detail)
- [Programmatische Nutzung](#programmatische-nutzung)
- [Ausgabe verstehen](#ausgabe-verstehen)
- [Architektur & Design-Entscheidungen](#architektur--design-entscheidungen)
- [Bootstrap UI-Metadaten (Phase 0)](#bootstrap-ui-metadaten-phase-0)
- [Bootstrap Application Shell (Phase 1)](#bootstrap-application-shell-phase-1)
- [Bootstrap Domain-Liste (Phase 2)](#bootstrap-domain-liste-phase-2)
- [Bootstrap Domain Workspace (Phase 3)](#bootstrap-domain-workspace-phase-3)
- [Bootstrap Create/Edit-Formulare und Editor-UX (Phase 4)](#bootstrap-createedit-formulare-und-editor-ux-phase-4)
- [Bootstrap Fachliche Multi-Domain-Workspaces (Phase 5)](#bootstrap-fachliche-multi-domain-workspaces-phase-5)
- [Bootstrap Atomarer Multi-Domain-Save (Phase 6)](#bootstrap-atomarer-multi-domain-save-phase-6)
- [Projektstruktur](#projektstruktur)
- [Tests](#tests)
- [Dependencies](#dependencies)
- [Weitere Dokumente](#weitere-dokumente)

## Ziel & Funktionsumfang
Der Metadata Reader liefert ein vollständiges, framework-agnostisches **Metamodell** und stellt eine **Grails-Beispielimplementierung** bereit:
- Klassen/Tabellen, Attribute/Spalten, Constraints
- Beziehungen aus ili2db-FKs und ili2c-Semantik (Associations, Rollen, Reference, Composition)
- Enumerationen inkl. Reihenfolge und Erweiterbarkeit
- Dokumentation/Labels

Die Metadaten kommen aus zwei Quellen:
1. **ili2db Metatabellen** (Mapping, physische DB-Struktur)
2. **ili2c Compiler** (Semantik, Constraints, Dokumentation)

## Voraussetzungen
- **Java 17+**
- Zugriff auf eine **ili2db**-Datenbank (alle ili2db-Flavours sind grundsätzlich möglich; **getestet ist aktuell nur ili2pg**). Die von Grails verwendete Hibernate-Version muss den Datenbank-Flavor unterstützen.
- Eine passende **.ili**-Modelldatei **oder** Zugriff auf ein INTERLIS-Repository (z. B. models.interlis.ch)
- Für Grails-Ausgabe/Start: **Grails SDK** und ein Grails-Projekt

Prüfen:
```bash
java -version
```

## Installation & Build
```bash
./gradlew build
```

## Getting Started Tutorial
Für absolute Beginner gibt es ein Schritt-für-Schritt-Tutorial mit Docker-DB,
ili2pg-Schemaimport, optionalem Seed-Datenimport und Grails-App-Erzeugung:
[docs/getting-started.md](docs/getting-started.md).

Der komplette Ablauf kann direkt automatisiert werden:

```bash
./scripts/getting-started.sh simple
```

Mit `advanced` wird das Advanced-Modell verwendet; fuer einen bewussten
Neuaufbau steht `--reset` zur Verfuegung. Der ili2pg-Pfad kann ueber
`ILI2PG_HOME` ueberschrieben werden.

## Schnellstart (CLI)
**PostgreSQL:**

```bash
docker compose up
```

```bash
java -jar ili2pg-5.5.1.jar --dbhost localhost:54321 --dbdatabase edit --dbusr postgres --dbpwd secret --defaultSrsCode 2056 --createFk --nameByTopic --strokeArcs --smart2Inheritance --createEnumTabs --modeldir test-models --models SimpleAddressModel --dbschema sa --schemaimport
```

```bash
./gradlew :cli:run --args="read \
  'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=sa' \
  SimpleAddressModel \
  sa \
  --model-file test-models/SimpleAddressModel.ili"
```

**Repository-Lookup (nur Modellname, Datei wird aus Repos geholt):**
```bash
./gradlew :cli:run --args="read \
  'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=sa' \
  DM01AVCH24LV95D \
  public \
  --model-repos https://models.interlis.ch/"
```

**Parameter (lokale Datei):**
1. JDBC-URL (inkl. User/Passwort)
2. INTERLIS-Modellname
3. (Optional) DB-Schema
4. `--model-file <file>` für die `.ili`-Datei

**Parameter (Repository-Lookup):**
1. JDBC-URL (inkl. User/Passwort)
2. INTERLIS-Modellname
3. (Optional) DB-Schema
4. (Optional) `--model-repos` (Repository-Liste)

**Grails CRUD-Generierung (optional):**
```bash
./gradlew :cli:run --args="generate \
  'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=sa' \
  SimpleAddressModel \
  sa \
  --target grails \
  --model-file test-models/SimpleAddressModel.ili \
  --grails-output ./generated-grails \
  --grails-package ch.example.demo"
```

**Multi-Target-Generierung (Grails + Django `models.py`):**
```bash
./gradlew :cli:run --args="generate \
  'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=sa' \
  SimpleAddressModel \
  sa \
  --target grails \
  --target django \
  --model-file test-models/SimpleAddressModel.ili \
  --metadata-json build/metadata/SimpleAddressModel.json \
  --grails-output ./generated-grails \
  --grails-package ch.example.demo \
  --django-output ./generated-django \
  --django-app simple_app"
```

Weitere Optionen:
- Subcommands: `read` liest/zeigt Metadaten, `generate` erzeugt ausgewählte Targets
- `--target grails|django` (bei `generate`, wiederholbar; Targets laufen in der angegebenen Reihenfolge)
- `--model-file <file>` (optional: explizite `.ili`-Datei)
- `--model-repos <r1;r2>` (optional: Repository-Liste für die Modellauflösung)
- `--metadata-json <file>` (optional: schreibt eine deterministische JSON-Ausgabe der Core-IR)
- `--merge-report <dir>` (optional: schreibt Merge-Diagnostik für Relationships und Association-Rollen als Markdown und JSON)
- `--grails-init [appName]` (optional: erzeugt ein Grails-Projekt im Zielverzeichnis; mit `appName` wird ein Unterordner erstellt)
- `--grails-domain-package` (Default: Basis-Package)
- `--grails-enum-package` (Default: `<Basis-Package>.enums`)
- `--grails-ui-theme <default|bootstrap>` (Default: `default`)
- `--grails-map-editor <none|openlayers>` (Default: `openlayers` bei `bootstrap`, sonst `none`)
- `--grails-default-srid <int>` (Default: `2056`)
- `--grails-generate-all` (nur mit `--grails-init`, ruft `./grailsw generate-all` für jede Domain auf)
- `--grails-association-ui <auto|off|read-only|editable>` (Default: `auto`; steuert die Association-UX der generierten App)
- `--grails-association-page-size <1..100>` (Default: `10`; Seitengrösse für Association-Listen)
- `--grails-association-navigation <auto|show|hide>` (Default: `auto`; Sichtbarkeit technischer Association-Controller in der Navigation)
- `--grails-language <de-CH|en>` (Default: `de-CH`; Sprache der generierten Bootstrap-Oberfläche)
- `--django-output <dir>` und `--django-app <python_package>` (für `--target django`; schreibt `<dir>/<app>/models.py`)

## Grails-Projekt starten
Der Generator schreibt Artefakte in ein bestehendes Grails-Projekt (oder in ein neu erzeugtes). Die Dateien landen in:
- `grails-app/domain/...` (Domains)
- `src/main/groovy/...` (Enums)

### 1) Grails-App erstellen (falls noch nicht vorhanden)
Manuell (Grails CLI):
```bash
grails create-app my-grails-app
```

Für die wiederholbare Erzeugung einer Test-App mit dem Generator kann das
Hilfsscript verwendet werden. Der erste Parameter ist der gewünschte App-Name;
standardmässig wird das Bootstrap-Theme mit OpenLayers verwendet:

```bash
./scripts/create-grails-app.sh styling-lab
```

Das Script baut zuerst `:cli:installDist` und erzeugt danach die App unter
`generated-grails/styling-lab`. Modell, Datenbank, Package und Theme können über
die im Script dokumentierten Umgebungsvariablen überschrieben werden, zum Beispiel:

```bash
MODEL_FILE=test-models/AssociationCases.ili \
MODEL_NAME=AssociationCases \
DB_SCHEMA=association_cases \
BASE_PACKAGE=ch.example.association \
./scripts/create-grails-app.sh association-lab
```

Alternativ kann der Generator das Projekt anlegen, wenn im Zielverzeichnis noch keine Grails-Struktur vorhanden ist (bei `appName` wird ein Unterordner erzeugt):
```bash
./gradlew :cli:run --args="generate \
  'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=sa' \
  SimpleAddressModel \
  sa \
  --target grails \
  --model-file test-models/SimpleAddressModel.ili \
  --grails-output ./generated-grails \
  --grails-init my-grails-app \
  --grails-package ch.example.demo"
```
Der Scaffold-Schritt wird blockiert, wenn im Zielverzeichnis bereits `build.gradle`, `settings.gradle` oder `grails-app/` vorhanden sind.

Hinweis: Der Generator ergänzt in `build.gradle` automatisch die JTS-, PostgreSQL- und WebJar-Dependencies für Bootstrap, OpenLayers und proj4, sobald eine Grails-App vorhanden ist.
Zusätzlich setzt der Generator in `grails-app/conf/application.yml` die `development`-Datenbank auf die per CLI übergebene JDBC-URL, ergänzt `currentSchema` (falls gesetzt), stellt `dbCreate` auf `none` und setzt den PostgreSQL-Hibernate-Dialekt.
Wenn die JDBC-URL `user`, `username` oder `password` enthält, entfernt der Generator diese Werte aus der URL und schreibt stattdessen `DB_USERNAME`/`DB_PASSWORD`-Platzhalter in die Grails-Konfiguration.
Für `production` schreibt der Generator keine Demo-URL, sondern erwartet explizit `DB_URL`, `DB_USERNAME` und `DB_PASSWORD`; fehlende Werte sollen beim Start sichtbar fehlschlagen.
Ist Geometrie aktiviert (Map-Editor `openlayers` oder Geometrie-Felder im Modell), ergänzt der Generator zusätzlich `hibernate-spatial`, setzt den Spatial-Dialekt und schreibt `interlis.geometry.defaultSrid`.

### 2) CRUD-Artefakte generieren
```bash
./grailsw generate-all Address
./grailsw generate-all Person
```
Alternativ kann `--grails-generate-all` verwendet werden, um diesen Schritt automatisch für alle generierten Domains auszuführen.
Wenn `--grails-ui-theme bootstrap` gesetzt ist, kopiert der Generator vor `generate-all` automatisch das Overlay (Scaffolding-Templates, Layout, Assets) nach:
- `src/main/templates/scaffolding`
- `grails-app/views/layouts/main.gsp`
- `grails-app/assets/javascripts/ili-geometry-editor.js`
- `grails-app/assets/javascripts/ili-form-ux.js`
- `grails-app/assets/stylesheets/ili-modern.css`

Der Ablauf ist damit: `--grails-init` → Overlay kopieren → Domains/Enums schreiben → optional `generate-all`.
Hinweis: `--grails-ui-theme carbon` wird nicht mehr unterstützt und muss auf `bootstrap` umgestellt werden.

### Sprache der generierten Bootstrap-App

Die Bootstrap-Oberfläche ist standardmässig Schweizer Hochdeutsch (`de-CH`). Mit
`--grails-language en` wird eine englische App generiert. Die Auswahl erfolgt nur
bei der Generierung; ein Laufzeit-Umschalter ist derzeit nicht enthalten.

Der Generator schreibt die verwalteten Übersetzungen in
`grails-app/i18n/messages_de_CH.properties` bzw. `messages_en.properties` und
ergänzt die gewählte Variante zusätzlich in `messages.properties`, ohne
projektspezifische Message-Keys zu überschreiben. `html lang`, die feste
Standard-Locale und die Domain-Label-Auflösung verwenden dieselbe Auswahl. Für
Domain-Labels gilt der Fallback: gewählte Sprache, `de-CH`, `de`, `en`, danach
der technische Domain-Name.

Die Listen verwenden die standardisierte Pagination-Anzeige **Zeilen pro Seite**
rechts neben der Navigation; der Treffertext steht darunter. Suche, Filter und
Sortierung bleiben beim Ändern der Seitengrösse erhalten, der Seitenoffset wird
auf den Anfang zurückgesetzt.

### 3) Grails-App starten
```bash
cd /path/to/my-grails-app
./gradlew bootRun
# Alternativ:
grails run-app
```
Die DB-Verbindung kommt aus der Grails-Konfiguration in `grails-app/conf/application.yml`
(Properties `dataSource.url`, `dataSource.username`, `dataSource.password`). Bei generierten lokalen Demo-Apps mit Credentials in der JDBC-URL müssen die Umgebungsvariablen gesetzt sein:
```bash
DB_USERNAME=postgres DB_PASSWORD=secret ./gradlew bootRun
```

Für den Production-Start müssen alle drei Verbindungswerte gesetzt werden; `DB_URL`
enthält dabei auch das Schema, falls die App auf ein ili2pg-Schema zeigen soll:
```bash
DB_URL='jdbc:postgresql://localhost:54321/edit?currentSchema=sa' \
DB_USERNAME=postgres \
DB_PASSWORD=secret \
./gradlew -Dgrails.env=prod bootRun
```

## Benutzeranleitung (Detail)
### 1) Datenbank vorbereiten
Die Datenbank muss mit **ili2db** befüllt sein – inklusive Metatabellen. Der Reader nutzt u. a.:
- `t_ili2db_classname` (Klassen/Tabellen-Mapping)
- `t_ili2db_attrname` (Attribute/Spalten-Mapping)
- `t_ili2db_inheritance` (Vererbung)
- `t_ili2db_trafo` (Transformationsstrategien)
- `t_ili2db_column_prop` (Constraints/Properties)
- Der Primary Key ist immer `t_id`/`T_id` und wird zusätzlich ergänzt, da er nicht in `t_ili2db_attrname` enthalten ist.

### 2) INTERLIS-Modell bereitstellen
Entweder eine lokale `.ili`-Datei angeben **oder** das Modell via Repository auflösen.  
Bei Repository-Lookup muss der Modellname die gleiche Modellversion widerspiegeln wie der ili2db-Import.  
Repositories können mit `--model-repos` gesetzt werden (Standard: models.interlis.ch).

### 3) Programm starten
Nutzen Sie die Beispiele aus dem Schnellstart. Bei Bedarf kann das Schema explizit gesetzt werden (z. B. `public`).

### 4) Ergebnis interpretieren
Die Ausgabe zeigt:
- Modellname, Schema, Versionsinfos
- Klassen und Attribute inkl. Typen, Constraints, Enums
- Beziehungen mit Quelle/Semantik (`ILI2DB_FK`, `REFERENCE_ATTRIBUTE`, `COMPOSITION_ATTRIBUTE`, `ASSOCIATION_ROLE`)

Optional kann die kanonische IR als JSON geschrieben werden:
```bash
./gradlew :cli:run --args="read \
  'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=sa' \
  SimpleAddressModel \
  sa \
  --model-file test-models/SimpleAddressModel.ili \
  --metadata-json build/metadata/SimpleAddressModel.json"
```

Die JSON-Ausgabe ist stabil sortiert und eignet sich für Golden-Tests und weitere Generatoren.

Optional kann zusätzlich ein Merge-Report für Relationships geschrieben werden:
```bash
./gradlew :cli:run --args="read \
  'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=sa' \
  SimpleAddressModel \
  sa \
  --model-file test-models/SimpleAddressModel.ili \
  --merge-report build/reports/metadata-merge"
```

Der Markdown-Report ist für manuelle Reviews gedacht. Der JSON-Report enthält dieselben
Kategorien maschinenlesbar und eignet sich für automatisierte Checks. Neben der
kompatiblen Relationship-Sicht enthält der Report auch eine Association-Role-Sicht
aus `AssociationMetadata`, damit Rollen, physische Spaltennamen und Merge-Confidence
direkt prüfbar sind.

## Programmatische Nutzung
```java
import ch.interlis.generator.metadata.MetadataReader;
import ch.interlis.generator.model.*;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;

public class ExampleUsage {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/mydb", "user", "password");

        MetadataReader reader = new MetadataReader(
            conn,
            new File("models/MeinModell.ili"),
            "public",
            null
        );

        ModelMetadata metadata = reader.readMetadata("MeinModellName");

        for (ClassMetadata clazz : metadata.getAllClasses()) {
            System.out.println("Klasse: " + clazz.getSimpleName());
            for (AttributeMetadata attr : clazz.getAllAttributes()) {
                System.out.println("  - " + attr.getName()
                    + " : " + attr.getCoreType()
                    + " (Java: " + attr.getJavaType() + ")");
            }
        }

        conn.close();
    }
}
```

**Repository-Lookup (ohne lokale Datei):**
```java
MetadataReader reader = new MetadataReader(
    conn,
    null,
    "public",
    List.of("https://models.interlis.ch/")
);

ModelMetadata metadata = reader.readMetadata("DM01AVCH24LV95D");
```

## Ausgabe verstehen
Beispielauszug:
```
INTERLIS Model Metadata
Model Name:     SimpleAddressModel
Schema:         PUBLIC
ILI Version:    2.3
ili2db Version: 4.9.1

CLASSES:
■ SimpleAddressModel.Addresses.Address
  Table:        address
  Attributes:
    ◦ street       : String [astreet]      NOT NULL (100)
    ◦ status       : String [status]
      → Enum: SimpleAddressModel.Addresses.AddressStatus
```

**Legende:**
- `■` Klasse
- `◦` Attribut
- `NOT NULL` Pflichtfeld
- `(100)` Maximale Länge

## Architektur & Design-Entscheidungen
### Hybrid-Ansatz: ili2db + ili2c
**Warum:**
- **ili2db** liefert exakte Tabellen-/Spaltennamen und Mappings, ohne Modellkompilierung.
- **ili2c** liefert Semantik: Constraints, Doku, Labels, Units, Enums.
- **Kombiniert** entsteht ein vollständiges Metamodell für die Code-Generierung.

**Ablauf:**
1. ili2db-Metatabellen lesen
2. INTERLIS-Modell kompilieren (ili2c)
3. Daten mergen → vollständige Metadaten

### Metamodell-Prinzipien
- Framework-agnostisch (Grails, Spring, etc.)
- Core-first: `ModelMetadata` ist die kanonische IR, Grails ist nur ein Target
- Erweiterbar für weitere Metadaten und Generatoren
- Separiert von ili2db/ili2c-Implementierungen
- Grails-Ausgabe als **Beispielimplementierung** (Domains/Enums), nicht als exklusives Ziel
- Django/GeoDjango-Ausgabe als **Target-Spike** zur Validierung der Core-IR, nicht als produktionsreife App-Generierung

### Core-IR / JSON-Vertrag
Die Core-IR ist der stabile Vertrag zwischen Metadata Reader und Generator-Targets.
Die JSON-Ausgabe von `--metadata-json` bildet diesen Vertrag deterministisch ab und
wird über Golden-Tests abgesichert. Generatoren sollen aus dieser IR lesen und keine
ili2db-/ili2c-spezifischen Details direkt nachbauen.

**Kompatibilitätsregeln:**
- Der JSON-Vertrag wurde im aktuellen SNAPSHOT bewusst geändert: Attribut-`javaType` steht nicht mehr top-level, sondern als `targetHints.javaType`.
- Weitere bestehende JSON-Feldnamen und Semantik bleiben kompatibel; Erweiterungen erfolgen additiv.
- Attribute behalten die bisherigen Constraint-Felder (`mandatory`, `maxLength`, `minValue`, `maxValue`, `cardinalityMin`, `cardinalityMax`, `ordered`) und schreiben zusätzlich das additive Objekt `constraints`.
- Fehlende optionale Felder bedeuten `unbekannt` oder `nicht vorhanden`, nicht automatisch `false`.
- Reihenfolgen in JSON sind deterministisch sortiert; Generatoren sollen sich trotzdem fachlich an Namen/IDs orientieren.
- Unbounded Cardinality wird in Java und JSON als `-1` ausgegeben.
- Target-spezifische Namen, Packages, Controller- oder View-Pfade bleiben außerhalb der Core-IR und werden pro Target abgeleitet.

Beispiel eines Attribut-Ausschnitts:
```json
{
  "name": "Street",
  "iliType": "TextType",
  "coreType": "TEXT",
  "targetHints": {
    "javaType": "String"
  },
  "mandatory": true,
  "maxLength": 100,
  "constraints": {
    "required": true,
    "maxLength": 100,
    "minInclusive": null,
    "maxInclusive": null,
    "precision": null,
    "scale": null,
    "cardinalityMin": null,
    "cardinalityMax": null,
    "ordered": false
  }
}
```

| Objekt | Wichtigste Felder | Semantik / Herkunft | Stabilität |
| --- | --- | --- | --- |
| `ModelMetadata` | `modelName`, `schemaName`, `iliVersion`, `modelVersion`, `ili2dbVersion`, `settings`, `classes`, `associations`, `enums`, `relationships` | Wurzel der IR; kombiniert ili2db-Mapping, ili2c-Semantik und Metadaten zum Import. | Stabiler Einstiegspunkt. Neue Top-Level-Felder nur additiv. |
| `ClassMetadata` | `name`, `simpleName`, `topicName`, `tableName`, `sqlName`, `kind`, `abstract`, `baseClass`, `inheritanceStrategy`, `attributes`, `labels` | INTERLIS-Klasse, Structure oder Association plus physisches Tabellenmapping, falls vorhanden. | `name` ist die fachliche Identität; `tableName`/`sqlName` sind physische DB-Details. |
| `AssociationMetadata` | `name`, `associationClass`, `physicalTable`, `physicalSqlName`, `roles`, `attributes` | Kanonische IR für INTERLIS-Associations; Rollen, Kardinalitäten, eigene Attribute und physische Abbildung bleiben zusammen. | Additiver Core-Baustein. `RelationshipMetadata` bleibt für v1 kompatibel erhalten. |
| `AssociationRoleMetadata` | `name`, `targetClass`, `oppositeRoleName`, `cardinality`, Flags, physische Felder, Merge-Diagnostik | Rolle innerhalb einer Association. Physische FK-/Role-Spalten gehören zur Rolle, nicht zu den Association-Attributen. | Targets sollen Rollen bevorzugt über `AssociationMetadata` lesen und Relationships als Fallback behandeln. |
| `AttributeMetadata` | `name`, `qualifiedName`, `columnName`, `sqlName`, `iliType`, `domainName`, `coreType`, `targetHints.javaType`, `dbType`, `mandatory`, `precision`, `scale`, `constraints`, `geometry*`, `enumType`, `unit`, `referencedClass` | Attribut-/Spalten-IR mit Constraints, Typ- und Referenzinformationen. `coreType` ist der framework-agnostische Typvertrag; `constraints` bündelt Pflichtigkeit, Grenzen, Dezimalpräzision, Kardinalität und `ordered`. Geometrien führen zusätzlich `geometryKind`, `geometrySrid`, `geometryHasZ`, `geometryHasM` und `allowEmptyGeometry`, soweit bekannt. `targetHints.javaType` ist nur ein Java-/Grails-Hinweis. | `coreType`, `constraints` und die Geometrie-Metadaten sind für Generatoren maßgeblich. Legacy-Constraint-Felder bleiben aus Kompatibilitätsgründen erhalten. `targetHints` bleiben optional und target-spezifisch. |
| `RelationshipMetadata` | `name`, `sourceClass`, `targetClass`, `type`, `semanticKind`, Rollen/FK-Felder, `cardinality`, Flags, Merge-Diagnostik | Beziehung als First-Class-IR aus ili2db-FK und/oder ili2c-Semantik. | `semanticKind` und Klassen-/Rollenfelder sind für Targets maßgeblich; Diagnosefelder erklären die Zusammenführung. |
| `EnumMetadata` | `name`, `simpleName`, `extendable`, `baseEnum`, `values[].iliCode`, `dispName`, `seq`, `labels` | INTERLIS-Enumeration inkl. Reihenfolge, Erweiterbarkeit und Display-/Label-Daten. | `iliCode` und `seq` sind stabil für Generatoren; Ziel-Identifier werden target-spezifisch erzeugt. |

`coreType` verwendet aktuell diese Werte:
`TEXT`, `MTEXT`, `NUMERIC`, `BOOLEAN`, `DATE`, `DATETIME`, `TIME`, `ENUM`,
`COORD`, `POLYLINE`, `SURFACE`, `REFERENCE`, `COMPOSITION`, `OBJECT`, `UNKNOWN`.

Geometrieattribute verwenden zusätzlich den typisierten Geometrievertrag
`POINT`, `MULTIPOINT`, `LINESTRING`, `MULTILINESTRING`, `POLYGON`,
`MULTIPOLYGON` oder `GEOMETRY`. Die JSON-Ausgabe bleibt kompatibel und schreibt
`geometryKind` weiterhin als String.

### Relationship-Semantik
- ili2db-Beziehungen liefern physische Namen: FK-Spalten, Zielspalten und Tabellenmapping.
- ili2c-Beziehungen liefern fachliche Semantik: Association-Rollen, Kardinalitäten, `ORDERED`, `EXTERNAL`, Reference- und Composition-Attribute.
- Beim Merge gewinnen ili2db-Namen für die physische DB-Struktur und ili2c-Felder für fachliche Semantik.
- Unbounded Cardinality wird in Java und JSON als `-1` ausgegeben.

`semanticKind` beschreibt die fachliche Quelle der Beziehung:

| `semanticKind` | Bedeutung | Target-Hinweis |
| --- | --- | --- |
| `ILI2DB_FK` | Physische FK-Beziehung aus ili2db-Metatabellen. | Für DB-Mapping und To-One-Properties verwenden, aber ohne ili2c-Semantik vorsichtig interpretieren. |
| `REFERENCE_ATTRIBUTE` | INTERLIS `REFERENCE TO` aus ili2c. | Fachliche Referenz; physische Spalte kommt nur aus einem Merge mit ili2db. |
| `COMPOSITION_ATTRIBUTE` | INTERLIS `BAG/LIST OF` Composition aus ili2c. | Kardinalität entscheidet über To-One vs. Collection; Composition kann Ziel-Structures generationserheblich machen. |
| `ASSOCIATION_ROLE` | Rolle einer INTERLIS Association. | In v1 als Rolle auf der Association-Klasse interpretieren; inverse Collections und komplexe Association-Semantik bleiben bewusst konservativ. |

`ASSOCIATION_ROLE` bleibt als kompatible Relationship-Sicht erhalten. Die
kanonische Quelle für INTERLIS-Associations ist jedoch `AssociationMetadata`:
Targets können daraus explizite Association-Klassen mit Rollen-Properties ableiten,
ohne Rollen aus losen Relationships rekonstruieren zu müssen.

### Core-IR Merge-Diagnostik
Relationships enthalten zusätzlich Diagnosefelder, damit der Merge von ili2db- und
ili2c-Informationen nachvollziehbar bleibt:

| Feld | Bedeutung |
| --- | --- |
| `physicalName` | Physischer ili2db-Name, aktuell die FK-Spalte der DB-Beziehung. |
| `semanticName` | Fachlicher ili2c-Name, z. B. Attribut- oder Rollenname inklusive Scope. |
| `mergeReason` | Grund des aktuellen Zustands: `ILI2DB_ONLY`, `ILI2C_ONLY`, `EXACT_NAME`, `EXACT_SOURCE_ATTRIBUTE`, `EXACT_TARGET_ROLE` oder `NORMALIZED_TOKEN`. |
| `mergeConfidence` | Einschätzung der Match-Qualität: `NONE`, `EXACT` oder `MEDIUM`. |
| `mergeToken` | Normalisierter Token, der bei heuristischen Matches den Ausschlag gab. |

`NORMALIZED_TOKEN` ist bewusst als mittlere Confidence markiert: dieser Pfad ist
praktisch für reale ili2db-Spalten wie `person_id`, bleibt aber der kritischste
Punkt für Debugging bei großen Modellen.

Der Merge-Report fasst sowohl `RelationshipMetadata` als auch Association-Rollen
aus `AssociationMetadata` zusammen. Für Association-Rollen werden unter anderem
Association, Rolle, Zielklasse, `physicalName`, `semanticName`, `mergeReason` und
`mergeConfidence` ausgegeben. Die CLI-Option bleibt unverändert: `--merge-report`
schreibt weiterhin je Modell eine Markdown- und eine JSON-Datei.

### Grails Relationship-/Structure-Mapping
- Grails nutzt eine interne `GrailsRelationshipMapper`-Schicht statt roher Relationship-Listen.
- Association-Rollen werden bevorzugt aus `AssociationMetadata` gelesen; `ASSOCIATION_ROLE`-Relationships bleiben Fallback.
- Das Relationship-Matching berücksichtigt `sourceAttribute`, `targetRoleName` und `physicalName`, damit gemergte ili2db-Spaltennamen wie von Django erkannt werden.
- `CLASS` und `ASSOCIATION` werden generiert, wenn sie nicht abstrakt sind.
- `STRUCTURE` wird nur als Domain generiert, wenn sie physisch gemappt ist (`tableName`/`sqlName`) oder Ziel einer `COMPOSITION_ATTRIBUTE` ist.
- Normale `ILI2DB_FK`- und `REFERENCE_ATTRIBUTE`-Beziehungen werden als typisierte Properties ausgegeben, erzeugen aber kein automatisches `belongsTo`.
- `COMPOSITION_ATTRIBUTE` erzeugt bei `max > 1` oder `max = -1` ein `hasMany`; bei `max = 1` eine einfache Ziel-Property.
- `belongsTo` wird nur für physisch vorhandene Composition-FKs ausgegeben. Der Generator erfindet dafür keine synthetischen DB-Spalten.
 - Association-Rollen werden in v1 als Properties auf der Association-Domain modelliert; inverse `hasMany` auf den Zielklassen und direkte Many-to-Many-Abbildungen bleiben bewusst aus.

#### Association-UX: Quick-Link (binäre Associations)
- Für binäre `LINK_ENTITY`-Associations ohne eigene Attribute (vom Planner als `QUICK` klassifiziert) bietet die generierte App direktes Hinzufügen und Entfernen aus der Perspektive eines beteiligten Fachobjekts.
- Auf der Show-Seite erscheint pro Kontext ein Related-Abschnitt mit serverseitigem Autocomplete (**Zuordnen**) und einem **Entfernen**-Button je Zeile.
- Mutationen laufen ausschliesslich über `POST` (`associationCreate`) und `DELETE` (`associationDelete`); es gibt keine GET-Mutation und keine freie Return-URL.
- Serverseitig geprüft werden: Context-Gültigkeit, Owner-Zugehörigkeit (`fixedRoleProperty.id == participantId`, schützt gegen Manipulation über falschen Owner), zulässige Zielrolle, binäre Min/Max-Kardinalität und identische Duplikate.
- Das Löschen entfernt ausschliesslich die Association-Domain (den Link); Zielobjekte bleiben immer erhalten. Kompositions- und Attribut-Associations sind nicht Quick-Link-fähig und verwenden weiterhin das Association-CRUD.
- Mit `--grails-association-ui read-only` oder `off` werden alle Schreibpfade deaktiviert (Registry `writable=false`, `createMode=NONE`).

#### Association-UX: Kontextuelle Formulare (attributierte / n-äre / Selbst-Assoziationen)
- Für `CONTEXTUAL_FORM` und `NARY_CONTEXTUAL_FORM`-Assoziationen (mit eigenen Attributen, Spezialsemantik oder >2 Rollen) wird die bestehende Association-Domain kontextuell genutzt.
- Auf der Show-Seite erscheint ein **Hinzufügen**-Button, der auf den Association-Controller mit `associationContext` und `associationOwnerId` verweist.
- Die feste Teilnehmerrolle wird im Formular vorgefüllt und ist nicht editierbar; sie wird serverseitig nach jedem `bindData` erneut gesetzt (Mass-Assignment-Schutz).
- Übrige Rollen werden über den gemeinsamen Autocomplete-Mechanismus ausgewählt; eigene Attribute über das bestehende Fields-Template.
- Selbstassoziationen verwenden getrennte Kontexte pro Rollenname (z.B. **Primary** und **Secondary**).
- Die Rückleitung erfolgt deterministisch aus Registry-Kontext und Owner-ID; es gibt keine freie `returnUrl`.

#### Association-UX: Navigation
- `InterlisNavigationSupport` filtert die Navigationsleiste basierend auf der generierten Registry.
- Technische Association-Controller (`BeteiligungController`, `PersonRefController`, etc.) werden standardmässig ausgeblendet, wenn mindestens ein kontextueller Zugang existiert (default: `--grails-association-navigation auto`).
- Mit `show` bleiben alle Controller sichtbar; mit `hide` werden alle Association-Controller ausgeblendet.
- Nicht erkannte Controller werden konservativ angezeigt (kein Reflection-Fehler zerstört das Layout).

#### Association-UX: Performance & Sicherheit
- **Fetch-Join:** Counterpart-Zielobjekte werden per `FetchMode.JOIN` im Criteria-Query mitgeladen (verhindert N+1-Abfragen).
- **Property-Whitelisting:** Alle dynamischen Sortier-/Property-Zugriffe werden gegen GORM-Metadaten geprüft (`safeSort`).
- **AbortController:** Der Autocomplete-JS-Client bricht laufende Requests beim neuen Suchbegriff ab.
- **Kardinalität:** Binäre Max-/Min-Prüfung mit best-effort pessimistischem Locking; DB-Constraints als Sicherheitsnetz.
- **Konflikt:** `DataIntegrityViolationException` und `OptimisticLockingFailureException` werden in verständliche 409-Fehlermeldungen übersetzt.
- **Accessibility:** `prefers-reduced-motion`, `prefers-contrast` und `@media print` CSS-Regeln; ARIA-Attribute auf Sections und Tabellen.

### Django/GeoDjango Target-Spike
- Das Paket `ch.interlis.generator.django` erzeugt aktuell nur eine repräsentative `models.py` aus der Core-IR.
- Der Spike liest ausschließlich die Core-IR (`ModelMetadata`, `ClassMetadata`, `AssociationMetadata`, `AttributeMetadata`, `RelationshipMetadata` und `EnumMetadata`); er greift nicht auf ili2db-/ili2c-Readerdetails zu.
- Association-Rollen werden bevorzugt aus `AssociationMetadata` gelesen und als Felder der expliziten Association-Klasse ausgegeben.
- Physisch gemergte ili2db-Klassen erhalten `db_table`, `managed = False` und FK-`db_column`-Informationen.
- Numerische Attribute nutzen `constraints.precision` und `constraints.scale` für `DecimalField`, falls diese Werte vorhanden sind.
- ili2c-only Geometrieattribute aktivieren GeoDjango (`django.contrib.gis.db.models`) und werden als `GeometryField` ausgegeben.
- Structures und Compositions werden bewusst minimal als Django-Models, `ForeignKey` oder `ManyToManyField` abgebildet, um Core-IR-Grenzen sichtbar zu machen.
- Die CLI-Integration schreibt aktuell nur `<django-output>/<django-app>/models.py`; der Spike hat keine Migrationsstrategie und keine Admin-/View-Generierung.

### Target-Naming
- Zielnamen bleiben Generator-spezifisch und werden nicht in die Core-IR geschrieben.
- Grails verwendet `TargetNameRegistry` im Paket `ch.interlis.generator.grails` als zentrale Naming-Policy für Domain-Klassen, Enums, Properties, Relationen, Controller und View-Pfade.
- Django verwendet eine separate Python/Django-Naming-Policy; Target-Namen bleiben auch dort außerhalb der Core-IR.
- Eindeutige INTERLIS-SimpleNames bleiben unverändert. Bei Kollisionen wird deterministisch mit Topic-/Modell-Kontext präfixiert, z. B. `TopicGebaeude`.
- Java/Groovy-Keywords und ungültige Zeichen werden stabil normalisiert, damit erzeugte Groovy-Klassen kompilierbar bleiben.
- Enum-Konstanten werden ebenfalls als gültige, eindeutige Groovy-Identifier ausgegeben.
- `--grails-generate-all` nutzt dieselbe Registry wie die Domain-Dateien und ruft dadurch die kollisionsfreien Grails-Klassennamen auf.

### Bootstrap UI-Metadaten (Phase 0)
Phase 0 ergänzt ausschliesslich das Grails-Target. Das Modul `core` bleibt frei von
Grails-/GORM-/UI-Typen; `ModelMetadata`, `ClassMetadata` und die bestehende
Relationship-IR bleiben der framework-agnostische Vertrag.

Der Generator schreibt zusätzlich zur unveränderten
`InterlisAssociationRegistry` die deterministische
`ch.interlis.generator.grails.generated.InterlisUiRegistry`. Ihre `DOMAINS`-Einträge
enthalten `domainClassName`, `controller`, `iliName`, `modelName`, den aus
`ClassMetadata.topicName` abgeleiteten `topicName` ohne führenden Modellnamen,
`className`, ein deterministisches `label`, `navigationVisible` und
`associationDomain`. Die Einträge sind nach vollständigem INTERLIS-Namen sortiert.
`domain(iliName)` und die Konfigurationsreferenzen verwenden den exakten
INTERLIS-Namen; `domainForClassName` verbindet die Registry mit einer Grails-Domain.
Klassennamen und Controllerpfade kommen ausschliesslich aus `TargetNameRegistry`.
Association-Sichtbarkeit und `associationDomain` werden ausschliesslich vom
`GrailsAssociationPlanner` bezogen; die bestehende Association-/Relationship-Runtime
wird dadurch nicht dupliziert oder umgebaut.

`InterlisUiDescriptorSupport` wird als managed Bootstrap-Overlay-Datei installiert.
`descriptor(grailsApplication, domainType)` liefert eine zentrale Map mit Registry-
Metadaten, Label/App-Titel, Listen-Spalten, Suchfeldern, Filterdefinitionen,
prominenten Filtern, Form-/Detail-Sektionen sowie den vorhandenen Relationship- und
Geometry-Metadaten. Ohne Konfiguration sind `id`, ein bevorzugtes Display-Feld und
bis zu vier kompakte skalare Felder vorgesehen; Geometrien, Collections, `version`
und erkennbar lange Textfelder werden aus den Listen-Defaults ausgeschlossen.

Die optionale Konfiguration bleibt unter `ili2grails.ui` und referenziert Domains
ausschliesslich über `iliName`. Für das Branding in der Navigationsleiste unterstützt
`ili2grails.ui` die folgenden Keys (Priorität: `appLogo` > `appLogoIcon` > `grid`-Icon):

```yaml
ili2grails:
  ui:
    appTitle: "Fachdatenverwaltung"
    appLogo: "mein-logo.svg"            # Neu: Pfad zu einem Asset-Bild (wird als <img> gerendert)
    appLogoIcon: "house"                # Neu: Icon-Name der ili:icon-TagLib (nur wenn appLogo nicht gesetzt)
    domains:
      - iliName: "SimpleAddressModel.Addresses.Address"
        label: "Adresse"
        list:
          columns: [id, name, year]
          searchFields: [name]
          displayFields: [name]
          prominentFilters: [year]
        form:
          sections:
            - title: "Allgemein"
              fields: [name, year]
```

`list.displayFields` definiert den fachlichen Anzeigenamen eines Datensatzes.
Die Liste enthält ein oder zwei bekannte direkte skalare Felder; ihre nichtleeren
Werte werden in der angegebenen Reihenfolge mit einem Leerzeichen verbunden.
Beispielsweise erzeugt `displayFields: [firstname, lastname]` den Anzeigenamen
`Ada Keller`. Beziehungen, Collections, Geometrien, `version` und unbekannte
Felder werden abgelehnt. Ohne diese Konfiguration bleiben die automatisch aus
`interlisDisplayMeta` abgeleiteten Display-Felder aktiv. Der bestehende Schlüssel
`list.displayField` bleibt davon unabhängig und bestimmt weiterhin die verlinkte
Spalte in der Liste.

Der fachliche Anzeigename wird für Workspace-/Show-Titel, Relationship-Labels,
Relationship-Auswahllisten sowie Show-/Edit-Breadcrumbs verwendet. Die Breadcrumbs
lauten für eine Domain `Explorer > Employee`, beim Erfassen
`Explorer > Employee > Erfassen`, beim Anzeigen beispielsweise
`Explorer > Employee > Ada Keller` und beim Bearbeiten
`Explorer > Employee > Ada Keller > Bearbeiten`.

#### Listenfilter: Platzierung und Eingabekomponenten

Die generische Domain-Liste zeigt die Freitextsuche unabhängig von den Feldfiltern
immer an. Die Feldfilter werden aus den Domain-/GORM-Eigenschaften und den
generierten INTERLIS-Metadaten abgeleitet.

Ohne `list.prominentFilters` werden alle erkannten Filter eingeklappt im Bereich
**Filter** angezeigt. Direkt sichtbare **Quick Filters** müssen pro Domain
explizit konfiguriert werden. Eine explizit leere Liste bedeutet ebenfalls, dass
keine Quick Filters sichtbar sind. Die übrigen erkannten Filter stehen im Bereich
**Weitere Filter**. Die Reihenfolge entspricht der Reihenfolge der erkannten
Domain-Eigenschaften; sie ist damit insbesondere von der generierten Grails-Domain
und ihren Metadaten abhängig.

Die sichtbaren Quick Filters können pro Domain explizit konfiguriert werden:

```yaml
ili2grails:
  ui:
    domains:
      - iliName: "ListQueryE2E.Lists.Record"
        list:
          prominentFilters: [status, municipality]
          filters:
            status:
              label: "Status"
            municipality:
              label: "Gemeinde"
```

`prominentFilters` ist dann die maßgebliche Liste: Die genannten Filter werden
direkt angezeigt, alle anderen weiterhin unter **Weitere Filter**. Unbekannte oder
für die Domain nicht filterbare Feldnamen werden mit dem betroffenen `iliName` und
der Konfigurationssektion diagnostiziert. Die aktuelle Implementierung übernimmt
eine explizite Liste in der angegebenen Länge. Wenn keine Filter prominent
konfiguriert sind, wird die Überschrift des eingeklappten Bereichs als **Filter**
gerendert; sobald mindestens ein Quick Filter sichtbar ist, lautet sie
**Weitere Filter**.

Die UI-Komponente hängt vom erkannten Filtertyp ab:

| Filtertyp | UI-Komponente | Verhalten |
| --- | --- | --- |
| `relationship` | Auswahlfeld/Combobox | Auswahl aus referenzierten To-One-Datensätzen; zusätzlich gibt es `Alle`. |
| `enum` | Auswahlfeld/Combobox | Auswahl aus den generierten Enum-Werten; zusätzlich gibt es `Alle`. |
| `boolean` | Auswahlfeld/Combobox | `Alle`, `Ja` oder `Nein`. |
| `text` | Texteingabe | Enthält-Suche über den Textwert. |
| `number` | Zwei Zahleneingaben | Bereich mit `Von` und `Bis`. |
| `date` | Zwei Datumseingaben | Bereich mit `Von` und `Bis`. |

Ein Feld wird nur dann als generischer Filter angeboten, wenn sein Typ unterstützt
wird. `id`, `version`, Geometrien, Collections und To-Many-Relationships werden
ausgeschlossen. Nicht unterstützte Spezialtypen, beispielsweise grosse Binärfelder,
erhalten ebenfalls keinen generischen Filter.

##### Warum ist `Department` eine Combobox?

Im Getting-Started-Modell ist `Department` nicht einfach ein Textfeld von
`Employee`. Die INTERLIS-Assoziation `DepartmentEmployee` verbindet ein
`Department` mit `0..*` `Employees` und erzeugt auf der Employee-Seite die
To-One-Relationship `department`:

```text
Employee.department : Department
```

Der Generator erkennt diese Relationship aus dem Relationship-Metamodell. Deshalb
wird der Filtertyp `relationship` verwendet und die UI rendert ein Auswahlfeld mit
den vorhandenen Departments, zum Beispiel `Operations` und `Planning`. `Alle`
setzt den Relationship-Filter zurück. Die Optionen werden zunächst begrenzt
serverseitig geladen (standardmässig bis zu 25 Einträge). Ein bereits ausgewählter
Wert bleibt auch dann als Option erhalten, wenn er nicht auf der ersten Seite
liegt. Das Listenfilter-Auswahlfeld selbst ist derzeit ein natives `<select>`;
die progressive Relationship-Autocomplete-Suche wird bei den Relationship-Pickern
in Create/Edit verwendet. Eine To-Many-Relationship würde dagegen als Collection
behandelt und nicht als generischer Listenfilter angezeigt.

Unbekannte Domains und Felder werden mit `IllegalArgumentException` und
`iliName`, Feldname sowie betroffener Konfigurationssektion diagnostiziert.

#### Listenspalten: Default-Auswahl und Konfiguration

Die List-View rendert nicht automatisch alle Attribute einer Domain als
Tabellenspalten. Ohne `list.columns` wird eine kompakte Default-Auswahl gebildet:

- `id`, sofern die Domain ein solches Feld besitzt,
- höchstens ein bevorzugtes Display-/Namensfeld (`name`, `bezeichnung`, `label`,
  `title`, `code` oder `ident`),
- bis zu vier weitere kompakte skalare Felder in der Reihenfolge der generierten
  Domain-Eigenschaften.

Geometrien, Collections und Relationships, `version` sowie erkennbar lange
Textfelder werden nicht in diese Default-Auswahl aufgenommen. Dadurch entstehen
typischerweise höchstens sechs Daten-Spalten; zusätzlich wird die Aktionsspalte
für Anzeigen, Bearbeiten und Löschen gerendert. Bei weniger geeigneten Attributen
kann die Tabelle entsprechend weniger Daten-Spalten enthalten.

Die Spaltenauswahl kann pro Domain mit `list.columns` vollständig überschrieben
werden. Dann werden die angegebenen bekannten Felder in der angegebenen
Reihenfolge gerendert; eine automatische zusätzliche Begrenzung auf vier oder
sechs Spalten gibt es für diese explizite Konfiguration nicht:

```yaml
ili2grails:
  ui:
    domains:
      - iliName: "SimpleAddressModel.Addresses.Address"
        list:
          columns: [id, name, municipality, year, status, description]
```

Nicht bekannte Feldnamen in `list.columns` werden mit dem betroffenen `iliName`
und der Konfigurationssektion diagnostiziert. Die Detailansicht verwendet eine
separate Spaltenauswahl und ist von dieser kompakten List-View-Auswahl nicht
begrenzt.

#### Phase-0-Altlasteninventur und Phase-1-Migration
Phase 0 hat die historischen `--dp-*`-Namen und die ursprünglichen Inline-SVGs
inventarisiert. Phase 1 hat diese Altlasten aus dem gemanagten Bootstrap-Overlay
entfernt; `ili-modern.css` verwendet jetzt Bootstrap-Variablen und native Werte.
Die frühere Tokenliste bleibt hier als historische Referenz dokumentiert:

```text
--dp-color-accent, --dp-color-accent-hover, --dp-color-bg,
--dp-color-border, --dp-color-border-strong, --dp-color-danger-bg,
--dp-color-danger-text, --dp-color-ink, --dp-color-line, --dp-color-surface,
--dp-color-surface-alt, --dp-color-surface-subtle, --dp-color-text,
--dp-color-text-muted, --dp-control-radius, --dp-focus-ring, --dp-radius-md,
--dp-radius-sm, --dp-space-1, --dp-space-2, --dp-space-3, --dp-space-4,
--dp-space-6, --dp-space-8
```

Im aktuellen managed Bootstrap-UI-Code dürfen diese Namen nicht mehr vorkommen;
es gibt keine Alias-Kompatibilitätsschicht. Rot wird nur für Bootstrap-Danger- und
Fehlersemantik verwendet.

Generische UI-Icons werden in Phase 1 zentral über die Whitelist-basierte
`ili:icon`-TagLib als lokal eingebettete Bootstrap-Icons-SVGs gerendert. Es gibt
weder Icon-Webfont noch CDN; auch die berührten CRUD-Action- und Shell-Icons
duplizieren keine direkten Standard-Action-SVGs in Templates.

Die Shell hat keine Login-, User-, Principal- oder Security-Plugin-Kopplung. Die
vorhandene Security-Logik beschränkt sich auf CSP-/HTTP-Security-Header in
`InterlisCrudControllerSupport`; Datenbank-Credentials in der Grails-Konfiguration
sind keine Authentifizierung. Authentisierung, Rollen, Autorisierung und Auditierung
bleiben separate Produktionsaufgaben.

### Bootstrap Application Shell (Phase 1)
Der Bootstrap-Modus verwendet ab Phase 1 eine server-rendered Application Shell:

- `main.gsp` rendert Topbar, App-Titel, globalen Domain Finder, Breadcrumb-Kontext,
  responsive Sidebar und den Layout-Body. Der obere rechte Bereich ist nur der leere,
  auth-unabhängige Extension Point `data-ili-extension-point="user-slot"`; es gibt
  keinen Principal, keinen Dummy-Benutzer und keine Login-Funktion.
- `InterlisNavigationSupport.navigationModel(grailsApplication)` verwendet die
  generierte `InterlisUiRegistry` als Primärquelle und gruppiert sichtbare Domains
  deterministisch nach Modell, Topic und Label. Technische Association-Domains bleiben
  gemäss Registry-/Planner-Semantik verborgen; unbekannte Nicht-Domain-Controller
  erscheinen nur crash-sicher in einer neutralen Fallback-Gruppe.
- Der Explorer ist unter `/interlisUi/index` erreichbar. Die serverseitige Suche
  läuft als normales GET unter `/interlisUi/domains?q=...` und durchsucht nur
  Navigation-Metadaten nach Label, Klassenname, Topic, Modell und INTERLIS-Name.
  Es gibt keine globalen Counts und keine Datensatzsuche.
- `ili-navigation.js` ergänzt den Finder progressiv mit clientseitiger Filterung,
  Pfeil-/Enter-/Escape-Steuerung sowie optionalen localStorage-Favoriten und Recents
  (`ili2grails.ui.favorites`, `ili2grails.ui.recents`). Nicht verfügbare oder
  fehlerhafte lokale Speicherung deaktiviert nur diese Komfortfunktionen; normale
  Links und serverseitige GET-Fallbacks bleiben nutzbar.
- Bootstrap 5.3, OpenLayers, proj4 und das neue lokale Navigationsskript bleiben in
  der bestehenden Asset-Pipeline. CSP und Security-Header bleiben restriktiv und
  auth-unabhängig; Phase 2-Funktionen wie vollständige Domain-Listenfilter,
  Enum-/Range-Filter und ein Detail-Workspace sind nicht Teil dieser Phase.

### Typ-Inferenz (Beispiele)
| Quelle | `coreType` | `targetHints.javaType` |
| --- | --- | --- |
| `TEXT`, `VARCHAR` | `TEXT` | `String` |
| `MTEXT` | `MTEXT` | `String` |
| `BOOLEAN` | `BOOLEAN` | `Boolean` |
| `INTERLIS.XMLDate`, SQL `DATE` | `DATE` | `java.time.LocalDate` |
| `INTERLIS.XMLDateTime`, SQL `TIMESTAMP` | `DATETIME` | `java.time.LocalDateTime` |
| `INTERLIS.XMLTime`, SQL `TIME` | `TIME` | `java.time.LocalTime` |
| `NUMERIC` | `NUMERIC` | z. B. `Integer` oder `java.math.BigDecimal` |
| `EnumerationType` | `ENUM` | `String` |
| `COORD`, `POLYLINE`, `SURFACE` | `COORD`, `POLYLINE`, `SURFACE` | `org.locationtech.jts.geom.Geometry` |

### Modernes SSR-Scaffolding und Geometrie-Editing
- Mit `--grails-ui-theme bootstrap` werden moderne SSR-Scaffolding-Templates verwendet (kein SPA-Zwang).
- Mit `--grails-map-editor openlayers` erhalten Scaffold-`create/edit/show` bei Geometrie-Attributen eine Webkarte.
- Geometrien werden als WKT über Hidden-Fields gebunden und serverseitig via `WKTReader` in JTS-`Geometry` umgewandelt. Die Runtime prüft erwarteten Geometrietyp, Empty-Geometrien, JTS-Validität und konvertiert Single-Geometrien bei erwarteten Multi-Typen in Multi-Geometrien.
- Die Editierwerkzeuge sind bewusst einfach: Zeichnen, Ändern, Löschen und Snapping auf vorhandene Editor-Vertices. Fachliche Topologie-Regeln bleiben ein projektspezifischer Extension Point.
- Die Oberfläche nutzt Bootstrap 5.3 als technische Basis und bleibt no-frills: einheitliche 3px-Radien, dünne Linien und sehr dezente Card-Shadows. Aktive Filter-Chips behalten als einzige Ausnahme ihre vollständig runde Pill-Form. Das Primärblau `#4299E1` wird mit einer kompakten, kühl abgestimmten Neutralpalette kombiniert. Deren semantische CSS-Variablen unterscheiden nur Text, Sekundärtext, Border, Main-Background, Tabellen-Header/Disabled und Hover. Trefferzahlen erscheinen als Sekundärtext direkt vor Tabelle bzw. Empty-State. `data-ili-neutral-palette="balanced"` auf dem `<html>`-Element ist der Standard; `quiet` reduziert und `defined` verstärkt die Flächenkontraste. Rot erscheint nur semantisch für Danger-/Fehlerzustände. Bootstrap, OpenLayers, proj4 und die Navigation werden über die lokale WebJar-/Asset-Pipeline eingebunden, nicht über CDN.
- Die Bootstrap-GUI verwendet lokal eingebettetes Fira Sans statt Frutiger. Die WOFF2-Schnitte für 400 und 600 liegen im managed Overlay unter `grails-app/assets/fonts/fira-sans/` und werden über `ili-modern.css` mit `font-display: swap` geladen; der frühere 700-Bold-Schnitt wird nicht mehr verwendet. Es gibt keine externe Font-CDN-Abhängigkeit. Fira Sans steht unter der SIL Open Font License; der Lizenztext liegt unter `src/main/resources/fonts/fira-sans/OFL.txt`.
- `create/edit` teilen ein gemeinsames Form-Template mit Split-Layout:
  links Formular, rechts Geometrie-Panel (falls Geometrie-Felder vorhanden).
- Dokumentation und Units aus der Core-IR werden als zurückhaltende Feldhinweise im Formular angezeigt. Übersetzte Labels bleiben weiterhin über Grails-Message-Codes überschreibbar.
- Typisierte To-One-Relationships werden im Bootstrap-Overlay als serverseitige Selects mit
  paginiertem Autocomplete-Endpunkt und serverseitiger Fallback-Auswahl gerendert.
  Der Generator schreibt additive `interlisDisplayMeta`-/`interlisRelationshipMeta`-Maps in
  die Grails-Domains. Relationship-Optionen suchen und sortieren bevorzugt nach
  `name`, `bezeichnung`, `label`, `title`, `code`, `ident` und danach nach sinnvollen
  Textfeldern; Labels werden aus ein bis zwei Display-Feldern zusammengesetzt und fallen
  zuletzt auf `id` zurück.
- Bei mehreren Geometriefeldern wird rechts ein Tab-Panel pro Feld gerendert.
- `show` nutzt ebenfalls das Split-Layout und eine separate Danger-Zone mit Confirm-Modal vor `DELETE`.
- `index` rendert als Tabelle mit serverseitigem Paging, Freitextsuche über Textspalten, echten Sortierlinks, einfachen typisierten Filtern und Row-Actions.
- Unsaved-Changes werden in `create/edit` als Badge + `beforeunload`-Warnung signalisiert.
- Der Runtime-Support setzt Security-Header mit lokaler CSP und fängt referenzielle
  Integritätsfehler bei Deletes als verständliche Flash-Meldung ab, statt einen 500er
  durchzureichen.
- Wiederverwendbare Runtime-Logik liegt in `ch.interlis.generator.grails.runtime`. Das Controller-Template delegiert an `InterlisCrudControllerSupport`, statt Paging, Suche, Relationship-Optionen und Geometrie-Binding in jede generierte Controller-Klasse zu kopieren.

Opt-in Browser-E2E:
```bash
./gradlew :target-grails:browserE2eTest
```
Der Test startet `docker compose edit-db`, importiert `SimpleAddressModel` mit ili2pg,
erzeugt eine temporäre Grails-App, führt `generate-all` aus, startet die App und prüft
im echten Chromium-Browser den CRUD-Pfad: Objekt erstellen, Geometrie speichern und
ändern, Relationship-Objekt wählen, wieder öffnen und löschen. `grails`,
`docker compose`, ein lokales ili2pg und installierte Playwright-Browser sind dafür
Voraussetzung.

Für manuelle Prüfungen gegen eine bereits gestartete passende App kann derselbe
Browser-CRUD-Pfad weiterhin auf eine externe URL gerichtet werden:
```bash
./gradlew :target-grails:browserE2eTest -PbrowserE2eAppUrl=http://localhost:8080
```

#### UX-Grenzen dieser Iteration
- Keine Bulk-Actions und keine SPA-Architektur.
- Relationship-Autocomplete lädt pro Anfrage eine begrenzte Ergebnismenge und bleibt ein progressives Enhancement über dem serverseitig gerenderten Select.
- Die Suche ist bewusst generisch und auf einfache Textspalten begrenzt; modell- oder fachdomänenspezifische Filter bleiben späteren Targets vorbehalten.
- Security-Header sind Betriebsdefaults, ersetzen aber keine Authentisierung, Rollen,
  Autorisierung oder Audit-Logs.

### Bootstrap Domain-Liste (Phase 2)

Die Bootstrap-Indexseite ist eine serverseitig gerenderte Arbeitsseite. Sie verwendet
den `InterlisUiDescriptorSupport` für kompakte Default-Spalten, die verlinkte
Display-Spalte, sichere Sortierspalten, Suchfelder und typisierte Filter. Die
Controller-Signatur `index(Integer max, Integer offset)` bleibt kompatibel; Parsing,
Coercion, Criteria, Paging- und URL-Modelle liegen in
`InterlisListQuerySupport`. Das Default-Theme und `core` werden dadurch nicht berührt.

Der GET-Vertrag lautet:

| Zweck | Parameter |
| --- | --- |
| Freitext | `q` |
| Text/Enum/Boolean/To-One | `filter.<field>` |
| Zahlenbereich | `filter.<field>.min`, `filter.<field>.max` |
| Datumsbereich | `filter.<field>.from`, `filter.<field>.to` |
| Sortierung | `sort`, `order=asc|desc` |
| Paging | `max`, `offset` |

Textfilter verwenden `contains`. Enumwerte werden exakt gegen die generierten
Enum-Konstanten geprüft; Boolean akzeptiert nur `true` oder `false`; Relationship-
Filter nur numerische Ziel-IDs. Ungültige oder unbekannte Filter werden ignoriert
und als sichtbare Warnung angezeigt. Eine ungültige Sortierung fällt auf `id` zurück.
Filter-/Suchformulare und alle serverseitig erzeugten Chip-, Sortier- und Paging-URLs
setzen bei einer Filteränderung `offset=0` und erhalten die übrigen aktiven Filter.
Relationship-Filter-Chips zeigen das sichtbare Label der ausgewählten Option; wenn
kein Label verfügbar ist, bleibt die technische Ziel-ID als Fallback erhalten.

Criteria erhält Property-Namen, Relationship-Pfade und Klassen ausschliesslich aus
dem Descriptor. Suchpfade dürfen nur explizit konfiguriert und maximal ein
whitelisted To-One-Hop sein, zum Beispiel:

```yaml
ili2grails:
  ui:
    domains:
      - iliName: "ListQueryE2E.Lists.Record"
        list:
          searchFields: [name, municipality.name]
          displayField: name
          sortableColumns: [year, status, name]
          prominentFilters: [status, municipality]
          filters:
            status:
              label: "Status"
            municipality:
              label: "Gemeinde"
```

Ungültige Suchpfade schlagen beim Erzeugen des Descriptors mit Domain-, Pfad- und
Kontextinformation fehl. To-One-Filteroptionen werden über den vorhandenen
Relationship-Options-Endpunkt paginiert geladen; die erste Seite und ein gewählter
Wert werden serverseitig als normale `<select>`-Optionen gerendert. JavaScript bleibt
damit ein optionales Progressive Enhancement und keine Voraussetzung.

Die managed GSP-Struktur besteht aus einem dünnen `index.gsp`-Orchestrator sowie
Partials für Header, Suche/Quick-/Advanced-Filter, Filter-Chips, Tabelle, Pagination
und den Empty State für vollständig leere Domains. Bei aktiver Suche oder Filterung
und `0 Treffer` wird nur die Trefferzahl angezeigt; eine zusätzliche Meldungs-Card
wird nicht gerendert. Aktive Filter-Chips erscheinen ohne separates `Aktiv:`; der
Chip selbst ist nicht klickbar, nur das `×` entfernt den einzelnen Filter. `Alle
zurücksetzen` bleibt eine separate Aktion. Die Gestaltung folgt Mockup 02 strukturell mit Bootstrap-
Standardsemantik, ohne neue Farbwelt, globale Counts, Collection-Fetches oder
unpaginierten Relationship-Loads.

### Bootstrap Domain Workspace (Phase 3)

Die Bootstrap-`show`-Seite ist eine serverseitig gerenderte Domain-Workspace-Seite. Der
`InterlisCrudControllerSupport` behält seine Controller-API und die vorhandenen Geometry-,
Relationship- und Association-Modelle; die zusätzliche Workspace-Aufbereitung delegiert er an
`InterlisWorkspaceSupport`.

Der Workspace-Header verwendet die bestehende
`InterlisRelationshipOptions`-Fallbacklogik für Display Labels und zeigt Domain-Label, ID sowie
die Primäraktionen Liste, Neu und Bearbeiten. `InterlisUiDescriptorSupport` liefert additive
Detailsektionen: direkte skalare Attribute werden aus dem Descriptor dargestellt, während `id`,
`version`, Geometrien, Collections und Relationships nicht als Detailzeilen dupliziert werden.
Message-Codes haben Vorrang; `interlisFieldMeta` liefert die Fallback-Labels. Konfigurierte
`form.sections` ergänzen die Darstellung, ersetzen aber nicht die übrigen skalaren Attribute.

`show.gsp` ist nur noch Orchestrator. Wiederverwendbare managed GSP-Komponenten liegen unter
`grails-app/views/interlisUi/`:

- `_workspace-header.gsp`, `_workspace-details.gsp`, `_workspace-relationships.gsp` und
  `_workspace-danger-zone.gsp` bilden die generische Workspace-Struktur.
- `_association-sections.gsp`, Quick Add, kontextuelle Association-Formulare und
  `_geometry-panel.gsp` bleiben unverändert die Semantikquellen und werden nur eingebettet.
- Direkte Relationships werden nur für whitelisted To-One-Domainobjekte als Links zur über die
  `InterlisUiRegistry` aufgelösten Controller-Route gerendert. Association-Collections bleiben
  beim bestehenden Association-Service.

Die Danger Zone erklärt technisch korrekt, dass das Löschen serverseitig geprüft wird und
referenzielle Beziehungen oder andere Datenbank-Integritätsbedingungen das Löschen verhindern
können. Der Controller macht den Konflikt sowohl als Flash-Meldung für Formulare als auch als
409-Fehlerantwort für andere Formate sichtbar. Es gibt ausdrücklich keine Audit-, Verlaufs-,
Protokoll-, Timeline- oder Restore-Funktion und keine Persistenz dafür.

Die Workspace-Stile erweitern `ili-modern.css` responsiv im bestehenden Bootstrap-no-frills-
System. Es werden keine `--dp-*`-Tokens und keine neue Farbwelt eingeführt; generische Aktionen
verwenden weiterhin die vorhandene `ili:icon`-TagLib.

Die vollständige Abnahme deckt Unit-/Overlay-Tests, `generate-all`/`compileGroovy`, H2-Workspace-
View-Model und FK-Konflikt, Real-ili2db sowie Browser-E2E ab. Der Browser-Harness erzeugt die
visuellen Prüfartefakte unter `build/e2e-screenshots` und prüft unter anderem Objektöffnung,
Relationship-Navigation zur Municipality, Geometry, leere Associations mit Quick Add,
kontextuelle Formulare, Delete-Dialog und sichtbare Integritätsfehler. Mockup 03 ist als
strukturelle Referenz erkennbar; illustrative blaue Brand-Farben, Benutzeranzeige und
Verlauf-/Protokoll-Tabs sind nicht Bestandteil der Implementierung.

### Bootstrap Create/Edit-Formulare und Editor-UX (Phase 4)

Create- und Edit-Formulare des managed Bootstrap-Overlays bleiben normale Grails-Forms mit
serverseitigem PRG. `InterlisUiDescriptorSupport` liefert standardmässig die Sektion `Allgemein`;
`form.sections` kann bekannte editierbare Scalar- und To-One-Felder deterministisch gruppieren.
Nicht konfigurierte editierbare Felder erscheinen automatisch in `Weitere Felder`. Geometrien,
Collections, `id` und `version` bleiben ausserhalb dieser Sektionen im bestehenden Geometry-Panel
beziehungsweise in den bestehenden Relationship-/Association-Komponenten.

Die generischen CRUD-Aktionen verwenden kurze, kontextbezogene Beschriftungen: Neue Datensätze
werden über `Neu` mit Plus-Icon gestartet, der Create-Titel lautet `{Domain} erfassen`, der
Listen-Link zeigt Listen-Icon und `Liste`, und der primäre Create-Submit heisst `Speichern`.
`Speichern und weiter` und `Abbrechen` bleiben unverändert. Die technische Domain-Bezeichnung
selbst wird dadurch nicht verändert; ihre fachsprachliche Darstellung bleibt einer späteren
separaten Lösung vorbehalten. Die sticky Aktionsleiste bleibt funktional erhalten, verwendet
aber keine eigene Fläche, keinen Rahmen und keinen Schatten.

Die feldnahe Metadatenanzeige verwendet INTERLIS-Dokumentation und Units aus `fieldMeta`.
Message-Codes haben Vorrang vor den Metadaten-Fallback-Labels. Eine Validation-Summary verlinkt
auf feldnahe Fehler; ungültige Controls erhalten `is-invalid`, `aria-invalid` und behalten bereits
eingegebene Werte. Der vorhandene paginierte Relationship Picker bleibt je Sektion aktiv und
ergänzt eine eingereichte Auswahl, wenn sie nicht auf der ersten Optionsseite liegt.

`InterlisFormSupport` whitelisted die Submit-Modi `save` und `saveAndContinue`; unbekannte Werte
fallen sicher auf `save` zurück. `Speichern` nutzt den bisherigen Show-/Context-Redirect,
`Speichern und weiter` führt per PRG zum Edit-Formular des gespeicherten Objekts und erhält den
Association-Context. Kontextsensitive Create-/Edit-/Update-Pfade prüfen Ownership über
`prepareEditContext`; Fixed-Relationships bleiben serverseitig geschützt.

Die Editor-UX zeigt das Badge `Ungespeicherte Änderungen`, berücksichtigt Scalar-/Relationship-
Änderungen und Geometry-WKT und warnt vor relevanter Navigation sowie beim Verlassen des Fensters.
Nach einem gültigen Submit wird die Warnung entfernt; bei clientseitig blockierter Validation bleibt
sie bestehen. Die responsive sticky Action-Bar berücksichtigt Safe-Area-Abstände. Das JavaScript
ist fokussiertes Progressive Enhancement und überschreibt niemals den ausgewählten HTML-Submitter;
es wird keine SPA eingeführt. Geometry Editor, Map-Split, Multi-Geometry-Tabs und kontextuelle
Association-Formulare bleiben funktional unverändert.

Die Phase-4-Abnahme umfasst Unit-/Descriptor-/Runtime-/Overlay-Tests, Grails-/H2- und Real-ili2db-
Smoke sowie vier Browser-E2E-Tests für Sektionen, Inline-Metadaten, Relationship-Paging und
Selected-Value-Fallback, Validation/Werterhalt, Context-State, beide Submit-Modi, Dirty State,
Geometry und kontextuelle Associations. Der Default-Theme und `core` bleiben unangetastet.

### Bootstrap Fachliche Multi-Domain-Workspaces (Phase 5)

Fachliche Arbeitsseiten werden als normale Grails-Erweiterung gebaut: ein eigener Controller
nimmt die Route entgegen, ein anwendungsspezifischer Service lädt die ausdrücklich benötigten
Domains und eine GSP orchestriert die vorhandenen Bootstrap-Workspace-Partials. Dadurch bleibt
generisches CRUD parallel verfügbar; es ist weder ein Fork der Scaffold-Templates noch eine
automatische Fachprozess-DSL nötig.

Workspaces können zusätzlich zur Domain-Navigation konfiguriert werden:

```yaml
ili2grails:
  ui:
    workspaces:
      - id: parcel-workspace
        label: Parzellen-Workspace
        controller: parcelWorkspace
        action: index
```

Die Einträge erscheinen in der separaten Navigationsgruppe **Fachliche Arbeitsseiten** in
Explorer, Sidebar und Breadcrumbs. Sie sind keine Domain-Metadaten: Sie erhalten kein `iliName`,
keine Domainklasse und keinen `InterlisUiRegistry`-Eintrag; die Domain-Suche bleibt auf registrierte
Domain-Metadaten beschränkt. Ungültige Konfigurationen sowie nicht vorhandene Controller werden
mit ID- und Controller-Kontext abgewiesen.

Für eigene Seiten können `grails-app/views/interlisUi/_workspace-link.gsp`,
`_workspace-table.gsp` und `_workspace-empty.gsp` direkt neben den bestehenden Partials verwendet
werden. Der kleine View-Model-Vertrag besteht aus:

```groovy
[
  workspaceDetailSections: [...],
  workspaceRelationshipLinks: [...],
  workspaceTableSections: [[
    key: 'buildings', title: 'Gebäude', columns: [[key: 'name', label: 'Name']],
    rows: [[values: [name: 'Haus A'], links: [name: [controller: 'building', action: 'show', id: id]]]],
    emptyMessage: 'Keine Gebäude vorhanden.'
  ]]
]
```

`InterlisWorkspaceSupport.tableSection(...)` und `tableRow(...)` kopieren und validieren diese
Präsentationsdaten; sie enthalten keine Query-, Persistenz- oder Prozesslogik. Sections definieren
`columns` und `rows`; Rows liefern `values` sowie optionale sichere Ziel-Links pro Zelle. Ein
`emptyMessage` beschreibt den leeren Zustand. `workspaceDetailSections` und
`workspaceRelationshipLinks` bleiben die bestehenden Detail-/Relationship-Semantiken der
generischen `show`-Seite und werden von fachlichen Seiten nur bei Bedarf ergänzt.

Die Referenz-Fixture liegt in `target-grails/src/test/java/.../MultiDomainWorkspaceFixture.java`
und verwendet `test-models/MultiDomainWorkspaceE2E.ili` mit `Parcel`, `Building` und `Owner`.
Der Service fragt nur diese bekannten Domains ab, begrenzt und sortiert die Ergebnisse serverseitig
und verlinkt Related Objects auf ihre normalen generierten CRUD-`show`-Seiten. Das INTERLIS-Modell
bildet die beiden Klassenbeziehungen über explizite Associations ab: `REFERENCE TO` ist in ili2c
nicht direkt als Klassenattribut zulässig, sondern für Strukturen vorgesehen. Die fachliche
Referenzsemantik bleibt dadurch gültig und wird ohne dynamische Klassenwahl oder automatische
Objektgraph-Interpretation ausgewertet.

Die Referenz zeigt Daten- und Empty-Sections im H2-Runtime-Smoke und im Browser-E2E. Sie enthält
in Phase 5 bewusst noch keine gemeinsame Save-/Edit-Transaktion; diese wird für denselben
Referenz-Workspace in Phase 6 ergänzt. Audit, Verlauf und Protokoll werden auch in Phase 5 nicht
eingeführt.

### Bootstrap Atomarer Multi-Domain-Save (Phase 6)

Der Referenz-`ParcelWorkspace` besitzt einen ausdrücklichen Edit-Pfad mit einem gemeinsamen
POST-Formular für `Parcel`, `Building` und `Owner`. Die Request-Grenze besteht aus den typisierten
Grails Command Objects `ParcelWorkspaceCommand`, `BuildingEditCommand` und `OwnerEditCommand`.
Das Formular überträgt nur die bekannten IDs, Versionen, Namen, die Parzellennummer und explizite
`removedBuildingIds`-/`removedOwnerIds`-Listen.

`ParcelWorkspaceCommandService` ist ein fachlicher `@Transactional` Service. Er lädt ausschließlich
die drei fest verdrahteten Domainklassen, prüft Route-/Command-ID, positive und doppelte IDs,
Ownership, Versionen, Domain-Validation sowie Remove-Konflikte und weist die Whitelist-Felder
explizit zu. Omitted Related Objects bleiben erhalten; gelöscht wird nur eine ausdrücklich
übertragene, besessene Remove-ID. Ein Fehler, eine Integritätsverletzung oder ein Optimistic-Lock-
Konflikt wird als Runtime-Fehler aus der Transaktion propagiert, sodass alle Teiländerungen
zurückgerollt werden. Erfolg endet per PRG auf der Workspace-Show-Seite; Fehler rendern das gleiche
Formular mit sectionbezogenen Fehlern und den eingereichten Werten.

Das Framework stellt hierfür die normalen Grails-/GSP-/Dirty-State-Bausteine bereit. Die fachliche
Command-Struktur, die Ownership-Regeln und die erlaubten Remove-Operationen bleiben Verantwortung
des Fachentwicklers im konkreten Workspace. Beliebiges Objektgraph-Editing, dynamische Klassen- oder
Property-Namen und generisches Mass Assignment werden ausdrücklich nicht automatisch generiert.
Die `version`-Felder der Referenz-Fixture sind ausschließlich technische GORM-Optimistic-Locking-
Tokens; sie sind keine Audit-, Verlaufs-, Historisierungs- oder Restore-Daten. Audit und Verlauf
bleiben vollständig außerhalb dieser Phase.

### Strukturen im Domain-Model
- INTERLIS-Strukturen werden als eigene `STRUCTURE`-Klassen im Metamodell geführt.
- In Grails v1 werden Structures konservativ nur dann als Domain ausgegeben, wenn sie
  physisch gemappt sind oder durch eine Composition tatsächlich gebraucht werden.
- Ungenutzte, nicht persistierbare Structures bleiben im Core-Metamodell sichtbar, werden
  aber nicht als Grails-Domain geschrieben.

### Aktuelle Grenzen
- Getesteter Primärpfad ist weiterhin PostgreSQL/PostGIS mit ili2pg; andere ili2db-Flavours sind nicht als produktiv validiert.
- Grails-CRUD nutzt weiterhin Grails-Scaffolding/Template-Overlay; das Core-Metamodell soll davon unabhängig bleiben.
- Django/GeoDjango ist derzeit ein Target-Spike mit CLI-verdrahteter `models.py`-Ausgabe und Snapshot-Abdeckung, aber ohne produktionsreife Runtime-Validierung.
- Der Generator schreibt Credentials aus JDBC-URLs nicht mehr dauerhaft in `application.yml`, sondern nutzt `DB_USERNAME`/`DB_PASSWORD`-Platzhalter und für `production` zusätzlich `DB_URL`. Auth/Rollen, Autorisierung und Audit-Felder bleiben separate Produktionshärtungsaufgaben.

## Projektstruktur
```
ili2grails/
├── README.md
├── build.gradle
├── settings.gradle
├── core/
│   └── src/main/java/ch/interlis/generator/{model,metadata,reader}/
├── target-grails/
│   └── src/main/java/ch/interlis/generator/grails/
├── target-django/
│   └── src/main/java/ch/interlis/generator/django/
├── cli/
│   └── src/main/java/ch/interlis/generator/MetadataReaderApp.java
└── test-models/
```

Die Gradle-Module bilden die Architekturgrenzen ab:
- `core`: Core-IR, ili2db-/ili2c-Reader, Merge und JSON-Vertrag.
- `target-grails`: Grails/GORM-Generator, Template-Overlay, Grails-spezifisches Naming und Grails-Smoke-Tests.
- `target-django`: Django/GeoDjango-Spike gegen die Core-IR.
- `cli`: Kommandozeilen-Orchestrierung und Application-Entry-Point.

## Bootstrap-UI: Phase-7-Härtung und Abnahme

Der gemanagte `bootstrap`-Overlay ist server-rendered mit Grails/GSP und Progressive
Enhancement. Phase 7 ergänzt keine neue öffentliche Core-API, keine Authentifizierung und
kein Audit-/Verlaufssystem.

Die Härtungsregeln sind verbindlich:

- `core`, `target-django` und das Grails-`default`-Theme bleiben ausserhalb des Bootstrap-Overlays.
- Das Overlay verwendet Bootstrap-no-frills mit dem Primärblau `#4299E1` und einer kleinen,
  semantischen Neutralpalette. Rot bleibt auf Danger-/Fehlerzustände über Bootstrap-Variablen
  beschränkt.
- Es gibt keine `--dp-*`-CSS-Custom-Properties oder Aliase im gemanagten Bootstrap-Code.
- Generische Aktionen verwenden den zentralen `ili:icon`-Renderpfad mit lokal eingebetteten
  Bootstrap-Icons-SVGs. Icon-Webfonts, CDN-Icons und externe Icon-Services sind ausgeschlossen.
- Die Bootstrap-Typografie verwendet die lokalen Fira-Sans-WOFF2-Ressourcen des Overlays;
  externe Font-CDNs und Frutiger gehören nicht zum managed Bootstrap-Code.
- Alle dynamischen Association-/Workspace-Werte werden escaped gerendert; Sortierung, Filter,
  Controller, Actions, IDs, Ownership und Delete-Flows bleiben whitelisted und serverseitig geprüft.
- Navigation, Listen und Relationship-Picker bleiben serverseitig begrenzt und paginiert. Der
  Workspace-Display-Support führt selbst keine Queries oder Persistenzoperationen aus.
- Der Benutzer-/Login-Slot ist nur ein leerer Extension Point. Es gibt keine Principal-, Login-,
  Rollen- oder Security-Plugin-Abhängigkeit und keinen Dummy-Benutzer.
- „Zuletzt verwendet“ ist ausschliesslich eine lokale Navigationshilfe. Audit, Envers, Verlauf,
  Protokoll, Timeline, Restore und Historisierungs-Persistenz gehören nicht zum Feature.

Die fünf Mockups werden als strukturelle Referenzen geprüft: Shell/Explorer, Liste/Filter,
Objekt-Workspace, Edit-Formular und Multi-Domain-Workspace. Farben, Pixelabstände sowie
illustrative Benutzer-, Verlauf- und Protokoll-Elemente sind keine Produktvorgaben. Aktuelle
Browser-Artefakte liegen unter `build/e2e-screenshots/phase7-mockup-*.png`.

Die Overlay-Installation wird auf vollständige Managed-File-Abdeckung, Legacy-Bereinigung,
idempotente Asset-Requires und die unveränderte Default-Theme-Trennung geprüft. Accessibility-
Regressionen decken Tastatur, Fokus-Rückgabe, Combobox/Listbox, Modals, Tabs, Feldfehler,
Labels, Tabellen und responsive Overflow ab.

### Phase-7-Abnahme (2026-07-16)

Die vollständige lokale Matrix wurde auf dem aktuellen Stand ausgeführt und war grün:

- `./gradlew clean test --rerun-tasks --no-daemon`
- `PATH="$HOME/.sdkman/candidates/grails/current/bin:$PATH" ./gradlew :target-grails:grailsRuntimeSmokeTest --rerun-tasks --no-daemon` — 5 Tests
- `PATH="$HOME/.sdkman/candidates/grails/current/bin:$PATH" ./gradlew :target-grails:realIli2dbSmokeTest --rerun-tasks --no-daemon` — 9 Tests
- `PATH="$HOME/.sdkman/candidates/grails/current/bin:$PATH" ./gradlew :target-grails:browserE2eTest --rerun-tasks --no-daemon` — 5 Tests
- `git diff --check`

Die normale Matrix umfasste 157 Tests (core 31, target-django 7, cli 12, target-grails 107);
alle gemeldeten Tests hatten 0 Skips, 0 Failures und 0 Errors. Es wurde kein Test deaktiviert.
Die fünf strukturellen
Browser-Artefakte sind `build/e2e-screenshots/phase7-mockup-01-shell.png` bis
`phase7-mockup-05-workspace.png`; sie wurden auf Shell, Liste, Workspace, Edit-Formular,
Multi-Domain-Sections und Desktop-/Mobile-Overflow geprüft. Pixel-Golden-Tests sind bewusst
nicht Teil der Abnahme.

## Tests
```bash
./gradlew test
```

Die Tests enthalten gezielte Naming-Kollisionsfälle und kompilieren generierte
Grails-Domains/Enums mit dem Standalone-Groovy-Compiler. Zusätzlich vergleichen
Generated-Output-Snapshots ausgewählte Domain-/Enum-Dateien für repräsentative
Relationship-, Association- und Structure-/Composition-Fälle. `AssociationCases`
deckt unter anderem Associations ohne Attribute, mit eigenem Attribut, zwei Rollen
auf gleicher Zielklasse, abweichende physische Rollenspalten, `EXTERNAL`, `COMPOSITE`
und Associations in erweitertem Topic ab. Die Grails-Snapshots
liegen unter `target-grails/src/test/resources/grails-snapshots/`.

Der Django/GeoDjango-Spike ist über `models.py`-Snapshots für gemergte ili2db-FKs,
ili2c-only Geometry/Enums, Association-Rollen und Structure-/Composition-Fälle abgesichert. Diese
Snapshots liegen unter `target-django/src/test/resources/django-snapshots/`. Snapshot-Updates
sollen nur bei absichtlichen Generatoränderungen erfolgen.

`VSADSSMINI_2020_LV95` aus
`test-models/VSADSSMINI_2020_2_d_LV95-20251129.ili` wird zuerst mit ili2c
validiert; danach werden auch die daraus generierten Grails-Target-Dateien
kompiliert. Ist ein externes Modell-Repository nicht erreichbar, wird dieser
Großmodell-Test sauber übersprungen.

Optional kann zusätzlich eine echte temporäre Grails-App erzeugt und kompiliert werden:
```bash
./gradlew :target-grails:grailsRuntimeSmokeTest
```

Dieser Runtime-Smoke-Test benötigt eine lokale `grails`-CLI im `PATH`, erzeugt seine App
in einem temporären Verzeichnis, kompiliert generierte Domains/Enums mit `compileGroovy`
und prüft zusätzlich `grailsw generate-all` mit den Registry-Klassennamen. Die aktive
Grails-Version wird durch die `grails`-CLI im `PATH` bestimmt:
```bash
PATH=/path/to/grails-7.0.6/bin:$PATH ./gradlew :target-grails:grailsRuntimeSmokeTest
```

Für eine echte ili2pg/PostGIS-Validierung der Structure-/Composition-Abbildung gibt es
einen weiteren opt-in Test:
```bash
./gradlew :target-grails:realIli2dbSmokeTest
```

Dieser Test nutzt `docker-compose.yml` (`edit-db` auf Port `54321`) und das lokale ili2pg
unter `/Users/stefan/apps/ili2pg-5.5.1`. Der Pfad kann überschrieben werden:
```bash
./gradlew :target-grails:realIli2dbSmokeTest -Pili2pgHome=/path/to/ili2pg-5.5.1
```

Der Browser-E2E-Track schließt zusätzlich den generierten Grails-Browser-Pfad:
```bash
./gradlew :target-grails:browserE2eTest
```

Der Test benötigt dieselben lokalen Dienste wie der ili2db-Smoke-Test, zusätzlich
eine lokale `grails`-CLI und Playwright Chromium. Grails-CLI-Pfad, ili2pg-Home
und JDBC-URL können angepasst werden:
```bash
./gradlew :target-grails:browserE2eTest \
  -Pili2pgHome=/path/to/ili2pg-5.5.1 \
  -PbrowserE2eJdbcUrl='jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret'
```

Der Test importiert temporäre Schemas mit ili2pg, liest echte ili2db-Metatabellen,
validiert Naming/Structure-/Composition-Mapping und schreibt Diagnose-Artefakte nach
`build/reports/real-ili2db-smoke/`. Die Reports werden als maschinenlesbares JSON und
als Markdown-Inventar geschrieben; enthalten sind Counts, Structures, Composition-
Relationships, generierte Grails-Klassen, übersprungene Structures mit Grund und kurze
Hinweise zu leeren Befunden. `StructureCompositionCases` ist der deterministische lokale
Structure-/Composition-Realtest ohne externe Modell-Repositories; `VSADSSMINI_2020_LV95`
bleibt das große opportunistische Realmodell. Docker-, ili2pg- oder Repository-Probleme
führen zu einem sauberen Skip statt zu einem roten Standard-Build.
