# ADR-0014: Session-cookie login with Redis session snapshots

## Status

Accepted — 2026-10-05. Owner requested a redesign of login and access control
and the rename of the `identity` module.

## Context

The previous identity implementation (opaque cookie, signed JWT held in Redis,
CSRF token, rate limiting, generation-based revocation, logout-all) and its
tests were removed by the owner. The feature is now named "User & Access
Control". Business controllers already read the authenticated `UserPrincipal`.

## Decision

### Module

- Rename the `identity` module to `accesscontrol`
  (`com.ngockhanh.clinic.accesscontrol`). It owns `accounts`, `staff_members`,
  `roles`, `permissions`, `account_roles` and `role_permissions`.
- `accesscontrol::access` publishes `UserPrincipal`.

### Session protocol

- `POST /api/v1/auth/login` and `POST /api/v1/auth/logout` only. The HTTP
  contract is in [login operations](../api/login.md).
- On valid credentials the backend creates a random 32-byte session ID
  (base64url, 43 characters) and returns it only in an HttpOnly cookie. The body
  never contains the session ID.
- Redis key `nkc:auth:session:{sha256(sessionId)}` holds a JSON session snapshot:
  account ID, account type, staff/patient ID, username, active roles with their
  permission codes, creation time and absolute expiry. Hashing the ID keeps
  usable cookies out of Redis dumps.
- Redis set `nkc:auth:account-sessions:{accountId}` indexes an account's
  sessions so revocation and logout-all can be added without a data change.
- Each request reads the snapshot from the cookie, rejects it after absolute
  expiry and extends the key TTL to `min(idle timeout, absolute expiry - now)`.
- Logging in with an existing session cookie deletes the old session first
  (prevents orphaned sessions and session fixation).

### Why no JWT

The snapshot lives only on the server and the client holds an opaque ID. A
signature would not protect anything an attacker able to write Redis could not
already replace, while it would add a signing key, a JOSE library and its tests.

### Timeouts

Idle timeout 30 minutes, absolute lifetime 8 hours.

- Idle 30 minutes: front-desk and consultation workstations are shared and often
  left unattended. Expiring an inactive session limits how long someone else can
  use an open session to read patient data (OWASP ASVS suggests 15–30 minutes
  for high-risk applications).
- Absolute 8 hours: one working shift. Even an active user logs in again each
  shift, so role and permission snapshots refresh, and a leaked cookie has a hard
  limit.
- A fixed 8-hour session would leave an unattended workstation logged in for up
  to 8 hours. A sliding 30-minute session without a cap would never expire for
  an active user and would never refresh permissions.
- The cookie has no Max-Age (browser-session cookie); Redis decides real expiry.

### Cookie and request protection

- Cookie `__Host-NKC_SESSION` when `cookie-secure` is true, `NKC_SESSION` for
  local HTTP; HttpOnly, SameSite=Lax, Path=/, no Domain.
- POST/PUT/PATCH/DELETE require an `Origin` (or `Referer`) in the configured
  allow-list; otherwise 403. No Spring CSRF token is used.
- `Authorization: Bearer` is not accepted.

### Credentials

- Username and password are compared exactly as submitted; username is
  case-sensitive, matching the `accounts.username` unique constraint.
- Passwords are NFKC-normalized and hashed with bcrypt (`{bcrypt}` prefix).
  Inputs longer than 72 UTF-8 bytes cannot be encoded and fail login with 401.
- Unknown user, wrong password and ineligible account return the same 401 body.

### Authorization

- Authorities: `ACCOUNT_STAFF` or `ACCOUNT_PATIENT` derived only from account
  type, plus `ROLE_<roleCode>` and `PERM_<permissionCode>`. Prefixes prevent a
  permission code from impersonating an account-type authority.
- Until per-endpoint RBAC exists, `/api/v1/**` requires `ACCOUNT_STAFF`.
  PATIENT accounts can log in but receive 403 on business routes.

### Audit order

- Successful login: `ACCOUNT_LOGIN` audit commits first; writing the Redis
  session is the last step. A Redis failure returns 503 without a cookie.
- Failed login for an existing account: `ACCOUNT_LOGIN_FAILED` in its own
  transaction, actor NULL, reason metadata only, no credentials. Unknown
  usernames are logged, not audited (`audit_events.resource_id` is required).
- Logout of a valid session: `ACCOUNT_LOGOUT`.

## Go-live blockers

- [ ] Login rate limiting (username and IP).
- [ ] Revocation: logout-all and automatic revocation on account lock or grant change.
- [ ] CSRF: confirm Origin check plus SameSite=Lax suffices for the deployment, or
      add a CSRF token; configure production allowed origins.
- [ ] Per-endpoint RBAC with `PERM_` authorities.

## Accepted risks

- No dummy password hash: an unknown username responds faster than a wrong
  password, which can reveal valid usernames. Usernames are internal staff codes;
  rate limiting mitigates this.
- If Redis fails after the login audit commits, an `ACCOUNT_LOGIN` row exists for
  a login that returned 503. This avoids compensating deletes.

## Consequences

Module inventory, Modulith verification and record ownership use
`accesscontrol`. No database migration is required. No refresh-token endpoint
is introduced.
