# Real ili2db Structure/Composition Inventory

- Model: `CoreIrTestModel`
- Schema: `rt_coreir_1o21aj0mz1`
- Classes: 4
- Structures: 1
- Composition targets: 1
- Generated structures: 1

## Structures

| Name | Table | Physical | Composition Target | Generated | Abstract |
|---|---|---:|---:|---:|---:|
| `CoreIrTestModel.Relations.Component` | `relations_component` | true | true | true | false |

## Composition Relationships

| Source | Target | Attribute | Cardinality | Ordered | External | Generated Target |
|---|---|---|---|---:|---:|---:|
| `CoreIrTestModel.Relations.Child` | `CoreIrTestModel.Relations.Component` | `Components` | `1..1 -> 0..*` | true | false | true |

## Generated Classes

| IR Name | Grails Target |
|---|---|
| `CoreIrTestModel.Relations.Child` | `Child` |
| `CoreIrTestModel.Relations.Component` | `Component` |
| `CoreIrTestModel.Relations.Parent` | `Parent` |
| `CoreIrTestModel.Relations.ParentChild` | `ParentChild` |

## Skipped Structures

No structures were skipped.

## Relationship Counts

| Kind | Count |
|---|---:|
| `ASSOCIATION_ROLE` | 2 |
| `COMPOSITION_ATTRIBUTE` | 1 |
| `ILI2DB_FK` | 1 |
| `REFERENCE_ATTRIBUTE` | 1 |
