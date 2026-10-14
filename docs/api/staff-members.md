# Staff member and role API

Owner-approved contract of 2026-10-13. Owning module: `accesscontrol` (`StaffMemberController`,
`RoleController`). Responses use
[the common envelope and pagination](../../PROJECT_RULES.md#envelope-errors-and-pagination).
Migration `V008` binds the routes to their permissions.

An Administrator creates a staff member, and the backend creates the staff member's STAFF account
in the same transaction. This replaces the former UC-005 mockup (an account for an existing staff
profile with a typed username): the owner selected generated codes and usernames.

## Routes

Every route requires a STAFF account with the listed permission
([ADR-0015](../adr/0015-endpoint-permission-rbac.md)). Permissions are the snapshot taken at sign-in:
sign in again after `V008`.

| Method/path | Success | Required STAFF permission |
|---|---|---|
| GET `/api/v1/roles` | 200 list of assignable roles | `ROLE_VIEW` (V008) |
| GET `/api/v1/staff-members` | 200 page of staff members | `STAFF_MEMBER_VIEW` (V008) |
| POST `/api/v1/staff-members` | 201 staff member with its account | `USER_ACCOUNT_CREATE` (V002, bound by V008); the use case also requires `USER_ROLE_ASSIGN` |

`V008` grants `ROLE_VIEW` and `STAFF_MEMBER_VIEW` to `ADMINISTRATOR`, which already holds
`USER_ACCOUNT_CREATE` and `USER_ROLE_ASSIGN` (V002).

## GET /api/v1/roles

`data` is a list (not a page) of active roles that may be granted to a staff account, ordered by
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
| `fullName` | Required, max 200 after trimming, contains at least one letter |
| `dateOfBirth` | Optional ISO date (`yyyy-MM-dd`) in the past |
| `sex` | Optional: `MALE`, `FEMALE`, `OTHER`, `UNKNOWN` |
| `phone` | Optional, max 30 |
| `email` | Optional valid email, max 255 |
| `initialPassword` | Required, 8 to 1024 characters; more than 72 UTF-8 bytes after NFKC normalization is a 400 on this field. Not trimmed |
| `roleCodes` | Required, 1 to 20 non-blank codes; duplicates are ignored |

Text fields are trimmed; blank optional fields become null. `staffCode`, `username`, `status`,
department and room are not accepted.

The backend, in one transaction:

1. checks the caller holds `USER_ACCOUNT_CREATE` and `USER_ROLE_ASSIGN`;
2. checks every role code names an active role other than `PATIENT`, otherwise 409;
3. creates the staff member with status `ACTIVE` and the next `staffCode` `NV-<n>`, `n` zero-padded
   to 4 digits (`NV-0001`, `NV-0002`, …, `NV-10000`);
4. creates an `ACTIVE` STAFF account with the generated username and the bcrypt hash of
   `initialPassword`, and grants the roles with `granted_by` = the caller;
5. records the audit events `CREATE_STAFF_MEMBER` and `CREATE_ACCOUNT` (no password, hash or contact
   details).

Username: the name is stripped of Vietnamese diacritics (`đ` → `d`), lowercased and split into words
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
| `account.roleCodes` | codes of the active roles granted, ordered by code |

## Errors

| Status | When |
|---|---|
| 400 | Validation failure with the field list, malformed JSON, or `initialPassword` too long |
| 401 | No valid session |
| 403 | Not a STAFF account, a permission is missing, or Origin not allowed |
| 409 | POST: a role code is unknown, inactive or `PATIENT`; or a concurrent identity collision |

Out of scope: editing, locking or deactivating staff members, password reset, role changes after
creation, department/room assignment and credential delivery.
