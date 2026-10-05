# Ngọc Khánh Clinic Backend — Project Rules

> Owning backend technical policy. MUST is required; SHOULD is a default whose
> exceptions need a concrete reason in the change. Unqualified rules are mandatory.

## 1. Product and Business Baseline

Use [AGENTS.md](AGENTS.md#source-of-truth-and-workflow) for source precedence.
Accepted ADRs define intended behavior; current handlers/DTOs define available
HTTP contracts. Identify disagreements before changing dependent behavior.
[Domain workflows](docs/architecture/03-domain-and-workflows.md) own business
invariants; [API inventory](docs/api/clean-slate-migration.md) owns route availability.
Schema records and UI mockups do not authorize unsupported workflows.

## 2. Approved Stack

Java 25; Spring Boot 4 / Framework 7; PostgreSQL 18; MyBatis 4.x starter;
Spring Modulith 2.x; Maven; Flyway; JUnit 5, AssertJ, Mockito, Testcontainers.
Persistence is MyBatis + PostgreSQL. No JPA/Hibernate/Spring Data JPA or H2 as
a PostgreSQL substitute. Core stack changes require an ADR.

## 3. Root Package

Root: `com.ngockhanh.clinic`. Java packages are lowercase without underscores.

## 4. Modular Monolith

One deployable application, not a Maven multi-module project or microservices MVP.
[ADR-0012](docs/adr/0012-clean-slate-module-boundaries.md) owns the inventory:
identity, patient, catalog, encounter, clinical, billing, diagnostics,
healthexamination, document, prescription, notification, integration,
appointment, portal; supporting audit; shared technical code.
Keep `healthexamination` as the Java name.

## 5. DDD Layering Per Module

Create only roles required by the task. Canonical layout for new business flows:

```text
<module>/
├── api/
│   ├── controller/                    XxxController
│   └── request/                       XxxRequest
├── application/
│   ├── command/                       CreateXxxCommand, UpdateXxxCommand
│   ├── query/                         GetXxxQuery, XxxListQuery; read contracts
│   ├── response/                      XxxResponse
│   ├── usecase/                       CreateXxxUseCase, GetXxxUseCase, ListXxxUseCase
│   ├── port/                          external/application ports when needed
│   └── exception/                     application failures when needed
├── domain/
│   ├── aggregate/                     aggregate roots; create/restore factories
│   ├── entity/                        owned entities
│   ├── valueobject/                   immutable value objects
│   ├── enums/                         domain enums
│   ├── repository/                    XxxRepository ports; domain read contracts
│   ├── event/                         domain events when needed
│   └── exception/                     invariant violations
└── infrastructure/
    ├── persistence/
    │   ├── record/                    XxxRecord; one existing table per record
    │   ├── mapper/                    XxxMyBatisMapper
    │   ├── repository/                MyBatisXxxRepository
    │   ├── converter/                 XxxPersistenceConverter
    │   └── view/                      XxxView; SQL projections/join results
    └── <existing adapter package>/    external adapters/configuration
```

API owns HTTP mapping and structural validation. Application owns use cases,
transactions, authorization orchestration, cross-aggregate coordination and
command/query/domain/read-contract → response mapping. Domain owns invariants,
aggregates, entities, value objects, services, events and repository ports;
it remains framework-light and persistence-ignorant. Infrastructure owns SQL,
records, adapters and technical configuration.
Reuse existing supported contracts; do not rename modules wholesale to fit examples.

## 6. Dependency Direction

Allowed: api → application; application → domain; infrastructure → domain and
deliberately published application ports/read contracts; bootstrap → adapters.
Domain imports no application, API, infrastructure, MyBatis, database-driver or
HTTP DTO concerns. Application imports no `api.*` or foreign mappers.
Controllers call use cases, not repositories/mappers, and own no business rules,
SQL or transactions.

## 7. Module Encapsulation

Cross-module access uses published application facades, events or explicit
read/query contracts. No imports of foreign infrastructure/internal/persistence/
mapper/record packages. Cross-context joins require documented read-model
ownership, not convenience. Spring Modulith verification protects these boundaries.

## 8. `shared` Rules

Shared contains cross-cutting technical config/security/errors/idempotency/web/time.
Business concepts belong to their context. Audit contracts and storage belong
to `audit`; consumers use `audit::recording` (ADR-0012).

## 9. Domain vs Persistence Model

Keep domain models separate from table records:

```text
patient/domain/aggregate/Patient.java
patient/domain/repository/PatientRepository.java
patient/infrastructure/persistence/record/PatientRecord.java
patient/infrastructure/persistence/mapper/PatientMyBatisMapper.java
patient/infrastructure/persistence/repository/MyBatisPatientRepository.java
```

Each persistence record represents exactly one existing table. Reuse its record;
summary/reference/join/screen shapes belong in `infrastructure/persistence/view`
with suffix `View`. Map SQL views inside the adapter to the owning port's typed
read contract: `domain/repository` for domain ports, `application/query` for
application read ports. Those contracts never import infrastructure. Published
reads still obey module boundaries.

Adapters coordinate SQL. Pure converters map records ↔ domain/read contracts
without querying. Restore complete aggregates; use read contracts for summaries.
Domain never depends on MyBatis annotations.

Creating cohesive files inside the canonical tree, existing adapter/test packages,
mapper resources and Flyway directory is part of an authorized task. This includes
converters, read models, tests and migrations; no separate file approval is needed.
Reuse matching responsibilities. Nested types are for local concepts, not file-count
workarounds. New top-level modules/layers need an approved contract.

## 10. MyBatis Rules

Infrastructure only. XML resources: `src/main/resources/mapper/<module>/*.xml`.
Prefer XML for non-trivial SQL; annotations may serve small obvious queries.
Bind values with `#{...}`; never interpolate untrusted `${...}`.
Select required columns, use stable pagination ordering, avoid N+1 loops and
batch supported bulk writes. Business branching stays at its owning layer.
PostgreSQL-specific SQL requires integration tests.

## 11. PostgreSQL 18 Rules

[ADR-0013](docs/adr/0013-clean-slate-application-contract.md) owns conventions:

```text
tables          plural snake_case
columns         snake_case
primary keys    id where defined; retain actual composite keys
foreign keys    <entity>_id
time            timestamptz(3); Java Instant; UTC instants
money           numeric(14,2); Java BigDecimal
text            text/varchar(n) with documented lengths
public UUID     uuid where specified
concurrency     bigint row_version; SQL compares and increments
```

Use required FK/UK/NOT NULL/check constraints. Preserve exact CCCD as text and
documented business-code uniqueness. Justify indexes with real query patterns.

## 12. Flyway Rules

All schema changes use `src/main/resources/db/migration/V<version>__<description>.sql`.
Never edit applied shared migrations or use runtime auto-DDL in production.
Fresh installs apply the full chain. The clean-slate V001 cannot replace former
V001–V003 history on deployed databases; conversion follows
[deployment policy](docs/architecture/06-testing-and-operations.md#deployment).
Include required constraints/indexes. Destructive production data changes require
explicit review and a considered recovery strategy.

## 13. Identity and Patient Rules

Follow [account authentication](docs/architecture/03-domain-and-workflows.md#account-authentication)
and [patient identity](docs/architecture/03-domain-and-workflows.md#patient-identity).

## 14. Health Examination Rules

Follow [Organization/Batch](docs/architecture/03-domain-and-workflows.md#organization-and-batch),
[Batch Participant](docs/architecture/03-domain-and-workflows.md#batch-participant)
and [record history](docs/architecture/03-domain-and-workflows.md#record-history-and-other-contexts).
[Excel roster import is removed](docs/architecture/03-domain-and-workflows.md#removed-roster-import);
historical schema records do not authorize restoring it.

## 15. Encounter and Diagnostic Progress Rules

Derive worklists/progress from owned visit/order/authorization/result records
under [current workflows](docs/architecture/03-domain-and-workflows.md#record-history-and-other-contexts).
No parallel queue-stage state machine.

## 16. Orders and Payment

Preserve payment authorization, multiple OrderRounds, Doctor ordering and
baseline Front Desk collection under
[current workflows](docs/architecture/03-domain-and-workflows.md#record-history-and-other-contexts).

## 17. Clinical and Diagnostic Results

Preserve final versions, permissions, traceability and immutable history under
[current workflows](docs/architecture/03-domain-and-workflows.md#record-history-and-other-contexts).

## 18. Prescription Rules

Preserve [issued versions](docs/architecture/03-domain-and-workflows.md#record-history-and-other-contexts).
Inventory/dispensing requires an explicit new business contract.

## 19. API Contract

Base: `/api/v1`. Resource endpoints and explicit domain-command actions expose
transport DTOs, not persistence records/domain internals.
Map `api/request` → `application/command` or `application/query` in the controller
or request's `toCommand()/toQuery()`. Simple path IDs may stay scalar.
Use cases return `application/response`; controllers add status/envelope only.
Commands without results may return `void`.

### Envelope, errors and pagination

Reuse `shared.web.ApiResponse`, `PageResponse`, `GlobalExceptionHandler` and
`ApiResponseWriter`. Wire examples:

```json
{"result":"OK","code":200,"data":{"items":[],"page":1,"size":10,"totalElements":0,"totalPages":0}}
```

```json
{"result":"NG","code":409,"message":"Record was changed by another request"}
```

`code` matches numeric HTTP status; `result` is OK/NG; null message/data are omitted.
No separate public string error code, field-error list or trace ID exists.
`ConcurrentUpdateException.errorCode()` is internal. New wire fields require an
explicit contract change. Failed business commands never return HTTP 200.

| Category | Current exception / handler | Status |
|---|---|---|
| Structural/malformed input | Bean Validation, malformed JSON/type, IllegalArgumentException | 400 |
| Authentication | ApplicationException.Type.UNAUTHENTICATED | 401 |
| Forbidden | ApplicationException.Type.ACCESS_DENIED, AccessDeniedException | 403 |
| Not found | ResourceNotFoundException | 404 |
| Unsupported method | HttpRequestMethodNotSupportedException | 405 |
| Identity conflict | DuplicateKeyException | 409 |
| Business rule | BusinessRuleException, module DomainException subclasses | 409 |
| Lost update | ConcurrentUpdateException | 409 |
| Rate limit | ApplicationException.Type.RATE_LIMITED | 429 |
| Dependency unavailable | ApplicationException.Type.DEPENDENCY_UNAVAILABLE | 503 |
| Unexpected failure | Central fallback | 500 |

`shared.exception.ApplicationException` covers typed input/auth/access/rate/
dependency failures, not all exceptions. Reuse existing failure types; new domain
failures SHOULD use `<Rule>Exception` in `domain/exception` with the module base.
Keep safe public messages and causes internal. Errors use Cache-Control: no-store;
positive retry delays use Retry-After.

Lists reuse `shared.constants.PaginationConstants`: page starts at 1 (default 1),
size defaults to 10 (max 100), sortBy ASC/DESC (default ASC), sortKey defaults to id.
Allowlist each endpoint's sort/search fields and stable tie-breaker.
`PageResponse`: items, page, size, totalElements, totalPages; empty → zero pages.
HTTP DTOs own bounds/defaults; supported non-HTTP callers use one validated
application contract.

## 20. Application Services / Use Cases

New operations use `application/usecase/<Verb><Concept>UseCase` with one public
`execute`. Published facades delegate to focused use cases. Collaborators belong
outside `usecase`, in an existing application service package as needed.
The formerly misplaced `BatchDraftEditor` is not a naming precedent. Source work
in progress has removed its file while some tests still reference it; see
[code follow-ups](docs/maintenance/code-follow-ups.md) before treating old examples as current.

Use cases load, authorize, invoke domain behavior, persist through ports, coordinate
published contracts and own transactions. No SQL, foreign-table manipulation,
duplicated invariants, HTTP parsing or print-HTML formatting.

### Application comments and Javadoc

New/changed use cases have concise English class Javadoc naming the operation.
Public entry points/published contracts document behavior, every @param, non-void
@return and caller-actionable @throws. Inherit documented interface contracts;
add only implementation-specific guarantees.

Document only enforced authorization, resource scope, atomic writes/audit,
expected-version conflicts, idempotency and external effects. Actor IDs do not
prove permission; return inside a transaction does not prove commit.
Inline comments explain non-obvious ordering/history/rollback reasons and link
the relevant contract. Trivial accessors/fields need comments only for hidden
semantics. Update comments with behavior; TODOs name a concrete missing contract
or tracked follow-up and never disguise fake success.

## 21. Transaction Rules

Atomic application methods use `@Transactional`, never controllers. Keep scope
bounded; slow external calls need an explicit reason to occur inside a DB
transaction. Required audit shares the business transaction. Durable external
effects use outbox/post-commit dispatch.

## 22. Concurrency

Required mutable writes compare expected row_version, increment on success and
detect zero-row updates as conflicts. No silent overwrite of sensitive clinical/
financial changes. Update predicates and public expected-version fields agree.

## 23. Idempotency

Preserve required idempotency for duplicate-sensitive creation, payment callbacks,
inbound integration, print jobs and notification dispatch. Use `idempotency_keys`
where the schema contract supports it; do not infer a public workflow from storage.

## 24. Outbox and External Integration

Business changes and required outbox rows commit together; leased/idempotent
dispatch follows commit. Adapters live in integration or owning infrastructure.
Vendor codes stay behind documented mapping contracts, outside domain logic.

## 25. Security / RBAC

[API/security](docs/architecture/05-api-and-security.md#authentication-and-authorization)
owns current policy and profile behavior. Backend authorization is mandatory;
frontend visibility and actor IDs grant no permission. Use explicit defined
permissions for patient, clinical, billing, corporate, admin and audit access.
Production omits local/test profiles. Mixed-profile handling and the existing
controller actor fallback need code fixes in
[the follow-up list](docs/maintenance/code-follow-ups.md).

## 26. Audit

Use audit::recording and append-only audit_events. Required sensitive mutations
capture account actor, action, resource type/ID, occurredAt and concise safe
metadata, including relevant before/after values. Audit failure rolls back business
writes. No secrets/tokens or unnecessary complete clinical payloads.

## 27. Logging

Controllers/use cases use @Slf4j and parameterized English events with named
fields. Application owns operation events; HTTP adds useful request context.
Domain, DTOs and pure converters have no operational logging. No compulsory
entry/exit log for every method.

| Level | Use |
|---|---|
| DEBUG | Useful reads, validation/conflicts and idempotent replays |
| INFO | Meaningful mutation milestone, summarized once |
| WARN | Recoverable degradation or actionable security signal |
| ERROR | Unexpected failure, once at the handling boundary |

Allowlist only needed internal IDs, existing correlation ID, counts, durations,
versions and safe outcome codes. IDs remain sensitive metadata. Placeholders
do not sanitize. Never serialize request/response/command/query/domain objects,
raw search text, rows, filenames, names, contact details, CCCD, clinical content,
credentials, cookies, tokens or payment secrets.
Reuse validated correlation context; avoid new logging-only domain parameters.

Inside transactions use pending-commit wording, e.g.
`Organization insert executed; commit pending: organizationId={}`.
Committed-success logs require confirmed commit. Logs do not replace audit;
do not add an event framework just for logging.
Propagate/translate preserving causes; avoid repeated catch/log/rethrow.
Expected conflicts need no ERROR stack trace. Raw causes and SQL messages may
contain health data; only established redacted/access-controlled diagnostics may
be logged. Bulk work logs bounded summaries, not per-participant INFO.
Test required redaction/event behavior, not ordinary wording for every use case.

## 28. Secrets and Configuration

Environment/approved secret storage owns real DB/JWT/SMS/payment/integration
credentials. Never commit them or include them in snapshots/logs.
Use existing profile configuration; production omits local/test. See
[deployment](docs/architecture/06-testing-and-operations.md#deployment).

## 29. Validation

Bean Validation owns HTTP structure; domain/application owns business
preconditions. Keep each rule at its owner, with required DB constraints.
Do not duplicate rules across controller, application and aggregate.

## 30. Search

Use PostgreSQL and requirement-defined authorized fields for MVP; no Elasticsearch.
Bound/paginate results and review indexes/query plans before large wildcard scans.

## 31. Performance

Use bounded projections/pagination, justified indexes and supported bulk SQL.
Avoid N+1 loops, full histories for summary screens and giant transactions.
Add caches/search brokers only for measured needs.

## 32. Testing Rules

Domain invariants run without Spring. Application/API tests verify observable
orchestration, validation, access, statuses, envelope/serialization and exposed
idempotency. PostgreSQL 18 Testcontainers prove real MyBatis/Flyway/constraints/
transactions; H2, mocks and schema text tests cannot prove SQL/rollback.
Spring Modulith verifies inventory, published interfaces and layer separation.

Test classes: `<Subject>Test` or `<Subject>IntegrationTest`; new methods SHOULD use
lowerCamelCase behaviors (e.g. rejectsStaleVersionBeforeMutation). Preserve unrelated
test names. Existing checks include ModuleVerificationTest,
PersistenceRecordContractTest, CleanSlateMigrationContractTest,
HealthExaminationApiSurfaceTest and GlobalExceptionHandlerTest.
These prove their assertions, not universal policy compliance.

## 33. Test Data

Test fixtures/builders use synthetic data under test code. Runtime never depends
on fake data. Developer seeds are isolated from production; no real patient data
in automated tests.

## 34. Code Quality

Keep orchestration readable: load/check → domain → persist/audit → response,
with required ordering/transactions intact. Extract real responsibilities;
required repository/external ports remain valid with one implementation.
Generic base CRUD/services/pass-through layers need a concrete benefit.
Use the configured formatter; keep unrelated cleanup/type migrations separate.
Tests assert observable outcomes and failures; mocks do not prove commit/SQL.

Outside domain prefer Lombok @RequiredArgsConstructor for final dependencies and
useful DTO boilerplate. Avoid generated sensitive toString. Domain may use Lombok
only while preserving invariant-enforcing factories/constructors/business methods.

### DTO construction

For new/changed DTOs prefer Lombok @Builder, including fixtures, while preserving
required-value validation, defaults, defensive copies and serialization.
Builders alone do not validate completeness. Required response/read values SHOULD
be checked in constructors/factories. HTTP validation stays at its owner; do not
copy constraints into commands merely to compensate for a builder.
Keep framework-required/invariant constructors/factories. Existing records stay
records; do not convert solely for builders. Migrate only affected call sites.

### Agent workflow

[AGENTS.md](AGENTS.md#source-of-truth-and-workflow) owns lookup and workflow.
Preserve existing worktree changes. Stop only changes dependent on an unresolved
contract; continue independent authorized work. Record lasting rules once and link
elsewhere. Plans/reviews/templates never silently supersede accepted contracts.

### Java types and collections

Use wrappers for nullable values, including nullable DTO/record fields and SQL
columns. Use primitives for local counters/flags and required non-null scalar
values when compatible with the owning transport/persistence/framework contract.
Keep domain value objects and BigDecimal for money. Do not migrate declarations
unrelated to the task.

Required wrapper HTTP fields need @NotNull; numeric bounds alone permit null.
Resolve null before unboxing/arithmetic/order/boolean conditions. Compare wrappers
by equals/Objects.equals, never reference ==/!=. Boolean.TRUE.equals is appropriate
only when null legitimately means false. A type change updates callers, MyBatis,
null/equality behavior and affected record assertions together.

Ordered business collections use List<T>, ArrayList for mutation and List.of/
List.copyOf/Stream.toList for immutable results. Preserve defensive copies and
mutability. Set expresses uniqueness; Map expresses keyed lookup. Arrays remain
appropriate for JDK/framework contracts, annotations, JVM entry point and binary/
crypto/I/O/bytea buffers; confine incidental arrays to adapters.
Use listener/state fields for captured scalars and atomic types only for actual
concurrency.

## 35. ADR Policy

ADRs cover lasting module/stack/auth/transaction/outbox/document-rendering/
storage/integration/database-identity decisions. Routine extraction needs no ADR.

## 36. Definition of Done

Runtime/build changes run the wrapper (verify includes test):

```powershell
.\mvnw.cmd verify
```

```bash
./mvnw verify
```

Run applicable PostgreSQL/Redis integration, module, security and migration
startup checks. Clean stale target output after source/XML removal or movement.
Report failures and unavailable Docker/Redis/skips; never claim unrun checks passed.

Documentation/skill/ignore/line-ending-policy changes without runtime/build edits
verify local links/anchors, skill resources, contract consistency and git diffs.
A line-ending policy change does not authorize repository-wide renormalization.
Completion requires no missing migration/public-contract documentation, broken
module boundary, UI-only invariant or introduced secret/fake production behavior.

## 37. Implementation References

For new endpoints/use cases, read
[nkc-backend-use-case](.agents/skills/nkc-backend-use-case/SKILL.md) and its
[source reference index](.agents/skills/nkc-backend-use-case/references/organization-flow.md).
Current organization code supplies concrete vocabulary/contracts, not blanket
approval. Recheck types/equality, DTOs, validation, Javadoc, logging, authorization
and transaction/audit claims. Never copy a local/test actor fallback into new flows.

## Final Principle

Preserve business correctness, traceability, explicit ownership, PostgreSQL
constraints and auditable history. Class/statement counts are not correctness.
