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

- `V004__seed_access_control_roles_and_permissions.sql` seeds the whole matrix:
  seven roles (`PATIENT`, `RECEPTIONIST`, `GENERAL_PRACTITIONER`,
  `DIAGNOSTIC_DOCTOR`, `DATA_ENTRY_STAFF`, `CLINIC_MANAGER`, `ADMINISTRATOR`),
  one permission per matrix row and one `role_permissions` row per non-"No" cell.
- Permission codes are `<ENTITY>_<ACTION>`; patient-portal codes start with
  `OWN_` and are granted only to `PATIENT`.
- The sign-in and password rows have no permission: every authenticated account
  may use them, so their routes are `permitAll` or `authenticated`.
- A "Restricted n" cell grants the permission. The use case enforces footnote n
  (scope, state) when it is implemented.
- The seed creates no accounts. Matrix changes require a new migration; V004 is
  never edited.

### Endpoint of a permission (V005)

- `permissions.http_method` and `permissions.endpoint` name the one endpoint a
  permission protects. `endpoint` is the route template declared by the
  controller, with its path variable names, for example
  `/api/v1/organizations/{organizationId}`; no wildcard or pattern is stored.
  Both columns are NULL for a permission without an endpoint yet, and
  `(http_method, endpoint)` is unique.
- V005 deletes the V003 roster codes (`HEALTH_EXAMINATION_PARTICIPANT_READ`,
  `HEALTH_EXAMINATION_PARTICIPANT_IMPORT`), which the matrix codes
  `PARTICIPANT_VIEW`, `PARTICIPANT_TEMPLATE_DOWNLOAD` and `PARTICIPANT_IMPORT`
  replace.
- V005 adds four permissions for existing endpoints that the matrix does not
  list yet, granted to `CLINIC_MANAGER`: `ORGANIZATION_DELETE`,
  `HEALTH_EXAMINATION_BATCH_DETAIL_VIEW`, `HEALTH_EXAMINATION_BATCH_DELETE`
  and `SERVICE_CATALOG_VIEW`. The SRS matrix must be extended with them.
- V006 follows the matrix update that gives "Manage document templates" and
  "Manage notification templates" to the Administrator (Full) instead of the
  Clinic Manager, as in the SRS 4.1 Administrator actor.

### Enforcement

- The security filter chain only requires a signed-in account on
  `/api/v1/**` (anonymous callers receive 401).
- `EndpointPermissionInterceptor` (Spring MVC) runs after the controller method
  is chosen. It looks the permission up by the HTTP method and the exact route
  template of that method (`HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`) and
  requires both `ACCOUNT_STAFF` and `PERM_<code>`, so a permission granted to
  another account type never opens a staff endpoint. Patient-portal routes will
  require `ACCOUNT_PATIENT` with `PERM_OWN_*`.
- Fail-closed: an endpoint without a stored permission returns 403. A URL that
  matches no controller returns 404.
- Endpoint permissions are read once at startup; changing an endpoint or method
  requires a restart. Startup logs a warning for each controller endpoint
  without a permission and each stored endpoint without a controller.
- Permissions are the snapshot taken at sign-in. A grant change applies after
  the user signs in again or the account's sessions are revoked
  (`SessionStore.revokeAll`).
- Business code may still check the principal's permissions for data-scope rules
  (Restricted footnotes); route rules only decide whether the endpoint is
  callable.

## Consequences

- Each new endpoint needs a migration that stores its permission, or it returns
  403; `AuthIntegrationTest` fails when a controller endpoint and the stored
  permissions differ.
- The 14 existing business endpoints (organization, batch, participant roster and
  service catalog) are granted to `CLINIC_MANAGER`.
- The local/test mock batch actor is removed; batch creation uses the signed-in
  principal.
- Permissions without endpoints are seeded but unused until their use cases ship.
- The frontend may hide actions using the permissions returned by
  `/api/v1/auth/me`; the backend remains authoritative.
