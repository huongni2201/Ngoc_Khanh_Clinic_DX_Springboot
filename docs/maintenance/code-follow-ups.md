# Backend code follow-ups

Recorded 2026-10-05. This is implementation debt, not an amendment to accepted
business contracts. Documentation/skill/hygiene repair did not change runtime
code, schema or authorization. The owning policies remain in PROJECT_RULES and ADRs.

| Priority | Evidence | Required follow-up and acceptance evidence |
|---|---|---|
| High | [OrganizationBatchController.actor](../../src/main/java/com/ngockhanh/clinic/healthexamination/api/controller/OrganizationBatchController.java) falls back to a configured actor when principal is absent in local/test. [AuthInfrastructureConfiguration](../../src/main/java/com/ngockhanh/clinic/identity/infrastructure/configuration/AuthInfrastructureConfiguration.java) enables development behavior whenever local/test is active, including alongside prod/production. | Move test/development actor provisioning to explicit security configuration; keep real authenticated identity for business writes. Reject mixed production/development profiles at startup. Test prod+local, prod+test, production+local, production+test and absent-principal writes, alongside standalone profile regressions. The current filter chain provides access checks; controller fallback alone is not proof of a reachable unauthenticated HTTP bypass. |
| Medium | During this documentation repair, source work outside this task removed BatchDraftEditor and batch create/list use-case files while [BatchDraftEditorTest](../../src/test/java/com/ngockhanh/clinic/healthexamination/application/usecase/BatchDraftEditorTest.java) and the batch controller still reference them. | Reconcile callers/tests with the intended replacement before runtime verification. If the collaborator is retained, place it outside usecase. Source may still be changing; recheck the checkout rather than restoring removed files from this report. |
| Medium | Java-type policy formerly required wrappers even for local values. | Policy now follows nullability. Do not bulk-migrate existing declarations. When changing nullable contracts, update null/equality handling, MyBatis and affected type assertions together; required local scalar primitives are valid. |

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
