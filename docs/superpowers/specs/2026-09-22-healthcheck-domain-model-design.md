# Health Check Domain Model Design

## Purpose and scope

Define the framework-free domain model for the `healthcheck` bounded context of the corporate adult health-check flow. This work covers aggregates, entities, value objects, domain exceptions, repository ports, and domain-unit tests only.

It does not add HTTP APIs, persistence adapters, Flyway migrations, authorization implementation, audit persistence, document rendering, or cross-module application orchestration.

The source of truth is `requirement-v2.4`, `use-case-v2.5`, and `table-design-v2.10`.

## Package layout

The implemented domain package layout makes each model role visible without coupling the domain to a framework:

```text
healthcheck/domain/
├── aggregate/    aggregate roots and their child entities
├── valueobject/  immutable business values
├── enums/        finite lifecycle and import states
├── port/         aggregate persistence contracts
└── exception/    domain-rule violations
```

`HealthCheckBatchService` and `HealthCheckBatchEmployeeService` remain in `aggregate` with their respective aggregate roots. Their mutation methods are package-private, so a caller cannot bypass aggregate invariants such as controlled repricing and billability.

## Ubiquitous language

The canonical terms are recorded in the repository-root `CONTEXT.md`. In particular, Company Employee is a roster member rather than a Patient, and Health Check Record is the visit identity that owns SHS and the administrative print snapshot.

## Aggregate boundaries

### Company

`Company` is an aggregate root for the corporate customer master and its single primary contact. It is independent of batches, so a batch does not duplicate the Company data.

### Company Employee

`CompanyEmployee` is an aggregate root for company-specific roster membership. Its employee code and identificationNumber are unique within the Company. Import may create or update it without a Patient link. Once an authorized preparation workflow has linked a Patient by exact identificationNumber, a re-import cannot silently relink it to a different Patient.

### Health Check Batch

`HealthCheckBatch` is an aggregate root for one corporate campaign. `HealthCheckBatchService` is an entity inside it. The aggregate owns the batch lifecycle, site, planned window, master Form 03 template version, selected service scope, negotiated price, and specialist-template version.

Normal configuration is mutable only in `DRAFT`. `READY` freezes normal scope/template configuration. `FINALIZED` and `CLOSED` require an authorized reopen with a reason before repricing, then re-finalization. A batch-service price is common for every employee assignment of that service in the batch; the domain does not expose an employee-specific price override.

### Health Check Batch Employee

`HealthCheckBatchEmployee` is an aggregate root for one employee's participation in one batch. It owns the confirmed, validated administrative roster snapshot and `HealthCheckBatchEmployeeService` assignment entities. Assignment existence represents a doctor-confirmed selection; no redundant `selected` flag exists.

The application layer supplies the resolved batch-service identity and common negotiated price. The aggregate rejects a service outside its batch, duplicate assignment, and modification after clinical execution has begun. It marks an assignment billable only after the linked Service Request is confirmed complete through a public cross-module contract.

### Health Check Record

`HealthCheckRecord` is an aggregate root for one adult health-check visit. It owns an immutable SHS and the typed administrative snapshot used for Mẫu số 03. It requires a Patient and Encounter ID, validates adult eligibility on the planned date during preparation and again on the actual date at check-in, and does not create a new record during reprint or repeated check-in.

### Health Check Import Job

`HealthCheckImportJob` is an aggregate root for an employee-list or result-import workflow. It owns row validation/resolution state through `HealthCheckImportRow` entities. Employee-list confirmation creates or updates roster data only; result imports resolve to the shared clinical result domain and do not store a separate medical result.

## Value objects and states

The initial model uses only concepts present in the baseline: `IdentificationNumber`, `ShsCode`, `Money`, `AdministrativeSnapshot`, `ExaminationSite`, `BatchStatus`, `BatchEmployeeStatus`, `HealthCheckRecordStatus`, `ImportType`, and `ImportStatus`.

`AdministrativeSnapshot` is typed and validated in the domain even though its persistence representation is JSON. Its required fields are full name, date of birth, sex, and identificationNumber. Optional Mẫu số 03 fields remain nullable when the business contract permits them.

## Invariants

- Importing roster data never creates Patient or Encounter.
- A Company Employee cannot be silently relinked to another Patient.
- A Batch Employee belongs to exactly one Company Employee and one Health Check Batch; the roster snapshot is independent from mutable master data.
- A batch service is unique inside its batch, and an employee-service assignment is unique for its Batch Employee and Batch Service.
- Only an in-scope batch service may be assigned to a Batch Employee.
- Normal scope/template changes are forbidden outside `DRAFT`; price changes are common across a batch service rather than per employee.
- SHS and an issued record's administrative snapshot are immutable; reprint renders that record rather than mutable Patient data.
- Adult eligibility is required at preparation/printing and rechecked at actual check-in.
- Assignment billability follows successful Service Request completion; printing or preparing a visit never makes it billable.

## Ports and cross-context boundaries

The domain exposes repository ports for each aggregate root. Ports use domain IDs and aggregates rather than MyBatis records.

Cross-context interactions are application-layer orchestration through public contracts: exact Patient resolution/creation, prepared Encounter creation/check-in, Service Request creation/completion, template lookup, authorization, idempotency, audit, and transaction control. `healthcheck` never reaches into another module's mapper, table, persistence record, or internal package.

## Errors

The domain exposes explicit business errors, including `BatchConfigurationLocked`, `ServiceOutsideBatchScope`, `DuplicateEmployeeServiceAssignment`, `AdultEligibilityViolation`, and `PatientRelinkForbidden`. SHS and administrative snapshot have no mutation command. API error mapping remains outside the domain.

## Tests

Pure JUnit domain tests will prove required snapshot/identificationNumber fields, planned and actual-date adult eligibility, roster import without Patient creation, forbidden Patient relink, DRAFT-only configuration, unique/in-scope employee service assignment, immutable SHS and record snapshot, and billable transition only after verified Service Request completion.

Cross-aggregate transactional tests for uniform repricing, idempotency, authorization, and integration contracts are deliberately deferred to application-layer work.

## Open boundaries

The baseline leaves the permissions and target state for result-import completion, replacement after results exist, result-version behavior for reprint, partial refund, and off-site offline operation unresolved. The domain model must not infer behavior for those cases.

For a 29 February birthday in a non-leap year, the current domain uses 1 March as the conservative 18th-birthday threshold. This interpretation needs business confirmation. A batch price revision carries the old and new common price; applying it to an employee assignment rejects a stale old price. Updating all assignments and related Service Requests atomically remains an application-layer responsibility.
