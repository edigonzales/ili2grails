# Relationship Merge Report: AssociationCases

Total relationships: 15

## Summary by mergeReason

| mergeReason | Count |
|---|---:|
| EXACT_TARGET_ROLE | 15 |

## Summary by mergeConfidence

| mergeConfidence | Count |
|---|---:|
| EXACT | 15 |

Total association roles: 15

## Association roles by mergeReason

| mergeReason | Count |
|---|---:|
| EXACT_TARGET_ROLE | 15 |

## Association roles by mergeConfidence

| mergeConfidence | Count |
|---|---:|
| EXACT | 15 |

## Suspicious association roles

| Association | Role | Target | physicalName | semanticName | Reason | Confidence | token |
|---|---|---|---|---|---|---|---|

## Association roles

| Association | Role | Target | physicalName | semanticName | Reason | Confidence | token |
|---|---|---|---|---|---|---|---|
| AssociationCases.Base.AssociationWithAttribute | DocumentRole | AssociationCases.Base.Document | document_role_id | AssociationCases.Base.AssociationWithAttribute.DocumentRole | EXACT_TARGET_ROLE | EXACT | documentrole |
| AssociationCases.Base.AssociationWithAttribute | PersonRole | AssociationCases.Base.Person | person_role_id | AssociationCases.Base.AssociationWithAttribute.PersonRole | EXACT_TARGET_ROLE | EXACT | personrole |
| AssociationCases.Base.EmptyAssociation | ParcelRole | AssociationCases.Base.Parcel | parcel_role_id | AssociationCases.Base.EmptyAssociation.ParcelRole | EXACT_TARGET_ROLE | EXACT | parcelrole |
| AssociationCases.Base.EmptyAssociation | PersonRole | AssociationCases.Base.Person | person_role_id | AssociationCases.Base.EmptyAssociation.PersonRole | EXACT_TARGET_ROLE | EXACT | personrole |
| AssociationCases.Base.ExternalCompositeAssociation | Buildings | AssociationCases.Base.Building | building_id | AssociationCases.Base.ExternalCompositeAssociation.Buildings | EXACT_TARGET_ROLE | EXACT | buildings |
| AssociationCases.Base.ExternalCompositeAssociation | Owner | AssociationCases.Base.Person | owner_id | AssociationCases.Base.ExternalCompositeAssociation.Owner | EXACT_TARGET_ROLE | EXACT | owner |
| AssociationCases.Base.PhysicalMismatchAssociation | OwnedParcel | AssociationCases.Base.Parcel | parcel_fk | AssociationCases.Base.PhysicalMismatchAssociation.OwnedParcel | EXACT_TARGET_ROLE | EXACT | ownedparcel |
| AssociationCases.Base.PhysicalMismatchAssociation | SemanticOwner | AssociationCases.Base.Person | owner_fk | AssociationCases.Base.PhysicalMismatchAssociation.SemanticOwner | EXACT_TARGET_ROLE | EXACT | semanticowner |
| AssociationCases.Base.SameTargetAssociation | PrimaryPerson | AssociationCases.Base.Person | primary_person_id | AssociationCases.Base.SameTargetAssociation.PrimaryPerson | EXACT_TARGET_ROLE | EXACT | primaryperson |
| AssociationCases.Base.SameTargetAssociation | SecondaryPerson | AssociationCases.Base.Person | secondary_person_id | AssociationCases.Base.SameTargetAssociation.SecondaryPerson | EXACT_TARGET_ROLE | EXACT | secondaryperson |
| AssociationCases.Extended.ExtendedTopicAssociation | ExtendedParcelRole | AssociationCases.Extended.ExtendedParcel | ext_parcel_id | AssociationCases.Extended.ExtendedTopicAssociation.ExtendedParcelRole | EXACT_TARGET_ROLE | EXACT | extendedparcelrole |
| AssociationCases.Extended.ExtendedTopicAssociation | ExtendedPersonRole | AssociationCases.Base.Person | ext_person_id | AssociationCases.Extended.ExtendedTopicAssociation.ExtendedPersonRole | EXACT_TARGET_ROLE | EXACT | extendedpersonrole |
| AssociationCases.Extended.TernaryAssociation | DocumentRole | AssociationCases.Base.Document | document_role_id | AssociationCases.Extended.TernaryAssociation.DocumentRole | EXACT_TARGET_ROLE | EXACT | documentrole |
| AssociationCases.Extended.TernaryAssociation | ParcelRole | AssociationCases.Base.Parcel | parcel_role_id | AssociationCases.Extended.TernaryAssociation.ParcelRole | EXACT_TARGET_ROLE | EXACT | parcelrole |
| AssociationCases.Extended.TernaryAssociation | PersonRole | AssociationCases.Base.Person | person_role_id | AssociationCases.Extended.TernaryAssociation.PersonRole | EXACT_TARGET_ROLE | EXACT | personrole |

## Suspicious

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|

## NORMALIZED_TOKEN matches

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|

## Exact matches

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|
| AssociationCases.Base.AssociationWithAttribute | AssociationCases.Base.Document | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | document_role_id | AssociationCases.Base.AssociationWithAttribute.DocumentRole | documentrole |
| AssociationCases.Base.AssociationWithAttribute | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | person_role_id | AssociationCases.Base.AssociationWithAttribute.PersonRole | personrole |
| AssociationCases.Base.EmptyAssociation | AssociationCases.Base.Parcel | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | parcel_role_id | AssociationCases.Base.EmptyAssociation.ParcelRole | parcelrole |
| AssociationCases.Base.EmptyAssociation | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | person_role_id | AssociationCases.Base.EmptyAssociation.PersonRole | personrole |
| AssociationCases.Base.ExternalCompositeAssociation | AssociationCases.Base.Building | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | building_id | AssociationCases.Base.ExternalCompositeAssociation.Buildings | buildings |
| AssociationCases.Base.ExternalCompositeAssociation | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | owner_id | AssociationCases.Base.ExternalCompositeAssociation.Owner | owner |
| AssociationCases.Base.PhysicalMismatchAssociation | AssociationCases.Base.Parcel | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | parcel_fk | AssociationCases.Base.PhysicalMismatchAssociation.OwnedParcel | ownedparcel |
| AssociationCases.Base.PhysicalMismatchAssociation | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | owner_fk | AssociationCases.Base.PhysicalMismatchAssociation.SemanticOwner | semanticowner |
| AssociationCases.Base.SameTargetAssociation | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | primary_person_id | AssociationCases.Base.SameTargetAssociation.PrimaryPerson | primaryperson |
| AssociationCases.Base.SameTargetAssociation | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | secondary_person_id | AssociationCases.Base.SameTargetAssociation.SecondaryPerson | secondaryperson |
| AssociationCases.Extended.ExtendedTopicAssociation | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | ext_person_id | AssociationCases.Extended.ExtendedTopicAssociation.ExtendedPersonRole | extendedpersonrole |
| AssociationCases.Extended.ExtendedTopicAssociation | AssociationCases.Extended.ExtendedParcel | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | ext_parcel_id | AssociationCases.Extended.ExtendedTopicAssociation.ExtendedParcelRole | extendedparcelrole |
| AssociationCases.Extended.TernaryAssociation | AssociationCases.Base.Document | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | document_role_id | AssociationCases.Extended.TernaryAssociation.DocumentRole | documentrole |
| AssociationCases.Extended.TernaryAssociation | AssociationCases.Base.Parcel | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | parcel_role_id | AssociationCases.Extended.TernaryAssociation.ParcelRole | parcelrole |
| AssociationCases.Extended.TernaryAssociation | AssociationCases.Base.Person | ASSOCIATION_ROLE | EXACT_TARGET_ROLE | EXACT | person_role_id | AssociationCases.Extended.TernaryAssociation.PersonRole | personrole |

## ILI2DB_ONLY

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|

## ILI2C_ONLY

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|

