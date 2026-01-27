# QUICKSTART - INTERLIS CRUD Generator Phase 1

## Installation in 3 Schritten

### 1. Voraussetzungen prüfen

**Java 17+** muss installiert sein:
```bash
java -version
# Sollte "17" oder höher anzeigen
```

Falls Java fehlt:
- **Linux/Mac**: `sudo apt install openjdk-17-jdk` oder `brew install openjdk@17`
- **Windows**: Download von https://adoptium.net/

### 2. Projekt entpacken

```bash
tar -xzf interlis-crud-generator-phase1.tar.gz
cd interlis-crud-generator
```

Oder das Verzeichnis `interlis-crud-generator` direkt verwenden.

### 3. Build

```bash
chmod +x build.sh
./build.sh
```

Oder direkt mit Gradle (falls installiert):
```bash
./gradlew build
```

**Das war's!** Das Projekt ist bereit.

---

## Erste Schritte

### Option A: Mit Test-Modell (empfohlen für erste Tests)

Das Projekt enthält ein fertiges Test-Modell.

**Ohne echte Datenbank (nur ili2c):**
```bash
# Nur das INTERLIS-Modell einlesen (ohne DB-Verbindung)
# Hierfür müssten Sie den Code leicht anpassen oder ein Mock verwenden
```

**Mit H2-Datenbank (embedded):**
```bash
# 1. Test-Datenbank vorbereiten
./demo.sh

# Das Script erstellt eine H2-Datenbank mit Beispieldaten
```

### Option B: Mit Ihrer eigenen INTERLIS-Datenbank

**Voraussetzung**: Eine Datenbank mit ili2db-importierten Daten

#### PostgreSQL Beispiel:
```bash
./gradlew run --args="'\
jdbc:postgresql://localhost:5432/meinedatenbank?user=postgres&password=geheim' \
/pfad/zu/MeinModell.ili \
MeinModellName \
public"
```

#### Eigene H2-Datenbank:
```bash
./gradlew run --args="'\
jdbc:h2:./daten/meine_db' \
/pfad/zu/MeinModell.ili \
MeinModellName"
```

**Parameter:**
1. JDBC-URL (mit Benutzername/Passwort)
2. Pfad zur .ili-Datei
3. Name des INTERLIS-Modells
4. Schema-Name (optional, default: auto-detect)

---

## Ausgabe verstehen

Das Programm gibt eine strukturierte Übersicht aus:

```
═══════════════════════════════════════════════════════════
INTERLIS Model Metadata
═══════════════════════════════════════════════════════════
Model Name:     SimpleAddressModel
Schema:         PUBLIC
ILI Version:    2.3
ili2db Version: 4.9.1
Classes:        3
Enumerations:   1
═══════════════════════════════════════════════════════════

CLASSES:
───────────────────────────────────────────────────────────

■ SimpleAddressModel.Addresses.Address
  Simple Name:  Address
  Table:        address
  Kind:         CLASS
  Abstract:     false
  Attributes:
    ◦ street               : String          [astreet]      NOT NULL (100)
    ◦ houseNumber          : String          [housenumber]  (10)
    ◦ postalCode           : String          [postalcode]   NOT NULL (10)
    ◦ city                 : String          [city]         NOT NULL (100)
    ◦ status               : String          [status]       
      → Enum: SimpleAddressModel.Addresses.AddressStatus
    ◦ position             : Geometry        [position]     GEOMETRY
    
...
```

**Legende:**
- `■` = Klasse
- `◦` = Attribut
- `PK` = Primary Key
- `FK` = Foreign Key
- `NOT NULL` = Pflichtfeld
- `(100)` = Maximale Länge

---

## Programmatische Verwendung

### In Ihrem Java-Projekt

**1. Dependency hinzufügen:**

Kopieren Sie die JAR oder verwenden Sie das Projekt als Modul.

**2. Code:**

```java
import ch.interlis.generator.metadata.MetadataReader;
import ch.interlis.generator.model.*;

import java.io.File;
import java.sql.*;

public class MeinGenerator {
    public static void main(String[] args) throws Exception {
        // Datenbank-Verbindung
        Connection conn = DriverManager.getConnection(
            "jdbc:postgresql://localhost:5432/mydb",
            "user", "password"
        );
        
        // Metadata Reader erstellen
        MetadataReader reader = new MetadataReader(
            conn,
            new File("models/MeinModell.ili"),
            "public",
            null
        );
        
        // Metadaten lesen
        ModelMetadata metadata = reader.readMetadata("MeinModellName");
        
        // Jetzt können Sie damit arbeiten!
        System.out.println("Modell: " + metadata.getModelName());
        System.out.println("Anzahl Klassen: " + metadata.getClasses().size());
        
        // Beispiel: Alle Klassen durchgehen
        for (ClassMetadata clazz : metadata.getAllClasses()) {
            System.out.println("\nKlasse: " + clazz.getSimpleName());
            System.out.println("Tabelle: " + clazz.getTableName());
            
            // Attribute
            for (AttributeMetadata attr : clazz.getAllAttributes()) {
                System.out.printf("  - %-20s : %s%n",
                    attr.getName(),
                    attr.getJavaType()
                );
            }
        }
        
        conn.close();
    }
}
```

---

## Tests ausführen

```bash
./gradlew test
```

Oder mit ausführlichem Output:
```bash
./gradlew test --info
```

---

## Troubleshooting

### "Java not found"
→ Java 17+ installieren (siehe oben)

### "Could not connect to database"
→ Prüfen Sie:
- Ist die Datenbank erreichbar?
- Sind Benutzername/Passwort korrekt?
- Firewall-Einstellungen

### "Table t_ili2db_classname not found"
→ Die Datenbank wurde nicht mit ili2db importiert
→ Verwenden Sie ili2db, um Ihr Modell zuerst zu importieren

### "Model file not found"
→ Prüfen Sie den Pfad zur .ili-Datei
→ Verwenden Sie absolute Pfade wenn möglich

### "Failed to compile INTERLIS model"
→ Modell-Syntax-Fehler
→ Fehlende Abhängigkeiten (andere Modelle)
→ Prüfen Sie mit ili2c direkt

---

## Nächste Schritte

1. **Metadaten inspizieren**: Schauen Sie sich die Ausgabe an
2. **Eigenes Modell testen**: Verwenden Sie Ihre INTERLIS-Datenbank
3. **Code-Generierung vorbereiten**: Phase 2 kommt als nächstes!

### Phase 2 Vorschau

In Phase 2 wird das Metamodell verwendet um zu generieren:
- **Grails Domain Classes** mit Constraints
- **Controller** mit CRUD-Operationen  
- **Views (GSP)** mit Formularen
- **Import/Export** via ili2db

---

## Hilfe & Support

- **Dokumentation**: Siehe `README.md` und `ARCHITECTURE.md`
- **Beispiele**: `test-models/SimpleAddressModel.ili`
- **Tests**: `src/test/java/...`

## Lizenz

Prototyp für persönlichen/kommerziellen Gebrauch.

---

**Viel Erfolg! 🚀**
