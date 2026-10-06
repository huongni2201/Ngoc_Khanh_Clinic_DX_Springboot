# Login operations

Decision record: [ADR-0014](../adr/0014-session-cookie-redis-login.md).

## POST /api/v1/auth/login

Request (`application/json`):

| Field | Rule |
|---|---|
| `username` | required, at most 150 characters, compared exactly (case-sensitive, not trimmed) |
| `password` | required, at most 1024 characters; more than 72 UTF-8 bytes after NFKC normalization fails with 401 |

Requires an allowed `Origin` (or `Referer`) header.

Response 200 (`ApiResponse`), `data` is the authenticated principal:

| Field | Meaning |
|---|---|
| `userId` | account ID |
| `staffId` | staff member ID for STAFF accounts, otherwise null |
| `patientId` | patient ID for PATIENT accounts, otherwise null |
| `username` | account username |
| `principalType` | `STAFF` or `PATIENT` |
| `roleAssignments[]` | `roleId`, `roleCode`, `permissions[]` of active roles |
| `idleExpiresAt` | session expiry if idle |
| `absoluteExpiresAt` | hard session expiry (login + 8 hours) |

The response sets the session cookie. It never contains the session ID or the password.
An existing session cookie sent with the request is invalidated.

## POST /api/v1/auth/logout

Requires an allowed `Origin` (or `Referer`). Deletes the session if the cookie
is valid and always clears the cookie. Response 204.

## Session cookie

| Attribute | Value |
|---|---|
| Name | `__Host-NKC_SESSION` (secure), `NKC_SESSION` when `NKC_AUTH_COOKIE_SECURE=false` |
| Value | opaque 43-character session ID |
| Flags | HttpOnly, Secure (when enabled), SameSite=Lax, Path=/ |
| Lifetime | browser session (no Max-Age); server expiry: 30 minutes idle, 8 hours absolute |

Browsers must send the cookie on every API request (`credentials: "include"`).

## Errors

All errors use the `ApiResponse` error envelope with `Cache-Control: no-store`.

| Status | When |
|---|---|
| 400 | malformed body or validation failure |
| 401 | invalid credentials, ineligible account, missing/expired/duplicate session cookie |
| 403 | Origin/Referer not allowed; authenticated account not allowed on the route (e.g. PATIENT on business routes) |
| 503 | Redis unavailable |

Invalid-credential responses are identical whether the username exists or not.

## Redis keys

| Key | Value |
|---|---|
| `nkc:auth:session:{sha256(sessionId)}` | JSON session snapshot; TTL = min(30 min, absolute expiry − now) |
| `nkc:auth:account-sessions:{accountId}` | set of session hashes of the account |

## Configuration

| Property | Environment variable | Default |
|---|---|---|
| `spring.data.redis.host` / `port` / `password` | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | `localhost`, `6379`, empty |
| `clinic.auth.idle-timeout` | — | `30m` |
| `clinic.auth.absolute-timeout` | — | `8h` |
| `clinic.auth.cookie-secure` | `NKC_AUTH_COOKIE_SECURE` | `true` (`false` in `local`) |
| `clinic.auth.allowed-origins` | `NKC_AUTH_ALLOWED_ORIGINS` | empty (`http://localhost:3000` in `local`) |

Production must set `NKC_AUTH_ALLOWED_ORIGINS` and keep `NKC_AUTH_COOKIE_SECURE=true`.

## Provisioning accounts

Store password hashes produced by the application password encoder
(`accesscontrol.infrastructure.security.UserPasswordEncoder`): NFKC-normalized,
bcrypt, `{bcrypt}` prefix, at most 72 UTF-8 bytes. Never pass passwords as
command-line arguments or store plaintext. `scripts/auth/provision-staff.sql`
shows the account/staff rows to insert.
