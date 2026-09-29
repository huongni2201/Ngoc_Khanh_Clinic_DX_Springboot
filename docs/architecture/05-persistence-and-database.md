# 05 — Persistence and Database Architecture

## 1. Database

Primary database:

```text
PostgreSQL 18
```

Persistence framework:

```text
MyBatis
```

Schema migration:

```text
Flyway
```

## 2. MyBatis rules

Use explicit columns.

Preferred:

```sql
SELECT id, patient_id, status, created_at
FROM public.encounters
WHERE id = #{id}
```

Avoid:

```sql
SELECT *
```

Use parameter binding:

```text
#{value}
```

Never interpolate user data with `${...}`.

## 3. Persistence records

Each file in `infrastructure/persistence/record` represents rows of one existing
table. Reuse that table record across queries. No summary, reference, join-result,
or screen-specific record files belong here. This table mapping uses MyBatis;
it does not introduce JPA or Hibernate.

They may use:

```text
UUID
String
LocalDate
Instant
BigDecimal
byte[]
long (row_version)
```

Do not use persistence records as domain entities.

## 4. Converter

Each non-trivial aggregate should have explicit mapping:

```text
Domain -> PersistenceRecord
PersistenceRecord -> Domain restore(...)
```

Mapping must preserve all lifecycle and concurrency fields.

Repository adapters coordinate mapper calls and pass records to converters.
Converters never execute SQL. Restore aggregates with their required owned state;
use explicit domain read contracts for summaries rather than incomplete aggregates.
Reuse existing files: a private nested converter in the repository is permitted,
and read contract types may be nested in their owning repository interface.

## 5. Migration policy

Never edit an already-applied Flyway migration to change deployed schema.

Use `V<version>__<description>.sql` and append new versions for schema changes.
The initial chain was consolidated into V001 before first deployment; subsequent
migrations extend that starting schema. Treat applied migrations as immutable.

## 6. Current migration chain

Fresh PostgreSQL 18 installations apply every migration in
`src/main/resources/db/migration/` in version order. V001 alone is not the current schema.

| Migration | Effect |
|---|---|
| `V001__create_final_schema.sql` | Initial consolidated schema. |
| `V002__store_batch_employee_administrative_snapshot_as_columns.sql` | Backfills batch-participant administrative snapshots into typed columns, then drops `administrative_snapshot_json` (ADR-0007). The historical filename retains `employee`. |
| `V003__remove_organization_code.sql` | Drops the organization-code unique constraint and `organizations.organization_code`. |

This describes files currently present in the workspace, not which versions have
been applied to an environment. Verify deployment state from that environment's
Flyway history. This chain is not a SQL Server-to-PostgreSQL data-transfer plan.

ADR-0008 keeps administrative snapshot values as explicit aggregate fields and
retains the typed columns introduced by V002; it requires no separate migration.

## 7. Identifier strategy

Persisted IDs:

```text
uuid
```

Application-generated:

```text
UUID v7
```

No database-generated identity integer is used for aggregate identity.

## 8. Patient identity

`patients.identification_number`:

- mandatory;
- unique;
- digits only at domain validation level;
- current MVP business identity.

## 9. Lifecycle columns

Use business timestamps where needed:

```text
created_at
updated_at
finalized_at
closed_at
released_to_patient_at
```

Timestamp semantics must be documented and tested.

Domain and persistence records use `Instant` for values representing a point in time. Event and audit timestamps use `timestamptz(3)` to preserve millisecond precision. The shared MyBatis type handler binds instants as UTC `OffsetDateTime` values and converts reads back with `toInstant()`, so session and JVM time zones do not change the represented instant. SQL writes that create timestamps use `CURRENT_TIMESTAMP`.

## 10. Result release

Required fields:

### lab_results

```text
released_to_patient_at timestamptz(3) NULL
released_to_patient_by_user_id uuid NULL
```

### diagnostic_reports

```text
released_to_patient_at timestamptz(3) NULL
released_to_patient_by_user_id uuid NULL
```

Release is exact-version visibility.

SMS delivery state is not the source of truth for Patient Portal visibility.

## 11. Corporate price snapshots

Corporate pricing uses immutable/use-case-controlled snapshots.

The following must remain consistent after repricing:

```text
health_examination_batch_services.negotiated_unit_price
health_examination_batch_participant_services.unit_price_snapshot
corporate service_requests.unit_price_snapshot
```

Retail `service_prices` must not be changed by batch repricing.

## 12. Constraints

Prefer DB constraints for stable simple invariants:

```text
NOT NULL
UNIQUE
FOREIGN KEY
CHECK
```

Do not try to encode complex cross-aggregate business workflow in unreadable SQL constraints.

## 13. Concurrency

Where concurrent updates can cause lost data, use one or more:

```text
bigint row_version
optimistic version check
transaction locking
unique constraint
idempotency key
```

`row_version` is a per-row bigint token incremented by a PostgreSQL before-update trigger. Updates that need optimistic locking must still compare the previously-read version and treat a zero-row update as a conflict.
