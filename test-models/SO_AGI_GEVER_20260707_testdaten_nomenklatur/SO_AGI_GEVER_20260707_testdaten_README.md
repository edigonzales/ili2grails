# SO_AGI_GEVER_20260707 – Testdaten Nomenklatur

Diese Testdaten bilden ein fiktives, aber fachlich plausibles GEVER-Szenario ab:

- Antrag der Gemeinde Musterwil zur Umbenennung des Flurnamens "Im alten Boden" in "Bodenrain"
- fachliche Prüfung durch die Fachstelle Nomenklatur
- Entscheid der kantonalen Nomenklaturkommission
- Mitteilung an die Gemeinde
- Nachführung im Fachsystem / in der amtlichen Vermessung
- Abschluss des Dossiers

## Dateien

- `SO_AGI_GEVER_20260707_testdaten_nomenklatur.xtf`: kompletter Datensatz mit drei Baskets
- `01_SO_AGI_GEVER_20260707_testdaten_kataloge.xtf`: nur Katalogdaten
- `02_SO_AGI_GEVER_20260707_testdaten_stammdaten.xtf`: nur Stammdaten
- `03_SO_AGI_GEVER_20260707_testdaten_geschaeftsdaten.xtf`: nur operative Geschäftsdaten

## Importidee

1. Kataloge importieren
2. Stammdaten importieren
3. Geschäftsdaten importieren

Für eine reine Validierung ist der kombinierte XTF meist am einfachsten, weil alle referenzierten Objekte in derselben Transferdatei enthalten sind.

Hinweis: Die Datei wurde syntaktisch als XML geprüft. Eine vollständige INTERLIS-Validierung mit `ili2c`/`ilivalidator` konnte in dieser Umgebung nicht durchgeführt werden.
