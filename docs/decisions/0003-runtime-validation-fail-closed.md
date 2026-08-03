# Runtime-Validierung fail-closed

## Status
Accepted

## Kontext
Das Runtime-Plugin loggte bei deaktiviertem Strict-Modus, fehlerhafte
Schreibfunktionen würden herabgestuft, ohne dass dies technisch erzwungen
wurde. Eine Registry konnte beim Startup gültig erscheinen, obwohl die
Abbildung nicht zur Datenbank passte.

## Entscheidung
Ein globaler `InterlisRuntimeSafetyState` erzwingt die Semantik technisch:

- gültige Registry: Schreiben erlaubt;
- ungültige Registry und Strict-Modus: Startup-Fehler;
- ungültige Registry und Non-strict-Modus: Anwendung startet read-only,
  alle generierten Schreiboperationen sind blockiert.

Command-Services prüfen den Safety-State vor jeder Schreiboperation und
liefern typisierte Read-only-Ergebnisse (`RUNTIME_DESCRIPTOR_INVALID`).
Flows und Views bieten Schreibaktionen nicht mehr an. Die fachliche
`InterlisAuthorizationPolicy` bleibt davon getrennt. Logging und
Dokumentation beschreiben exakt das tatsächliche Verhalten.

## Konsequenzen
- Keine stillen Per-Feature-Downgrades mehr; der Non-strict-Modus ist
  technisch read-only.
- Auch ein nicht ausführbarer Validator führt zu read-only (fail-closed).

## Verworfene Alternativen
- Per-Feature-Downgrade der Deskriptoren (immutable IR, nicht durchsetzbar).
- Nur UI-Buttons ausblenden (direkte Command-Aufrufe bleiben möglich).
