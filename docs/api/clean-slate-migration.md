# Current API inventory and migration compatibility

Updated 2026-10-16 from current controllers and accepted contracts. A controller
mapping does not guarantee authorization: production also requires a matching
`EndpointPermissions` rule and the applicable application policy.

## Current routes

All paths below start with `/api/v1`. `O` means
`/organizations/{organizationId}` and `B` means
`O/agreements/{agreementId}`, and `S` means `O/sessions/{sessionId}`.

| Module | Mapped routes | Contract |
|---|---|---|
| accesscontrol | `POST /auth/login`, `POST /auth/logout`, `GET /auth/me` | [Login](login.md) |
| accesscontrol | `GET/POST /staff-members`, `GET /roles` | [Staff members and roles](staff-members.md); POST creates the staff member and its STAFF account, permissions `STAFF_MEMBER_VIEW`, `USER_ACCOUNT_CREATE` (+ `USER_ROLE_ASSIGN`), `ROLE_VIEW` |
| accesscontrol | `GET/POST /admin/roles`, `PUT /admin/roles/{roleId}`, `GET/PUT /admin/roles/{roleId}/permissions`, `GET /admin/permissions` | [Roles and permissions](roles-and-permissions.md); permissions `ROLE_MANAGE`, `ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_PERMISSION_VIEW`, `ROLE_PERMISSION_ASSIGN`, `PERMISSION_VIEW` |
| catalog | `GET /catalog/services` | Active service lookup; pagination, code/name search and allowlisted sorting |
| catalog | `GET/POST /catalog/management/{resource}`, `GET/PUT/DELETE /catalog/management/{resource}/{id}`, `POST /catalog/management/{resource}/{id}/reactivate` | [Catalog management](catalog-management.md); five static controllers for departments, rooms, specialties, services and medicines; DELETE deactivates with expected rowVersion |
| healthexamination | `GET/POST /organizations`, `GET/PUT/DELETE O` | [Organization contract](organizations-and-agreements.md#organization); DELETE handler deactivates with expected `rowVersion` |
| healthexamination | `GET/POST O/agreements`, `GET/PUT/DELETE B`, `POST B/activate`, `POST B/end` | [Agreement contract](organizations-and-agreements.md#health-examination-agreement); PUT replaces draft configuration; DELETE handler soft-deletes with expected `rowVersion`; activate moves DRAFT to ACTIVE (`HEALTH_EXAMINATION_AGREEMENT_ACTIVATE`), end moves ACTIVE to ENDED; services change only while DRAFT (through PUT) and are fixed once ACTIVE |
| healthexamination | `GET/POST O/sessions`, `GET/PUT S`, `POST S/cancel` | [Session contract](sessions-and-assignments.md#session) |
| healthexamination | `GET/POST S/assignments`, `POST S/assignments/move`, `POST S/assignments/{assignmentId}/move`, `POST S/assignments/{assignmentId}/cancel`, `POST S/assignments/{assignmentId}/attendance` | [Assignment contract](sessions-and-assignments.md#assignment) |
| healthexamination | `GET B/participants`, `GET B/participants/import-template`, `POST B/participants/imports` | [Participant import and list](participant-import-and-list.md) |
| healthexamination | `POST B/participants`, `GET/PUT/DELETE B/participants/{participantId}`, `POST B/participants/{participantId}/reactivate` | [Participant manual changes](participant-manual-crud.md) |
| healthexamination | `GET B/examination-details`, `GET B/examination-details/summary`, `GET B/examination-details/export`, `POST B/examination-details/imports` | [Examination details](examination-details-and-report.md) |
| healthexamination | `GET B/reports/payment-summary`, `GET B/reports/payment-summary/docx` | [Payment report](examination-details-and-report.md) |
| healthexamination | `GET B/participants/{participantId}/examination-form` | [Examination form print](examination-form-print.md); inline PDF, permission `PARTICIPANT_EXAMINATION_FORM_PRINT` |
| healthexamination | `POST B/participants/examination-form-print-jobs` | [Examination form print jobs](examination-form-print-jobs.md); 202, permission `PARTICIPANT_EXAMINATION_FORM_BULK_PRINT` |
| document | `GET /document-render-jobs/{jobId}`, `GET /document-render-jobs/{jobId}/parts/{partNo}` | [Examination form print jobs](examination-form-print-jobs.md); creator only, permissions `DOCUMENT_RENDER_JOB_READ` and `DOCUMENT_RENDER_JOB_DOWNLOAD` |

V002 stores a method/route/permission mapping for 83 operations, including
`PARTICIPANT_EXAMINATION_FORM_PRINT`, the three bulk-print permissions (bulk print, job read,
job download) and `HEALTH_EXAMINATION_AGREEMENT_ACTIVATE`, together with the three staff member/role routes, the six role administration routes, the 30 catalog
management bindings (CLINIC_MANAGER, the actor of UC-110–UC-115) the agreement end route and the
session and assignment permissions (83 bound routes in total), under
[ADR-0015](../adr/0015-endpoint-permission-rbac.md), including `ORGANIZATION_DELETE`,
`HEALTH_EXAMINATION_AGREEMENT_DELETE` and `SERVICE_CATALOG_VIEW`. Each requires a STAFF
account with its endpoint permission. The local TEST role uses
the same checks, with all permissions granted by the local seed.
[Open authorization items](../architecture/07-open-items.md#endpoint-authorization)
records the remaining checks for direct application callers.

## Client compatibility

Organization uses UUID `id` and optional unique `taxCode`; it has no `code`,
`organizationType` or `contactPosition`. General phone is optional. See
[Organization identity](../adr/0016-organization-tax-code-identity.md).

Agreement requests carry the optional validity period and the services, with `rowVersion` for updates; dates and sites belong to sessions ([ADR-0019](../adr/0019-agreement-session-assignment.md)).
The backend generates `agreementCode` as `KSK-<year>-<running number>` and preserves
it on update. Participant codes are also generated and immutable through roster
editing. Participant detail and import support the optional personal fields
documented in their linked contracts.

REST JSON responses use `ApiResponse` and the documented pagination/error shape
in [PROJECT_RULES](../../PROJECT_RULES.md#envelope-errors-and-pagination).
Downloads return file bytes; authorized Organization/Agreement DELETE requests return 204.

The batch routes `/health-examination-batches/{batchId}/...` and the `*_BATCH_*` permission codes no longer exist; clients use the agreement routes and codes above. No current handlers expose visit preparation,
official record issuance, clinical ordering/results, billing payments, prescription
issuance, portal releases or document-template management
(only the starter template is seeded; there is no upload/publish API). Their schema/domain
concepts do not imply supported client routes.

Document-template editing/upload/publish is excluded from product scope by
[ADR-0018](../adr/0018-it-managed-document-templates.md). IT maintains versions
through reviewed releases/migrations after business/clinical content approval;
this is not a pending client API. Excel roster and reconciliation imports remain
supported under their own contracts.

## Removed Excel roster import

The former upload/preview/confirm/cancel workflow has no supported handlers.
The replacement is one `POST B/participants/imports`, with template download
and list, as specified in [Participant import and list](participant-import-and-list.md).
Historical import jobs, rows and provenance remain available to their owners.
The single-step service-reconciliation import is a separate workflow.

## Database compatibility

Fresh databases apply one final schema initializer V001 and data-only seeds
V002/V003; staff administration, role administration, catalog management and the agreement, session
and assignment model are part of them (consolidation of 2026-10-16). Earlier migration
histories cannot use the rewritten baseline as an
in-place upgrade. See [deployment](../architecture/06-testing-and-operations.md#deployment)
for conversion requirements. The current baseline also stores endpoint mappings
and separate detail/summary/export permissions in V002, as defined by ADR-0015.
