# 06 — Integration and Events Architecture

## 1. Principle

External systems are adapters, not domain dependencies.

Domain/application depends on a port.

Infrastructure implements the external connector.

## 2. Outbox pattern

Use outbox for reliable post-commit work such as:

```text
result-ready notification
integration export
external system synchronization
document generation request
```

Business transaction:

```text
update domain state
insert outbox event
commit
```

Worker/process:

```text
read pending outbox
call external integration
mark processed/retry
```

## 3. Notifications

Notification state and patient release state are separate.

Correct flow:

```text
result finalized
-> authorized user releases exact version
-> released_to_patient_at is persisted
-> notification/outbox created
-> SMS attempt occurs
```

If SMS fails:

```text
release remains valid
```

## 4. Integration messages

Persist enough metadata for:

```text
source
destination
external message ID
correlation ID
payload reference
status
retry count
last error
received/sent timestamp
```

Do not embed provider-specific logic into domain entities.

## 5. Idempotency

Incoming external messages must be idempotent.

Recommended key:

```text
provider + external message/event id
```

or a documented deterministic business key.

## 6. Retry policy

Retries should distinguish:

```text
transient failure
permanent validation failure
authentication/configuration failure
duplicate message
```

Do not retry permanent business validation forever.

## 7. File attachments

Store file metadata in database.

The actual binary storage strategy may be:

```text
filesystem
object storage
document service
```

Domain should reference a storage/file identifier, not filesystem-specific paths.

## 8. Integration ownership

The `integration` context owns generic infrastructure contracts.

Business interpretation remains in the owning business module.

Example:

```text
diagnostics interprets lab result semantics
integration handles transport/idempotency
```
