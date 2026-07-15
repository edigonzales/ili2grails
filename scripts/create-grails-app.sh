#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

usage() {
    cat <<'EOF'
Usage:
  scripts/create-grails-app.sh <app-name>

Example:
  scripts/create-grails-app.sh styling-lab

Environment overrides:
  MODEL_FILE       INTERLIS model file relative to the repo root
                   (default: test-models/SimpleAddressModel.ili)
  MODEL_NAME       INTERLIS model name (default: SimpleAddressModel)
  DB_SCHEMA        Database schema (default: sa)
  JDBC_URL         JDBC URL including credentials (default: local Docker DB)
  BASE_PACKAGE     Java/Groovy base package (default: ch.example.demo)
  UI_THEME         UI theme: default|bootstrap (default: bootstrap)
  MAP_EDITOR       Map editor: none|openlayers (default: openlayers)
  OUTPUT_ROOT      Parent output directory relative to repo root
                   (default: generated-grails)
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi

if [[ $# -ne 1 || -z "${1:-}" ]]; then
    usage >&2
    exit 2
fi

APP_NAME="$1"
MODEL_FILE="${MODEL_FILE:-test-models/SimpleAddressModel.ili}"
MODEL_NAME="${MODEL_NAME:-SimpleAddressModel}"
DB_SCHEMA="${DB_SCHEMA:-sa}"
JDBC_URL="${JDBC_URL:-jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=$DB_SCHEMA}"
BASE_PACKAGE="${BASE_PACKAGE:-ch.example.demo}"
UI_THEME="${UI_THEME:-bootstrap}"
MAP_EDITOR="${MAP_EDITOR:-openlayers}"
OUTPUT_ROOT="${OUTPUT_ROOT:-generated-grails}"

if [[ "$MODEL_FILE" = /* ]]; then
    MODEL_FILE_ABSOLUTE="$MODEL_FILE"
else
    MODEL_FILE_ABSOLUTE="$REPO_ROOT/$MODEL_FILE"
fi

if [[ "$OUTPUT_ROOT" = /* ]]; then
    OUTPUT_ROOT_ABSOLUTE="$OUTPUT_ROOT"
else
    OUTPUT_ROOT_ABSOLUTE="$REPO_ROOT/$OUTPUT_ROOT"
fi

APP_DIR="$OUTPUT_ROOT_ABSOLUTE/$APP_NAME"
GENERATOR="$REPO_ROOT/cli/build/install/cli/bin/cli"

if [[ ! -x "$REPO_ROOT/gradlew" ]]; then
    echo "Fehler: Gradle Wrapper nicht gefunden: $REPO_ROOT/gradlew" >&2
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "Fehler: Java ist nicht im PATH." >&2
    exit 1
fi

JAVA_VERSION="$(java -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*"/\1/p' | head -n 1)"
if [[ -z "$JAVA_VERSION" || "$JAVA_VERSION" -lt 17 || "$JAVA_VERSION" -gt 21 ]]; then
    echo "Fehler: Dieses Repo benötigt Java 17 bis 21; gefunden wurde Java ${JAVA_VERSION:-unbekannt}." >&2
    echo "Beispiel: export JAVA_HOME=/Users/<user>/.sdkman/candidates/java/21.0.7-tem" >&2
    exit 1
fi

if ! command -v grails >/dev/null 2>&1; then
    echo "Fehler: Grails ist nicht im PATH." >&2
    exit 1
fi

if [[ ! -f "$MODEL_FILE_ABSOLUTE" ]]; then
    echo "Fehler: INTERLIS-Modell nicht gefunden: $MODEL_FILE_ABSOLUTE" >&2
    exit 1
fi

if [[ -e "$APP_DIR" ]]; then
    echo "Fehler: Zielprojekt existiert bereits: $APP_DIR" >&2
    echo "Bitte einen anderen App-Namen oder OUTPUT_ROOT verwenden." >&2
    exit 1
fi

cd "$REPO_ROOT"

echo "==> Generator bauen"
./gradlew :cli:installDist

if [[ ! -x "$GENERATOR" ]]; then
    echo "Fehler: Generator-Executable nicht gefunden: $GENERATOR" >&2
    exit 1
fi

echo "==> Grails-Projekt erzeugen: $APP_DIR"
echo "    Theme: $UI_THEME"
echo "    Modell: $MODEL_NAME"

"$GENERATOR" generate \
    "$JDBC_URL" \
    "$MODEL_NAME" \
    "$DB_SCHEMA" \
    --target grails \
    --model-file "$MODEL_FILE_ABSOLUTE" \
    --grails-output "$APP_DIR" \
    --grails-init \
    --grails-package "$BASE_PACKAGE" \
    --grails-ui-theme "$UI_THEME" \
    --grails-map-editor "$MAP_EDITOR" \
    --grails-generate-all

cat <<EOF

Projekt erstellt:
  $APP_DIR

Starten:
  cd "$APP_DIR"
  DB_USERNAME=postgres DB_PASSWORD=secret ./gradlew bootRun
EOF
