# Real ili2db Structure/Composition Inventory

- Model: `StructureCompositionCases`
- Schema: `rt_structcomp_1o1xwk8kwh`
- Classes: 7
- Structures: 3
- Composition targets: 3
- Generated structures: 3

## Structures

| Name | Table | Physical | Composition Target | Generated | Abstract |
|---|---|---:|---:|---:|---:|
| `StructureCompositionCases.Cases.Attachment` | `cases_attachment` | true | true | true | false |
| `StructureCompositionCases.Cases.Inspection` | `cases_inspection` | true | true | true | false |
| `StructureCompositionCases.Cases.Part` | `cases_part` | true | true | true | false |

## Composition Relationships

| Source | Target | Attribute | Cardinality | Ordered | External | Generated Target |
|---|---|---|---|---:|---:|---:|
| `StructureCompositionCases.Cases.Asset` | `StructureCompositionCases.Cases.Attachment` | `OptionalAttachment` | `1..1 -> 0..1` | false | false | true |
| `StructureCompositionCases.Cases.Asset` | `StructureCompositionCases.Cases.Inspection` | `MainInspection` | `1..1 -> 1..1` | false | false | true |
| `StructureCompositionCases.Cases.Asset` | `StructureCompositionCases.Cases.Part` | `Parts` | `1..1 -> 0..*` | true | false | true |

## Generated Classes

| IR Name | Grails Target |
|---|---|
| `StructureCompositionCases.Cases.Asset` | `Asset` |
| `StructureCompositionCases.Cases.AssetDocument` | `AssetDocument` |
| `StructureCompositionCases.Cases.Attachment` | `Attachment` |
| `StructureCompositionCases.Cases.Document` | `Document` |
| `StructureCompositionCases.Cases.Inspection` | `Inspection` |
| `StructureCompositionCases.Cases.Owner` | `Owner` |
| `StructureCompositionCases.Cases.Part` | `Part` |

## Skipped Structures

No structures were skipped.

## Relationship Counts

| Kind | Count |
|---|---:|
| `ASSOCIATION_ROLE` | 2 |
| `COMPOSITION_ATTRIBUTE` | 3 |
| `ILI2DB_FK` | 3 |
| `REFERENCE_ATTRIBUTE` | 1 |
