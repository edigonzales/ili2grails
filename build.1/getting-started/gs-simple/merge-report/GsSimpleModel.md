# Relationship Merge Report: GsSimpleModel

Total relationships: 6

## Summary by mergeReason

| mergeReason | Count |
|---|---:|
| ILI2C_ONLY | 4 |
| ILI2DB_ONLY | 2 |

## Summary by mergeConfidence

| mergeConfidence | Count |
|---|---:|
| NONE | 6 |

Total association roles: 4

## Association roles by mergeReason

| mergeReason | Count |
|---|---:|
| ILI2C_ONLY | 4 |

## Association roles by mergeConfidence

| mergeConfidence | Count |
|---|---:|
| NONE | 4 |

## Suspicious association roles

| Association | Role | Target | physicalName | semanticName | Reason | Confidence | token |
|---|---|---|---|---|---|---|---|
| GsSimpleModel.Organization.CompanyDepartment | Company | GsSimpleModel.Organization.Company |  | GsSimpleModel.Organization.CompanyDepartment.Company | ILI2C_ONLY | NONE |  |
| GsSimpleModel.Organization.CompanyDepartment | Departments | GsSimpleModel.Organization.Department |  | GsSimpleModel.Organization.CompanyDepartment.Departments | ILI2C_ONLY | NONE |  |
| GsSimpleModel.Organization.DepartmentEmployee | Department | GsSimpleModel.Organization.Department |  | GsSimpleModel.Organization.DepartmentEmployee.Department | ILI2C_ONLY | NONE |  |
| GsSimpleModel.Organization.DepartmentEmployee | Employees | GsSimpleModel.Organization.Employee |  | GsSimpleModel.Organization.DepartmentEmployee.Employees | ILI2C_ONLY | NONE |  |

## Association roles

| Association | Role | Target | physicalName | semanticName | Reason | Confidence | token |
|---|---|---|---|---|---|---|---|
| GsSimpleModel.Organization.CompanyDepartment | Company | GsSimpleModel.Organization.Company |  | GsSimpleModel.Organization.CompanyDepartment.Company | ILI2C_ONLY | NONE |  |
| GsSimpleModel.Organization.CompanyDepartment | Departments | GsSimpleModel.Organization.Department |  | GsSimpleModel.Organization.CompanyDepartment.Departments | ILI2C_ONLY | NONE |  |
| GsSimpleModel.Organization.DepartmentEmployee | Department | GsSimpleModel.Organization.Department |  | GsSimpleModel.Organization.DepartmentEmployee.Department | ILI2C_ONLY | NONE |  |
| GsSimpleModel.Organization.DepartmentEmployee | Employees | GsSimpleModel.Organization.Employee |  | GsSimpleModel.Organization.DepartmentEmployee.Employees | ILI2C_ONLY | NONE |  |

## Suspicious

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|
| GsSimpleModel.Organization.CompanyDepartment | GsSimpleModel.Organization.Company | ASSOCIATION_ROLE | ILI2C_ONLY | NONE |  | GsSimpleModel.Organization.CompanyDepartment.Company |  |
| GsSimpleModel.Organization.CompanyDepartment | GsSimpleModel.Organization.Department | ASSOCIATION_ROLE | ILI2C_ONLY | NONE |  | GsSimpleModel.Organization.CompanyDepartment.Departments |  |
| GsSimpleModel.Organization.DepartmentEmployee | GsSimpleModel.Organization.Department | ASSOCIATION_ROLE | ILI2C_ONLY | NONE |  | GsSimpleModel.Organization.DepartmentEmployee.Department |  |
| GsSimpleModel.Organization.DepartmentEmployee | GsSimpleModel.Organization.Employee | ASSOCIATION_ROLE | ILI2C_ONLY | NONE |  | GsSimpleModel.Organization.DepartmentEmployee.Employees |  |
| GsSimpleModel.Organization.Department | GsSimpleModel.Organization.Company | ILI2DB_FK | ILI2DB_ONLY | NONE | company |  |  |
| GsSimpleModel.Organization.Employee | GsSimpleModel.Organization.Department | ILI2DB_FK | ILI2DB_ONLY | NONE | department |  |  |

## NORMALIZED_TOKEN matches

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|

## Exact matches

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|

## ILI2DB_ONLY

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|
| GsSimpleModel.Organization.Department | GsSimpleModel.Organization.Company | ILI2DB_FK | ILI2DB_ONLY | NONE | company |  |  |
| GsSimpleModel.Organization.Employee | GsSimpleModel.Organization.Department | ILI2DB_FK | ILI2DB_ONLY | NONE | department |  |  |

## ILI2C_ONLY

| Source | Target | Kind | Reason | Confidence | physicalName | semanticName | token |
|---|---|---|---|---|---|---|---|
| GsSimpleModel.Organization.CompanyDepartment | GsSimpleModel.Organization.Company | ASSOCIATION_ROLE | ILI2C_ONLY | NONE |  | GsSimpleModel.Organization.CompanyDepartment.Company |  |
| GsSimpleModel.Organization.CompanyDepartment | GsSimpleModel.Organization.Department | ASSOCIATION_ROLE | ILI2C_ONLY | NONE |  | GsSimpleModel.Organization.CompanyDepartment.Departments |  |
| GsSimpleModel.Organization.DepartmentEmployee | GsSimpleModel.Organization.Department | ASSOCIATION_ROLE | ILI2C_ONLY | NONE |  | GsSimpleModel.Organization.DepartmentEmployee.Department |  |
| GsSimpleModel.Organization.DepartmentEmployee | GsSimpleModel.Organization.Employee | ASSOCIATION_ROLE | ILI2C_ONLY | NONE |  | GsSimpleModel.Organization.DepartmentEmployee.Employees |  |

