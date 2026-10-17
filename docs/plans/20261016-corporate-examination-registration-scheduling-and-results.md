# Kế hoạch thay Batch bằng Thỏa thuận + Lịch khám cho khám sức khỏe doanh nghiệp

**Ngày lập:** 16/10/2026. **Bản sửa:** 16/10/2026 (rút gọn phạm vi, bổ sung hiện trạng đã xác minh).

**Trạng thái:** Đề xuất để review nghiệp vụ và kỹ thuật; chưa được chấp nhận làm hợp đồng triển khai.
Tài liệu này không thay đổi runtime, schema hoặc API.

**Hướng:** Batch tách thành **Thỏa thuận** (dịch vụ, giá, hiệu lực) và **Lịch khám** (ngày,
địa điểm, loại hoạt động). Participant gắn với Thỏa thuận, được **phân lịch** độc lập và có thể có nhiều lịch.

## 1. Mục tiêu và phạm vi

### 1.1. Vấn đề

| Vấn đề nghiệp vụ | Nguyên nhân trong code/schema hiện tại |
|---|---|
| Phải biết ngày khám mới nhập được danh sách | `health_examination_batch_participants.batch_day_id NOT NULL` |
| Lấy mẫu và khám khác ngày | Một Participant chỉ có một `batch_day_id` |
| Đổi lịch, khám bù, không đến | Attendance nằm trên Participant, không có lịch sử theo từng lịch |
| Không thêm được dịch vụ sau khi bắt đầu | `HealthExaminationBatch.updateDraft` yêu cầu DRAFT; dịch vụ khóa từ READY |
| Địa điểm cố định cho cả đợt | `examination_site_*` nằm trên Batch |
| Báo cáo/in phiếu chỉ theo Batch | Mọi query/report/print scope theo `batch_id` |

### 1.2. Trong phạm vi plan này (bản lõi)

- Thỏa thuận: dịch vụ, giá thỏa thuận, hiệu lực; thêm dịch vụ khi đang áp dụng.
- Participant thuộc Thỏa thuận, chưa cần lịch; thêm thủ công và import Excel.
- Lịch khám theo ngày, địa điểm, loại hoạt động; phân lịch, đổi, hủy, khám bù, không đến.
- **Không** quản lý sức chứa và **không** chia ca/giờ: nhân viên tự tính số người cho từng ngày
  khi phân lịch; hệ thống chỉ hiển thị số người đã phân để tham khảo.
- Đối soát dịch vụ thực tế, báo cáo thanh toán, in phiếu chuyển sang phạm vi mới.
- Frontend thay màn Batch bằng Thỏa thuận / Đăng ký / Lịch khám / Đối soát.

### 1.3. Tách sang plan riêng (không chặn bản lõi)

| Plan sau | Nội dung | Lý do tách |
|---|---|---|
| Chuẩn bị hồ sơ | Tra/tạo Patient bằng CCCD, tạo Encounter + HealthExaminationRecord | Chưa có use case/API nào; là tính năng mới, không phải migration |
| Chuyên môn | Chỉ định, lấy mẫu, nhập/duyệt kết quả, hoàn tất/phát hành | Thuộc clinical/diagnostics/billing; cần các quyết định riêng |
| Self-service | Email xác nhận lịch, yêu cầu dịch vụ thêm | Phụ thuộc portal/notification và mô hình lõi đã ổn định |
| Vận hành nâng cao | Bàn khám, cân bằng tải, worklist lấy mẫu | Chỉ cần sau khi có dữ liệu thực hiện |

Bản lõi **giữ nguyên hành vi** của các phần chuyên môn hiện có trong schema (record, version,
trigger phát hành); chỉ đổi tham chiếu `batch_*` sang bảng mới.

## 2. Hiện trạng đã xác minh

| Điểm | Bằng chứng | Hệ quả cho plan |
|---|---|---|
| Chưa go-live | `docs/architecture/07-open-items.md` (Session go-live blockers); `04-persistence.md` "no deployed-data conversion" | Có thể không cần backfill; xem D01 |
| Migration hiện có | `V001`–`V003` | Thay đổi bằng `V004`, không sửa V001–V003 |
| FINALIZED/CLOSED chưa dùng | `HealthExaminationBatch.finalizeBatch()/close()` không có use case gọi | Không phải map lifecycle chốt Batch |
| Chuẩn bị hồ sơ chưa có | `HealthExaminationBatchParticipant.prepare()` không có use case; record chỉ có aggregate/persistence record | Không nằm trong bản lõi |
| Đổi ngày đã có một phần | `HealthExaminationBatchParticipant.moveToDay()` | Thay bằng phân lịch có lịch sử |
| Đối soát giữ giá snapshot, hàng bỏ chọn | `reconcileServices()` | Giữ nguyên quy tắc, đổi phạm vi từ Batch sang Thỏa thuận |
| Import bắt buộc gắn Batch | `import_jobs.batch_id`, `ck_import_jobs_batch`, `trg_validate_batch_participant` | Đổi sang `agreement_id` |
| Phạm vi ảnh hưởng | grep `batchId/batch_id/BatchDay`: ~105 file main, ~54 file test backend, ~59 file frontend | Refactor lớn; chia theo đợt ở mục 8 |

### 2.1. Ràng buộc DB tham chiếu Batch

| Đối tượng trong V001 | Hiện làm gì | Hành động trong V004 |
|---|---|---|
| `trg_validate_batch_participant` | Import job cùng Batch; khóa đổi Patient khi đã có hồ sơ | Đổi sang `agreement_id`; giữ phần khóa Patient |
| `trg_validate_participant_service_scope` | Dịch vụ đối soát thuộc `batch_services` cùng Batch; ServiceRequest đúng Patient/Encounter | Đổi sang `agreement_services`; giữ phần ServiceRequest |
| `trg_validate_health_exam_record_scope` | Hồ sơ dùng đúng Patient của Participant | Đổi tên bảng/cột tham chiếu |
| `trg_validate_record_version_item` | Item tham chiếu ParticipantService đã performed | Đổi join `batch_services` → `agreement_services` |
| `trg_validate_record_version_official` | Phát hành chính thức yêu cầu Participant `RECONCILED` và đủ item | **Giữ nguyên ngữ nghĩa** trong bản lõi; xem câu hỏi mở Q1 |
| `ck_import_jobs_batch`, `ix_import_jobs_batch_created` | Import phải có Batch | Đổi sang `agreement_id` |
| Index `ix_health_exam_batch_participants_*` | Lọc theo Batch/ngày/attendance | Thay bằng index theo agreement và assignment |
| `ux` hồ sơ active theo `batch_participant_id` | Một hồ sơ đang hoạt động cho một Participant | Giữ, đổi tên cột |

`notifications.batch_id` tham chiếu `notification_batches`, **không** liên quan.

## 3. Quyết định cần chốt

Mỗi quyết định có đáp án mặc định; phòng khám chỉ cần đồng ý hoặc sửa.

### 3.1. Gate G0: chặn bắt đầu bản lõi

| ID | Câu hỏi | Mặc định đề xuất |
|---|---|---|
| D01 | Có môi trường nào giữ dữ liệu không được mất (staging/UAT/khách)? | **Không.** Dùng chiến lược A (mục 7): V004 tái cấu trúc, dữ liệu dev nạp lại từ `db/local` |
| D02 | Phạm vi chống trùng đăng ký | Một đăng ký ACTIVE cho mỗi (Thỏa thuận, CCCD). Năm sau/đợt sau tạo Thỏa thuận mới |
| D03 | Một lịch khám xác định bởi gì | (Đơn vị, ngày, địa điểm, loại hoạt động) là duy nhất. Không có ca/giờ, không có sức chứa (đã chốt: nhân viên tự chia) |
| D04 | Import có kèm lịch không | **Không.** Import chỉ tạo danh sách; phân lịch là bước riêng (giữ all-or-nothing đơn giản) |
| D05 | Thêm dịch vụ khi Thỏa thuận đang áp dụng | Được thêm dòng mới; dòng đã được dùng trong đối soát không sửa giá, chỉ ngừng áp dụng và thêm dòng mới |

### 3.2. Chốt trong lúc triển khai (không chặn bắt đầu)

| ID | Câu hỏi | Mặc định đề xuất | Cần trước |
|---|---|---|---|
| D06 | Lifecycle Thỏa thuận | DRAFT → ACTIVE → ENDED; sửa danh sách/đối soát khi DRAFT/ACTIVE | Đợt 2 |
| D07 | Thời hạn đổi/hủy lịch | Không chặn theo thời gian trong bản lõi; chỉ chặn lịch đã ATTENDED | Đợt 2 |
| D08 | Mã đăng ký | Giữ `participant_code`, sinh theo Thỏa thuận; mã đã cấp không cấp lại | Đợt 2 |
| D09 | In phiếu khi có nhiều lịch | In theo lịch được chọn; phiếu hiện ngày của lịch đó | Đợt 2 |
| D10 | Phạm vi báo cáo thanh toán | Thỏa thuận + khoảng ngày (tùy chọn); ghi `generatedAt` và bộ lọc | Đợt 2 |

### 3.3. Câu hỏi mở cho plan chuyên môn

| ID | Câu hỏi | Ghi chú |
|---|---|---|
| Q1 | Phát hành hồ sơ chính thức có phải chờ đối soát `RECONCILED`? | Trigger hiện tại bắt buộc; bản lõi giữ nguyên |
| Q2 | Khám nhiều ngày dùng một hay nhiều Encounter | Đề xuất một Encounter kéo dài, giữ `encounter_id UNIQUE` |
| Q3 | Bằng chứng lấy mẫu/lấy lại mẫu | Thuộc diagnostics |
| Q4 | Bên chi trả hỗn hợp, dịch vụ cá nhân tự trả | Thuộc billing |

## 4. Mô hình đích

### 4.1. Khái niệm

| Khái niệm | Chứa | Không chứa |
|---|---|---|
| Organization | Đơn vị (giữ nguyên) | — |
| Thỏa thuận (Agreement) | Dịch vụ, giá thỏa thuận, hiệu lực, trạng thái | Ngày khám, địa điểm, tiến độ |
| Participant | Snapshot người đăng ký trong một Thỏa thuận; trạng thái đối soát | Ngày khám, attendance |
| Lịch khám (Session) | Ngày, địa điểm, loại hoạt động | Giá, dịch vụ, ca/giờ, sức chứa |
| Phân lịch (Assignment) | Participant ↔ Lịch; trạng thái đến/không đến/đổi/hủy; lịch sử | Bằng chứng thực hiện dịch vụ |
| ParticipantService | Dịch vụ thực tế + giá snapshot (giữ nguyên quy tắc hiện tại) | Kết quả chuyên môn |

Owner: toàn bộ thuộc module `healthexamination`. **Không** đặt lịch tập thể trong `appointment`,
vì `appointments` bắt buộc Patient/bác sĩ/khoa/dịch vụ và sẽ tạo phụ thuộc hai chiều giữa hai module.

### 4.2. Schema đích (phác thảo V004)

```sql
-- Batch -> Agreement (bỏ site; ngày chuyển sang sessions)
health_examination_agreements (
    id, organization_id, agreement_code UNIQUE, name,
    valid_from date NULL, valid_to date NULL,
    status DRAFT|ACTIVE|ENDED, created_by, created_at, updated_at, row_version, deleted_at,
    UNIQUE (organization_id, id))

-- BatchService -> AgreementService (giữ cột giá, thêm được khi ACTIVE)
health_examination_agreement_services (
    id, agreement_id, service_id, reference_price_snapshot, negotiated_price,
    display_order, active, created_at, updated_at, row_version,
    UNIQUE (agreement_id, id))
    -- bỏ UNIQUE (batch_id, service_id); thay bằng partial unique (agreement_id, service_id) WHERE active

-- BatchDay -> Session
health_examination_sessions (
    id, organization_id, agreement_id NULL,
    session_date date,
    activity ALL|SAMPLING|EXAMINATION,
    site_type CLINIC|ORGANIZATION_SITE, site_address,
    status OPEN|CANCELLED, created_by, created_at, updated_at, row_version,
    UNIQUE (organization_id, id))
    -- partial unique (organization_id, session_date, site_type, site_address, activity) WHERE status = 'OPEN'  (D03)

-- BatchParticipant -> Participant
health_examination_participants (
    ... các cột snapshot hiện có ...,
    organization_id, agreement_id,                 -- thay batch_id
    -- bỏ batch_day_id, attendance_status, actual_examination_date, attendance_recorded_*, attendance_note
    FOREIGN KEY (organization_id, agreement_id) REFERENCES agreements (organization_id, id),
    UNIQUE (organization_id, id), UNIQUE (agreement_id, id))
    -- partial unique (agreement_id, identification_number) WHERE roster_status = 'ACTIVE'   (D02)

-- Mới
health_examination_session_assignments (
    id, organization_id, participant_id, session_id,
    status ASSIGNED|ATTENDED|NO_SHOW|MOVED|CANCELLED,
    replaced_by_assignment_id NULL, attended_at NULL, note NULL,
    recorded_by, recorded_at, created_at, updated_at, row_version,
    FOREIGN KEY (organization_id, participant_id) REFERENCES participants (organization_id, id),
    FOREIGN KEY (organization_id, session_id) REFERENCES sessions (organization_id, id),
    CHECK ((status = 'ATTENDED') = (attended_at IS NOT NULL)))
    -- partial unique (participant_id, session_id) WHERE status IN ('ASSIGNED','ATTENDED')

-- ParticipantService: batch_id -> agreement_id, batch_service_id -> agreement_service_id
-- import_jobs: batch_id -> agreement_id
-- health_examination_records: batch_participant_id -> participant_id
```

Tên bảng/cột chốt trong ADR ở Đợt 0. Composite FK theo `organization_id` giữ cùng kiểu kiểm tra
phạm vi bằng khóa như schema hiện tại, không cần trigger mới.

### 4.3. Phân lịch và đổi lịch

- Không kiểm tra sức chứa. Số người đã phân mỗi lịch được tính khi đọc (`COUNT` assignment
  ASSIGNED/ATTENDED), không lưu bộ đếm.
- Chống phân trùng bằng partial unique `(participant_id, session_id)`; phân hàng loạt bỏ qua người
  đã có trong lịch và trả về danh sách bị bỏ qua.
- Đổi lịch: một transaction, kiểm tra `row_version` của assignment cũ, đánh dấu `MOVED` +
  `replaced_by_assignment_id`, tạo assignment mới. Lỗi → rollback toàn bộ.
- Không đổi/hủy assignment đã `ATTENDED`. Không hủy lịch còn assignment ASSIGNED/ATTENDED.

### 4.4. Bất biến của bản lõi

1. Nhập danh sách không tạo Patient/Encounter/hồ sơ.
2. Đổi lịch không tạo lại Participant, không đổi giá snapshot, ParticipantService hay hồ sơ.
3. Không đến một lịch chỉ ghi ở assignment đó; Participant vẫn ACTIVE.
4. Giá đã ghi ở ParticipantService không đổi khi giá Thỏa thuận thay đổi; hàng bỏ chọn được giữ.
5. Một Participant không có hai assignment đang hiệu lực trong cùng một lịch.
6. Hủy đăng ký/lịch giữ lịch sử; không xóa dữ liệu chuyên môn/tài chính.

Quy tắc chung (expected version, audit cùng transaction, idempotency, không log CCCD, quyền ở backend)
áp dụng theo PROJECT_RULES §21–28; không nhắc lại ở từng luồng.

## 5. Luồng nghiệp vụ bản lõi

| Luồng | Các bước | Ghi chú |
|---|---|---|
| L1. Thỏa thuận | Tạo DRAFT → chọn dịch vụ + giá → kích hoạt ACTIVE → thêm/ngừng dịch vụ khi cần → ENDED | Thay `CreateHealthExaminationBatch`, `UpdateHealthExaminationBatch`, `StartHealthExaminationBatch` |
| L2. Đăng ký | Thêm/sửa/hủy/khôi phục Participant; import Excel add-only, all-or-nothing, idempotent | Template bỏ cột ngày khám (D04) |
| L3. Lịch | Tạo/sửa/hủy lịch; xem số người đã phân | Hủy lịch có người → yêu cầu đổi/hủy assignment trước |
| L4. Phân lịch | Chọn nhiều Participant → phân vào lịch; đổi; hủy; khám bù = phân lịch mới sau NO_SHOW | Lọc "chưa xếp lịch", "đã có lịch SAMPLING chưa có EXAMINATION" |
| L5. Ghi nhận đến | Đánh dấu ATTENDED/NO_SHOW theo assignment | Thay phần attendance của `RecordExaminationDetailUseCase` |
| L6. Đối soát | Ghi dịch vụ thực tế theo dịch vụ của Thỏa thuận; giữ quy tắc `reconcileServices()` | Import đối soát đổi phạm vi sang Thỏa thuận |
| L7. Báo cáo | Payment summary + export theo Thỏa thuận (+ khoảng ngày) | Đếm riêng: số người, số lịch, số lượt đến |
| L8. In phiếu | Một người hoặc theo lịch (thay scope `BATCH_DAY`) | Job in cũ vẫn đọc được |

## 6. API

Đường dẫn dưới đây là đề xuất; chốt trong `docs/api/` ở Đợt 0. Giữ envelope, pagination, error hiện tại.
Hệ thống chưa go-live nên **thay thẳng** API Batch, không giữ song song.

| Hiện tại | Đề xuất |
|---|---|
| `/organizations/{orgId}/health-examination-batches` (CRUD, `/start`) | `/organizations/{orgId}/agreements` (CRUD, `/activate`, `/end`, `/services`) |
| `.../batches/{batchId}/participants` (CRUD, `/imports`, `/import-template`) | `/organizations/{orgId}/agreements/{agreementId}/participants` (giữ các route con) |
| — | `/organizations/{orgId}/sessions` (CRUD, kèm số người đã phân) |
| — | `/organizations/{orgId}/sessions/{sessionId}/assignments` (phân, đổi, hủy, đến/không đến) |
| `.../examination-details` (`/export`, `/summary`, import) | `/organizations/{orgId}/agreements/{agreementId}/examination-details` |
| `.../payment-summary`, `/docx` | `.../agreements/{agreementId}/payment-summary?from=&to=` |
| `/examination-form`, `/examination-form-print-jobs` scope `BATCH_DAY` | scope `SESSION` hoặc danh sách participant |

Permission mới cho Thỏa thuận, Lịch, Phân lịch phải có trong route matrix (ADR-0015) và seed V00x.

## 7. Chiến lược migration

| Chiến lược | Điều kiện (D01) | Việc phải làm |
|---|---|---|
| **A (mặc định)** | Không có dữ liệu cần giữ | V004: `ALTER TABLE ... RENAME`, thêm/bỏ cột, tạo bảng mới, viết lại trigger; dữ liệu dev nạp lại từ `db/local` |
| B | Có dữ liệu cần giữ | Như A, cộng backfill: mỗi `batch_day` → một session (giữ ngày + site, activity = ALL); mỗi participant → một assignment theo `batch_day_id`, `attendance_status` → ATTENDED/NO_SHOW/ASSIGNED. Đối chiếu số dòng, giá, tổng tiền trước/sau; backup đã kiểm chứng trước khi chạy |

Cả hai: không sửa V001–V003; không `DROP ... CASCADE`; không lấy giá từ catalog hiện tại thay giá snapshot.
Dùng `RENAME` thay vì tạo bảng mới + copy để giữ id, FK từ `health_examination_records`, `import_jobs`
và lịch sử audit.

## 8. Các đợt triển khai

Mỗi đợt kết thúc khi `.\mvnw.cmd verify` (và frontend checks cho Đợt 3) chạy thành công; ghi rõ skip.

### Đợt 0. Chốt hợp đồng (tài liệu)

- [ ] Ghi kết quả D01–D05 và người quyết định.
- [ ] ADR mới thay phần Batch/Participant của ADR-0013 (không sửa ADR cũ, đánh dấu superseded một phần).
- [ ] Cập nhật API docs: `organizations-and-batches.md`, `participant-*.md`, `examination-details-and-report.md`, `examination-form-print*.md`, `clean-slate-migration.md`.
- [ ] Danh sách permission mới.

**Xong khi:** ADR accepted; API docs ghi rõ request/response/permission/error cho mục 6.

### Đợt 1. Schema và domain

**Files:** `db/migration/V004__*.sql`; `healthexamination/domain/**`; `infrastructure/persistence/{record,mapper}`; `resources/mapper/healthexamination/*.xml`; `resources/mapper/integration/{ParticipantImport,BatchHistory}Mapper.xml`; `db/local` seed.

- [ ] V004 theo mục 4.2 và bảng 2.1 (trigger viết lại, index mới).
- [ ] `HealthExaminationBatch` → `HealthExaminationAgreement` (bỏ site/days; cho thêm dịch vụ khi ACTIVE theo D05).
- [ ] `HealthExaminationBatchDay` → aggregate `HealthExaminationSession` (ngày, địa điểm, loại hoạt động).
- [ ] Aggregate mới `SessionAssignment` (ASSIGNED/ATTENDED/NO_SHOW/MOVED/CANCELLED).
- [ ] `HealthExaminationBatchParticipant` → `HealthExaminationParticipant`: bỏ `moveToDay`, `recordAttendance`, `batchDayId`; giữ `reconcileServices`, `cancel`, `reactivate`, `prepare`.
- [ ] Repository + MyBatis mapper tương ứng.
- [ ] Tests: domain (chuyển trạng thái assignment, thêm dịch vụ, giá snapshot); Testcontainers (Flyway fresh, composite FK, partial unique lịch và assignment, trigger, hai giao dịch phân cùng người vào cùng lịch).

**Xong khi:** schema mới migrate sạch trên DB trống; test domain/persistence xanh.

### Đợt 2. Use case và API backend

**Files:** `healthexamination/{application,api}/**`; `integration/application/imports/**`, `integration/infrastructure/persistence/**`; Excel reader/writer; `ExaminationFormSourceLoader`, `ExaminationFormValuesSource`, `ExaminationFormPrintScope`; `PaymentSummaryReportAssembler`; permission seed.

- [ ] L1 Thỏa thuận: thay 5 use case Batch.
- [ ] L2 Đăng ký + import: đổi scope sang Thỏa thuận; template bỏ cột ngày; giữ idempotency/receipt.
- [ ] L3–L5 Lịch, phân lịch, đổi, hủy, đến/không đến (mục 4.3).
- [ ] L6 Đối soát: tách attendance khỏi `RecordExaminationDetailUseCase`; import đối soát theo Thỏa thuận.
- [ ] L7 Báo cáo: payment summary/export theo Thỏa thuận + khoảng ngày.
- [ ] L8 In phiếu: scope `SESSION`; job cũ đọc được.
- [ ] Xóa controller/use case/route Batch cũ.
- [ ] Tests: API (quyền, scope sai organization, stale version, idempotent import, phân trùng, đổi đồng thời, đổi assignment đã ATTENDED), report (đa ngày, nhiều giá cùng dịch vụ, hàng bỏ chọn), print.

**Xong khi:** mọi case mục 9 thuộc backend có test xanh; không còn tham chiếu `batch` trong `healthexamination`/`integration` (trừ migration lịch sử).

### Đợt 3. Frontend

**Files:** `ngoc_khanh_clinic_frontend/src/modules/healthexamination/{batches,organizations}`; routes dưới `src/app`; API/hooks/types/schemas/tests.

- [ ] Thay module `batches` bằng các tab trong Chi tiết đơn vị: Thỏa thuận / Đăng ký / Lịch khám / Đối soát.
- [ ] Đăng ký: lọc "chưa xếp lịch", chọn nhiều → phân lịch.
- [ ] Lịch khám: danh sách theo ngày, số người đã phân, người trong lịch, đổi/hủy, đến/không đến.
- [ ] Hiển thị lỗi conflict từ backend (phân trùng, stale version); không tự tính rule ở frontend.
- [ ] Giữ bộ lọc ngày, tìm kiếm, trang khi quay lại.
- [ ] `pnpm lint`, `typecheck`, `check:terminology`, `test`, `build`, `test:e2e:backend` cho luồng: tạo Thỏa thuận → import → phân lịch → đổi lịch → đến → đối soát → báo cáo → in.

**Xong khi:** E2E với backend thật pass; không còn route/mã Batch.

### Đợt 4. Đóng bản lõi

- [ ] Cập nhật `CONTEXT.md` (thuật ngữ Thỏa thuận, Lịch khám, Phân lịch), `docs/architecture/02-module-contracts.md`, `03-domain-and-workflows.md`, `07-open-items.md`.
- [ ] Completion report: modules, files, migration, API, tests, lệnh đã chạy, skip, quyết định còn mở.
- [ ] Lập plan riêng cho chuẩn bị hồ sơ và chuyên môn (mục 1.3) từ Q1–Q4.

Đợt 2 và 3 có thể làm song song theo từng luồng (L1 → L2 → L3–L5 → L6–L8) khi API của luồng đó đã ổn định.

## 9. Acceptance cases của bản lõi

| Case | Kết quả cần đạt |
|---|---|
| Import 300 người chưa biết ngày | 300 Participant chưa có lịch; không tạo Patient/Encounter |
| Gửi lại cùng file/idempotency key | Trả receipt cũ; không thêm người |
| Cùng CCCD hai lần trong một Thỏa thuận | Lỗi trùng (D02); Thỏa thuận khác thì hợp lệ |
| Phân 300 người vào 5 ngày theo số nhân viên tự chia | Thành công; mỗi lịch hiện đúng số người đã phân |
| Phân hàng loạt có người đã ở trong lịch | Người đó bị bỏ qua và được liệt kê; người khác vẫn được phân |
| Đổi từ ngày 20 sang 22 | Cùng Participant, giá, đối soát; assignment cũ MOVED, có liên kết |
| Hai nhân viên cùng đổi lịch một người | Một thành công, một conflict (stale version) |
| Lấy mẫu ngày 20, khám ngày 22 | Một Participant, hai assignment khác activity |
| Không đến rồi khám bù | Assignment cũ NO_SHOW; assignment mới ASSIGNED |
| Thêm dịch vụ khi Thỏa thuận ACTIVE | Dịch vụ mới dùng được trong đối soát; giá dịch vụ cũ đã ghi không đổi |
| Đổi giá dịch vụ đang áp dụng | Ngừng dòng cũ, thêm dòng mới; ParticipantService cũ giữ giá snapshot |
| Công ty dời cả buổi | Chuyển hàng loạt assignment ASSIGNED sang lịch mới; ATTENDED không bị đụng |
| Báo cáo Thỏa thuận nhiều ngày | Số người duy nhất ≠ tổng số người theo ngày; tiền theo giá snapshot |
| In phiếu theo lịch | Đúng người trong lịch, đúng ngày của lịch; job cũ vẫn tải được |
| Nhân viên không có quyền lịch | Backend từ chối cả HTTP và direct application call |

## 10. Rủi ro

| Rủi ro | Kiểm soát |
|---|---|
| Thỏa thuận thành "Batch đổi tên" | Ngày/địa điểm/attendance chỉ nằm ở Session/Assignment; review ở Đợt 0 |
| Viết lại trigger làm hỏng ràng buộc chuyên môn | Giữ nguyên ngữ nghĩa (bảng 2.1); test Testcontainers cho từng trigger |
| Refactor lớn (~200 file) kéo dài | Đi theo luồng L1→L8; mỗi luồng merge khi xanh |
| Phát sinh dữ liệu thật trước khi xong | Chốt D01 ngay; nếu thay đổi thì chuyển chiến lược B trước Đợt 1 |
| Phạm vi lan sang chuyên môn | Mục 1.3; mọi việc ngoài L1–L8 đưa vào plan sau |

## 11. Trạng thái khi tạo tài liệu

- Chỉ sửa plan này; runtime, schema, API, frontend chưa thay đổi.
- Hiện trạng mục 2 xác minh bằng đọc `V001__create_schema.sql`, domain aggregate, use case và grep phạm vi; số file là ước lượng.
- Chưa chạy Maven/frontend tests (thay đổi chỉ là tài liệu).
- D01–D10 và Q1–Q4 chưa được phòng khám chấp nhận.
