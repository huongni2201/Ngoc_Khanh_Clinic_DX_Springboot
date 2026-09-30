# Health examination batch CRUD

Base resource: `/api/v1/organizations/{organizationId}/health-examination-batches`.

| Method | Path | Result |
| --- | --- | --- |
| POST | base resource | 201 with persisted draft details |
| GET | base resource | 200 with a page of summaries |
| GET | `/{batchId}` | 200 with details and service snapshots |
| PUT | `/{batchId}` | 200 with updated draft details |
| DELETE | `/{batchId}` | 200 with an empty success envelope |

All responses use the existing `ApiResponse` envelope. Batch identifiers are scoped to the organization in the URL. Unknown or soft-deleted details return 404. PUT on another lifecycle state returns 409.

## Create and update

```json
{
  "batchCode": "HC-2026-01",
  "batchName": "September health examination",
  "startDate": "2026-09-30",
  "endDate": null,
  "reason": null,
  "payerType": null,
  "examinationSiteType": "COMPANY",
  "examinationSiteName": "Organization examination site",
  "examinationSiteAddress": null,
  "services": [
    {
      "serviceId": "01990000-0000-7000-8000-000000000101",
      "negotiatedUnitPrice": 150000.00
    }
  ]
}
```

Each selected service has one entered VND price. There is no reference-price lookup. Price must be nonnegative and fit `numeric(18,2)`. Services must be nonempty and unique; newly selected services must exist, be active, and be eligible for health examinations. Backend snapshots their catalog code and name. PUT preserves IDs and snapshots of retained services and updates their price/order. Removing a referenced service returns 409.

Code is required (max 40 characters) and unique within its organization, including soft-deleted batches. Name is required (max 250). Dates are optional; when both exist, end date cannot precede start date. Site type is `CLINIC` or `COMPANY`; site name is required (max 250), address is optional for a draft (max 500). A company address is required before readiness. Reason is optional (max 300), payer type is optional (max 24).

POST requires an active organization and exactly one effective master health-examination template version in the document module. It uses that template version for the batch. It does not generate a document or select specialist templates; those are part of the later READY workflow.

The server supplies `createdBy`. The API does not accept actor, status, template version, or catalog snapshots from the body. Each mutation and its audit write share one database transaction.

## List

Defaults: `page=1`, `size=10`, `sortKey=id`, `sortBy=ASC`. Size is bounded to 100. `searchKey` is a keyword (max 100 characters) across batch code/name; `%`, `_` and backslash are treated literally.

Sort keys: `id`, `batchCode`, `batchName`, `startDate`, `status`, `createdAt`. Direction is ASC/DESC; ordering includes an ID tie-breaker. Soft-deleted batches are hidden. Existing organizations with no visible batches return an empty page; unknown organizations return 404.

## Soft deletion

DELETE changes a DRAFT without dependent roster/import records to `DELETED`. It retains batch and service rows and reserves the batch code. Repeating DELETE returns 200 without another audit record. Deleting a non-draft or dependent batch returns 409. This resource has no cancel endpoint.

## Local actor fixture and deferred authentication

With profile `local`, the controller uses `clinic.health-examination.batch.mock-created-by`. `application-local.yaml` adds the isolated `db/local` Flyway location; its repeatable migration creates the corresponding development staff/user row so the actor FK is valid. Default/production migration locations do not include this fixture.

Outside local/test, mutation requires an authenticated principal whose name is its UUID; otherwise the controller rejects the request with 403. The principal mapping will be integrated with the future authentication feature. Role/permission policy is deferred as requested. Default Spring Security filters still apply.

## Schema changes

`V003__simplify_health_examination_batch_price_and_status.sql` adds the `DELETED` status and removes the batch-service reference-price column/constraint. V002 is already used by the roster import change. This records the owner's approved single-price/soft-delete contract, superseding the corresponding reference-price/status parts of the FINAL baseline. Apply the full migration chain; deployed V001 is unchanged.
