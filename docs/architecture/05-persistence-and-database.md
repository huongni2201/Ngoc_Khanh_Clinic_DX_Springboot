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

Persistence records represent database shape.

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

## 5. Migration policy

Never edit an already-applied Flyway migration to change deployed schema.

Use:

```text
V001 ...
V002 ...
V003 ...
V004 ...
```

Fresh-install baseline (PostgreSQL 18):

```text
V001 = final schema for the current table-design baseline and accepted ADR additions
```

The initial migration chain was consolidated before first deployment because no production database exists. Once the first database is deployed, treat every applied migration as immutable and append new migrations for future changes. This clean-install baseline is not an in-place SQL Server-to-PostgreSQL data-transfer plan.

## 6. Initial schema scope

The cut-over instructions that follow describe the pre-squash migration chain and are obsolete. Fresh installation uses only `V001__create_final_schema.sql`.

The final schema does not create the retired workflow tables. Fresh initialization needs no follow-up cut-over or data-removal step.

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
