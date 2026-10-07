# PostgreSQL and MyBatis persistence

## Current baseline

PostgreSQL 18, MyBatis and Flyway remain the persistence stack. The owner-selected
clean-slate contract follows [the current domain workflows](03-domain-and-workflows.md).
Fresh databases apply `src/main/resources/db/migration/V001__create_clean_slate_schema.sql`
in one public application schema. The baseline creates 64 business tables.

An environment with the former V001–V003 history must use a separately reviewed
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
  A permission may name the endpoint it protects (`http_method`, `endpoint`
  route template; V005, ADR-0015).
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
`numeric(14,2)` and `BigDecimal`. Integer and boolean scalar declarations use
wrappers (`Long`, `Integer`, `Short`, `Boolean`) for both required and nullable
columns in new or changed code. SQL nullability remains authoritative: a wrapper
does not make a `NOT NULL` field optional. Preserve required-value validation and
null-safe value comparisons. Existing primitive records and their exact-type
assertions must be migrated together; see
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

## Verification

Use PostgreSQL 18 Testcontainers for fresh Flyway migration, constraints, MyBatis
round trips and optimistic conflicts. Domain/application/API tests do not substitute
for those checks. If Docker is unavailable, report integration skips explicitly.
