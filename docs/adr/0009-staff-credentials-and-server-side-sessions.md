# ADR-0009: Staff credentials and server-side JWT sessions

## Status

Accepted — 2026-09-28, implementing the approved staff-login plan on
`feature/TungTQ/staff-login`.

The mixed-profile startup rejection below is superseded by
[ADR-0010](0010-remove-auth-mixed-profile-rejection.md); other decisions remain in force.
STAFF-only eligibility, the required role, JWT identity and login route are
superseded by [ADR-0011](0011-shared-user-login.md).

## Context

The provider/subject identity baseline does not implement the requested staff
username/password login. The browser must receive an opaque session identifier,
while the backend retains a signed, scoped authorization snapshot. Patient access
through SMS links and business RBAC are separate features.

## Decision

- V002 replaces `users.auth_provider/auth_subject` and their unique constraint
  with nullable `username varchar(200)` (unique, case sensitive) and `password text`.
  This supersedes that credential representation in table-design-v2.11. Principal
  type, staff/patient relations and existing constraints remain intact.
- Accounts without credentials cannot log in. Only active STAFF users linked to
  active staff with an active, currently effective role assignment are eligible.
- Usernames are stripped of surrounding whitespace; passwords are never trimmed.
  Passwords use application-generated `{bcrypt}` hashes with cost 12 and at most
  72 UTF-8 bytes. Missing accounts/unsupported hashes perform dummy verification.
- The browser receives a cryptographically random 32-byte Base64URL session ID
  only through the HttpOnly `NKC_SESSION` cookie. Redis keys contain SHA-256 of
  this ID. Credentials, JWTs and session IDs are excluded from logs and responses.
- Redis standalone stores HS256 JWTs and session metadata. JWTs require a Base64
  environment key decoding to at least 32 bytes, issuer, audience, subject/user ID,
  staff ID, username, STAFF principal type, issue/expiry times, jti and scoped role
  assignments. No fallback secret or direct bearer authentication is provided.
- A session expires after 30 idle minutes or 8 absolute hours, whichever is sooner.
  Each request verifies the JWT and atomically validates/touches the Redis session.
  Effective intervals are half-open: [valid_from, valid_to). Expired assignments
  are removed from the request principal; no remaining role invalidates the session.
  New DB permissions are never silently merged into an existing session snapshot.
- Multiple device sessions are allowed. Successful login replaces only the
  session cookie supplied by the same browser. Logout removes one session;
  logout-all atomically increments the user's revocation version and deletes all
  indexed sessions. Login reads that version before loading credentials/roles.
  Lua rejects stale issuance and never recreates missing sessions during touch.
- `identity::access` exports the application principal under
  `application.query.access`; `identity::sessions` exports only
  `application.port.SessionRevocation`. Features changing passwords,
  account status or grants must revoke sessions after their DB commit.
- Business RBAC is deferred. Default/production configuration denies business
  endpoints even for authenticated staff. Only local/test profiles allow
  authenticated development access. If local/test is active alongside a
  production profile, local/test development settings apply, as specified by
  ADR-0010. Role permissions are not flattened into Spring authorities.
- Cookie-based authentication requires CSRF on login and unsafe requests.
  CookieCsrfTokenRepository supplies a masked token through JSON; the browser sends
  the named header. Login/logout clear the CSRF cookie, requiring a new token.
  Session and CSRF cookies are host-only, Path=/, SameSite=Lax, Secure outside
  local/test. CORS credentials require an explicit allowlist.

## Transactions and failure modes

1. Resolve the account identity, read Redis revocation version, then read credentials
   and assignments in a short repeatable-read DB transaction.
2. End the transaction before bcrypt verification, JWT signing and Redis issuance.
3. Commit last_login_at and success audit together in a separate DB transaction.
   Only then recheck the session version and emit the session cookie.
4. A DB failure compensates by deleting the unissued Redis session. If deletion
   also fails, log an operational error; the unexposed session expires by TTL.
   There is no distributed transaction and no login outbox/reissue process.
5. Redis failures deny authentication and return 503. Failed revocation cannot
   report successful logout. Once revocation succeeds, a later audit failure is
   logged and does not restore sessions or misreport their state.
6. A request that finished authentication before revocation may finish execution.
   A request checked after revocation must fail. Direct DB edits do not revoke
   snapshots automatically.
7. Audit writes use the published `shared::audit` contract. Failure logs contain
   correlation IDs, never credentials, token material or patient payloads.

## Consequences and operation

Redis is an authentication dependency, not an optional performance cache. Use
standalone Redis with noeviction, controlled access and monitored capacity. Session
indexes are sorted by absolute expiry, pruned on issuance and expire with their
last member; idle-expired references are therefore retained for at most 8 hours.
Revocation versions have no TTL and begin from a random epoch, so an in-flight
login cannot match a reset version after Redis data loss. Redis loss requires users to authenticate again;
there is no stateless fallback.

JWT-in-Redis costs storage and signature verification compared with storing a
plain principal. It is selected to meet the agreed signed snapshot contract, not
to claim statelessness. Central revocation and scoped snapshot semantics are the
benefits of this design. It does not implement business authorization.

Rate limiting uses Redis: 10 failed attempts per username and 60 attempts per IP
per 15-minute window, with Retry-After. Shared clinic NATs require capacity tuning.
Forwarded client IPs are trusted only through explicitly configured proxy addresses.

Existing provider/subject data requires a verified backup before V002. Recreating
the dropped columns does not recover their values. Restore from backup under an
approved migration/rollback procedure. No default accounts or passwords are seeded.

## Implementation boundaries

Identity follows the `healthexamination` package layout subject to project rules:
API controller/request, application command/query/port/response/usecase, domain
entity/valueobject/repository, and persistence converter/mapper/projection/record/
repository. The HTTP-specific cookie and trusted-proxy adapters remain in
`api.http`; Spring/security/Redis configuration and adapters remain under
infrastructure. Only needed packages are created. Use cases own their orchestration
and call explicitly configured transaction operations for short database work;
password hashing, JWT signing and Redis operations remain outside database
transactions. System times use Clock/Instant and timestamptz(3), as in ADR-0006.

Redis starter and Spring Security JOSE use the Spring Boot 4.1.1 dependency
management already in this project. Java 25 compile/runtime and PostgreSQL 18 /
Redis 7.4 integration tests validate the actual resolved stack. No JPA or alternate
JWT library is introduced.

See [staff login operations and API](../api/login.md).
