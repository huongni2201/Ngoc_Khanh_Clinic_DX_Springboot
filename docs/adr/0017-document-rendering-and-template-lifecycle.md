# ADR-0017: Render documents from versioned Word templates through a PDF sidecar

## Status

Proposed — 2026-10-13, pending owner acceptance. Implemented for one workflow (print one
Participant's blank examination form) following plan `20261012-examination-form-printing` v2.
It does not accept the official-document issuance pipeline that
[Open items](../architecture/07-open-items.md#official-document-issuance) still lists.

## Context

The clinic prints the periodic health examination form (Mẫu số 03, Phụ lục XXIV, TT 32/2023/TT-BYT)
for every Participant of a corporate batch. The layout is a folded A3 booklet that the clinic wants
to change without a code change or a deployment. The document module held schema records only.

## Decision

- **Word is the template format.** A template version is a `.docx` holding `{{key}}` placeholders.
  The document type's contract (`DocumentTypeContract`) lists the allowed keys, owned by the business
  module that implements it (`PeriodicFormBlankContract` in `healthexamination`, version 1). The
  document module never imports a business module; it receives contracts by injection.
- **Contract evolution.** An optional placeholder keeps the contract version; renaming, removing,
  retyping or adding a required placeholder raises it. "The template must contain a placeholder"
  (`required`) is separate from "the data must exist": missing Participant data prints as a dotted line.
- **Filling is done in the JDK.** `DomDocxTemplateEngine` edits the OOXML parts directly. A value is
  only inserted as text, so nothing is evaluated; DOCTYPE is refused, entries and inflated size are
  capped, and one value is capped at 1,000 characters. poi-tl was not chosen because its spike could
  not run; it can be reconsidered when repeating table rows or images are needed.
- **PDF is made by Gotenberg (LibreOffice)** over HTTP (`/forms/libreoffice/convert`), 15 s timeout,
  any failure is a 503. The sidecar must run without outbound network in production.
- **Version lifecycle.** `DRAFT → PUBLISHED | DISCARDED`. The effective version is the `PUBLISHED`
  one whose period contains the render instant. File, layout and contract version never change after
  creation; a published version may only be retired, or cancelled before it takes effect; published
  periods of one template never overlap; a referenced file cannot change or be deleted (V004
  triggers). Page size and orientation are read from the `.docx`, not typed in.
- **Starter template.** The code ships `starter-v1.docx`; startup seeds it as `PUBLISHED` version 1
  only when the type has no version at all. After that the templates belong to the clinic.
- **Rendering on demand, nothing stored.** The blank form is a working document, not an issued one:
  no PDF is stored and `issued_representations` is not written. The source facts are read in one short
  read-only transaction; conversion runs with no transaction open. Audit records identifiers and the
  template version, never personal data.
- **Overflow is an error.** A rendered PDF with more pages than the version's `pages_per_record` is
  refused with 409 instead of printed.
- **Permission.** `PARTICIPANT_EXAMINATION_FORM_PRINT` protects only the single-print endpoint
  ([ADR-0015](0015-endpoint-permission-rbac.md)); bulk printing has its own (see Addendum).
- **Migrations are additive.** V004, V005 and V006 extend the V001–V003 baseline instead of being folded into it.

## Addendum: bulk printing (proposed)

- **Background job, not a request.** A print job holds record ids and a context, never personal data. A
  single worker per instance claims jobs from PostgreSQL with `FOR UPDATE SKIP LOCKED` and a lease
  (`lock_token`, `locked_until`); every write checks the lock, so a stalled worker cannot overwrite a
  successor. A job is taken at most 3 times.
- **Template pinned per job.** The version is resolved once and used for every record of the job.
- **Parts.** Up to 200 records are merged per PDF part with Gotenberg's PDF merge route (no new Java
  library); a one-record part is stored as rendered. Parts are temporary `LOCAL` files, deleted 24 hours
  after the job ends, after which the job is `EXPIRED`.
- **Skip, don't fail.** A record that is gone, cancelled, too long or fails to render is skipped with a
  reason; the job fails only when nothing can be printed or the renderer, storage or template is
  unavailable.
- **Access.** Only the account that created the job can read or download it (others get 404). Three
  permissions, one per endpoint: `PARTICIPANT_EXAMINATION_FORM_BULK_PRINT`, `DOCUMENT_RENDER_JOB_READ`,
  `DOCUMENT_RENDER_JOB_DOWNLOAD` (V006). Limits: 1000 records per job, 3 active jobs per account.
- **Not decided:** parallel rendering, object storage for parts, longer retention, notification on completion.

## Consequences

- A Word file can be replaced without a deployment once the upload/publish API exists; until then
  only the starter is available and a new version needs a reviewed SQL or code change.
- Output follows LibreOffice's layout, which can differ from Word's; a template must be checked in the
  PDF, not in Word.
- Gotenberg is a new runtime dependency and a single point of failure for printing.
- The page-count check reads page objects from uncompressed PDFs written by LibreOffice. If the sidecar
  ever returns compressed object streams the check is skipped and logged, not guessed.

## Barcode addendum

A picture whose alternative text is `{{@key}}` is a picture placeholder. The DOM engine links a new
image part from the same drawing, so the frame keeps its size and position; without a value the
drawing is removed. Pictures are Code 128 barcodes drawn by an in-house encoder (JDK `ImageIO`, no new
dependency) to the exact pixel size of the frame at 1200 dpi, with whole-pixel bars. Gotenberg is
asked for lossless image compression and no resampling. See
[examination-form print](../api/examination-form-print.md#mã-vạch).

## Not decided here

Template upload/publish API and UI, bulk print jobs, per-organization templates, object
storage (`S3` is only an allowed provider value), persisted PDF snapshots and official issuance.
