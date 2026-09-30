# Kế hoạch triển khai import danh sách người khám theo đợt

**Ngày:** 2026-09-29  
**Module backend:** `healthexamination`  
**Module frontend:** `src/modules/health-examinations`  
**Trạng thái:** Đã triển khai theo ADR-0009; còn gate kiểm chứng PostgreSQL và tích hợp auth.

## 1. Mục tiêu và quyết định đã chốt

Tại một Organization và Health Examination Batch, Quản lý bệnh viện tải mẫu, upload Excel, kiểm tra mapping cột, xem preview lỗi và xác nhận. Upload chỉ tạo staging; Confirm mới ghi danh sách người khám trong **một transaction**.

1. **Import toàn bộ hoặc không import:** chỉ một dòng có lỗi chặn thì Confirm ghi **0 dòng** dữ liệu nghiệp vụ. Không có partial success cho roster. Thiếu trường tùy chọn là cảnh báo, không phải lỗi chặn.
2. File Excel vừa cung cấp là **mẫu import danh sách riêng**, không phải layout hay hợp đồng in Mẫu số 03. File CSV 13 cột được thấy trước đó là file khác và không dùng cho luồng này.
3. **Bỏ cột Mã NV khỏi file.** Khóa tìm lại người trong cùng Organization là CCCD chính xác. Cột `participant_code` hiện bắt buộc trong DB sẽ là mã kỹ thuật ổn định, không chứa CCCD: người mới dùng chuỗi UUIDv7 đã tạo cho `id`, người cũ giữ nguyên code. Không hiển thị nó như “Mã NV”.
4. Nhận file `.xls` như mẫu được gửi và `.xlsx` do hệ thống xuất ra. `org.apache.fesod:fesod-sheet` đã có trong `pom.xml`; dùng adapter hiện có hoặc bổ sung adapter mỏng, không thêm thư viện Excel mới.

**Đồng bộ nguồn chuẩn trước code:** `requirement-v2.5_FINAL.docx`, `use-case-v2.7_FINAL.docx`, `table-design-v2.11_FINAL.docx` hiện còn quy định xác nhận từng dòng hợp lệ và yêu cầu employeeCode. Cần phát hành bản cập nhật/điều chỉnh ghi rõ quyết định 1–3. Các quy tắc khác vẫn giữ: chỉ `CLINIC_MANAGER` import, CCCD bắt buộc, đủ 18 tuổi theo ngày khám, import không tạo Patient/Encounter, chuẩn bị hồ sơ trước check-in, bác sĩ mới chọn hạng mục. ADR-0007/0008 và Flyway V001 quyết định cách lưu snapshot vật lý.

**Tích hợp xác thực còn thiếu trong repository:** frontend `authApi` hiện trả `null` cho người dùng và login trả 501; backend hiện chưa có authentication provider / role mapper trong checkout. UI chỉ hiện CTA khi `currentUser.role === CLINIC_MANAGER`, còn backend dùng `@PreAuthorize` và yêu cầu `Authentication.getName()` là UUID người dùng. Luồng import vì vậy fail-closed cho tới khi auth provider cấp đúng authority và UUID principal, đồng thời frontend nhận được user session.

## 2. Cấu trúc thực tế của file mẫu

File `DS KSK Cty FDI năm 2026.xls` là Excel BIFF `.xls`, gồm **1 sheet, 16 cột**. Hàng 1 là tiêu đề gộp `A1:P1`; hàng 2 là header; dữ liệu bắt đầu ở hàng Excel 3. Có 19 hàng được đánh số trong file; hàng cuối thiếu cả ngày sinh lẫn CCCD. Theo quy tắc strict, **file mẫu nguyên trạng phải báo lỗi và không thể Confirm**. Không commit dữ liệu cá nhân thật từ file này vào test fixture hoặc tài liệu.

Các ô ngày trong mẫu có cả dạng số ngày Excel và dạng text; CCCD đang ở dạng text. Fesod đọc hàng thành `Map<Integer, String>` và format ô ngày thành text. Áp dụng đúng `IdentificationNumber` hiện tại (chỉ chữ số, tối đa 20 ký tự); sample dùng CCCD text 12 chữ số. Parser hiện không phân biệt ô CCCD vốn là text hay numeric; vì hợp đồng hiện tại không quy định độ dài tối thiểu, số 0 đầu bị mất có thể vẫn qua nếu phần còn lại là chữ số. Template đặt CCCD/điện thoại dạng Text; khi dùng file ngoài template, người nhập nên lưu hai cột này dạng Text. Không tự ép độ dài 12 số khi chưa chốt hợp đồng. `rowNumber` trong lỗi là số hàng Excel thực, không lấy STT làm định danh.

## 3. Mapping 16 cột

Backend đọc header và gợi ý mapping theo tên đã chuẩn hóa (hoa/thường, khoảng trắng, xuống dòng). Quản lý kiểm tra/chỉnh mapping trước khi validate. Lưu mapping đã chọn trong `health_examination_import_jobs.column_mapping_json`. Không dựa mù vào vị trí cột; từ chối thiếu cột bắt buộc, mapping trùng đích hoặc cột định danh không rõ. File `.xlsx` hệ thống phát ra dùng đúng 16 tiêu đề sau, cùng thứ tự với mẫu.

| Cột trong mẫu | Trường chuẩn | Kiểm tra | Đích sau Confirm |
|---|---|---|---|
| STT | số thứ tự hiển thị | Không là khóa nghiệp vụ | Không lưu làm định danh |
| HỌ VÀ TÊN | `fullName` | Bắt buộc | Organization participant + batch snapshot |
| GIỚI TÍNH | `sex` | Bắt buộc; map từ tập giá trị đã chốt | Organization participant + batch snapshot |
| NGÀY/THÁNG/NĂM SINH | `dateOfBirth` | Bắt buộc; ngày hợp lệ | Organization participant + batch snapshot |
| SỐ ĐIỆN THOẠI | `phone` | Tùy chọn; giữ dạng text | `phone_snapshot` |
| SỐ CCCD/HỘ CHIẾU/MÃ ĐỊNH DANH | `identificationNumber` | Bắt buộc **CCCD** theo baseline; hộ chiếu/loại khác bị lỗi chặn | Organization participant + batch snapshot |
| CẤP NGÀY | `identificationNumberIssueDate` | Tùy chọn; ngày hợp lệ nếu có | Issue-date snapshot |
| NƠI CẤP | `identificationNumberIssuePlace` | Tùy chọn | Issue-place snapshot |
| DÂN TỘC | `ethnicity` | Tùy chọn | `ethnicity_snapshot` |
| ĐỐI TƯỢNG | `subjectType` | Tùy chọn | `subject_type_snapshot` |
| NHÓM MÁU (NẾU CÓ) | `bloodGroup` | Tùy chọn; giá trị hợp lệ nếu có | `blood_group_snapshot` |
| NGHỀ NGHIỆP | `occupation` | Tùy chọn | `HealthExaminationParticipant.occupation` + `occupation_snapshot`; không tự ghi `administrative_occupation_snapshot` của Mẫu số 03 |
| NƠI LÀM VIỆC | `workplaceOrSchool` | Tùy chọn | `workplace_or_school_snapshot` |
| CHỖ Ở HIỆN TẠI | `addressDetail` | Tùy chọn; giữ nguyên text, không suy đoán tỉnh/phường | `address_detail_snapshot` |
| NGUỒN CHI TRẢ | `payerSource` | Tùy chọn | `payer_source_snapshot` |
| GHI CHÚ | `rosterNote` | Tùy chọn; dữ liệu nhạy cảm | Cột mới `roster_note_snapshot` của batch participant |

`roster_note_snapshot` là cột nghiệp vụ mới duy nhất: thêm Flyway migration mới kiểu `varchar(1000)`, đồng thời cập nhật aggregate, record, converter và test. Chỉ trả ghi chú trong preview/chi tiết roster cho người có quyền; không tự thêm vào API danh sách chung. Không đánh đồng ghi chú với `health_examination_reason_snapshot`, không tự đưa ghi chú vào HealthExaminationRecord hoặc Mẫu số 03.

Import không tạo Patient, Encounter, ServiceRequest, hạng mục theo từng người, thanh toán, kết quả, SHS hoặc tài liệu. Luồng chuẩn bị hồ sơ được ủy quyền tiếp tục resolve/tạo Patient bằng CCCD chính xác, tạo/tái sử dụng Encounter `PREPARED` và HealthExaminationRecord/SHS trước check-in; check-in dùng lại các bản ghi đó.

## 4. Validate và preview

### 4.1. File, sheet, hàng dữ liệu

- Giới hạn ban đầu qua config: 10 MiB/file và 10.000 hàng dữ liệu. Chặn khi đọc, không đợi parse xong; giới hạn sheet/cell và kích thước giải nén để tránh cạn tài nguyên.
- Xác minh nội dung là `.xls` hoặc `.xlsx` thật; không chỉ tin extension/MIME do browser gửi. Từ chối file hỏng, có mật khẩu, macro hoặc cấu trúc ngoài hợp đồng bằng mã lỗi an toàn.
- Nhận hàng 1 là title, hàng 2 là header, hàng 3 trở đi là data. Hàng có STT số và dữ liệu nghiệp vụ vẫn là data dù thiếu DOB/CCCD; **không bỏ qua hàng cuối đang lỗi trong file mẫu**.
- Chỉ bỏ qua hàng trống theo quy tắc xác định. Gặp ô không rỗng sau vùng dữ liệu thì phân loại rõ là data hoặc báo lỗi file; không âm thầm bỏ thông tin.
- Sinh file mẫu `.xlsx` trống, 16 header, CCCD/điện thoại định dạng Text và hướng dẫn ngày tháng; không nhúng dữ liệu cá nhân của file mẫu.

### 4.2. Quy tắc từng hàng

- Trim/chuẩn hóa khoảng trắng không làm đổi nghĩa tên, CCCD, điện thoại, địa chỉ. CCCD dùng `IdentificationNumber` hiện có; không fuzzy match tên, không tự tạo CCCD. Fesod trả cell thành text, nên áp dụng đúng quy tắc `IdentificationNumber`; giá trị numeric bị mất số 0 đầu phải bị chặn nếu thành giá trị không hợp lệ.
- Bắt buộc: `fullName`, `sex`, `dateOfBirth`, `identificationNumber`. Kiểm tra ngày, enum, độ dài theo schema, CCCD hợp lệ và quyền/batch. Thiếu optional tạo warning, không tạo giá trị giả.
- Hai hàng trùng CCCD trong cùng file đều lỗi. Ngày không hợp lệ, identity không phải CCCD, hoặc xung đột định danh trong Organization là lỗi chặn. Một hàng có thể có nhiều lỗi field/code/message.
- Kiểm tra tuổi tại **ngày khám dự kiến** nếu ngày đó đã xác định; không dùng ngày upload. Thiếu ngày dự kiến không được tự suy đoán và vẫn chặn bước chuẩn bị/in theo hợp đồng. Bước chuẩn bị và check-in kiểm tra lại tuổi theo thời điểm tương ứng.
- Preview trả `totalRows`, `validRows`, `warningRows`, `errorRows`, lỗi/cảnh báo từng hàng, cùng hành động dự kiến `CREATE`/`UPDATE`/`UNCHANGED`. Preview là ảnh chụp tại thời điểm validate, Confirm phải kiểm tra lại.
- Backend và FE đều khóa Confirm nếu `errorRows > 0`, `totalRows == 0`, hoặc mapping chưa validate. Warning đơn thuần không khóa Confirm.

### 4.3. Xử lý người đã tồn tại

Dùng unique `(organization_id, identification_number)` đã có. Tải CCCD của toàn file trong truy vấn theo lô, rồi tải membership trong batch; tránh N+1. Patient có cùng CCCD **không** là lỗi import và chưa được tạo/liên kết ở bước này.

| Tình huống | Preview | Confirm |
|---|---|---|
| CCCD chưa có trong Organization | `CREATE` | Tạo Organization participant với UUIDv7/mã kỹ thuật nội bộ, rồi tạo batch participant + snapshot. |
| CCCD đã có trong Organization nhưng chưa thuộc batch | `CREATE` membership | Dùng lại participant và `patient_id` hiện có; cập nhật các thuộc tính roster được phép, tạo membership + snapshot. |
| CCCD đã có trong batch, dữ liệu giống hệt | `UNCHANGED` | Không update business record; vẫn ghi row-resolution và tính là một hàng Confirm thành công. |
| CCCD đã có trong batch, trường roster thay đổi hợp lệ | `UPDATE` | Update cùng participant/membership ID; giữ `patient_id`, status, service assignment và snapshot hồ sơ y tế đã chuẩn bị/phát hành. |
| CCCD trùng nhiều hàng file, hoặc mapping tới identity/Patient link mâu thuẫn | Lỗi chặn | Không ghi hàng nào; cần luồng sửa định danh được ủy quyền, không tự relink Patient. |

Nếu một HealthExaminationRecord đã chuẩn bị/phát hành khác roster mới, preview nêu cảnh báo rõ. Confirm có thể cập nhật **roster snapshot**, nhưng không sửa snapshot HealthExaminationRecord hay reprint ngầm; sửa/in lại hồ sơ đi qua workflow riêng. Repository batch participant hiện chỉ có `insert` trong `save`; phải thêm update có điều kiện, không gọi `save` để cố insert lại người cũ.

## 5. Tái sử dụng kiến trúc và transaction

Tái sử dụng các bảng `health_examination_import_jobs`, `health_examination_import_rows`, `file_attachments` và các aggregate/repository `HealthExaminationImportJob`, `HealthExaminationImportRow`, `HealthExaminationParticipant`, `HealthExaminationBatchParticipant`. Giữ enum hiện tại `UPLOADED`, `VALIDATED`, `CONFIRMED`, `PARTIAL`, `FAILED`, `CANCELED`; `PARTIAL` vẫn có thể phục vụ import loại khác, **roster không bao giờ chuyển PARTIAL**. Không tạo bảng employee-import/status `READY`/`IMPORTING` song song. UUIDv7 do Java tạo, không thêm DB default `uuidv7()`.

File nguồn được mã hóa ngoài PostgreSQL; metadata vào `file_attachments`, job tham chiếu `source_file_attachment_id`. Nếu DB ghi thất bại, xóa file vừa lưu hoặc để orphan có thể nhận diện và cleanup an toàn. Không log CCCD, payload, đường dẫn file, khóa mã hóa. **Không tự đặt retention 7 ngày hoặc xóa import rows:** cần chốt chính sách lưu trữ/audit trước, và mọi cleanup file nguồn phải giữ được job/row-resolution/audit.

**Confirm tại application layer với `@Transactional`:**

1. Xác thực `CLINIC_MANAGER`, Organization–Batch–Job ownership và `ImportType.PARTICIPANT_LIST`.
2. Khóa job bằng `findByIdForUpdate` đã có. Nếu `CONFIRMED`, trả lại kết quả đã lưu, không chạy lần hai. Từ chối job `CANCELED`, `FAILED` hoặc chưa validate.
3. Khóa/đọc batch và participant đã match theo thứ tự cố định. Policy ban đầu: cho chỉnh roster ở `DRAFT`, `READY`, `IN_PROGRESS`; từ chối `RESULT_PROCESSING`, `FINALIZED`, `CLOSED`, `CANCELED`. Ghi policy này vào hợp đồng nghiệp vụ trước code.
4. Yêu cầu **tất cả** staging rows hợp lệ, `errorRows == 0`, tổng dòng nhất quán. Tra lại CCCD và membership hiện tại; tính lại action/conflict. Preview cũ hoặc xung đột trả `409 IMPORT_PREVIEW_STALE` và rollback toàn bộ; FE cho revalidate/reupload.
5. Tạo/cập nhật/giữ nguyên participant và batch membership trong cùng transaction; bảo toàn Patient link, service assignment và snapshot hồ sơ. MyBatis ghi theo lô có giới hạn; unique constraint DB là lớp bảo vệ cuối. Vi phạm unique/lost update thành conflict, không thành partial success.
6. Lưu resolved IDs và action cuối vào `normalized_payload_json` của import row; lưu warning codes trong payload này và tính `warning_rows` từ các hàng có warning. Tính counts từ action đã lưu để retry trả cùng kết quả. Lưu `confirmed_by_user_id`, `confirmed_at`, audit record và job `CONFIRMED` cùng transaction. Response có `importedRows = totalRows`, `createdRows`, `updatedRows`, `unchangedRows`.

Không giữ DB transaction trong lúc giải mã/đọc/parse Excel. Parse và preview chỉ ghi staging. Đo trường hợp 10.000 hàng rồi chốt kích thước lô MyBatis theo kết quả, không cố định ước lượng trong code.

## 6. API công khai

Base URL: `/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}`. Dùng `ApiResponse` và error contract hiện có; preview phân trang có giới hạn. **Mọi route** kiểm tra quyền Manager và ownership, kể cả đọc/download/cancel.

| Chức năng | Route | Kết quả |
|---|---|---|
| Tải mẫu `.xlsx` 16 cột | `GET /employees/import-template` | File trống, tên an toàn, CCCD/phone Text |
| Upload `.xls`/`.xlsx` | `POST /employee-imports` (`multipart/form-data`) | `importId`, `UPLOADED`, header và mapping gợi ý |
| Xem/chỉnh mapping rồi validate | `PUT /employee-imports/{importId}/mapping` | `VALIDATED`, counts và errors/warnings; lưu `column_mapping_json` |
| Xem tổng quan | `GET /employee-imports/{importId}` | status, mapping, counts, quyền Confirm và kết quả nếu đã xong |
| Preview theo trang | `GET /employee-imports/{importId}/rows?page=1&size=50&status=INVALID` | Excel row number, trường cần kiểm tra, lỗi/cảnh báo/action; size tối đa |
| Confirm | `POST /employee-imports/{importId}/confirm` | Idempotent `CONFIRMED` hoặc 409 khi preview cũ; 0 write nếu có lỗi chặn |
| Hủy job chưa Confirm | `DELETE /employee-imports/{importId}` | `CANCELED`; không xóa audit/business data |

Không tin ID/mapping do FE gửi là bằng chứng quyền. Lỗi và log không lộ CCCD đầy đủ. Test `FRONT_DESK` và `SYSTEM_ADMIN` không có business permission bị từ chối ở download/upload/mapping/preview/confirm/cancel. Import RESULTS giữ quyền và nghiệp vụ riêng.

## 7. Frontend

Sửa trực tiếp module `src/modules/health-examinations`; không tạo `src/features` song song. Checkout hiện có `ParticipantsTab` nhưng không có dialog import đang chạy, nên thêm `components/participants-tab/participant-import-dialog.tsx`, API wrapper `api/participant-imports.ts`, hooks và nút toolbar/empty-state. Dùng `src/shared/api/api-client.ts` đã hỗ trợ `FormData` và `getBlob`, query keys dưới `healthExaminationKeys.participantsRoot(...)`, cùng TanStack Query hiện có.

Dialog: chọn file → upload → kiểm tra mapping → preview validation → confirm → thành công. Hiển thị lỗi theo hàng/trường, warning riêng, counts `CREATE`/`UPDATE`/`UNCHANGED`; chỉ Manager thấy CTA. Confirm disabled khi có lỗi chặn; sau thành công invalidate danh sách participant. Khi `IMPORT_PREVIEW_STALE`, giữ dialog mở và hướng dẫn validate/upload lại. FE chỉ hỗ trợ UX, backend quyết định hợp lệ/quyền.

## 8. Thứ tự triển khai và kiểm chứng

### Task 0: Đồng bộ quyết định roster

**Tài liệu trong repo:** ADR-0009 và plan này. Các FINAL DOCX nguồn ở Downloads nằm ngoài repo; ADR-0009 ghi quyết định đã được requester chốt và theo dõi việc đồng bộ DOCX trước release.

- [x] Ghi strict all-or-nothing cho roster, bỏ employeeCode trong file, 16 cột riêng và CCCD-only ở baseline hiện tại trong ADR-0009.
- [x] Ghi `rosterNote` là batch roster snapshot riêng, không vào Mẫu số 03/hồ sơ y tế trong ADR-0009.
- [x] Ghi status batch được sửa roster và quy tắc khi import lại người đã có hồ sơ trong ADR-0009.
- [x] Soát ADR-0009 và plan: không còn partial roster confirm hoặc employeeCode trong upload. Cần đồng bộ các bản FINAL DOCX ngoài repo trước release.

### Task 1: Template, parser, staging, preview

**Backend:** mở rộng import job/row, mapper/repository đang có; tạo adapter Fesod, template writer, use case và DTO trong `healthexamination` khi cần. Không đổi `pom.xml` nếu dependency hiện tại đủ dùng.

- [x] Tạo template `.xlsx` trống đúng 16 cột, CCCD/phone Text.
- [x] Parse `.xls` giống cấu trúc mẫu và `.xlsx` tương đương; header ở hàng 2, lỗi mang số hàng Excel thật.
- [x] Gợi ý/validate/lưu mapping; không cố định vị trí cột sau khi mapping được chốt.
- [x] Normalize/validate mọi hàng, kể cả hàng cuối thiếu DOB/CCCD của mẫu; lưu staging rows theo lô cùng error codes, warnings, normalized payload và action dự kiến, chưa ghi business tables.
- [x] Viết fixture `.xls`/`.xlsx` tổng hợp không chứa dữ liệu thật: header, ngày dạng số/text, CCCD text, trùng CCCD, thiếu DOB/CCCD, identity sai loại, mapping sai.
- [x] Chạy test parser/domain/application tập trung trước giai đoạn tiếp.

### Task 2: Re-import và Confirm nghiêm ngặt

**Backend:** mở rộng participant và batch-participant domain/repository/mapper, import job repository/mapper. **DB:** thêm một Flyway migration mới cho `roster_note_snapshot`; không sửa V001.

- [x] Tra cứu CCCD/membership theo lô; phân loại `CREATE`/`UPDATE`/`UNCHANGED` không có N+1.
- [x] Tách bulk insert membership mới khỏi bulk update membership cũ; update giữ ID, status, assignment, Patient link và snapshot hồ sơ đã có.
- [x] Chặn Confirm roster nếu **bất kỳ** row invalid; không đổi semantics của RESULTS import.
- [x] Khóa, revalidate, commit và audit trong một transaction; retry trả cùng kết quả; preview cũ có thể validate lại cùng staging file.
- [x] Unit tests cho người mới, update có Patient link/hồ sơ đã chuẩn bị, unchanged, stale preview, re-import preview và lỗi một dòng → không gọi business writes.
- [ ] PostgreSQL 18 integration tests cho confirm đồng thời, unique conflict và rollback cuối transaction; Testcontainers cần Docker.
- [x] Spring Modulith / ArchUnit boundary test.

### Task 3: API an toàn và frontend thật

**Backend:** controller trong `healthexamination/api`, chỉ gọi application use cases, dùng centralized errors. **Frontend:** thêm `api/participant-imports.ts` và hooks; nối dialog, toolbar, empty state trong `ParticipantsTab` hiện có.

- [x] Thực hiện 7 route mục 6, preview pagination có giới hạn, Javadoc tiếng Anh cho từng endpoint public.
- [x] Gắn `CLINIC_MANAGER` và ownership checks vào usecases; test Front Desk bị từ chối, retry Confirm và error code `IMPORT_PREVIEW_STALE`.
- [x] Nối upload/mapping/preview/confirm thật; invalidate đúng participant-list query. Checkout ban đầu không có dialog import đang chạy, nên nối trực tiếp vào `ParticipantsTab`.
- [x] FE tests cho `.xls`, mapping, lỗi khóa Confirm, stale preview/revalidate, re-import counts và role visibility.
- [x] Frontend tests, typecheck và lint chạy bằng binary đã cài trong `node_modules` (Corepack `pnpm` shim cần tải `@pnpm/exe`, registry không truy cập được).
- [ ] Frontend production build; Next.js không tải được Google Fonts `Geist Mono`/`Inter` do session không có network.

### Task 4: Kiểm chứng cuối

- [ ] Chạy `.\mvnw.cmd test` và `.\mvnw.cmd verify`; Testcontainers cần Docker, nếu không có phải ghi rõ chưa chạy.
- Kết quả cả `test` và `verify`: 177 tests, 2 failures ngoài phạm vi import ở `ListBatchParticipantUseCaseTest.rejectsInvalidPaginationAndSortValues` và `ListOrganizationsUseCaseTest.searchesFiltersAndPaginatesOrganizations`; 6 Testcontainers tests bị skip vì Docker pipe không khả dụng.
- [x] Test cấu trúc mẫu bằng fixture giả; không commit bản ghi cá nhân thật từ workbook.
- [ ] Chạy toàn chuỗi migration V001 → V002 trên PostgreSQL 18 sạch; Docker/Testcontainers không khả dụng trong session.
- [x] Confirm chỉ gọi participant, batch snapshot, import job và audit writer; không gọi Patient/Encounter/service assignment/payment/result/document writer.
- [x] Audit confirm/cancel ghi actor, thời điểm, trạng thái/counts mà không chứa roster/CCCD; không đặt retention hoặc cleanup mới; log import chỉ ghi importId/batchId/file size.
- [x] Giữ các thay đổi workspace ngoài phạm vi import.

## 9. Definition of Done

- Manager tải được template 16 cột, upload `.xls`/`.xlsx`, kiểm tra mapping, preview đúng lỗi hàng/trường.
- Một hàng lỗi chặn → Confirm **0 business row**. Chỉ warning → vẫn Confirm được.
- CCCD chính xác giúp tạo/cập nhật/giữ nguyên đúng participant Organization và membership batch; không tạo trùng hoặc đổi Patient link ngầm.
- Snapshot HealthExaminationRecord đã chuẩn bị/phát hành không đổi khi import lại.
- Confirm atomic, race-safe, idempotent, có audit và trả conflict an toàn khi preview cũ.
- Front Desk/SYSTEM_ADMIN không có quyền nghiệp vụ bị chặn ở mọi API; FE không còn import giả.
- Mọi check áp dụng ở giai đoạn 4 đã chạy thành công, hoặc giới hạn môi trường được báo rõ.






