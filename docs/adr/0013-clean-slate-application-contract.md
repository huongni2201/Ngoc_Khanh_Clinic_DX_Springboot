# ADR-0013: Adopt the clean-slate database and application contract

## Status

Accepted — 2026-10-04. The owner explicitly selected the clean-slate design as
the new business contract and requested migration of domain, use cases, APIs,
infrastructure records and affected ADRs.

## Context

The fresh-database migration already defines the new schema. Existing Java and
MyBatis code still targets the former table-design-v2.11 baseline. Renaming tables
alone cannot adapt workflows whose identity, lifecycle, pricing or ownership changed.

The authoritative inputs are the owner-supplied
`NKC_DX_Clean_Slate_Database_Design_Detailed_No_Reporting_Schema.md` and
`nkc_dx_clean_slate_postgresql18.sql`, represented in this repository by
`src/main/resources/db/migration/V001__create_clean_slate_schema.sql`.
The owner retains **Participant** terminology and one `public` SQL schema.
Earlier FINAL documents and ADRs remain historical sources; the clean-slate
contract supersedes their conflicting business/schema decisions.

## Decision

### Persistence and concurrency

- Use the existing clean-slate V001 for a **fresh database only**. Never apply it
  to an environment with the former V001–V003 migration history. Deployed data
  conversion requires a separate approved migration procedure.
- Each table has one infrastructure record in its owning module, with SQL column
  names mapped to Java camelCase. Composite-key tables keep their actual keys;
  do not invent an `id` or preserve records for removed tables.
- PostgreSQL UUIDv7 defaults are supported. Application-generated UUIDv7 remains
  valid when IDs must exist before inserts. Keep `Instant`/`timestamptz(3)`.
- Money follows the new `numeric(14,2)` contract. Mutable writes compare
  `row_version` and increment it in application SQL. There is no version trigger.
  A zero-row versioned update raises a concurrency conflict.

### Access control

- Access control (`accesscontrol`, formerly `identity`) owns `accounts`,
  `staff_members`, `account_roles`, `roles`, `permissions` and
  `role_permissions`. Credentials use `password_hash`.
- Role grants are account/role pairs with `granted_by` and `granted_at`, without
  fabricated assignment IDs, validity windows or department/batch scopes.
- The session protocol is decided in
  [ADR-0014](0014-session-cookie-redis-login.md). A refresh-token storage column
  does not specify a new public authentication protocol. No refresh-token
  endpoint is inferred from it.
- Login and session views identify the account and its STAFF/PATIENT owner.
  Account identifiers referenced by audit and other modules are account IDs.

### Health examination

- Organization has its own code, type, general contact channels and one named
  contact. The old organization-code removal and minimal contact shape are replaced.
- Batch owns at least one `HealthExaminationBatchDay`; displayed date bounds are
  derived from those dates. Site types are `CLINIC` and `ORGANIZATION_SITE`.
  Lifecycle is `DRAFT`, `READY`, `FINALIZED`, `CLOSED`. The old `DELETED`,
  `IN_PROGRESS`, `RESULT_PROCESSING` and `CANCELED` batch states do not apply.
- Batch services preserve reference price at addition and negotiated price.
  Previously recorded performed-service price snapshots do not change merely
  because the current batch price changes. Retroactive repricing requires its
  own authorized, audited use case.
- Roster members are batch participants, with identity and employment fields
  stored on the batch row. There is no separate organization Participant aggregate
  or table. Optional participant codes are not generated business employee codes.
- Import or manual roster addition never creates Patient/Encounter records.
  Authorized visit preparation links/creates Patient only by exact CCCD.
- Roster, attendance and service reconciliation have independent state. The
  participant's scheduled day belongs to the same batch. Moving a planned day
  does not rewrite attendance, issued snapshots or prepared visit links.
- Participant services record staff reconciliation of actual performed items,
  within batch service scope. They are independent of Doctor order selection.
  Retain rows when deselecting performed items and retain historical prices.
- Health-examination records use the persisted `mrn` as the shared record code.
  Administrative snapshots and clinical record versions have separate typed,
  versioned tables; issued content remains immutable. Template versions belong
  to issued representations, rather than a mutable batch configuration.

### Import ownership and behavior

Implementation update — 2026-10-05: the owner requested removal of the backend
Excel roster import workflow. The import-specific runtime contracts and adapters
below have been retired; the schema and historical data remain unchanged. See
[the current API removal contract](../api/clean-slate-migration.md#removed-excel-roster-import).
The original design decision below remains a historical record.


- Integration owns generic import staging, exposed through a published contract;
  healthexamination owns roster interpretation and business validation.
- Invalid files create neither an import job nor staging rows. Validate the whole
  request before persisting a `VALIDATED` job. Collect row errors; structural
  errors fail fast. Infrastructure/parser failures remain exceptions.
- Capture explicit selected batch-day IDs in job configuration. Allocate new
  participants by lowest active count, then examination date, then day ID.
  Confirmation uses approved staging assignments and never reallocates them.
- Duplicate CCCD within a file or the batch rejects the import. Confirmation is
  atomic and insert-only; it no longer updates existing organization participants.
  Retry of a confirmed job returns its persisted confirmation result.
- Changes and confirmation compare expected job/participant versions and audit
  in the same transaction. Backend access checks remain authoritative.

### Other ownership

- Clinical owns vital signs, assessment/version and diagnosis records; diagnostics
  owns result series/versions/items. Billing owns invoice lines, refunds and
  service authorizations. Prescription uses separate versions and version items.
- Catalog owns specialty, medicine and lab-test/analyte definitions. Document
  owns files, templates/versions, service mappings and issued representations;
  obsolete generated-document/specimen/file-attachment records are removed.
- Portal owns exact-version result/document releases independently of delivery.
  Integration owns connections, submissions, idempotency and outbox. Notification
  owns batches, notifications and attempts. Audit uses append-only `audit_events`.

## Retired documentation

Historical ADRs and the earlier ten-file architecture set have been removed at
owner request. Current contracts are expressed directly in
[the clean-slate architecture](../architecture/README.md), this decision and
[the module decision](0012-clean-slate-module-boundaries.md). Readers do not need
retired documents to determine active types, workflows, security or deployment.

## Consequences

Request/response fields and import semantics change. Clients must use new
organization fields, batch dates/days, selected-day import configuration and
expected versions. Unsupported old states or columns cannot be silently aliased.
Existing local seed data and tests must use the full clean-slate migration.

Schema records do not mean every module has a complete HTTP workflow. This change
migrates existing supported workflows; new business endpoints require their own
use-case contract. Preserve authorization, audit and safe error handling throughout.

## References

- [Module ownership](0012-clean-slate-module-boundaries.md)
- [Implementation plan](../plans/2026-10-04-clean-slate-application-migration.md)
- [Persistence architecture](../architecture/04-persistence.md)
