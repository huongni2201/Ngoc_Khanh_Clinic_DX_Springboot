# ADR-0015: Per-endpoint permissions from the SRS Permission Matrix

## Status

Accepted — 2026-10-07. Establishes per-endpoint HTTP RBAC for
[ADR-0014](0014-session-cookie-redis-login.md). Remaining application authorization
gaps are tracked in [Open items](../architecture/07-open-items.md#endpoint-authorization).

## Context

ADR-0014 let any signed-in STAFF account call every `/api/v1/**` route. Sessions
already carry `ACCOUNT_<type>`, `ROLE_<roleCode>` and `PERM_<permissionCode>`
authorities, but `roles`, `permissions` and `role_permissions` were empty. The
SRS Permission Matrix 4.4, aligned with the use-case table 4.2, defines which
actor may perform each action.

## Decision

### Seed

- Owner amendment — 2026-10-07: the fresh-database migrations are consolidated
  into schema V001 and seed V002. `V002__seed_roles_and_permissions.sql`
  seeds the whole matrix:
  seven roles (`PATIENT`, `RECEPTIONIST`, `GENERAL_PRACTITIONER`,
  `DIAGNOSTIC_DOCTOR`, `NURSE`, `CLINIC_MANAGER`, `ADMINISTRATOR`),
  one permission per matrix row and one `role_permissions` row per non-"No" cell.
  Owner amendment — 2026-10-15: the former `DATA_ENTRY_STAFF` (Data Entry Staff) role is
  renamed `NURSE` (Nurse) in V002 itself, with the same grants; databases created from the
  earlier V002 must be recreated.
- Permission codes are `<ENTITY>_<ACTION>`; patient-portal codes start with
  `OWN_` and are granted only to `PATIENT`.
- The sign-in and password rows have no permission: every authenticated account
  may use them, so their routes are `permitAll` or `authenticated`.
- A "Restricted n" cell grants the permission. The use case enforces footnote n
  (scope, state) when it is implemented.
- The seed creates no accounts. Matrix changes require a new migration; applied V002 is
  never edited. The endpoint bindings and the print, bulk-print and batch-start permissions
  were folded into V002 on 2026-10-13.

### Participant permission amendment — 2026-10-07

The owner selected separate permissions for manual Participant operations: DETAIL_VIEW
for detail, CREATE for manual add, UPDATE for edit, REMOVE for cancel and
REACTIVATE for reactivation. All codes start with `PARTICIPANT_`. The consolidated
V002 seed grants CREATE and REACTIVATE only to CLINIC_MANAGER. Runtime application
policies and HTTP rules enforce the same permission; the legacy MANAGE grant is
not accepted.
See [the manual Participant contract](../api/participant-manual-crud.md).

Owner amendment — 2026-10-07, revised 2026-10-13: an account holds exactly one role
(`accounts.role_id`). Patient accounts receive only the `PATIENT` role and staff accounts
never do; a V001 trigger enforces the pairing. There is no grantor column.

### Examination detail entry amendment — 2026-10-12

The owner approved two more examination-detail permissions, each protecting one endpoint:
`HEALTH_EXAMINATION_SERVICE_TEMPLATE_DOWNLOAD` (`GET .../examination-details/import-template`, the pre-filled
import template) and `HEALTH_EXAMINATION_SERVICE_RECORD` (`PUT .../examination-details/{participantId}`, manual
entry for one Participant). V002 seeds them and grants both to every role that holds
`HEALTH_EXAMINATION_SERVICE_RECONCILE`. Sessions must sign in again to receive them. See
[the examination detail contract](../api/examination-details-and-report.md).

### Role administration amendment — 2026-10-15

The owner approved role and role permission administration (SRS UC-008, UC-009;
[the contract](../api/roles-and-permissions.md)). V002 (folded into the baseline on 2026-10-16, formerly `V005__role_permission_management.sql`):

- binds `ROLE_MANAGE` and `ROLE_PERMISSION_ASSIGN` to their endpoints and adds `ROLE_CREATE`,
  `ROLE_UPDATE`, `PERMISSION_VIEW` and `ROLE_PERMISSION_VIEW`, granted to `ADMINISTRATOR`; these four
  must be added to the SRS matrix;
- adds `roles.row_version`, makes `roles.description` optional, enforces the role code format and a
  case-insensitive unique role name (BR-11);
- adds `permissions.module`, `use_cases` and `sensitive` for the Role Permissions screen. The
  `sensitive` list awaits stakeholder confirmation.

Permissions stay predefined: no endpoint creates one, because a permission only takes effect
through the endpoint it is bound to. The `ADMINISTRATOR` role cannot be deactivated and keeps the
role administration permissions, so roles can always be administered again. The `PATIENT` role
cannot be deactivated, since every patient portal account holds it, and a new staff role never
copies its `OWN_*` permissions (not in the SRS yet).

### Enforcement

- Each endpoint is stored with its permission: `permissions.http_method` and
  `permissions.endpoint` hold the HTTP method and the controller route template,
  such as `/api/v1/organizations/{organizationId}`. A permission protects at
  most one endpoint, and `(http_method, endpoint)` is unique. No rule list exists
  in code: `EndpointPermissions` reads the stored endpoints once at startup and
  the most specific matching template decides; changing them needs a restart.
- Endpoints that shared a permission received their own:
  `HEALTH_EXAMINATION_BATCH_DETAIL_VIEW`, `PARTICIPANT_DETAIL_VIEW`,
  `HEALTH_EXAMINATION_SERVICE_SUMMARY_READ`, `HEALTH_EXAMINATION_SERVICE_EXPORT`
  and `HEALTH_EXAMINATION_REPORT_EXPORT`; `ORGANIZATION_DELETE`,
  `HEALTH_EXAMINATION_BATCH_DELETE` and `SERVICE_CATALOG_VIEW` cover endpoints
  that had none. All eight belong to `CLINIC_MANAGER` and must be added to the
  SRS matrix.
- A staff rule requires both `ACCOUNT_STAFF` and `PERM_<code>`, so a permission
  granted to another account type never opens a staff endpoint. Patient-portal
  routes will require `ACCOUNT_PATIENT` with `PERM_OWN_*`.
- Fail-closed: any request without a matching rule is denied. Anonymous callers
  receive 401; signed-in callers receive 403, including for unknown URLs, so the
  response does not reveal which routes exist.
- Permissions are the snapshot taken at sign-in. A grant change applies after
  the user signs in again or the account's sessions are revoked
  (`SessionStore.revokeAll`).
- Business code may still check the principal's permissions for data-scope rules
  (Restricted footnotes); route rules only decide whether the endpoint is
  callable.

## Consequences

- Each new endpoint needs a stored permission with its method and route, or it
  returns 403.
- The 25 business endpoints are granted to `CLINIC_MANAGER`.
- In the `local` profile only, the local seed grants every permission to
  `TEST` to ease manual testing. It uses the same endpoint checks; no role
  bypasses unmapped endpoints.
- The local/test mock batch actor is removed; batch creation uses the signed-in
  principal.
- Permissions without endpoints are seeded but unused until their use cases ship.
- The frontend may hide actions using the permissions returned by
  `/api/v1/auth/me`; the backend remains authoritative.
