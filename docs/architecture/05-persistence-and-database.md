# 05 — Persistence and Database Architecture

## 1. Database

Primary database:

```text
Microsoft SQL Server 2022+
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
FROM dbo.encounters
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
LocalDateTime
BigDecimal
byte[]
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

Current v2.11 direction:

```text
V001 = original baseline
V002 = legacy workflow table removal + legacy CCCD column names
V003 = complete result release schema
```

Future changes append new migrations.

## 6. Legacy workflow cut-over

Removing the legacy workflow tables is destructive.

V002 should stop if legacy workflow rows exist.

Operational process:

```text
backup
inspect legacy rows
archive/reconcile
clear only after review
run migration
verify derived worklists
```

Do not silently drop populated legacy workflow tables.

## 7. Identifier strategy

Persisted IDs:

```text
uniqueidentifier
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

## 10. Result release

Required fields:

### lab_results

```text
released_to_patient_at datetime2(3) NULL
released_to_patient_by_user_id uniqueidentifier NULL
```

### diagnostic_reports

```text
released_to_patient_at datetime2(3) NULL
released_to_patient_by_user_id uniqueidentifier NULL
```

Release is exact-version visibility.

SMS delivery state is not the source of truth for Patient Portal visibility.

## 11. Corporate price snapshots

Corporate pricing uses immutable/use-case-controlled snapshots.

The following must remain consistent after repricing:

```text
health_examination_batch_services.negotiated_unit_price
health_examination_batch_employee_services.unit_price_snapshot
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
rowversion
optimistic version check
transaction locking
unique constraint
idempotency key
```

Choice depends on aggregate/use case.
