---
name: nkc-backend-use-case
description: Use when adding or extending an NKC backend REST endpoint or application use case with MyBatis persistence. Not schema-only work or generic CRUD generation.
---

# NKC backend use case

Follow [AGENTS.md](../../../AGENTS.md) and
[PROJECT_RULES](../../../PROJECT_RULES.md). This skill supplies current source
references, not generated templates or additional business contracts.

1. Identify the owning module, supported callers and acceptance criteria from
   accepted ADRs/workflows and [the API inventory](../../../docs/api/clean-slate-migration.md).
   Resolve a missing contract before changing dependent behavior.
2. Inspect the affected flow with CodeGraph when indexed. Choose only required
   roles from [the package tree](../../../PROJECT_RULES.md#5-ddd-layering-per-module).
3. Read [the organization-flow index](references/organization-flow.md), then look
   up only affected source/test roles. Adapt current code against project policy;
   examples do not prove that every rule or required guarantee is already enforced.
4. Map request → command/query at API boundaries and return application responses.
   SQL views stay in infrastructure and map to typed port read contracts. Use one
   public execute per business use case. Validation stays at its input/invariant owner.
5. Use [nullability-appropriate types and value equality](../../../PROJECT_RULES.md#java-types-and-collections).
   Take audit actor identity from authentication; separately enforce permission.
   Preserve production's deny policy until an approved access contract exists.
6. Verify observable domain, orchestration, HTTP and persistence outcomes.
   Mocks/standalone MockMvc do not prove filter-chain access, CSRF, SQL commits or
   business/audit rollback. Add endpoint-specific access/CSRF checks when affected;
   use real PostgreSQL integration evidence for transaction guarantees.
7. Format changed source with the configured formatter, review the diff and follow
   [definition of done](../../../PROJECT_RULES.md#36-definition-of-done).
   Report actual results/skips, migrations/API changes and unresolved contracts.
