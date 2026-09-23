# ADR-0001: Use UUID v7 identifiers and identificationNumber

## Status

Accepted

## Context

The un-applied baseline used numeric surrogate keys, duplicate `public_id` values, and the legacy identity-field name. The project is still pre-deployment, so the baseline can change without data conversion.

## Decision

Every persisted primary key and typed relationship key uses application-generated UUID v7 in Java and `uniqueidentifier` in SQL Server. The duplicate `public_id` columns are removed. Business identity fields use `identificationNumber` in Java and `identification_number` in SQL. Domain models use wrapper types rather than Java primitives.

## Consequences

UUIDs must be generated before persistence and indexes are wider than `bigint` indexes. If the baseline is deployed before a future identity change, that change requires a separate data migration.
