# Clean-slate migration and architecture review — 2026-10-04

## Delivered scope

The owner selected the new clean-slate business contract, retained Participant
terminology/public schema, and requested removal of old ADR/architecture documents.
Existing account, organization, batch and roster/import paths now follow that
contract through domain, application, API, MyBatis and records.

All 64 database tables have infrastructure records in the owning modules. This
does not claim every clinic workflow has been implemented. The existing clean-slate
V001 and module/audit moves in the workspace were inputs to this continuation.

## Review results and corrections

Independent Standards and Spec reviews were followed by scoped re-review.
The reviewed findings were corrected; neither scoped re-review reported an
unresolved finding in the corrected seams.

| Finding | Final correction |
|---|---|
| Batch guard referenced a removed service table | Uses health_examination_participant_services and its actual keys. |
| Import/participant writes did not advance versions | Compare-and-set statements increment row_version; stale writes fail. |
| Reconciliation saved header without item rows | Save retained/new service rows with scoped versions in the application transaction. |
| Replacement rows could alter historical identity/price/request links | Domain preserves original identity, price and established links; unchecked rows remain. |
| Batch query reached integration staging tables | Application uses published ImportStore reference query; integration owns SQL. |
| Money still allowed the former precision | Domain accepts numeric(14,2), with overflow/scale regression coverage. |
| Confirmation JSON leaked into application/domain | Typed confirmation result and audit snapshots; repository adapters encode/decode JSON. |
| Reordering the same selected days regenerated assignments | Set comparison preserves reviewed assignments; regression reproduced before correction. |
| Template download referenced Fesod infrastructure directly | Application writer port, implemented by the Fesod adapter. |
| Removed DELETE batch route returned 500 | Central handler returns 405 and the supported Allow header. |

Aggregate inventory tests now distinguish aggregate roots from nested value types.
ArchUnit additionally checks use-case serialization and application/infrastructure
separation. Read/mutation logs use identifiers/counts without roster payloads.

## Modules and files

Behavioral changes primarily affect identity, healthexamination, integration,
catalog/document lookups, audit contracts and shared HTTP errors. Other business
contexts receive records matching their new owned tables: patient, encounter,
clinical, diagnostics, billing, prescription, appointment, portal and notification.

The [complete changed-file manifest](2026-10-04-clean-slate-file-manifest.md) records
added, modified and removed workspace files.

Main additions/changes include batch-day and participant-service records, account
records, generic import store/records/mappers, typed confirmation/audit contracts,
the template writer port, domain aggregates, use cases, request/response classes,
controllers, XML mappers, provisioning SQL and corresponding tests. Obsolete
table records/adapters/tests are removed or replaced by new-contract coverage.

The architecture index and six current documents replace the old ten-file set.
Fourteen historical ADR files are removed; only clean-slate ADR-0012 and ADR-0013
remain. AGENTS, project rules/skills, CONTEXT, API and operation links use the new
documents. See [current architecture](../architecture/README.md).

## Database and public API

No additional Flyway migration is introduced: Java/SQL mapping targets the supplied
`V001__create_clean_slate_schema.sql`. This is fresh-database-only; environments
with former V001–V003 history require a separately reviewed conversion procedure.

Breaking HTTP fields/routes include account session terminology, organization
code/type/contact channels, explicit batch dates/days, negotiated/reference prices,
participant import paths, selected-day preview configuration and expected versions.
Invalid uploads persist no staging; confirmation is atomic, insert-only and replayable.
The unsupported batch deletion/template association is removed.
See [API migration](../api/clean-slate-migration.md) and [login operations](../api/login.md).

## Verification

- Spotless applied to changed Java sources; unchanged source bytes were preserved.
- `mvnw.cmd test`: 183 tests, zero failures/errors/skips, including real PostgreSQL 18 and Redis.
- `mvnw.cmd verify`: BUILD SUCCESS; 183 tests with zero failures/errors/skips, followed by successful Spring Boot packaging.
- Schema record and runtime SQL contracts, migration tests, module/ArchUnit, domain,
  application, API, security, rollback/concurrency and import replay checks are included.
- Review regressions were observed failing before their fixes, then passed in focused runs.

## Remaining scope and risks

Clients must migrate to the breaking API contract. Deployed database conversion is
outside this fresh-baseline work. Runtime business RBAC remains the existing documented
policy; record presence does not enable unsupported clinic features.

Organization CRUD's absence of a dedicated actor/business-audit flow predates the
review baseline and was not classified as an introduced migration defect. Do not
claim complete organization mutation traceability until that separate contract is
implemented. No new requirement conflict was left unresolved in the migrated paths.
