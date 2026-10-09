# Current API inventory and migration compatibility

Updated 2026-10-08 from current controllers and accepted contracts. A controller
mapping does not guarantee authorization: production also requires a matching
`EndpointPermissions` rule and the applicable application policy.

## Current routes

All paths below start with `/api/v1`. `O` means
`/organizations/{organizationId}` and `B` means
`O/health-examination-batches/{batchId}`.

| Module | Mapped routes | Contract |
|---|---|---|
| accesscontrol | `POST /auth/login`, `POST /auth/logout`, `GET /auth/me` | [Login](login.md) |
| catalog | `GET /catalog/services` | Active service lookup; pagination, code/name search and allowlisted sorting |
| healthexamination | `GET/POST /organizations`, `GET/PUT/DELETE O` | [Organization contract](organizations-and-batches.md#organization); DELETE handler deactivates with expected `rowVersion` |
| healthexamination | `GET/POST O/health-examination-batches`, `GET/PUT/DELETE B` | [Batch contract](organizations-and-batches.md#health-examination-batch); PUT replaces draft configuration; DELETE handler soft-deletes with expected `rowVersion` |
| healthexamination | `GET B/participants`, `GET B/participants/import-template`, `POST B/participants/imports` | [Participant import and list](participant-import-and-list.md) |
| healthexamination | `POST B/participants`, `GET/PUT/DELETE B/participants/{participantId}`, `POST B/participants/{participantId}/reactivate` | [Participant manual changes](participant-manual-crud.md) |
| healthexamination | `GET B/examination-details`, `GET B/examination-details/summary`, `GET B/examination-details/export`, `POST B/examination-details/imports` | [Examination details](examination-details-and-report.md) |
| healthexamination | `GET B/reports/payment-summary`, `GET B/reports/payment-summary/docx` | [Payment report](examination-details-and-report.md) |

The current permission list has no explicit rules for Organization DELETE,
Batch DELETE or catalog service lookup. These mapped operations remain denied
under normal production authorization; the local TEST bypass is described in
[API/security](../architecture/05-api-and-security.md#authentication-and-authorization).
The [open authorization items](../architecture/07-open-items.md#endpoint-authorization)
tracks the gaps; this inventory does not grant new permissions.

## Client compatibility

Organization uses UUID `id` and optional unique `taxCode`; it has no `code`,
`organizationType` or `contactPosition`. General phone is optional. See
[Organization identity](../adr/0016-organization-tax-code-identity.md).

Batch requests carry dates, site and services, with `rowVersion` for updates.
The backend generates `batchCode` as `KSK-<year>-<running number>` and preserves
it on update. Participant codes are also generated and immutable through roster
editing. Participant detail and import support the optional personal fields
documented in their linked contracts.

REST JSON responses use `ApiResponse` and the documented pagination/error shape
in [PROJECT_RULES](../../PROJECT_RULES.md#envelope-errors-and-pagination).
Downloads return file bytes; successful DELETE handlers return 204. The two
Organization/Batch DELETE routes remain denied by normal HTTP authorization.

No current handlers expose batch lifecycle transitions, visit preparation,
official record issuance, clinical ordering/results, billing payments, prescription
issuance, portal releases or document-template management. Their schema/domain
concepts do not imply supported client routes.

## Removed Excel roster import

The former upload/preview/confirm/cancel workflow has no supported handlers.
The replacement is one `POST B/participants/imports`, with template download
and list, as specified in [Participant import and list](participant-import-and-list.md).
Historical import jobs, rows and provenance remain available to their owners.
The single-step service-reconciliation import is a separate workflow.

## Database compatibility

Fresh databases apply one final schema initializer V001 and data-only seeds
V002/V003. Earlier migration histories cannot use the rewritten baseline as an
in-place upgrade. See [deployment](../architecture/06-testing-and-operations.md#deployment)
for conversion requirements. This consolidation changes migration packaging,
not public routes or business permissions.
