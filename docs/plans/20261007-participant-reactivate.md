# Kế hoạch khôi phục Participant đã hủy (CANCELLED → ACTIVE)

> Dành cho người triển khai: thực hiện lần lượt các task bằng `superpowers:executing-plans`; chỉ dùng subagent khi được yêu cầu. Đây là kế hoạch, không xác nhận runtime đã triển khai hoặc tests đã chạy.

**Ngày:** 2026-10-07

**Mục tiêu:** Cho staff hoàn tác việc hủy một Participant trong batch bằng cách đưa **chính bản ghi cũ** về ACTIVE; có UI tương ứng trong tab Người khám. Không tạo bản ghi mới, không nới unique CCCD.

**Bối cảnh:** Kế hoạch [Participant CRUD thủ công](20261007-participant-manual-crud.md) chốt D5 "không có endpoint khôi phục". Hệ quả: người khám bị hủy nhầm không thể thêm lại (POST cùng CCCD → 409 `Participant identity already exists in this batch`) và không có đường nào đưa họ về danh sách. Kế hoạch này thay D5.

**Kiến trúc:** `healthexamination` sở hữu toàn bộ, đi đúng khuôn của `CancelParticipantUseCase`: Controller → command → use case → aggregate `HealthExaminationBatchParticipant` → repository MyBatis; audit ghi cùng transaction. Không đụng `integration`, không cần migration.

## 1. Vì sao khôi phục bản ghi cũ là an toàn

- `cancel()` chỉ cho hủy khi `preparedAt == null`, `attendanceStatus != ATTENDED`, `reconciliationStatus != RECONCILED`. Mọi bản ghi CANCELLED đều chưa có Patient/Encounter/ParticipantService đã đối soát → đưa về ACTIVE không làm mồ côi dữ liệu.
- Giữ nguyên provenance (`import_job_id`, `source_row_number`), lịch sử audit liền mạch, một CCCD = một dòng trong batch (báo cáo, đối soát không bị trùng).
- Unique `(batch_id, identification_number)` giữ nguyên; không có race trùng CCCD vì không insert.

Phương án bị loại: đổi unique thành partial index `WHERE roster_status = 'ACTIVE'` để POST lại → sinh nhiều dòng cùng CCCD, mất liên kết với dòng import gốc, phức tạp báo cáo/đối soát.

## 2. Đề xuất cần chốt (Task 0)

| # | Đề xuất | Lý do |
|---|---|---|
| R1 | Route `POST /{participantId}/reactivate` | Hành động nghiệp vụ, không phải sửa field; tránh `PATCH rosterStatus` tổng quát |
| R2 | Dùng lại permission `HEALTH_EXAMINATION_PARTICIPANT_MANAGE` | Cùng nhóm thao tác với thêm/sửa/hủy; không cần migration |
| R3 | Body `{ rowVersion (bắt buộc), batchDayId (tùy chọn) }`; `batchDayId` null = giữ ngày cũ | Bản ghi CANCELLED không sửa được bằng PUT nên cần chọn lại ngày ngay khi khôi phục |
| R4 | Khôi phục **không** sửa roster (tên, CCCD...) trong cùng request; sửa sau bằng PUT | Một thao tác một ý nghĩa; audit rõ ràng |
| R5 | Giữ nguyên attendance/note/reconciliation như lúc hủy, không reset | `cancel()` không đổi các field này; khôi phục là đảo ngược đúng phép hủy |
| R6 | Trả `200` + `ParticipantDetailResponse` (rowVersion mới) | FE cập nhật ngay, giống PUT |
| R7 | POST thêm mới trùng CCCD **vẫn** 409, không tự khôi phục; import Excel trùng CCCD đã hủy **vẫn** 409 (import add-only) | Không để thao tác "thêm" ngầm đổi trạng thái bản ghi khác; FE hướng người dùng sang nút Khôi phục |
| R8 | Không thay đổi `row_version` của batch | Nhất quán D7 |
| R9 | Không có trường lý do (free text) | Tránh PII trong audit; nếu cần thì làm sau với enum lý do |

## 3. Hiện trạng đã đối chiếu source

| Thành phần | Có sẵn | Cần bổ sung |
|---|---|---|
| Aggregate | `cancel()`, `moveToDay()` (yêu cầu ACTIVE), `requireActive()`; `restore(...)` là **factory rehydrate** | Method `reactivate()` (không dùng tên `restore`) |
| Repository | `findInBatch`, `save(participant, expectedVersion)`; mapper `update` đã ghi `roster_status`, `batch_day_id` | Không đổi |
| Support | `ParticipantChangeSupport.lockBatchForChange`, `requireDay`, `audit`, `applyingDomainRule`, `examinationDate` | Không đổi |
| Controller | POST, GET detail, PUT, DELETE | `POST /{participantId}/reactivate` |
| Security | Matcher MANAGE cho `POST base`, `GET/PUT/DELETE /*` | Matcher `POST /*/reactivate`. Thiếu matcher thì route rơi vào `/api/v1/**` (chỉ cần ACCOUNT_STAFF) — use case vẫn chặn nhưng phải có matcher cho defense-in-depth |
| List API | Đã lọc được `identificationNumber` (khớp chính xác) + `rosterStatus` | Không đổi; FE dùng để tìm dòng đã hủy khi POST bị 409 |
| DB | CHECK `roster_status IN ('ACTIVE','CANCELLED')`, FK `(batch_id, batch_day_id)` | Không cần migration |
| FE | `cancelParticipant`, `useCancelParticipant`, `cancel-participant-dialog.tsx`, `participant-write-errors.ts`; bảng hiển thị "—" cho dòng CANCELLED | API, hook, dialog khôi phục, nút Khôi phục, map lỗi mới, gợi ý khi trùng CCCD |

## 4. Contract HTTP

Base: `/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants`

| Hành vi | Method / suffix | Request | Thành công | Permission |
|---|---|---|---|---|
| Khôi phục | POST `/{participantId}/reactivate` | `ReactivateParticipantRequest` | 200 + `ApiResponse<ParticipantDetailResponse>`, `no-store` | MANAGE |

### 4.1. Body

```json
{
  "rowVersion": 4,
  "batchDayId": "019a...-day"
}
```

`rowVersion` bắt buộc, ≥ 0. `batchDayId` tùy chọn; có giá trị thì phải là ngày của batch.

### 4.2. Lỗi

| Trường hợp | HTTP | Safe message |
|---|---|---|
| Thiếu/sai `rowVersion`, `batchDayId` không phải UUID | 400 | Validation message hiện có |
| Batch/Participant không tồn tại, sai scope, batch đã xóa | 404 | `Participant not found` |
| Participant đang ACTIVE | 409 | `Participant is not cancelled` (**mới**) |
| Batch không DRAFT/READY hoặc Organization INACTIVE | 409 | `Batch does not accept Participant changes` |
| `batchDayId` không thuộc batch | 409 | `Examination day is not a day of this batch` |
| `rowVersion` cũ | 409 | `Record was changed by another request` |
| Dữ liệu bất thường: đã chuẩn bị/ATTENDED/RECONCILED | 409 | `Participant cannot be reactivated after preparation or attendance` (**mới**, guard phòng thủ) |
| Không login / thiếu quyền / sai Origin | 401 / 403 | Shared message |

## 5. Thiết kế backend

### 5.1. Domain — `HealthExaminationBatchParticipant`

```java
/**
 * Returns a cancelled Participant to the active roster. Only the roster status changes; the row,
 * its provenance, attendance and reconciliation stay as they were at cancellation.
 *
 * @throws DomainRuleViolation when the Participant is not cancelled, or (defensively) was prepared,
 *     attended or reconciled
 */
public void reactivate() {
  if (rosterStatus != RosterStatus.CANCELLED)
    throw new DomainRuleViolation("Participant is not cancelled");
  if (preparedAt != null
      || attendanceStatus == AttendanceStatus.ATTENDED
      || reconciliationStatus == ReconciliationStatus.RECONCILED)
    throw new DomainRuleViolation(
        "Participant cannot be reactivated after preparation or attendance");
  rosterStatus = RosterStatus.ACTIVE;
}
```

Đổi ngày: use case gọi `moveToDay(day)` **sau** `reactivate()` (vì `moveToDay` yêu cầu ACTIVE), chỉ khi `batchDayId` khác ngày hiện tại.

### 5.2. Application

| Lớp | Vai trò |
|---|---|
| `ReactivateParticipantCommand(Long expectedRowVersion, UUID batchDayId)` | `batchDayId` nullable |
| `ReactivateParticipantUseCase.execute(orgId, batchId, participantId, command, principal)` | → `ParticipantDetailResponse` |

Luồng trong `@Transactional` (khuôn `CancelParticipantUseCase`):

1. `access.requireManage(principal)` trước mọi truy vấn.
2. Validate tham số null; `expectedRowVersion` null/âm → `IllegalArgumentException` (400).
3. `support.lockBatchForChange(orgId, batchId)` → 404 / 409 batch không nhận thay đổi. Thứ tự khóa batch → Participant.
4. `participants.findInBatch(...)` → 404; so `rowVersion` → `ConcurrentUpdateException`.
5. Nếu `command.batchDayId() != null` → `support.requireDay(batch, batchDayId)` (409).
6. `ParticipantChangeSupport.applyingDomainRule(participant::reactivate)`; nếu ngày khác ngày hiện tại → `applyingDomainRule(() -> participant.moveToDay(day))`.
7. `participants.save(participant, expectedVersion)` (SQL predicate `row_version` là chốt chặn cuối).
8. `support.audit(actor, "REACTIVATE_BATCH_PARTICIPANT", orgId, participant, expected, expected + 1, changedFields)` với `changedFields = ["rosterStatus"]` (+ `"batchDayId"` nếu đổi). Không CCCD/tên/phone/email.
9. Đọc lại `findInBatch` và trả `ParticipantDetailResponse.from(stored, ParticipantChangeSupport.examinationDate(batch, stored.getBatchDayId()))`.

Log: `log.info("Participant reactivation pending commit: organizationId={}, batchId={}, participantId={}", ...)`; không log body.

### 5.3. API / Security

- `ReactivateParticipantRequest(@NotNull @PositiveOrZero Long rowVersion, UUID batchDayId)` + `toCommand()`, theo mẫu `CancelParticipantRequest`.
- `BatchParticipantController`: handler `@PostMapping("/{participantId}/reactivate")`, `@Valid @RequestBody`, trả `200` + `CacheControl.noStore()`.
- `SecurityConfiguration`: thêm
  ```java
  .requestMatchers(HttpMethod.POST, PARTICIPANTS_PATH + "/*/reactivate")
  .access(staffWith("PERM_HEALTH_EXAMINATION_PARTICIPANT_MANAGE"))
  ```
  đặt cùng nhóm matcher Participant, trước `"/api/v1/**"`.
- `HealthExaminationApiSurfaceTest.ALLOWED_MAPPINGS`: thêm `POST .../participants/{participantId}/reactivate`.
- Không migration (R2).

## 6. Thiết kế frontend

| File | Thay đổi |
|---|---|
| `types/index.ts` | `ReactivateParticipantRequest { organizationId, batchId, participantId, rowVersion, batchDayId? }` |
| `api/participants.ts` | `buildParticipantReactivateUrl(...)`, `reactivateParticipant(request)` → POST body `{ rowVersion, batchDayId }`, parse bằng `participantDetailResponseSchema` |
| `hooks/use-health-examination-batches.ts` | `useReactivateParticipant()`; `retry: false`; `onSuccess` invalidate `participantsRoot` (đã bao `participantDetail`) |
| `components/participants-tab/reactivate-participant-dialog.tsx` | Dialog xác nhận: tên người khám, select ngày khám từ `batch.days` mặc định `participant.batchDayId`, ghi chú "Người khám sẽ trở lại danh sách với trạng thái Đang tham gia; thông tin cũ được giữ nguyên, có thể sửa sau". Lỗi hiển thị bằng `Alert` như dialog hủy |
| `participants-table.tsx` | `ParticipantRowActions`: dòng CANCELLED hiện nút **"Khôi phục"** (`aria-label="Khôi phục người khám {tên}"`) thay cho "—"; prop mới `onReactivate` |
| `participants-tab.tsx` | State `reactivating`, render dialog, truyền `onReactivate`; chỉ khi `changeAllowed` |
| `cancel-participant-dialog.tsx` | Đổi câu "không thể thêm lại cùng CCCD" → "CCCD vẫn được giữ trong đợt khám; có thể khôi phục người khám này sau nếu hủy nhầm." |
| `utils/participant-write-errors.ts` | Thêm rule `Participant is not cancelled` → kind `not-cancelled`, "Người khám này không còn ở trạng thái Đã hủy (có thể đã được khôi phục). Danh sách đã được tải lại."; rule `Participant cannot be reactivated after preparation or attendance` → kind `reactivate-blocked` |
| `participant-form-dialog.tsx` (chế độ thêm) | Khi 409 `duplicate-identity`: gọi list `?identificationNumber={cccd}&rosterStatus=CANCELLED&size=1`. Tìm thấy → hiện `Alert` "CCCD này thuộc người khám **{tên}** đã bị hủy" + nút **"Khôi phục người khám này"** (đóng form, mở dialog khôi phục). Không thấy → giữ lỗi field CCCD như hiện tại |

Hành vi lỗi dialog khôi phục: `stale-version` → toast + invalidate list rồi đóng; `not-cancelled` → invalidate list rồi đóng; 403/404 → toast và đóng; `batch` không nhận thay đổi → hiện Alert. Chạy `pnpm check:terminology`.

## 7. Task triển khai

### Task 0 — Chốt contract và tài liệu

- [ ] Chốt R1–R9.
- [ ] `docs/plans/20261007-participant-manual-crud.md`: đánh dấu D5 "Thay bằng `20261007-participant-reactivate.md`".
- [ ] `docs/api/participant-manual-crud.md`: bỏ "There is no restore endpoint", thêm mục `POST /{participantId}/reactivate`, cập nhật rule CCCD ("A CANCELLED Participant keeps its CCCD reserved; reactivate it instead of adding it again"), thêm action audit.
- [ ] `docs/architecture/03-domain-and-workflows.md` (mục Participant manual changes): thêm chuyển trạng thái CANCELLED → ACTIVE và điều kiện.
- [ ] `docs/architecture/05-api-and-security.md`: route + matcher mới.

### Task 1 — Domain (TDD)

- [ ] `HealthExaminationBatchParticipantTest`:
  - reactivate từ CANCELLED → ACTIVE; roster, day, provenance, attendance, reconciliation, patientId không đổi.
  - reactivate khi ACTIVE → `DomainRuleViolation("Participant is not cancelled")`.
  - guard phòng thủ: dựng bằng `restore(...)` một bản ghi CANCELLED có `preparedAt`/ATTENDED/RECONCILED → `DomainRuleViolation`.
  - sau reactivate thì `moveToDay`, `updateRoster`, `cancel` hoạt động lại.
- [ ] Implement `reactivate()`.

### Task 2 — Use case (TDD)

- [ ] `ReactivateParticipantUseCaseTest` (mock repo/support/audit):
  - thiếu MANAGE → `ACCESS_DENIED`, không gọi repo.
  - null id / rowVersion âm → `IllegalArgumentException`.
  - Participant không thấy → 404.
  - stale version → `ConcurrentUpdateException`, không save.
  - Participant ACTIVE → `ConflictException("Participant is not cancelled")`.
  - batch FINALIZED/CLOSED/Organization INACTIVE → conflict từ `lockBatchForChange`.
  - `batchDayId` ngoài batch → 409, không save.
  - không truyền `batchDayId` → giữ ngày cũ, audit `["rosterStatus"]`.
  - truyền ngày khác → đổi ngày, audit `["rosterStatus","batchDayId"]`; truyền đúng ngày cũ → audit chỉ `["rosterStatus"]`.
  - audit metadata không chứa CCCD/tên/phone/email; version trước/sau đúng.
- [ ] Implement `ReactivateParticipantCommand`, `ReactivateParticipantUseCase`.

### Task 3 — Persistence (hồi quy)

- [ ] `MyBatisHealthExaminationBatchParticipantRepositoryTest` (PostgreSQL 18 Testcontainers): insert → cancel/save → reactivate/save → đọc lại `roster_status = ACTIVE`, `row_version` +2, `import_job_id`/`source_row_number` giữ nguyên; stale version → `ConcurrentUpdateException`.

### Task 4 — API & security

- [ ] `BatchParticipantControllerTest`: 200 + envelope + `no-store`; thiếu `rowVersion` → 400; `batchDayId` sai định dạng → 400; map 404/409.
- [ ] Filter-chain test (theo test bảo mật Participant hiện có): anonymous 401; STAFF chỉ READ → 403; Origin sai → 403; MANAGE → qua.
- [ ] `HealthExaminationApiSurfaceTest.ALLOWED_MAPPINGS` thêm route.
- [ ] Integration end-to-end: create → cancel → POST cùng CCCD → 409 → list `?identificationNumber=&rosterStatus=CANCELLED` thấy 1 dòng → reactivate → list thấy ACTIVE, cùng `id` → reactivate lần 2 → 409 `Participant is not cancelled` → cancel lại được.
- [ ] Import Excel chứa CCCD của Participant đã hủy → vẫn 409 (R7).

### Task 5 — Frontend

- [ ] `participant-manual-api.test.ts`: URL `/participants/{id}/reactivate`, method POST, body có/không `batchDayId`, parse detail.
- [ ] `participant-write-errors` test: 2 rule mới.
- [ ] `participants-manual-crud.test.tsx`:
  - dòng CANCELLED có nút Khôi phục khi `canManage` + batch DRAFT/READY; không có khi thiếu quyền hoặc batch FINALIZED/CLOSED.
  - dialog mặc định ngày cũ; submit gửi đúng `rowVersion`/`batchDayId`; thành công đóng dialog và invalidate list.
  - xử lý `stale-version`, `not-cancelled`.
  - form thêm: 409 trùng CCCD + list trả 1 dòng CANCELLED → hiện gợi ý và nút khôi phục; list rỗng → chỉ lỗi field.
  - text mới của dialog hủy.
- [ ] Implement theo bảng §6.
- [ ] `e2e/participants-flow.spec.ts`: thêm → hủy → thêm lại cùng CCCD thấy gợi ý → khôi phục → dòng về trạng thái Đang tham gia.

### Task 6 — Verify

Backend:

```powershell
.\mvnw.cmd "-Dtest=HealthExaminationBatchParticipantTest,ReactivateParticipantUseCaseTest" test
.\mvnw.cmd "-Dtest=MyBatisHealthExaminationBatchParticipantRepositoryTest,BatchParticipantControllerTest,HealthExaminationApiSurfaceTest,ModuleVerificationTest" test
.\mvnw.cmd verify
```

Frontend:

```powershell
pnpm typecheck; pnpm lint; pnpm check:terminology; pnpm test; pnpm test:e2e
```

Báo cáo skip (Docker/Redis không có) thay vì coi là pass.

## 8. Acceptance checklist

- [ ] Khôi phục đưa đúng bản ghi cũ (cùng `id`) về ACTIVE; không tạo dòng mới; provenance, roster, attendance, reconciliation giữ nguyên.
- [ ] Chỉ khôi phục được Participant CANCELLED, trong batch DRAFT/READY của Organization ACTIVE, với `rowVersion` mới nhất.
- [ ] Có thể chọn lại ngày khám trong batch khi khôi phục; mặc định giữ ngày cũ.
- [ ] Sau khôi phục, sửa/hủy/chuẩn bị lượt khám hoạt động bình thường.
- [ ] POST thêm mới và import trùng CCCD vẫn 409; UI gợi ý khôi phục khi CCCD thuộc người khám đã hủy.
- [ ] Audit `REACTIVATE_BATCH_PARTICIPANT` cùng transaction, không chứa CCCD/tên/phone/email.
- [ ] Route được bảo vệ bằng MANAGE ở cả security chain và use case.
- [ ] UI chỉ hiện nút Khôi phục khi có quyền và batch cho phép; backend vẫn là nơi quyết định.
