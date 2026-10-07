# ADR-0015: Per-endpoint permissions from the SRS Permission Matrix

## Status

Accepted — 2026-10-07. Closes the RBAC go-live blocker of
[ADR-0014](0014-session-cookie-redis-login.md).

## Context

ADR-0014 let any signed-in STAFF account call every `/api/v1/**` route. Sessions
already carry `ACCOUNT_<type>`, `ROLE_<roleCode>` and `PERM_<permissionCode>`
authorities, but `roles`, `permissions` and `role_permissions` were empty. The
SRS Permission Matrix 4.4, aligned with the use-case table 4.2, defines which
actor may perform each action.

## Decision

### Seed

- `V002__seed_access_control_roles_and_permissions.sql` seeds the whole matrix:
  seven roles (`PATIENT`, `RECEPTIONIST`, `GENERAL_PRACTITIONER`,
  `DIAGNOSTIC_DOCTOR`, `DATA_ENTRY_STAFF`, `CLINIC_MANAGER`, `ADMINISTRATOR`),
  one permission per matrix row and one `role_permissions` row per non-"No" cell.
- Permission codes are `<ENTITY>_<ACTION>`; patient-portal codes start with
  `OWN_` and are granted only to `PATIENT`.
- The sign-in and password rows have no permission: every authenticated account
  may use them, so their routes are `permitAll` or `authenticated`.
- A "Restricted n" cell grants the permission. The use case enforces footnote n
  (scope, state) when it is implemented.
- The seed creates no accounts. Matrix changes require a new migration; V002 is
  never edited.

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

- Each new endpoint must add a rule, or it returns 403; tests fail early.
- Existing endpoints: organization view/create/update and health examination
  batch view/create, all granted to `CLINIC_MANAGER`.
- The local/test mock batch actor is removed; batch creation uses the signed-in
  principal.
- Permissions without endpoints are seeded but unused until their use cases ship.
- The frontend may hide actions using the permissions returned by
  `/api/v1/auth/me`; the backend remains authoritative.
