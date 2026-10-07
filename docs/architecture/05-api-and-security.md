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
Use explicit expected versions on mutable configuration updates. A stale update rolls back instead of overwriting data.

## Authentication and authorization

The `accesscontrol` module authenticates with an opaque session cookie whose ID
maps to a server-side JSON session snapshot in Redis (no JWT). Each business
endpoint has one permission in `public.permissions` (`http_method` and the
controller route template in `endpoint`); it requires a STAFF account holding
that permission of the SRS Permission Matrix. Endpoints without a stored
permission are denied and unknown URLs return 404. Rate limiting and revocation
are not yet implemented and block go-live. Credentials and session IDs are never returned or logged. The decisions
are [ADR-0014](../adr/0014-session-cookie-redis-login.md) and
[ADR-0015](../adr/0015-endpoint-permission-rbac.md); the HTTP contract is
[login operations](../api/login.md).

## Audit and healthcare data

Audit owns append-only audit_events, exposed through audit::recording. Capture
account actor, action, resource type/ID, occurredAt and concise safe metadata.
Business mutations and required audit commit together; audit failure rolls back.
Login audit commits before the Redis session is written (see ADR-0014).

Application owns operational use-case events; controllers add useful HTTP context.
Follow [the logging policy](../../PROJECT_RULES.md#27-logging) for levels, safe
fields, exception ownership and transaction-aware wording. Logs do not replace
audit or prove commit. Public use-case contracts follow
[application Javadoc rules](../../PROJECT_RULES.md#application-comments-and-javadoc).

Store secrets in environment/approved storage. Backend authorization is authoritative.
Preserve exact-version patient releases independently of notification delivery.
Rendering documents does not prove printing completed.
