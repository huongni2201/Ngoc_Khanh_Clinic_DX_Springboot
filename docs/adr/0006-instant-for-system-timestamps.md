# ADR-0006: Use `Instant` for timestamps representing moments

## Status

Accepted — 2026-09-26 (supersedes ADR-0004's Java timestamp type mapping)

## Context

ADR-0004 maps PostgreSQL `timestamptz(3)` to Java `OffsetDateTime`. Domain and
persistence timestamps such as `createdAt`, `updatedAt`, and lifecycle times
represent a point on the timeline; their UTC offset is not business data.

## Decision

- Use `Instant` for domain and persistence values representing a point in time.
- Keep calendar-only values such as dates as `LocalDate`.
- Keep PostgreSQL columns as `timestamptz(3)` with millisecond precision.
- Use the shared MyBatis type handler to bind `Instant` as UTC `OffsetDateTime`
  and convert database values back with `toInstant()`.

## Consequences

- Domain and persistence types express instant semantics without carrying a
  meaningless offset.
- JDBC conversion remains explicit and preserves the same instant regardless of
  application or database session time zone.
- No database migration is required; SQL-generated timestamps continue to use
  `CURRENT_TIMESTAMP`.

## Related decisions

- ADR-0004's PostgreSQL physical mappings remain in force; this decision only
  replaces its Java `OffsetDateTime` contract with `Instant`.
- ADR-0005 continues to set PostgreSQL 18 as the primary database.
