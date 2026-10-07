# Verification and operations

## Required checks

For runtime/build changes, run the project Maven wrapper (verify includes test):

```powershell
.\mvnw.cmd verify
```

Documentation/skill/hygiene-only changes verify links/anchors, skill resources,
contracts and git diffs under [definition of done](../../PROJECT_RULES.md#36-definition-of-done).

Domain tests cover lifecycle/identity/pricing/history without Spring. Application
and API tests cover orchestration, input, authorization, response fields and errors.
Spring Modulith/ArchUnit check module inventory, public interfaces, pure domain
and transport/persistence separation. Spotless uses the project's committed format.

Use PostgreSQL 18 Testcontainers for Flyway, MyBatis, unique/composite constraints,
optimistic conflicts, atomic business/audit rollback, price history and immutable
versions. Access-control integration additionally uses Redis. Report real skips/failures;
H2, mocks and schema text tests are not evidence of PostgreSQL compatibility.

The current schema has 64 business tables. Contract tests assert every table's
record columns/order/types/owner and no removed table records. Runtime SQL table
checks supplement, rather than replace, actual PostgreSQL execution.

API surface coverage verifies that all removed Excel import routes remain absent.
The single-step Participant import, template and list remain covered; only the old
multi-step preview/confirm/cancel workflow is removed. Participant service
reconciliation and historical provenance retain their own tests.
Batch coverage includes atomic days/services, reference snapshots and stale versions.
API requests require explicit mutable versions.

## Build output

After removing or moving source classes/XML, clean Maven output before evaluating
tests. Old class/resource files copied under target can otherwise remain visible
to MyBatis or module scanning. A clean failure is an environment/build-output issue,
not permission to weaken tests or retain obsolete runtime adapters.

## Deployment

Use Java 25 and PostgreSQL 18. The owner-requested consolidation on 2026-10-07
establishes V001 and V002 for fresh databases: V001 creates the complete
schema; V002 inserts access-control roles, permissions and grants, including the
owner-approved PARTICIPANT_CREATE and PARTICIPANT_REACTIVATE permissions. V003
adds the catalog; V004 aligns the account-role trigger with the PATIENT role.
Changes from the pre-consolidation V002–V006 history are represented in the
current schema and seed; V003 and V004 are later catalog and account-role
amendments. Older migration numbers in implementation plans describe historical
work, not the current migration chain.
Databases with any earlier migration history require a separate reviewed conversion procedure.
Never replace applied checksums or point this baseline at deployed historical data.

Flyway startup is disabled by default and enabled for local/test databases by
configuration. Production migration is an explicit deployment step. Startup must
use a compatible migrated schema. No runtime auto-create/auto-alter ORM schema exists.

DB_URL, DB_USERNAME, DB_PASSWORD, Redis settings, `NKC_AUTH_COOKIE_SECURE` and
`NKC_AUTH_ALLOWED_ORIGINS` belong in environment/secret storage. Do not use
local/test profiles in production. Keep the documented cookie/Origin policy
([login operations](../api/login.md)) and database privileges.

Outbox/external effects are recorded with business changes and dispatched after
commit. Workers use leases/idempotency and bounded retries. Operational health and
logging reveal dependency readiness/errors without patient or secret payloads.
Backups and verified restore procedures are required before deployed conversion.
