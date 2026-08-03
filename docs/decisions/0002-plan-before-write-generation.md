# Plan-before-write-Generierung

## Status
Accepted

## Kontext
Der Generator schrieb zuvor Dateien direkt und führte auch bei blockierenden
Legacy-Diagnostics weitere Änderungen aus. Für einen Generator, der fremde,
potentiell benutzerbearbeitete Projekte verändert, ist das ein
Sicherheits- und Vertrauensproblem.

## Entscheidung
Vor dem ersten Schreibzugriff sind alle Änderungen und alle Blocker bekannt:
`GrailsGenerationPlanner.plan(...)` erzeugt den vollständigen
`GenerationPlan` (CREATE/UPDATE/DELETE/UNCHANGED/BLOCKED pro Datei, alle
Diagnostics) ohne Dateiänderung. `GrailsGenerationExecutor.apply(...)`
schreibt atomar (temporäre Datei + Move), löscht zuletzt und publiziert das
Manifest zuletzt. Bei einer einzigen blockierenden Diagnostic wird keine
Projektdatei verändert. Alle Generatoren besitzen reine `plan()`-Funktionen;
`GrailsCrudGenerator` nutzt nur `plan/apply/generate`.

## Konsequenzen
- Benutzerveränderte verwaltete Dateien blockieren den gesamten Apply
  (`USER_MODIFIED_MANAGED_FILE`).
- Das Manifest (`.ili2grails/generation-manifest.json`) ist die Wahrheit für
  generatorverwaltete Dateien; zweite identische Generationen sind
  idempotent.
- Direkte Produktions-Schreibzugriffe sind auf Executor und Manifest-Store
  beschränkt (durch Guard-Test erzwungen).

## Verworfene Alternativen
- Weiterschreiben ohne Plan (bisheriges Verhalten, Risiko unkontrollierter
  Nebenwirkungen).
- Generische Workflow-Engine (überdimensioniert).
