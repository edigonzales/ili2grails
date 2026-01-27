# Phase 1 Implementation Details

## Architektur-Entscheidungen

### Hybrid-Ansatz: ili2db + ili2c

**Entscheidung:** Kombination beider Informationsquellen

**Begründung:**
1. **ili2db Metatabellen** liefern die Basis-Struktur:
   - Exaktes Mapping INTERLIS → Datenbank
   - Tatsächlich verwendete Tabellen-/Spaltennamen
   - Transformationsregeln (Vererbungsstrategien)
   - Schneller Zugriff (keine Modell-Kompilierung nötig)

2. **ili2c Modell** liefert semantische Informationen:
   - Vollständige Constraint-Definitionen
   - Dokumentation und mehrsprachige Labels
   - Units und genaue Wertebereichs-Definitionen
   - Enum-Definitionen mit Hierarchie

**Ablauf:**
```
1. Lies ili2db Metatabellen → Basis-Struktur
2. Kompiliere INTERLIS-Modell → Semantik
3. Merge beide Informationen → Vollständiges Metamodell
```

### Internes Metamodell

**Design-Prinzipien:**
- Unabhängig von ili2db und ili2c
- Framework-agnostisch (nutzbar für Grails, Spring, etc.)
- Enthält alle Informationen für Code-Generierung
- Erweiterbar für zusätzliche Metadaten

**Kernklassen:**

```
ModelMetadata
├── ClassMetadata (n)
│   ├── AttributeMetadata (n)
│   └── RelationshipMetadata (n)
└── EnumMetadata (n)
    └── EnumValue (n)
```

## ili2db Metatabellen-Schema

### t_ili2db_classname
Mapping INTERLIS-Klasse → DB-Tabelle

```sql
CREATE TABLE t_ili2db_classname (
  iliname VARCHAR(1024),    -- qualifizierter INTERLIS Name
  sqlname VARCHAR(1024)     -- DB-Tabellenname
);
```

**Beispiel:**
```
iliname: "DM01.BodenBedeckung.BoFlaeche"
sqlname: "bodenbedeckung_boflaeche"
```

### t_ili2db_attrname
Mapping INTERLIS-Attribut → DB-Spalte

```sql
CREATE TABLE t_ili2db_attrname (
  iliname VARCHAR(1024),    -- INTERLIS Attributname
  sqlname VARCHAR(1024),    -- DB-Spaltenname
  owner VARCHAR(1024),      -- INTERLIS Klassenname
  target VARCHAR(1024)      -- Ziel-Klasse (bei FK)
);
```

**Beispiel FK:**
```
iliname: "gebaeude"
sqlname: "gebaeude_tid"
owner: "DM01.Liegenschaften.Liegenschaft"
target: "DM01.Liegenschaften.Gebaeude"
```

### t_ili2db_inheritance
Vererbungshierarchie

```sql
CREATE TABLE t_ili2db_inheritance (
  thisclass VARCHAR(1024),  -- Abgeleitete Klasse
  baseclass VARCHAR(1024)   -- Basisklasse
);
```

### t_ili2db_trafo
Transformationsstrategien

Mögliche Strategien:
- **Smart1Inheritance**: Standard, intelligente Vererbung
- **Smart2Inheritance**: Alternative Strategie
- **NoSmartMapping**: Keine intelligente Mappings

### t_ili2db_column_prop
Spalten-Properties

```sql
CREATE TABLE t_ili2db_column_prop (
  tablename VARCHAR(255),
  columnname VARCHAR(255),
  tag VARCHAR(1024),        -- Property-Name
  setting VARCHAR(1024)     -- Property-Wert
);
```

**Wichtige Tags:**
- `ch.ehi.ili2db.unit` - Masseinheit
- `ch.ehi.ili2db.enumDomain` - Enum-Typ
- `ch.ehi.ili2db.dispName` - Anzeigename
- `ch.ehi.ili2db.textKind` - Text-Art (MTEXT)

## ili2c Integration

### TransferDescription
Zentrale Klasse von ili2c:

```java
TransferDescription td = Ili2c.runCompiler(config);

// Model abrufen
Model model = (Model) td.getElement(Model.class, "ModelName");

// Topics durchgehen
Iterator topicIter = model.iterator();
while (topicIter.hasNext()) {
    Topic topic = (Topic) topicIter.next();
    
    // Klassen im Topic
    Iterator classIter = topic.iterator();
    while (classIter.hasNext()) {
        Table table = (Table) classIter.next();
        // ...
    }
}
```

### Wichtige ili2c-Klassen

- **Model**: INTERLIS-Modell
- **Topic**: Thema (Gruppierung von Klassen)
- **Table**: Klasse oder Struktur
- **AssociationDef**: Assoziationsklasse
- **AttributeDef**: Attribut-Definition
- **Type**: Typen-Hierarchie
  - TextType, NumericType, EnumerationType
  - CoordType, GeometryType
  - ReferenceType

### Typ-Extraktion

```java
Type type = attrDef.getDomain();

if (type instanceof TextType) {
    TextType textType = (TextType) type;
    int maxLength = textType.getMaxLength();
}
else if (type instanceof NumericType) {
    NumericType numType = (NumericType) type;
    PrecisionDecimal min = numType.getMinimum();
    PrecisionDecimal max = numType.getMaximum();
}
else if (type instanceof EnumerationType) {
    EnumerationType enumType = (EnumerationType) type;
    Enumeration enumeration = enumType.getConsolidatedEnumeration();
}
```

## Java Typ-Mapping

### Mapping-Strategie

1. **Geometrie-Typen → JTS Geometry**
   ```
   COORD, MULTICOORD → org.locationtech.jts.geom.Geometry
   ```

2. **Text-Typen → String**
   ```
   TEXT, MTEXT → String
   ```

3. **Datum/Zeit → java.time**
   ```
   INTERLIS.XMLDate → LocalDate
   INTERLIS.XMLDateTime → LocalDateTime
   ```

4. **Numerische Typen**
   ```
   0..100 → Integer
   0..1000000000 → Long
   0.00..999.99 → BigDecimal
   ```

5. **Enumerationen → String** (oder enum-Klasse)

### Attribut-Anreicherung

```java
// Aus ili2db DB-Schema
attr.setDbType("VARCHAR(100)");
attr.setMandatory(true);
attr.setColumnName("astreet");

// Aus ili2c Modell
attr.setIliType("TEXT");
attr.setMaxLength(100);
attr.setDocumentation("Strassenname");

// Inferenz
attr.inferJavaType(); // → "String"
```

## Beziehungs-Extraktion

### Foreign Keys (ili2db)

```sql
-- t_ili2db_attrname
iliname: "gebaeude"
target: "DM01.Gebaeude"

-- Wird zu:
RelationshipMetadata {
  type: MANY_TO_ONE,
  sourceClass: "DM01.Liegenschaft",
  targetClass: "DM01.Gebaeude",
  sourceAttribute: "gebaeude",
  targetAttribute: "T_Id"
}
```

### Assoziationen (ili2c)

```interlis
ASSOCIATION PersonAddress =
  person -- {0..*} Person;
  address -- {0..1} Address;
END PersonAddress;
```

Wird erkannt als separate Klasse mit 2 FKs.

## Vererbung

### ili2db Strategien

**Smart1Inheritance** (default):
- Abstrakte Klassen: keine Tabelle
- Konkrete Klassen: eigene Tabelle + geerbte Attribute

**Smart2Inheritance**:
- Alle Attribute in Subklassen-Tabellen

**NoSmartMapping**:
- Jede Klasse → eigene Tabelle
- Joins für Vererbungshierarchie

### Beispiel

```interlis
CLASS Shape ABSTRACT =
  color: TEXT;
END Shape;

CLASS Circle EXTENDS Shape =
  radius: NUMERIC;
END Circle;
```

**Smart1** → nur Tabelle `circle` mit `color` und `radius`

## Erweiterungs-Punkte

### Für Phase 2 (Code-Generation)

Das Metamodell kann erweitert werden um:

1. **Validierungs-Metadaten**
   ```java
   class ValidationRule {
     String expression;
     String message;
   }
   ```

2. **UI-Hints**
   ```java
   class UIMetadata {
     boolean searchable;
     boolean sortable;
     String widgetType;
   }
   ```

3. **Security-Metadaten**
   ```java
   class SecurityMetadata {
     List<String> readRoles;
     List<String> writeRoles;
   }
   ```

### Custom Annotations

Später können INTERLIS-Metadaten als Annotations ausgelesen werden:

```interlis
!!@ description = "Haupt-Adresse"
!!@ indexed = true
CLASS Address = ...
```

## Performance-Überlegungen

### Caching

Für große Modelle:
```java
class MetadataCache {
  Map<String, ModelMetadata> cache;
  
  ModelMetadata get(String modelName) {
    return cache.computeIfAbsent(modelName, 
      k -> reader.readMetadata(k));
  }
}
```

### Lazy Loading

Nur benötigte Teile laden:
```java
class LazyModelMetadata {
  // Klassen nur bei Bedarf laden
  private Map<String, ClassMetadata> lazyClasses;
}
```

## Fehlerbehandlung

### ili2db Probleme

- **Tabellen fehlen**: Model nicht mit ili2db importiert
- **Schema nicht gefunden**: Falscher Schema-Name
- **Metatabellen fehlen**: Alte ili2db-Version

### ili2c Probleme

- **Modell-Datei fehlt**: Pfad prüfen
- **Kompilierungsfehler**: INTERLIS-Syntax-Fehler
- **Abhängigkeiten fehlen**: Model-Repository nicht erreichbar

### Robustheit

```java
try {
  metadata = reader.readMetadata(modelName);
} catch (SQLException e) {
  // DB-Probleme → Fallback auf nur ili2c?
} catch (Ili2cFailure e) {
  // Modell-Probleme → nur ili2db?
}
```

## Testing-Strategie

### Unit-Tests
- Einzelne Klassen isoliert testen
- Mocks für DB und ili2c

### Integration-Tests
- H2 Memory-DB mit simulierten ili2db-Tabellen
- Echte ili2c-Kompilierung mit Test-Modell

### End-to-End-Tests
- Echte PostgreSQL/PostGIS-DB
- Vollständiger ili2db-Import
- Komplexe Real-World-Modelle

## Nächste Schritte (Phase 2)

1. **Grails Domain Generator**
   ```
   ClassMetadata → Grails Domain Class
   - Constraints
   - Relationships (hasMany, belongsTo)
   - Custom validators
   ```

2. **Controller Generator**
   ```
   ClassMetadata → Grails Controller
   - CRUD operations
   - Search/Filter
   - Validation
   ```

3. **View Generator**
   ```
   ClassMetadata → GSP Views
   - List view
   - Show view
   - Create/Edit forms
   - Validation messages
   ```

4. **Import/Export Integration**
   ```
   - ili2db wrapper für Import
   - ili2db wrapper für Export
   - Validierung vor Export
   ```
