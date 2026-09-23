# UUID v7 Identifier Design

## Purpose and scope

Replace the current numeric surrogate identifiers in the un-applied table-design baseline with UUID v7 identifiers, and keep the Java persistence model and database contract aligned.

The change covers:

- every table primary-key column named `id`;
- every typed relationship key such as `patient_id`, `encounter_id`, and `created_by_user_id`;
- typed polymorphic identifiers currently represented as `entity_id` or `internal_entity_id` with `bigint`;
- Java persistence records and their contract tests;
- healthcheck domain aggregate IDs, repository-port signatures, and domain tests;
- `table-design-v2.10.md`, an ADR, and the Flyway baseline migration.

The current migration is not applied in a shared or production environment. Therefore the implementation updates the baseline migration directly and does not include a data-conversion migration.

The change does not alter statuses, permissions, clinical rules, payment rules, or module boundaries. The approved identity-contract update below replaces the previous identificationNumber naming/requirement with `identificationNumber`/`identification_number`.

## Decision

All persisted surrogate identifiers use SQL Server `uniqueidentifier` and Java `java.util.UUID`. The application creates UUID v7 values before insert. Database-generated `NEWID()` and `NEWSEQUENTIALID()` are not used because they do not provide UUID v7 semantics.

`public_id` is removed from the baseline schema and from persistence records. Once `id` itself is a UUID v7, maintaining a second public UUID for the same row duplicates identity and creates avoidable synchronization rules.

The following remain unchanged:

- business identifiers such as `patient_code`, `encounter_code`, `invoice_number`, `company_code`, and `shs_code`;
- the legal/business identity field `identificationNumber` in Java and `identification_number` in SQL;
- `correlation_id` UUID columns, which are identifiers but not row keys;
- untyped string references defined by the table design, including audit `entity_id`, idempotency `result_entity_id`, and outbox `aggregate_id`;
- all non-identifier numeric fields such as amounts, quantities, versions, display orders, and row numbers.

The typed polymorphic fields `file_attachments.entity_id` and `external_code_mappings.internal_entity_id` become `uniqueidentifier` because their current `bigint` values refer to persisted entities whose IDs are changing.

## Identity contract update

The current repository rules and baseline documents still describe identificationNumber/`identificationNumber`. The approved requirement for this change supersedes that naming and identity contract:

- Java/domain/API terminology is `identificationNumber`;
- SQL/persistence terminology is `identification_number`;
- the old `IdentificationNumber` value object/class, `identificationNumber` fields, `identificationNumber_snapshot` columns, and identificationNumber wording are removed;
- requirement, use-case, table-design, project-rule, and test documentation is updated consistently;
- no compatibility alias is kept, so every exact-identity lookup and health-check snapshot uses the new field name.

This is a deliberate business-contract update and is recorded in the ADR alongside the UUID v7 decision.

## UUID v7 generation

Java 25's `java.util.UUID` API supports UUID manipulation and versions such as random UUID v4, but it does not provide a UUID v7 factory. The repository therefore owns a small framework-free generator in the shared technical layer.

The generator:

1. takes the current Unix timestamp in milliseconds for the 48-bit UUID v7 timestamp field;
2. sets the version field to `0111` (version 7);
3. uses a 12-bit monotonic `rand_a` value within the same millisecond;
4. fills the 62-bit `rand_b` value from `SecureRandom`;
5. sets the RFC 9562 variant bits to `10`;
6. advances the logical timestamp when more than 4096 values are requested in one millisecond so generated values remain ordered rather than wrapping `rand_a`.

The generator has no Spring or persistence dependency. It is used by application/domain construction code; persistence adapters receive UUIDs and bind them to SQL Server `uniqueidentifier` parameters.

## Database contract

Every table's primary key changes from:

```sql
id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_<table> PRIMARY KEY
```

to:

```sql
id uniqueidentifier NOT NULL CONSTRAINT PK_<table> PRIMARY KEY
```

All typed foreign-key columns use `uniqueidentifier` with the same nullability and named foreign-key constraints as the v2.10 design. No `IDENTITY` or UUID default is declared; application-generated UUID v7 values are required on insert.

The migration must contain no `public_id` columns or constraints and no `UQ_*_public_id` constraints. Every foreign-key pair must have identical SQL Server types.

The migration keeps the existing 67-table inventory and existing constraints/indexes unless an identifier column or its associated public-id constraint is directly affected.

## Java persistence contract

All persistence-record fields representing `id` or a typed `*_id` become `UUID` instead of `Long`. `UUID publicId` fields are removed. `UUID` fields already used for `correlationId` remain present.

The affected records include all bounded contexts currently represented under `infrastructure.persistence.record`, including patient, catalog, encounter, clinical, billing, diagnostics, healthcheck, identity, document, prescription, notification, integration, and shared audit records.

The change does not create a generic ID wrapper or a cross-module repository. UUID remains the explicit Java type at the persistence boundary, while domain modules continue to own their domain IDs and public contracts.

## Healthcheck domain contract

The current `healthcheck` aggregate model and repository ports use primitive numeric IDs, so they must change with the persisted identifier contract. The following domain values become `UUID`:

- aggregate IDs and related IDs in `Company`, `CompanyEmployee`, `HealthCheckBatch`, `HealthCheckBatchService`, `HealthCheckBatchEmployee`, `HealthCheckBatchEmployeeService`, `HealthCheckRecord`, `HealthCheckImportJob`, and their related models;
- `findById` and other ID-bearing method parameters/return values in healthcheck repository ports;
- the corresponding `HealthCheckDomainTest` fixtures and assertions;
- the `IdentificationNumber` value object becomes an `IdentificationNumber` value object with the same structural validation currently applied to the identity number.

The domain retains the same invariants and constructor/factory behavior. Numeric checks such as `id <= 0` become null checks, while nullable relationship IDs remain nullable. The domain does not generate UUIDs itself during restoration; application code supplies IDs generated by `UuidV7Generator` for new aggregates and UUIDs loaded from persistence for restored aggregates.

## Documentation contract

`docs/baseline/table-design-v2.10.md` becomes the UUID v7 baseline:

- its identifier convention states that primary/typed relationship IDs are UUID v7 values stored as `uniqueidentifier`;
- every `id` and typed relationship row uses `uniqueidentifier`;
- all `public_id` rows are removed;
- the exceptions for string polymorphic references and non-key UUID correlation IDs are explicit;
- the migration and Java-record contract are documented without changing business semantics.

An ADR records the long-lived identity decision, the fresh-baseline assumption, the choice to generate UUID v7 in application code, and the consequences for indexing, ordering, and future migrations.

## Testing contract

Add or update tests to prove:

- generated UUIDs have version 7 and RFC variant 2;
- generated UUIDs carry a timestamp close to the generation time;
- all business identity references use `identificationNumber`/`identification_number` and no source or test retains the old `identificationNumber` contract;
- sequential values from one generator are ordered, including same-millisecond generation;
- every persistence record ID/typed relationship field uses `UUID` and no record retains `publicId`;
- healthcheck domain IDs and repository-port ID signatures use `UUID`, while domain invariants still reject missing required IDs;
- the migration has exactly the existing 67 tables, no `IDENTITY` identifier definition, no `bigint` typed identifier definition, no `public_id`, and no legacy public-id constraint;
- representative primary-key, foreign-key, and typed polymorphic columns are `uniqueidentifier` in the migration text;
- existing SQL Server Testcontainers migration verification still applies the baseline cleanly when Docker is available.

No H2 test is introduced. No data migration is added because the baseline has not been applied.

## Alternatives considered

### Keep bigint `id` and use UUID v7 only for `public_id`

Rejected because the request is to make `id` UUID v7 and because the repository would retain two identifiers for every externally relevant row.

### Keep bigint internal IDs and add UUID v7 `public_id`

Rejected for the same duplication and because all inter-table relationships would continue to use numeric IDs.

### Use SQL Server `NEWSEQUENTIALID()`

Rejected because it is not UUID v7 and is database-generated, which prevents the application from assigning the identity before insert and does not give the required RFC 9562 layout.

### Add a third-party UUID library

Rejected for this change because the required bit layout is small enough to implement and test locally, and the project rules require avoiding a dependency when the JDK does not already provide the exact feature.

## Operational consequences

- UUIDs increase key and index width from 8 bytes to 16 bytes.
- Randomized UUID v7 suffixes can fragment indexes less than UUID v4 because their timestamp prefix is ordered, but SQL Server's `uniqueidentifier` comparison/storage behavior must still be covered by integration tests.
- API examples and future DTOs must expose the UUID `id` as the resource identifier; no `public_id` compatibility field is available in the fresh baseline.
- If this baseline is later applied to an environment with existing data, a separate reviewed migration must map every old numeric key and all dependent foreign keys; this spec intentionally does not define that conversion.

## Out of scope

- changing table inventory or bounded-context ownership;
- changing business identifiers or generating UUID v7 for business codes;
- adding REST endpoints or changing endpoint paths;
- converting existing shared/production data;
- adding JPA/Hibernate or another persistence framework;
- introducing a UUID database extension, trigger, or external service.
