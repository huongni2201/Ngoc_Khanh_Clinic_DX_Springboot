# 07 — Security and Audit Architecture

## 1. Authentication

Authentication identifies the current user/principal.

Security framework:

```text
Spring Security
```

Do not expose authentication details into domain objects unless a business rule truly needs actor identity.

## 2. Authorization

Authorization should exist at application boundary.

Examples:

```text
Receptionist
Physician
Diagnostic Staff
Clinic Manager
Administrator
```

Never rely only on frontend hiding buttons.

## 3. Sensitive data

Clinic data includes sensitive personal and medical data.

Minimum rules:

```text
no PHI in debug logs unless explicitly masked/approved
no passwords/tokens in logs
no SQL query construction from raw input
least-privilege DB credentials
role-based access checks
audit privileged changes
```

## 4. Audit

Use `audit_logs` for business/security-relevant changes.

Examples:

```text
patient profile changes
batch repricing
result release
result correction
prescription correction
role changes
encounter reassignment
health-examination batch reopen
```

Audit record should capture when practical:

```text
actor
action
entity type
entity id
reason
before summary
after summary
timestamp
correlation/request id
```

## 5. Repricing audit

Repricing must require a reason.

Persist:

```text
old price
new price
batch
service
actor
reason
timestamp
```

## 6. Result release audit

Persist:

```text
result/report version
patient
actor/system
release timestamp
```

## 7. Prescription correction

Issued prescription should not be overwritten.

Correction creates a new version and preserves the old version for traceability.

## 8. Error responses

Do not leak:

```text
stack traces
SQL text
database names
credentials
internal filesystem paths
provider secrets
```

Map internal failures to controlled API error responses.
