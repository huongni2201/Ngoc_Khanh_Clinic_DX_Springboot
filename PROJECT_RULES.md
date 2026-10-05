# Ngọc Khánh Clinic Backend — Project Rules

> Mandatory backend engineering rules for NKC-DX. A later accepted ADR or explicitly newer source-of-truth document may supersede a rule.

## 1. Product and Business Baseline

The backend supports a real outpatient clinic and corporate health-check workflow.

Current source-of-truth documents:

The owner-selected clean-slate design and `V001__create_clean_slate_schema.sql`
are the current business/schema contract (ADR-0013). Earlier FINAL documents and
ADRs remain historical references where superseded. Read ADR-0013 for the changed
identity, roster, pricing, snapshots, release and concurrency contracts.

Do not infer domain behavior from UI mockups when these documents define the rule.

If code, UI, and documentation disagree, identify the conflict before changing business behavior.

---

## 2. Approved Stack

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

Persistence stack is **MyBatis + PostgreSQL 18**.

Forbidden by default:

```text
JPA
Hibernate
Spring Data JPA
H2 as PostgreSQL substitute
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

ADR-0012 defines the clean-slate module inventory: fourteen business contexts,
the supporting audit context, and shared technical code. Keep `healthexamination`
as the Java module name even though the source design uses `health_examination`.

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
response DTOs in application/response
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
domain -> infrastructure/database driver
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
shared/idempotency
shared/web
shared/time
```

Do not put domain concepts in `shared`.

Audit contracts and persistence belong to `audit`; consumers use the published
`audit::recording` interface rather than a shared audit package (ADR-0012).

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

Each file in `infrastructure/persistence/record` represents rows of exactly one
existing database table. Reuse its table record; do not add summary, reference,
join-result, or screen-specific record files here. This convention does not add JPA.

Repository adapters coordinate SQL mapper calls. Converters map table records to
domain aggregates, entities, or explicit read contracts, and back. Converters do
not query the database. Restore aggregates with all required owned state; use read
contracts instead of incomplete aggregates for read-only summaries.

Reuse existing files/folders. A private converter nested in its repository and a
read contract nested in its owning interface are allowed when needed to avoid new
files. Do not generate new files or folders without explicit authorization.

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
- PostgreSQL-specific behavior must be covered by integration tests.

---

## 11. PostgreSQL 18 Rules

Clean-slate conventions (ADR-0013):

```text
table names       plural snake_case
column names      snake_case
primary key       id where defined; retain schema-defined composite keys
foreign key       <entity>_id
time              `timestamptz(3)`; Java `Instant`; values represent UTC instants
money             numeric(14,2)
text              text or varchar(n), preserving documented length limits
public UUID       uuid where specified
concurrency       application-incremented bigint `row_version`, compared on update
```

Use database constraints for true invariants.

Examples:

```text
UNIQUE identification_number
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
V<version>__<description>.sql
```

Rules:

- Never use application auto-DDL in production.
- Never edit an already-applied shared migration.
- Add a new migration for every subsequent schema change.
- Fresh installations apply `V001__create_clean_slate_schema.sql`. Existing databases with the former V001-V003 history require a separate data-conversion plan; do not reuse that history with this baseline.
- Include required FK/UK/check/index definitions in migrations.
- Migration rollback strategy must be considered for destructive changes.
- Destructive production data changes require explicit review.

---

## 13. Identity and Patient Rules

Current MVP:

```text
identification_number = mandatory patient identifier; CCCD is the current business/document label
```

Rules:

- Exact CCCD lookup before Patient creation.
- `patients.identification_number` must be unique.
- No passport/identity-type abstraction in baseline.
- No fuzzy duplicate merge by name/phone in baseline.
- Do not create `patient_contacts`, `patient_addresses`, or `patient_merge_history` unless requirements explicitly reintroduce them.
- Patient historical clinical/financial data is not hard-deleted.

---

## 14. Health Examination Rules

Organization owns Batches. Each Batch has at least one BatchDay and a maximum
service scope. Batch participants are independent roster snapshots; there is no
organization-level Participant aggregate or table.

- Preserve CCCD as text; a participant is not automatically a Patient.
- Prepare/link Patient and Encounter only in an authorized preparation use case,
  using exact CCCD. Import never creates them.
- Batch states are DRAFT, READY, FINALIZED and CLOSED; sites are CLINIC or
  ORGANIZATION_SITE. Date bounds derive from BatchDays.
- Reference price is captured from catalog on service addition; negotiated price
  is entered for the batch. Later batch price changes preserve performed-item
  snapshots unless an explicit audited repricing use case changes them.
- Staff reconciliation records performed items within batch service scope; it
  is independent of Doctor orders. Attendance and reconciliation have their own
  states. Preserve historical rows when a performed selection is withdrawn.
- Import validates the entire file before storing a VALIDATED job or any staging.
  Duplicates within the file or batch reject it; confirm inserts only new rows
  atomically and retries return the stored result.
- Selected BatchDay IDs and approved allocations are captured in staging.
  Confirmation does not reallocate. Manual day changes preserve prepared links.
- The record's mrn is shared across its forms. Administrative snapshots and
  clinical record versions are separate typed models, immutable after issue.
- The backend does not enforce age eligibility; see `docs/architecture/03-domain-and-workflows.md`.
- Verify versions and audit sensitive mutations in the application transaction.

---

## 15. Encounter and Diagnostic Progress Rules

Encounter, ServiceRequest and Result have their own lifecycles. Diagnostic progress and worklists
are derived from Encounter, OrderRound, ServiceRequest, ServiceAuthorization, performing location
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

Structured input belongs in `api/request`, then maps to `application/command` or
`application/query`. Simple path IDs may remain scalar parameters. Use cases return
payload DTOs from `application/response`; controllers use those directly and add
only HTTP status and the shared envelope. No domain-to-response mapping belongs in
controllers. Commands with no result may return `void`.

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

### Application comments and Javadoc

For new or changed application use cases, write concise English class Javadoc
stating the business operation. Document each public use-case entry point and
published application contract with its behavior, `@param` for every parameter,
`@return` for a non-void result, and `@throws` for caller-actionable failures.
When implementing a documented interface, inherit its contract and document only
implementation-specific guarantees; do not copy the same Javadoc twice.

Describe relevant guarantees: required caller authorization, resource scope,
atomic writes/audit, expected-version conflicts, retry/idempotency behavior and
external side effects. Document only guarantees actually enforced by the current
flow. A caller-provided actor ID alone is not proof of authorization. A method
return inside an enclosing transaction does not guarantee that transaction committed.

Use short inline comments for non-obvious reasons, such as why confirmed retries
precede version checks, why reviewed day assignments stay frozen, or why an audit
failure must roll back writes. Link the relevant ADR/contract when it explains a
decision; keep the essential reason beside the code. Improve names/control flow
before adding a comment that merely paraphrases the next statement.

Private methods, trivial accessors, and command/query/response fields need comments
only for semantics not evident from their names/types. Update comments with behavior;
remove obsolete or commented-out code. TODOs must name a concrete missing contract
or tracked follow-up and must never disguise successful fake behavior.

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

For PostgreSQL `row_version` tables:

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

Controllers and application use cases use `@Slf4j` and parameterized logs with
stable English event descriptions and named fields. Application owns meaningful
use-case events; controllers add only useful HTTP context. Keep domain objects,
DTOs and pure converters free of operational logging. A logger annotation does
not require entry/exit logs on every method.

| Level | Use |
|---|---|
| DEBUG | Useful read/request diagnostics, expected validation/conflicts and idempotent replays; omit routine noise. |
| INFO | Meaningful mutation milestones, summarized once per operation. |
| WARN | Recoverable dependency/degraded behavior or security signals that need attention. |
| ERROR | Unexpected failure requiring investigation, at the boundary that handles it. |

Log only allowlisted operational fields needed for diagnosis: internal resource
IDs, existing correlation ID, counts, durations, versions, and safe outcome/error
codes. IDs are still sensitive metadata; log only those needed. Never serialize
request/response/command/query/domain objects, raw search text, uploaded rows,
filenames, names, contact details, CCCD, clinical content, credentials, session
cookies, tokens or payment secrets. Parameterized placeholders do not sanitize
values. Reuse validated correlation context when available rather than generating
a new ID per layer or passing logging-only parameters through domain contracts.

Inside a transaction, describe a completed step explicitly as pending commit,
for example `Organization insert executed; commit pending: organizationId={}`.
Do not report committed/succeeded merely because a repository call returned.
If committed-outcome logging is required, emit it only after confirmed commit;
do not add an event/outbox framework solely for a log statement. Logs are not
the audit trail; required business audit still commits with business writes.

Unexpected failures are logged once by the handling HTTP/job/integration boundary.
Intermediate methods propagate or translate while preserving the cause; do not
catch, log and rethrow at every layer. Expected validation/conflict outcomes do
not need ERROR stack traces. Raw exception messages, SQL-driver details and nested
causes can contain healthcare data: use safe codes/context in operational logs
and only include diagnostics whose redaction and access controls are established.
Preserve the original cause internally even when it cannot safely be logged.

For bulk imports, log a bounded summary such as job ID, accepted/rejected counts
and elapsed time, not one INFO line per participant. Use existing logging and
correlation facilities; avoid generic logging wrappers or timing AOP without a
demonstrated need. Test redaction or event behavior when it is a requirement;
do not snapshot ordinary log wording or require a logger test for every use case.

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
subset of batch services     domain invariant
payment authorization        business rule
```

Do not duplicate the same business rule in controller, application service, and domain object.

---

## 30. Search

Do not add Elasticsearch for MVP.

Use PostgreSQL indexes and well-designed queries first.

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

Use PostgreSQL 18 Testcontainers for MyBatis integration tests.

Never use H2 as proof that PostgreSQL SQL is correct.

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

Extract shared implementation for a real repeated concept; introduce boundary
ports when the architecture requires them, even with one current implementation.

### Readability and change scope

- Keep a use case readable as orchestration: load/check, invoke domain behavior,
  persist/audit, map its result. Preserve business-required ordering and transaction
  scope; extract a method only when its name captures a cohesive operation.
- Prefer guard clauses, descriptive names and straightforward loops when they
  clarify branching or mutation. Use streams for clear transformations, not hidden
  persistence calls or state changes. Line count alone is not a quality target.
- Introduce abstractions for a real responsibility or boundary. Domain repository
  ports and external-system ports remain valid with one implementation; generic
  service interfaces, base classes and pass-through layers need a concrete benefit.
- Keep validation at its owning boundary; required database constraints and
  authorization checks are not redundant merely because the frontend also checks.
- Use the existing formatter/import conventions. Remove dead code introduced by
  the current change; keep unrelated cleanup and broad type migrations separate.
- Make tests assert observable outcomes, invariants and meaningful failure paths.
  A unit test with mocks cannot prove commit/rollback, SQL or concurrency behavior;
  use the documented integration tests for those guarantees.

Outside domain, prefer Lombok `@RequiredArgsConstructor` for final dependencies and
Lombok for useful DTO boilerplate. Existing Java records do not need redundant
constructors. Avoid generated `toString()` on sensitive DTOs. Domain may use Lombok
but must preserve invariant-enforcing constructors, factories, and business methods.

### DTO construction

For new or changed DTOs, prefer Lombok `@Builder` and construct instances with
`DtoType.builder().field(value).build()` rather than direct `new DtoType(...)`
calls. Apply this preference to request, response, command/query and integration
DTOs, including test fixtures. Preserve required-value validation, defaults,
defensive copies and serialization/deserialization contracts.

Keep an existing constructor or factory when it is required by a framework or
enforces validation/invariants that a builder would bypass. Existing Java records
may remain records; apply `@Builder` when changing their construction without
converting them to classes solely for this rule. Migrate affected construction
sites within the current change; keep unrelated DTO migrations separate.

### Agent workflow

Before editing, identify the owning module, active contract, supported callers and
observable acceptance criteria. Read the relevant architecture sections instead
of loading unrelated documents or invoking every available skill. Reuse current
capabilities before proposing dependencies, frameworks or new files; the file
creation restriction in section 9 still applies.

Preserve existing worktree changes. Keep each diff tied to the requested outcome,
including comments and documentation. Treat existing code as evidence, not authority
over accepted contracts. Report conflicts with exact sources and stop only the
dependent business change; continue independent authorized work.

Review the final diff against the acceptance criteria, use the verification rules
in section 36, and report commands actually run plus failures/skips. Record lasting
rules in their owning document and link to them elsewhere instead of copying whole
policies. Plans/reviews describe work and evidence; they do not silently supersede
accepted ADRs or current business contracts.

### Java types and collections

Use wrapper types rather than primitive declarations in new or changed Java code:
`Integer` for `int`, `Long` for `long`, `Short` for `short`, `Byte` for `byte`,
`Double` for `double`, `Float` for `float`, `Boolean` for `boolean`, and
`Character` for `char`. This applies to fields, record components, parameters,
return values and local variables in production code and corresponding tests.
Keep domain-specific value objects and `BigDecimal` for money.

Required values remain required, including SQL `NOT NULL` columns. Use `@NotNull`
on required HTTP wrapper fields; numeric bounds alone do not reject null. Keep
domain/application preconditions at their existing owning boundary. Resolve null
before unboxing, arithmetic, ordering or boolean conditions. Use `equals` or
`Objects.equals` for wrapper value comparisons; never use `==`/`!=` between
wrappers. Use `Boolean.TRUE.equals(value)` only when null legitimately means false.

Declare ordered business collections as `List<T>` and use `new ArrayList<>()`
for mutable storage, for example `List<Integer> rowNumbers = new ArrayList<>();`.
Do not use `int[]`, `long[]`, `Integer[]`, `String[]` or other arrays to represent
business lists. Use `List.of`, `List.copyOf` or `Stream.toList` for immutable
results; copy into an `ArrayList` before mutation. Preserve defensive copies and
existing mutability contracts. Keep `Set` for uniqueness and `Map` for keyed lookup.
Do not replace single-element scalar arrays with single-element lists: use
wrapper fields on the owning listener/state object instead. Atomic types require
an actual concurrency need, not merely a lambda capture workaround.

Technical exceptions are limited to Java/JDK/library contracts that require a
primitive or array: overridden signatures, annotation elements and their
compile-time primitive constants, the JVM entry point, and binary/crypto/I/O
buffers or PostgreSQL `bytea` (`byte[]`). Confine unavoidable arrays such as
`String.split` results to the adapter operation; expose business lists as `List<T>`.
Primitive literals, casts and unavoidable unboxing at these boundaries are valid.
Explain non-obvious exceptions locally; do not blanket-exempt infrastructure or tests.

Apply this rule when adding or changing declarations. Existing primitive/list-array
declarations are migration debt, not approved examples. A type migration must
update callers, equality/null behavior, MyBatis contracts and affected tests
together, including schema-record type assertions. Do not perform an unrelated
repository-wide replacement as part of a focused change.

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
PostgreSQL Testcontainers integration tests
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
2. PostgreSQL 18 + Flyway
3. global errors/security/audit primitives
4. Patient
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

Use `healthexamination` as the reference for package structure, request/command/query/response boundaries, use cases, domain repository ports, and MyBatis adapters. Apply project rules and accepted ADRs when reusing a pattern; existing code is not an exception to those rules.

---

## Final Principle

Business correctness and data traceability are more important than reducing the number of classes or writing fewer SQL statements.

Prefer explicit DDD boundaries, reliable PostgreSQL constraints, auditable clinical/financial history, and testable use cases over framework shortcuts.
