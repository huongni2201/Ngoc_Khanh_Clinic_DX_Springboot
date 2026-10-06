# Current clean-slate HTTP contract

Verified against controller source on 2026-10-05. This document inventories
declared controller routes; [ADR-0013](../adr/0013-clean-slate-application-contract.md)
owns intended clean-slate behavior and [API/security](../architecture/05-api-and-security.md)
owns access policy. A handler's existence does not imply production authorization.

The batch controller was completed on 2026-10-06 (create, get, update, list, delete).
This inventory is not a successful build/runtime claim; run `.\mvnw.cmd verify` for
evidence.

## Implemented routes

All paths include `/api/v1`.

| Method | Path | Handler |
|---|---|---|
| POST | /api/v1/organizations | OrganizationController.create |
| GET | /api/v1/organizations | OrganizationController.list |
| GET | /api/v1/organizations/{organizationId} | OrganizationController.get |
| PUT | /api/v1/organizations/{organizationId} | OrganizationController.update |
| DELETE | /api/v1/organizations/{organizationId}?rowVersion=N | OrganizationController.delete |
| POST | /api/v1/organizations/{organizationId}/health-examination-batches | OrganizationBatchController.create |
| GET | /api/v1/organizations/{organizationId}/health-examination-batches | OrganizationBatchController.list |
| GET | /api/v1/organizations/{organizationId}/health-examination-batches/{batchId} | OrganizationBatchController.get |
| PUT | /api/v1/organizations/{organizationId}/health-examination-batches/{batchId} | OrganizationBatchController.update |
| DELETE | /api/v1/organizations/{organizationId}/health-examination-batches/{batchId}?rowVersion=N | OrganizationBatchController.delete |
| GET | /api/v1/catalog/services | ServiceCatalogController.list |
| GET | /api/v1/auth/csrf | AuthController.csrf |
| POST | /api/v1/auth/login | AuthController.login |
| GET | /api/v1/auth/me | AuthController.me |
| POST | /api/v1/auth/logout | AuthController.logout |
| POST | /api/v1/auth/logout-all | AuthController.logoutAll |

Source: [OrganizationController](../../src/main/java/com/ngockhanh/clinic/healthexamination/api/controller/OrganizationController.java),
[OrganizationBatchController](../../src/main/java/com/ngockhanh/clinic/healthexamination/api/controller/OrganizationBatchController.java),
[ServiceCatalogController](../../src/main/java/com/ngockhanh/clinic/catalog/api/controller/ServiceCatalogController.java),
[AuthController](../../src/main/java/com/ngockhanh/clinic/identity/api/controller/AuthController.java).

There is no participant list/add/check-in or print HTTP handler in this
checkout. The only catalog handler is the read-only service list below. In particular, `GET .../health-examination-batches/{batchId}/participants` and
`GET .../health-examination-batches/{batchId}/participant` are unsupported.
A frontend route/API function is not evidence that these handlers exist.

### Batch contract

All batch routes use the organization in the path and the authenticated
`UserPrincipal` account as the actor (no body or configuration actor). Local/test
require a STAFF principal with an active role and CSRF on POST/PUT/DELETE; production
still denies every business endpoint. Responses use the common envelope; a successful
DELETE is an empty 204.

`POST .../health-examination-batches` creates a `DRAFT` batch and returns 201 with the
batch (`BatchDetailResponse`) and a `Location` header for `.../{batchId}`. Body:
`batchCode` (unique across all organizations and all time, max 50), `batchName`,
`examinationDates` (at least one distinct date), `examinationSiteType` (`CLINIC` or
`ORGANIZATION_SITE`), `examinationSiteName`, `examinationSiteAddress` and `services`
(at least one distinct `serviceId` with `negotiatedPrice`, 0 or more, at most 12
integer and 2 fraction digits). The reference price is copied from the catalog; the
client never sends it. Past dates are allowed. Unknown organization is 404; an
`INACTIVE` organization, or an inactive/unknown catalog service, is 409; a duplicate
`batchCode` is 409; any other invalid input is 400. Audit action
`CREATE_HEALTH_EXAMINATION_BATCH`.

`GET .../{batchId}` returns days, services, `status` and `rowVersion`. Every service
carries read-only `serviceCode` and `serviceName` taken from the catalog for display
(`null` if the catalog row is missing); `referencePriceSnapshot` and `negotiatedPrice`
are always the stored values, never the catalog's current price. Create and update
return the same shape. The batch must
belong to the organization in the path and not be deleted (otherwise 404). The
organization itself may be `INACTIVE`.

`PUT .../{batchId}` replaces the whole configuration (same fields as create plus a
required `rowVersion`, not negative) and returns the updated batch. Only a `DRAFT`
batch can be updated; any other status is 409. A stale `rowVersion` is 409. A day or
service that participant data still references cannot be removed (409); retained days
and services keep their ids, services keep their reference price snapshot, and the
display order follows the order of the request. Newly added services must be active in
the catalog and snapshot its current price. Audit action `UPDATE_HEALTH_EXAMINATION_BATCH`
with before and after configuration.

`GET .../health-examination-batches` accepts `page`, `size`, `searchKey` (code or name,
case-insensitive contains, `%`, `_` and `\` literal, max 100), `sortKey` (`id` default,
`batchCode`, `batchName`, `startDate`, `status`, `createdAt`) and `sortBy`
(`ASC` default, `DESC`); every sort uses the id as tie-breaker. Each item carries `id`, `batchCode`,
`batchName`, `startDate`, `endDate`, `status`, `createdAt`, `updatedAt` and `rowVersion`
(the version a `DELETE`/`PUT` of that batch expects). Deleted batches are not
listed. A page past the end returns empty items with the totals.

`DELETE .../{batchId}?rowVersion=N` is a soft delete (`deleted_at`, V002) and returns
204. It is allowed only for a `DRAFT` batch that has no Participant (any roster status)
and no import history; otherwise 409. The `rowVersion` query parameter is required and
not negative (400 otherwise); a stale value is 409. The batch, its days and services
are kept and its `batchCode` stays reserved. A deleted or unknown batch is 404 for get,
update and delete (a repeated delete is 404, not a no-op). Audit action
`DELETE_HEALTH_EXAMINATION_BATCH`.

### Catalog service list contract

`GET /api/v1/catalog/services` returns `ApiResponse<PageResponse<ServiceCatalogItemResponse>>`
with HTTP 200 and is read-only. Items: `id`, `code`, `name`, `serviceType`
(`CONSULTATION`, `LAB`, `ULTRASOUND`, `XRAY`, `ECG`, `OTHER`), `unitPrice` and `active`.
Only `active` services are listed, because only those can be newly selected for a batch.
Query parameters: `page` (default 1, min 1), `size` (default 10, 1-100), `searchKey`
(optional, max 100, trimmed; case-insensitive contains match on code/name, `%`, `_` and
`\` are literal), `sortKey` (`id` default, `code`, `name`, `unitPrice`) and `sortBy`
(`ASC` default, `DESC`); every sort uses the id as tie-breaker. A page past the end returns
empty items with the totals. It falls under the existing `/api/v1/**` business access policy
(STAFF with an active role locally; denied in production) and needs no CSRF token.
Owner approved this contract on 2026-10-06.

Local data: the `local` profile also loads `db/local/R__local_catalog_seed.sql` (two departments,
five active services, idempotent, no accounts) so the catalog is not empty during development.
Smoke check once the app runs with an authenticated STAFF cookie:
`curl -b cookies.txt "http://localhost:8080/api/v1/catalog/services?page=1&size=100&sortKey=code"`.

### Organization list and delete contract

`GET /api/v1/organizations` returns `ApiResponse<PageResponse<OrganizationResponse>>`
with HTTP 200. Query parameters: `page` (default 1, min 1), `size` (default 10, 1-100),
`searchKey` (optional, max 100, trimmed; case-insensitive contains match on taxCode/name,
`%`, `_` and `\` are literal), `sortKey` (`id` default, `taxCode`, `name`) and `sortBy`
(`ASC` default, `DESC`). Only `ACTIVE` organizations are listed; there is no status or
type filter. TaxCode/name sorts use the id as tie-breaker in the same direction. A page
past the end returns empty items with the filtered totals.

`GET /api/v1/organizations/{organizationId}` returns only `ACTIVE` organizations; an
`INACTIVE` organization is reported as 404.

`DELETE /api/v1/organizations/{organizationId}?rowVersion=N` deactivates the
organization (`ACTIVE` to `INACTIVE`); the row and its batches are kept. `rowVersion` is
required and not negative (400 otherwise). Success is an empty 204. Unknown ID is 404 and
a stale `rowVersion` is 409. Repeating the call with the current version against an
already `INACTIVE` organization is a 204 no-op (no version bump, no audit). The audit
action is `DEACTIVATE_ORGANIZATION`. Existing batches are not changed or blocked.

## DTO and lifecycle boundaries

Inspect linked controller request/application response types before an integration
change; do not infer fields from UI models. Organization carries an optional unique
tax code, general contacts and one named contact; update carries expected rowVersion.
Batch create and update supply concrete days and configured service prices; its
displayed date bounds derive from those days.

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
