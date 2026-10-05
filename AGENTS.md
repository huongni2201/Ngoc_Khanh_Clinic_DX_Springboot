# Ngọc Khánh Clinic Backend

Production backend for NKC-DX: a modular monolith with bounded contexts and
ports/adapters. Root package: `com.ngockhanh.clinic`. Retain Participant terminology.

## Non-negotiables

These are the high-priority index to [PROJECT_RULES.md](PROJECT_RULES.md), the
owning technical policy. Follow its linked details for implementation.

1. MUST keep Java 25, Spring Boot 4 / Framework 7, PostgreSQL 18, MyBatis,
   Spring Modulith, Maven and Flyway. No JPA/Hibernate or H2 substitution (§2).
2. MUST respect module ownership and published contracts; no foreign internal
   packages, shared mappers/repositories or undocumented cross-context SQL (§4–8).
3. MUST keep domain free of transport/persistence/framework concerns, application
   free of `api.*`, and controllers free of business rules/SQL/transactions (§5–6).
4. MUST enforce authorization on the backend; actor IDs alone grant no access (§25).
5. MUST keep credentials, CCCD and clinical payloads out of logs/errors (§27–28).
6. MUST preserve clinical/financial history and immutable issued versions
   ([domain workflows](docs/architecture/03-domain-and-workflows.md)).
7. MUST use Flyway for schema changes and preserve applied migrations (§12).
8. MUST detect required expected-version conflicts; business writes and required
   audit share their transaction (§21–22, §26).
9. MUST preserve required idempotency and durable post-commit external effects (§23–24).
10. MUST use documented business fields, states, permissions and payment rules;
    stop dependent work when the needed contract is absent (§1).
11. MUST use nullability-appropriate types, value comparisons and business `List<T>` as
    defined in [Java types and collections](PROJECT_RULES.md#java-types-and-collections).
12. MUST verify applicable behavior and report failures/skips; no fake successful
    runtime implementations (§32–33, §36).

## Source of truth and workflow

For non-trivial work, read in this order:

1. [PROJECT_RULES.md](PROJECT_RULES.md): technical policy and canonical package tree.
2. [PROJECT_SKILLS.md](PROJECT_SKILLS.md): task routing; load only relevant skills.
3. Relevant accepted decisions:
   [ADR-0012](docs/adr/0012-clean-slate-module-boundaries.md) for module ownership;
   [ADR-0013](docs/adr/0013-clean-slate-application-contract.md) for the application contract.
4. Relevant sections of [architecture](docs/architecture/README.md): current
   domain/workflows, persistence, API/security and operations.
5. Current API contract and migrations in `src/main/resources/db/migration/`
   when changing a business or storage contract.

Explicit owner instructions take precedence. Accepted ADRs and owner-selected
contracts override generic technical examples; architecture describes their
current supported behavior. SQL defines physical storage, not permission to
invent a public workflow. Plans, reviews, existing code and skill templates are
supporting evidence. If authoritative sources disagree, identify the exact
conflict and stop the dependent change; continue independent authorized work.

Identify the owning module, invariants, supported callers and acceptance criteria.
Inspect the implementation, make the smallest cohesive change, add/update relevant
tests, and review the diff. Preserve existing worktree changes.

Creating required files within the canonical package/resource/test tree is part
of an authorized task; see [file policy](PROJECT_RULES.md#9-domain-vs-persistence-model).
For new endpoints/use cases, load
[nkc-backend-use-case](.agents/skills/nkc-backend-use-case/SKILL.md).

## Code and documentation lookup

<!-- CODEGRAPH_START -->
When `.codegraph/` exists, MUST use `codegraph_explore` (MCP) or
`codegraph explore "<symbols or question>"` before text search/file reads to
understand or locate indexed code. Name a symbol/file for current source and call
paths. Use raw reads for non-indexed documents or details the tool did not cover.
Without `.codegraph/`, skip CodeGraph; indexing is the owner's decision.
<!-- CODEGRAPH_END -->

<!-- context7 -->
For library/framework/SDK/API/CLI/cloud-service syntax, configuration, migration,
setup or library-specific debugging, MUST fetch current Context7 documentation.
Start with `resolve-library-id` unless the user supplies an exact `/org/project`
ID; select the relevant reputable match, then `query-docs` per concept. Do not
send secrets or patient data. Ordinary refactoring, business-logic debugging,
code review and general programming concepts do not need Context7.
<!-- context7 -->

## Verification and completion

For runtime/build changes, verify includes test (see [definition of done](PROJECT_RULES.md#36-definition-of-done)):

```powershell
.\mvnw.cmd verify
```

```bash
./mvnw verify
```

Run relevant PostgreSQL 18 Testcontainers, module and security checks when affected.
Report unavailable Docker/Redis and integration skips. For documentation/skill/
hygiene-only changes, verify links, contract consistency, skill resources and diffs.
Never claim checks passed without successful execution.

Completion report: what changed; owning modules; files; migrations; public API
changes; tests added/updated; commands/results; assumptions/conflicts; remaining
risks. For guidance-only work, state that runtime modules, schema and APIs are unchanged.
