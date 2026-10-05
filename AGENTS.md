# AGENTS.md

## Ngọc Khánh Clinic Backend

This repository contains the production backend for **Ngọc Khánh Clinic Digital Transformation (NKC-DX)**.

This is not a demo/prototype repository.

Current business/schema authority: the owner-selected clean-slate design and
`V001__create_clean_slate_schema.sql`, as recorded in ADR-0013. Follow ADR-0013
where earlier FINAL documents or rules below conflict; retain Participant terminology.

Before making any non-trivial change, read in this order:

1. `PROJECT_RULES.md`
2. `PROJECT_SKILLS.md`
3. Relevant ADRs in `docs/adr/`
4. Backend architecture docs in `docs/architecture/`

Use the owner-selected clean-slate design, current architecture documents and SQL migration for business-contract changes. Keep the two current clean-slate ADRs as decision records. Earlier FINAL copies and retired ADRs do not override this contract. If a required business rule is unavailable, state it and stop at a safe boundary.

Accepted ADRs and project rules override generic skill examples.

---

## 1. Technical Baseline

Use:

```text
Java                 25
Spring Boot          4.x
Spring Framework     7.x
PostgreSQL           18
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
com.ngockhanh.clinic
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
healthexamination
document
prescription
notification
integration
appointment
portal
audit
shared
```

`shared` is technical support; `audit` owns audit contracts and persistence.
See ADR-0012 for the clean-slate module inventory. SQL schema names do not change
Java package naming; keep `healthexamination` without an underscore.

Create only modules required by the current task. Do not generate empty folder trees speculatively.

Use `healthexamination` as the reference module for package structure and layer responsibilities, subject to project rules and accepted ADRs.

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

The domain layer must not depend on Spring MVC, MyBatis, PostgreSQL classes, HTTP DTOs, or persistence records.

---

## 4. Cross-Module Rules

A module may not reach into another module's internals.

Forbidden:

```text
healthexamination -> patient.infrastructure
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

All SQL and Flyway migrations must be PostgreSQL 18 compatible.

---

## 6. Database Source of Truth

The clean-slate V001 is the fresh-database baseline (ADR-0013), superseding table-design-v2.11.
PostgreSQL physical types and application-managed versions follow `docs/architecture/04-persistence.md` and the clean-slate schema.

Important baseline rules:

- English `snake_case` database identifiers.
- Plural table names.
- Primary key: `id` where defined; preserve schema-defined composite keys (ADR-0013).
- Foreign key: `<entity>_id`.
- PostgreSQL types such as `uuid`, `timestamptz(3)` (Java `Instant`), `numeric(14,2)`, `text`/`varchar`, `boolean`, and application-incremented `bigint` version counters.
- Clinical and financial history is never hard-deleted.
- Final clinical results and issued prescriptions are versioned, not overwritten.
- Database schema changes must use Flyway migrations.
- Application startup must not auto-create or auto-alter production schema.

Never use H2 as a substitute for PostgreSQL integration tests.

---

## 7. Core Domain Rules

Do not silently change these rules:

### Patient

- CCCD is mandatory in the current baseline.
- CCCD is unique; its technical field name is `identification_number`.
- No fuzzy duplicate/merge workflow by name or phone in MVP.
- Do not create separate identity-type/passport abstractions unless requirements change.

### Corporate Health Examination

- Organization owns Batch, BatchDay and batch-scoped Participant snapshots.
- Import never creates Patient records; authorized preparation links by exact CCCD.
- Batch has at least one day; DRAFT, READY, FINALIZED and CLOSED are its states.
- Staff reconcile actual performed services within batch scope independently of
  Doctor orders. Keep attendance, roster and reconciliation state independent.
- Validate an entire import before storing staging. Reject duplicates; confirm
  inserts new participants atomically and preserves approved day assignments.
- Each record has one mrn/SHS across its forms. Issued snapshots/versions are
  immutable and administrative print data comes from the issued snapshot.

### Encounter and diagnostic progress

- Encounter and ServiceRequest retain their own lifecycles.
- Diagnostic progress is derived from Encounter, OrderRound, ServiceRequest, ServiceAuthorization, location and Result.
- Derive operational progress from Encounter and its related records; do not persist a parallel queue-stage model.
- Doctor worklists are driven by Encounter and required ServiceRequest/Result state.
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
- add HTTP status/envelope to application responses; keep domain-to-response mapping in application;
- do not contain business logic;
- do not call MyBatis mappers directly;
- do not open manual JDBC connections;
- do not own transactions.

Validation ownership:

- Bean Validation on `api/request` DTOs, triggered with `@Valid`, owns HTTP structural constraints such as bounds, formats, and allowed values.
- Use cases own business rules and application preconditions; do not repeat the same request constraint for the same call path.
- If another supported entry point needs that constraint, define it once in the application input contract and have adapters use that contract.

Do not expose persistence records as API responses.

Do not expose stack traces, SQL, internal exception classes, or sensitive patient data in errors.

---

## 9. Transactions and Concurrency

Transaction boundaries belong in application use cases/services.

Use `@Transactional` at the application layer when a use case modifies one consistency boundary.

Do not annotate controllers with `@Transactional`.

Do not place Spring transaction annotations in pure domain objects.

Use the `bigint` `row_version` counter or another explicit optimistic-concurrency strategy where the schema requires concurrent-update protection.

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

Java types and collections:

- Use wrapper types (`Integer`, `Long`, `Short`, `Byte`, `Double`, `Float`, `Boolean`, `Character`) instead of primitive declarations in new or changed code, including required persistence fields.
- Declare ordered collections as `List<T>`; use `ArrayList<T>` when mutation is needed. Do not represent business lists with arrays or use single-element arrays as mutable scalar holders.
- Preserve required-value validation and compare wrapper values with `equals`/`Objects.equals`, never reference equality. A wrapper does not make a required value optional.
- Follow [PROJECT_RULES.md, Java types and collections](PROJECT_RULES.md#java-types-and-collections) for null handling, immutable lists, technical exceptions and migration checks.

Controller comments:

- Write concise English Javadoc for every public endpoint method.
- Describe the endpoint behavior and document each parameter with `@param` and each non-void result with `@return`.
- Document externally visible behavior only; do not narrate annotations, logging, or obvious implementation details.
- Update the Javadoc when the endpoint contract, parameters, response, or authorization behavior changes.

Application comments and logging:

- When adding or changing use cases or published application contracts, follow
  [application Javadoc](PROJECT_RULES.md#application-comments-and-javadoc).
- Follow [logging](PROJECT_RULES.md#27-logging) for event ownership, levels,
  safe fields, exception handling and transaction-aware wording.
- Follow [code quality and agent workflow](PROJECT_RULES.md#34-code-quality)
  to keep changes cohesive, readable and verifiable.

---

## 14. DTO / Model Rules

Follow [DTO construction](PROJECT_RULES.md#dto-construction) for the Lombok
`@Builder` preference when adding or changing DTOs and their construction sites.

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
- MyBatis/SQL tests use PostgreSQL 18 Testcontainers.
- Do not use H2 to claim PostgreSQL compatibility.
- Use Spring Modulith verification tests for module boundaries.
- Test authorization for sensitive endpoints.
- Test concurrency/idempotency for workflows that require them.
- Do not mock the unit under test.

Critical business rules include:

```text
CCCD / `identification_number` uniqueness
health-examination lifecycle (no backend age eligibility rule)
participant import validation
authorized participant service reconciliation
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
V<version>__<description>.sql
```

Migrations must include constraints and indexes required by the documented model.

Apply the full migration chain in `src/main/resources/db/migration/` for a fresh database; see `docs/architecture/04-persistence.md`.

Do not rely only on application validation for database invariants such as a unique `patients.identification_number`.

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
PostgreSQL constraints
+
safe healthcare-data handling
+
real tests
```

over framework-driven domain models, speculative abstractions, and cross-module shortcuts.
