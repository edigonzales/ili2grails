# INTERLIS CRUD Generator - Phase 1: Metadata Reader

Phase 1 der INTERLIS CRUD-Anwendungs-Generator Implementierung.

## Übersicht

Dieser Metadaten-Reader liest Informationen aus:
1. **ili2db Metatabellen** (in der Datenbank) - für Struktur und DB-Mapping
2. **INTERLIS-Modell via ili2c** - für semantische Informationen

### Erstellt ein internes Metamodell mit:
- Klassen (Tables)
- Attribute (Columns) mit Constraints
- Beziehungen (Foreign Keys)
- Enumerationen
- Vererbungshierarchie
- Dokumentation und Labels

## Architektur

```
┌─────────────────────────────────────────────────────────────┐
│                    MetadataReader                           │
│  (kombiniert ili2db + ili2c)                               │
└───────────────────┬─────────────────────────────────────────┘
                    │
        ┌───────────┴────────────┐
        │                        │
        ▼                        ▼
┌──────────────────┐    ┌──────────────────┐
│ Ili2dbMetadata   │    │ Ili2cModelReader │
│ Reader           │    │                  │
│ (DB Metatabellen)│    │ (ili2c Compiler) │
└──────────────────┘    └──────────────────┘
        │                        │
        │                        │
        ▼                        ▼
┌──────────────────┐    ┌──────────────────┐
│  PostgreSQL/H2   │    │ .ili Modell-Datei│
│  Datenbank       │    │                  │
└──────────────────┘    └──────────────────┘
```

## Projekt-Struktur

```
src/main/java/ch/interlis/generator/
├── model/                      # Internes Metamodell
│   ├── ModelMetadata.java      # Hauptklasse
│   ├── ClassMetadata.java      # Klasse/Tabelle
│   ├── AttributeMetadata.java  # Attribut/Spalte
│   ├── EnumMetadata.java       # Enumeration
│   └── RelationshipMetadata.java # Beziehung
├── reader/                     # Reader-Implementierungen
│   ├── Ili2dbMetadataReader.java # Liest ili2db-Tabellen
│   └── Ili2cModelReader.java     # Liest INTERLIS-Modell
├── metadata/                   # High-level APIs
│   ├── MetadataReader.java     # Kombinierter Reader
│   └── MetadataPrinter.java    # Ausgabe-Utility
└── MetadataReaderApp.java      # Demo-Anwendung
```

## Verwendung

### Voraussetzungen

1. **Datenbank mit ili2db-Schema**
   - PostgreSQL oder H2
   - Mit ili2db importierte Daten
   - ili2db Metatabellen müssen vorhanden sein

2. **INTERLIS-Modelldatei** (.ili)
   - Das Modell, das für den ili2db-Import verwendet wurde

### Build

```bash
./gradlew build
```

### Ausführung

```bash
./gradlew run --args="<jdbcUrl> <modelFile> <modelName> [schema]"
```

#### Beispiele

**PostgreSQL:**
```bash
./gradlew run --args="'jdbc:postgresql://localhost:5432/mydb?user=postgres&password=secret' \
  test-models/SimpleAddressModel.ili \
  SimpleAddressModel \
  public"
```

**H2 Database:**
```bash
./gradlew run --args="'jdbc:h2:./data/testdb' \
  test-models/SimpleAddressModel.ili \
  SimpleAddressModel"
```

### Programmatische Verwendung

```java
import ch.interlis.generator.metadata.MetadataReader;
import ch.interlis.generator.model.ModelMetadata;

// Datenbankverbindung
Connection conn = DriverManager.getConnection(jdbcUrl);

// Reader erstellen
MetadataReader reader = new MetadataReader(
    conn,
    new File("model.ili"),
    "public",  // Schema (optional)
    null       // Model-Verzeichnisse (optional)
);

// Metadaten lesen
ModelMetadata metadata = reader.readMetadata("MyModel");

// Klassen durchgehen
for (ClassMetadata clazz : metadata.getAllClasses()) {
    System.out.println("Class: " + clazz.getName());
    System.out.println("Table: " + clazz.getTableName());
    
    for (AttributeMetadata attr : clazz.getAllAttributes()) {
        System.out.println("  - " + attr.getName() + 
                         " : " + attr.getJavaType());
    }
}
```

## Internes Metamodell

### ModelMetadata
- Repräsentiert das gesamte Modell
- Enthält alle Klassen und Enumerationen
- ili2db-Einstellungen

### ClassMetadata
- INTERLIS-Klasse → Datenbanktabelle
- Attribute, Beziehungen
- Vererbungsinformationen
- Dokumentation

### AttributeMetadata
- INTERLIS-Attribut → Datenbankspalte
- Datentypen (INTERLIS, DB, Java)
- Constraints (NOT NULL, Length, Range)
- Enumerationen, Units
- Foreign Keys

### EnumMetadata
- INTERLIS-Enumeration
- Enum-Werte mit Reihenfolge
- Erweiterbarkeit

### RelationshipMetadata
- Beziehungen zwischen Klassen
- Kardinalität
- Typ (1:1, 1:n, n:m, Association)

## ili2db Metatabellen

Der Reader nutzt folgende ili2db-Tabellen:

| Tabelle | Inhalt |
|---------|--------|
| `t_ili2db_classname` | Mapping INTERLIS-Klasse → DB-Tabelle |
| `t_ili2db_attrname` | Mapping INTERLIS-Attribut → DB-Spalte |
| `t_ili2db_inheritance` | Vererbungshierarchie |
| `t_ili2db_trafo` | Transformationsregeln |
| `t_ili2db_settings` | Import-Einstellungen |
| `t_ili2db_column_prop` | Spalten-Properties (Constraints, Units) |
| `t_ili2db_model` | Importierte Modelle |

## Typ-Mapping

### INTERLIS → Java

| INTERLIS-Typ | Java-Typ |
|--------------|----------|
| TEXT | String |
| MTEXT | String |
| BOOLEAN | Boolean |
| DATE | LocalDate |
| DATETIME | LocalDateTime |
| COORD, MULTICOORD | Geometry (JTS) |
| Numerische Typen | Integer, Long, BigDecimal, Double |
| Enumerationen | String (oder enum-Klasse) |

## Nächste Schritte (Phase 2)

- [ ] Code-Generator für Grails Domains
- [ ] Constraints in Grails-Syntax übersetzen
- [ ] Beziehungen in GORM-Syntax generieren
- [ ] Enum-Klassen generieren
- [ ] Controller-Generierung (CRUD)
- [ ] View-Generierung (GSP)

## Test-Modell

Ein einfaches Test-Modell ist enthalten:
- `test-models/SimpleAddressModel.ili`
- Enthält: Address, Person, Association
- Demonstriert: Geometrie, Enum, Beziehungen

## Debugging

Für detailliertes Logging:

```bash
# Logback-Konfiguration anpassen
export JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
```

## Bekannte Limitierungen

- **Komplexe Geometrien**: Nur Basis-Unterstützung
- **Strukturen**: Werden wie Klassen behandelt
- **Mehrsprachigkeit**: Labels werden gelesen, aber noch nicht vollständig genutzt
- **Erweiterte Constraints**: Nur Basis-Constraints (Length, Range)

## Dependencies

- **ili2c** 5.5.2 - INTERLIS Compiler
- **PostgreSQL JDBC** 42.7.1
- **H2 Database** 2.2.224
- **SLF4J/Logback** - Logging
- **JUnit 5** - Testing

## Lizenz

Dieses Projekt ist ein Prototyp für den persönlichen/kommerziellen Gebrauch.

## Autor

Erstellt mit Claude (Anthropic) für INTERLIS CRUD-Generator Projekt
