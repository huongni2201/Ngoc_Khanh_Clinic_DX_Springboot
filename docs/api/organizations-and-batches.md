# Organization and health-examination batch API

Reviewed against the current controllers/requests and stored permissions on 2026-10-09. Owning module:
`healthexamination`. Responses use [the common envelope and pagination](../../PROJECT_RULES.md#envelope-errors-and-pagination).
All route IDs are UUIDs. PUT replaces the editable fields and requires the last
read `rowVersion` (integer >= 0); stale versions return 409.

## Organization

Base path: `/api/v1/organizations`.

| Method/path | Success | Required STAFF permission |
|---|---|---|
| GET collection | 200 page of Organizations | `ORGANIZATION_SEARCH` |
| GET `/{organizationId}` | 200 Organization | `ORGANIZATION_VIEW` |
| POST collection | 201 Organization | `ORGANIZATION_CREATE` |
| PUT `/{organizationId}` | 200 Organization | `ORGANIZATION_UPDATE` |
| DELETE `/{organizationId}?rowVersion={n}` | 204 | `ORGANIZATION_DELETE` |

Create/PUT fields:

| Field | Rule |
|---|---|
| `name` | Required, max 300 characters |
| `taxCode` | Optional, max 50; each non-null value is unique |
| `phone` | Optional general contact number |
| `email`, `address` | Required; email must be valid |
| `contactFullName` | Required, max 200 |
| `contactPhone`, `contactEmail` | Required named-contact channels; email must be valid |
| `rowVersion` | PUT only, required and nonnegative |

Text is trimmed; blank optional tax code/phone becomes null. No `code`,
`organizationType` or `contactPosition` field exists. Responses contain `id`,
the contact fields above, `status` and `rowVersion`.

GET collection accepts `page`, `size`, `searchKey`, `sortKey` (`id`, `taxCode`,
`name`) and `sortBy` (`ASC`/`DESC`). List and detail hide INACTIVE Organizations.
Deletion means deactivation, retaining the row and batch history. The current
update use case can edit an inactive Organization; it does not reactivate it.
Writes and required audit share a transaction. Duplicate non-null tax code is 409.

## Health examination batch

Base path: `/api/v1/organizations/{organizationId}/health-examination-batches`.

| Method/path | Success | Required STAFF permission |
|---|---|---|
| GET collection | 200 page of batch summaries | `HEALTH_EXAMINATION_BATCH_VIEW` |
| GET `/{batchId}` | 200 batch detail | `HEALTH_EXAMINATION_BATCH_DETAIL_VIEW` |
| POST collection | 201 batch detail | `HEALTH_EXAMINATION_BATCH_CREATE` |
| PUT `/{batchId}` | 200 batch detail | `HEALTH_EXAMINATION_BATCH_UPDATE` |
| DELETE `/{batchId}?rowVersion={n}` | 204 | `HEALTH_EXAMINATION_BATCH_DELETE` |
| POST `/{batchId}/start` body `{"rowVersion": n}` | 200 batch detail (READY) | `HEALTH_EXAMINATION_BATCH_START` (V007) |

Create/PUT fields:

| Field | Rule |
|---|---|
| `batchName` | Required, max 300 |
| `examinationDates[]` | Nonempty list of distinct ISO dates (`yyyy-MM-dd`) |
| `examinationSiteType` | `CLINIC` or `ORGANIZATION_SITE` |
| `examinationSiteAddress` | Required and non-blank for `ORGANIZATION_SITE`; empty for `CLINIC` |
| `services[]` | Nonempty list; each item has a unique catalog `serviceId` and required `negotiatedPrice` |

A rejected body returns 400 and names each field: `message` lists the fields (for
example `Invalid request: examinationSiteName`) and `data` carries `{field, code, message}`
per field (see the envelope rules in `PROJECT_RULES.md`). A required field the client does
not send is reported the same way; when the client sends every field of this table, that
report usually means the running backend predates this contract, so restart it from the
current branch.
| `services[].negotiatedPrice` | Nonnegative VND decimal, max 12 integer and 2 fractional digits |
| `rowVersion` | PUT only, required and nonnegative |

The backend creates a DRAFT batch, allocates day/service IDs and display order,
captures catalog reference prices and generates `batchCode`. The code is absent
from request DTOs and remains fixed on update. Dates derive `startDate`/`endDate`;
they are not separate input fields. See [batch code and lifecycle rules](../architecture/03-domain-and-workflows.md#organization-and-batch).

PUT is permitted only in DRAFT. Retained days/services keep their IDs and price
snapshots; referenced days/services cannot be removed. Header, days, services and
audit changes are atomic. A newly selected service must be available in catalog.
Batch configuration does not reprice historical performed-service rows.

Detail contains `id`, `organizationId`, `batchCode`, `batchName`, `days[]`
(`id`, `examinationDate`), date bounds, the site fields, `status`, `createdBy`,
`createdAt`, `updatedAt`, `rowVersion` and `services[]` (`id`, `serviceId`,
`serviceCode`, `serviceName`, `referencePriceSnapshot`, `negotiatedPrice`,
`displayOrder`, `active`, `rowVersion`). List returns summaries without days/services.
GET collection accepts `page`, `size`, `searchKey`, `sortKey` (`id`, `batchCode`,
`batchName`, `startDate`, `status`, `createdAt`) and `sortBy`.

Deletion means soft deletion (`deleted_at`), retaining the batch, days, services
and code. Only a DRAFT batch without any Participant or import history qualifies.
Soft-deleted batches are absent from subsequent reads/writes; a mismatched
Organization/Batch pair or missing resource is 404.

Start (UC-071) moves a DRAFT batch of an ACTIVE organization to READY with the
expected `rowVersion`; the header version is incremented once and
`START_HEALTH_EXAMINATION_BATCH` is audited in the same transaction. A stale
version, a batch that is not a draft or an inactive organization is 409; a
missing or deleted batch is 404. A READY batch can no longer be updated or
deleted; Participants can still be added, edited, cancelled and imported, and
examination forms can be printed ([one form](examination-form-print.md),
[print jobs](examination-form-print-jobs.md)). There is no current endpoint to
move a batch to FINALIZED or CLOSED.

## Authorization limits

V002 grants the mapped Organization/Batch permissions above to CLINIC_MANAGER.
The local seed additionally grants every permission to TEST and ADMINISTRATOR;
these roles use the same endpoint checks. Current Organization/Batch use cases
still lack equivalent principal-based permission checks for direct callers.
See [Open items](../architecture/07-open-items.md#endpoint-authorization) before
using a mapped handler as evidence that an operation is production-ready.
