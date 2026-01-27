# Architektur – INTERLIS CRUD Generator

Die Architektur- und Entscheidungsdetails sind jetzt in der [README.md](README.md) konsolidiert, damit der Kontext an einer Stelle gepflegt wird.

## Kurzüberblick
- Hybrid-Ansatz: **ili2db + ili2c** → vollständiges Metamodell
- Metamodell ist **framework-agnostisch** und erweiterbar
- Reader schichten sich zu einem gemeinsamen `MetadataReader`

Siehe ausführlich: [README.md – Architektur & Design-Entscheidungen](README.md#architektur--design-entscheidungen)
