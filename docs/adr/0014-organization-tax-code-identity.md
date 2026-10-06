# ADR-0014: Use tax code as the Organization business identifier

## Status

Accepted — 2026-10-06, following the owner's instruction. Supersedes the
Organization code and tax-code uniqueness rules in ADR-0013.

## Context

The previous Organization contract carried a separate `code` and treated
`taxCode` as optional and non-unique. The owner selected tax code as the unique
business identifier because each company has one tax code, and requested removal
of the separate Organization code field.

## Decision

- Remove `code` from the Organization aggregate, create/update requests and
  commands, responses, frontend transport/view models, and the `organizations`
  table. UUID `id` remains the persistence and route identity.
- Keep `taxCode` nullable as in the existing contract, but enforce a unique
  database constraint and check for duplicates in Organization use cases.
  PostgreSQL's unique constraint permits multiple `NULL` tax codes; every
  non-null value is unique.
- Organization search and sorting use `taxCode` and `name` instead of `code`.
- The clean-slate V001 has not been deployed or run. Remove the column directly
  from V001 and add the tax-code constraint there; do not add V003.

## Consequences

- Create/update and response DTOs no longer expose `code`; frontend Organization
  forms, detail views and transport types use the same contract.
- A duplicate non-null tax code is a conflict, including a concurrent insert
  rejected by the PostgreSQL constraint.
- Existing status, contact channels, versioning, audit transaction boundaries
  and UUID identity are unchanged.
- The API contract is incompatible with clients that still send or expect the
  Organization `code` field.

## Related decisions

- [ADR-0013](0013-clean-slate-application-contract.md) remains the historical
  clean-slate contract except for the Organization code and tax-code uniqueness
  decisions superseded here.

