# Clean-slate application migration

## Authority and scope

The owner explicitly selected the clean-slate design as the new business contract
on 2026-10-04 and requested migration through domain, use cases, API, infrastructure
records and affected ADRs. Earlier contracts are superseded where they conflict.
Keep Participant terminology, the public SQL schema and existing Java module names.

Sources: `src/main/resources/db/migration/V001__create_clean_slate_schema.sql` and
`C:/Users/PC/Downloads/NKC_DX_Clean_Slate_Database_Design_Detailed_No_Reporting_Schema.md`.

Implement existing supported workflows against the new contract. Do not scaffold
unrequested CRUD for every database table. Preserve backend access checks, safe
errors, audit, transactions and PostgreSQL integration verification.

## Tasks and ownership

- [x] Identity: account/staff/role persistence, authentication application/domain
  contracts and API response consistency; retain existing secure session mechanism
  unless the new design specifies a replacement protocol. Update identity tests.
- [x] Organization and batch: new organization fields, batch days, four-state
  lifecycle, reference/negotiated pricing, optimistic versions, request/response
  contracts, MyBatis queries and related domain/application/API tests.
- [x] Roster and import: batch-scoped participants, no standalone organization
  participant, validated-only import staging, selected day configuration,
  deterministic allocation, atomic insert-only confirmation and concurrency.
  Update participant/import endpoints, domain models, persistence and tests.
- [x] Remaining records: map existing infrastructure records to actual clean-slate
  tables and their owning modules; remove obsolete table records, add current table
  representations. Verify schema contracts and fresh PostgreSQL migration tests.
- [x] Documentation: record supersession in affected ADRs and current architecture,
  API and source-of-truth docs. Document migration as fresh-database-only.
- [x] Verification: format changed files, run focused checks, `mvnw.cmd test` and
  `mvnw.cmd verify`; review boundaries, SQL names, audit and concurrency.

## Coordination

Identity owns identity source/tests/XML. Organization/batch owns organization and
batch code/tests and catalog/document lookup adapters. Roster/import owns other
healthexamination source/tests/XML. Coordinator owns remaining modules' records,
schema contract tests, documentation and final verification. Shared interfaces
are communicated before edits. No commits, resets or pushes; preserve existing
workspace changes. Work in the existing `feature/migrate-db` checkout because the
uncommitted clean-slate work is the input to this task.

## Final verification and review

All planned tasks completed. Full test and verify runs passed 183 tests with no
failures, errors or skips. PostgreSQL 18 and Redis tests actually executed.
Standards/Spec review findings were fixed and scoped re-reviews reported no
remaining findings in their reviewed seams. Review regressions were reproduced
before correction, including day-set ordering and adapter boundaries.

The owner subsequently requested deletion of old ADRs/architecture documents and
confirmed retention of only clean-slate ADR-0012/0013. The new six-document
architecture set and source pointers replace the old files.
