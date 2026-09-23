# ADR-0001: Use UUID v7 identifiers and cccd

## Status

Accepted

## Context

The un-applied baseline used numeric surrogate keys, duplicate `public_id` values, and the legacy identity-field name. The UUIDv7 strategy remains in force. The v2.11 business identity terminology requires a schema cut-over from the earlier baseline.

## Decision

Every persisted primary key and typed relationship key uses application-generated UUID v7 in Java and `uniqueidentifier` in SQL Server. The duplicate `public_id` columns are removed. Business identity fields use `cccd` in Java and `cccd` in SQL. Domain models use wrapper types rather than Java primitives.

## Consequences

UUIDs must be generated before persistence and indexes are wider than `bigint` indexes. An applied V001 migration is preserved; V002 renames the CCCD columns and removes empty legacy workflow tables.
