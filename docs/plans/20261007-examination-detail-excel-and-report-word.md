# Kế hoạch Chi tiết khám (xuất/nhập Excel) và Báo cáo đợt khám (xuất Word)

> Dành cho người triển khai: thực hiện lần lượt các task bằng `superpowers:executing-plans`; chỉ dùng subagent khi được yêu cầu. Đây là kế hoạch, không xác nhận runtime đã triển khai hoặc tests đã chạy.

**Ngày:** 2026-10-07

**Mục tiêu:**

1. Tab **Chi tiết khám** của một batch: xem ma trận Người khám × Hạng mục (X = đã thực hiện), **xuất Excel** và **nhập Excel** để ghi nhận đối soát dịch vụ.
2. Tab **Báo cáo**: tổng hợp số người khám từng hạng mục × đơn giá = thành tiền, và **xuất Word (.docx)** bảng tổng hợp thanh toán.

**Kiến trúc:** `healthexamination` sở hữu toàn bộ nghiệp vụ, read model và adapter Excel/Word (ADR-0012: không tạo module reporting, báo cáo là query của module nghiệp vụ). Tên dịch vụ lấy qua contract công khai `catalog` `ServiceCatalogQuery`, không join SQL sang bảng `services`. Lịch sử import/idempotency qua published contract của `integration`; audit qua `audit::recording` trong cùng transaction.

**Stack giữ nguyên:** Java 25, Spring Boot 4 / Framework 7, PostgreSQL 18, MyBatis, Spring Modulith, Maven, Flyway; Apache POI `poi-ooxml` 5.5.1 (đã có, gồm XSSF và XWPF). Frontend: Next 16, React 19, TanStack Query, zod.

## 1. Quyết định đã chốt (chủ dự án, 2026-10-07)

1. **Import Chi tiết khám = đánh dấu X hạng mục đã khám.** Ghi vào đối soát dịch vụ (`health_examination_participant_services.is_performed`) và điểm danh `ATTENDED`. Không nhập kết quả lâm sàng, kết luận hay record versions.
2. **Ghi đè theo file.** Với mỗi Người khám có trong file, file là trạng thái đối soát mới: X → đã thực hiện; ô trống → `is_performed=false` (giữ row, không xóa). Người khám không có trong file giữ nguyên. Toàn bộ file hoặc không ghi gì (all-or-nothing).
3. **Word = tổng hợp thanh toán:** thông tin đơn vị/đợt khám, bảng Hạng mục – Số người khám – Đơn giá – Thành tiền, tổng tiền bằng số và bằng chữ, số người đăng ký/đã khám, chỗ ký hai bên. Không có danh sách từng người.
4. **Phạm vi:** backend + frontend.

## 2. Đề xuất còn cần chốt (Task 0)

| # | Đề xuất | Lý do |
|---|---|---|
| E1 | Import cho phép khi batch **DRAFT hoặc READY**, chưa soft delete, Organization ACTIVE (giống `acceptsParticipantChanges()`); FINALIZED/CLOSED → 409 | Hiện **chưa có endpoint chuyển DRAFT→READY→FINALIZED** (`markReady/finalizeBatch` chỉ ở domain). Chỉ cho READY sẽ làm tính năng không dùng được. Cấu hình batch DRAFT vẫn an toàn: domain đã chặn xóa day/service đang được tham chiếu |
| E2 | So khớp dòng bằng **`participant_id` ẩn**, không bằng CCCD | File xuất chỉ chứa CCCD đã mask; tránh đưa CCCD đầy đủ ra Excel |
| E3 | Mỗi dòng mang **`row_version` ẩn** của Participant; khác DB → 409 “Row N: … export again” | Chống ghi đè mất dữ liệu khi người khác đã sửa sau lúc xuất (PROJECT_RULES §21) |
| E4 | Dòng có ≥ 1 X → attendance `ATTENDED`; ngày thực tế = cột “Ngày khám thực tế” nếu có, nếu trống thì giữ ngày đã ghi (nếu đã ATTENDED), nếu chưa thì dùng ngày khám dự kiến của Participant | Ràng buộc DB: ATTENDED bắt buộc `actual_examination_date` |
| E5 | Ngày khám thực tế: ISO `yyyy-MM-dd`, ≥ ngày bắt đầu batch và ≤ hôm nay (Clock); không bắt buộc thuộc BatchDay | Cho phép khám bù ngoài ngày dự kiến |
| E6 | Dòng toàn trống: nếu Participant chưa có service row nào → **bỏ qua** (không đánh RECONCILED); nếu đã có → set tất cả `false`, giữ RECONCILED. Attendance **không** bị đảo về UNCONFIRMED | Không biến người chưa đến thành “đã đối soát 0 hạng mục”; sửa điểm danh là use case riêng |
| E7 | Dòng không đổi (X và ngày trùng DB) → không ghi, không tăng version | Import lại cùng file an toàn, giảm write |
| E8 | Không xuất/nhập `attendance_note` (bỏ cột “Ghi chú” mock ở FE) | `ParticipantDetailResponse` đã cố ý không trả note; có thể chứa thông tin nhạy cảm |
| E9 | Permissions mới qua **V005**: `HEALTH_EXAMINATION_SERVICE_READ` (xem/xuất chi tiết khám), `HEALTH_EXAMINATION_SERVICE_RECONCILE` (nhập Excel), `HEALTH_EXAMINATION_REPORT_READ` (xem/xuất Word báo cáo); cấp ADMIN + CLINIC_MANAGER | Không tái dùng quyền Participant (khác ngữ nghĩa); không cấp cho mọi STAFF |
| E10 | Import có `Idempotency-Key` + import job/rows; thêm `import_type` **`HEALTH_EXAMINATION_SERVICE_RECONCILIATION`** qua **V006** | Retry khi timeout trả lại kết quả cũ; giữ lịch sử như import Participant; không lạm dụng `HEALTH_EXAMINATION_RESULT` (là kết quả lâm sàng) |
| E11 | Báo cáo gom theo `(batch_service_id, unit_price_snapshot)`, chỉ `is_performed=true` của Participant roster ACTIVE; liệt kê cả hạng mục 0 người | Dùng giá snapshot lịch sử, không lấy giá hiện tại; dòng 0 giúp đối chiếu |
| E12 | Batch chưa FINALIZED → báo cáo `provisional=true`, Word có tiêu đề “(TẠM TÍNH)” | Chưa có bước chốt số liệu |
| E13 | File Word **không** lưu vào `files`/`issued_representations`, không phải văn bản phát hành chính thức; audit `EXPORT_PAYMENT_REPORT` | Văn bản phát hành theo template version thuộc module `document`, là use case khác |
| E14 | Audit cả `EXPORT_EXAMINATION_DETAILS` (file có họ tên người khám) | Theo dõi xuất dữ liệu cá nhân |
| E15 | FE tab Báo cáo: nút **“Xuất Word”** + **“Xuất Excel chi tiết”** (dùng chung endpoint xuất của Chi tiết khám); bỏ “Excel tổng hợp (dọc)” và bộ sinh CSV/SpreadsheetML phía client `utils/export-excel.ts` | Một nguồn số liệu ở backend; client không tự sinh file từ mock |

## 3. Hiện trạng đã đối chiếu source

| Thành phần | Có sẵn | Cần bổ sung |
|---|---|---|
| Domain Participant | `reconcileServices(actual, scope, actor, at)`: chặn service ngoài scope, service mới phải `active` + `performed` + đúng giá thỏa thuận, giữ row cũ khi bỏ chọn, set RECONCILED; `recordAttendance(...)`; `requireActive()` | Factory tạo `HealthExaminationBatchParticipantService` mới (id UUIDv7, snapshot giá); helper so sánh “không đổi” |
| Repository | `save()` cập nhật header theo `row_version` và insert/update service rows theo version; `findInBatch`, `findServices(ids)` | `findManyInBatchForUpdate(batchId, ids)` (một query header + một query services, khóa theo thứ tự id) |
| Batch | `acceptsParticipantChanges()`; services có `displayOrder`, `active`, `negotiatedPrice` | Không có endpoint ready/finalize/close (xem E1) |
| Tên dịch vụ | `ServiceCatalogQuery` (catalog published), đang dùng ở `GetHealthExaminationBatchByIdUseCase` | Tái dùng; không join SQL `services` |
| Excel | `PoiParticipantExcelReader/TemplateWriter`, `ParticipantImportProperties` (budget package/ZIP) | Tách phần kiểm tra package OOXML dùng chung; reader/writer mới cho ma trận |
| Word | POI 5.5.1 có XWPF; chưa có adapter | Writer `.docx` + tiện ích đọc số tiền bằng chữ |
| Integration | `ParticipantImportStore` (riêng Participant); `import_jobs.import_type` CHECK: `ORGANIZATION_PARTICIPANT`, `HEALTH_EXAMINATION_RESULT` | Contract mới + V006 mở rộng CHECK (E10) |
| Security | Matchers `PARTICIPANTS_PATH`; permissions V003/V004 | Matchers mới, V005 (E9) |
| Migrations | V001–V004; plan reactivate Participant ghi “không migration” | V005, V006 |
| FE | Tab `examination`/`report` đang render `UnsupportedBatchTab`; components ma trận/báo cáo có sẵn nhưng dùng type mock (`examStatus`, `note`, `completedServiceIds`), API trả `unavailableApi(...)`; `utils/export-excel.ts` sinh CSV client | API thật, schema zod, hooks, dialog import, nút xuất, gỡ mock |

Worktree đang có thay đổi chưa commit (Participant CRUD thủ công, plan reactivate). Người triển khai giữ nguyên các thay đổi đó; kiểm tra lại `git status` trước khi bắt đầu.

## 4. Contract HTTP

Base: `/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}`. Organization/Batch không khớp hoặc batch đã soft delete → 404.

| Hành vi | Method / suffix | Thành công | Permission |
|---|---|---|---|
| Ma trận (phân trang) | GET `/examination-details` | 200 + `ApiResponse<PageResponse<ExaminationDetailRowResponse>>` | SERVICE_READ |
| Bộ đếm | GET `/examination-details/summary` | 200 + `ApiResponse<ExaminationSummaryResponse>` | SERVICE_READ |
| Xuất Excel (cũng là file mẫu để nhập) | GET `/examination-details/export` | 200 + binary `.xlsx` | SERVICE_READ |
| Nhập Excel | POST `/examination-details/imports` (multipart `file`, header `Idempotency-Key`) | 201 + `ApiResponse<ExaminationDetailImportResponse>` | SERVICE_RECONCILE |
| Báo cáo thanh toán | GET `/reports/payment-summary` | 200 + `ApiResponse<PaymentSummaryReportResponse>` | REPORT_READ |
| Xuất Word | GET `/reports/payment-summary/docx` | 200 + binary `.docx` | REPORT_READ |

Cột của ma trận = `services` trong response GET batch detail hiện có (thứ tự `displayOrder`). Không thêm field mới vào `PageResponse`.

### 4.1. `GET /examination-details`

Query: `page`, `size`, `sortKey` (`id`, `participantCode`, `fullName`, `examinationDate`; mặc định `id`), `sortBy`, `searchKey` (mã, họ tên, phòng ban; không tìm theo CCCD đầy đủ), `attendanceStatus`, `reconciliationStatus`, `rosterStatus` (mặc định chỉ ACTIVE). Theo `PaginationConstants`.

```json
{
  "id": "019a…",
  "participantCode": "NV-001",
  "fullName": "Nguyễn Văn A",
  "identificationNumberMasked": "********1234",
  "departmentName": "Kỹ thuật",
  "positionName": "Kỹ sư",
  "examinationDate": "2026-10-20",
  "attendanceStatus": "ATTENDED",
  "actualExaminationDate": "2026-10-20",
  "reconciliationStatus": "RECONCILED",
  "performedBatchServiceIds": ["019a…", "019a…"],
  "rowVersion": 3
}
```

Không tạo trạng thái “Chưa khám/Đang khám/Hoàn thành” ở backend (PROJECT_RULES §15: không dựng state machine song song). FE suy nhãn: UNCONFIRMED → “Chưa đến”, ABSENT → “Vắng”, ATTENDED + PENDING → “Đã đến – chờ đối soát”, RECONCILED → “Đã đối soát”.

### 4.2. `GET /examination-details/summary`

```json
{ "registered": 120, "unconfirmed": 10, "attended": 105, "absent": 5, "reconciled": 100, "pendingReconciliation": 5 }
```

Chỉ đếm roster ACTIVE.

### 4.3. Xuất Excel

- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`; `Content-Disposition: attachment; filename="chi-tiet-kham-<batchCode đã lọc [A-Za-z0-9_-]>.xlsx"`; `Cache-Control: no-store`.
- Luôn xuất toàn bộ roster ACTIVE của batch (không theo filter UI) để file nhập lại đầy đủ. Lỗi vẫn trả JSON envelope.

### 4.4. Nhập Excel

- `file`: một `.xlsx` ≤ 5 MiB; `Idempotency-Key`: UUID bắt buộc. Không có `rowVersion` batch: concurrency kiểm theo `row_version` từng Participant trong file (E3).

```json
{
  "importJobId": "019a…",
  "batchId": "019a…",
  "totalRows": 120,
  "updatedParticipants": 87,
  "unchangedParticipants": 33,
  "performedItems": 412,
  "completedAt": "2026-10-07T03:00:00Z"
}
```

Replay cùng key + cùng file → trả nguyên 201/body cũ. Cùng key khác file → 409.

### 4.5. `GET /reports/payment-summary`

```json
{
  "batchId": "019a…",
  "batchCode": "KSK-2026-001",
  "batchName": "Khám sức khỏe định kỳ 2026",
  "batchStatus": "READY",
  "provisional": true,
  "registeredCount": 120,
  "attendedCount": 105,
  "reconciledCount": 100,
  "items": [
    { "batchServiceId": "019a…", "serviceCode": "KNTQ", "serviceName": "Khám nội tổng quát",
      "displayOrder": 1, "unitPrice": 100000.00, "examinedCount": 50, "amount": 5000000.00 }
  ],
  "totalAmount": 5000000.00,
  "generatedAt": "2026-10-07T03:00:00Z"
}
```

Tiền dùng `BigDecimal` như `BatchDetailResponse`. `amount = unitPrice × examinedCount` (bằng tổng `unit_price_snapshot`), `totalAmount = Σ amount`.

### 4.6. Xuất Word

- `Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document`; `filename="bao-cao-thanh-toan-<batchCode đã lọc>.docx"`; `Cache-Control: no-store`.
- Dùng đúng read model của 4.5 (một nguồn số liệu).

### 4.7. Lỗi (giữ envelope `result/code/message`)

| Trường hợp | HTTP | Ví dụ message |
|---|---|---|
| File thiếu/rỗng/không phải OOXML, sai layout, ô X không hợp lệ, ngày sai format, trùng `participant_id` | 400 | `Row 7: column "Xét nghiệm máu" accepts only X or blank` |
| Template cũ (bộ cột dịch vụ khác batch hiện tại) | 400 | `The file does not match this batch; export it again` |
| `participant_id` không thuộc batch | 409 | `Row 5: participant is not in this batch` |
| Participant CANCELLED | 409 | `Row 5: participant is cancelled` |
| `row_version` lệch | 409 | `Row 9: participant was changed after export; export again` |
| X mới cho service `active=false` | 409 | `Row 12: service "…" is no longer offered in this batch` |
| Batch FINALIZED/CLOSED, Organization INACTIVE | 409 | `Batch does not accept examination detail changes` |
| Idempotency key dùng cho file khác / đang xử lý | 409 | `Idempotency key was used for another request` |
| Không login / thiếu quyền | 401 / 403 | Envelope chung |
| Quá kích thước / sai media type | 413 / 415 | Dùng handler đã có của import Participant |

Báo lỗi đầu tiên theo thứ tự kiểm tra xác định, số dòng Excel thực. Không đưa CCCD, họ tên, giá trị ô hay SQL vào message/log.

## 5. Contract Excel Chi tiết khám V1

### 5.1. Layout

Sheet `ChiTietKham` (nhập/xuất) và `HuongDan` (hướng dẫn + metadata `templateVersion=1`, `batchId`, `exportedAt`; chỉ để đối chiếu, backend không dựa vào metadata để cấp quyền hay chọn batch).

- Dòng 1: nhãn tiếng Việt (hiển thị). Dòng 2: **machine key** (ẩn). Dữ liệu từ dòng 3. Freeze tới cột Họ tên/dòng 2.
- Sheet protection không mật khẩu: chỉ ô X, “Ngày khám thực tế” được unlock. Đây là hỗ trợ nhập; backend vẫn validate toàn bộ.

| Cột | Key dòng 2 | Nhập lại? | Ghi chú |
|---|---|---|---|
| A (ẩn) | `participant_id` | Bắt buộc | UUID |
| B (ẩn) | `row_version` | Bắt buộc | số nguyên ≥ 0 |
| STT | `seq` | Bỏ qua | |
| Mã người khám, Họ tên, Ngày sinh, Giới tính, CCCD (mask), Phòng ban, Vị trí, Ngày khám dự kiến | `participant_code`, `full_name`, … | Bỏ qua | Chỉ để đọc; sửa roster ở tab Người khám |
| Ngày khám thực tế | `actual_examination_date` | Tùy chọn | TEXT `yyyy-MM-dd` (E4, E5) |
| Mỗi hạng mục (theo `displayOrder`) | `svc:<batch_service_id>` | Có | `X` hoặc trống; dropdown list `X` |

Thứ tự dòng: ngày khám dự kiến, mã người khám (null cuối), họ tên, id.

### 5.2. Quy tắc parser

1. Tái dùng kiểm tra package của import Participant: kích thước, OOXML thật, không XLS/XLSM/VBA/encrypted/external links, budget ZIP entries/giải nén, không FormulaEvaluator.
2. Đọc sheet `ChiTietKham`; dòng 2 phải có đủ key cố định đúng thứ tự cột hiện có; tập `svc:<id>` phải **bằng đúng** tập batch services của batch (kể cả inactive) — lệch → 400 template cũ.
3. Ô dịch vụ: STRING trim = `X`/`x` → true; BLANK hoặc chuỗi rỗng → false; mọi thứ khác (số, `1`, `✓`, FORMULA, ERROR, BOOLEAN) → 400.
4. `actual_examination_date`: chỉ TEXT ISO strict hoặc trống; ô NUMERIC (ngày Excel) → 400 (cùng quy tắc import Participant).
5. Dòng hoàn toàn trống → bỏ qua. Dòng thiếu `participant_id` nhưng có dữ liệu → 400. `participant_id` lặp → 400.
6. Parser trả typed rows `(rowNumber, participantId, rowVersion, actualDate?, Set<UUID> performedServiceIds)`; không truy vấn DB, không log giá trị ô.

### 5.3. Giới hạn đề xuất

File 5 MiB; tối đa 2.000 dòng dữ liệu; tối đa 100 cột dịch vụ; giữ budget ZIP như import Participant. Task 7 đo memory/latency với 2.000 × 30 rồi hiệu chỉnh.

## 6. Quy tắc ghi đè đối soát (file → domain)

Với mỗi dòng (sau khi khóa batch và Participants):

1. Kiểm tra Participant thuộc batch, ACTIVE, `row_version` khớp.
2. Tính `X` = tập batch service có X; `S` = service rows hiện có.
3. Nếu `X` rỗng và `S` rỗng → bỏ qua (E6). Nếu không đổi so với DB (E7) → bỏ qua.
4. Nếu `X` không rỗng: `recordAttendance(ATTENDED, actualDate theo E4, actor, now, existingNote)` khi attendance hoặc ngày thay đổi.
5. Danh sách gửi `reconcileServices`:
   - service ∈ `S`: `existing.recordPerformed(id ∈ X, actor, now)` — giữ id, giá snapshot, `service_request_id`;
   - service ∈ `X` \ `S`: row mới, UUIDv7, `performed=true`, `unitPriceSnapshot = negotiatedPrice` hiện tại, `recordedBy/At = actor/now` (domain chặn nếu service inactive).
6. `reconcileServices(list, batch.services(), actor, now)` → RECONCILED.
7. `repository.save(participant, expectedRowVersion)`.

`now` lấy một lần từ `Clock` cho cả import (domain yêu cầu `recordedAt` trùng thời điểm reconciliation).

## 7. Báo cáo và nội dung Word

### 7.1. Query

```sql
-- minh họa; viết trong mapper XML của healthexamination
SELECT ps.batch_service_id, ps.unit_price_snapshot, COUNT(*) AS examined_count, SUM(ps.unit_price_snapshot) AS amount
FROM health_examination_participant_services ps
JOIN health_examination_batch_participants p ON p.batch_id = ps.batch_id AND p.id = ps.batch_participant_id
WHERE ps.batch_id = #{batchId} AND ps.is_performed AND p.roster_status = 'ACTIVE'
GROUP BY ps.batch_service_id, ps.unit_price_snapshot;
```

Use case ghép với batch services (thứ tự `displayOrder`, thêm dòng 0 người cho service không có row), lấy tên/mã qua `ServiceCatalogQuery`, đếm registered/attended/reconciled bằng một query đếm có điều kiện. Index sẵn có `ix_health_exam_participant_services_performed (batch_id, batch_participant_id, is_performed)` phục vụ query; kiểm EXPLAIN trước khi thêm index.

### 7.2. Bố cục `.docx` (A4 dọc, XWPF)

1. Bảng header hai cột không viền: trái — tên/địa chỉ/điện thoại phòng khám (config `nkc.report.clinic.*` trong `application.yaml`, không hardcode); phải — “CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM / Độc lập – Tự do – Hạnh phúc”.
2. Tiêu đề: “BẢNG TỔNG HỢP KHÁM SỨC KHỎE VÀ GIÁ TRỊ THANH TOÁN”, thêm “(TẠM TÍNH)” khi `provisional`.
3. Thông tin: Đơn vị (tên, mã số thuế, địa chỉ), Đợt khám (tên, mã), thời gian (ngày đầu – ngày cuối), địa điểm, số người đăng ký / đã khám / đã đối soát.
4. Bảng: STT | Hạng mục | Số người khám | Đơn giá (đ) | Thành tiền (đ); dòng Tổng cộng in đậm; số định dạng `#.##0` kiểu Việt Nam (hiển thị 2 chữ số thập phân chỉ khi có phần lẻ).
5. “Bằng chữ: …” (tiện ích đọc số tiền tiếng Việt, ví dụ 1.005.000 → “Một triệu không trăm linh năm nghìn đồng”).
6. Ghi chú cách tính: “Thành tiền = số người thực tế khám từng hạng mục × đơn giá thỏa thuận”.
7. Dòng địa danh/ngày (`<city>, ngày dd tháng MM năm yyyy`, theo múi giờ Asia/Ho_Chi_Minh) và hai cột ký: “ĐẠI DIỆN ĐƠN VỊ” / “ĐẠI DIỆN PHÒNG KHÁM” + “(Ký, ghi rõ họ tên)”.

Không có dữ liệu cá nhân Người khám trong file Word.

**Rủi ro POI lite schemas:** `poi-ooxml` dùng `poi-ooxml-lite`; một số CT class (khổ giấy, viền bảng, cột) có thể thiếu và chỉ lỗi ở runtime. Test bắt buộc sinh `.docx` thật rồi mở lại bằng `XWPFDocument`. Nếu thiếu class → thêm `poi-ooxml-full` cùng version (ghi lý do trong change). Tra Context7 cho API XWPF/XSSF khi triển khai (AGENTS.md).

## 8. Thiết kế backend

Prefix: `src/main/java/com/ngockhanh/clinic/`.

### 8.1. Domain (`healthexamination.domain`)

- `HealthExaminationBatchParticipantService.newPerformed(id, batchId, participantId, batchService, actor, at)` — factory, lấy `negotiatedPrice` làm snapshot.
- (Tùy) `HealthExaminationBatchParticipant.hasSameReconciliation(Set<AggregateId>, LocalDate)` cho E7. Không đổi các invariant hiện có.

### 8.2. Application (`healthexamination.application`)

```java
// usecase
PageResponse<ExaminationDetailRowResponse> ListExaminationDetailsUseCase.execute(UUID organizationId, UUID batchId, ExaminationDetailListQuery query, UserPrincipal principal);
ExaminationSummaryResponse GetExaminationSummaryUseCase.execute(UUID organizationId, UUID batchId, UserPrincipal principal);
ExaminationDetailExportResponse ExportExaminationDetailsUseCase.execute(UUID organizationId, UUID batchId, UserPrincipal principal); // bytes + fileName
ExaminationDetailImportResponse ImportExaminationDetailsUseCase.execute(UUID organizationId, UUID batchId, ImportExaminationDetailsCommand command, UserPrincipal principal);
PaymentSummaryReportResponse GetPaymentSummaryReportUseCase.execute(UUID organizationId, UUID batchId, UserPrincipal principal);
PaymentReportDocumentResponse ExportPaymentSummaryReportUseCase.execute(UUID organizationId, UUID batchId, UserPrincipal principal); // bytes + fileName
```

- Ports: `ExaminationDetailReader` (list/summary/export rows/report aggregates; read contracts trong `application/query`), `ExaminationDetailExcelReader`, `ExaminationDetailExcelWriter`, `PaymentReportDocumentWriter`.
- `ImportExaminationDetailsCommand(byte[] workbook, UUID idempotencyKey)` — defensive copy.
- `ExaminationDetailImportCommitter` (`application/service`, `@Transactional`, gọi qua Spring proxy) — giống `ParticipantImportCommitter`; parse Excel **ngoài** transaction.
- Authorization: tái dùng kiểu `ParticipantAccessPolicy` (scope Organization/Batch + permission) — thêm policy cho SERVICE/REPORT.

### 8.3. Luồng import nguyên tử

1. Ngoài transaction: kiểm package, parse → typed rows; fingerprint SHA-256 của bytes.
2. Trong committer: khóa batch `FOR UPDATE` (cùng thứ tự khóa với import/CRUD Participant: batch → participants).
3. Recheck Organization ACTIVE, batch DRAFT/READY, chưa xóa (E1).
4. `reserve(idempotency)` → Replay thì trả receipt cũ.
5. Kiểm tập `svc:<id>` = batch services.
6. `findManyInBatchForUpdate(batchId, ids)` theo thứ tự id; kiểm thuộc batch/ACTIVE/`row_version`.
7. Áp dụng §6 cho từng dòng; `save` các Participant thay đổi.
8. `createValidatedJob` + rows (`normalized_payload` chỉ gồm `participantId`, `performedBatchServiceIds`, `actualExaminationDate` — không CCCD/họ tên), `markRowsCommitted`, `confirmJob`, `completeRequest`.
9. `audit.record("IMPORT_EXAMINATION_DETAILS", "HEALTH_EXAMINATION_BATCH", batchId, counts)`.
10. Bất kỳ lỗi nào → rollback toàn bộ. Unique/version race → 409 generic.

### 8.4. Persistence

- `HealthExaminationBatchParticipantMyBatisMapper.xml`: `findManyInBatchForUpdate`.
- `ExaminationDetailMyBatisMapper` + XML mới: `countRows/findRows` (page header Participants) + `findPerformedServiceIds(participantIds)` (một query, không N+1), `summarize`, `findExportRows` (toàn bộ ACTIVE + performed ids), `aggregatePerformedServices` (§7.1). Views trong `infrastructure/persistence/view` (`ExaminationDetailRowView`, `ServiceAmountView`, `ExaminationCountView`).
- `MyBatisExaminationDetailReader` implement port.
- Mask CCCD dùng chung hàm mask của list Participant (không viết bản thứ hai).

### 8.5. Integration (`integration.application.imports`)

- Published contract `ServiceReconciliationImportStore` (reserve / createValidatedJob / markRowsCommitted / confirmJob / completeRequest) cùng adapter `MyBatisServiceReconciliationImportStore`; tái dùng mapper/SQL chung của `idempotency_keys`/`import_jobs` bên trong module `integration`. Join transaction của caller, không tự mở transaction.
- `BatchHistoryQuery` hiện dùng để chặn xóa batch: xác nhận vẫn đúng khi có job loại mới (batch có lịch sử import → không xóa được).

### 8.6. Adapters

- `healthexamination/infrastructure/excel`: `OoxmlPackageGuard` (tách từ reader Participant, giữ hành vi + tests cũ), `PoiExaminationDetailExcelReader`, `PoiExaminationDetailExcelWriter`, `ExaminationDetailExcelColumns`.
- `healthexamination/infrastructure/word`: `PoiPaymentReportDocxWriter`, `VietnameseAmountInWords`, `ReportClinicProperties` (`@ConfigurationProperties("nkc.report.clinic")`: name, address, phone, city).

### 8.7. API, security, migrations

- Controllers: `ExaminationDetailController` (4 route), `BatchReportController` (2 route). Controller chỉ map request → command/query và thêm headers.
- `SecurityConfiguration`: matchers cho 6 route với `staffWith("PERM_…")` như Participant.
- `V005__add_examination_detail_and_report_permissions.sql`: 3 permission + grant ADMIN, CLINIC_MANAGER (`ON CONFLICT DO NOTHING`).
- `V006__add_service_reconciliation_import_type.sql`: drop/add `ck_import_jobs_type` thêm `HEALTH_EXAMINATION_SERVICE_RECONCILIATION`.
- `R__local_access_control_seed.sql`: thêm 3 permission vào grant local.
- Logging: chỉ batchId, số dòng, số Participant cập nhật, thời gian; không filename, giá trị ô, CCCD, họ tên.

## 9. Thiết kế frontend (`src/modules/health-examinations`)

- `api/examination-details.ts`: `buildExaminationDetailListUrl`, `fetchExaminationDetails`, `fetchExaminationSummary`, `downloadExaminationDetailExport` (blob + tên file từ `Content-Disposition`, fallback `chi-tiet-kham.xlsx`), `importExaminationDetails(file, idempotencyKey)`.
- `api/reports.ts`: `fetchPaymentSummaryReport`, `downloadPaymentSummaryDocx`.
- `types/transport.ts`: zod schema cho 4 response; thay type mock `ParticipantExaminationProgress`/`HealthExaminationBatchReportSummary` bằng type map từ DTO.
- `query-keys.ts`: `examinationDetails(org, batch, params)`, `examinationSummary(org, batch)`, `paymentReport(org, batch)`.
- Hooks: `useExaminationDetails`, `useExaminationSummary`, `useExportExaminationDetails`, `useImportExaminationDetails` (sinh `crypto.randomUUID()` một lần cho mỗi lần chọn file; retry dùng lại key; thành công → invalidate examination, report, participants), `usePaymentSummaryReport`, `useExportPaymentSummaryDocx`.
- Tab Chi tiết khám (`examination-detail-tab/*`): nhận `organizationId` + `batch`; cột = `batch.services`; dải bộ đếm từ summary; toolbar: ô tìm, select trạng thái (4 nhãn §4.1); bỏ danh sách phòng ban hardcode và cột/badge “Ghi chú” mock (E8); nút “Xuất Excel” (SERVICE_READ), “Nhập Excel” (SERVICE_RECONCILE, chỉ khi batch DRAFT/READY). `ExaminationDetailImportDialog` theo khuôn `ParticipantImportDialog` (kiểm `.xlsx` ≤ 5 MiB phía client, hiển thị message lỗi dòng từ backend, nhắc “xuất lại file” khi 409 version).
- Tab Báo cáo (`report-tab/*`): dữ liệu thật; badge “Tạm tính” khi `provisional`; dòng thông tin số người; nút “Xuất Word” (REPORT_READ) và “Xuất Excel chi tiết” (SERVICE_READ); xóa `utils/export-excel.ts` và nút “Excel tổng hợp (dọc)” (E15); `CalculationExample` hiển thị ví dụ từ dòng đầu tiên thật thay vì số cứng.
- `health-examination-batch-detail-page.tsx`: bỏ `examination`/`report` khỏi `UNSUPPORTED_TABS`; ẩn tab khi thiếu quyền tương ứng (`utils/participant-permissions.ts` → thêm 3 hằng permission).
- Ngoài phạm vi: `OrganizationExaminationDetailTab`, `organization-reports-tab` (cấp Organization) vẫn giữ trạng thái hiện tại; ghi follow-up.

## 10. Danh sách file dự kiến

Backend (mới): 6 use case; `ExaminationDetailImportCommitter`; ports/read contracts/commands/responses tương ứng; `ExaminationDetailController`, `BatchReportController` + request DTO list; mapper Java/XML + views; `MyBatisExaminationDetailReader`; adapters Excel/Word + properties; `ServiceReconciliationImportStore` + adapter; V005, V006; `docs/api/examination-details-and-report.md`.

Backend (sửa): `HealthExaminationBatchParticipantService`, `HealthExaminationBatchParticipantRepository` + MyBatis impl/XML, `PoiParticipantExcelReader` (dùng `OoxmlPackageGuard`), `SecurityConfiguration`, `R__local_access_control_seed.sql`, `application.yaml` (`nkc.report.clinic.*`), `HealthExaminationApiSurfaceTest`, `CleanSlateMigrationContractTest`, `docs/architecture/03-domain-and-workflows.md` (mục mới “Examination details and payment report”), `05-api-and-security.md`, `CONTEXT.md` nếu cần thuật ngữ.

Frontend: `api/examination-details.ts`, `api/reports.ts`, `api/index.ts` (gỡ `unavailableApi` tương ứng), `types/*`, `query-keys.ts`, `hooks/use-health-examination-batches.ts`, components hai tab + dialog import, page batch detail, `utils/participant-permissions.ts`, xóa `utils/export-excel.ts`; tests trong `__tests__/` và `e2e/support/mock-backend.ts`.

## 11. Task triển khai

### Task 0 — Chốt contract và tài liệu
- [ ] Chủ dự án chốt E1–E15; ghi quyết định vào `03-domain-and-workflows.md` và tạo `docs/api/examination-details-and-report.md`.
- [ ] Kiểm lại worktree, giữ thay đổi đang dở (CRUD/reactivate Participant).

### Task 1 — Domain
- [ ] Factory `newPerformed`, helper so sánh không đổi.
- [ ] Tests `HealthExaminationBatchParticipantTest`: X → blank giữ row `false`; X mới dùng giá thỏa thuận; service inactive bị chặn; ATTENDED cần ngày; CANCELLED bị chặn.

### Task 2 — Persistence & read model
- [ ] `findManyInBatchForUpdate`, mapper `ExaminationDetail*`, views, reader.
- [ ] Integration tests PostgreSQL 18 (Testcontainers): phân trang/filter/mask, không N+1, summary, aggregate theo giá snapshot (2 giá cho cùng service → 2 dòng), Participant CANCELLED bị loại.

### Task 3 — Excel adapters
- [ ] Tách `OoxmlPackageGuard`; tests import Participant cũ vẫn xanh.
- [ ] Writer: layout, dòng key ẩn, cột ẩn, text format, dropdown X, protection, tên file.
- [ ] Reader: X/x/blank, giá trị lạ, FORMULA, ngày numeric, key sai/thiếu, `participant_id` lặp, giới hạn dòng/cột.
- [ ] Round-trip: export → import không sửa → 0 Participant cập nhật.

### Task 4 — Integration store + import use case
- [ ] V006; `ServiceReconciliationImportStore` + adapter + tests reserve/replay/conflict.
- [ ] `ImportExaminationDetailsUseCase` + committer theo §8.3; tests: ghi đè, bỏ qua dòng trống (E6), không đổi (E7), version lệch, sai batch, CANCELLED, FINALIZED/CLOSED, Organization INACTIVE, replay, rollback toàn bộ khi một dòng lỗi, audit đúng một event không có dữ liệu cá nhân.

### Task 5 — Báo cáo + Word
- [ ] `GetPaymentSummaryReportUseCase` (ghép catalog, dòng 0, provisional).
- [ ] `VietnameseAmountInWords` tests: 0, 15, 21, 105, 1.000.000, 1.005.000, 1.000.000.000, số lẻ thập phân.
- [ ] `PoiPaymentReportDocxWriter`: sinh thật, mở lại bằng `XWPFDocument`, assert tiêu đề, (TẠM TÍNH), các dòng, tổng, bằng chữ, chỗ ký; xử lý thiếu class lite (§7.2).

### Task 6 — API, security, migration V005
- [ ] Controllers, V005, seed local, matchers.
- [ ] Controller tests: content-type, `Content-Disposition`, `no-store`, multipart, 400/401/403/404/409/413/415; security tests từng permission; cập nhật `HealthExaminationApiSurfaceTest`, `CleanSlateMigrationContractTest`; Spring Modulith verify.

### Task 7 — Frontend
- [ ] API, zod, query keys, hooks; gỡ mock và `utils/export-excel.ts`.
- [ ] Hai tab + dialog import + nút xuất + gating quyền/trạng thái batch.
- [ ] Vitest: URL builders, schema, mapping nhãn trạng thái, dialog lỗi (400 dòng, 409 version), ẩn nút theo quyền; cập nhật e2e mock backend.

### Task 8 — Hiệu năng, tài liệu, verify cuối
- [ ] Đo import/export 2.000 dòng × 30 dịch vụ (thời gian, heap); hiệu chỉnh budget.
- [ ] Hoàn thiện tài liệu API/architecture; completion report theo AGENTS.md.

## 12. Lệnh verification

```bash
# Backend (definition of done; verify đã gồm test). Báo rõ nếu Docker/Redis không có → skip integration.
./mvnw verify

# Frontend
pnpm lint && pnpm typecheck && pnpm check:terminology && pnpm test && pnpm build
pnpm test:e2e
```

## 13. Acceptance checklist

- [ ] Ma trận hiển thị đúng X theo `is_performed`, CCCD luôn mask, phân trang/filter hoạt động.
- [ ] File xuất mở được trong Excel, nhập lại ngay không thay đổi gì.
- [ ] Sửa X/bỏ X trong file → DB đúng theo §6; Người khám không có trong file giữ nguyên.
- [ ] Một dòng lỗi → không ghi gì; message có số dòng, không lộ dữ liệu cá nhân.
- [ ] Retry cùng `Idempotency-Key` trả cùng kết quả; key cũ + file khác → 409.
- [ ] Báo cáo: thành tiền = số người thực tế × giá snapshot; tổng khớp giữa JSON, màn hình và Word.
- [ ] Word mở được bằng Word/LibreOffice, có “Bằng chữ”, chỗ ký, “(TẠM TÍNH)” khi chưa FINALIZED.
- [ ] Đúng permission cho 6 route; audit cho import và hai loại xuất.
- [ ] `./mvnw verify` và bộ lệnh frontend chạy thật; báo cáo skip nếu có.

## 14. Rủi ro và follow-up

- **Chưa có endpoint chuyển trạng thái batch** → báo cáo luôn “Tạm tính” cho tới khi có use case READY/FINALIZED/CLOSED (kế hoạch riêng).
- **POI lite schemas** cho XWPF có thể thiếu class → cần test sinh file thật; có thể phải thêm `poi-ooxml-full`.
- **Ghi đè theo file** có thể bỏ X nhầm nếu người dùng xóa ô; giảm thiểu bằng E3 (version), E6 (dòng trống không đảo attendance) và thông báo số Participant bị thay đổi sau import.
- Sửa/huỷ điểm danh (ATTENDED → ABSENT) và nhập kết quả lâm sàng nằm ngoài phạm vi.
- Tab cấp Organization (chi tiết khám, báo cáo nhiều batch) giữ nguyên, cần kế hoạch riêng.
