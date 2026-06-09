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
- `--grails-version <x.y>` (nur mit `--grails-init`)
- `--grails-domain-package` (Default: Basis-Package)
- `--grails-enum-package` (Default: `<Basis-Package>.enums`)
- `--grails-ui-theme <default|bootstrap>` (Default: `default`)
- `--grails-map-editor <none|openlayers>` (Default: `openlayers` bei `bootstrap`, sonst `none`)
- `--grails-default-srid <int>` (Default: `2056`)
- `--grails-generate-all` (nur mit `--grails-init`, ruft `./grailsw generate-all` für jede Domain auf)
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
  --grails-version 7.0.6 \
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
- Die Oberfläche nutzt Bootstrap 5.3 als technische Basis, wird aber mit ruhigen Datenportal-Tokens (`ili-modern.css`) gestaltet: kleine Radien, dünne Linien, rote Akzente und keine Card-Shadows. Bootstrap, OpenLayers und proj4 werden über lokale WebJars/Asset-Pipeline eingebunden, nicht über CDN.
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
und prüft zusätzlich `grailsw generate-all` mit den Registry-Klassennamen. Die getestete
Grails-Version ist standardmäßig `7.0.6` und kann überschrieben werden:
```bash
./gradlew :target-grails:grailsRuntimeSmokeTest -PgrailsSmokeVersion=7.0.6
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
eine lokale `grails`-CLI und Playwright Chromium. Die Grails-Version, ili2pg-Home
und JDBC-URL können überschrieben werden:
```bash
./gradlew :target-grails:browserE2eTest \
  -PgrailsSmokeVersion=7.0.6 \
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
