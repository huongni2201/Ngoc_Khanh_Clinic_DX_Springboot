# Ngọc Khánh Clinic Backend — Project Rules

> Mandatory backend engineering rules for NKC-DX. A later accepted ADR or explicitly newer source-of-truth document may supersede a rule.

## 1. Product and Business Baseline

The backend supports a real outpatient clinic and adult/corporate health-check workflow.

Current source-of-truth documents:

```text
requirement-v2.5
use-case-v2.7
table-design-v2.11
```

Do not infer domain behavior from UI mockups when these documents define the rule.

If code, UI, and documentation disagree, identify the conflict before changing business behavior.

---

## 2. Approved Stack

```text
Java                 25
Spring Boot          4.x
Spring Framework     7.x
SQL Server           2022+
MyBatis              4.x Spring Boot starter
Spring Modulith      2.x
Maven
Flyway
JUnit 5
AssertJ
Mockito
Testcontainers
```

Persistence stack is **MyBatis + SQL Server**.

Forbidden by default:

```text
JPA
Hibernate
Spring Data JPA
H2 as SQL Server substitute
```

Any core stack change requires an ADR.

---

## 3. Root Package

Canonical package:

```text
com.ngockhanh.clinic
```

Java package names must be lowercase and must not contain underscores.

---

## 4. Modular Monolith

The application is one deployable Spring Boot application with explicit bounded contexts.

Canonical bounded contexts:

```text
identity
patient
catalog
encounter
clinical
billing
diagnostics
healthcheck
document
prescription
notification
integration
shared
```

This is not a Maven multi-module project unless an ADR later changes that decision.

Do not split into microservices during MVP.

---

## 5. DDD Layering Per Module

Use, as needed:

```text
<module>/
├── api/
├── application/
├── domain/
└── infrastructure/
```

### `api`

Owns:

```text
REST controllers
request DTOs
response DTOs
HTTP mapping
transport validation
```

Must not own business rules or SQL.

### `application`

Owns:

```text
use cases
command/query handlers or application services
transaction boundaries
authorization orchestration where applicable
cross-aggregate orchestration
mapping between transport/application/domain models
```

### `domain`

Owns:

```text
aggregates
entities
value objects
domain services
domain events
repository ports
domain exceptions
invariants
```

The domain should be framework-light and persistence-ignorant.

### `infrastructure`

Owns:

```text
MyBatis mappers
persistence records
repository adapters
SQL-specific mapping
external API adapters
SMS/payment/device integrations
technical configuration
```

---

## 6. Dependency Direction

Allowed:

```text
api -> application
application -> domain
infrastructure -> domain
bootstrap/configuration -> all required adapters
```

Forbidden:

```text
domain -> application
domain -> api
domain -> MyBatis
domain -> SQL Server driver
domain -> HTTP DTO
controller -> mapper
controller -> repository implementation
application -> another module's mapper
```

---

## 7. Module Encapsulation

Treat each bounded context as an internal module.

Cross-module access must go through a deliberately exposed public contract.

Prefer:

```text
application facade/interface
published event
explicit read/query contract
```

Never import another module's:

```text
infrastructure.*
internal.*
persistence.*
mapper.*
record.*
```

Never join another bounded context's tables from a mapper merely because SQL makes it easy. If a cross-context read model is genuinely required, design it explicitly and document ownership.

Spring Modulith verification is mandatory for structural regression protection.

---

## 8. `shared` Rules

`shared` is only for truly cross-cutting technical code.

Allowed examples:

```text
shared/config
shared/security
shared/exception
shared/audit
shared/idempotency
shared/web
shared/time
```

Do not put domain concepts in `shared`.

Forbidden examples:

```text
shared/PatientUtils
shared/HealthCheckService
shared/BillingHelper
shared/CommonDomain
```

If code has business meaning, it belongs to the owning bounded context.

---

## 9. Domain vs Persistence Model

Persistence records and domain models must not be conflated.

Example:

```text
patient/domain/model/Patient.java
patient/domain/repository/PatientRepository.java

patient/infrastructure/persistence/record/PatientRecord.java
patient/infrastructure/persistence/mapper/PatientMapper.java
patient/infrastructure/persistence/repository/MyBatisPatientRepository.java
```

`PatientRecord` may reflect SQL structure.

`Patient` should reflect business behavior and invariants.

Do not make the domain model depend on MyBatis annotations.

---

## 10. MyBatis Rules

Use MyBatis only inside infrastructure.

Mapper location:

```text
src/main/resources/mapper/<module>/*.xml
```

Recommended for non-trivial queries:

```text
XML mapper
```

Annotations are acceptable only for small, obvious SQL where they improve readability.

Rules:

- Never use `${...}` for untrusted values.
- Prefer `#{...}` parameter binding.
- Avoid `SELECT *`.
- Select only columns required by the persistence/read model.
- Define deterministic ordering for paginated queries.
- Avoid N+1 query patterns.
- Batch intentionally when importing large employee rosters.
- Keep business branching out of SQL when it belongs in domain/application logic.
- SQL Server-specific behavior must be covered by integration tests.

---

## 11. SQL Server Rules

Baseline conventions from `table-design-v2.11`:

```text
table names       plural snake_case
column names      snake_case
primary key       id
foreign key       <entity>_id
time              datetime2(3)
money             decimal(18,2) unless table design says otherwise
unicode text      nvarchar
public UUID       uniqueidentifier where specified
concurrency       rowversion where specified
```

Use database constraints for true invariants.

Examples:

```text
UNIQUE cccd
NOT NULL required identity fields
foreign keys
check constraints where appropriate
unique business codes
```

Indexes must be justified by real query patterns.

---

## 12. Flyway Rules

All schema changes use Flyway.

Location:

```text
src/main/resources/db/migration/
```

Naming:

```text
V001__create_security_tables.sql
V002__create_patient_tables.sql
...
```

Rules:

- Never use application auto-DDL in production.
- Never edit an already-applied shared migration.
- Add a new migration for every subsequent schema change.
- Include required FK/UK/check/index definitions in migrations.
- Migration rollback strategy must be considered for destructive changes.
- Destructive production data changes require explicit review.

---

## 13. Identity and Patient Rules

Current MVP:

```text
cccd = mandatory patient business identity
```

Rules:

- Exact cccd lookup before Patient creation.
- `patients.cccd` must be unique.
- No passport/identity-type abstraction in baseline.
- No fuzzy duplicate merge by name/phone in baseline.
- Do not create `patient_contacts`, `patient_addresses`, or `patient_merge_history` unless requirements explicitly reintroduce them.
- Patient historical clinical/financial data is not hard-deleted.

---

## 14. Health Check Rules

Company-first flow:

```text
Company
-> HealthCheckBatch
-> HealthCheckBatchService
-> HealthCheckBatchEmployee
-> HealthCheckRecord
-> Encounter / Services / Results
```

Rules:

- Imported `CompanyEmployee`/batch employee is not automatically a Patient.
- Excel import must preserve cccd as text.
- Blocking validation includes required fields and current adult health-check rules.
- Do not fabricate missing optional data.
- Patient link/create occurs at check-in or the documented workflow point.
- One health-check visit/record has exactly one SHS.
- SHS is reused across the forms for that health-check record.
- Mẫu số 03 is the master form and uses the SHS barcode.
- Reprint reads administrative snapshot from `HealthCheckRecord`.
- Updating Patient later must not mutate old health-check snapshots.

Employee service selection:

```text
Doctor only
+
subset of HealthCheckBatchService only
```

Front Desk must not choose per-employee examination items.

---

## 15. Encounter and Diagnostic Progress Rules

Encounter, ServiceRequest and Result have their own lifecycles. Diagnostic progress and worklists
are derived from Encounter, OrderRound, ServiceRequest, PaymentAuthorization, performing location
and Result. Do not create Journey/JourneyStage or a separate CLS state machine.

Do not introduce reception/exam queue tickets or a return queue ticket.
Doctor review readiness follows completion of required requests and results.

---

## 16. Orders and Payment

One Encounter may have multiple Order Rounds.

Pattern:

```text
Order Round 1
-> payment authorization
-> diagnostic execution
-> results

Order Round N+1
-> payment authorization
-> diagnostic execution
-> results
```

Doctor creates Service Requests.

Front Desk handles current baseline payment collection.

Do not bypass the payment gate for a billable diagnostic service unless a documented authorization/exemption rule permits it.

Financial records are immutable/history-preserving according to the table design.

---

## 17. Clinical and Diagnostic Results

Rules:

- Final clinical/diagnostic data is not hard-deleted.
- Final results are versioned/corrected; do not silently overwrite history.
- Result finalization must enforce role/permission rules.
- A result must remain traceable to the correct Service Request and Encounter.
- Partial vs final result state must be explicit.
- File attachments must not be treated as the sole structured clinical result when structured data exists.

---

## 18. Prescription Rules

Prescription issuance is history-preserving.

Issued prescriptions must not be overwritten in place.

Inventory/dispensing is outside the current core baseline unless requirements explicitly add it.

---

## 19. API Contract

Base:

```text
/api/v1
```

Prefer resource-oriented endpoints and explicit action endpoints only for true domain commands.

Do not expose persistence table shapes directly.

HTTP/API DTOs are transport contracts, not domain entities.

Use consistent pagination, validation, and error shapes.

Never return HTTP 200 for failed business commands.

---

## 20. Application Services / Use Cases

Application services coordinate work; they do not become giant domain-script classes.

They may:

```text
load aggregates
check permissions
invoke domain behavior
save through ports
publish events
coordinate modules through public contracts
own transaction scope
```

They should not:

```text
contain SQL
directly manipulate another module's tables
duplicate aggregate invariants
format print HTML
parse arbitrary HTTP concerns
```

---

## 21. Transaction Rules

Use `@Transactional` on application methods that represent atomic use cases.

Keep transactions as short as practical.

Do not hold a DB transaction open while calling slow external services unless explicitly required.

Use outbox/event patterns for reliable post-commit integration.

Do not place `@Transactional` on controllers.

---

## 22. Concurrency

Use explicit optimistic concurrency where required.

For SQL Server `rowversion` tables:

- read current version;
- update with version predicate or equivalent mapper contract;
- detect zero-row update;
- return a conflict rather than silently overwrite.

Never implement last-write-wins for sensitive clinical/financial edits without an explicit rule.

---

## 23. Idempotency

Use idempotency for commands/callbacks where retries or double clicks can create duplicates.

Important examples:

```text
Encounter creation
payment callback
integration inbound message
bulk print job creation where duplicate jobs matter
notification dispatch
```

Use `idempotency_keys` where consistent with the schema.

---

## 24. Outbox and External Integration

For reliable external side effects:

```text
business transaction
+
outbox record
COMMIT
+
async/scheduled dispatcher
```

External integration adapters belong to `integration` or the owning infrastructure layer.

External codes must use documented mapping tables/contracts.

Never embed vendor-specific codes throughout domain logic.

---

## 25. Security / RBAC

Authorization is enforced by backend.

Frontend role visibility is never sufficient authorization.

Use explicit permissions rather than scattering role-name checks when permissions are defined by the domain.

Protect:

```text
patient search/read
clinical edit/finalize
billing/payment actions
corporate data
admin/configuration
audit access
```

Principle of least privilege applies.

---

## 26. Audit

Sensitive mutation paths must produce audit records according to project requirements.

Audit should capture enough context to answer:

```text
who
what
when
which record
which action
relevant before/after metadata when appropriate
```

Do not put secret/token material or unnecessarily complete clinical payloads in audit logs.

---

## 27. Logging

Use structured, concise logs.

Never log full:

```text
patient object
clinical note
lab result payload
cccd
credentials
tokens
payment secret
```

Use identifiers/correlation IDs where possible.

Log enough for operations without leaking healthcare information.

---

## 28. Secrets and Configuration

Use profile/config separation:

```text
application.yml
application-local.yml
application-test.yml
environment variables / secret store
```

Do not commit real:

```text
DB passwords
JWT secrets
SMS credentials
payment credentials
integration API keys
```

Do not include secrets in test snapshots or logs.

---

## 29. Validation

Use Bean Validation for transport-level structural validation.

Business validation belongs in domain/application rules.

Examples:

```text
@NotBlank fullName           transport/basic
valid adult eligibility      domain/application rule
subset of batch services     domain invariant
payment authorization        business rule
```

Do not duplicate the same business rule in controller, application service, and domain object.

---

## 30. Search

Do not add Elasticsearch for MVP.

Use SQL Server indexes and well-designed queries first.

Patient search must follow requirement-defined fields and authorization.

Search endpoints need pagination and bounded result sizes.

Do not implement `%keyword%` scans over large tables without reviewing indexes/query plan.

---

## 31. Performance

Optimize measured bottlenecks, but prevent obvious problems:

- no N+1 mapper loops;
- no unbounded list endpoints;
- batch Excel import writes;
- server-side pagination;
- proper indexes;
- avoid loading full clinical history when a summary/read model is enough;
- avoid giant transactions;
- use projection/read models for operational screens where appropriate.

Do not introduce Redis/search brokers merely for hypothetical future scale.

---

## 32. Testing Rules

### Unit

Test pure domain invariants without Spring when possible.

### Application

Test use-case orchestration and transactional behavior.

### Persistence

Use SQL Server Testcontainers for MyBatis integration tests.

Never use H2 as proof that SQL Server SQL is correct.

### Module

Use Spring Modulith:

```java
ApplicationModules.of(NgocKhanhClinicBackendApplication.class).verify();
```

Add module integration tests for important boundaries.

### API

Test:

```text
validation
authorization
status codes
error contract
serialization
idempotency where exposed
```

---

## 33. Test Data

Use builders/fixtures under test code.

Production runtime must not depend on fake data.

Developer seed data must be clearly isolated and must never execute in production accidentally.

Never use real patient data in automated tests.

---

## 34. Code Quality

Avoid:

```text
God service classes
generic BaseService CRUD hierarchies
generic repository abstractions with no domain meaning
reflection-heavy magic
deep inheritance
static mutable state
catch-all exceptions
boolean parameter explosions
massive switch statements for domain workflows when a clearer model exists
```

Prefer cohesive, explicit code.

Do not abstract until there is a real repeated concept.

---

## 35. ADR Policy

Create an ADR for long-lived decisions such as:

```text
module boundary changes
core stack changes
auth/token strategy
transaction/event strategy
outbox strategy
document rendering architecture
file storage architecture
integration strategy
major database identity changes
```

Do not create ADRs for routine implementation details.

---

## 36. Definition of Done

Before completion:

```text
./mvnw test
./mvnw verify
```

And when applicable:

```text
SQL Server Testcontainers integration tests
Spring Modulith verification
security tests
migration startup test
```

The task is not done if:

- required tests fail;
- a migration is missing;
- a public contract changed without documentation;
- an invariant exists only in UI;
- module boundaries are violated;
- secrets/mock production behavior were introduced.

---

## 37. Implementation Order

For a new backend, prefer:

```text
1. Foundation/config
2. SQL Server + Flyway
3. global errors/security/audit primitives
4. Patient reference module
5. Catalog
6. Encounter
7. Clinical
8. Billing/Order Round
9. Diagnostics
10. Health Check
11. Documents/printing
12. Prescription
13. Notification
14. External integration
```

Use `patient` as the first complete reference vertical slice before copying architectural patterns to other modules.

---

## Final Principle

Business correctness and data traceability are more important than reducing the number of classes or writing fewer SQL statements.

Prefer explicit DDD boundaries, reliable SQL Server constraints, auditable clinical/financial history, and testable use cases over framework shortcuts.
