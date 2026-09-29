# Staff login: API and operations

Branch: `feature/TungTQ/staff-login`. Decision:
[ADR-0008](../adr/0008-staff-credentials-and-server-side-sessions.md).

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

Choose `local` for HTTP development. Default/prod/production uses Secure cookies
and denies every business endpoint until RBAC policies are implemented. Combining
prod/production with local/test fails startup. Normal local application settings,
including datasource and file-storage configuration, still apply.

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
| POST /staff/login | JSON username/password plus CSRF header and cookie. Sets NKC_SESSION and returns staff session view. |
| GET /me | NKC_SESSION required. Returns effective assignments from the session snapshot. |
| POST /logout | CSRF required. Deletes this session and clears cookie; expired/missing session is also 204. |
| POST /logout-all | Authenticated session and CSRF required. Revokes every session of the current user; 204. |

Success uses the existing ApiResponse envelope:
`{"result":"OK","code":200,"message":"...","data":{...}}`.
Login and /me data contains userId, staffId, username, principalType,
roleAssignments, idleExpiresAt and absoluteExpiresAt.
Each assignment contains assignmentId, roleCode, permissions, departmentId,
roomId, validFrom, validTo. Permissions remain attached to their scope.

Login request: `{"username":"staff.username","password":"<entered password>"}`.
Username is case sensitive and trimmed; password is unchanged, nonempty and at
most 72 UTF-8 bytes. The API never returns password/hash/JWT/sessionId fields.

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
- 401: invalid credentials, ineligible staff account, invalid/expired/revoked session.
  Eligibility failures intentionally share one message.
- 403: CSRF/access denied or business endpoint without a production RBAC policy.
- 429: exceeded rate limit, with Retry-After seconds.
- 503: Redis could not complete authentication/revocation.
- 500: DB/audit failure during login; no session cookie is issued.

CSRF applies to login and logout. No HTTP Basic, form login, bearer JWT or
HttpSession authentication is supported. CORS credentials require an allowed
origin. Default/production does not authorize business APIs just because a
permission string is present in a session.

## Provision credentials

Migration V003 does not create accounts, usernames, passwords or roles.

1. Back up the database and provider/subject mappings before V003 on any populated
   database. Validate the backup restore procedure. This branch only tests migration
   in disposable containers; deployment migration is a separate operator action.
2. Use existing active staff and role records. Keep the user ID of an existing STAFF
   account wherever one exists. For new rows, allocate IDs using the existing
   application UUIDv7 generator.
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
   The hash, not the plaintext password, is the password parameter. Verify exactly
   one intended row changed and commit credentials/assignment together.
5. Assign at least one existing active role with a current [valid_from, valid_to)
   interval and the intended department/room scope. Do not add invented permission
   codes. Empty credentials and patient accounts cannot use staff login.
6. Credential/status/grant changes on existing accounts require
   `SessionRevocation.revokeAllSessions(userId)` after DB commit. Direct SQL
   changes do not revoke existing snapshots. Future account/RBAC use cases must
   invoke this public contract and report/retry any Redis failure.

## Failure handling and maintenance

PostgreSQL and Redis are not one transaction. Successful login audit and
last_login_at commit before a cookie is emitted. A failed commit deletes the
unissued Redis session; failed compensation logs an operational error and leaves
only an unreachable TTL-bound session.

Logout/revocation first changes Redis. Audit failure afterwards logs an operational
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

Dropping/recreating provider/subject columns cannot restore lost values. A rollback
needs the pre-migration backup and an approved restore/migration procedure, with
credential and session invalidation coordinated. Do not edit V001/V002 checksums.

## Verification and boundaries

Run `./mvnw test` and `./mvnw verify` with Docker available. Tests use PostgreSQL 18
and Redis Testcontainers, application failure injection, actual HTTP security,
and Modulith/ArchUnit. No production database is needed.

This feature does not implement staff business RBAC, role administration,
patient login/SMS links, registration, password reset, remember-me or Next.js UI.
