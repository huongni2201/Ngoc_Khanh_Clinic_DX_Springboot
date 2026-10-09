# Current backend architecture

These documents describe the accepted backend contract and its implementation
limits. Start with [the documentation index](../README.md) for task-specific links.

Follow [source precedence](../../AGENTS.md#source-of-truth-and-workflow): technical
policy, task-relevant skills, accepted ADRs, then the relevant architecture/API
contract. Schema defines storage; it does not authorize a new workflow.

1. [Overview and layering](01-overview.md)
2. [Module ownership and public contracts](02-module-contracts.md)
3. [Domain model and workflows](03-domain-and-workflows.md)
4. [PostgreSQL/MyBatis persistence](04-persistence.md)
5. [API, security and audit](05-api-and-security.md)
6. [Testing and operations](06-testing-and-operations.md)
7. [Open implementation and decision items](07-open-items.md)

Use [PROJECT_RULES](../../PROJECT_RULES.md) and [PROJECT_SKILLS](../../PROJECT_SKILLS.md)
for implementation discipline. Physical schema is
[V001](../../src/main/resources/db/migration/V001__create_schema.sql).
V002 seeds access control; V003 seeds the catalog. See
[persistence](04-persistence.md#current-baseline) for the final migration layout.
The owner's clean-slate business contract supersedes earlier schema/workflows;
retain Participant terminology and public SQL schema. Keep domain, persistence,
application and HTTP responsibilities separate.

[API migration](../api/clean-slate-migration.md) describes supported client/deployment
contracts; [login operations](../api/login.md) describe the session login API.
No schema representation claims every clinic feature or endpoint is implemented.

The retained clean-slate decisions are
[the module inventory](../adr/0012-clean-slate-module-boundaries.md),
[the application/database contract](../adr/0013-clean-slate-application-contract.md),
[Organization tax-code identity](../adr/0016-organization-tax-code-identity.md),
[the session login protocol](../adr/0014-session-cookie-redis-login.md) and
[per-endpoint permissions](../adr/0015-endpoint-permission-rbac.md).
The [ADR index](../adr/README.md) distinguishes these decisions by a unique ID.

Superseded plans and design notes are removed. Their implemented contracts live
in architecture/API docs; unfinished authorization and proposed document issuance
are summarized in Open items. The rendering/storage proposal is not an accepted
amendment to the current schema or official-document workflow.
