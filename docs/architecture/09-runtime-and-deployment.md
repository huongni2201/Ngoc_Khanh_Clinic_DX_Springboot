# 09 — Runtime and Deployment

## 1. Runtime

Application:

```text
Java 25
Spring Boot 4.x
Maven Wrapper
```

Database:

```text
PostgreSQL 18
```

## 2. Configuration

Secrets and environment-specific values must come from environment/configuration.

Examples:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
SMS credentials
external integration credentials
storage configuration
```

Do not commit secrets.

## 3. Profiles

Recommended:

```text
application.yaml
application-local.yaml
application-test.yaml
```

Production secrets should not live in committed profile files.
For local development, `docker compose up -d postgres` starts the PostgreSQL 18 service. The committed local fallback password is development-only; production must supply credentials through environment/configuration. PostgreSQL 18 uses a version-specific data directory; the Compose volume mounts `/var/lib/postgresql`. Upgrade existing PostgreSQL 17 data with `pg_upgrade` or dump/restore before starting this image; it cannot open the old cluster directly.

## 4. Startup

Recommended startup order:

```text
1. establish DB connection
2. Flyway validate/migrate
3. initialize application
4. expose readiness
```

Do not allow application to run against an incompatible schema.

## 5. Migration deployment

Before production migration:

```text
backup database
validate migration checksum
run prechecks
apply Flyway
run schema smoke checks
start application
run API smoke tests
```

Fresh PostgreSQL 18 installations use the single final-schema migration. After first deployment, future schema changes append new Flyway versions.

## 6. Observability

At minimum record:

```text
request/correlation id
application errors
integration failures
migration failure
outbox retry failure
authentication/authorization failure
```

Avoid logging sensitive medical data by default.

## 7. Health checks

Expose safe operational health endpoints for:

```text
application liveness
database readiness
optional external dependency status
```

Do not expose credentials or sensitive details.

## 8. Backup and recovery

Database backup is part of operational design.

For destructive migrations:

```text
backup must exist before apply
rollback is restore/forward-fix, not editing old Flyway history
```
