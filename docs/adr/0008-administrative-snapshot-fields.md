# ADR-0008: Keep administrative snapshot data as aggregate fields

## Status

Accepted — 2026-09-28 (supersedes the application/API value-object decision in ADR-0007)

## Decision

HealthExaminationBatchParticipant, HealthExaminationRecord, and import rows keep their
administrative snapshot values as explicit fields. They do not use an
AdministrativeSnapshot value object.

The typed database columns from ADR-0007 remain unchanged.
