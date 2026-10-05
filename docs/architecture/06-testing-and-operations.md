# Verification and operations

## Required checks

Run the project Maven wrapper:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

Domain tests cover lifecycle/identity/pricing/history without Spring. Application
and API tests cover orchestration, input, authorization, response fields and errors.
Spring Modulith/ArchUnit check module inventory, public interfaces, pure domain
and transport/persistence separation. Spotless uses the project's committed format.

Use PostgreSQL 18 Testcontainers for Flyway, MyBatis, unique/composite constraints,
optimistic conflicts, atomic business/audit rollback, price history and immutable
versions. Identity integration additionally uses Redis. Report real skips/failures;
H2, mocks and schema text tests are not evidence of PostgreSQL compatibility.

The current schema has 64 business tables. Contract tests assert every table's
record columns/order/types/owner and no removed table records. Runtime SQL table
checks supplement, rather than replace, actual PostgreSQL execution.

API surface coverage verifies that all removed Excel import routes remain absent.
Import-specific tests and workbook fixtures are removed; participant service
reconciliation and historical provenance remain covered by their own tests.
Batch coverage includes atomic days/services, reference snapshots and stale versions.
API requests require explicit mutable versions.

## Build output

After removing or moving source classes/XML, clean Maven output before evaluating
tests. Old class/resource files copied under target can otherwise remain visible
to MyBatis or module scanning. A clean failure is an environment/build-output issue,
not permission to weaken tests or retain obsolete runtime adapters.

## Deployment

Use Java 25 and PostgreSQL 18. Fresh databases apply the clean-slate V001; databases
with the former V001-V003 history require a separate reviewed conversion procedure.
Never replace applied checksums or point this baseline at deployed historical data.

Flyway startup is disabled by default and enabled for local/test databases by
configuration. Production migration is an explicit deployment step. Startup must
use a compatible migrated schema. No runtime auto-create/auto-alter ORM schema exists.

DB_URL, DB_USERNAME, DB_PASSWORD, Redis settings and JWT key belong in
environment/secret storage. Do not use local/test profiles in
production. Keep the documented cookie/CSRF/CORS policy and database privileges.

Outbox/external effects are recorded with business changes and dispatched after
commit. Workers use leases/idempotency and bounded retries. Operational health and
logging reveal dependency readiness/errors without patient or secret payloads.
Backups and verified restore procedures are required before deployed conversion.
