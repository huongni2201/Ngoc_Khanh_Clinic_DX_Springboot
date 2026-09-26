# ADR-0004: PostgreSQL 17 as the primary database

## Status

Accepted — 2026-09-26

## Context

The backend was configured for SQL Server, including Flyway, JDBC, MyBatis SQL,
and persistence integration tests. The project has not been deployed and has no
production database to preserve. Continuing with that stack would require
carrying SQL Server-specific configuration and tooling into the first release.

## Decision

- Use PostgreSQL 17 as the sole primary relational database.
- Keep MyBatis and Flyway; use PostgreSQL 17-compatible SQL and Testcontainers.
- Port the existing V001-V007 migration sequence for fresh PostgreSQL
  initialization. This is not a SQL Server data-transfer procedure.
- Keep UUID v7 generation in Java and store identifiers as PostgreSQL `uuid`.
- Map timestamp values to PostgreSQL `timestamptz(3)` and Java
  `OffsetDateTime`, preserving millisecond precision and an unambiguous instant.
  PostgreSQL stores the instant in UTC and renders it in the session time zone;
  SQL-generated values use `CURRENT_TIMESTAMP`.
- Replace SQL Server `rowversion` with a `bigint row_version` counter and a
  PostgreSQL `BEFORE UPDATE` trigger that increments it per row. Optimistic-locking
  updates must compare the previously-read version and report a zero-row update.
- Use PostgreSQL-native booleans, `text` for unbounded text, `varchar(n)` where
  the existing schema defines a length bound, `numeric` for exact amounts, and
  `bytea` for binary values.

This decision supersedes the database-dialect portions of earlier project
guidance and ADR-0001. The business entities, columns, constraints, identity
rules, and UUID v7 strategy remain governed by their existing source documents.

## Consequences

- Local development uses the `postgres:17-alpine` Compose service.
- Spring Boot runtime dependencies use the PostgreSQL JDBC driver and Flyway's
  PostgreSQL database module.
- Database integration tests run against PostgreSQL 17, not H2 or SQL Server.
- Because deployment has not happened, the migration files are ported for a
  clean initial database. After first deployment, all applied Flyway migrations
  are immutable; future schema changes append migrations.
- No SQL Server data export/import is included or required for this project.

## Alternatives considered

- Keep SQL Server: rejected because the application is not deployed and the
  user selected PostgreSQL before launch.
- Maintain both database dialects: rejected because it doubles SQL, migration,
  and integration-test paths without a current product requirement.
