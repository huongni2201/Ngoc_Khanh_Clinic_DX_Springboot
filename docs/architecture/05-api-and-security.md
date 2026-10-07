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
maps to a server-side JSON session snapshot in Redis (no JWT). Business routes
under `/api/v1/**` require a logged-in STAFF account; PATIENT accounts can log in
but receive 403. The Participant list, template and import routes are the first
guarded by permission (`HEALTH_EXAMINATION_PARTICIPANT_READ` and
`HEALTH_EXAMINATION_PARTICIPANT_IMPORT`, granted to ADMIN and CLINIC_MANAGER by migration
V003). Manual add, detail, edit, cancel and reactivate (`POST`, `GET`, `PUT`, `DELETE` on
`.../participants[/{participantId}]` and `POST .../participants/{participantId}/reactivate`) need `HEALTH_EXAMINATION_PARTICIPANT_MANAGE`, granted to
the same roles by V004; only that detail response carries the full CCCD. The permission set is a login snapshot, so an existing session must sign in again
to receive them. The examination detail and report routes use `HEALTH_EXAMINATION_SERVICE_READ`,
`HEALTH_EXAMINATION_SERVICE_RECONCILE` and `HEALTH_EXAMINATION_REPORT_READ`, granted to the same roles by V005
([contract](../api/examination-details-and-report.md)). Per-endpoint RBAC for the other routes, rate limiting and revocation
are not yet implemented and block go-live. Credentials and session IDs are never returned or
logged. The decision is [ADR-0014](../adr/0014-session-cookie-redis-login.md);
the HTTP contract is [login operations](../api/login.md).

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
