# ADR-0002: Store patient result release on each result version

## Status

Accepted — approved by the user on 2026-09-23.

## Context

Requirement v2.5 currently gates Patient Portal visibility on a successful `RESULT_READY` SMS, while the v2.11 fix plan calls for a persisted release state on each exact result version. The plan was approved on 2026-09-23, including the change that SMS delivery must not grant or revoke portal access.

The current `lab_results` and `diagnostic_reports` schema has no release columns. Notification delivery status is owned by a separate lifecycle and cannot safely represent whether a specific clinical result version has been released.

## Decision

Store `released_to_patient_at` and `released_to_patient_by_user_id` on each `lab_results` and `diagnostic_reports` row. A result version is visible to its Patient through the portal only when its own `released_to_patient_at` is non-null.

Release is allowed only for a final result version. Repeating release for the same version is idempotent. A corrected or otherwise new result version starts unreleased. The release transaction records the release and queues a `RESULT_READY` notification through the durable notification/outbox path. SMS status does not change release state.

This decision supersedes requirement v2.5's SMS-gated visibility rule and supplements table-design v2.11 only for result release state. All other baseline rules remain in force.

## Consequences

- Database migrations and persistence records must carry release fields for both result tables.
- Portal reads must filter by release state for the exact version and must retain existing authorization checks.
- Notification retries and delivery outcomes cannot expose or hide clinical results.
- Existing requirement documents still contain the older SMS-gated rule; this ADR takes precedence until those source documents are revised.

## Related

- `docs/baseline/requirement-v2.5_FINAL.docx`
- `docs/baseline/table-design-v2.11_FINAL.docx`
- `C:\Users\PC\Downloads\ngoc-khanh-backend-fix-plan-v2.11.md`
