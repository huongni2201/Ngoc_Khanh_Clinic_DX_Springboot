# Ngọc Khánh Clinic Backend — Skill Routing

Follow [AGENTS.md](AGENTS.md) for precedence and lookup. Read only task-relevant
skills. [PROJECT_RULES.md](PROJECT_RULES.md) overrides generic examples/templates.

## Bundled local skills

These links resolve inside this repository; no installation is needed.

| Task | Local skill | Boundary |
|---|---|---|
| Endpoint/use case | [nkc-backend-use-case](.agents/skills/nkc-backend-use-case/SKILL.md) | Current source references; task-required roles only. |
| Focused implementation/refactoring | [karpathy-guidelines](.agents/skills/karpathy-guidelines/SKILL.md) | Preserve required ports and DTO boundaries. |
| Interface/seam design | [codebase-design](.agents/skills/codebase-design/SKILL.md) | Deep modules without generic CRUD scaffolding. |
| Domain/workflow | [domain-modeling](.agents/skills/domain-modeling/SKILL.md) | Accepted workflows and terminology. |
| Layering | [architecture-patterns](.agents/skills/architecture-patterns/SKILL.md) | Current modular monolith and published contracts. |
| Lasting decision | [architecture-decision-records](.agents/skills/architecture-decision-records/SKILL.md) | Routine extraction needs no ADR. |
| REST design | [api-design-principles](.agents/skills/api-design-principles/SKILL.md) | Actual envelope/pagination; adapt concepts to Java, not bundled Python examples. |
| Bug diagnosis | [diagnosing-bugs](.agents/skills/diagnosing-bugs/SKILL.md) | Supported callers and root cause. |
| Explicit test-first work | [tdd](.agents/skills/tdd/SKILL.md) | Observable business/security/concurrency outcomes. |

## Optional session skills

The following names refer to the active session catalog, **not** bundled
`.agents/skills/` directories. Use only when present and relevant:

| Task | Optional catalog skill |
|---|---|
| PostgreSQL schema/query | postgresql-table-design; sql-optimization-patterns |
| Failure mapping | error-handling-patterns |
| Diff/PR review with base/spec | code-review |
| Explicit threat model | security-threat-model |
| Agent instructions/skills | writing-for-agents; skill-creator |
| Formatting changed source | codefmt |

Do not install tools to satisfy this table. If absent, follow the owning policy
and existing configured tooling.

## Stack compatibility

This is Maven: run the wrapper under [definition of done](PROJECT_RULES.md#36-definition-of-done).
Gradle-only run-tests/coverage recipes are inapplicable; do not add a coverage
plugin just to obtain a number. Java security uses
[API/security](docs/architecture/05-api-and-security.md) and access tests;
security-best-practices does not cover Java.
No JPA/CRUD generator, Node/Husky backend default or removal of required ports,
authorization, audit, transactions and tests for simplification.
