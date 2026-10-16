# Staff member and role API

Owner-approved contract of 2026-10-13. Owning module: `accesscontrol` (`StaffMemberController`,
`RoleController`). Responses use
[the common envelope and pagination](../../PROJECT_RULES.md#envelope-errors-and-pagination).
Migration `V004` binds the routes to their permissions; `V007` binds the role change route.

An Administrator creates a staff member, and the backend creates the staff member's STAFF account
in the same transaction. This replaces the former UC-005 mockup (an account for an existing staff
profile with a typed username): the owner selected generated codes and usernames.

## Routes

Every route requires a STAFF account with the listed permission
([ADR-0015](../adr/0015-endpoint-permission-rbac.md)). Permissions are the snapshot taken at sign-in:
sign in again after `V004`.

| Method/path | Success | Required STAFF permission |
|---|---|---|
| GET `/api/v1/roles` | 200 list of assignable roles | `ROLE_VIEW` (V004) |
| GET `/api/v1/staff-members` | 200 page of staff members | `STAFF_MEMBER_VIEW` (V004) |
| POST `/api/v1/staff-members` | 201 staff member with its account | `USER_ACCOUNT_CREATE` (V002, bound by V004); the use case also requires `USER_ROLE_ASSIGN` |
| PUT `/api/v1/accounts/{accountId}/role` | 200 [AccountRole](#accountrole) | `USER_ROLE_ASSIGN` (V002, bound by V007) |

`V004` grants `ROLE_VIEW` and `STAFF_MEMBER_VIEW` to `ADMINISTRATOR`, which already holds
`USER_ACCOUNT_CREATE` and `USER_ROLE_ASSIGN` (V002).

## GET /api/v1/roles

`data` is a list (not a page) of active roles a staff account may hold, ordered by
`code`. `PATIENT` is never listed.

| Field | Meaning |
|---|---|
| `id` | role UUID |
| `code` | role code, for example `GENERAL_PRACTITIONER` |
| `name` | English role name |
| `description` | English description |
| `permissionCount` | number of permissions the role grants |

## GET /api/v1/staff-members

Accepts `page`, `size` (max 100), `searchKey` (max 100; contains match on `staffCode`, `fullName`
and the account `username`, case-insensitive), `sortKey` (`id`, `staffCode`, `fullName`) and
`sortBy` (`ASC`/`DESC`). Ties are ordered by `id`. Staff members of every status are listed.
`data` is a page of [StaffMember](#staffmember).

## POST /api/v1/staff-members

Request (`application/json`, Origin required):

| Field | Rule |
|---|---|
| `fullName` | Required, max 200. Only letters of the Vietnamese alphabet and `f`, `j`, `w`, `z` (with or without diacritics, precomposed or with combining marks), spaces, `.`, apostrophe and `-`, with at least one letter, so a username can always be generated. Digits and letters such as `ø`, `ß`, `æ`, `ł` are rejected (400 on `fullName`) |
| `dateOfBirth` | Optional ISO date (`yyyy-MM-dd`) in the past |
| `sex` | Optional: `MALE`, `FEMALE`, `OTHER`, `UNKNOWN` |
| `phone` | Optional, max 30 |
| `email` | Optional valid email, max 255 |
| `initialPassword` | Required, 8 to 72 printable ASCII characters without spaces or diacritics (`!` to `~`: letters, digits, symbols), so each character is one bcrypt byte. Not trimmed |
| `roleCode` | Required, max 80: the one role the account holds (an account holds exactly one role) |

Text fields are trimmed; blank optional fields become null. `staffCode`, `username`, `status`,
department and room are not accepted.

The backend, in one transaction:

1. checks the caller holds `USER_ACCOUNT_CREATE` and `USER_ROLE_ASSIGN`;
2. checks `roleCode` names an active role other than `PATIENT`, otherwise 422;
3. creates the staff member with status `ACTIVE` and the next `staffCode` `NV-<n>`, `n` zero-padded
   to 4 digits (`NV-0001`, `NV-0002`, …, `NV-10000`);
4. creates an `ACTIVE` STAFF account with the generated username and the bcrypt hash of
   `initialPassword`, holding that role (`accounts.role_id`);
5. records the audit events `CREATE_STAFF_MEMBER` and `CREATE_ACCOUNT` (no password, hash or contact
   details).

Username: always lowercase `a`–`z` followed by digits, without diacritics. The name is stripped of
Vietnamese diacritics (`đ` → `d`), lowercased and split into words
holding only `a`–`z`. The base is the last word followed by the first letter of each earlier word, at
most 140 characters; the username is the base followed by the next free number starting at 1.
Examples: `Nguyễn Văn An` → `annv1`, then `annv2`; `Đặng Thị Đào` → `daodt1`; `An` → `an1`.

Response 201, message `Staff member created`, `data` is a [StaffMember](#staffmember). The password
is never returned and the response is not repeatable: the administrator hands the username and the
temporary password to the staff member. No SMS or notification is sent.

## PUT /api/v1/accounts/{accountId}/role

Changes the one role of a staff account (SRS UC-010 Change User Role). An account holds exactly one
role, so the role is replaced, never added or removed.

| Field | Rule |
|---|---|
| `roleCode` | Required, max 80: an active role other than `PATIENT`, different from the current role |
| `rowVersion` | Required, the `account.rowVersion` last read |

The backend, in one transaction:

1. checks the caller holds `USER_ROLE_ASSIGN`;
2. checks the account is an `ACTIVE` STAFF account (otherwise 422 with
   `data: [{"field":"accountId","code":"InactiveAccount"}]`) and `rowVersion` is current (409);
3. checks `roleCode` names an active role other than `PATIENT` (otherwise 422 with
   `data: [{"field":"roleCode","code":"UnavailableRole"}]`) and differs from the
   current role (otherwise 400);
4. when the account holds `ADMINISTRATOR` and the new role is another one, checks under a
   transaction-scoped lock that another account can still administer the system: an `ACTIVE` STAFF
   account of an `ACTIVE` staff member holding the active `ADMINISTRATOR` role (otherwise 409 with
   `data: [{"field":"roleCode","code":"LastAdministrator"}]`);
5. replaces the role, increments the account `rowVersion` and records the audit event
   `CHANGE_ACCOUNT_ROLE` (previous and new role code and version).

Response 200, message `Account role changed`. Sessions already open keep their permissions; the user
receives the new role at the next sign-in. An Administrator may change the role of their own account
while another Administrator account remains (owner confirmation pending).

## AccountRole

| Field | Meaning |
|---|---|
| `accountId` | account UUID |
| `roleCode` | code of the role the account now holds |
| `rowVersion` | incremented account version |

## StaffMember

| Field | Meaning |
|---|---|
| `id` | staff member UUID |
| `staffCode` | generated code, for example `NV-0071` |
| `fullName` | full name |
| `dateOfBirth`, `sex`, `phone`, `email` | optional values, null when absent |
| `status` | `ACTIVE`, `INACTIVE` or `SUSPENDED` |
| `rowVersion` | integer >= 0 |
| `account` | the staff member's account, or null when none exists (accounts provisioned by SQL may be missing) |
| `account.id` | account UUID |
| `account.username` | sign-in username |
| `account.status` | `ACTIVE`, `LOCKED` or `DISABLED` |
| `account.roleCode` | code of the one role the account holds |
| `account.rowVersion` | account version, sent back when changing the role |

## Errors

| Status | When |
|---|---|
| 400 | Validation failure with the field list (for example a non-ASCII `initialPassword` or a `fullName` without a Latin letter), or malformed JSON; PUT role: the role is the current one |
| 401 | No valid session |
| 403 | Not a STAFF account, a permission is missing, or Origin not allowed |
| 404 | PUT role: unknown `accountId` |
| 409 | POST: a unique staff code, username or owner collided (for example a row inserted by SQL at the same time); submitting again generates new values. PUT role: stale `rowVersion` (no `data`), or `LastAdministrator` on `roleCode` |
| 422 | POST and PUT role: `roleCode` is unknown, inactive or `PATIENT`; PUT role: the account is not an `ACTIVE` STAFF account |

Out of scope: editing, locking or deactivating staff members, password reset, department/room
assignment and credential delivery.
