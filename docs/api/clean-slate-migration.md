# Clean-slate API migration

The owner-selected contract is [ADR-0013](../adr/0013-clean-slate-application-contract.md).
These are breaking changes to the existing application APIs. All routes remain
under `/api/v1`; existing authentication, CSRF and backend access checks apply.
This document describes implemented migration paths, not APIs for every schema table.

## Account sessions

Login and `/auth/me` return `accountId`, `staffMemberId`, `patientId`, `username`,
`accountType`, `roleAssignments`, `idleExpiresAt` and `absoluteExpiresAt`.
Assignments contain `roleId`, `roleCode`, `permissions`, `grantedBy`, `grantedAt`.
The old assignment ID, location scope and validity-window fields are removed.
Cookie/CSRF transport and `/auth/login`, `/auth/logout`, `/auth/logout-all` routes
follow [login operations](login.md).

## Organizations

`POST /organizations` and `PUT /organizations/{organizationId}` use:

```json
{
  "code": "ORG-001",
  "name": "Organization name",
  "organizationType": "COMPANY",
  "taxCode": null,
  "phone": "0900000000",
  "email": "office@example.invalid",
  "address": "Organization address",
  "contactFullName": "Contact name",
  "contactPosition": null,
  "contactPhone": "0900000001",
  "contactEmail": "contact@example.invalid",
  "rowVersion": 0
}
```

Types are `COMPANY`, `SCHOOL`, `GOVERNMENT`, `OTHER`. `code` is unique; optional
tax code is not a unique organization identifier. `rowVersion` is required for
PUT and comes from the last response. Organization/contact channels are separate.
Responses include these fields, status, timestamps and the current version.

## Batch configuration

Base: `/organizations/{organizationId}/health-examination-batches`.
POST creates a batch and its days/services atomically; PUT `/{batchId}` updates
a draft using the expected `rowVersion`.

```json
{
  "batchCode": "BATCH-001",
  "batchName": "Campaign name",
  "examinationDates": ["2026-10-05", "2026-10-06"],
  "examinationSiteType": "ORGANIZATION_SITE",
  "examinationSiteName": "Examination site",
  "examinationSiteAddress": "Site address",
  "services": [{"serviceId": "<uuid>", "negotiatedPrice": 100000.00}],
  "rowVersion": 0
}
```

At least one unique date and service are required. Site is `CLINIC` or
`ORGANIZATION_SITE`. The server captures reference price from current service
catalog when adding a service. Amounts fit numeric(14,2). Retained day/service
IDs and reference snapshots are preserved; referenced days/services cannot be removed.

Detail responses include `days` (`id`, `examinationDate`), derived `startDate`/
`endDate`, header `rowVersion`, and each service's `referencePriceSnapshot`,
`negotiatedPrice`, `displayOrder`, `active`, `rowVersion`. Batch lifecycle is
`DRAFT`, `READY`, `FINALIZED`, `CLOSED`. The old DELETE batch endpoint and
`reason`, `payerType`, batch template association and entered date range are removed.
Normal price edits preserve previously recorded performed-service snapshots.

## Participant roster and import

All paths below are relative to the batch base plus `/{batchId}`:

| Method | Path | Contract |
|---|---|---|
| GET | `/participants` | Authorized, bounded batch roster query. |
| GET | `/participants/import-template` | Standard XLSX roster template. |
| POST | `/participant-imports` | Multipart `file` and JSON `configuration.selectedBatchDayIds`; validates the complete file before staging. |
| GET | `/participant-imports/{importId}` | Current job summary/version. |
| GET | `/participant-imports/{importId}/rows` | Paginated preview rows with masked CCCD. |
| PUT | `/participant-imports/{importId}/preview` | JSON `selectedBatchDayIds`, required `expectedRowVersion`, optional `rowAssignments` keyed by source row number. |
| POST | `/participant-imports/{importId}/confirm` | JSON `expectedRowVersion`; commits the approved preview atomically. |
| DELETE | `/participant-imports/{importId}` | JSON `expectedRowVersion`; cancels a validated job. |

The old `/employee-imports`, `/employees/import-template`, singular `/participant`
and column-mapping action paths are removed.

The template columns are STT, Mã nhân viên, Họ và tên, Ngày sinh, Giới tính, CCCD,
Điện thoại, Email, Phòng ban, Chức danh. Name, birth date, sex, CCCD, department
and position are required; participant code, phone and email are optional.
CCCD remains text. Do not substitute the previous 16-column workbook.

A valid upload returns 201 with import ID, `VALIDATED`, row version, selected days
and preview rows. An invalid roster returns 422 with row errors and `importId = null`;
no job, staging, Patient or Encounter rows are persisted. Invalid workbook structure
uses the safe validation error contract. Duplicate CCCD within file or batch rejects
the complete file; import does not update existing participants.

New rows are allocated by lowest active count, then date, then day ID. Preview may
revise assignments. Confirmation uses approved assignments without reallocating,
checks the job version and stores its result. Retry of a confirmed job returns that
result rather than inserting again. Existing roster members never move during import.
Job states are `VALIDATED`, `CONFIRMED`, `CANCELLED`, `EXPIRED`.

Use the newest response version on subsequent writes. Stale versions return the
centralized concurrency conflict, with no partial business rows or audit commits.

## Database deployment boundary

These contracts require the clean-slate V001 on a fresh PostgreSQL 18 database.
The former V001–V003 database is incompatible; this work contains no production
data-conversion procedure. No extra Flyway migration is needed for Java/API mapping
to the already supplied clean-slate schema.
