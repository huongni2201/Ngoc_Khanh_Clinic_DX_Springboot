# User account creation (UC-005)

Added 2026-10-13. Base path: `/api/v1/user-accounts`. Implementation: module
`accesscontrol` (`UserAccountController`, `CreateUserAccountUseCase`).
Responses use [the common envelope](../../PROJECT_RULES.md#envelope-errors-and-pagination)
and `Cache-Control: no-store`; failures use `result: "NG"`.

An Administrator creates a sign-in account for an existing ACTIVE staff member or
patient that has no account yet. The account owns no personal data: the staff
profile and the patient profile stay with their own use cases (UC-109, UC-013).

## Permissions

Every route requires a STAFF account and its own permission (ADR-0015, V005).
All four are granted to `ADMINISTRATOR` only. Sign in again after migration.

| Route | Permission |
|---|---|
| `POST /api/v1/user-accounts` | `USER_ACCOUNT_CREATE` |
| `GET /api/v1/user-accounts/owner-candidates` | `USER_ACCOUNT_OWNER_SEARCH` |
| `GET /api/v1/user-accounts/username-availability` | `USER_ACCOUNT_USERNAME_CHECK` |
| `GET /api/v1/user-accounts/assignable-roles` | `USER_ACCOUNT_ASSIGNABLE_ROLE_VIEW` |

## POST /api/v1/user-accounts

Requires an allowed `Origin`. Request:

| Field | Rule |
|---|---|
| `accountType` | required, `STAFF` or `PATIENT` |
| `ownerId` | required, staff member id (STAFF) or patient id (PATIENT) |
| `username` | required, 1 to 150 printable ASCII characters (no diacritics), no leading or trailing space, unique, compared exactly (case-sensitive) |
| `temporaryPassword` | required, not blank; no complexity policy. Bcrypt reads at most 72 UTF-8 bytes after NFKC normalization, so a longer password is rejected with `Invalid request` |
| `roleIds` | required array, at most 20; STAFF: at least one active role other than `PATIENT`; PATIENT: empty, the `PATIENT` role is granted automatically |

```json
{"accountType":"STAFF","ownerId":"0199…","username":"nguyen.van.a",
 "temporaryPassword":"…","roleIds":["0199…"]}
```

Rules:

- The owner must exist (404), be ACTIVE, have no account and have a phone number
  that normalizes to a 10-digit Vietnamese mobile number (`0[35789]` + 8 digits;
  spaces, dots, dashes, parentheses and a `+84`/`84` prefix are accepted).
- The account is created ACTIVE. Its roles record the signed-in Administrator as
  `granted_by`. The account, the roles and the audit event `CREATE_USER_ACCOUNT`
  (resource `ACCOUNT`; metadata: account type, owner id, status and role codes)
  commit together. The password is stored only as a bcrypt hash and never
  returned, logged or audited.
- After the commit the sign-in information is sent once by SMS to the owner's
  mobile number ([ADR-0017](../adr/0017-sign-in-credential-sms-delivery.md)).
  `smsDelivered` is true only when the gateway accepted it. No gateway is
  integrated yet, so it is currently always false; the account still exists.

Response 201:

```json
{"result":"OK","code":201,"message":"User account created","data":{
 "accountId":"0199…","accountType":"STAFF","username":"nguyen.van.a","status":"ACTIVE",
 "ownerId":"0199…","roleCodes":["GENERAL_PRACTITIONER"],"smsDelivered":false}}
```

Errors. The messages are part of the contract: the frontend tells the cases apart
by the exact text and shows its own wording.

| Status | Message | When |
|---|---|---|
| 400 | `Invalid request: <fields>` | structural validation; `data` lists the fields |
| 400 | `Invalid request` | password longer than 72 UTF-8 bytes |
| 400 | `A staff account requires at least one role` | STAFF with no role |
| 400 | `A patient account takes no roles` | PATIENT with roles |
| 400 | `A role can be selected only once` | duplicate role id |
| 403 | `You are not authorized to perform this action` | missing permission or not a STAFF account |
| 404 | `Requested resource not found` | owner does not exist |
| 409 | `Username already exists` | username taken, including a concurrent request |
| 409 | `Account owner already has an account` | owner linked, including a concurrent request |
| 409 | `Account owner is not active` | owner not ACTIVE |
| 409 | `Account owner has no valid mobile phone number` | no mobile number for the SMS |
| 409 | `Role is not assignable` | unknown, inactive or `PATIENT` role for a STAFF account |

## GET /api/v1/user-accounts/owner-candidates

Query: `accountType` (`STAFF` or `PATIENT`, required) and `keyword` (2 to 100
characters, required). Returns at most 20 ACTIVE profiles without an account,
ordered by full name. LIKE wildcards in the keyword match literally.

- STAFF matches staff code, full name (case-insensitive) or phone number.
- PATIENT matches patient code, full name (case-insensitive), phone number, or
  the exact identification number (no partial match).

The list is bounded instead of paginated: patients that already have an account
are removed after the search, so an exact total is not available. `limited: true`
asks the user to refine the keyword.

```json
{"result":"OK","code":200,"data":{"items":[{"ownerId":"…","ownerCode":"NV-0071",
 "fullName":"…","departmentName":"Khoa Khám Bệnh","dateOfBirth":null,
 "phone":"0912 345 678","email":"…","smsPhoneNumber":"0912345678"}],"limited":false}}
```

Staff items carry `departmentName` and `email`; patient items carry
`dateOfBirth`; the other fields are null. `smsPhoneNumber` is null when the stored
phone number cannot receive the SMS, in which case creation returns 409.

## GET /api/v1/user-accounts/username-availability

Query: `username`, with the same rules as the create request. Returns
`{"available": true|false}`. The answer is advisory; creation checks again.

## GET /api/v1/user-accounts/assignable-roles

Returns the active roles other than `PATIENT`, ordered by name then code:
`[{"roleId":"…","code":"NURSE","name":"Nurse","description":"…"}]`.

## Not covered

User list, user details, editing, deactivation and Administrator password reset
(UC-006 to UC-010) have no endpoints yet. See
[open items](../architecture/07-open-items.md).
