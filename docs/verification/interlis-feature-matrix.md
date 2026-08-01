# INTERLIS Feature-Matrix

Generiert aus `verification/model-corpus.yaml` (Schema-Version 1).

| Feature | Status | Szenarien | Core-Test | Real-DB-Test | Browser-Test | Bemerkung |
|---|---|---|---|---|---|---|
| `association.cardinality` | PARTIAL | association-cases | association-cases | association-cases | - | realer DB-Vertrag mit dokumentierter Einschränkung (allowedDifferences) |
| `association.composite-role` | PARTIAL | association-cases | association-cases | association-cases | - | realer DB-Vertrag mit dokumentierter Einschränkung (allowedDifferences) |
| `association.external-role` | PARTIAL | association-cases | association-cases | association-cases | - | realer DB-Vertrag mit dokumentierter Einschränkung (allowedDifferences) |
| `association.link-entity` | SUPPORTED | p0-persistence-contract, structure-composition | p0-persistence-contract, structure-composition | p0-persistence-contract, structure-composition | - | belegt durch realen PostgreSQL/ili2pg-Vertrag |
| `association.role-fk-column-naming` | PARTIAL | association-cases | association-cases | association-cases | - | realer DB-Vertrag mit dokumentierter Einschränkung (allowedDifferences) |
| `association.two-roles-same-class` | PARTIAL | association-cases, merge-ambiguity | association-cases, merge-ambiguity | association-cases | - | realer DB-Vertrag mit dokumentierter Einschränkung (allowedDifferences) |
| `association.with-attribute` | PARTIAL | association-cases | association-cases | association-cases | - | realer DB-Vertrag mit dokumentierter Einschränkung (allowedDifferences) |
| `association.without-attribute` | PARTIAL | association-cases | association-cases | association-cases | - | realer DB-Vertrag mit dokumentierter Einschränkung (allowedDifferences) |
| `composition.child-fk` | SUPPORTED | p0-persistence-contract, structure-composition | p0-persistence-contract, structure-composition | p0-persistence-contract, structure-composition | - | belegt durch realen PostgreSQL/ili2pg-Vertrag |
| `composition.no-join-table` | SUPPORTED | p0-persistence-contract, structure-composition | p0-persistence-contract, structure-composition | p0-persistence-contract, structure-composition | - | belegt durch realen PostgreSQL/ili2pg-Vertrag |
| `enum.basic` | PARTIAL | core-types | core-types | - | - | semantische Generierung belegt; kein realer DB-Vertrag |
| `geometry.basic` | SUPPORTED | geometry-basic, p0-persistence-contract | geometry-basic, p0-persistence-contract | geometry-basic, p0-persistence-contract | - | belegt durch realen PostgreSQL/ili2pg-Vertrag |
| `geometry.srid` | SUPPORTED | geometry-basic, p0-persistence-contract | geometry-basic, p0-persistence-contract | geometry-basic, p0-persistence-contract | - | belegt durch realen PostgreSQL/ili2pg-Vertrag |
| `inverse.read-only` | UNSUPPORTED | - | - | - | - | kein Szenario belegt dieses Feature |
| `inverse.writable` | UNSUPPORTED | - | - | - | - | kein Szenario belegt dieses Feature |
| `merge.attribute-ambiguity` | PARTIAL | merge-ambiguity | merge-ambiguity | - | - | semantische Generierung belegt; kein realer DB-Vertrag |
| `merge.relationship-ambiguity` | PARTIAL | merge-ambiguity | merge-ambiguity | - | - | semantische Generierung belegt; kein realer DB-Vertrag |
| `model-selection.dependency` | PARTIAL | model-selection | model-selection | - | - | semantische Generierung belegt; kein realer DB-Vertrag |
| `model-selection.root` | PARTIAL | model-selection | model-selection | - | - | semantische Generierung belegt; kein realer DB-Vertrag |
| `model-selection.transitive-dependency` | PARTIAL | model-selection | model-selection | - | - | semantische Generierung belegt; kein realer DB-Vertrag |
| `model-selection.unrelated-excluded` | PARTIAL | model-selection | model-selection | - | - | semantische Generierung belegt; kein realer DB-Vertrag |
| `persistence.contract` | SUPPORTED | association-cases, geometry-basic, p0-persistence-contract, structure-composition | association-cases, geometry-basic, p0-persistence-contract, structure-composition | association-cases, geometry-basic, p0-persistence-contract, structure-composition | - | belegt durch realen PostgreSQL/ili2pg-Vertrag |
| `real-world.large-model` | PARTIAL | vsadssmini-large-model | vsadssmini-large-model | - | - | semantische Generierung belegt; kein realer DB-Vertrag |
| `reference.many-to-one` | SUPPORTED | merge-ambiguity, p0-persistence-contract, structure-composition | merge-ambiguity, p0-persistence-contract, structure-composition | p0-persistence-contract, structure-composition | - | belegt durch realen PostgreSQL/ili2pg-Vertrag |
| `reference.two-fks-same-target` | PARTIAL | association-cases | association-cases | association-cases | - | realer DB-Vertrag mit dokumentierter Einschränkung (allowedDifferences) |
| `scalar.basic` | PARTIAL | core-types | core-types | - | - | semantische Generierung belegt; kein realer DB-Vertrag |
| `structure.basic` | PARTIAL | structure-composition | structure-composition | structure-composition | - | realer DB-Vertrag mit dokumentierter Einschränkung (allowedDifferences) |

Statuswerte: SUPPORTED = realer Datenbank-/Mapping-Contract vorhanden; PARTIAL = konkrete Einschränkung dokumentiert; EXPERIMENTAL = experimentell; UNSUPPORTED = nicht belegt.
