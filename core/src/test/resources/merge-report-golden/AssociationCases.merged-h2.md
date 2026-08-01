# Relationship Merge Report: AssociationCases

Total relationships: 17

## Summary by mergeReason

| mergeReason | Count |
|---|---:|
| EXACT_TARGET_ROLE | 17 |

## Summary by mergeConfidence

| mergeConfidence | Count |
|---|---:|
| EXACT | 17 |

Total association roles: 17

## Association roles by mergeReason

| mergeReason | Count |
|---|---:|
| EXACT_TARGET_ROLE | 17 |

## Association roles by mergeConfidence

| mergeConfidence | Count |
|---|---:|
| EXACT | 17 |

## Suspicious association roles

| Association | Role | Target | physicalName | semanticName | Reason | Confidence | token |
|---|---|---|---|---|---|---|---|

## Association roles

| Association | Role | Target | physicalName | semanticName | Reason | Confidence | token |
|---|---|---|---|---|---|---|---|
| AssociationCases.Base.AssociationWithAttribute | DocumentRole | AssociationCases.Base.Document | document_role_id | AssociationCases.Base.AssociationWithAttribute.DocumentRole | EXACT_TARGET_ROLE | EXACT | DocumentRole |
| AssociationCases.Base.AssociationWithAttribute | PersonRole | AssociationCases.Base.Person | person_role_id | AssociationCases.Base.AssociationWithAttribute.PersonRole | EXACT_TARGET_ROLE | EXACT | PersonRole |
| AssociationCases.Base.EmptyAssociation | ParcelRole | AssociationCases.Base.Parcel | parcel_role_id | AssociationCases.Base.EmptyAssociation.ParcelRole | EXACT_TARGET_ROLE | EXACT | ParcelRole |
| AssociationCases.Base.EmptyAssociation | PersonRole | AssociationCases.Base.Person | person_role_id | AssociationCases.Base.EmptyAssociation.PersonRole | EXACT_TARGET_ROLE | EXACT | PersonRole |
| AssociationCases.Base.ExternalCompositeAssociation | Buildings | AssociationCases.Base.Building | building_id | AssociationCases.Base.ExternalCompositeAssociation.Buildings | EXACT_TARGET_ROLE | EXACT | Buildings |
| AssociationCases.Base.ExternalCompositeAssociation | Owner | AssociationCases.Base.Person | owner_id | AssociationCases.Base.ExternalCompositeAssociation.Owner | EXACT_TARGET_ROLE | EXACT | Owner |
| AssociationCases.Base.OrderedAssociation | Docs | AssociationCases.Base.Document | docs_id | AssociationCases.Base.OrderedAssociation.Docs | EXACT_TARGET_ROLE | EXACT | Docs |
| AssociationCases.Base.OrderedAssociation | Owner | AssociationCases.Base.Person | owner_id | AssociationCases.Base.OrderedAssociation.Owner | EXACT_TARGET_ROLE | EXACT | Owner |
| AssociationCases.Base.PhysicalMismatchAssociation | OwnedParcel | AssociationCases.Base.Parcel | parcel_fk | AssociationCases.Base.PhysicalMismatchAssociation.OwnedParcel | EXACT_TARGET_ROLE | EXACT | OwnedParcel |
| AssociationCases.Base.PhysicalMismatchAssociation | SemanticOwner | AssociationCases.Base.Person | owner_fk | AssociationCases.Base.PhysicalMismatchAssociation.SemanticOwner | EXACT_TARGET_ROLE | EXACT | SemanticOwner |
| AssociationCases.Base.SameTargetAssociation | PrimaryPerson | AssociationCases.Base.Person | primary_person_id | AssociationCases.Base.SameTargetAssociation.PrimaryPerson | EXACT_TARGET_ROLE | EXACT | PrimaryPerson |
| AssociationCases.Base.SameTargetAssociation | SecondaryPerson | AssociationCases.Base.Person | secondary_person_id | AssociationCases.Base.SameTargetAssociation.SecondaryPerson | EXACT_TARGET_ROLE | EXACT | SecondaryPerson |
| AssociationCases.Extended.ExtendedTopicAssociation | ExtendedParcelRole | AssociationCases.Extended.ExtendedParcel | ext_parcel_id | AssociationCases.Extended.ExtendedTopicAssociation.ExtendedParcelRole | EXACT_TARGET_ROLE | EXACT | ExtendedParcelRole |
| AssociationCases.Extended.ExtendedTopicAssociation | ExtendedPersonRole | AssociationCases.Base.Person | ext_person_id | AssociationCases.Extended.ExtendedTopicAssociation.ExtendedPersonRole | EXACT_TARGET_ROLE | EXACT | ExtendedPersonRole |
| AssociationCases.Extended.TernaryAssociation | DocumentRole | AssociationCases.Base.Document | document_role_id | AssociationCases.Extended.TernaryAssociation.DocumentRole | EXACT_TARGET_ROLE | EXACT | DocumentRole |
| AssociationCases.Extended.TernaryAssociation | ParcelRole | AssociationCases.Base.Parcel | parcel_role_id | AssociationCases.Extended.TernaryAssociation.ParcelRole | EXACT_TARGET_ROLE | EXACT | ParcelRole |
| AssociationCases.Extended.TernaryAssociation | PersonRole | AssociationCases.Base.Person | person_role_id | AssociationCases.Extended.TernaryAssociation.PersonRole | EXACT_TARGET_ROLE | EXACT | PersonRole |

## Suspicious

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|

## NORMALIZED_TOKEN matches

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|

## Exact matches

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|
| AssociationCases.Base.AssociationWithAttribute | AssociationCases.Base.Document | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | document_role_id | AssociationCases.Base.AssociationWithAttribute.DocumentRole | DocumentRole |
| AssociationCases.Base.AssociationWithAttribute | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | person_role_id | AssociationCases.Base.AssociationWithAttribute.PersonRole | PersonRole |
| AssociationCases.Base.EmptyAssociation | AssociationCases.Base.Parcel | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | parcel_role_id | AssociationCases.Base.EmptyAssociation.ParcelRole | ParcelRole |
| AssociationCases.Base.EmptyAssociation | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | person_role_id | AssociationCases.Base.EmptyAssociation.PersonRole | PersonRole |
| AssociationCases.Base.ExternalCompositeAssociation | AssociationCases.Base.Building | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | building_id | AssociationCases.Base.ExternalCompositeAssociation.Buildings | Buildings |
| AssociationCases.Base.ExternalCompositeAssociation | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | owner_id | AssociationCases.Base.ExternalCompositeAssociation.Owner | Owner |
| AssociationCases.Base.OrderedAssociation | AssociationCases.Base.Document | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | docs_id | AssociationCases.Base.OrderedAssociation.Docs | Docs |
| AssociationCases.Base.OrderedAssociation | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | owner_id | AssociationCases.Base.OrderedAssociation.Owner | Owner |
| AssociationCases.Base.PhysicalMismatchAssociation | AssociationCases.Base.Parcel | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | parcel_fk | AssociationCases.Base.PhysicalMismatchAssociation.OwnedParcel | OwnedParcel |
| AssociationCases.Base.PhysicalMismatchAssociation | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | owner_fk | AssociationCases.Base.PhysicalMismatchAssociation.SemanticOwner | SemanticOwner |
| AssociationCases.Base.SameTargetAssociation | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | primary_person_id | AssociationCases.Base.SameTargetAssociation.PrimaryPerson | PrimaryPerson |
| AssociationCases.Base.SameTargetAssociation | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | secondary_person_id | AssociationCases.Base.SameTargetAssociation.SecondaryPerson | SecondaryPerson |
| AssociationCases.Extended.ExtendedTopicAssociation | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | ext_person_id | AssociationCases.Extended.ExtendedTopicAssociation.ExtendedPersonRole | ExtendedPersonRole |
| AssociationCases.Extended.ExtendedTopicAssociation | AssociationCases.Extended.ExtendedParcel | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | ext_parcel_id | AssociationCases.Extended.ExtendedTopicAssociation.ExtendedParcelRole | ExtendedParcelRole |
| AssociationCases.Extended.TernaryAssociation | AssociationCases.Base.Document | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | document_role_id | AssociationCases.Extended.TernaryAssociation.DocumentRole | DocumentRole |
| AssociationCases.Extended.TernaryAssociation | AssociationCases.Base.Parcel | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | parcel_role_id | AssociationCases.Extended.TernaryAssociation.ParcelRole | ParcelRole |
| AssociationCases.Extended.TernaryAssociation | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | person_role_id | AssociationCases.Extended.TernaryAssociation.PersonRole | PersonRole |

## ILI2DB_ONLY

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|

## ILI2C_ONLY

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|

