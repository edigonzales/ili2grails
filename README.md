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
- Beziehungen (FK, Associations) und Vererbung
- Enumerationen inkl. Reihenfolge und Erweiterbarkeit
- Dokumentation/Labels

Die Metadaten kommen aus zwei Quellen:
1. **ili2db Metatabellen** (Mapping, physische DB-Struktur)
2. **ili2c Compiler** (Semantik, Constraints, Dokumentation)

## Voraussetzungen
- **Java 17+**
- Zugriff auf eine **ili2db**-Datenbank (alle ili2db-Flavours sind grundsätzlich möglich; **getestet ist aktuell nur ili2pg**). Die von Grails verwendete Hibernate-Version muss den Datenbank-Flavor unterstützen.
- Eine passende **.ili**-Modelldatei
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
  test-models/SimpleAddressModel.ili \
  SimpleAddressModel \
  sa"
```

**Parameter:**
1. JDBC-URL (inkl. User/Passwort)
2. Pfad zur `.ili`-Datei
3. INTERLIS-Modellname
4. (Optional) DB-Schema

**Grails CRUD-Generierung (optional):**
```bash
./gradlew run --args="'jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=sa' \
  test-models/SimpleAddressModel.ili \
  SimpleAddressModel \
  sa \
  --grails-output ./generated-grails \
  --grails-package ch.example.demo"
```

Weitere Optionen:
- `--grails-init [appName]` (optional: erzeugt ein Grails-Projekt im Zielverzeichnis; mit `appName` wird ein Unterordner erstellt)
- `--grails-version <x.y>` (nur mit `--grails-init`)
- `--grails-domain-package` (Default: Basis-Package)
- `--grails-enum-package` (Default: `<Basis-Package>.enums`)
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
  test-models/SimpleAddressModel.ili \
  SimpleAddressModel \
  sa \
  --grails-output ./generated-grails \
  --grails-init my-grails-app \
  --grails-version 7.0.6 \
  --grails-package ch.example.demo"
```
Der Scaffold-Schritt wird blockiert, wenn im Zielverzeichnis bereits `build.gradle`, `settings.gradle` oder `grails-app/` vorhanden sind.

Hinweis: Der Generator ergänzt in `build.gradle` automatisch die JTS-Dependency, sobald eine Grails-App vorhanden ist.
Zusätzlich setzt der Generator in `grails-app/conf/application.yml` die `development`-Datenbank auf die per CLI übergebene JDBC-URL, ergänzt `currentSchema` (falls gesetzt), stellt `dbCreate` auf `none` und setzt den PostgreSQL-Hibernate-Dialekt.

### 2) CRUD-Artefakte generieren
```bash
./grailsw generate-all Address
./grailsw generate-all Person
```
Alternativ kann `--grails-generate-all` verwendet werden, um diesen Schritt automatisch für alle generierten Domains auszuführen.

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
Die `.ili`-Datei muss die gleiche Modellversion widerspiegeln wie der ili2db-Import.

### 3) Programm starten
Nutzen Sie die Beispiele aus dem Schnellstart. Bei Bedarf kann das Schema explizit gesetzt werden (z. B. `public`).

### 4) Ergebnis interpretieren
Die Ausgabe zeigt:
- Modellname, Schema, Versionsinfos
- Klassen und Attribute inkl. Typen, Constraints, Enums
- Beziehungen (FK/Association)

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
- Erweiterbar für weitere Metadaten
- Separiert von ili2db/ili2c-Implementierungen
- Grails-Ausgabe als **Beispielimplementierung** (Domains/Enums), nicht als exklusives Ziel

### Typ-Inferenz (Beispiele)
```
TEXT + VARCHAR     → String
XMLDate + DATE     → LocalDate
COORD + GEOMETRY   → org.locationtech.jts.geom.Geometry
NUMERIC 1..3       → Integer
NUMERIC 1.00..3.55 → BigDecimal
```

### Strukturen im Domain-Model
- INTERLIS-Strukturen werden als eigene `STRUCTURE`-Klassen im Metamodell geführt.
- In Domain-Modellen werden Struktur-Attribute als **eingebettete Value-Objects** oder
  **kompositionale 1:1-Beziehungen** modelliert (abhängig vom Framework).

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
