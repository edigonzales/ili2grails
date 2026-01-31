# INTERLIS CRUD Generator – Metadata Reader

Der **INTERLIS CRUD Generator** liest Metadaten aus einer ili2db-Datenbank und einem INTERLIS-Modell und baut daraus ein internes Metamodell auf. Dieses Metamodell ist die Grundlage für spätere Code-Generatoren (z. B. Grails Domains).

## Inhalt
- [Ziel & Funktionsumfang](#ziel--funktionsumfang)
- [Voraussetzungen](#voraussetzungen)
- [Installation & Build](#installation--build)
- [Schnellstart (CLI)](#schnellstart-cli)
- [Benutzeranleitung (Detail)](#benutzeranleitung-detail)
- [Programmatische Nutzung](#programmatische-nutzung)
- [Ausgabe verstehen](#ausgabe-verstehen)
- [Architektur & Design-Entscheidungen](#architektur--design-entscheidungen)
- [Projektstruktur](#projektstruktur)
- [Tests](#tests)
- [Dependencies](#dependencies)
- [Weitere Dokumente](#weitere-dokumente)

## Ziel & Funktionsumfang
Der Metadata Reader liefert ein vollständiges, framework-agnostisches **Metamodell**:
- Klassen/Tabellen, Attribute/Spalten, Constraints
- Beziehungen (FK, Associations) und Vererbung
- Enumerationen inkl. Reihenfolge und Erweiterbarkeit
- Dokumentation/Labels

Die Metadaten kommen aus zwei Quellen:
1. **ili2db Metatabellen** (Mapping, physische DB-Struktur)
2. **ili2c Compiler** (Semantik, Constraints, Dokumentation)

## Voraussetzungen
- **Java 17+**
- Zugriff auf eine **ili2db**-Datenbank (PostgreSQL oder H2)
- Eine passende **.ili**-Modelldatei
- Für H2 mit Geometrietypen: **H2GIS** (in den Dependencies enthalten)

Prüfen:
```bash
java -version
```

## Installation & Build
```bash
./gradlew build
```

Optional: Demo-Skript mit eingebauter H2-DB:
```bash
./demo.sh
```

## Schnellstart (CLI)
**PostgreSQL:**
```bash
./gradlew run --args="'jdbc:postgresql://localhost:5432/mydb?user=postgres&password=secret' \
  test-models/SimpleAddressModel.ili \
  SimpleAddressModel \
  public"
```

**H2 (embedded):**
```bash
./gradlew run --args="'jdbc:h2:./data/testdb' \
  test-models/SimpleAddressModel.ili \
  SimpleAddressModel"
```

**Parameter:**
1. JDBC-URL (inkl. User/Passwort)
2. Pfad zur `.ili`-Datei
3. INTERLIS-Modellname
4. (Optional) DB-Schema

**Grails CRUD-Generierung (optional):**
```bash
./gradlew run --args="'jdbc:h2:./data/testdb' \
  test-models/SimpleAddressModel.ili \
  SimpleAddressModel \
  --grails-output ./generated-grails \
  --grails-package ch.example.demo"
```

Weitere Optionen:
- `--grails-domain-package` (Default: Basis-Package)
- `--grails-controller-package` (Default: Basis-Package)
- `--grails-enum-package` (Default: `<Basis-Package>.enums`)

## Benutzeranleitung (Detail)
### 1) Datenbank vorbereiten
Die Datenbank muss mit **ili2db** befüllt sein – inklusive Metatabellen. Der Reader nutzt u. a.:
- `t_ili2db_classname` (Klassen/Tabellen-Mapping)
- `t_ili2db_attrname` (Attribute/Spalten-Mapping)
- `t_ili2db_inheritance` (Vererbung)
- `t_ili2db_trafo` (Transformationsstrategien)
- `t_ili2db_column_prop` (Constraints/Properties)

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
interlis-crud-generator/
├── README.md
├── ARCHITECTURE.md
├── QUICKSTART.md
├── PROJECT_SUMMARY.md
├── IMPLEMENTATION_REPORT.md
├── build.gradle
├── demo.sh
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

## Dependencies
| Library | Version | Zweck |
| --- | --- | --- |
| ili2c-core | 5.5.2 | INTERLIS-Compiler |
| ili2c-tool | 5.5.2 | INTERLIS-Tools |
| PostgreSQL JDBC | 42.7.1 | PostgreSQL-Treiber |
| H2 Database | 1.4.197 | Embedded DB |
| H2GIS | 1.5.0 | Spatial Functions für H2 |
| SLF4J/Logback | 2.0.9/1.4.14 | Logging |
| JUnit 5 | 5.10.1 | Testing |

## Weitere Dokumente
- [ARCHITECTURE.md](ARCHITECTURE.md) – technische Details, die nicht in den Schnellstart gehören
- [QUICKSTART.md](QUICKSTART.md) – Kurzfassung der Installation
- [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) – kompakte Projektübersicht
- [IMPLEMENTATION_REPORT.md](IMPLEMENTATION_REPORT.md) – Abschlussbericht
