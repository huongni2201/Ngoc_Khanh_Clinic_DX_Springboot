# ADR-0005: PostgreSQL 18 as the primary database

## Status

Accepted — 2026-09-26 (supersedes ADR-0004's server major-version target)

## Context

ADR-0004 selected PostgreSQL 17 before deployment. The project remains
undeployed, so there is no production database to migrate. The repository now
targets PostgreSQL 18 for development, CI, and the first deployment.

## Decision

- Use PostgreSQL 18 as the sole primary relational database.
- Keep MyBatis, Flyway, Java UUID v7 identifiers, and the PostgreSQL physical
  type mappings defined in ADR-0004.
- Validate the existing fresh-install Flyway chain against PostgreSQL 18; this
  version update does not change business tables or add a migration.
- Use `postgres:18-alpine` for local Compose and PostgreSQL Testcontainers.
- Mount local Compose data at `/var/lib/postgresql`, matching the PostgreSQL 18
  official image layout.

## Consequences

- PostgreSQL 18 is the compatibility target for SQL, migrations, and database
  integration tests.
- A PostgreSQL 17 data directory cannot be opened directly by PostgreSQL 18.
  Existing local data must be upgraded with `pg_upgrade` or dump/restore before
  reusing it; the official image detects an old cluster and exits with an
  explanatory error.
- Since the backend has not been deployed, no production data migration is
  required for this decision.
- ADR-0004 remains the historical record of the original PostgreSQL selection;
  its type mapping and MyBatis/Flyway decisions remain in force.

## Alternatives considered

- Keep PostgreSQL 17: rejected because the project requested PostgreSQL 18
  before initial deployment.
- Support PostgreSQL 17 and 18 in parallel: rejected because it doubles the
  database compatibility and integration-test surface without a current need.

## References

- [PostgreSQL 18 documentation](https://www.postgresql.org/docs/18/)
- [PostgreSQL major-version upgrade documentation](https://www.postgresql.org/docs/18/pgupgrade.html)
- [PostgreSQL Docker Official Image PostgreSQL 18 data-directory layout](https://github.com/docker-library/postgres/blob/master/Dockerfile-debian.template)
