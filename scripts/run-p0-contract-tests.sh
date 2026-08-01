#!/usr/bin/env bash
# Führt den obligatorischen Grails-/PostgreSQL-/ili2pg-Vertragstest aus.
# Scheitert, wenn ein erforderliches Werkzeug fehlt (kein Skip).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

command -v grails >/dev/null 2>&1 || {
    echo "ERROR: grails CLI not found in PATH (required for the mandatory contract test)" >&2
    exit 1
}
command -v docker >/dev/null 2>&1 || {
    echo "ERROR: docker not found in PATH (required for the mandatory contract test)" >&2
    exit 1
}
ILI2PG_HOME="${ILI2PG_HOME:-/Users/stefan/apps/ili2pg-5.5.1}"
if [ ! -f "${ILI2PG_HOME}/ili2pg-5.5.1.jar" ]; then
    echo "ERROR: ili2pg not found at ${ILI2PG_HOME}/ili2pg-5.5.1.jar (set ILI2PG_HOME)" >&2
    exit 1
fi

cd "${REPO_ROOT}"
"${REPO_ROOT}/gradlew" :target-grails:grailsPostgresContractTest \
    -PcontractTestRequired=true \
    -Pili2pgHome="${ILI2PG_HOME}" \
    --rerun-tasks --no-daemon
