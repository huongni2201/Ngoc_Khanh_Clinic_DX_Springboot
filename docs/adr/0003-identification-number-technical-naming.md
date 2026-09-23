# ADR-0003: Use IdentificationNumber as the technical name for CCCD

## Status

Accepted — supersedes the technical naming decision in ADR-0001.

## Date

2026-09-23

## Context

ADR-0001 used `Cccd` and `cccd` for domain and SQL names. The v2.11 implementation plan requires a stable technical name that can continue to describe the supported identifier without adding an identity-type or passport abstraction. The current MVP still accepts CCCD only.

## Decision

Use these technical names:

| Boundary | Name |
| --- | --- |
| Java value object | `IdentificationNumber` |
| Java property | `identificationNumber` |
| SQL column | `identification_number` |
| Business and document label | CCCD |

The value object keeps the current CCCD-compatible validation. This decision changes technical naming only; it does not add supported identity document types or change CCCD requirements.

Applied migrations remain immutable. A forward Flyway migration restores the final `identification_number` column and index names after V002.

## Consequences

- Domain properties, persistence records, mapper parameters, SQL columns, and schema contracts use the technical name above.
- Existing databases preserve data while a forward migration renames columns and indexes.
- User-facing labels and business rules continue to use CCCD.
- ADR-0001 remains the source for UUID v7 identifiers and other decisions; this ADR supersedes only its CCCD technical naming choice.

## Related decisions

- [ADR-0001: UUID v7 identifiers and CCCD](0001-uuid-v7-identifiers-and-cccd.md)
- [ADR-0002: Patient result release state](0002-patient-result-release-state.md)
