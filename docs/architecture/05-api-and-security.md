# API, security and audit

## HTTP boundaries

Versioned REST routes use /api/v1. Bean Validation on api/request plus @Valid
owns HTTP structural constraints. Application/domain owns business preconditions
and invariants. Controllers call use cases, return their application responses
with the shared envelope, and contain no persistence or transaction logic.

Endpoint Javadoc describes behavior, every parameter and non-void result.
Use bounded pagination and allowlisted sort keys. Stable API contracts do not
expose persistence records or domain internals. Central error handling distinguishes
validation, not found, business rules, conflicts, concurrency, authentication,
access denial, dependencies and unexpected errors without leaking SQL/stack traces.

Breaking clean-slate fields/routes are listed in [the API migration](../api/clean-slate-migration.md).
Use explicit expected versions on preview/confirmation/cancellation and mutable
configuration updates. A stale update rolls back instead of overwriting data.

## Authentication and authorization

Preserve secure opaque NKC_SESSION cookies, Redis-held signed session snapshots,
CSRF on login/unsafe requests, explicit CORS origins, rate limiting and revocation.
The JWT key comes from environment and must meet configured strength. Username
limit follows accounts.username (150 characters); passwords retain bcrypt policy.
Credentials/tokens are never returned or logged.

Default/production currently denies business endpoints until explicit RBAC
policies are implemented. Local/test requires authenticated STAFF with an active
role for business access, including CLINIC_MANAGER at the local roster-import
boundary. Patients and roleless staff do not gain business access merely by login.
No method-level @PreAuthorize is added to health-examination use cases.

Existing profile selection remains: local/test settings apply if those profiles
are active even alongside prod/production. Production must omit local/test.
This is documented existing behavior, not a new production bypass.

Permission updates must revoke existing snapshots after database commit. Direct
SQL grants/status changes do not silently refresh sessions. See
[login operations](../api/login.md) for actual protocol/configuration and failure handling.

## Audit and healthcare data

Audit owns append-only audit_events, exposed through audit::recording. Capture
account actor, action, resource type/ID, occurredAt and concise safe metadata.
Business mutations and required audit commit together; audit failure rolls back.
Authentication's Redis/database compensation remains documented in login operations.

Controllers/use cases use parameterized logs: DEBUG for reads, INFO for meaningful
mutations. Log identifiers/counts, never request/response payloads, CCCD, credentials,
clinical content or full search strings. Logs do not replace audit or prove commit.

Store secrets in environment/approved storage. Backend authorization is authoritative.
Preserve exact-version patient releases independently of notification delivery.
Rendering documents does not prove printing completed.
