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

## Follow-up source and documentation review — 2026-10-05

This follow-up reviews the current working tree against ADR-0012/0013 and the
owner's wrapper/list convention. The verification above describes the earlier
review snapshot; it is not evidence that the current working tree builds.
Inspection covered current architecture/rules, scalar/array declarations,
schema-type assertions and the batch/import flow. It is not a claim that every
unsupported clinic workflow has been audited or executed.

### Findings

1. **P1 — Deleted import ports leave the application uncompilable.**
   `application/port/out/ImportFileStorage`, `ParticipantImportAuditWriter`,
   `ParticipantImportTemplateWriter` and `ParticipantSpreadsheetReader` are
   staged deletions with no replacement declarations under `src/main/java`.
   Callers still use these types. For example,
   `FesodParticipantTemplateWriter.java:15` implements the removed fully qualified
   template port, and `FesodParticipantSpreadsheetReader.java:22` implements the
   absent spreadsheet port. Restore the required contracts or finish a coherent
   relocation with all consumers/tests and published boundaries updated.

2. **P2 — Schema type assertions still enforce the former primitive convention.**
   `PersistenceRecordContractTest.java:149`–`152` requires primitive Boolean/integer
   representations for required SQL columns. A wrapper migration would fail those
   exact-type assertions even though SQL types/nullability are unchanged. Migrate
   affected records and assertions together, keeping separate required-value
   checks. This follow-up updates the documentation convention, not those records.

3. **Migration debt — Scalar contracts still use primitives.**
   Examples include `PageResponse.java:7`–`10`,
   `HealthExaminationImportJob.java:27`, response records and repository/version
   parameters. Apply the new convention when changing these declarations, with
   their callers and tests. In particular, the version comparisons in
   `ConfirmParticipantImportUseCase.java:42` and
   `UpdateParticipantImportPreviewUseCase.java:48` must become value comparisons
   if both operands become wrappers; merely changing declarations is unsafe.

4. **Migration debt — Single-element arrays hide listener state.**
   `FesodParticipantSpreadsheetReader.java:59`–`60` uses `int[]` and `boolean[]`
   as mutable scalar holders. Move this state to wrapper fields on the listener
   or its owning state object when refactoring this seam; these values are not
   lists and should not become single-element `ArrayList`s. Actual ordered
   spreadsheet values already use `List<String>` with `ArrayList` storage.

The prior persistence documentation prescribed primitives for required columns;
that conflict with the new owner instruction is corrected in this follow-up.
Binary `byte[]`, annotation arrays and required library signatures remain valid
technical exceptions, not business-list violations.

### Changes and verification

- Updated existing `AGENTS.md`, `PROJECT_RULES.md`, `PROJECT_SKILLS.md`,
  `docs/architecture/04-persistence.md` and this review. Rules require wrappers,
  `List<T>`/mutable `ArrayList<T>`, null safety, value equality and explicit narrow
  technical exceptions. The rule applies to new or changed declarations;
  existing declarations remain documented migration debt.
- No Java/SQL source, public API, migration, dependency or test was changed.
  Reviewed seams belong mainly to `healthexamination`, shared pagination and
  schema-record contracts across modules. Existing staged/unstaged work is retained.
- `mvnw.cmd test`: failed during compilation, 26 compiler errors, before tests.
  The sandbox attempt also reported Maven-cache access failure; the rerun with
  dependency access reproduced the missing-port compilation errors.
- `mvnw.cmd verify`: failed during compilation with the same missing-port errors.
  PostgreSQL/Redis integration and module checks did not execute; this result
  does not establish whether Docker is available.
- `git diff --check` passed for this follow-up's changed documentation files.
  No completion or passing-test claim is made for the current application.
