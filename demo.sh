#!/bin/bash
# Beispiel-Script zum Testen des Metadata-Readers mit SQLite/GeoPackage

echo "INTERLIS Metadata Reader - Demo"
echo "================================"
echo ""

DB_FILE="./testdb.gpkg"
JDBC_URL="jdbc:sqlite:${DB_FILE}"

if [ ! -f "${DB_FILE}" ]; then
  echo "SQLite-DB nicht gefunden: ${DB_FILE}"
  echo "Bitte stelle sicher, dass testdb.gpkg im Repo liegt."
  exit 1
fi

echo "1. Metadata lesen..."
echo ""

# Metadata Reader aufrufen
./gradlew run --args="${JDBC_URL} test-models/SimpleAddressModel.ili SimpleAddressModel" \
  --console=plain 2>&1 | grep -v "BUILD\|Download\|Gradle"

echo ""
echo "================================"
echo "Demo abgeschlossen!"
echo ""
echo "Die SQLite-Datenbank liegt in: ${DB_FILE}"
echo "Sie können sie mit einem GeoPackage-/SQLite-Client öffnen."
