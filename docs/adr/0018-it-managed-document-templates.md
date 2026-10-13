# ADR-0018: Let IT maintain document templates

## Status

Accepted — 2026-10-13, by the owner's instruction to remove template editing if
implemented and update the documentation. This decides template maintenance only;
it does not accept the remaining proposed rendering/storage choices in
[ADR-0017](0017-document-rendering-and-template-lifecycle.md).
It supersedes the expectation of future template upload/publish APIs and screens,
including that part of frontend ADR-0010's consequences.

## Context

Templates change infrequently. Their placeholders, page layout and contract
versions depend on the rendering code. A user-facing editor or upload/publish
workflow would require validation and support without enough operational benefit.
The current backend and frontend expose printing, not template maintenance.

## Decision

- Remove template editing, upload and publication from the product scope and
  implementation backlog. This applies to document templates, not the supported
  Excel roster or service-reconciliation download/import workflows.
- Business staff request changes and the responsible business/clinical owner
  approves the content. IT edits the template, updates rendering code/contracts
  when needed, validates the resulting PDF and releases the new version.
- Keep template/version storage, effective-version resolution, lifecycle
  protections and version pinning for print jobs in `document`. Business modules
  retain ownership of their source data and placeholder contracts.
- Each replacement uses a new template version and a distinct immutable file
  reference. Never overwrite a referenced file, edit issued content or delete
  historical references. Follow the existing lifecycle and effective-period
  constraints; use additive Flyway migrations for database changes.
- IT validates allowed/required placeholders, Vietnamese text, missing/long data,
  page count, paper size, orientation and PDF output before activation. Paper or
  booklet layout changes also need a sample print on the clinic's printer.

## Consequences and current limits

- No template-maintenance UI, public API, new permission or replacement admin
  tool is required. Staff continue to preview, download and print through the
  supported document workflows.
- IT changes go through code review and a reviewed release/migration procedure.
  There is currently no general template-update command. The starter seeder only
  inserts version 1 when the document type has no versions; changing the starter
  and restarting does not register a replacement. Existing databases need an
  explicit version/file insertion and effective-period transition. Packaged
  resources need distinct paths retained for referenced historical versions.
- Published/issued history remains protected. Blank examination forms are still
  rendered on demand with the currently effective version, and bulk jobs pin one
  version. This decision does not add stored PDFs or byte-identical reprints of
  official documents; those remain an
  [open issuance contract](../architecture/07-open-items.md#official-document-issuance).
- This change requires documentation only: runtime modules, database schema,
  migrations and public HTTP APIs remain unchanged.
