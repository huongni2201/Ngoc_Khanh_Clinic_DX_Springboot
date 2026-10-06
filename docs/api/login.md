# Login operations

Decision record: [ADR-0014](../adr/0014-session-cookie-redis-login.md).
Implementation: module `accesscontrol` (`AuthController`, `SecurityConfiguration`).

## POST /api/v1/auth/login

Request (`application/json`):

| Field | Rule |
|---|---|
| `username` | required, at most 150 characters, compared exactly (case-sensitive, not trimmed) |
| `password` | required, at most 1024 characters (400 above); more than 72 UTF-8 bytes after NFKC normalization fails with 401 |

Requires an allowed `Origin` (or `Referer`) header. A session cookie sent with
the request is ended first.

Response 200 (`ApiResponse`), `data` is the authenticated principal:

| Field | Meaning |
|---|---|
| `userId` | account ID |
| `staffId` | staff member ID for STAFF accounts, otherwise null |
| `patientId` | patient ID for PATIENT accounts, otherwise null |
| `username` | account username |
| `principalType` | `STAFF` or `PATIENT` |
| `roleAssignments[]` | `roleId`, `roleCode`, `permissions[]` of active roles at sign-in |
| `idleExpiresAt` | session expiry if no further request is made |
| `absoluteExpiresAt` | hard session expiry (sign-in + 8 hours) |

The response sets the session cookie. It never contains the session ID or the
password. Every rejected sign-in returns the same 401 response.

## GET /api/v1/auth/me

Returns the principal of the current session (same `data` as login) for STAFF
and PATIENT accounts, extending the session. 401 without a valid session.

## POST /api/v1/auth/logout

Requires an allowed `Origin` (or `Referer`). Ends the session if the cookie is
valid and always clears the cookie. Response 204.

## Session cookie

| Attribute | Value |
|---|---|
| Name | `__Host-NKC_SESSION`; `NKC_SESSION` when `NKC_AUTH_COOKIE_SECURE=false` |
| Value | opaque 43-character session ID |
| Flags | HttpOnly, Secure (when enabled), SameSite=Lax, Path=/, no Domain |
| Lifetime | browser session (no Max-Age); server expiry 30 minutes idle, 8 hours absolute |

Browsers must send the cookie with every API request (`credentials: "include"`).

## Errors

All errors use the `ApiResponse` error envelope with `Cache-Control: no-store`.

| Status | When |
|---|---|
| 400 | malformed body or validation failure |
| 401 | rejected credentials; missing, expired or duplicate session cookie |
| 403 | Origin/Referer not allowed; account not allowed on the route (PATIENT on business routes) |
| 503 | session store (Redis) unavailable |

## Redis keys

| Key | Value |
|---|---|
| `nkc:auth:session:{sha256(sessionId)}` | JSON session snapshot; TTL = min(30 min, absolute expiry − now) |
| `nkc:auth:account-sessions:{accountId}` | set of the account's session hashes |

## Configuration

| Property | Environment variable | Default |
|---|---|---|
| `spring.data.redis.host` / `port` / `password` | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | `localhost`, `6379`, empty |
| `clinic.auth.idle-timeout` | — | `30m` |
| `clinic.auth.absolute-timeout` | — | `8h` (at most 12h) |
| `clinic.auth.cookie-secure` | `NKC_AUTH_COOKIE_SECURE` | `true` (`false` in `local`) |
| `clinic.auth.allowed-origins` | `NKC_AUTH_ALLOWED_ORIGINS` (comma-separated) | empty (`http://localhost:3000` in `local`) |

The same origin list drives CORS for `/api/**`: allowed origins receive
`Access-Control-Allow-Origin` and `Access-Control-Allow-Credentials: true` for
methods GET, POST, PUT, PATCH, DELETE and headers `Content-Type`, `Accept`;
other origins get no CORS headers (preflight 403).

With an empty origin list every state-changing request, including sign-in, is
rejected. Production must set `NKC_AUTH_ALLOWED_ORIGINS` and keep
`NKC_AUTH_COOKIE_SECURE=true`.

## Provisioning accounts

Store password hashes produced by `UserPasswordEncoder` (NFKC-normalized,
bcrypt, `{bcrypt}` prefix, at most 72 UTF-8 bytes). Never pass passwords as
command-line arguments or store plaintext. `scripts/auth/provision-staff.sql`
shows the account and role rows to insert.
