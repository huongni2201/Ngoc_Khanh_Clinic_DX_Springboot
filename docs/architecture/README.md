# Current clean-slate architecture

This is the active backend contract for Ngọc Khánh Clinic. It replaces the old
architecture documentation and historical decision-record authority.

Read the selected clean-slate schema and these documents before changing behavior:

1. [Overview and layering](01-overview.md)
2. [Module ownership and public contracts](02-module-contracts.md)
3. [Domain model and workflows](03-domain-and-workflows.md)
4. [PostgreSQL/MyBatis persistence](04-persistence.md)
5. [API, security and audit](05-api-and-security.md)
6. [Testing and operations](06-testing-and-operations.md)

Use `PROJECT_RULES.md` and `PROJECT_SKILLS.md` for implementation discipline.
Physical schema is `src/main/resources/db/migration/V001__create_clean_slate_schema.sql`.
The owner's clean-slate business contract supersedes earlier schema/workflows;
retain Participant terminology and public SQL schema. Keep domain, persistence,
application and HTTP responsibilities separate.

[API migration](../api/clean-slate-migration.md) and
[login operations](../api/login.md) describe supported client/deployment contracts.
No schema representation claims every clinic feature or endpoint is implemented.

The retained clean-slate decisions are
[the module inventory](../adr/0012-clean-slate-module-boundaries.md) and
[the application/database contract](../adr/0013-clean-slate-application-contract.md).
Older ADR files and architecture documents are removed.
