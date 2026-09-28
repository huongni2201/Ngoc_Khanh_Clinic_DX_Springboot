# ADR-0007: Store batch employee administrative snapshots in typed columns

## Status

Accepted — 2026-09-28 (supersedes the JSON storage representation in `table-design-v2.11`, section 4.35)

## Context

`health_examination_batch_participants` stored confirmed administrative roster data in
`administrative_snapshot_json`. The data fields are stable and already have typed
counterparts in `health_examination_records`. JSON path expressions also made employee
search depend on casts and JSON structure.

## Decision

- Store each administrative snapshot field on `health_examination_batch_participants`
  as a typed column, using the physical types already used by `health_examination_records`.
- Keep the batch and employment snapshot fields as columns. Keep administrative occupation
  separate from the employment `occupation_snapshot` field.
- Keep the application and API `AdministrativeSnapshot` value object. Persistence maps
  between that value object and the typed columns.
- Backfill existing JSON payloads into the columns in a new Flyway migration, then drop
  `administrative_snapshot_json`.
- Employee-list search reads the full name, identification number, and phone columns.

## Consequences

- Required identity fields remain `NOT NULL`; optional administrative fields remain nullable.
- SQL no longer needs to parse JSON for batch employee search or persistence.
- The table has a fixed typed schema for the administrative fields; adding a new field
  requires an application and database migration.
- No search index is added by this decision.

## Related decisions

- ADR-0005 sets PostgreSQL 18 as the database target.
- ADR-0006 sets `Instant` as the Java type for system timestamps.
