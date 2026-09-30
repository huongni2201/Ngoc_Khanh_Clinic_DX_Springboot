# ADR-0009: Organization roster import rules

## Status

Accepted — 2026-09-30, based on the requester's explicit product decisions.

## Context

The current FINAL requirement/use-case/table-design copies describe confirmation of valid employee rows and require an employee code. The requester has clarified that roster import must be all-or-nothing and the supplied roster workbook has no employee-code column. The sample is a separate 16-column roster format, independent of the Mẫu số 03 print layout.

The current import tables and `HealthExaminationParticipant` require a technical `participant_code`. The roster model also separates employment `occupation_snapshot` from Mẫu số 03 administrative occupation. Existing import jobs support other import types, so roster rules must not change RESULTS import semantics.

## Decision

- For `PARTICIPANT_LIST`, any blocking row error rejects the entire confirmation. Confirm writes zero business rows when any blocking error exists. Optional-field warnings do not block. `PARTIAL` remains available to other import types but is never used for roster import.
- The import template has 16 columns matching the supplied corporate roster workbook and remains separate from Mẫu số 03. The upload contains no employee-code field.
- Resolve an existing person only by exact CCCD within the Organization. CCCD is required under the current baseline; passports and other identifiers are not accepted.
- Generate new persisted IDs with the existing application UUIDv7 generator. To meet the existing non-null `participant_code` column without exposing personal data or inventing a business employee code, set a new participant's technical `participant_code` to its generated UUID string. Preserve the code of an existing participant. Do not display this technical value as “Mã NV”.
- Re-import creates, updates, or leaves unchanged the matching Organization participant and batch membership. Preserve the Patient link, membership IDs, service assignments, and prepared/issued HealthExaminationRecord snapshots. Identity conflicts block the complete file; import never relinks a Patient.
- On re-import into the same Organization/batch, a blank optional field does not erase an existing nonblank value; a supplied nonblank value updates it. Clearing an optional value requires a separate authorized edit. A new batch membership takes optional values from that import only.
- The sample's “Ghi chú” is stored as a typed batch roster snapshot (`roster_note_snapshot`), separate from the Mẫu số 03 administrative/clinical record. It is visible only in an authorized roster/import view and is never added to general participant-list responses by default.
- Roster edits are allowed in `DRAFT`, `READY`, and `IN_PROGRESS`. Reject `RESULT_PROCESSING`, `FINALIZED`, `CLOSED`, and `CANCELED` batches.
- `CLINIC_MANAGER` is required for template download, upload, mapping, preview, confirmation, and cancellation. Other import permissions remain separate.

## Consequences

- Import validation and confirmation differ from the previously written partial-success rules; this decision takes precedence for roster import only. The remaining business rules in the FINAL documents remain in force.
- A new Flyway migration adds `roster_note_snapshot`; the existing V001 migration is not edited.
- The 16-column workbook must be corrected before confirmation when any required row field is missing. The supplied workbook currently contains an incomplete numbered row and therefore must preview as invalid.
- The downloaded FINAL DOCX copies are outside this repository and were not edited. Their roster-import clauses need a later document revision to match this accepted decision before product release.

## References

- `docs/plans/2026-09-29-employee-import-implementation-plan.md`
- `requirement-v2.5_FINAL.docx`, `use-case-v2.7_FINAL.docx`, `table-design-v2.11_FINAL.docx` (source copies supplied outside the repository)
- ADR-0001, ADR-0007, ADR-0008
