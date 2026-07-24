#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

ILI2PG_VERSION="5.5.1"
ILI2PG_HOME="${ILI2PG_HOME:-/Users/stefan/apps/ili2pg-${ILI2PG_VERSION}}"
ILI2PG_JAR="$ILI2PG_HOME/ili2pg-${ILI2PG_VERSION}.jar"

DB_HOST="localhost"
DB_PORT="54321"
DB_DATABASE="edit"
DB_USER="postgres"
DB_PASSWORD="secret"

RESET=false
MODEL_VARIANT=""

usage() {
    cat <<'EOF'
Usage:
  scripts/getting-started.sh <simple|advanced> [--reset]

Examples:
  scripts/getting-started.sh simple
  scripts/getting-started.sh advanced --reset

The script starts the local Docker database, imports the selected INTERLIS
model and demo data, reads metadata, and generates a Grails application.

Options:
  --reset    Drop only the selected gs_* schema and remove its generated output
             before rebuilding it.
  -h, --help Show this help.

Environment:
  ILI2PG_HOME  ili2pg installation directory
              (default: /Users/stefan/apps/ili2pg-5.5.1)
EOF
}

fail() {
    echo "Fehler: $*" >&2
    exit 1
}

require_command() {
    local command_name="$1"

    command -v "$command_name" >/dev/null 2>&1 \
        || fail "Befehl nicht gefunden: $command_name"
}

parse_arguments() {
    if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
        usage
        exit 0
    fi

    if [[ $# -lt 1 || $# -gt 2 ]]; then
        usage >&2
        exit 2
    fi

    MODEL_VARIANT="$1"
    case "$MODEL_VARIANT" in
        simple|advanced)
            ;;
        *)
            usage >&2
            fail "Unbekannte Modellvariante '$MODEL_VARIANT'; erwartet wird simple oder advanced."
            ;;
    esac

    if [[ $# -eq 2 ]]; then
        if [[ "$2" != "--reset" ]]; then
            usage >&2
            fail "Unbekannte Option '$2'."
        fi
        RESET=true
    fi
}

configure_model() {
    case "$MODEL_VARIANT" in
        simple)
            MODEL_NAME="GsSimpleModel"
            DB_SCHEMA="gs_simple"
            DATA_FILE="$REPO_ROOT/docs/getting-started/data/GsSimpleModel.xtf"
            MODEL_FILE="$REPO_ROOT/docs/getting-started/models/GsSimpleModel.ili"
            METADATA_DIR="$REPO_ROOT/build/getting-started/gs-simple"
            GRAILS_OUTPUT="$REPO_ROOT/build/getting-started/grails-simple"
            GRAILS_APP_NAME="simple-app"
            GRAILS_PACKAGE="ch.example.gssimple"
            GRAILS_MAP_EDITOR="none"
            ;;
        advanced)
            MODEL_NAME="GsAdvancedModel"
            DB_SCHEMA="gs_advanced"
            DATA_FILE="$REPO_ROOT/docs/getting-started/data/GsAdvancedModel.xtf"
            MODEL_FILE="$REPO_ROOT/docs/getting-started/models/GsAdvancedModel.ili"
            METADATA_DIR="$REPO_ROOT/build/getting-started/gs-advanced"
            GRAILS_OUTPUT="$REPO_ROOT/build/getting-started/grails-advanced"
            GRAILS_APP_NAME="advanced-app"
            GRAILS_PACKAGE="ch.example.gsadvanced"
            GRAILS_MAP_EDITOR="openlayers"
            ;;
    esac

    METADATA_JSON="$METADATA_DIR/metadata.json"
    MERGE_REPORT="$METADATA_DIR/merge-report"
    GRAILS_APP_DIR="$GRAILS_OUTPUT/$GRAILS_APP_NAME"
    JDBC_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_DATABASE}?user=${DB_USER}&password=${DB_PASSWORD}&dbSchema=${DB_SCHEMA}"
}

validate_inputs() {
    require_command java
    require_command docker
    require_command grails

    [[ -x "$REPO_ROOT/gradlew" ]] \
        || fail "Gradle Wrapper nicht gefunden oder nicht ausführbar: $REPO_ROOT/gradlew"

    docker compose version >/dev/null 2>&1 \
        || fail "Docker Compose ist nicht verfügbar."

    [[ -d "$ILI2PG_HOME" ]] \
        || fail "ili2pg-Verzeichnis nicht gefunden: $ILI2PG_HOME"
    [[ -f "$ILI2PG_JAR" ]] \
        || fail "ili2pg-JAR nicht gefunden: $ILI2PG_JAR"
    [[ -d "$ILI2PG_HOME/libs" ]] \
        || fail "ili2pg-Libs-Verzeichnis nicht gefunden: $ILI2PG_HOME/libs"

    [[ -f "$MODEL_FILE" ]] \
        || fail "INTERLIS-Modell nicht gefunden: $MODEL_FILE"
    [[ -f "$DATA_FILE" ]] \
        || fail "Demodaten nicht gefunden: $DATA_FILE"

    if [[ "$RESET" != true && ( -e "$METADATA_DIR" || -e "$GRAILS_OUTPUT" ) ]]; then
        fail "Getting-Started-Ausgabe existiert bereits für $MODEL_VARIANT. Mit --reset neu aufbauen: $METADATA_DIR bzw. $GRAILS_OUTPUT"
    fi
}

run_ili2pg() {
    java -cp "$ILI2PG_JAR:$ILI2PG_HOME/libs/*" ch.ehi.ili2pg.PgMain "$@"
}

start_database() {
    echo "==> Docker-Datenbank starten"
    docker compose up -d edit-db

    echo "==> Auf PostgreSQL warten"
    local attempt
    for ((attempt = 1; attempt <= 30; attempt++)); do
        if docker compose exec -T edit-db pg_isready -U "$DB_USER" -d "$DB_DATABASE" >/dev/null 2>&1; then
            return
        fi
        sleep 2
    done

    fail "PostgreSQL ist nach 60 Sekunden nicht verfügbar."
}

run_psql() {
    docker compose exec -T edit-db psql \
        -v ON_ERROR_STOP=1 \
        -U "$DB_USER" \
        -d "$DB_DATABASE" \
        "$@"
}

schema_exists() {
    local result
    if ! result="$(run_psql -tAc "SELECT 1 FROM pg_namespace WHERE nspname = '$DB_SCHEMA'")"; then
        fail "Datenbankschema konnte nicht geprüft werden: $DB_SCHEMA"
    fi
    result="$(printf '%s' "$result" | tr -d '[:space:]')"
    [[ "$result" == "1" ]]
}

prepare_targets() {
    if schema_exists; then
        if [[ "$RESET" != true ]]; then
            fail "Datenbankschema existiert bereits: $DB_SCHEMA. Mit --reset neu aufbauen."
        fi

        echo "==> Vorhandenes Schema löschen: $DB_SCHEMA"
        run_psql -c "DROP SCHEMA IF EXISTS $DB_SCHEMA CASCADE"
    fi

    if [[ "$RESET" == true ]]; then
        echo "==> Vorhandene Getting-Started-Ausgabe löschen"
        rm -rf "$METADATA_DIR" "$GRAILS_OUTPUT"
    fi
}

import_schema_and_data() {
    echo "==> ili2pg-Schema erzeugen: $DB_SCHEMA"
    run_ili2pg \
        --dbhost "$DB_HOST" \
        --dbport "$DB_PORT" \
        --dbdatabase "$DB_DATABASE" \
        --dbusr "$DB_USER" \
        --dbpwd "$DB_PASSWORD" \
        --defaultSrsCode 2056 \
        --createFk \
        --nameByTopic \
        --strokeArcs \
        --smart2Inheritance \
        --createEnumTabs \
        --modeldir "$REPO_ROOT/docs/getting-started/models" \
        --models "$MODEL_NAME" \
        --dbschema "$DB_SCHEMA" \
        --schemaimport

    echo "==> Demodaten importieren"
    run_ili2pg \
        --dbhost "$DB_HOST" \
        --dbport "$DB_PORT" \
        --dbdatabase "$DB_DATABASE" \
        --dbusr "$DB_USER" \
        --dbpwd "$DB_PASSWORD" \
        --defaultSrsCode 2056 \
        --createFk \
        --nameByTopic \
        --strokeArcs \
        --smart2Inheritance \
        --createEnumTabs \
        --modeldir "$REPO_ROOT/docs/getting-started/models" \
        --models "$MODEL_NAME" \
        --dbschema "$DB_SCHEMA" \
        --import "$DATA_FILE"
}

build_generator() {
    echo "==> Generator bauen"
    (cd "$REPO_ROOT" && ./gradlew :cli:installDist)

    GENERATOR="$REPO_ROOT/cli/build/install/cli/bin/cli"
    [[ -x "$GENERATOR" ]] \
        || fail "Generator-Executable nicht gefunden: $GENERATOR"
}

read_metadata() {
    echo "==> Metadaten lesen"
    "$GENERATOR" read \
        "$JDBC_URL" \
        "$MODEL_NAME" \
        "$DB_SCHEMA" \
        --model-file "$MODEL_FILE" \
        --metadata-json "$METADATA_JSON" \
        --merge-report "$MERGE_REPORT"
}

generate_grails_app() {
    echo "==> Grails-App erzeugen: $GRAILS_APP_DIR"
    "$GENERATOR" generate \
        "$JDBC_URL" \
        "$MODEL_NAME" \
        "$DB_SCHEMA" \
        --target grails \
        --model-file "$MODEL_FILE" \
        --grails-output "$GRAILS_OUTPUT" \
        --grails-init "$GRAILS_APP_NAME" \
        --grails-package "$GRAILS_PACKAGE" \
        --grails-ui-theme bootstrap \
        --grails-map-editor "$GRAILS_MAP_EDITOR" \
        --grails-default-srid 2056 \
        --grails-generate-all
}

print_result() {
    cat <<EOF

Getting-Started-Lauf abgeschlossen: $MODEL_VARIANT

Metadaten:
  $METADATA_JSON
Merge-Report:
  $MERGE_REPORT
Grails-Projekt:
  $GRAILS_APP_DIR

Anwendung starten:
  cd "$GRAILS_APP_DIR"
  DB_USERNAME=$DB_USER DB_PASSWORD=$DB_PASSWORD ./gradlew bootRun
EOF
}

main() {
    parse_arguments "$@"
    configure_model
    validate_inputs
    start_database
    prepare_targets
    import_schema_and_data
    build_generator
    read_metadata
    generate_grails_app
    print_result
}

main "$@"
