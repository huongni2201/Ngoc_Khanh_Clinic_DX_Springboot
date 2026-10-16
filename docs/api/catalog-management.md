# Catalog Management API (Departments, Rooms, Specialties, Services, Medicines)

Implemented contract for UC-110–UC-115.
Base path: `/api/v1/catalog/management/{resource}`, where `{resource}` is one of:
- `departments` (Khoa)
- `rooms` (Phòng)
- `specialties` (Chuyên khoa)
- `services` (Dịch vụ kỹ thuật / khám)
- `medicines` (Danh mục thuốc kê đơn)

Responses use the [standard project envelope](../../PROJECT_RULES.md#envelope-errors-and-pagination) (`ApiResponse<T>`).
Lookup endpoint `GET /api/v1/catalog/services` for health examination batches remains untouched and active-only.

---

## 1. Permission Matrix

Under ADR-0015, every route requires a `STAFF` account and its own explicit permission. V007 grants these to `CLINIC_MANAGER`; `ADMINISTRATOR` receives no catalog grants. UC-110–UC-115 belong to `CLINIC_MANAGER`, including manager accounts created through the staff-member API:

| Resource | Action | Method | Path | Required Permission |
|---|---|---|---|---|
| Departments | List | GET | `/api/v1/catalog/management/departments` | `CATALOG_DEPARTMENT_VIEW` |
| Departments | Detail | GET | `/api/v1/catalog/management/departments/{id}` | `CATALOG_DEPARTMENT_DETAIL_VIEW` |
| Departments | Create | POST | `/api/v1/catalog/management/departments` | `CATALOG_DEPARTMENT_CREATE` |
| Departments | Update | PUT | `/api/v1/catalog/management/departments/{id}` | `CATALOG_DEPARTMENT_UPDATE` |
| Departments | Deactivate | DELETE | `/api/v1/catalog/management/departments/{id}?rowVersion={v}` | `CATALOG_DEPARTMENT_DEACTIVATE` |
| Departments | Reactivate | POST | `/api/v1/catalog/management/departments/{id}/reactivate` | `CATALOG_DEPARTMENT_REACTIVATE` |
| Rooms | List | GET | `/api/v1/catalog/management/rooms` | `CATALOG_ROOM_VIEW` |
| Rooms | Detail | GET | `/api/v1/catalog/management/rooms/{id}` | `CATALOG_ROOM_DETAIL_VIEW` |
| Rooms | Create | POST | `/api/v1/catalog/management/rooms` | `CATALOG_ROOM_CREATE` |
| Rooms | Update | PUT | `/api/v1/catalog/management/rooms/{id}` | `CATALOG_ROOM_UPDATE` |
| Rooms | Deactivate | DELETE | `/api/v1/catalog/management/rooms/{id}?rowVersion={v}` | `CATALOG_ROOM_DEACTIVATE` |
| Rooms | Reactivate | POST | `/api/v1/catalog/management/rooms/{id}/reactivate` | `CATALOG_ROOM_REACTIVATE` |
| Specialties | List | GET | `/api/v1/catalog/management/specialties` | `CATALOG_SPECIALTY_VIEW` |
| Specialties | Detail | GET | `/api/v1/catalog/management/specialties/{id}` | `CATALOG_SPECIALTY_DETAIL_VIEW` |
| Specialties | Create | POST | `/api/v1/catalog/management/specialties` | `CATALOG_SPECIALTY_CREATE` |
| Specialties | Update | PUT | `/api/v1/catalog/management/specialties/{id}` | `CATALOG_SPECIALTY_UPDATE` |
| Specialties | Deactivate | DELETE | `/api/v1/catalog/management/specialties/{id}?rowVersion={v}` | `CATALOG_SPECIALTY_DEACTIVATE` |
| Specialties | Reactivate | POST | `/api/v1/catalog/management/specialties/{id}/reactivate` | `CATALOG_SPECIALTY_REACTIVATE` |
| Services | List | GET | `/api/v1/catalog/management/services` | `CATALOG_SERVICE_VIEW` |
| Services | Detail | GET | `/api/v1/catalog/management/services/{id}` | `CATALOG_SERVICE_DETAIL_VIEW` |
| Services | Create | POST | `/api/v1/catalog/management/services` | `CATALOG_SERVICE_CREATE` |
| Services | Update | PUT | `/api/v1/catalog/management/services/{id}` | `CATALOG_SERVICE_UPDATE` |
| Services | Deactivate | DELETE | `/api/v1/catalog/management/services/{id}?rowVersion={v}` | `CATALOG_SERVICE_DEACTIVATE` |
| Services | Reactivate | POST | `/api/v1/catalog/management/services/{id}/reactivate` | `CATALOG_SERVICE_REACTIVATE` |
| Medicines | List | GET | `/api/v1/catalog/management/medicines` | `CATALOG_MEDICINE_VIEW` |
| Medicines | Detail | GET | `/api/v1/catalog/management/medicines/{id}` | `CATALOG_MEDICINE_DETAIL_VIEW` |
| Medicines | Create | POST | `/api/v1/catalog/management/medicines` | `CATALOG_MEDICINE_CREATE` |
| Medicines | Update | PUT | `/api/v1/catalog/management/medicines/{id}` | `CATALOG_MEDICINE_UPDATE` |
| Medicines | Deactivate | DELETE | `/api/v1/catalog/management/medicines/{id}?rowVersion={v}` | `CATALOG_MEDICINE_DEACTIVATE` |
| Medicines | Reactivate | POST | `/api/v1/catalog/management/medicines/{id}/reactivate` | `CATALOG_MEDICINE_REACTIVATE` |

---

## 2. Core Business Rules

1. **Identifiers & Canonical Codes:**
   - Resource IDs are backend-generated UUIDv7.
   - Code is manually entered, trimmed, and uppercased via `Locale.ROOT`.
   - Pattern: `[A-Z0-9][A-Z0-9_.-]{0,49}` (1 to 50 characters).
   - Code is **immutable** after creation. Legacy rows retain their stored spelling; comparison and uniqueness use the canonical form.
   - Uniqueness is case-insensitive and whitespace-trimmed across both active and inactive records (`upper(btrim(code))`). Duplicate code returns `409 Conflict`.
2. **Soft Deactivation & Reactivation:**
   - Deactivation sets `active = false` (no hard delete, foreign keys and history remain intact).
   - Reactivation sets `active = true`.
   - Repeated deactivation on an already inactive entity (or reactivation on an already active entity) with the matching version is a **no-op** (returns current state without bumping version or generating false audit logs).
3. **Optimistic Locking:**
   - Every entity maintains a `rowVersion` (starts at 0 upon creation).
   - `PUT`, `DELETE`, and `POST /reactivate` require `rowVersion`.
   - If the database `row_version` does not match the supplied version, returns `409 Conflict` (`CONCURRENT_UPDATE`).
4. **Dependency Guards:**
   - **Department:** Cannot be deactivated if it still contains active rooms or active services (`409 Conflict`).
   - **Specialty:** Cannot be deactivated if active services still reference it (`409 Conflict`).
   - **Room:** Cannot be created or reactivated if its parent department is inactive (`409 Conflict`).
   - **Service:** Cannot be created or reactivated if its performing department or chosen specialty is inactive (`409 Conflict`). Assigning a different inactive specialty is rejected. An inactive service may edit its details while retaining its unchanged inactive specialty/department; this does not reactivate it.
   - `room.departmentId` and `service.performingDepartmentId` are immutable in this release to prevent unintended cross-department reassignment cascades.
5. **Auditing:**
   - Mutating operations record structured audit entries with the actor's `userId` from session authentication in the same database transaction.
   - Before/after values include optional specialty/instruction/brand fields, including changes to `null`. Audit failure rolls back the mutation.
6. **Persisted responses and concurrency:**
   - Create/update/reactivate responses use the persisted row version and timestamps; they agree with detail reads. Legacy `rooms`, `specialties` and `medicines` may retain `createdAt=null` because the old schema did not record creation times.
   - Service update/reactivation locks department, then old/new specialties in UUID order, then the service, and rechecks the initial relationship/version after locking. Stale probes are conflicts.

---

## 3. Query & List Specifications

All collection `GET` endpoints support the following parameters:
- `page`: 1-based page index (default: `1`, min: `1`).
- `size`: page size (default: `10`, min: `1`, max: `100`).
- `searchKey`: case-insensitive partial search on code and names (with wildcard escaping for `%`, `_`, `\`).
- `active`: optional boolean (`true`, `false`, or omitted for all).
- `sortKey`: resource-specific allowlist (default: `id`).
- `sortBy`: `ASC` (default) or `DESC`.
- Invalid enum filters return `400`; enum filters are trimmed and case-insensitive. Search length is limited to 100 characters and list reads preserve one consistent count/page snapshot.

### Resource-Specific Query Filters & Sort Allowlist

| Resource | Additional Filters | Sort Key Allowlist |
|---|---|---|
| `departments` | `departmentType` (`CLINICAL`, `DIAGNOSTIC`, `ADMINISTRATIVE`) | `id`, `code`, `name` |
| `rooms` | `departmentId` (UUID), `roomType` (string) | `id`, `code`, `name` |
| `specialties` | None | `id`, `code`, `name` |
| `services` | `serviceType`, `performingDepartmentId` (UUID), `specialtyId` (UUID) | `id`, `code`, `name`, `unitPrice` |
| `medicines` | `dosageForm` | `id`, `code`, `genericName`, `brandName` |

---

## 4. Payloads & Endpoints

### 4.1 Departments (`/api/v1/catalog/management/departments`)

#### Create Request (`POST /`)
```json
{
  "code": "KHAM-BENH",
  "name": "Khoa Khám Bệnh",
  "departmentType": "CLINICAL"
}
```

#### Update Request (`PUT /{id}`)
```json
{
  "name": "Khoa Khám Bệnh & Cấp Cứu",
  "departmentType": "CLINICAL",
  "rowVersion": 0
}
```

#### Response (`DepartmentResponse`)
```json
{
  "code": 200,
  "result": "OK",
  "message": "Success",
  "data": {
    "id": "018f3a12-89ab-7cde-9012-3456789abcde",
    "code": "KHAM-BENH",
    "name": "Khoa Khám Bệnh",
    "departmentType": "CLINICAL",
    "active": true,
    "rowVersion": 0,
    "createdAt": "2026-10-14T02:00:00Z",
    "updatedAt": "2026-10-14T02:00:00Z"
  }
}
```

---

### 4.2 Rooms (`/api/v1/catalog/management/rooms`)

#### Create Request (`POST /`)
```json
{
  "code": "P-101",
  "name": "Phòng Khám Nội 1",
  "departmentId": "018f3a12-89ab-7cde-9012-3456789abcde",
  "roomType": "EXAMINATION"
}
```

#### Update Request (`PUT /{id}`)
```json
{
  "name": "Phòng Khám Nội Tổng Quát",
  "roomType": "EXAMINATION",
  "rowVersion": 0
}
```

#### Response (`RoomResponse`)
```json
{
  "code": 200,
  "result": "OK",
  "message": "Success",
  "data": {
    "id": "018f3a12-89ab-7cde-9012-3456789abcdf",
    "code": "P-101",
    "name": "Phòng Khám Nội 1",
    "departmentId": "018f3a12-89ab-7cde-9012-3456789abcde",
    "departmentName": "Khoa Khám Bệnh",
    "roomType": "EXAMINATION",
    "active": true,
    "rowVersion": 0,
    "createdAt": "2026-10-14T02:00:00Z",
    "updatedAt": "2026-10-14T02:00:00Z"
  }
}
```

---

### 4.3 Specialties (`/api/v1/catalog/management/specialties`)

#### Create Request (`POST /`)
```json
{
  "code": "NOI-KHOA",
  "name": "Nội Khoa"
}
```

#### Update Request (`PUT /{id}`)
```json
{
  "name": "Nội Tổng Quát",
  "rowVersion": 0
}
```

#### Response (`SpecialtyResponse`)
```json
{
  "code": 200,
  "result": "OK",
  "message": "Success",
  "data": {
    "id": "018f3a12-89ab-7cde-9012-3456789abce0",
    "code": "NOI-KHOA",
    "name": "Nội Khoa",
    "active": true,
    "rowVersion": 0,
    "createdAt": "2026-10-14T02:00:00Z",
    "updatedAt": "2026-10-14T02:00:00Z"
  }
}
```

---

### 4.4 Services (`/api/v1/catalog/management/services`)

#### Create Request (`POST /`)
```json
{
  "code": "XRAY-CHEST",
  "name": "Chụp X-Quang Ngực Thẳng",
  "serviceType": "XRAY",
  "performingDepartmentId": "018f3a12-89ab-7cde-9012-3456789abcde",
  "specialtyId": "018f3a12-89ab-7cde-9012-3456789abce0",
  "unitPrice": 250000.00,
  "preparationInstruction": "Tháo đồ trang sức kim loại vùng ngực trước khi chụp"
}
```

#### Update Request (`PUT /{id}`)
```json
{
  "name": "Chụp X-Quang Ngực Thẳng Kỹ Thuật Số",
  "serviceType": "XRAY",
  "specialtyId": "018f3a12-89ab-7cde-9012-3456789abce0",
  "unitPrice": 280000.00,
  "preparationInstruction": "Tháo đồ trang sức kim loại vùng ngực",
  "rowVersion": 0
}
```

#### Response (`ManagedServiceResponse`)
```json
{
  "code": 200,
  "result": "OK",
  "message": "Success",
  "data": {
    "id": "018f3a12-89ab-7cde-9012-3456789abce1",
    "code": "XRAY-CHEST",
    "name": "Chụp X-Quang Ngực Thẳng",
    "serviceType": "XRAY",
    "performingDepartmentId": "018f3a12-89ab-7cde-9012-3456789abcde",
    "performingDepartmentName": "Khoa Khám Bệnh",
    "specialtyId": "018f3a12-89ab-7cde-9012-3456789abce0",
    "specialtyName": "Nội Khoa",
    "unitPrice": 250000.00,
    "preparationInstruction": "Tháo đồ trang sức kim loại vùng ngực trước khi chụp",
    "active": true,
    "rowVersion": 0,
    "createdAt": "2026-10-14T02:00:00Z",
    "updatedAt": "2026-10-14T02:00:00Z"
  }
}
```

---

### 4.5 Medicines (`/api/v1/catalog/management/medicines`)

#### Create Request (`POST /`)
```json
{
  "code": "PARA-500",
  "genericName": "Paracetamol",
  "brandName": "Panadol",
  "strength": "500mg",
  "dosageForm": "Viên nén",
  "unit": "Viên"
}
```

#### Update Request (`PUT /{id}`)
```json
{
  "genericName": "Paracetamol",
  "brandName": "Panadol Extra",
  "strength": "500mg",
  "dosageForm": "Viên nén bao phim",
  "unit": "Viên",
  "rowVersion": 0
}
```

#### Response (`MedicineResponse`)
```json
{
  "code": 200,
  "result": "OK",
  "message": "Success",
  "data": {
    "id": "018f3a12-89ab-7cde-9012-3456789abce2",
    "code": "PARA-500",
    "genericName": "Paracetamol",
    "brandName": "Panadol",
    "strength": "500mg",
    "dosageForm": "Viên nén",
    "unit": "Viên",
    "active": true,
    "rowVersion": 0,
    "createdAt": "2026-10-14T02:00:00Z",
    "updatedAt": "2026-10-14T02:00:00Z"
  }
}
```

---

### 4.6 Deactivate & Reactivate (All Resources)

#### Deactivate (`DELETE /{id}?rowVersion={rowVersion}`)
- Query parameter: `rowVersion` (required, non-negative integer).
- No request body.
- Successful response: HTTP `204 No Content`.
- Error responses:
  - `400`: Missing or negative `rowVersion`.
  - `404`: Entity not found.
  - `409`: Version mismatch or active dependencies exist (e.g. active rooms/services).

#### Reactivate (`POST /{id}/reactivate`)
- Request body:
```json
{
  "rowVersion": 0
}
```
- Successful response: HTTP `200 OK` with the reactivated detail response.
- Error responses:
  - `400`: Missing or invalid `rowVersion`.
  - `404`: Entity not found.
  - `409`: Version mismatch or parent entity is inactive.

## 5. Deployment and verification

`V007__catalog_management.sql` follows the unchanged V001–V006 migrations. It adds catalog
versions/timestamps, canonical-code unique indexes, parent-filter indexes and non-negative
version constraints. Newly added timestamps are NOT NULL with CURRENT_TIMESTAMP defaults.

Each of the 30 permissions declares its method/path in the same INSERT and is granted to
CLINIC_MANAGER. The local access-control seed and afterMigrate callback grant TEST access.
Sign in again after deployment to refresh the permission snapshot. See the
[deployment policy](../architecture/06-testing-and-operations.md#deployment) for databases
with an incompatible migration history.

`CatalogManagementIntegrationTest` covers all five CRUD/status cycles through real session
filters, action/resource permissions, Origin validation, PostgreSQL mappings, audit/rollback
and concurrent writes. It also provisions a CLINIC_MANAGER through the staff-member API
and exercises all five catalogs. `CatalogManagementMigrationIntegrationTest` verifies the
sequential V001–V009 history, seed prices, timestamp/version constraints, canonical-code
uniqueness, manager/admin grants, atomic DDL/permission rollback and local TEST grants.
Use `mvnw.cmd verify` with Docker available; skipped tests do not establish these guarantees.
