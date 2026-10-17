# Staff member and role API

Owner-approved contract of 2026-10-13. Owning module: `accesscontrol` (`StaffMemberController`,
`RoleController`). Responses use
[the common envelope and pagination](../../PROJECT_RULES.md#envelope-errors-and-pagination).
The V002 seed binds the routes to their permissions.

An Administrator creates a staff member, and the backend creates the staff member's STAFF account
in the same transaction. This replaces the former UC-005 mockup (an account for an existing staff
profile with a typed username): the owner selected generated codes and usernames.

## Routes

Every route requires a STAFF account with the listed permission
([ADR-0015](../adr/0015-endpoint-permission-rbac.md)). Permissions are the snapshot taken at sign-in:
sign in again after a permission change.

| Method/path | Success | Required STAFF permission |
|---|---|---|
| GET `/api/v1/roles` | 200 list of assignable roles | `ROLE_VIEW` (V002) |
| GET `/api/v1/staff-members` | 200 page of staff members | `STAFF_MEMBER_VIEW` (V002) |
| POST `/api/v1/staff-members` | 201 staff member with its account | `USER_ACCOUNT_CREATE` (V002); the use case also requires `USER_ROLE_ASSIGN` |

V002 grants `ROLE_VIEW`, `STAFF_MEMBER_VIEW`, `USER_ACCOUNT_CREATE` and `USER_ROLE_ASSIGN` to
`ADMINISTRATOR`.

## GET /api/v1/roles

`data` is a list (not a page) of active roles a staff account may hold, ordered by
`code`. `PATIENT` is never listed.

| Field | Meaning |
|---|---|
| `id` | role UUID |
| `code` | role code, for example `GENERAL_PRACTITIONER` |
| `name` | English role name |
| `description` | English description |

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

## Errors

| Status | When |
|---|---|
| 400 | Validation failure with the field list (for example a non-ASCII `initialPassword` or a `fullName` without a Latin letter), or malformed JSON |
| 401 | No valid session |
| 403 | Not a STAFF account, a permission is missing, or Origin not allowed |
| 409 | POST: a unique staff code, username or owner collided (for example a row inserted by SQL at the same time); submitting again generates new values |
| 422 | POST: `roleCode` is unknown, inactive or `PATIENT` |

Out of scope: editing, locking or deactivating staff members, password reset, role changes after
creation, department/room assignment and credential delivery.
