# ADR-0009: Remove authentication mixed-profile startup rejection

## Status

Accepted — 2026-09-29, following the explicit request to remove profile guards.
Supersedes only the mixed-profile rejection in
[ADR-0008](0008-staff-credentials-and-server-side-sessions.md).

## Decision

Remove `rejectMixedProfiles` and its calls from identity configuration.
Keep the existing profile-based selection: any active `local` or `test` profile
selects development authentication settings, including when `prod` or
`production` is also active. No replacement startup profile guard is introduced.

## Consequences

Identity configuration no longer fails because production and development
profiles are combined. Such combinations use non-Secure session/CSRF cookies,
allow authenticated staff access to business endpoints, and default CORS to
`http://localhost:3000`. Deployment configuration must omit `local` and `test`
to retain Secure cookies and deny business endpoints until RBAC is implemented.

Other configuration validation, including JWT keys, session limits and explicit
CORS origins, remains unchanged. This decision does not change schema, routes,
session storage or Spring's handling of other profile-specific settings.
