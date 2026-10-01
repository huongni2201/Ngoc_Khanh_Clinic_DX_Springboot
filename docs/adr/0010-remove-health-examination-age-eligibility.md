# ADR-0010: Remove health-examination age eligibility validation

## Status

Accepted — 2026-10-01, at the request of the project owner.

## Context

The previous health-examination contract required patients to be at least 18 at the planned and actual examination dates. The project owner has requested removal of that rule from the backend.

## Decision

- Do not block roster preview/import, health-examination record preparation/restoration, or check-in based on age.
- Keep required date-of-birth fields and date-format validation.
- Preserve all other roster, identity, snapshot, and health-examination rules.

This decision supersedes the age-eligibility rule only. It does not change the remaining FINAL requirement, use-case, or table-design contracts.

## Consequences

- Age-related roster errors, warnings, and domain exceptions are removed.
- The supplied FINAL DOCX copies remain unchanged and retain their former 18-year wording. Update those copies before using them as the contract for a future health-examination implementation.
