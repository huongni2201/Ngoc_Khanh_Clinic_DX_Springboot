# Remove Organization Code — Design

## Goal

Remove the Organization `code` field from the business model, HTTP contract,
frontend, and PostgreSQL schema. Convert the remaining aggregate record,
`Organization`, to a stateful class with invariant-preserving domain methods.

## Current context

- `Organization` is the only record under `domain.aggregate`; the batch,
  participant, and health-examination-record aggregates are already classes.
- The current contract treats organization code as unique, and the `code`
  column is `NOT NULL UNIQUE` in V001.
- Organization code appears in create/update requests and responses, duplicate
  checks, list search/sort, MyBatis mapping, and frontend types and forms.
- V002 is already present in the worktree and adds batch soft deletion.
- Existing user changes in both repositories must remain intact.

Owner amendment (2026-10-06): `taxCode` replaces `code` as the unique business
identifier. It remains nullable under the existing contract; non-null values must
be unique. The owner confirmed V001 has not been applied to or run against a
database, so the baseline will be edited directly and no V003 will be added.

## Decisions

### Domain aggregate

- Replace the `Organization` record with a `public final class`.
- Keep its ID, other organization data, validation, status and row-version
  invariants, `create`/`restore` factories, and fluent accessors.
- Remove `code` from the aggregate and its factories.
- Keep `taxCode` optional and enforce uniqueness when present.
- Make `updateDetails` and `deactivate` mutate aggregate state, following the
  existing stateful aggregate pattern. Application use cases persist the
  mutated aggregate. Capture before-state audit metadata before mutation.
- Do not change the other aggregate classes or unrelated code fields.

### HTTP and frontend

- Remove organization code from create/update request DTOs, commands, response
  DTOs, frontend transport/view types, Zod schemas, forms, fixtures, and UI.
- Replace duplicate-code checks with duplicate-tax-code checks and map those
  conflicts to the same safe 409 contract.
- Organization search and sorting use `taxCode` and name. Allowed sort keys are
  `id`, `taxCode`, and `name`.
- Remove the organization-code detail row and describe the remaining search
  behavior accurately in the UI.
- This is a breaking API change; backend and frontend must ship against the same
  contract.

### Persistence and history

- Remove organization code from persistence records, converters, mapper methods,
  and SQL statements.
- Remove `public.organizations.code` from V001 and add a unique constraint on
  `public.organizations.tax_code`; leave V002 unchanged and do not add V003.
- No database has run these migrations, so there are no organization-code values
  to migrate or recover.
- New audit snapshots record taxCode and version instead of code. Keep
  existing audit events unchanged under the append-only audit contract; old
  metadata is not rewritten.

### Documentation

- Add ADR-0014 to supersede the Organization identifier/uniqueness portion of
  ADR-0013 without rewriting the accepted historical decision.
- Update current domain/workflow and API contract documentation to describe the
  code-less organization contract and name-only search.

## Verification

- Domain tests cover organization construction, detail updates and deactivation.
- Backend tests cover the revised HTTP fields, taxCode/name search and sorting, audit
  metadata, persistence mapping, and PostgreSQL migration behavior.
- Frontend tests cover organization forms, request/response types, details, and
  list query parameters.
- Run backend `./mvnw verify` (or `.\mvnw.cmd verify` in PowerShell) and
  frontend `pnpm lint`, `pnpm typecheck`, `pnpm test`, and `pnpm build`.
- Report any unavailable PostgreSQL/Docker checks or other skipped commands.

## Risks and boundaries

- The API change breaks existing clients that still send or expect organization
  code; coordinated frontend/backend release is required.
- V001 is edited before deployment; there is no applied organization schema to
  lose or roll back.
- Historical audit JSON remains append-only and may retain earlier code values.
- Batch codes, catalog service codes, and the numeric HTTP envelope `code` are
  outside this change.
