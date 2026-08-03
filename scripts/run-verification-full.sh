#!/usr/bin/env bash
# Vollständige Verifikation (Spezifikation §58): verificationFull mit allen
# erweiterten Tests im Required-Modus. Erfordert Grails-CLI, Docker Compose
# und ILI2PG_HOME; jede fehlende Abhängigkeit ist ein Fehler, kein Skip.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

command -v grails >/dev/null 2>&1 || {
    echo "ERROR: grails CLI not found in PATH (required for verificationFull)" >&2
    exit 1
}
command -v docker >/dev/null 2>&1 || {
    echo "ERROR: docker not found in PATH (required for verificationFull)" >&2
    exit 1
}
docker compose version >/dev/null 2>&1 || {
    echo "ERROR: docker compose not available (required for verificationFull)" >&2
    exit 1
}
if [ -z "${ILI2PG_HOME:-}" ]; then
    echo "ERROR: ILI2PG_HOME is not set (required for verificationFull)" >&2
    exit 1
fi
if [ ! -f "${ILI2PG_HOME}/ili2pg-5.5.1.jar" ]; then
    echo "ERROR: ili2pg-5.5.1.jar not found in ILI2PG_HOME (${ILI2PG_HOME})" >&2
    exit 1
fi

cd "${REPO_ROOT}"
"${REPO_ROOT}/gradlew" verificationFull \
    -PgrailsRuntimeSmokeRequired=true \
    -PrealIli2dbRequired=true \
    -PcontractTestRequired=true \
    -PbrowserE2eRequired=true \
    -Pili2pgHome="${ILI2PG_HOME}" \
    --rerun-tasks --no-daemon

echo
echo "Verification reports: ${REPO_ROOT}/build/reports/ili2grails-verification/"
echo "Contract reports:     ${REPO_ROOT}/target-grails/build/reports/grails-postgres-contract/"
echo "Corpus reports:       ${REPO_ROOT}/build/reports/model-corpus/"
