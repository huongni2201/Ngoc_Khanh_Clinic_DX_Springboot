# Kế hoạch Participant CRUD thủ công: thêm, sửa, hủy

> Dành cho người triển khai: thực hiện lần lượt các task bằng `superpowers:executing-plans`; chỉ dùng subagent khi được yêu cầu. Đây là kế hoạch, không xác nhận runtime đã triển khai hoặc tests đã chạy.

**Ngày:** 2026-10-07

**Mục tiêu:** Cho staff thêm một Participant thủ công, xem chi tiết để sửa, sửa và hủy Participant trong một batch; có UI tương ứng trong tab Người khám.

**Kiến trúc:** `healthexamination` sở hữu toàn bộ. Controller → command → use case → domain aggregate `HealthExaminationBatchParticipant` → repository MyBatis; `audit` ghi cùng transaction. Không đụng `integration` (không có import job/idempotency cho thao tác thủ công).

**Stack giữ nguyên:** Java 25, Spring Boot 4 / Framework 7, PostgreSQL 18, MyBatis, Spring Modulith, Maven, Flyway. Frontend: Next 16, React 19, TanStack Query, react-hook-form + zod.

## 1. Quyết định đã chốt (chủ dự án, 2026-10-07)

1. **Xóa = Hủy:** `DELETE` chuyển `roster_status` sang `CANCELLED`. Không xóa vật lý; giữ provenance import, lịch sử và audit. CCCD đã hủy vẫn chiếm chỗ trong batch (unique `(batch_id, identification_number)` áp mọi roster status).
2. **Sửa CCCD:** chỉ khi Participant chưa chuẩn bị lượt khám (`patient_id IS NULL`). Đã link Patient thì CCCD bị khóa.
3. **Phạm vi:** backend + frontend.
4. **Trạng thái batch:** thêm/sửa/hủy chỉ khi batch DRAFT/READY, chưa soft delete, Organization ACTIVE — trùng với `HealthExaminationBatch.acceptsParticipantImport()`. FINALIZED/CLOSED → 409.

## 2. Đề xuất còn cần chốt (Task 0)

| # | Đề xuất | Lý do |
|---|---|---|
| D1 | Một permission mới `HEALTH_EXAMINATION_PARTICIPANT_MANAGE` cho create/detail/update/cancel, cấp ADMIN + CLINIC_MANAGER qua V004 | Không tái dùng IMPORT (khác ngữ nghĩa); không cấp cho mọi STAFF |
| D2 | `GET /{participantId}` trả CCCD đầy đủ, phone, email, chỉ với MANAGE | Form sửa cần giá trị thật; list vẫn mask |
| D3 | Chặn hủy khi Participant đã chuẩn bị (`prepared_at` ≠ null), `ATTENDED` hoặc `RECONCILED` → 409 | Tránh mồ côi Encounter/record/đối soát dịch vụ |
| D4 | Hủy lặp lại trên Participant đã CANCELLED → 409 `Participant is cancelled` | Nhất quán `requireActive()` của domain |
| D5 | ~~Không có endpoint khôi phục (CANCELLED → ACTIVE)~~ **Thay bằng [20261007-participant-reactivate.md](20261007-participant-reactivate.md)** | Khôi phục đúng bản ghi cũ qua `POST /{participantId}/reactivate`; thêm lại cùng CCCD vẫn 409 |
| D6 | Create không dùng `Idempotency-Key`; unique CCCD chặn double submit | Thao tác đơn lẻ, retry trả 409 rõ ràng |
| D7 | Thao tác thủ công không tăng `row_version` của batch | Giữ ngữ nghĩa version = cấu hình batch như kế hoạch import |

## 3. Hiện trạng đã đối chiếu source

| Thành phần | Có sẵn | Cần bổ sung |
|---|---|---|
| Aggregate | `create(...)` (importJob/sourceRow cho phép cùng null), `moveToDay`, `requireActive`; `roster` là `final` | `updateRoster`, `cancel`, guard CCCD/prepared |
| Repository | `findById`, `save` (update có `row_version` predicate + batch `deleted_at IS NULL`), `insertMany`, `findExistingIdentities` | `insert` đơn, lookup trùng loại trừ chính mình, `findInBatch` scoped |
| Mapper XML `update` | Chỉ ghi day/patient/status/attendance/reconciliation | **Thêm 9 cột roster** (code, name, dob, sex, CCCD, phone, email, department, position) |
| Controller | `BatchParticipantController`: list, template, import | POST, GET detail, PUT, DELETE |
| Security | Matcher GET/POST theo `PARTICIPANTS_PATH` | Matcher cho 4 route mới, đặt `/import-template` trước `/*` |
| Permissions | V003: READ, IMPORT | V004: MANAGE (D1) |
| DB | Bảng, CHECK, FK `(batch_id, batch_day_id)`, trigger validate | Không cần đổi bảng |
| FE | `participants.ts` (list/template/import), tab/table/toolbar/import dialog, `apiClient.put/delete` | API + hooks + form dialog + cancel dialog + cột thao tác |

## 4. Contract HTTP

Base: `/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants`

| Hành vi | Method / suffix | Request | Thành công | Permission |
|---|---|---|---|---|
| Thêm | POST base | `CreateParticipantRequest` | 201 + `ApiResponse<ParticipantDetailResponse>` | MANAGE |
| Chi tiết | GET `/{participantId}` | — | 200 + `ApiResponse<ParticipantDetailResponse>`, `no-store` | MANAGE |
| Sửa | PUT `/{participantId}` | `UpdateParticipantRequest` (+ `rowVersion`) | 200 + detail | MANAGE |
| Hủy | DELETE `/{participantId}?rowVersion=N` | — | 204 | MANAGE |

### 4.1. Body

```json
{
  "participantCode": "NV-001",
  "fullName": "Nguyễn Văn A",
  "dateOfBirth": "1990-05-12",
  "sex": "MALE",
  "identificationNumber": "001090012345",
  "phone": "0901234567",
  "email": null,
  "departmentName": "Kế toán",
  "positionName": "Nhân viên",
  "batchDayId": "019a...-day",
  "rowVersion": 3
}
```

`rowVersion` chỉ có ở PUT (bắt buộc, ≥ 0). Validation cấu trúc ở request DTO (`@NotBlank`, `@Size(max=200)` tên, `@Size(max=500)` text khác, `@Pattern("\\d{1,20}")` CCCD); invariants thật nằm ở `Roster`/`IdentificationNumber`. Ngày khám chọn bằng `batchDayId` (UI có danh sách ngày của batch), khác Excel dùng `examination_date`.

### 4.2. `ParticipantDetailResponse`

Toàn bộ field của `ParticipantSummaryResponse` + `identificationNumber` (đầy đủ), `phone`, `email`, `patientLinked` (boolean, FE dùng để khóa ô CCCD), `source` (`IMPORT` | `MANUAL`, suy từ `import_job_id`), `createdAt`, `updatedAt`. Không trả `patientId`, attendance note, import job id.

### 4.3. Lỗi (giữ envelope `result/code/message`)

| Trường hợp | HTTP | Safe message |
|---|---|---|
| Field sai/thiếu, CCCD không phải số | 400 | `identificationNumber must be 1-20 digits` |
| Batch/Participant không tồn tại, sai scope, batch đã xóa | 404 | `Participant not found` |
| CCCD đã có trong batch (kể cả CANCELLED) | 409 | `Participant identity already exists in this batch` |
| `batchDayId` không thuộc batch | 409 | `Examination day is not a day of this batch` |
| Batch không DRAFT/READY hoặc Organization INACTIVE | 409 | `Batch does not accept Participant changes` |
| Đổi CCCD khi đã link Patient | 409 | `Identification number is locked after visit preparation` |
| Sửa/hủy Participant CANCELLED | 409 | `Participant is cancelled` |
| Hủy khi đã chuẩn bị/ATTENDED/RECONCILED (D3) | 409 | `Participant cannot be cancelled after preparation or attendance` |
| `rowVersion` cũ | 409 | `Record was changed by another request` |
| Không login / thiếu quyền / sai Origin | 401 / 403 | Shared message |

Không đưa CCCD, tên hoặc SQL vào message/log.

## 5. Thiết kế backend

### 5.1. Domain — `HealthExaminationBatchParticipant`

- Bỏ `final` khỏi `roster`.
- `updateRoster(Roster next)`: `requireActive()`; nếu `patientId != null` và `next.identificationNumber()` khác hiện tại → `DomainRuleViolation`. Không đụng attendance/reconciliation/patient.
- `moveToDay(day)` đã có, tái dùng khi đổi ngày.
- `cancel()`: `requireActive()`; nếu `preparedAt != null` hoặc `attendanceStatus == ATTENDED` hoặc `reconciliationStatus == RECONCILED` → `DomainRuleViolation` (D3); đặt `rosterStatus = CANCELLED`.
- `source()` helper: `importJobId == null ? MANUAL : IMPORT` (hoặc tính ở mapper response).
- Tạo thủ công dùng `create(id, batch, day, roster, null, null, now)` sẵn có.

### 5.2. Repository / MyBatis

```java
void insert(HealthExaminationBatchParticipant participant);                // 1 row, kiểm affected count
Optional<HealthExaminationBatchParticipant> findInBatch(AggregateId batchId, AggregateId id);
boolean identityTakenByOther(AggregateId batchId, IdentificationNumber identity, AggregateId excludeId); // excludeId null khi create
```

- `insert` có thể tái dùng SQL `insertMany` với 1 phần tử; vẫn giữ predicate batch chưa xóa (đồng bộ với `update`).
- **Mở rộng `<update id="update">`** thêm 9 cột roster. Các caller hiện có (`prepare`, `recordAttendance`, `reconcileServices`) ghi lại đúng giá trị cũ nên an toàn; có test hồi quy.
- Lookup trùng không lọc `roster_status`. Race giữa pre-check và insert/update: `DuplicateKeyException` đã được `GlobalExceptionHandler` map sẵn thành 409 generic — không cần catch ở adapter, chỉ cần test chứng minh.
- `findInBatch` scope `batch_id` + `id`; batch scope theo organization đã kiểm ở bước lock batch.

### 5.3. Application

| Lớp | Vai trò |
|---|---|
| `CreateParticipantUseCase.execute(orgId, batchId, CreateParticipantCommand, principal)` | → `ParticipantDetailResponse` |
| `GetParticipantDetailUseCase.execute(orgId, batchId, participantId, principal)` | readOnly; không cần lock |
| `UpdateParticipantUseCase.execute(orgId, batchId, participantId, UpdateParticipantCommand, principal)` | command có `expectedRowVersion` |
| `CancelParticipantUseCase.execute(orgId, batchId, participantId, CancelParticipantCommand, principal)` | command chỉ có `expectedRowVersion` |
| `ParticipantAccessPolicy.requireManage(principal)` | thêm `MANAGE_PERMISSION` |
| `ParticipantDetailResponse.from(aggregate, examinationDate)` | mapping |
| Commands `CreateParticipantCommand`, `UpdateParticipantCommand`, `CancelParticipantCommand` | không chứa actor/HTTP type |

Luồng chung trong `@Transactional` (create/update/cancel):

1. `requireManage(principal)` trước mọi truy vấn.
2. `batches.findDetails(orgId, batchId, lock=true)` → 404; lock `FOR UPDATE` batch (cùng thứ tự khóa với import: batch → Participant).
3. Organization ACTIVE và `batch.acceptsParticipantImport()` (đề xuất đổi tên/alias thành `acceptsParticipantChanges()`; giữ hàm cũ cho import) → nếu không, 409.
4. Create/update: `batchDayId` phải thuộc `batch.days()` → 409.
5. Update/cancel: `findInBatch` → 404; so `rowVersion` với expected → `ConcurrentUpdateException` sớm (SQL predicate vẫn là chốt chặn cuối).
6. Create/update: build `Roster` (IllegalArgumentException → 400 qua handler hiện có), check trùng CCCD (update loại trừ chính mình, chỉ khi CCCD đổi).
7. Gọi domain: `create` / `updateRoster` + `moveToDay` / `cancel`.
8. `insert` hoặc `save(participant, expectedRowVersion)`.
9. Audit cùng transaction: `CREATE_BATCH_PARTICIPANT` / `UPDATE_BATCH_PARTICIPANT` / `CANCEL_BATCH_PARTICIPANT`, entity `HEALTH_EXAMINATION_BATCH_PARTICIPANT`, id = participantId. Metadata: batchId, organizationId, version trước/sau, **tên các field đã đổi** (không giá trị), `source`. Không CCCD/tên/phone/email.
10. Update/create trả detail đọc lại sau ghi (rowVersion mới).

Log: DEBUG chỉ ID/outcome; không log body.

### 5.4. API / Security / Migration

- `BatchParticipantController`: 4 handler mới, chỉ map HTTP ↔ command/response, `no-store` cho detail/PUT/POST.
- Request DTO mới: `CreateParticipantRequest`, `UpdateParticipantRequest` (`toCommand()`), `CancelParticipantRequest` (query `rowVersion`) theo mẫu `DeleteHealthExaminationBatchRequest`.
- `SecurityConfiguration`: thêm matcher `POST PARTICIPANTS_PATH`, `GET/PUT/DELETE PARTICIPANTS_PATH + "/*"` → `staffWith("PERM_HEALTH_EXAMINATION_PARTICIPANT_MANAGE")`. **Đặt matcher `GET .../import-template` trước `GET .../*`** để template không bị đòi MANAGE.
- `V004__add_participant_manage_permission.sql` (kiểm lại số version trước khi tạo): insert permission + grant ADMIN, CLINIC_MANAGER, `ON CONFLICT DO NOTHING`, giống V003.
- `HealthExaminationApiSurfaceTest.ALLOWED_MAPPINGS`: thêm đúng 4 route.

## 6. Thiết kế frontend

| File | Thay đổi |
|---|---|
| `types/transport.ts` | `participantDetailResponseSchema` (zod) |
| `types/index.ts` | `ParticipantDetail`, `ParticipantFormValues`, `CreateParticipantRequest`, `UpdateParticipantRequest` |
| `api/participants.ts` | `fetchParticipantDetail`, `createParticipant`, `updateParticipant`, `cancelParticipant` (`DELETE ?rowVersion=`) |
| `query-keys.ts` | `participantDetail(orgId, batchId, participantId)` dưới `participantsRoot` |
| `hooks/use-health-examination-batches.ts` | `useParticipantDetail`, `useCreateParticipant`, `useUpdateParticipant`, `useCancelParticipant`; `onSuccess` invalidate `participantsRoot` (+ detail) |
| `schemas/participant.schema.ts` | zod form: fullName 1..200, CCCD `^\d{1,20}$`, sex enum, dob hợp lệ, department/position bắt buộc, batchDayId bắt buộc, email optional hợp lệ; backend vẫn là nguồn quyết định |
| `components/participants-tab/participant-form-dialog.tsx` | Dialog dùng chung create/edit theo mẫu `batch-form/health-examination-batch-form-dialog.tsx`; select ngày khám từ `batch.days`; CCCD `disabled` khi `patientLinked` kèm ghi chú |
| `components/participants-tab/cancel-participant-dialog.tsx` | AlertDialog xác nhận, nêu rõ "hủy, không xóa" và CCCD không thể thêm lại |
| `participants-table.tsx` | Cột thao tác (Sửa / Hủy) — ẩn khi CANCELLED, không có quyền, hoặc batch không DRAFT/READY; nút Hủy disabled + tooltip khi đã chuẩn bị/ATTENDED/RECONCILED |
| `participants-toolbar.tsx` / `participants-tab.tsx` | Nút "Thêm người khám"; prop `canManage`, `batch.days` |
| `utils/participant-permissions.ts` | `PARTICIPANT_MANAGE_PERMISSION` |
| `pages/health-examination-batch-detail-page.tsx` | Tính `canManageParticipants` bằng `hasStaffPermission` và truyền xuống |
| `utils/participant-labels.ts` | `isParticipantChangeAllowed(status)` (DRAFT/READY) |

Hành vi lỗi: 409 trùng CCCD → lỗi gắn vào field CCCD; 409 stale version → toast "Dữ liệu đã thay đổi" + refetch detail; 403/404 → toast và đóng dialog. Edit mở dialog thì gọi detail (CCCD đầy đủ), không dùng dữ liệu masked từ list. Chạy `pnpm check:terminology` để giữ thuật ngữ.

## 7. Task triển khai

### Task 0 — Chốt contract và tài liệu

- [ ] Chốt D1–D7.
- [ ] Amendment ADR-0013 + `03-domain-and-workflows.md`: thêm mục "Participant manual changes" (mục import "Add-only" vẫn đúng cho import).
- [ ] `05-api-and-security.md`: permission MANAGE và 4 route.
- [ ] Tạo `docs/api/participant-manual-crud.md` (hoặc mở rộng `participant-import-and-list.md`): request/response/lỗi.

### Task 1 — Domain

- [ ] Test `HealthExaminationBatchParticipantTest`: updateRoster đổi CCCD khi chưa/đã link Patient; update/cancel khi CANCELLED; cancel bị chặn khi prepared/ATTENDED/RECONCILED; cancel thành công đổi status, không đổi field khác.
- [ ] Implement `updateRoster`, `cancel`, bỏ `final` roster.

### Task 2 — Persistence

- [ ] Mở rộng `MyBatisHealthExaminationBatchParticipantRepositoryTest` (PostgreSQL 18 Testcontainers): insert thủ công (import_job_id/source_row null, trigger pass), update ghi roster columns, stale version → `ConcurrentUpdateException`, update trên batch đã xóa → 0 row, duplicate CCCD race → `DuplicateKeyException` (409), `identityTakenByOther` loại trừ chính mình và tính cả CANCELLED.
- [ ] Hồi quy: các flow đang dùng `save` (prepare/attendance/reconcile) không làm đổi roster.
- [ ] Implement mapper Java/XML + adapter.

### Task 3 — Use cases

- [ ] Test 4 use case (mock repo/audit): thiếu permission, 404 scope, batch FINALIZED/CLOSED/deleted, Organization INACTIVE, day ngoài batch, trùng CCCD, stale version, audit metadata không chứa CCCD/tên.
- [ ] Implement commands, use cases, `requireManage`, `ParticipantDetailResponse`.

### Task 4 — API & security

- [ ] `BatchParticipantControllerTest`: validation 400, 201/200/204, envelope, `no-store`, detail trả CCCD đầy đủ.
- [ ] Filter-chain test: anonymous 401; STAFF chỉ READ → 403 cho 4 route mới; READ vẫn tải được template (thứ tự matcher); Origin sai → 403 cho POST/PUT/DELETE.
- [ ] V004 migration + `ALLOWED_MAPPINGS`.
- [ ] Integration test end-to-end: create → list thấy ACTIVE → update → cancel → list thấy CANCELLED → create lại cùng CCCD → 409; import file chứa CCCD đã thêm tay → 409.

### Task 5 — Frontend

- [ ] `participants-api.test.ts`: URL, method, body, `rowVersion` query, parse detail.
- [ ] `participant-schema.test.ts`: rule form.
- [ ] `participants-tab.test.tsx`: nút Thêm/Sửa/Hủy theo quyền, batch status, roster status; edit prefill từ detail; CCCD disabled khi `patientLinked`; xử lý 409.
- [ ] Implement theo bảng §6.
- [ ] Mở rộng `e2e/participants-flow.spec.ts`: thêm → sửa → hủy.

### Task 6 — Verify

Backend:

```powershell
.\mvnw.cmd "-Dtest=HealthExaminationBatchParticipantTest,MyBatisHealthExaminationBatchParticipantRepositoryTest" test
.\mvnw.cmd "-Dtest=*ParticipantUseCaseTest,BatchParticipantControllerTest,HealthExaminationApiSurfaceTest,ModuleVerificationTest" test
.\mvnw.cmd verify
```

Frontend:

```powershell
pnpm typecheck; pnpm lint; pnpm check:terminology; pnpm test; pnpm test:e2e
```

Báo cáo skip (Docker/Redis không có) thay vì coi là pass.

## 8. Acceptance checklist

- [ ] Thêm thủ công tạo Participant ACTIVE/UNCONFIRMED/PENDING, `import_job_id` null, không tạo Patient/Encounter/ParticipantService.
- [ ] Sửa dùng expected version; CCCD khóa sau khi link Patient; đổi ngày khám chỉ trong batch.
- [ ] Hủy chỉ đổi `roster_status`; dòng, provenance, lịch sử còn nguyên; list vẫn hiển thị CANCELLED.
- [ ] CCCD trùng trong batch (kể cả CANCELLED) → 409 ở cả thủ công và import.
- [ ] Batch FINALIZED/CLOSED/deleted hoặc Organization INACTIVE → không thêm/sửa/hủy được.
- [ ] Mọi thay đổi có audit cùng transaction; không log/audit CCCD, tên, phone, email.
- [ ] READ không đủ để thêm/sửa/hủy hoặc xem CCCD đầy đủ.
- [ ] UI ẩn/khóa thao tác đúng quyền và trạng thái; backend vẫn là nơi quyết định.
