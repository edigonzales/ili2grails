#!/bin/bash
# Beispiel-Script zum Testen des Metadata-Readers mit PostgreSQL

echo "INTERLIS Metadata Reader - Demo"
echo "================================"
echo ""

JDBC_URL="${JDBC_URL:-jdbc:postgresql://localhost:5432/mydb?user=postgres&password=secret}"

echo "1. Metadata lesen..."
echo ""

# Metadata Reader aufrufen
./gradlew run --args="${JDBC_URL} test-models/SimpleAddressModel.ili SimpleAddressModel" \
  --console=plain 2>&1 | grep -v "BUILD\|Download\|Gradle"

echo ""
echo "================================"
echo "Demo abgeschlossen!"
echo ""
echo "Verwendete JDBC-URL: ${JDBC_URL}"
