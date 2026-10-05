# Current clean-slate HTTP contract

Verified against controller source on 2026-10-05. This document inventories
declared controller routes; [ADR-0013](../adr/0013-clean-slate-application-contract.md)
owns intended clean-slate behavior and [API/security](../architecture/05-api-and-security.md)
owns access policy. A handler's existence does not imply production authorization.

Source work outside this documentation task is in progress: the batch controller
currently refers to create/list use-case types whose source files were removed.
This inventory is not a successful build/runtime claim. Recheck
[code follow-ups](../maintenance/code-follow-ups.md) and run verification after the
source work completes.

## Implemented routes

All paths include `/api/v1`.

| Method | Path | Handler |
|---|---|---|
| POST | /api/v1/organizations | OrganizationController.create |
| GET | /api/v1/organizations/{organizationId} | OrganizationController.get |
| PUT | /api/v1/organizations/{organizationId} | OrganizationController.update |
| POST | /api/v1/organizations/{organizationId}/health-examination-batches | OrganizationBatchController.create |
| GET | /api/v1/organizations/{organizationId}/health-examination-batches | OrganizationBatchController.list |
| GET | /api/v1/auth/csrf | AuthController.csrf |
| POST | /api/v1/auth/login | AuthController.login |
| GET | /api/v1/auth/me | AuthController.me |
| POST | /api/v1/auth/logout | AuthController.logout |
| POST | /api/v1/auth/logout-all | AuthController.logoutAll |

Source: [OrganizationController](../../src/main/java/com/ngockhanh/clinic/healthexamination/api/controller/OrganizationController.java),
[OrganizationBatchController](../../src/main/java/com/ngockhanh/clinic/healthexamination/api/controller/OrganizationBatchController.java),
[AuthController](../../src/main/java/com/ngockhanh/clinic/identity/api/controller/AuthController.java).

There is no organization list/delete, batch detail/update/delete, participant
list/add/check-in, catalog or print HTTP handler in this checkout.
In particular, `GET /api/v1/organizations`,
`GET .../health-examination-batches/{batchId}` and
`GET .../health-examination-batches/{batchId}/participant` are unsupported.
A frontend route/API function is not evidence that these handlers exist.

## DTO and lifecycle boundaries

Inspect linked controller request/application response types before an integration
change; do not infer fields from UI models. Organization carries code/type,
general contacts and one named contact; update carries expected rowVersion.
Batch creation supplies concrete days and configured service prices; its displayed
date bounds derive from those days. Existing storage/domain update operations do
not create an HTTP update endpoint.

BatchStatus is exactly DRAFT, READY, FINALIZED, CLOSED. Record, roster, attendance
and reconciliation lifecycles are separate; their statuses cannot extend BatchStatus.
No age-eligibility rejection is part of the current roster/record contract.
Date-only values use LocalDate (YYYY-MM-DD); timestamp values use Instant (UTC,
for example 2026-10-05T03:00:00Z). Local display formatting belongs to the client.

Reuse [the envelope/error/pagination policy](../../PROJECT_RULES.md#envelope-errors-and-pagination).
Lists are one-based; auth logout endpoints return empty 204 responses on success.
Public cookies/CSRF/session behavior remains under
[authentication policy](../architecture/05-api-and-security.md#authentication-and-authorization).

## Removed Excel roster import

Owner amendment, 2026-10-05: no template, upload, mapping, preview, confirm or
cancel handler/use case remains. Legacy `employee-imports` and
`employees/import-template` calls are unsupported.
Integration schema records/import tables and participant provenance remain for
historical data. Their presence does not authorize restoring the workflow.

The sibling frontend still has legacy import and unsupported endpoint callers.
See [its code follow-ups](../../../ngoc_khanh_clinic_frontend/docs/maintenance/code-follow-ups.md).
Client removal/disablement is separate implementation work.

## Database deployment

The clean-slate V001 is for fresh databases only. Former V001–V003 deployments
require a reviewed conversion procedure; none is supplied by this inventory.
Follow [deployment policy](../architecture/06-testing-and-operations.md#deployment).
