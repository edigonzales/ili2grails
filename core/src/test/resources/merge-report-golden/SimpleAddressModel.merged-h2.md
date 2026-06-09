# Relationship Merge Report: SimpleAddressModel

Total relationships: 2

## Summary by mergeReason

| mergeReason | Count |
|---|---:|
| NORMALIZED_TOKEN | 2 |

## Summary by mergeConfidence

| mergeConfidence | Count |
|---|---:|
| MEDIUM | 2 |

Total association roles: 2

## Association roles by mergeReason

| mergeReason | Count |
|---|---:|
| NORMALIZED_TOKEN | 2 |

## Association roles by mergeConfidence

| mergeConfidence | Count |
|---|---:|
| MEDIUM | 2 |

## Suspicious association roles

| Association | Role | Target | physicalName | semanticName | Reason | Confidence | token |
|---|---|---|---|---|---|---|---|
| SimpleAddressModel.Addresses.PersonAddress | Address | SimpleAddressModel.Addresses.Address | address_id | SimpleAddressModel.Addresses.PersonAddress.Address | NORMALIZED_TOKEN | MEDIUM | address |
| SimpleAddressModel.Addresses.PersonAddress | Person | SimpleAddressModel.Addresses.Person | person_id | SimpleAddressModel.Addresses.PersonAddress.Person | NORMALIZED_TOKEN | MEDIUM | person |

## Association roles

| Association | Role | Target | physicalName | semanticName | Reason | Confidence | token |
|---|---|---|---|---|---|---|---|
| SimpleAddressModel.Addresses.PersonAddress | Address | SimpleAddressModel.Addresses.Address | address_id | SimpleAddressModel.Addresses.PersonAddress.Address | NORMALIZED_TOKEN | MEDIUM | address |
| SimpleAddressModel.Addresses.PersonAddress | Person | SimpleAddressModel.Addresses.Person | person_id | SimpleAddressModel.Addresses.PersonAddress.Person | NORMALIZED_TOKEN | MEDIUM | person |

## Suspicious

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|
| SimpleAddressModel.Addresses.PersonAddress | SimpleAddressModel.Addresses.Address | ASSOCIATION_ROLE | NORMALIZED_TOKEN | MEDIUM | address_id | SimpleAddressModel.Addresses.PersonAddress.Address | address |
| SimpleAddressModel.Addresses.PersonAddress | SimpleAddressModel.Addresses.Person | ASSOCIATION_ROLE | NORMALIZED_TOKEN | MEDIUM | person_id | SimpleAddressModel.Addresses.PersonAddress.Person | person |

## NORMALIZED_TOKEN matches

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|
| SimpleAddressModel.Addresses.PersonAddress | SimpleAddressModel.Addresses.Address | ASSOCIATION_ROLE | NORMALIZED_TOKEN | MEDIUM | address_id | SimpleAddressModel.Addresses.PersonAddress.Address | address |
| SimpleAddressModel.Addresses.PersonAddress | SimpleAddressModel.Addresses.Person | ASSOCIATION_ROLE | NORMALIZED_TOKEN | MEDIUM | person_id | SimpleAddressModel.Addresses.PersonAddress.Person | person |

## Exact matches

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|

## ILI2DB_ONLY

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|

## ILI2C_ONLY

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|

