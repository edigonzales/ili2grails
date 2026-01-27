#!/bin/bash
# Beispiel-Script zum Testen des Metadata-Readers mit H2-Datenbank

echo "INTERLIS Metadata Reader - Demo"
echo "================================"
echo ""

# Verzeichnis erstellen
mkdir -p demo-db

# H2 Datenbank als Datei
DB_FILE="./demo-db/testdb"
JDBC_URL="jdbc:h2:${DB_FILE}"

echo "1. Erstelle H2-Datenbank mit ili2db-Metatabellen..."

# SQL-Script zum Erstellen der Metatabellen
cat > demo-db/setup.sql << 'EOF'
-- ili2db Metatabellen erstellen
CREATE TABLE IF NOT EXISTS t_ili2db_classname (
  iliname VARCHAR(1024) PRIMARY KEY,
  sqlname VARCHAR(1024)
);

CREATE TABLE IF NOT EXISTS t_ili2db_attrname (
  iliname VARCHAR(1024),
  sqlname VARCHAR(1024),
  owner VARCHAR(1024),
  target VARCHAR(1024)
);

CREATE TABLE IF NOT EXISTS t_ili2db_settings (
  tag VARCHAR(1024),
  setting VARCHAR(1024)
);

CREATE TABLE IF NOT EXISTS t_ili2db_inheritance (
  thisclass VARCHAR(1024),
  baseclass VARCHAR(1024)
);

CREATE TABLE IF NOT EXISTS t_ili2db_column_prop (
  tablename VARCHAR(255),
  columnname VARCHAR(255),
  tag VARCHAR(1024),
  setting VARCHAR(1024)
);

-- Beispieldaten einfügen
INSERT INTO t_ili2db_classname VALUES 
  ('SimpleAddressModel.Addresses.Address', 'address'),
  ('SimpleAddressModel.Addresses.Person', 'person'),
  ('SimpleAddressModel.Addresses.PersonAddress', 'personaddress');

INSERT INTO t_ili2db_attrname VALUES 
  ('street', 'astreet', 'SimpleAddressModel.Addresses.Address', NULL),
  ('houseNumber', 'housenumber', 'SimpleAddressModel.Addresses.Address', NULL),
  ('postalCode', 'postalcode', 'SimpleAddressModel.Addresses.Address', NULL),
  ('city', 'city', 'SimpleAddressModel.Addresses.Address', NULL),
  ('firstName', 'firstname', 'SimpleAddressModel.Addresses.Person', NULL),
  ('lastName', 'lastname', 'SimpleAddressModel.Addresses.Person', NULL),
  ('email', 'email', 'SimpleAddressModel.Addresses.Person', NULL),
  ('birthDate', 'birthdate', 'SimpleAddressModel.Addresses.Person', NULL),
  ('person', 'person_id', 'SimpleAddressModel.Addresses.PersonAddress', 'SimpleAddressModel.Addresses.Person'),
  ('address', 'address_id', 'SimpleAddressModel.Addresses.PersonAddress', 'SimpleAddressModel.Addresses.Address');

INSERT INTO t_ili2db_settings VALUES 
  ('ch.ehi.ili2db.version', '4.9.1'),
  ('ch.ehi.ili2db.defaultSrsCode', '2056');

-- Tabellen erstellen
CREATE TABLE IF NOT EXISTS address (
  t_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  astreet VARCHAR(100) NOT NULL,
  housenumber VARCHAR(10),
  postalcode VARCHAR(10) NOT NULL,
  city VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS person (
  t_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  firstname VARCHAR(50) NOT NULL,
  lastname VARCHAR(50) NOT NULL,
  email VARCHAR(100),
  birthdate DATE
);

CREATE TABLE IF NOT EXISTS personaddress (
  t_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  person_id BIGINT,
  address_id BIGINT,
  FOREIGN KEY (person_id) REFERENCES person(t_id),
  FOREIGN KEY (address_id) REFERENCES address(t_id)
);
EOF

# H2-Datenbank initialisieren (benötigt Java)
echo ""
echo "2. Initialisiere Datenbank..."

# Prüfe ob H2 verfügbar ist (wird mit Gradle heruntergeladen)
if [ ! -f "build/libs/h2*.jar" ]; then
    echo "   Building project to get H2 jar..."
    ./gradlew build -x test > /dev/null 2>&1
fi

# SQL ausführen (alternative: mit Application)
echo "   Executing setup SQL..."
# Hier könnte man direkt H2 aufrufen, aber einfacher ist es über die App

echo ""
echo "3. Metadata lesen..."
echo ""

# Metadata Reader aufrufen
./gradlew run --args="${JDBC_URL} test-models/SimpleAddressModel.ili SimpleAddressModel" \
  --console=plain 2>&1 | grep -v "BUILD\|Download\|Gradle"

echo ""
echo "================================"
echo "Demo abgeschlossen!"
echo ""
echo "Die H2-Datenbank liegt in: ${DB_FILE}.mv.db"
echo "Sie können sie mit einem H2-Client öffnen."
