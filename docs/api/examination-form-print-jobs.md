# In hàng loạt phiếu khám (job nền)

Kế hoạch gốc: `20261012-examination-form-printing` (v2), T10. Quyết định kiến trúc: [ADR-0017](../adr/0017-document-rendering-and-template-lifecycle.md).
In **một** phiếu: xem [Examination form print](examination-form-print.md). Tài liệu này là hợp đồng HTTP của in nhiều người một lần.
Mã vạch trên phiếu: xem [Mã vạch](examination-form-print.md#mã-vạch).
Chưa có: upload/publish mẫu, đánh dấu (bookmark) hay đệm trang để in hai mặt.

## Luồng

1. `POST` tạo job → `202 Accepted` + `Location` + `jobId`.
2. Gọi `GET` job (poll, gợi ý 2–5 giây/lần) đến khi `status` là `SUCCEEDED` hoặc `FAILED`.
3. `SUCCEEDED`: tải từng phần PDF qua `GET .../parts/{partNo}`.
4. Sau 24 giờ kể từ lúc kết thúc, file bị xóa và job chuyển `EXPIRED`.

## Route

| Route | Permission | Ghi chú |
|---|---|---|
| `POST /api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants/examination-form-print-jobs` | `PARTICIPANT_EXAMINATION_FORM_BULK_PRINT` | Header `Idempotency-Key` (UUID) bắt buộc; body chọn người theo danh sách hoặc theo ngày khám (xem bên dưới) |
| `GET /api/v1/document-render-jobs/{jobId}` | `DOCUMENT_RENDER_JOB_READ` | Trạng thái và tiến độ |
| `GET /api/v1/document-render-jobs/{jobId}/parts/{partNo}` | `DOCUMENT_RENDER_JOB_DOWNLOAD` | `application/pdf`, `attachment; filename="phieu-kham-<mã đợt>-phan-<n>.pdf"`, `Cache-Control: no-store` |

V002 gán cả ba permission cho CLINIC_MANAGER, DATA_ENTRY_STAFF, RECEPTIONIST (một endpoint một permission, [ADR-0015](../adr/0015-endpoint-permission-rbac.md)).
Tài khoản phải là STAFF; mọi route trả `Cache-Control: no-store`.

## Tạo job (POST)

### Body

Hai cách chọn người in, phân biệt bằng `scope`:

| `scope` | Trường dùng | Ý nghĩa |
|---|---|---|
| `IDS` (mặc định) | `participantIds` | In đúng các người được liệt kê. 1–5000 phần tử không null trong body; sau khi kiểm tra, job nhận tối đa **1000** người (`nkc.document.jobs.max-records`), không trùng id. Thứ tự trong body là thứ tự in |
| `BATCH_DAY` | `batchDayId`, `includeAttended`, `sort` | In theo **ngày khám**: backend tự chọn người **ngay lúc tạo job** nên người được thêm, dời hay hủy sau đó không làm đổi job. Không được gửi kèm `participantIds`; `IDS` không được gửi kèm `batchDayId` |

```jsonc
// theo ngày khám
{ "scope": "BATCH_DAY", "batchDayId": "…", "includeAttended": false, "sort": "DEPARTMENT" }
// theo danh sách
{ "scope": "IDS", "participantIds": ["…", "…"] }
```

Quy tắc chọn người của `BATCH_DAY`:

- Chỉ người `ACTIVE` của đúng ngày đó. Người đã hủy và người `ABSENT` **không bao giờ** được in.
- Mặc định chỉ người `UNCONFIRMED` (chưa khám). `includeAttended: true` thêm người `ATTENDED` để in lại.
- `sort`: `DEPARTMENT` (mặc định: phòng ban → thứ tự dòng trong file import → mã người khám), `CODE` (mã người khám), `IMPORT_ORDER` (thứ tự dòng trong file import → mã).
- Ngày không thuộc đợt → 404. Ngày không còn ai để in → 409. Quá 1000 người → 400.
- Tên file tải về có thêm ngày khám: `phieu-kham-<mã đợt>-<yyyyMMdd>-phan-<n>.pdf`.
- Nếu retry cùng `Idempotency-Key` mà giữa hai lần gọi danh sách của ngày đã đổi (có người được thêm hoặc đã khám), backend trả 409 vì nội dung job khác; tạo lại với key mới.
- Lúc tạo chỉ kiểm tra quyền, Organization/Batch và trạng thái batch (READY/FINALIZED). Danh sách Participant được đọc lúc xử lý, nên người không còn hợp lệ được **bỏ qua có ghi lý do** thay vì làm hỏng cả job.
- Job chỉ lưu id và ngữ cảnh (organizationId, batchId, tên file), không lưu dữ liệu cá nhân.
- Cùng `Idempotency-Key` + cùng nội dung của cùng tài khoản trả lại đúng job cũ (vẫn 202); cùng key khác nội dung → 409.
- Audit `CREATE_EXAMINATION_FORM_PRINT_JOB` chỉ ghi khi job mới được tạo, không ghi khi phát lại.

| Mã | Khi nào |
|---|---|
| 202 | đã xếp hàng |
| 400 | thiếu/sai header hoặc body, danh sách rỗng, trùng id, quá 1000 người, `BATCH_DAY` thiếu `batchDayId` hoặc kèm `participantIds`, `IDS` kèm `batchDayId` |
| 403 | thiếu permission hoặc không phải STAFF |
| 404 | Organization/Batch không khớp hoặc batch đã xóa mềm; ngày khám không thuộc đợt (`BATCH_DAY`) |
| 409 | batch chưa READY/FINALIZED; ngày khám không còn ai để in (`BATCH_DAY`); `Idempotency-Key` đã dùng cho nội dung khác; tài khoản đang có 3 job chờ/chạy |
| 503 | chưa cấu hình thư mục lưu trữ `nkc.document.storage.local-root` |

## Xem job (GET)

Chỉ **người tạo** job xem được; người khác nhận 404 như job không tồn tại.

```json
{
  "code": 200,
  "data": {
    "id": "…", "documentType": "HEALTH_EXAM_PERIODIC_FORM_BLANK",
    "status": "SUCCEEDED", "totalCount": 480, "processedCount": 480,
    "errorCode": null,
    "skipped": [{"id": "…", "reason": "NOT_FOUND"}],
    "parts": [{"partNo": 1, "recordCount": 200, "sizeBytes": 18422113}],
    "createdAt": "…", "finishedAt": "…", "expiresAt": "…"
  }
}
```

- `status`: `QUEUED` → `RUNNING` → `SUCCEEDED` | `FAILED`; sau 24 giờ `EXPIRED`.
- `processedCount` tính cả người đã in lẫn người bị bỏ qua; lưu mỗi 25 người.
- `skipped[].reason`: `NOT_FOUND` (không thuộc batch), `CANCELLED` (Participant không còn ACTIVE), `TOO_LONG` (phiếu tràn số trang của mẫu), `RENDER_FAILED` (kết xuất người đó lỗi). Chỉ có id và lý do, không có tên.
- `parts`: mỗi phần tối đa 200 người (`part-size`), ghép bằng Gotenberg `/forms/pdfengines/merge` theo đúng thứ tự yêu cầu; phần chỉ có một người không qua bước ghép.
- Job `SUCCEEDED` luôn có ít nhất một phần. Nếu mọi người đều bị bỏ qua, job `FAILED` với `NOTHING_TO_PRINT`.

### Mã lỗi của job `FAILED` (`errorCode`)

| Mã | Ý nghĩa |
|---|---|
| `UNSUPPORTED_DOCUMENT_TYPE` | loại tài liệu không có nguồn dữ liệu |
| `NO_TEMPLATE` | chưa có phiên bản mẫu hiệu lực |
| `TEMPLATE_UNAVAILABLE` | file mẫu không đọc/điền được |
| `RENDERER_UNAVAILABLE` | Gotenberg không phản hồi (đã thử lại tối đa 3 lần) |
| `STORAGE_FAILED` | không ghi được file đầu ra |
| `SOURCE_NOT_FOUND` / `SOURCE_NOT_PRINTABLE` | batch bị xóa hoặc không còn READY/FINALIZED sau khi job được tạo |
| `NOTHING_TO_PRINT` | không còn ai in được |
| `ATTEMPTS_EXHAUSTED` | worker chết/treo quá 3 lần nhận job |
| `INTERNAL_ERROR` | lỗi không dự kiến (xem log) |

## Tải một phần (GET parts)

- 404: job không phải của người gọi, job/phần không tồn tại, hoặc job đã `EXPIRED` (không dùng 410).
- 409: job chưa `SUCCEEDED`.
- 500: bản ghi còn nhưng file trên đĩa đã mất (lỗi hệ thống).
- Audit `DOWNLOAD_RENDER_JOB_PART` (đối tượng `DOCUMENT_RENDER_JOB`).

## Vận hành

- Một worker đơn luồng mỗi instance, poll mỗi 5 giây; nhiều instance dùng chung hàng đợi qua lease trong DB (`FOR UPDATE SKIP LOCKED`, `lock_token`, `locked_until`). Mỗi lần ghi của worker đều kiểm tra lock; mất lease thì dừng, không ghi đè.
- Mẫu được **ghim** một phiên bản cho cả job: đổi mẫu giữa chừng không làm lẫn các phần.
- Kết xuất tuần tự (vài giây/người cho A3 hai trang), nên 1000 người có thể mất hàng chục phút. Tăng song song là việc sau.
- File lưu tại `{local-root}/render-jobs/YYYY/MM/{jobId}-{part}.pdf` (tối đa 256 MiB/file). Dọn dẹp mỗi 10 phút: xóa file trước rồi mới đánh dấu `EXPIRED`.
- Mỗi tài khoản tối đa 3 job chờ/chạy.

## Cấu hình

| Thuộc tính | Mặc định |
|---|---|
| `nkc.document.storage.local-root` (`NKC_DOCUMENT_STORAGE_ROOT`) | rỗng → tạo job trả 503 |
| `nkc.document.gotenberg.merge-timeout` | `90s` (`--api-timeout` của Gotenberg phải ≥ giá trị này) |
| `nkc.document.jobs.enabled` | `true` |
| `nkc.document.jobs.poll-interval` / `cleanup-interval` | `5s` / `10m` |
| `nkc.document.jobs.max-records` / `part-size` / `chunk-size` | `1000` / `200` / `25` |
| `nkc.document.jobs.lease` / `max-attempts` / `retention` | `5m` / `3` / `24h` |
| `nkc.document.jobs.max-active-per-requester` | `3` |

Dev: `docker compose up gotenberg` và đặt `NKC_DOCUMENT_STORAGE_ROOT` (compose đã mount volume lưu trữ).
