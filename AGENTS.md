# AGENTS.md

## Ngọc Khánh Clinic Backend

This repository contains the production backend for **Ngọc Khánh Clinic Digital Transformation (NKC-DX)**.

This is not a demo/prototype repository.

Before making any non-trivial change, read in this order:

1. `PROJECT_RULES.md`
2. `PROJECT_SKILLS.md`
3. Relevant ADRs in `docs/adr/`
4. Backend architecture docs in `docs/architecture/`
5. The current business source-of-truth documents:
   - `requirement-v2.3`
   - `use-case-v2.4`
   - `table-design-v2.9`

If the source-of-truth documents are not available in the repository/workspace, do not invent business rules. State the missing contract and stop at a safe boundary.

Accepted ADRs and project rules override generic skill examples.

---

## 1. Technical Baseline

Use:

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

Do not add JPA/Hibernate/Spring Data JPA.

Do not introduce another persistence framework without an accepted ADR.

---

## 2. Architecture

Use:

```text
DDD
+
Modular Monolith
+
Package-by-Bounded-Context
+
Ports/Adapters inside each module
```

Canonical root package:

```text
com.nkc.clinic
```

Do not use package names with underscores.

Do not create a generic top-level `feature` package.

Canonical modules:

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

Create only modules required by the current task. Do not generate empty folder trees speculatively.

---

## 3. Module Internal Structure

A business module follows this shape when the layer is actually needed:

```text
<module>/
├── api/
├── application/
├── domain/
└── infrastructure/
```

Responsibilities:

```text
api             HTTP/API boundary only
application     use cases, orchestration, transaction boundary
domain          aggregates, entities, value objects, domain services, ports
infrastructure  MyBatis, external systems, adapters, technical implementation
```

Dependency direction:

```text
api -> application -> domain
infrastructure -> domain
```

The domain layer must not depend on Spring MVC, MyBatis, SQL Server classes, HTTP DTOs, or persistence records.

---

## 4. Cross-Module Rules

A module may not reach into another module's internals.

Forbidden:

```text
healthcheck -> patient.infrastructure
billing -> encounter.persistence mapper
clinical -> diagnostics SQL table directly
module A -> module B internal package
```

Allowed communication:

```text
public application API
published module interface
domain/application event
explicit query/read contract
```

Do not share repositories or MyBatis mappers across bounded contexts.

Use Spring Modulith tests to verify boundaries.

---

## 5. Persistence Rules

Domain model and persistence model are separate concepts.

Example:

```text
Patient                domain aggregate/entity
PatientRecord          SQL/MyBatis persistence representation
PatientMapper          MyBatis mapper
PatientRepository      domain port
MyBatisPatientRepository infrastructure adapter
```

Never annotate domain objects as persistence records merely for convenience.

Use XML mappers for non-trivial SQL. Small obvious statements may use annotations only when readability is better.

All SQL must be SQL Server compatible.

---

## 6. Database Source of Truth

`table-design-v2.9` is the MVP database baseline unless superseded by a later accepted document/ADR.

Important baseline rules:

- English `snake_case` database identifiers.
- Plural table names.
- Primary key: `id`.
- Foreign key: `<entity>_id`.
- SQL Server types such as `bigint IDENTITY`, `uniqueidentifier`, `datetime2(3)`, `decimal(18,2)`, `nvarchar`, `varchar`, and `rowversion`.
- Clinical and financial history is never hard-deleted.
- Final clinical results and issued prescriptions are versioned, not overwritten.
- Database schema changes must use Flyway migrations.
- Application startup must not auto-create or auto-alter production schema.

Never use H2 as a substitute for SQL Server integration tests.

---

## 7. Core Domain Rules

Do not silently change these rules:

### Patient

- CCCD is mandatory in the current baseline.
- CCCD is unique.
- No fuzzy duplicate/merge workflow by name or phone in MVP.
- Do not create separate identity-type/passport abstractions unless requirements change.

### Corporate Health Check

```text
Company
  -> HealthCheckBatch
      -> HealthCheckBatchService
      -> HealthCheckBatchEmployee
          -> HealthCheckRecord
          -> HealthCheckBatchEmployeeService
```

- `CompanyEmployee` is not automatically a `Patient`.
- Importing an employee roster must not create Patient records.
- Patient linking/creation occurs when the employee is actually checked in.
- Only Doctor may select the per-employee subset of examination services.
- Selected employee services must be a subset of `HealthCheckBatchService`.
- Front Desk must not add/select examination items for an employee.
- No service outside batch scope may be added by that selection flow.
- One health-check record has one SHS/health-check record code used across its forms.
- Mẫu số 03 is the master health-check form and carries the SHS barcode.
- Administrative print data comes from the `HealthCheckRecord` snapshot, not the mutable current Patient record.

### Encounter / Journey

- `Encounter.status` and `Journey.currentStage` are separate concepts.
- Do not create reception/exam queue-ticket models.
- Doctor worklists are driven by encounter/journey state.
- Multiple `OrderRound`s may exist in one Encounter.

### Payment Gate

- Diagnostic services are executable only after valid payment authorization according to the requirement.
- Doctor creates orders; Doctor does not collect payment.
- Front Desk handles the baseline payment collection workflow.

---

## 8. API Rules

Use versioned REST APIs:

```text
/api/v1/...
```

Controllers:

- validate transport input;
- call application use cases;
- map responses;
- do not contain business logic;
- do not call MyBatis mappers directly;
- do not open manual JDBC connections;
- do not own transactions.

Do not expose persistence records as API responses.

Do not expose stack traces, SQL, internal exception classes, or sensitive patient data in errors.

---

## 9. Transactions and Concurrency

Transaction boundaries belong in application use cases/services.

Use `@Transactional` at the application layer when a use case modifies one consistency boundary.

Do not annotate controllers with `@Transactional`.

Do not place Spring transaction annotations in pure domain objects.

Use SQL Server `rowversion` or another explicit optimistic-concurrency strategy where the schema requires concurrent-update protection.

Detect and report lost-update conflicts explicitly.

---

## 10. Idempotency

Commands vulnerable to duplicate submission must be idempotent where required by the business flow.

Examples:

```text
create encounter
payment callbacks
external integration callbacks
document generation jobs
notification dispatch
```

Use the existing `idempotency_keys` design where applicable.

Never solve duplicate requests only with frontend button disabling.

---

## 11. Events and Integration

Use in-process module events for decoupling inside the modular monolith when synchronous direct calls would create undesirable coupling.

For external side effects that must survive failures, use the outbox pattern defined by the project schema.

Do not publish external messages before the database transaction commits.

Do not introduce Kafka/RabbitMQ only to communicate between modules in the same monolith unless an accepted ADR requires it.

---

## 12. Security and Healthcare Data

Healthcare data is sensitive.

Never:

- log full patient/clinical payloads;
- log passwords, access tokens, refresh tokens, CCCD in full when unnecessary, or secrets;
- commit credentials;
- store secrets in `application.yml`;
- trust frontend authorization;
- expose unrestricted patient search endpoints;
- bypass permission checks for convenience.

Backend authorization is authoritative.

Security-sensitive actions and clinical/financial changes must be auditable according to requirements.

Secrets belong in environment variables or an approved secret store.

---

## 13. Naming and Code Style

Use English identifiers in source code and database objects.

Use Vietnamese only for user-facing messages when appropriate.

Conventions:

```text
packages/classes/methods  standard Java conventions
database tables/columns   snake_case
constants                 UPPER_SNAKE_CASE
REST resources            kebab-case when multi-word
```

Avoid vague names:

```text
Helper
Utils
CommonService
BaseService
Manager
Processor
Data
Info
```

unless the name accurately describes a technical abstraction.

Prefer domain language from requirements.

---

## 14. DTO / Model Rules

Keep separate models when responsibilities differ:

```text
API Request/Response
Application Command/Query
Domain Model
Persistence Record
Integration DTO
```

Do not create mappings only for ceremony, but never collapse boundaries when doing so leaks infrastructure or API concerns into domain logic.

Do not use `Map<String, Object>` for stable business contracts.

Use enums/value objects for stable domain concepts where they improve correctness.

---

## 15. Error Handling

Use a centralized API error contract.

At minimum distinguish:

```text
validation error
not found
conflict
business rule violation
unauthorized
forbidden
concurrency conflict
integration failure
unexpected server error
```

Never catch `Exception` only to return HTTP 200.

Never swallow persistence or integration failures.

Preserve root cause internally while returning a safe external error.

---

## 16. Testing

Testing pyramid:

```text
domain unit tests
application use-case tests
MyBatis repository integration tests
module integration tests
controller/API tests
critical end-to-end tests when needed
```

Rules:

- Domain rules should be testable without Spring where possible.
- MyBatis/SQL tests use SQL Server Testcontainers.
- Do not use H2 to claim SQL Server compatibility.
- Use Spring Modulith verification tests for module boundaries.
- Test authorization for sensitive endpoints.
- Test concurrency/idempotency for workflows that require them.
- Do not mock the unit under test.

Critical business rules include:

```text
CCCD uniqueness
under-18 rejection for adult health checks
employee import validation
doctor-only employee service selection
subset-of-batch-service enforcement
health-check snapshot/reprint behavior
payment gate
multiple order rounds
result finalization/versioning
idempotent encounter creation
```

---

## 17. Migrations

Every schema change requires a new Flyway migration.

Never modify an already-applied migration in a shared environment.

Naming:

```text
V001__create_security_tables.sql
V002__create_patient_tables.sql
V003__create_catalog_tables.sql
...
```

Migrations must include constraints and indexes required by the documented model.

Do not rely only on application validation for database invariants such as unique CCCD.

---

## 18. Dependencies

Before adding a dependency, verify:

1. Java 25 compatibility.
2. Spring Boot 4 compatibility.
3. Existing project capability.
4. Maintenance status.
5. Security implications.
6. Whether the JDK/Spring/MyBatis already solves the problem.
7. Whether it is needed now.

Core stack changes require an ADR.

Do not add JPA, Hibernate, Lombok alternatives, code generators, CQRS frameworks, messaging brokers, cache servers, or search engines without a concrete current need.

---

## 19. No Demo Shortcuts

Production code must not contain:

- fake patients;
- fake companies;
- hard-coded medical results;
- mocked repositories wired into runtime profiles by default;
- TODO implementations that return successful fake responses;
- bypassed authorization;
- hard-coded payment success.

Use test fixtures and dedicated development seed migrations/data where explicitly needed.

---

## 20. AI / Code-Agent Discipline

Before coding:

```text
1. Read project rules.
2. Read relevant ADRs.
3. Read current requirement/use-case/table design.
4. Inspect existing implementation.
5. Identify the owning bounded context.
6. Identify aggregate/invariants.
7. Check existing public contracts.
8. Implement the smallest cohesive change.
9. Add/update tests.
10. Run verification.
```

Do not invent:

- tables;
- statuses;
- permissions;
- API fields;
- clinical rules;
- payment rules;
- print behavior;
- integrations.

If documentation conflicts, stop and report the conflict instead of choosing silently.

---

## 21. Verification

Before claiming completion, run all applicable checks:

```bash
./mvnw test
./mvnw verify
```

Also run relevant integration/module tests when affected.

If Docker/Testcontainers are required and unavailable, report that the integration checks were not run.

Never claim a check passed unless it actually ran successfully.

---

## 22. Completion Report

At task completion report:

1. What changed.
2. Bounded context/module affected.
3. Files added/modified.
4. Database migrations added.
5. Public API changes.
6. Tests added/updated.
7. Checks actually run.
8. Assumptions or unresolved requirement conflicts.
9. Remaining risks/TODOs.

---

## Guiding Principle

Prefer:

```text
explicit domain boundaries
+
simple application orchestration
+
domain invariants close to the domain
+
MyBatis as infrastructure
+
SQL Server constraints
+
safe healthcare-data handling
+
real tests
```

over framework-driven domain models, speculative abstractions, and cross-module shortcuts.
