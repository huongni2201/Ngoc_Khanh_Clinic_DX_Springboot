# ADR-0017: Post-commit SMS delivery of new account credentials

## Status

Accepted — 2026-10-13, with UC-005 Create User Account. Amends the outbox rule of
[PROJECT_RULES §24](../../PROJECT_RULES.md) and the transaction rule of §21 for
this one effect only.

## Context

UC-005 sends the username and temporary password of a new account to the owner's
mobile number by SMS. PROJECT_RULES asks durable external effects to go through
an outbox written in the business transaction. An outbox or `notifications` row
would have to keep the temporary password readable until it is dispatched,
which the SRS and PROJECT_RULES forbid for any password. No SMS gateway is
integrated yet.

## Decision

- `CreateUserAccountUseCase` is not `@Transactional`. It hashes the password,
  then runs the account insert, the role grants and the audit event in one
  `TransactionOperations` block, and refuses to run inside an outer transaction.
- After that block commits, it calls the `SignInCredentialSender` port once with
  the plain temporary password held only in memory. Nothing about the message is
  stored.
- The sender returns true only when the gateway accepted the message. A false
  result or any runtime failure leaves the account created and returns
  `smsDelivered: false`; the client shows the UC-005 11-EX-01 warning.
- Until a gateway is integrated, `UnconfiguredSignInCredentialSender` sends
  nothing, logs the account id once and returns false. It never reports a
  fake success.

## Consequences

- An SMS is never sent for an account that was rolled back, and an SMS failure
  never undoes an account.
- There is no automatic retry. Recovery is a new temporary password (Reset
  Password by an Administrator), which is not implemented yet and is tracked in
  [Open items](../architecture/07-open-items.md).
- A real gateway adapter replaces the stand-in bean; its credentials belong in
  environment or secret storage (PROJECT_RULES §28).
