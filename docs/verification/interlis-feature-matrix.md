# INTERLIS Feature-Matrix

Generiert aus `verification/model-corpus.yaml` (Schema-Version 1).

| Feature | Status | Szenarien | Core-Test | Real-DB-Test | Browser-Test | Bemerkung |
|---|---|---|---|---|---|---|
| `association.cardinality` | SUPPORTED | association-cases | association-cases | association-cases | - | belegt durch realen PostgreSQL/ili2pg-Vertrag |
| `association.composite-role` | SUPPORTED | association-cases | association-cases | association-cases | - | belegt durch realen PostgreSQL/ili2pg-Vertrag |
| `association.external-role` | SUPPORTED | association-cases | association-cases | association-cases | - | belegt durch realen PostgreSQL/ili2pg-Vertrag |
| `association.link-entity` | SUPPORTED | p0-persistence-contract, structure-composition | p0-persistence-contract, structure-composition | p0-persistence-contract, structure-composition | - | belegt durch realen PostgreSQL/ili2pg-Vertrag |
| `association.two-roles-same-class` | SUPPORTED | association-cases, merge-ambiguity | association-cases, merge-ambiguity | association-cases | - | belegt durch realen PostgreSQL/ili2pg-Vertrag |
| `association.with-attribute` | SUPPORTED | association-cases | association-cases | association-cases | - | belegt durch realen PostgreSQL/ili2pg-Vertrag |
| `association.without-attribute` | SUPPORTED | association-cases | association-cases | association-cases | - | belegt durch realen PostgreSQL/ili2pg-Vertrag |
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
| `reference.two-fks-same-target` | SUPPORTED | association-cases | association-cases | association-cases | - | belegt durch realen PostgreSQL/ili2pg-Vertrag |
| `scalar.basic` | PARTIAL | core-types | core-types | - | - | semantische Generierung belegt; kein realer DB-Vertrag |
| `structure.basic` | SUPPORTED | structure-composition | structure-composition | structure-composition | - | belegt durch realen PostgreSQL/ili2pg-Vertrag |

Statuswerte: SUPPORTED = realer Datenbank-/Mapping-Contract vorhanden; PARTIAL = konkrete Einschränkung dokumentiert; EXPERIMENTAL = experimentell; UNSUPPORTED = nicht belegt.
