# API Breaking-Change Inventory

Audit date: 2026-09-25

## Current API surface

No `@RestController`, `@Controller`, request-mapping endpoint, OpenAPI
contract, or generated API client exists in this backend checkout. The
frontend module APIs are in-memory adapters, not HTTP clients. Therefore there
is no active backend endpoint to rename during this pass.

## Reserved cut-over map

| Current / planned contract | Canonical contract | Consumer | Migration action | Status |
| --- | --- | --- | --- | --- |
| `/api/v1/health-check-batches` if introduced | `/api/v1/health-examination-batches` | future frontend/backend client | Introduce only the canonical endpoint; do not add a legacy alias | Reserved |
| `role=FRONT_DESK` if introduced | `role=RECEPTIONIST` | identity and frontend permission map | Use only `RECEPTIONIST`; no active backend value found | Reserved |
| `department=RECEPTIONIST` if introduced | `department=RECEPTION` | catalog/identity | Keep actor and department distinct | Reserved |
| `doctorId` / `doctorName` in the sibling frontend mock contract | `physicianId` / `physicianName` | frontend appointments/reception modules | Rename frontend types, fixtures and form contracts together; backend API does not yet exist | Pending frontend write access |
| `HealthCheck*` DTO names if introduced | `HealthExamination*` | future API layer | Use canonical Java/API names from the first endpoint | Reserved |

No compatibility aliases were added because the repositories are pre-API and
the plan explicitly prefers a clean greenfield cut-over.
