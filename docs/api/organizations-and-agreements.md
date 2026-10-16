# Organization and health-examination agreement API

Reviewed against the current controllers/requests and stored permissions on 2026-10-16. Owning module:
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
Deletion means deactivation, retaining the row and agreement history. The current
update use case can edit an inactive Organization; it does not reactivate it.
Writes and required audit share a transaction. Duplicate non-null tax code is 409.

## Health examination agreement

Base path: `/api/v1/organizations/{organizationId}/agreements`. Decision:
[ADR-0019](../adr/0019-agreement-session-assignment.md). The agreement holds what was agreed with
the customer: services and prices, and an optional validity period. Dates and sites belong to
[sessions](sessions-and-assignments.md); who came is derived from assignments.

| Method/path | Success | Required STAFF permission |
|---|---|---|
| GET collection | 200 page of agreement summaries | `HEALTH_EXAMINATION_AGREEMENT_VIEW` |
| GET `/{agreementId}` | 200 agreement detail | `HEALTH_EXAMINATION_AGREEMENT_DETAIL_VIEW` |
| POST collection | 201 agreement detail | `HEALTH_EXAMINATION_AGREEMENT_CREATE` |
| PUT `/{agreementId}` | 200 agreement detail | `HEALTH_EXAMINATION_AGREEMENT_UPDATE` |
| DELETE `/{agreementId}?rowVersion={n}` | 204 | `HEALTH_EXAMINATION_AGREEMENT_DELETE` |
| POST `/{agreementId}/activate` body `{"rowVersion": n}` | 200 detail (ACTIVE) | `HEALTH_EXAMINATION_AGREEMENT_ACTIVATE` |
| POST `/{agreementId}/end` body `{"rowVersion": n}` | 200 detail (ENDED) | `HEALTH_EXAMINATION_AGREEMENT_END` |

Create/PUT fields:

| Field | Rule |
|---|---|
| `agreementName` | Required, max 300 |
| `validFrom`, `validTo` | Optional ISO dates (`yyyy-MM-dd`); `validFrom` must not be after `validTo` |
| `services[]` | Nonempty list; each item has a unique catalog `serviceId` and a required `negotiatedPrice` |
| `services[].negotiatedPrice` | Nonnegative VND decimal, max 12 integer and 2 fractional digits |
| `rowVersion` | PUT only, required and nonnegative |

A rejected body returns 400 and names each field: `message` lists the fields and `data`
carries `{field, code, message}` per field (see the envelope rules in `PROJECT_RULES.md`).

The backend creates a DRAFT agreement, allocates the service line IDs and display order,
captures catalog reference prices and generates `agreementCode` (for example `KSK-2026-001`).
The code is absent from request DTOs and fixed on update. See
[agreement code and lifecycle rules](../architecture/03-domain-and-workflows.md#organization-and-agreement).

PUT is permitted only in DRAFT. Retained service lines keep their IDs and price snapshots;
a line with recorded services cannot be removed. Header, lines and audit are atomic.

**Lifecycle.** DRAFT → ACTIVE (activate) → ENDED (end). The organization must be ACTIVE to
activate; ending is allowed for an inactive organization. A stale version, a wrong status or
an inactive organization is 409; a missing or deleted agreement is 404. DRAFT and ACTIVE
agreements accept Participant changes, session assignments, service reconciliation and
printing (printing needs ACTIVE or ENDED: [one form](examination-form-print.md),
[print jobs](examination-form-print-jobs.md)). An ENDED agreement is read-only history.

**Service lines.** The services and their prices are changed only while the agreement is DRAFT,
through PUT; once it is ACTIVE they are fixed, and there is no endpoint to add, retire or
reprice a line. A service has at most one active line. A line retired before this rule keeps
its place in the list and its historical price, is not offered again and appears in reports only
when something was performed with it.

Detail contains `id`, `organizationId`, `agreementCode`, `agreementName`, `validFrom`,
`validTo`, `status`, `createdBy`, `createdAt`, `updatedAt`, `rowVersion` and `services[]` (`id`,
`serviceId`, `serviceCode`, `serviceName`, `referencePriceSnapshot`, `negotiatedPrice`,
`displayOrder`, `active`, `rowVersion`). List returns summaries without services. GET
collection accepts `page`, `size`, `searchKey`, `sortKey` (`id`, `agreementCode`,
`agreementName`, `validFrom`, `status`, `createdAt`) and `sortBy`.

Deletion means soft deletion (`deleted_at`), keeping the agreement, lines and code. Only a DRAFT
agreement without Participants, sessions or import history qualifies. Soft-deleted agreements
are absent from later reads and writes; a mismatched organization/agreement pair is 404.

## Authorization limits

V002 grants the mapped Organization permissions to CLINIC_MANAGER and V007 grants the agreement,
session and assignment permissions to the same role. The local seed additionally grants every
permission to TEST; it uses the same endpoint checks. See
[Open items](../architecture/07-open-items.md#endpoint-authorization) before using a mapped
handler as evidence that an operation is production-ready.
