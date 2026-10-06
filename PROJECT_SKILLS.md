# Ngọc Khánh Clinic Backend — Project Skills Guide

Use skills only when their workflow fits the current task and repository stack.
Read a selected skill's `SKILL.md` before applying it. Project rules and accepted
ADRs override generic examples; a skill cannot authorize a business-contract change.

## Start with the task

1. Read `PROJECT_RULES.md`, this guide, relevant ADRs and architecture sections.
2. Identify the owning module, supported behavior and verifiable acceptance criteria.
3. Select the smallest relevant set of skills from the table below. This is a
   routing guide, not a checklist requiring every skill on every change.
4. Inspect current code and callers, implement the authorized change, and run the
   checks required by `PROJECT_RULES.md` and the affected architecture contracts.
5. Report actual evidence, skipped/failed checks and unresolved requirements.

When `.codegraph/` exists, use CodeGraph first to locate/understand code. Use
Context7 for current library/API/configuration questions as directed by AGENTS;
ordinary domain reasoning, refactoring and rule review do not need a library lookup.
Existing code and generated examples never override the clean-slate contract.

## Skills by task

| Task | Relevant skills | Project-specific boundary |
|---|---|---|
| Focused implementation/refactoring | `karpathy-guidelines`, `codebase-design` when changing a seam | Keep the smallest cohesive change; preserve explicit ports and required DTO boundaries. |
| Domain or workflow change | `domain-modeling`; `architecture-patterns` when layering changes | Read ADR-0013 and current workflows; retain Participant terminology. |
| Long-lived architecture decision | `architecture-decision-records` | ADRs cover lasting decisions, not routine class/method extraction. |
| REST contract design | `api-design-principles` | Versioned REST; no invented fields, permissions, statuses or GraphQL. |
| PostgreSQL schema/query work | `postgresql-table-design`, `sql-optimization-patterns` as relevant | PostgreSQL 18 + MyBatis, approved schema, Flyway, actual integration tests. |
| Error mapping/integration failure | `error-handling-patterns` | Preserve safe error categories and root causes; no catch-all successful responses. |
| Bug diagnosis | `diagnosing-bugs` | Trace root cause and supported callers before changing behavior. |
| Test-first business change | `tdd` | Cover business outcomes, invariants, authorization and required retry/concurrency behavior. |
| Diff/PR review | `code-review` when base and specification are available | Review standards and specification; do not mislabel a diff review as a whole-repo audit. |
| Explicit threat-model request | `security-threat-model` | Use its actual supported workflow; security-relevant work alone does not invoke a full threat model. |
| Agent-facing rules/docs | `writing-for-agents` when available | Keep one owning policy and links; remove contradictions and stale pointers. |
| Code formatting | `codefmt` when available | Scope to changed files and the configured formatter; do not reformat unrelated work. |

For Java/Spring security review, use the current
[API/security architecture](docs/architecture/05-api-and-security.md), accepted
ADRs, current official documentation when API facts are needed, and authorization
tests. Review patient access, permission checks, CSRF/session behavior, safe errors,
logging and audit according to the actual supported contract.

## Compatibility and availability

Project-local copies under `.agents/skills/` were recorded on 2026-09-24:
`architecture-decision-records`, `architecture-patterns`, `codebase-design`,
`domain-modeling`, `api-design-principles`, `diagnosing-bugs`, `tdd`, and
`karpathy-guidelines`. These are local copies, not a latest-release claim.
`error-handling-patterns` was recorded in the parent workspace's `.agents/skills/`;
check availability when working outside that workspace. Check the active skill
catalog before selecting optional skills; do not install tools just to satisfy
this guide.

| Capability | Restriction / supported alternative |
|---|---|
| `run-tests` | The available skill targets Gradle. Use this repository's Maven wrapper directly; do not apply its Gradle commands. |
| `coverage` | Check the installed skill's build-tool support and project configuration first; do not apply Gradle recipes or add a plugin merely to obtain a percentage. |
| `security-best-practices` | The available skill supports Python, JavaScript/TypeScript and Go, not Java/Spring. Use the security-review route above. |
| `setup-pre-commit` | Its Node/Husky workflow is not the backend default. Use existing Java/Maven checks; hooks are optional work requiring a concrete request. |
| CRUD/JPA/mapper generators | Do not introduce JPA/Hibernate or generic CRUD scaffolding. MyBatis adapters and domain ports follow the approved architecture. |
| Simplification skills | Removing ceremony does not authorize removing repository/external ports, authorization, audit, transactions or tests. A boundary may have one implementation. |
| Frontend/Python/backend platform skills | Do not apply unrelated stacks to this backend. |

## Review focus

For an affected flow, check:

- domain/module boundaries, published cross-module contracts and mapper isolation;
- the owner of validation, authorization, transaction, audit and concurrency rules;
- SQL/schema compatibility, bounded queries and absence of N+1 behavior;
- [application Javadoc](PROJECT_RULES.md#application-comments-and-javadoc) and
  [logging](PROJECT_RULES.md#27-logging), including safe fields and commit wording;
- observable tests, supported callers and public API documentation;
- the focused diff, preserved worktree changes and actual verification evidence.

The [2026-10-04 migration review](docs/reviews/2026-10-04-clean-slate-migration-review.md)
is a dated record of findings and checks, not proof of current implementation
status. Recheck a finding against the current tree before acting on it.

## Verification

Use the Maven wrapper, following the current required checks in
[PROJECT_RULES.md](PROJECT_RULES.md#36-definition-of-done):

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

For removed/moved classes or mapper resources, follow the clean-output guidance
in [testing and operations](docs/architecture/06-testing-and-operations.md).
PostgreSQL/MyBatis integration needs PostgreSQL 18 Testcontainers; relevant
accesscontrol integration also needs Redis. Report skips/environment failures explicitly.
Use existing coverage facilities when requested to identify missing behavioral
coverage; a percentage does not replace critical workflow tests.

## Suggested task prompt

```text
Read AGENTS.md, PROJECT_RULES.md, PROJECT_SKILLS.md, relevant accepted ADRs and
current architecture/contracts for this task. Identify the owning module and
acceptance criteria, inspect existing code/callers, then make the smallest cohesive
change. Preserve Java 25 / Spring Boot 4 / PostgreSQL 18 / MyBatis and module boundaries.
Use wrapper scalar types and List<T>/ArrayList<T> for ordered business collections,
following PROJECT_RULES.md's null-safety and technical exceptions.
Apply application Javadoc and safe logging rules to changed use cases. Preserve
unrelated worktree changes. Do not invent business contracts or add speculative
abstractions/files. Run required checks and report changed files/modules, API and
migration impact, tests actually run, unresolved conflicts and remaining risks.
```
