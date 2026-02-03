# QUICKSTART – INTERLIS CRUD Generator

Kurzfassung für Installation und Start. Die ausführliche Benutzeranleitung inkl. Architektur- und Entscheidungs-Context steht in der [README.md](README.md).

## Voraussetzungen
- Java 17+
- ili2db-Datenbank + passende `.ili`-Datei

## Build
```bash
./gradlew build
```

## Start (CLI)
**PostgreSQL:**
```bash
./gradlew run --args="'jdbc:postgresql://localhost:5432/mydb?user=postgres&password=secret' \
  test-models/SimpleAddressModel.ili \
  SimpleAddressModel \
  public"
```

Weitere Details: [README.md](README.md)
