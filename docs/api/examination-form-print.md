# In phiếu khám của một người khám (PDF)

Kế hoạch gốc: `20261012-examination-form-printing` (v2). Quyết định kiến trúc: [ADR-0017](../adr/0017-document-rendering-and-template-lifecycle.md). Tài liệu này là hợp đồng HTTP hiện hành.
Phạm vi đã triển khai: in **một** phiếu khám của một Participant, kết xuất theo yêu cầu, không lưu PDF.
In nhiều người một lần (job nền): xem [In hàng loạt](examination-form-print-jobs.md).
Không triển khai chức năng sửa/upload/publish mẫu trên ứng dụng: IT quản lý thay đổi,
bên nghiệp vụ/chuyên môn duyệt nội dung theo [ADR-0018](../adr/0018-it-managed-document-templates.md).
Phiếu có mã vạch Code128 của mã người khám (xem [Mã vạch](#mã-vạch)).
Đợt khám phải đã **bắt đầu** (`POST …/health-examination-batches/{batchId}/start`, DRAFT → READY) thì mới in được; xem [Batch](organizations-and-batches.md#health-examination-batch).

## Route

`GET /api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants/{participantId}/examination-form`

| Mục | Giá trị |
|---|---|
| Permission | `PARTICIPANT_EXAMINATION_FORM_PRINT` (V002 gán endpoint này; CLINIC_MANAGER, DATA_ENTRY_STAFF, RECEPTIONIST) |
| 200 | `application/pdf`, `Content-Disposition: inline; filename="phieu-kham-<participantCode>.pdf"`, `Cache-Control: no-store` |
| 403 | thiếu permission hoặc không phải tài khoản STAFF |
| 404 | Organization/Batch/Participant không khớp, batch đã xóa mềm, hoặc chưa có phiên bản mẫu hiệu lực |
| 409 | batch chưa READY/FINALIZED, Participant không còn ACTIVE, hoặc phiếu tràn quá số trang của mẫu (thông báo nêu số trang) |
| 500 | file mẫu đã lưu không đọc/điền được (lỗi hệ thống, không phải lỗi người gọi) |
| 503 | Gotenberg không phản hồi / trả lỗi (`DependencyUnavailableException`) |

Permission được gắn vào đúng một endpoint theo [ADR-0015](../adr/0015-endpoint-permission-rbac.md).
In hàng loạt (UC-086) dùng permission riêng `PARTICIPANT_EXAMINATION_FORM_BULK_PRINT`, xem [In hàng loạt](examination-form-print-jobs.md).

## Dữ liệu và quy ước hiển thị

Hợp đồng placeholder `HEALTH_EXAM_PERIODIC_FORM_BLANK` v1 do `healthexamination` sở hữu
(`PeriodicFormBlankContract`), gồm 23 placeholder (22 chữ và 1 ảnh mã vạch `participant.barcode`). Mẫu bắt buộc chứa
`participant.code`, `participant.dateOfBirth`, `participant.identificationNumber`.
"Mẫu phải chứa placeholder" và "dữ liệu bắt buộc phải có" là hai việc khác nhau.

- Giá trị thiếu của `participant.*` in thành 25 ký tự `…` (đường chấm) để điền tay. Đơn vị, đợt khám và ngày in thiếu thì để trống.
- Tuổi tính tại ngày khám; giới tính in `Nam` / `Nữ` / `Khác` / đường chấm.
- Nơi làm việc mặc định là tên Organization khi Participant không có.
- Giá trị được làm sạch (ký tự điều khiển, surrogate lẻ thành khoảng trắng).
- Mã vạch: xem [Mã vạch](#mã-vạch).
- Tràn trang là lỗi chặn (409), không tự thu nhỏ chữ: PDF có nhiều trang hơn `pages_per_record` của phiên bản mẫu bị từ chối. Số trang đếm từ các page object của PDF LibreOffice không nén; PDF không đếm được thì bỏ qua kiểm tra và ghi log cảnh báo. Mẫu A3 gập đôi không đệm thêm trang trắng.
- Mỗi giá trị được cắt tối đa 1.000 ký tự (chỉ để chặn bộ nhớ, không phải giới hạn nghiệp vụ).

## Mã vạch

- `participant.barcode` là placeholder **ảnh**, tùy chọn nên không tăng `contractVersion`. Người soạn mẫu chèn một ảnh giữ chỗ và đặt `{{@participant.barcode}}` vào **văn bản thay thế (alt text)** của ảnh. Khi in, ảnh được thay bằng mã **Code128** chứa đúng `participant.code`, giữ nguyên khung (kích thước, vị trí, kiểu bố trí) của ảnh trong mẫu. Dòng chữ dưới mã do mẫu tự in bằng `{{participant.code}}`.
- Mã chỉ chứa mã người khám, không chứa CCCD hay họ tên. Ký tự được phép: ASCII in được (dấu cách đến `~`).
- Ảnh là PNG 1 bit, vẽ ở 1200 dpi đúng bằng kích thước khung (starter: 48,9 × 9 mm = 2310 × 425 px). Độ rộng một vạch là số pixel **nguyên** lớn nhất mà mã và hai vùng yên tĩnh (mỗi bên 10 vạch) còn vừa khung; phần dư chia vào vùng yên tĩnh. Mã 17 ký tự (`KSK-2026-001-0001`) ra vạch 0,212 mm; mã 18 ký tự ra 0,190 mm.
- Vạch mảnh hơn 0,17 mm hoặc mã có ký tự ngoài ASCII in được thì phiếu bị từ chối (409, như tràn trang); in hàng loạt bỏ qua người đó. Khung quá hẹp là lỗi của mẫu, không phải của dữ liệu.
- Người khám không có mã (`participant_code` null) thì ảnh bị **gỡ khỏi phiếu**, không in đường chấm và không bao giờ in ảnh giữ chỗ.
- Gotenberg được gọi với `losslessImageCompression=true` và `reduceImageResolution=false`: mặc định của LibreOffice nén ảnh JPEG và hạ xuống 300 dpi, làm vạch bị nhòe và máy quét đọc trượt.
- Mã được dựng bằng bộ mã hóa Code128 viết trong dự án (không thêm thư viện): code set B, tự chuyển sang C cho dãy chữ số khi ngắn hơn. Đã đối chiếu bằng bộ giải mã độc lập (zxing-cpp) trên 400 chuỗi ngẫu nhiên và trên PDF do LibreOffice kết xuất. **Chưa thử** bằng máy in và máy quét thật của phòng khám.
- Starter đã có sẵn khung mã vạch dưới dòng "PHÒNG KHÁM ĐA KHOA". Starter chỉ được nạp khi loại tài liệu chưa có phiên bản nào, nên cơ sở dữ liệu đã có v1 cũ (không mã vạch) vẫn in không có mã cho đến khi IT phát hành phiên bản mới của mẫu hoặc làm sạch dữ liệu mẫu.

## Mẫu và phiên bản

- Phiên bản hiệu lực là bản `PUBLISHED` có `active_from <= now` và chưa `retired_at`, của template mặc định đang hoạt động theo `template_type`.
- Khi khởi động, `StarterTemplateSeeder` nạp `document-templates/health-exam-periodic-form-blank/health-examination-v1.docx` thành v1 PUBLISHED
  **chỉ khi** loại tài liệu chưa có phiên bản nào. Tắt bằng `nkc.document.seed-starter-templates=false`.
- Khổ giấy và hướng được đọc từ `w:pgSz` của docx (A3/A4/A5/A6/CUSTOM, sai số 1,5 mm); starter là A3 ngang, 2 trang cho mỗi người.
- Nội dung và bố cục một phiên bản không đổi sau khi tạo; chỉ vòng đời DRAFT → PUBLISHED | DISCARDED và việc hết hiệu lực được phép (V001).
- Thay mẫu cần phiên bản mới, tham chiếu file riêng và quy trình phát hành/migration do IT review;
  sửa file starter rồi khởi động lại không cập nhật mẫu đã có. Giữ file và tham chiếu lịch sử.
- Phiếu trắng vẫn render theo yêu cầu bằng phiên bản hiệu lực; chưa lưu PDF để bảo đảm
  in lại giống từng byte. Job in hàng loạt cố định một phiên bản mẫu cho cả job.

## Cấu hình

| Thuộc tính | Môi trường | Mặc định |
|---|---|---|
| `nkc.document.gotenberg.url` | `NKC_DOCUMENT_GOTENBERG_URL` | `http://localhost:3000` (`local`: `3001`) |
| `nkc.document.gotenberg.timeout` | | `15s` |
| `nkc.document.storage.local-root` | `NKC_DOCUMENT_STORAGE_ROOT` | rỗng (chỉ đọc CLASSPATH) |
| `nkc.document.seed-starter-templates` | | `true` |

`docker compose up gotenberg` chạy Gotenberg 8 cho dev (cổng 127.0.0.1:3001). Môi trường production phải chạy Gotenberg
không có đường ra Internet (kế hoạch T13); compose hiện tại không cô lập mạng.

## Audit

`RENDER_EXAMINATION_FORM` ghi id Organization/Batch/Participant và phiên bản mẫu; không ghi dữ liệu cá nhân hay nội dung PDF.
