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

`GET /organizations/{organizationId}` retrieves one organization. `POST /organizations`
creates one, and `PUT /organizations/{organizationId}` updates it with the expected
`rowVersion`. The update request uses this shape; the create request omits
`rowVersion`.

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
GET returns a bounded, paginated list scoped to the organization. POST creates
a batch and its initial days/services atomically.

```json
{
  "batchCode": "BATCH-001",
  "batchName": "Campaign name",
  "examinationDates": ["2026-10-05", "2026-10-06"],
  "examinationSiteType": "ORGANIZATION_SITE",
  "examinationSiteName": "Examination site",
  "examinationSiteAddress": "Site address",
  "services": [{"serviceId": "<uuid>", "negotiatedPrice": 100000.00}]
}
```

At least one unique date and service are required. Site is `CLINIC` or
`ORGANIZATION_SITE`. The server captures the current catalog reference price;
amounts fit `numeric(14,2)`. The create response contains the initial days,
derived date range, status and batch version, plus each service's reference-price
snapshot, negotiated price, display order, active flag and version. Batch status
values are `DRAFT`, `READY`, `FINALIZED` and `CLOSED`.

## Removed Excel roster import

At the owner's request on 2026-10-05, the backend Excel roster import workflow
has been removed. The following batch-scoped routes are no longer registered:

- `GET /participants/export-template`;
- `POST /participant-imports`;
- `GET /participant-imports/{importId}`;
- `GET /participant-imports/{importId}/rows`;
- `PUT /participant-imports/{importId}/preview`;
- `POST /participant-imports/{importId}/confirm`;
- `POST /participant-imports/{importId}/cancel`.

Template generation, workbook upload/parsing, preview allocation, confirmation,
cancellation and their staging adapters are removed. No replacement roster API
is introduced. Frontend integration is outside this backend-only change and
existing clients must stop calling these routes.

The schema and historical import/participant provenance remain intact. No Flyway
migration or deployed data deletion is part of this removal.

## Database deployment boundary

These contracts require the clean-slate V001 on a fresh PostgreSQL 18 database.
The former V001–V003 database is incompatible; this work contains no production
data-conversion procedure. No extra Flyway migration is needed for Java/API mapping
to the already supplied clean-slate schema.
