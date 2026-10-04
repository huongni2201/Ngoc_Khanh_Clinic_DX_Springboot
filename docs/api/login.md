# Account login: API and operations

The clean-slate account contract follows
[ADR-0013](../adr/0013-clean-slate-application-contract.md) and the current
[API/security architecture](../architecture/05-api-and-security.md).
The supported server-side session mechanism and clean-slate role grants are
specified directly below; retired user-table ADRs are not required to operate it.

## Environment

Use Java 25 and the Maven wrapper. The existing Boot parent manages the added Redis
starter and Spring Security JOSE dependencies.

Required: `NKC_AUTH_JWT_KEY`, a Base64-encoded random key of at least 32 bytes.
An absent, malformed or short key prevents startup. Generate it using a CSPRNG
and store it in the deployment secret store. Do not put it in source control.
The zero-byte key under src/test/resources is exclusively a synthetic test fixture.

Redis connection settings: `REDIS_HOST` (localhost), `REDIS_PORT` (6379),
`REDIS_PASSWORD` (empty only for isolated local Redis). Standard Spring
`spring.data.redis.*` settings remain available, including TLS. Local Compose
adds Redis 7.4 bound to 127.0.0.1 with no eviction. Start it with
`docker compose up -d redis`; run the existing PostgreSQL service as needed.
Never expose that unauthenticated local Redis service to a shared/public network.

Choose `local` for HTTP development. Without `local` or `test` active,
default/prod/production uses Secure cookies and denies every business endpoint
until RBAC policies are implemented. Identity no longer rejects mixed profiles.
If `local` or `test` is active, even alongside `prod`/`production`, the existing
development policy applies: session and CSRF cookies omit Secure, authenticated
staff with an active role can access business endpoints, and the default CORS origin is
`http://localhost:3000`. Normal application profile settings, including datasource
and file-storage configuration, still apply. Production deployments must omit
`local` and `test` to retain the production authentication policy.

Flyway startup execution is disabled by default. Local and test configuration
enables it against their own databases. Apply production migrations through the
approved deployment migration step before starting the application; do not enable
automatic production schema changes as a workaround.

| Property | Default |
| --- | --- |
| clinic.auth.idle-timeout | PT30M |
| clinic.auth.absolute-timeout | PT8H; cannot exceed 8 hours |
| clinic.auth.issuer | nkc-clinic |
| clinic.auth.audience | nkc-staff |
| clinic.auth.allowed-origins / NKC_AUTH_ALLOWED_ORIGINS | localhost:3000 only in local/test; empty otherwise |
| clinic.auth.username-limit | 10 failures |
| clinic.auth.ip-limit | 60 attempts |
| clinic.auth.throttle-window | PT15M |
| clinic.auth.trusted-proxies | empty; comma-separated exact proxy IP addresses |

Duration properties accept ISO-8601 values. Origin entries include scheme and
port, e.g. http://localhost:3000; wildcards are rejected. SameSite=Lax assumes a
same-site frontend/backend deployment. A different-site deployment needs a
separate cookie/CSRF decision; CORS alone does not override SameSite.

The client IP defaults to the TCP peer. With trusted proxies configured, the
X-Forwarded-For chain is read from right to left through trusted addresses to the
first untrusted address. The edge proxy must sanitize inbound forwarding headers.
Review IP limits for shared clinic networks.

## HTTP contract

All paths are relative to `/api/v1/auth`. Browser fetches must use
`credentials: "include"`. Never store the session ID in JavaScript or send JWTs.

| Method/path | Input / behavior |
| --- | --- |
| GET /csrf | Public. Returns token and headerName; sets HttpOnly XSRF-TOKEN cookie. |
| POST /login | JSON username/password plus CSRF header and cookie. Sets NKC_SESSION and returns the account session view. |
| GET /me | NKC_SESSION required. Returns role grants from the session snapshot. |
| POST /logout | CSRF required. Deletes this session and clears cookie; expired/missing session is also 204. |
| POST /logout-all | Authenticated session and CSRF required. Revokes every session of the current account; 204. |

Success uses the existing ApiResponse envelope:
`{"result":"OK","code":200,"message":"...","data":{...}}`.
Login and /me data contains accountId, staffMemberId, patientId, username,
accountType, roleAssignments, idleExpiresAt and absoluteExpiresAt. STAFF has
staffMemberId and a null patientId; PATIENT has patientId and a null staffMemberId.
Assignments may be empty. Each grant contains roleId, roleCode, permissions,
grantedBy and grantedAt. Account roles have no assignment ID, department/room
scope or validity interval. Only active roles are loaded at login.

Login request: `{"username":"account.username","password":"<entered password>"}`.
Username is case sensitive, trimmed and limited to 150 characters; password is
unchanged, nonempty and at most 72 UTF-8 bytes. The API never returns
password/hash/JWT/sessionId fields.

Fetch /csrf before login, then send data.token in the header named by
data.headerName (normally X-XSRF-TOKEN). The token in JSON is masked by Spring
Security; do not substitute the cookie value. After login or logout, fetch /csrf
again before the next unsafe request because authentication clears the old token.
Old/expired session cookies do not prevent /csrf, login or logout.

NKC_SESSION is host-only, HttpOnly, SameSite=Lax, Path=/, Secure outside local/test.
Its maximum lifetime is the remaining absolute session duration; requests only
renew the Redis idle deadline. A cookie may remain after idle expiry: /me then
returns 401. Session views and auth errors use Cache-Control: no-store.

Errors use `{"result":"NG","code":401,"message":"..."}`:

- 400: structurally invalid/oversized input.
- 401: invalid credentials, locked/disabled account, inactive/suspended staff, unlinked account, invalid/expired/revoked session.
  Eligibility failures intentionally share one message.
- 403: CSRF/access denied, including a PATIENT or roleless STAFF requesting a
  local/test business endpoint, or any business endpoint without a production policy.
- 429: exceeded rate limit, with Retry-After seconds.
- 503: Redis could not complete authentication/revocation.
- 500: DB/audit failure during login; no session cookie is issued.

CSRF applies to login and logout. No HTTP Basic, form login, bearer JWT or
HttpSession authentication is supported. CORS credentials require an allowed
origin. Default/production does not authorize business APIs just because a
permission string is present in a session.

## Provision credentials

The clean-slate V001 is for a fresh database and does not create accounts,
credentials or roles. It does not upgrade a populated legacy database.

1. Apply V001 to the intended empty database using the approved deployment step.
   Existing data migration requires a separately reviewed migration/restore plan.
2. Use existing ACTIVE staff_members and active roles. Update the existing STAFF
   account where one exists; staff_member_id is unique. For new rows, allocate
   IDs using the application UUIDv7 generator.
3. Build and generate a hash with the same encoder as the application:

   PowerShell:
   ```powershell
   .\mvnw.cmd -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=target/auth-classpath.txt"
   $authClasspath = "target/classes;" + (Get-Content target/auth-classpath.txt -Raw).Trim()
   java --class-path $authClasspath scripts/auth/HashStaffPassword.java
   ```

   Run in a real terminal. Password entry is hidden, is not accepted as a command
   line argument, and is never written to disk. Treat the resulting hash as
   sensitive. It must begin with `{bcrypt}$2a$12$` (or a supported bcrypt variant).
4. Use the prepared statements in
   [provision-staff.sql](../../scripts/auth/provision-staff.sql) through an approved
   SQL client/JDBC parameter binding. Bind values instead of interpolating SQL.
   The hash, not the plaintext password, is the password_hash parameter. Verify exactly
   one intended row changed and commit credentials/grant together.
5. Staff and patient accounts may log in without a role. To access a local/test
   staff business endpoint, staff need at least one active role in the login
   snapshot. Grant roles through account_roles(account_id, role_id, granted_by,
   granted_at); do not invent permission codes. A patient account needs an
   existing patient and no staff_member_id. This guide does not register patients.
6. Credential/status/grant changes require
   `SessionRevocation.revokeAllSessions(accountId)` after DB commit. Direct SQL
   changes do not refresh or revoke existing snapshots. Account/RBAC use cases
   must invoke this contract and report/retry any Redis failure.

The exported internal UserPrincipal access boundary retains userId/staffId/
principalType accessor names for existing module consumers; their values are the
account ID, staff member ID and account type. HTTP responses use the new names.
The nullable refresh-token hash fields are unused by this session mechanism;
they do not add a refresh-token endpoint.

## Failure handling and maintenance

PostgreSQL and Redis are not one transaction. Login rechecks and locks an eligible account, then commits its ACCOUNT_LOGIN
audit event before emitting a cookie. The account schema has no last_login_at;
login does not change account updated_at or row_version. A failed commit deletes the
unissued Redis session; failed compensation logs an operational error and leaves
only an unreachable TTL-bound session.

Logout/revocation first changes Redis and records ACCOUNT_LOGOUT or
ACCOUNT_SESSIONS_REVOKED. Audit failure afterwards logs an operational
error but cannot undo revocation. A request already authenticated may finish;
requests checked after revocation are rejected. Monitor those error logs.

Redis must use noeviction and appropriate access controls. Keep the
`nkc:auth:` namespace exclusive to this application. Never log values when
debugging. Indexes are pruned on new issuance and expire by the last absolute
deadline; idle-expired index members may remain until then. Revocation versions
have no TTL and use a random initial epoch to reject stale issuance after metadata
loss. Do not restore old authentication snapshots during Redis recovery.
Observe memory growth and availability. This implementation targets
standalone Redis; Lua operations span keys and are not Redis Cluster compatible.

Changing the JWT signing key invalidates existing sessions; there is no overlapping
key rotation in this feature. JWT verification and Redis are both mandatory;
outages do not fall back to trusting cookie content or a bearer token.

Fresh-baseline rollback requires an approved restore/migration plan and
coordinated credential/session invalidation. Do not replay old Redis snapshots
after replacing the database.

## Verification and boundaries

Run `./mvnw test` and `./mvnw verify` with Docker available. Tests use PostgreSQL 18
and Redis Testcontainers, application failure injection, actual HTTP security,
and Modulith/ArchUnit. No production database is needed.

This feature does not implement business RBAC, role administration, patient
portal/SMS links, registration, password reset or remember-me.
