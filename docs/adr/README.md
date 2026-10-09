# Accepted architecture decisions

These records explain accepted decisions. Use [architecture](../architecture/README.md)
for current workflows and [API inventory](../api/clean-slate-migration.md) for
implemented handlers and authorization gaps. Acceptance does not claim deployment
or complete implementation.

| ID | Decision | Accepted |
|---|---|---|
| [0012](0012-clean-slate-module-boundaries.md) | Module ownership and published boundaries | 2026-10-03 |
| [0013](0013-clean-slate-application-contract.md) | Database/application baseline and owner amendments | 2026-10-04 |
| [0014](0014-session-cookie-redis-login.md) | Session-cookie login backed by Redis | 2026-10-05 |
| [0015](0015-endpoint-permission-rbac.md) | Per-endpoint permission enforcement | 2026-10-07 |
| [0016](0016-organization-tax-code-identity.md) | Organization tax-code identity | 2026-10-06 |

The Organization decision was renumbered from the duplicate ADR-0014 on
2026-10-08. Its acceptance date and business decision are unchanged. Retained IDs
need not follow acceptance-date order; future decisions use the next unused ID.

Unaccepted rendering/storage and authorization choices live in
[Open items](../architecture/07-open-items.md). They do not amend these decisions.
