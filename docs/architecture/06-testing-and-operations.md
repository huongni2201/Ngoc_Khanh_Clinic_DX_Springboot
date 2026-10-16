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

The current schema has 65 business tables. Contract tests assert every table's
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

Use Java 25 and PostgreSQL 18. The owner-requested consolidation on 2026-10-08
establishes the baseline for fresh databases: V001 creates the final schema,
V002 seeds access-control roles/permissions/grants, and V003 seeds the catalog.
The 2026-10-11 and 2026-10-13 consolidations fold every later amendment (former V004–V009) and the
removal of `account_roles` into those three files; V002 also carries the print, bulk-print and
batch-start endpoint permissions.
See [persistence baseline](04-persistence.md#current-baseline) for their responsibilities.
A database created before these edits cannot be reused: Flyway reports changed checksums and missing
versions, so recreate it (for the Compose database, `docker compose down -v`).
Clean build output before verification or packaging after consolidation so deleted
migration resources cannot survive under `target/classes/db/migration`.
Databases with any earlier migration history require a separate reviewed conversion procedure.
Never replace applied checksums or point this baseline at deployed historical data.

Catalog management V007 follows the unchanged V001–V006 baseline. It adds
versions, timestamps, indexes and 30 endpoint permissions granted to CLINIC_MANAGER
in one transaction. The local access-control seed and afterMigrate callback
provide TEST permissions.
See [catalog deployment](../api/catalog-management.md#5-deployment-and-verification).

Document printing needs a Gotenberg 8 service (`NKC_DOCUMENT_GOTENBERG_URL`, see
[examination form print](../api/examination-form-print.md#cấu-hình)). Production must run it
without Internet egress. The starter template is seeded at startup and the application fails
fast if it does not match its contract; set `nkc.document.seed-starter-templates=false` to skip it.
The seeder records the SHA-256 of `health-examination-v1.docx` and never replaces it, so editing that file after
a database has seeded it makes every print fail on the checksum: recreate local databases, and ship a
changed template as a new version under ADR-0018.

IT maintains document templates under
[ADR-0018](../adr/0018-it-managed-document-templates.md); there is no product template
editing/upload/publish workflow. Content needs business/clinical approval and PDF
validation before release. The seeder never updates an existing type: a replacement
needs a new immutable file reference/version and a reviewed release/migration,
including the effective-period transition. Keep referenced packaged resources at
their original paths; replacing `health-examination-v1.docx` in place is not a version upgrade.

Bulk printing needs `NKC_DOCUMENT_STORAGE_ROOT` (a writable directory; without it job creation returns
503) and Gotenberg's `--api-timeout` at least `nkc.document.gotenberg.merge-timeout` (90 s). The worker
runs in every instance (`nkc.document.jobs.enabled=false` turns it off). See
[print jobs](../api/examination-form-print-jobs.md#vận-hành).

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
