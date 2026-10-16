# Role and role permission administration API

Owner-approved contract of 2026-10-15. Owning module: `accesscontrol`
(`RoleAdministrationController`, `PermissionCatalogController`). Implements SRS UC-008 Manage Roles
(screens 1.3.1 Role List, 1.3.2 New Role, 1.3.3 Edit Role) and UC-009 Manage Role Permissions
(screen 1.3.4 Role Permissions). Responses use
[the common envelope and pagination](../../PROJECT_RULES.md#envelope-errors-and-pagination).
Migration `V005` binds the routes to their permissions.

Permissions are predefined: each one protects exactly one endpoint
([ADR-0015](../adr/0015-endpoint-permission-rbac.md)) and is seeded by a migration. An Administrator
grants and removes permissions of a role but never creates a permission (SRS UC-009). Roles are never
deleted; a deactivated role stays assigned to its accounts and grants no permission (BR-06).
Permission and status changes reach an account at its next sign-in.

## Routes

Every route requires a STAFF account with the listed permission. Sign in again after `V005`.

| Method/path | Success | Required STAFF permission |
|---|---|---|
| GET `/api/v1/admin/roles` | 200 page of [Role](#role) | `ROLE_MANAGE` (V002, bound by V005) |
| POST `/api/v1/admin/roles` | 201 [Role](#role) | `ROLE_CREATE` (V005); also `ROLE_PERMISSION_ASSIGN` with `copyFromRoleId` |
| PUT `/api/v1/admin/roles/{roleId}` | 200 [Role](#role) | `ROLE_UPDATE` (V005) |
| GET `/api/v1/admin/permissions` | 200 list of [Permission](#permission) | `PERMISSION_VIEW` (V005) |
| GET `/api/v1/admin/roles/{roleId}/permissions` | 200 [RolePermissions](#rolepermissions) | `ROLE_PERMISSION_VIEW` (V005) |
| PUT `/api/v1/admin/roles/{roleId}/permissions` | 200 [RolePermissions](#rolepermissions) | `ROLE_PERMISSION_ASSIGN` (V002, bound by V005) |

`V005` grants the four new permissions to `ADMINISTRATOR`, which already holds `ROLE_MANAGE` and
`ROLE_PERMISSION_ASSIGN` (V002). `GET /api/v1/roles` (assignable roles for staff creation,
[staff members](staff-members.md)) is unchanged.

## GET /api/v1/admin/roles

Accepts `page` and `size` (max 100). `data` is a page of [Role](#role) of every status, ordered by
`code`. Screens read every page and search, filter and sort on the names they display; other query
parameters are ignored.

## POST /api/v1/admin/roles

| Field | Rule |
|---|---|
| `code` | Required, max 80, uppercase letters, digits and underscores starting with a letter (`^[A-Z][A-Z0-9_]*$`). Unique. Never changes |
| `name` | Required, max 100 after trimming. Unique ignoring case (BR-11) |
| `description` | Optional, max 500 after trimming; blank becomes null |
| `copyFromRoleId` | Optional UUID of an active role, other than `PATIENT`, whose permissions the new role receives. Copying grants permissions, so it also requires `ROLE_PERMISSION_ASSIGN` (403 otherwise) |
| `active` | Optional, default `true` |

Response 201, message `Role created`. Audit `CREATE_ROLE` (code, name, description, active, copied role code and the
codes `granted` by the copy).

## PUT /api/v1/admin/roles/{roleId}

| Field | Rule |
|---|---|
| `name` | Required, max 100 after trimming, unique ignoring case among other roles |
| `description` | Optional, max 500 after trimming |
| `active` | Required; `false` deactivates the role, `true` reactivates it |
| `rowVersion` | Required, the version last read |

Response 200, message `Role updated`, with the incremented `rowVersion`. Audit `UPDATE_ROLE` with
before/after name, description, active and row version.

## GET /api/v1/admin/permissions

`data` lists every [Permission](#permission), ordered by `module` and `code`.

## PUT /api/v1/admin/roles/{roleId}/permissions

Only pending changes are sent:

| Field | Rule |
|---|---|
| `rowVersion` | Required, the role version last read (from Role or RolePermissions) |
| `grant` | Permission codes to grant, max 500 |
| `revoke` | Permission codes to remove, max 500 |

At least one code is required and a code may not be in both lists (400). Codes already granted (or
not granted, for `revoke`) are ignored. The role row version is incremented, so a second save based
on the same read is a conflict. Response 200, message `Role permissions updated`. Audit
`UPDATE_ROLE_PERMISSIONS` with the codes actually `granted` and `removed`.

## Errors

| Status | When |
|---|---|
| 400 | Bean Validation failure, with the field list; empty or contradictory permission changes |
| 403 | Missing permission, including `ROLE_PERMISSION_ASSIGN` when `copyFromRoleId` is sent |
| 404 | Unknown `roleId` |
| 409 with `data` | `FieldConflictException`: `[{"field":"code" or "name","code":"Duplicate"}]`, the code or name is used by another role |
| 409 without `data` | Stale `rowVersion` (`Record was changed by another request`), or a protected role change: deactivating `ADMINISTRATOR` or `PATIENT`, or removing a protected Administrator permission (`Business rule could not be completed`) |
| 422 | `copyFromRoleId` is unknown, inactive or `PATIENT`; the role whose permissions change is inactive; a permission code does not exist |

Protected roles (owner decisions 2026-10-15, not in the SRS yet): the `ADMINISTRATOR` role cannot
be deactivated and never loses `ROLE_MANAGE`, `ROLE_UPDATE`, `PERMISSION_VIEW`,
`ROLE_PERMISSION_VIEW` or `ROLE_PERMISSION_ASSIGN`. The `PATIENT` role, held by every patient
portal account, cannot be deactivated and is never a `copyFromRoleId` source.

## Role

| Field | Meaning |
|---|---|
| `id`, `code`, `name` | Role UUID, code and name |
| `description` | Description or null |
| `active` | Whether the role grants its permissions |
| `permissionCount` | Number of granted permissions |
| `userCount` | Number of accounts holding the role, whatever their status |
| `rowVersion` | Version for PUT requests |

## Permission

| Field | Meaning |
|---|---|
| `code`, `name` | Permission code and English name |
| `module` | Group shown on the Role Permissions screen, for example `Invoice and Payment` |
| `useCases` | Related SRS use case IDs, for example `["UC-041","UC-042"]`; may be empty |
| `sensitive` | Whether the screen labels it "(sensitive)". The list awaits stakeholder confirmation |

## RolePermissions

| Field | Meaning |
|---|---|
| `roleId`, `roleCode` | The role |
| `active` | Permissions of an inactive role cannot change |
| `rowVersion` | Role version for the next save |
| `permissionCodes` | Granted codes, ordered by code |
