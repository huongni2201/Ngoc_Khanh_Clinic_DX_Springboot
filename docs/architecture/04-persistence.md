# PostgreSQL and MyBatis persistence

## Current baseline

PostgreSQL 18, MyBatis and Flyway remain the persistence stack. The owner-selected
clean-slate contract follows [the current domain workflows](03-domain-and-workflows.md).
Fresh databases apply `../../src/main/resources/db/migration/V001__create_schema.sql`
in one public application schema. V001 creates the final schema with 65 business
tables, including batch `deleted_at`, the service reconciliation import type,
the six optional Participant personal-detail columns, nullable Organization
`phone`, the template-version lifecycle and the bulk-print render-job tables. An
account holds exactly one role (`accounts.role_id`, required); the account-role trigger
allows `PATIENT` only on patient accounts and requires a STAFF account for every other role.

Fresh databases apply one schema initializer, two data-only seeds and the additive V004. The
owner-requested consolidations of 2026-10-08, 2026-10-11 and 2026-10-13 folded every former
amendment (V004–V009, and the removal of `account_roles`) into them:

| Migration | Responsibility |
|---|---|
| `V001__create_schema.sql` | Final tables, constraints, indexes, functions and triggers: the batch table has no `examination_site_name` (a batch site is its type and address only; two CHECK constraints require a non-space batch name and a non-blank organization-site address), template versions have the DRAFT → PUBLISHED \| DISCARDED lifecycle with page layout and protection triggers, and `document_render_jobs` / `document_render_job_outputs` hold bulk printing |
| `V002__seed_roles_and_permissions.sql` | Roles, permissions with their endpoint bindings, and role grants, including the print, bulk-print and batch-start permissions |
| `V003__seed_paraclinical_service_catalog.sql` | Departments, rooms and paraclinical services with prices |
| `V004__staff_member_management_permissions.sql` | Additive, after the baseline: `STAFF_MEMBER_VIEW`, `ROLE_VIEW` and the `USER_ACCOUNT_CREATE` endpoint for [staff members](../api/staff-members.md) |

The catalog inserts final service codes such as `CLS58718` directly, with the
HIS id and no separator. Seeded services start at `row_version = 0`. The
two deferred foreign-key declarations in V001 only resolve tables created later
in the same initializer.
The batch site-name removal and the batch CHECK constraints are part of V001. Without `examination_site_name`, normal SQL no longer reads or writes stored
site names. A conversion that drops that column from an existing database may leave the
old bytes until rows are rewritten; this is not a secure-erasure procedure. Folding the changes
converts no existing history; a database that applied one still needs the reviewed procedure below.

The batch CHECK constraints rewrite no rows. Clinic batches keep an empty address.
Before a reviewed conversion applies them to a database that may hold older rows, count
the rows they would reject and correct them first; the conversion fails while any remain:

```sql
SELECT count(*) FROM public.health_examination_batches
 WHERE name !~ '\S'
    OR (examination_site_type = 'ORGANIZATION_SITE' AND examination_site_address !~ '\S');
```
Local demo data stays in `db/local`.

An environment with any earlier migration history must use a separately reviewed
conversion procedure. Do not replace applied checksums or apply this V001 on top
of the old schema. This change includes no deployed-data conversion.

## Records and ownership

Each table has one Java record in its owner's `infrastructure/persistence/record`.
The record follows the actual table columns and their order, including composite
keys, nullable lifecycle values and row versions. SQL projections/join results
belong in `infrastructure/persistence/view`, mapped by adapters to typed port read
contracts; see [model placement](../../PROJECT_RULES.md#9-domain-vs-persistence-model).
Domain objects and API responses never expose persistence records or views.

- Access control: accounts, staff members, account-role grants, roles and permissions.
- Catalog: departments, rooms, specialties, services, medicines and lab definitions.
- Patient: patient master, allergies and conditions.
- Encounter: encounters and assignment fields stored on each encounter.
- Clinical: vital signs, assessments/versions, diagnoses, order rounds and requests.
- Diagnostics: result series, result versions and lab result items.
- Billing: invoices, invoice lines, payments, refunds and service authorizations.
- Prescription: logical prescriptions, versions and version items.
- Health examination: organizations, batches/days/services/participants,
  reconciled participant services, records, administrative snapshots and versions.
- Document: files, templates/versions, service mappings and issued representations.
- Integration: generic import staging, connections, mappings, submissions,
  idempotency keys and durable outbox.
- Portal: result/document releases. Notification: batches, messages and attempts.
- Appointment and audit own appointments and append-only audit events respectively.

Removed tables have no compatibility record. Schema-contract tests compare all
current records with the baseline and reject surplus or incorrectly owned files.

## Types

UUID primary and relationship keys use `UUID`. PostgreSQL 18 `uuidv7()` defaults
support database allocation; the existing application UUIDv7 generator is used
when a use case needs IDs before insertion. Composite tables retain composite keys.

System timestamps use `timestamptz(3)` and Java `Instant`; calendar days use `date`
and `LocalDate`. The existing MyBatis Instant handler binds UTC values. Money uses
`numeric(14,2)` and `BigDecimal`. Nullable scalar fields use wrappers (`Long`,
`Integer`, `Short`, `Boolean`). Required non-null scalar fields and local
counters/flags use primitives when compatible with their owning contract.
SQL nullability remains authoritative: a wrapper does not make a `NOT NULL`
field optional. Preserve required-value validation and null-safe value comparisons;
do not change unrelated declarations. Any type change updates callers, MyBatis
mapping and exact-type assertions together; see
[Java types and collections](../../PROJECT_RULES.md#java-types-and-collections).
PostgreSQL `bytea` remains `byte[]` as a binary contract. JSONB is confined to storage
contracts and serialized with typed application/domain data, with explicit casts
in MyBatis writes. CCCD stays text and exact matching remains authoritative.

## SQL and concurrency

Use explicit columns and bound `#{...}` values. Complex statements live in XML.
Converters preserve lifecycle fields and versions and never execute SQL themselves.
Application use cases own transactions, audit and supported cross-module contracts.

Mutable updates use `WHERE id = #{id} AND row_version = #{expectedVersion}` and
`SET row_version = row_version + 1`; zero affected rows is a concurrency conflict.
The clean-slate schema has no row-version increment trigger. A database lock does not replace client expected-version checks. Serialize concurrent schedule/roster mutations on
the same batch consistency boundary.

## Historical state

Batch services retain reference and negotiated prices. Performed participant
services retain their recorded price; normal batch price changes do not reprice
historical rows. Explicit authorized retroactive repricing is a separate use case.

Administrative record snapshots and clinical versions are separate, typed rows.
Issued/final content and issued representations are immutable, protected by the
baseline triggers. Portal release rows reference exact versions and remain
independent of notification delivery. Render-on-demand uses source and template
versions; the schema contains no generated-document or printer tables.

Render jobs (V006): `document_render_jobs` (status, lease, attempts, source ids, skipped list,
`expires_at`; unique `(requested_by, idempotency_key)`) and `document_render_job_outputs` (one `files`
row per merged part, `LOCAL` provider). V006 is additive and also seeds three endpoint permissions.

Template versions (V004) move DRAFT → PUBLISHED | DISCARDED. `files.storage_provider` is
CLASSPATH, LOCAL or S3; only CLASSPATH and LOCAL have adapters. Each version stores its
`contract_version`, page size read from the docx and `pages_per_record`. There is one default
template per `template_type` (partial unique index). A trigger forbids changing a version's
file or layout, and any edit of a published version except scheduling its retirement or
cancelling it before it takes effect; published periods of one template never overlap; a
file referenced by a version cannot change or be deleted. The system-seeded starter version
has `created_by` NULL.

## Verification

Use PostgreSQL 18 Testcontainers for fresh Flyway migration, constraints, MyBatis
round trips and optimistic conflicts. Domain/application/API tests do not substitute
for those checks. If Docker is unavailable, report integration skips explicitly.
