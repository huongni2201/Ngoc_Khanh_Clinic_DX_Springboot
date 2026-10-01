# ADR-0011: Shared user login for staff and patients

## Status

Accepted — 2026-09-30. Supersedes the STAFF-only eligibility, required role,
STAFF-only JWT identity, and staff login route decisions in [ADR-0009](0009-staff-credentials-and-server-side-sessions.md).
The cookie, Redis, CSRF, transaction, audit and revocation decisions remain in force.

## Context

The `users` table already supports STAFF and PATIENT principals, but login rejected
patients and users without effective role assignments. Authentication must identify
both account types while business authorization remains separate.

## Decision

- `POST /api/v1/auth/login` replaces `/api/v1/auth/staff/login` without an alias.
- An ACTIVE user with valid credentials can log in without role assignments. STAFF
  must link to an active staff row; PATIENT must link to a patient row. No patient
  lifecycle rule is inferred from the patient module.
- The signed JWT and `/me` response carry the actual principal type, a STAFF-only
  `staffId` or PATIENT-only `patientId`, and a possibly empty assignment list.
  Existing valid STAFF JWTs without `patientId` remain valid until expiry.
- Assignments are filtered by their half-open validity interval on each request.
  Expiry of the last assignment removes business access but does not end the session.
- Default/production continues to deny business endpoints without policy. The
  local/test development gate requires a STAFF principal with an effective role;
  patients and roleless staff receive 403. This gate does not implement RBAC.
- Audit records for new operations use `USER_LOGIN`, `USER_LOGOUT`, and
  `USER_SESSIONS_REVOKED`; historical records are not rewritten.

## Consequences

Frontend clients must move to the new route and accept both session shapes. A
patient can authenticate but has no patient portal or clinical-record access from
this decision. Existing staff provisioning remains available; account creation,
self-registration and SMS access are separate features. No schema migration or
dependency change is required.
