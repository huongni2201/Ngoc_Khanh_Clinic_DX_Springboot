# Ngọc Khánh Clinic Backend — Project Skills Guide

This backend should keep a focused skill set aligned with:

```text
Java 25
Spring Boot 4
PostgreSQL 17
MyBatis
DDD
Modular Monolith
```

Repository rules and accepted ADRs override generic skill examples.

Before using a skill, read its `SKILL.md`.

## Installed skills and compatibility (2026-09-24)

Project-local copies are installed under `.agents/skills/`: `architecture-decision-records`,
`architecture-patterns`, `codebase-design`, `domain-modeling`, `api-design-principles`,
`diagnosing-bugs`, `tdd`, and `karpathy-guidelines`. They were copied from the existing local
Codex installation, including supporting files; no upstream version or latest-release claim is
made.
`error-handling-patterns` already exists in the parent workspace's `.agents/skills/`.
That inherited skill must be installed separately when using this backend outside that workspace.

The sections below describe desired capabilities, not a guarantee that every named
skill supports this stack. Apply these compatibility restrictions:

- The currently available `run-tests` skill targets Gradle. Run this project's Maven
  wrapper directly (`.\mvnw.cmd test` and `.\mvnw.cmd verify` on Windows); do not use
  that skill to select build commands for this repository.
- The currently available `security-best-practices` skill supports Python,
  JavaScript/TypeScript, and Go, not Java/Spring. It is not the Spring Security
  review implementation advertised below; use project security requirements and
  current official documentation for that work.
- Use `postgresql-table-design` for this PostgreSQL backend. Generic SQL skill examples still require dialect review before use.
- `setup-pre-commit` is not installed here; do not add its Node/Husky toolchain
  solely to implement backend checks.
- Generic architecture/API examples do not authorize Python, GraphQL,
  microservices, or additional frameworks in this project.
- `code-review` is a diff review workflow requiring a base and a specification;
  it is not an unconditional whole-repository architecture audit.

See [the architecture review](docs/reviews/2026-09-22-backend-review.md) for the
current implementation gaps and verification results.

---

## 1. Recommended Skills

Recommended local/backend skills:

```text
architecture-decision-records
architecture-patterns
code-review
codebase-design
diagnosing-bugs
karpathy-guidelines
domain-modeling
error-handling-patterns
security-best-practices
security-threat-model
sql-optimization-patterns
tdd
run-tests
setup-pre-commit
```

If available and appropriate:

```text
api-design-principles
coverage
postgresql-table-design
```

Apply PostgreSQL-specific DDL guidance when consistent with the clinic's documented business schema; SQL must remain PostgreSQL 17 compatible.

---

## 2. Domain / Architecture

### `domain-modeling`

Use for:

```text
aggregate boundaries
entities/value objects
invariants
domain services
domain events
bounded contexts
ubiquitous language
```

Business source-of-truth documents override generic DDD examples.

### `architecture-patterns`

Use for:

```text
layer boundaries
ports/adapters
modular monolith structure
dependency direction
event-driven decoupling
outbox pattern
```

Do not use this skill as justification to split the system into microservices.

### `codebase-design`

Use for:

```text
package structure
module ownership
public/internal contracts
refactoring boundaries
dependency cleanup
```

### `architecture-decision-records`

Use only for decisions with long-term architectural impact.

---

## 3. API

### `api-design-principles`

Use for:

```text
REST resources
HTTP semantics
pagination
idempotency
error contracts
API versioning
```

Do not invent backend fields or business actions not present in requirements.

---

## 4. SQL / MyBatis

### `sql-optimization-patterns`

Use for:

```text
index review
query plans
pagination
N+1 detection
batch operations
join/query optimization
```

All generated SQL must be reviewed for PostgreSQL 17 compatibility.

Project persistence is MyBatis, not JPA.

No skill may introduce Hibernate/JPA unless the project stack is intentionally changed through ADR.

---

## 5. Error Handling

### `error-handling-patterns`

Use for:

```text
domain errors
application errors
API exception mapping
integration failures
retry-safe behavior
concurrency conflicts
```

Do not convert all exceptions into generic HTTP 500 or HTTP 200 wrappers.

---

## 6. Security

### `security-best-practices`

Use for:

```text
Spring Security
RBAC/permissions
secret handling
sensitive logging
input validation
API authorization
healthcare-data protection
```

### `security-threat-model`

Use when adding:

```text
authentication
patient portal
file upload/download
payment callbacks
SMS/integration callbacks
admin functions
external integrations
```

---

## 7. Testing

### `tdd`

Use for business rules with clear deterministic behavior, especially:

```text
`identification_number` uniqueness (CCCD)
adult health-check eligibility
employee import validation
doctor-only service selection
batch-service subset rule
payment gate
ServiceRequest lifecycle
result finalization
idempotency
```

### `run-tests`

Use before completion to run actual configured Maven tests.

### `coverage`

Use to find meaningful missing coverage, not to chase a percentage mechanically.

---

## 8. Debugging / Review

### `diagnosing-bugs`

Use when:

```text
MyBatis mapping fails
transaction behavior is unexpected
PostgreSQL constraint errors occur
Spring context fails
module boundary tests fail
workflow behavior differs from requirements
```

Find root cause before patching symptoms.

### `karpathy-guidelines`

Use when writing, reviewing, or refactoring code to keep changes focused, avoid speculative
abstractions, surface assumptions, and define verifiable completion criteria. Project rules
and accepted ADRs remain authoritative.

### `code-review`

Use after meaningful feature/refactor work.

Review specifically for:

```text
DDD boundary leaks
controller business logic
mapper leakage
cross-module persistence access
transaction scope
SQL injection risks
missing indexes
sensitive logging
missing audit
test gaps
```

---

## 9. Pre-commit

### `setup-pre-commit`

Use for lightweight repository checks.

Adapt examples to Maven/Java.

Do not introduce Node/pnpm tooling into the backend repository just for pre-commit hooks unless already justified.

---

## 10. Skills by Task

### New Domain Module

```text
domain-modeling
architecture-patterns
codebase-design
tdd
code-review
```

### New REST API

```text
api-design-principles
error-handling-patterns
security-best-practices
tdd
```

### New MyBatis Repository / Query

```text
sql-optimization-patterns
tdd
code-review
```

### Health Check Workflow

```text
domain-modeling
architecture-patterns
tdd
security-best-practices
code-review
```

### Payment / External Callback

```text
security-threat-model
security-best-practices
error-handling-patterns
architecture-patterns
tdd
```

### Bug Fix

```text
diagnosing-bugs
tdd
code-review
```

### Architecture Change

```text
architecture-decision-records
architecture-patterns
codebase-design
code-review
```

---

## 11. Skills Not Appropriate by Default

Do not use frontend-specific skills for backend implementation:

```text
frontend-design
ui-styling
ui-ux-pro-max
tailwind-design-system
react-state-management
javascript-testing-patterns
web-design-guidelines
```

Do not use Python backend/async skills.

Do not use JPA/MapStruct CRUD generators as the default architecture.

---

## 12. Suggested Agent Prompt

```text
Read AGENTS.md, PROJECT_RULES.md, PROJECT_SKILLS.md, relevant ADRs, and the current requirement/use-case/table-design documents first.

Use Java 25 + Spring Boot 4 + PostgreSQL 17 + MyBatis.
Keep DDD modular-monolith boundaries.
Do not use JPA/Hibernate.
Domain models must not be MyBatis persistence records.
Do not access another module's mapper/repository implementation.
Do not invent business rules, statuses, tables, permissions, or API fields.
Use Flyway for every schema change.
Use PostgreSQL 17 Testcontainers for persistence integration tests.
Run ./mvnw test and ./mvnw verify before claiming completion.
Report changed modules, migrations, public API changes, tests run, assumptions, and remaining risks.
```
