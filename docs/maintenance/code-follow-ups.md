# Backend code follow-ups

Recorded 2026-10-05. This is implementation debt, not an amendment to accepted
business contracts. Documentation/skill/hygiene repair did not change runtime
code, schema or authorization. The owning policies remain in PROJECT_RULES and ADRs.

| Priority | Evidence | Required follow-up and acceptance evidence |
|---|---|---|
| High | [AuthInfrastructureConfiguration](../../src/main/java/com/ngockhanh/clinic/identity/infrastructure/configuration/AuthInfrastructureConfiguration.java) enables development behavior whenever local/test is active, including alongside prod/production. The batch controller no longer has an actor fallback (principal only, 2026-10-06), but the stale `clinic.health-examination.batch.mock-created-by` property remains in `application-local.yaml`. | Move test/development actor provisioning to explicit security configuration; keep real authenticated identity for business writes. Reject mixed production/development profiles at startup. Remove the unused property. Test prod+local, prod+test, production+local and production+test regressions alongside standalone profile regressions. |
| Medium | Java-type policy formerly required wrappers even for local values. | Policy now follows nullability. Do not bulk-migrate existing declarations. When changing nullable contracts, update null/equality handling, MyBatis and affected type assertions together; required local scalar primitives are valid. |

## Local catalog seed and unverified build (2026-10-06)

`src/main/resources/db/local/R__local_catalog_seed.sql` is a repeatable, idempotent Flyway script that
only runs for the `local` profile (`classpath:db/local`). It inserts two departments and five active
services (`LOCAL_DEPT_*`, `LOCAL_SVC_*`) with `ON CONFLICT (code) DO NOTHING`; it creates no account,
role or credential. Production uses `classpath:db/migration` only.

The catalog list endpoint (`GET /api/v1/catalog/services`), `serviceCode`/`serviceName` in batch detail and
`rowVersion` in batch summaries were written without a Java 25 toolchain, Maven Central access or Docker, so
`./mvnw verify` (including the Testcontainers tests) has **not** been run for them. Run it before merging.

## Local cache relocation

The ignored .m2 directory remains in the checkout. It was not deleted or moved
during this repair. Relocate it with the actual Maven invocation/settings that
select the repository path, preserve downloaded artifacts, and verify offline
resolution before removing the old cache. Do not guess which settings a developer
or CI invocation uses. This is a machine-local operational task, not a schema change.

## Frontend dependencies and integration

[Frontend code follow-ups](../../../ngoc_khanh_clinic_frontend/docs/maintenance/code-follow-ups.md)
owns the remaining removed-import callers, unsupported routes/enums, dependencies
and contrast fixes. The current [HTTP inventory](../api/clean-slate-migration.md)
does not authorize implementing missing business workflows merely to satisfy FE calls.
