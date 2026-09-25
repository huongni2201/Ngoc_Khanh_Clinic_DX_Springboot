# 08 — Testing and Quality Gates

## 1. Required verification command

Primary quality gate:

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

Must run in CI.

## 2. Test layers

### Domain unit tests

Fast and framework-free.

Cover:

```text
aggregate lifecycle
value-object validation
invariants
idempotent domain behavior
invalid transitions
```

### Application tests

Cover:

```text
orchestration
authorization decisions
transaction behavior
repository interactions
cross-aggregate invariants
```

### Persistence integration tests

Use real SQL Server through Testcontainers.

Do not use H2 as a substitute for SQL Server-specific behavior.

Cover:

```text
mapper XML
column mapping
constraints
roundtrip
rowversion/concurrency
```

### Migration integration tests

Start a fresh SQL Server and apply every migration.

Verify:

```text
migration count
table inventory
critical columns
foreign keys
unique indexes
check constraints
removed legacy schema
```

### Module verification

Use Spring Modulith verification to enforce module boundaries.

## 3. Current schema quality gates

Target:

```text
65 business tables
0 legacy workflow tables after v2.11
`identification_number` columns present
legacy identity columns absent
UUID relationships use uniqueidentifier
result release columns present
```

## 4. Contract tests

Persistence record contract tests are useful but must never become the only schema verification.

Regex/text checks do not replace real SQL Server migration tests.

## 5. Regression tests required for high-risk flows

### Health-examination repricing

Verify:

```text
batch service price
employee assignment price
corporate ServiceRequest price
report totals
rollback on failure
```

### Result release

Verify:

```text
exact version release
idempotency
new correction starts unreleased
SMS failure does not revoke release
portal only sees released result
```

### Prescription

Verify:

```text
issued version immutable
correction creates new version
supersedes link
correction after completed encounter
```

## 6. Branch protection

`main` should require the CI status:

```text
Backend verification
```

No merge on failed compile/tests/module verification/migrations.
