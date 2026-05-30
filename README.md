# INTERLIS CRUD Generator – Metadata Reader

Der **INTERLIS CRUD Generator** liest Metadaten aus einer ili2db-Datenbank und einem INTERLIS-Modell, baut daraus ein internes Metamodell auf und liefert zusätzlich eine **Beispielimplementierung für Grails** (Domains, Enums). Die Software bleibt jedoch im Kern **software- und framework-agnostisch** – das Metamodell dient als Basis für weitere Generatoren und Integrationen.

## Inhalt
- [Ziel & Funktionsumfang](#ziel--funktionsumfang)
- [Voraussetzungen](#voraussetzungen)
- [Installation & Build](#installation--build)
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

## Schnellstart (CLI)
**PostgreSQL:**

```bash
docker compose up
```

```bash
java -jar ili2pg-5.5.1.jar --dbhost localhost:54321 --dbdatabase edit --dbusr postgres --dbpwd secret --defaultSrsCode 2056 --createFk --nameByTopic --strokeArcs --smart2Inheritance --createEnumTabs --modeldir test-models --models SimpleAddressModel --dbschema sa --schemaimport
```

```bash
./gradlew run --args="'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=sa' \
  SimpleAddressModel \
  sa \
  --model-file test-models/SimpleAddressModel.ili"
```

**Repository-Lookup (nur Modellname, Datei wird aus Repos geholt):**
```bash
./gradlew run --args="'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=sa' \
  DM01AVCH24LV95D \
  public \
  --model-repos https://models.interlis.ch/"
```

**Parameter (lokale Datei):**
1. JDBC-URL (inkl. User/Passwort)
2. Pfad zur `.ili`-Datei
3. INTERLIS-Modellname
4. (Optional) DB-Schema

**Parameter (Repository-Lookup):**
1. JDBC-URL (inkl. User/Passwort)
2. INTERLIS-Modellname
3. (Optional) DB-Schema
4. (Optional) `--model-repos` (Repository-Liste)

**Grails CRUD-Generierung (optional):**
```bash
./gradlew run --args="'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=sa' \
  SimpleAddressModel \
  sa \
  --model-file test-models/SimpleAddressModel.ili \
  --grails-output ./generated-grails \
  --grails-package ch.example.demo"
```

Weitere Optionen:
- `--model-file <file>` (optional: explizite `.ili`-Datei statt positionaler Angabe)
- `--model-repos <r1;r2>` (optional: Repository-Liste für die Modellauflösung)
- `--metadata-json <file>` (optional: schreibt eine deterministische JSON-Ausgabe der Core-IR)
- `--grails-init [appName]` (optional: erzeugt ein Grails-Projekt im Zielverzeichnis; mit `appName` wird ein Unterordner erstellt)
- `--grails-version <x.y>` (nur mit `--grails-init`)
- `--grails-domain-package` (Default: Basis-Package)
- `--grails-enum-package` (Default: `<Basis-Package>.enums`)
- `--grails-ui-theme <default|bootstrap>` (Default: `default`)
- `--grails-map-editor <none|openlayers>` (Default: `openlayers` bei `bootstrap`, sonst `none`)
- `--grails-default-srid <int>` (Default: `2056`)
- `--grails-generate-all` (nur mit `--grails-init`, ruft `./grailsw generate-all` für jede Domain auf)

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
./gradlew run --args="'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=sa' \
  SimpleAddressModel \
  sa \
  --model-file test-models/SimpleAddressModel.ili \
  --grails-output ./generated-grails \
  --grails-init my-grails-app \
  --grails-version 7.0.6 \
  --grails-package ch.example.demo"
```
Der Scaffold-Schritt wird blockiert, wenn im Zielverzeichnis bereits `build.gradle`, `settings.gradle` oder `grails-app/` vorhanden sind.

Hinweis: Der Generator ergänzt in `build.gradle` automatisch die JTS-Dependency, sobald eine Grails-App vorhanden ist.
Zusätzlich setzt der Generator in `grails-app/conf/application.yml` die `development`-Datenbank auf die per CLI übergebene JDBC-URL, ergänzt `currentSchema` (falls gesetzt), stellt `dbCreate` auf `none` und setzt den PostgreSQL-Hibernate-Dialekt.
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
(Property `dataSource.url` inkl. `username`, `password`).

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
./gradlew run --args="'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=sa' \
  SimpleAddressModel \
  sa \
  --model-file test-models/SimpleAddressModel.ili \
  --metadata-json build/metadata/SimpleAddressModel.json"
```

Die JSON-Ausgabe ist stabil sortiert und eignet sich für Golden-Tests und weitere Generatoren.

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
                System.out.println("  - " + attr.getName() + " : " + attr.getJavaType());
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

### Relationship-Semantik
- ili2db-Beziehungen liefern physische Namen: FK-Spalten, Zielspalten und Tabellenmapping.
- ili2c-Beziehungen liefern fachliche Semantik: Association-Rollen, Kardinalitäten, `ORDERED`, `EXTERNAL`, Reference- und Composition-Attribute.
- Beim Merge gewinnen ili2db-Namen für die physische DB-Struktur und ili2c-Felder für fachliche Semantik.
- Unbounded Cardinality wird in Java und JSON als `-1` ausgegeben.

### Grails Relationship-/Structure-Mapping
- Grails nutzt eine interne `GrailsRelationshipMapper`-Schicht statt roher Relationship-Listen.
- `CLASS` und `ASSOCIATION` werden generiert, wenn sie nicht abstrakt sind.
- `STRUCTURE` wird nur als Domain generiert, wenn sie physisch gemappt ist (`tableName`/`sqlName`) oder Ziel einer `COMPOSITION_ATTRIBUTE` ist.
- Normale `ILI2DB_FK`- und `REFERENCE_ATTRIBUTE`-Beziehungen werden als typisierte Properties ausgegeben, erzeugen aber kein automatisches `belongsTo`.
- `COMPOSITION_ATTRIBUTE` erzeugt bei `max > 1` oder `max = -1` ein `hasMany`; bei `max = 1` eine einfache Ziel-Property.
- `belongsTo` wird nur für physisch vorhandene Composition-FKs ausgegeben. Der Generator erfindet dafür keine synthetischen DB-Spalten.
- `ASSOCIATION_ROLE` wird in v1 als Property auf der Association-Domain modelliert; inverse `hasMany` auf den Zielklassen bleibt bewusst aus.

### Target-Naming
- Zielnamen bleiben Generator-spezifisch und werden nicht in die Core-IR geschrieben.
- Grails verwendet `TargetNameRegistry` als zentrale Naming-Policy für Domain-Klassen, Enums, Properties, Relationen, Controller und View-Pfade.
- Eindeutige INTERLIS-SimpleNames bleiben unverändert. Bei Kollisionen wird deterministisch mit Topic-/Modell-Kontext präfixiert, z. B. `TopicGebaeude`.
- Java/Groovy-Keywords und ungültige Zeichen werden stabil normalisiert, damit erzeugte Groovy-Klassen kompilierbar bleiben.
- Enum-Konstanten werden ebenfalls als gültige, eindeutige Groovy-Identifier ausgegeben.
- `--grails-generate-all` nutzt dieselbe Registry wie die Domain-Dateien und ruft dadurch die kollisionsfreien Grails-Klassennamen auf.

### Typ-Inferenz (Beispiele)
```
TEXT + VARCHAR     → String
XMLDate + DATE     → LocalDate
COORD + GEOMETRY   → org.locationtech.jts.geom.Geometry
NUMERIC 1..3       → Integer
NUMERIC 1.00..3.55 → BigDecimal
```

### Modernes SSR-Scaffolding und Geometrie-Editing
- Mit `--grails-ui-theme bootstrap` werden moderne SSR-Scaffolding-Templates verwendet (kein SPA-Zwang).
- Mit `--grails-map-editor openlayers` erhalten Scaffold-`create/edit/show` bei Geometrie-Attributen eine Webkarte.
- Geometrien werden als WKT über Hidden-Fields gebunden und serverseitig via `WKTReader` in JTS-`Geometry` umgewandelt.
- Die Editierwerkzeuge sind bewusst einfach: Zeichnen, Ändern, Löschen (ohne Snapping/Topologieprüfung).
- Die Oberfläche nutzt Bootstrap 5.3 mit Standardkomponenten (Navbar mit Hamburger-Menü, Alerts, Tabellen, Modal).
- `create/edit` teilen ein gemeinsames Form-Template mit Split-Layout:
  links Formular, rechts Geometrie-Panel (falls Geometrie-Felder vorhanden).
- Typisierte To-One-Relationships werden im Bootstrap-Overlay als serverseitige Selects gerendert.
  Labels werden zur Laufzeit bevorzugt aus `name`, `bezeichnung`, `label`, `title`, danach `id`
  abgeleitet.
- Bei mehreren Geometriefeldern wird rechts ein Tab-Panel pro Feld gerendert.
- `show` nutzt ebenfalls das Split-Layout und eine separate Danger-Zone mit Confirm-Modal vor `DELETE`.
- `index` rendert ohne Paging/Search/Bulk als Bootstrap-Tabelle mit Row-Actions.
- Unsaved-Changes werden in `create/edit` als Badge + `beforeunload`-Warnung signalisiert.

#### UX-Grenzen dieser Iteration
- Kein Paging, keine Freitextsuche und keine Bulk-Actions.
- Relationship-Selects laden aktuell alle Zielobjekte serverseitig; Autocomplete/Paging folgt später.
- All-Rows-Index ist für moderate Datenmengen gedacht; bei sehr großen Tabellen kann die Ladezeit steigen.

### Strukturen im Domain-Model
- INTERLIS-Strukturen werden als eigene `STRUCTURE`-Klassen im Metamodell geführt.
- In Grails v1 werden Structures konservativ nur dann als Domain ausgegeben, wenn sie
  physisch gemappt sind oder durch eine Composition tatsächlich gebraucht werden.
- Ungenutzte, nicht persistierbare Structures bleiben im Core-Metamodell sichtbar, werden
  aber nicht als Grails-Domain geschrieben.

### Aktuelle Grenzen
- Getesteter Primärpfad ist weiterhin PostgreSQL/PostGIS mit ili2pg; andere ili2db-Flavours sind nicht als produktiv validiert.
- Grails-CRUD nutzt weiterhin Grails-Scaffolding/Template-Overlay; das Core-Metamodell soll davon unabhängig bleiben.
- Produktive Credential-Konfiguration sollte über Umgebungsvariablen oder Grails/Spring-Konfiguration erfolgen; die CLI-Beispiele enthalten Zugangsdaten nur für lokale Demos.

## Projektstruktur
```
ili2grails/
├── README.md
├── build.gradle
├── src/main/java/ch/interlis/generator/
│   ├── MetadataReaderApp.java
│   ├── model/
│   ├── reader/
│   └── metadata/
└── test-models/
```

## Tests
```bash
./gradlew test
```

Die Tests enthalten gezielte Naming-Kollisionsfälle und kompilieren generierte
Grails-Domains/Enums mit dem Standalone-Groovy-Compiler. `VSADSSMINI_2020_LV95`
aus `test-models/VSADSSMINI_2020_2_d_LV95-20251129.ili` wird zuerst mit ili2c
validiert; danach werden auch die daraus generierten Grails-Target-Dateien kompiliert.
Ist ein externes Modell-Repository nicht erreichbar, wird dieser Großmodell-Test sauber
übersprungen.

Optional kann zusätzlich eine echte temporäre Grails-App erzeugt und kompiliert werden:
```bash
./gradlew grailsRuntimeSmokeTest
```

Dieser Runtime-Smoke-Test benötigt eine lokale `grails`-CLI im `PATH`, erzeugt seine App
in einem temporären Verzeichnis, kompiliert generierte Domains/Enums mit `compileGroovy`
und prüft zusätzlich `grailsw generate-all` mit den Registry-Klassennamen. Die getestete
Grails-Version ist standardmäßig `7.0.6` und kann überschrieben werden:
```bash
./gradlew grailsRuntimeSmokeTest -PgrailsSmokeVersion=7.0.6
```

Für eine echte ili2pg/PostGIS-Validierung der Structure-/Composition-Abbildung gibt es
einen weiteren opt-in Test:
```bash
./gradlew realIli2dbSmokeTest
```

Dieser Test nutzt `docker-compose.yml` (`edit-db` auf Port `54321`) und das lokale ili2pg
unter `/Users/stefan/apps/ili2pg-5.5.1`. Der Pfad kann überschrieben werden:
```bash
./gradlew realIli2dbSmokeTest -Pili2pgHome=/path/to/ili2pg-5.5.1
```

Der Test importiert temporäre Schemas mit ili2pg, liest echte ili2db-Metatabellen,
validiert Naming/Structure-/Composition-Mapping und schreibt Diagnose-Artefakte nach
`build/reports/real-ili2db-smoke/`. Docker-, ili2pg- oder Repository-Probleme führen
zu einem sauberen Skip statt zu einem roten Standard-Build.
