# ADR-0013: Adopt the clean-slate database and application contract

## Status

Accepted — 2026-10-04. The owner explicitly selected the clean-slate design as
the new business contract and requested migration of domain, use cases, APIs,
infrastructure records and affected ADRs.

## Context

The fresh-database migration defines the physical schema. Domain, application,
API and MyBatis contracts must also reflect its identity, lifecycle, pricing and
ownership decisions; renaming tables alone cannot adapt those workflows.

The authoritative inputs are the owner-supplied
`NKC_DX_Clean_Slate_Database_Design_Detailed_No_Reporting_Schema.md` and
`nkc_dx_clean_slate_postgresql18.sql`. These owner-supplied source files are not
bundled here; their accepted baseline and amendments are represented by this ADR,
the architecture docs and [V001](../../src/main/resources/db/migration/V001__create_schema.sql).
The owner retains **Participant** terminology and one `public` SQL schema.
This clean-slate contract is the accepted business/schema baseline.

## Decision

### Persistence and concurrency

- Owner amendment — 2026-10-08: keep one final schema initializer (V001),
  access-control inserts (V002) and catalog inserts (V003) for a **fresh database only**.
  Fold the former V004–V007 changes into the initializer and catalog seed.
  Never apply these rewritten files
  to an environment with an earlier migration history. Deployed data
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

- Organization uses an optional unique tax code, general contact channels and
  one named contact, as amended by
  [Organization tax-code identity](0016-organization-tax-code-identity.md).
  General `phone` is optional; email, address and named contact fields remain required.
- Batch owns at least one `HealthExaminationBatchDay`; displayed date bounds are
  derived from those dates. Site types are `CLINIC` and `ORGANIZATION_SITE`.
  Lifecycle is `DRAFT`, `READY`, `FINALIZED`, `CLOSED`.
- Batch services preserve reference price at addition and negotiated price.
  Previously recorded performed-service price snapshots do not change merely
  because the current batch price changes. Retroactive repricing requires its
  own authorized, audited use case.
- Roster members are batch participants, with identity, personal and employment fields
  stored on the batch Participant row. There is no separate organization Participant
  aggregate or table. The system generates the Participant code on creation and
  retains it on update; it is not a client-supplied employee code.
- Roster addition never creates Patient/Encounter records.
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

### Roster import scope

Current accepted scope includes the 2026-10-06 single-step import amendment and
the 2026-10-07 manual-change amendment:

- [Participant import and list](../api/participant-import-and-list.md): template
  download, add-only/all-or-nothing import and list. Integration owns the VALIDATED
  then CONFIRMED import job, rows, idempotency receipt and Participant provenance.
- [Manual add, edit, cancel and reactivate](../api/participant-manual-crud.md):
  endpoint-specific `PARTICIPANT_*` permissions; writes require a DRAFT/READY batch
  of an ACTIVE organization. Cancellation preserves the row and reserves its CCCD;
  reactivation uses the same row. CCCD changes require no linked Patient.

The 2026-10-05 removal of the earlier multi-step workflow remains effective for
upload/preview/confirm/cancel endpoints. It does not prohibit the later accepted
single-step import or its template/staging adapters. Historical import provenance
is preserved. See [API compatibility](../api/clean-slate-migration.md#removed-excel-roster-import).

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

## Consequences

Clients use the current organization fields, batch dates/days and expected
versions. Preview/confirm/cancel import routes have no handlers; the single-step
routes of the later amendment exist. Unsupported old states or columns cannot be
silently aliased.
Existing local seed data and tests must use the full clean-slate migration.

Schema records do not mean every module has a complete HTTP workflow. This change
migrates existing supported workflows; new business endpoints require their own
use-case contract. Preserve authorization, audit and safe error handling throughout.

## References

- [Module ownership](0012-clean-slate-module-boundaries.md)
- [Current HTTP inventory](../api/clean-slate-migration.md)
- [Persistence architecture](../architecture/04-persistence.md)
