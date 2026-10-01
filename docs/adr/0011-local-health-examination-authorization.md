# ADR-0011: Keep health-examination authorization at the local access boundary

## Status

Accepted — 2026-10-01, at the request of the project owner.

## Context

ADR-0009 requires `CLINIC_MANAGER` authorization for the organization roster import workflow. The project owner clarified that authorization is handled locally and that health-examination use cases must not use Spring `@PreAuthorize`.

## Decision

- Keep the `CLINIC_MANAGER` requirement for roster template, upload, mapping, preview, confirmation, and cancellation operations.
- Do not enforce that requirement with `@PreAuthorize` on health-examination use cases. The local access boundary owns the role check.

This decision supersedes only the use-case `@PreAuthorize` enforcement specified in ADR-0009. All other import contracts and identity requirements remain in force.

## Consequences

- Keep authorization checks and tests at the local access boundary; do not add method-level role annotations to health-examination use cases.
- No database migration or REST contract change is required.
