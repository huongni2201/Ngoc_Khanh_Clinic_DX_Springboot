# ADR-0015: Per-endpoint permissions from the SRS Permission Matrix

## Status

Accepted — 2026-10-07. Establishes the RBAC mechanism of
[ADR-0014](0014-session-cookie-redis-login.md). Remaining route/application gaps
are recorded in [Open items](../architecture/07-open-items.md#endpoint-authorization).

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
  `DIAGNOSTIC_DOCTOR`, `DATA_ENTRY_STAFF`, `CLINIC_MANAGER`, `ADMINISTRATOR`),
  one permission per matrix row and one `role_permissions` row per non-"No" cell.
- Permission codes are `<ENTITY>_<ACTION>`; patient-portal codes start with
  `OWN_` and are granted only to `PATIENT`.
- The matrix's sign-in and password rows have no permission. Current login/logout
  are public subject to Origin checks; `/auth/me` requires authentication. No
  current password-change endpoint is inferred from the matrix.
- A "Restricted n" cell grants the permission. The use case enforces footnote n
  (scope, state) when it is implemented.
- The seed creates no accounts. Matrix changes require a new migration; applied V002 is
  never edited.

Owner amendment — 2026-10-08: retain V002 as the access-control seed, alongside
one final schema initializer V001 and catalog seed V003. The former V004
account-role trigger amendment is included directly in V001 for fresh databases.

### Participant permission amendment — 2026-10-07

The owner selected separate permissions for manual Participant operations: VIEW
for detail, CREATE for manual add, UPDATE for edit, REMOVE for cancel and
REACTIVATE for reactivation. All codes start with `PARTICIPANT_`. The consolidated
V002 seed grants CREATE and REACTIVATE only to CLINIC_MANAGER. Runtime application
policies and HTTP rules enforce the same permission; the legacy MANAGE grant is
not accepted.
See [the manual Participant contract](../api/participant-manual-crud.md).

Owner amendment — 2026-10-07: patient accounts receive only the `PATIENT` role;
an active STAFF account is required as `granted_by`. V001 enforces this pairing
while continuing to reject patient accounts receiving staff roles.

### Enforcement

- `EndpointPermissions` (accesscontrol infrastructure) is the single list of
  rules: HTTP method, path pattern and permission code.
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

- Each new business endpoint must add a rule and authorization coverage; without
  a rule a signed-in caller receives 403 under normal HTTP authorization.
- Existing endpoints: organization view/create/update and health examination
  batch view/create/update, Participant operations and examination detail/report
  operations are granted to `CLINIC_MANAGER`. Organization/Batch DELETE and
  catalog lookup still lack route rules; direct Organization/Batch/catalog
  application callers also need permission enforcement.
- The local/test mock batch actor is removed; batch creation uses the signed-in
  principal.
- Permissions without endpoints are seeded but unused until their use cases ship.
- The frontend may hide actions using the permissions returned by
  `/api/v1/auth/me`; the backend remains authoritative.
