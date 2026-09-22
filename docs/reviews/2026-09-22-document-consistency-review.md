# Rà soát đồng bộ tài liệu NKC DX

Ngày rà soát: 22/09/2026.

Kết luận: requirement-v2.3, use-case-v2.4 và table-design-v2.9 thống nhất về phần lớn luồng chính, nhưng chưa đủ nhất quán để đóng băng hợp đồng nghiệp vụ và sinh toàn bộ DDL production. Có một xung đột trực tiếp với quy tắc repo, một số mâu thuẫn nội bộ và các tình huống biên chưa được đặc tả đủ để triển khai an toàn.

## Phạm vi và căn cứ

- [Requirement v2.3](C:/Users/PC/Downloads/requirement-v2.3.docx).
- [Use case v2.4](C:/Users/PC/Downloads/use-case-v2.4.docx).
- [Table design v2.9](C:/Users/PC/Downloads/table-design-v2.9.docx).
- [PROJECT_RULES.md](../../PROJECT_RULES.md), [PROJECT_SKILLS.md](../../PROJECT_SKILLS.md) và AGENTS.md do người dùng cung cấp trong phiên làm việc.

Đã đọc nội dung đoạn văn và bảng của cả ba DOCX. Dẫn chiếu dưới đây sử dụng mã FR/BR/UC và số mục schema; không sử dụng số trang chưa được xác minh. Không chỉnh sửa các DOCX nguồn. Đây là review hợp đồng tài liệu, không phải kiểm chứng implementation, hiệu năng thực tế, bố cục in hay tính phù hợp pháp lý của biểu mẫu. Chưa có mẫu in thực tế trong ba tệp được cung cấp để đối chiếu renderer. Không thấy thư mục docs/adr và docs/architecture tại workspace hiện tại; chưa có ADR tại những vị trí đó để giải quyết xung đột.

P1: cần chốt trước khi triển khai phần nghiệp vụ liên quan. P2: cần hoàn thiện trước nghiệm thu hoặc khi chốt thiết kế chi tiết. Các hướng sửa là đề xuất, chưa phải quyết định thay thế baseline.

## Những phần đã đồng bộ

| Chủ đề | Căn cứ thống nhất |
|---|---|
| CCCD bắt buộc, lookup chính xác, không fuzzy merge | FR-PAT-003/004; UC-PAT-03; schema 4.1 |
| Encounter và Journey có lifecycle riêng, không queue ticket | FR-JOURNEY; UC phần 6.1; schema phần 5 |
| Bác sĩ chỉ định, Front Desk thu tiền, payment gate | FR-ORD/FR-BIL; UC-ORD/UC-BIL; schema 4.26–4.32 |
| Nhiều Order Round trong một Encounter | BR-005; UC-CLN-05; schema 4.26 |
| Chỉ bác sĩ chọn subset dịch vụ trong scope batch | BR-031; UC phần 5A; schema 4.39 và 7.4 |
| Gói doanh nghiệp dùng NOT_REQUIRED, không invoice cá nhân | FR-HC-012/BR-031; UC phần 5A; schema 4.26 và phần 12 |
| Kết quả doanh nghiệp dùng chung result domain | FR-HC-013; UC-HC-13; schema 4.39, 4.48–4.51 |
| SHS dùng chung, Mẫu số 03 có barcode, reprint giữ snapshot | FR-HC-006/008; UC-HC-08/09; schema 4.38 |
| Giá doanh nghiệp tách giá bán lẻ, hai báo cáo cùng nguồn | BR-026/028/029; UC-HC-14/15; schema 4.36/4.39 |
| Browser yêu cầu in, không xác nhận in vật lý thành công | BR-020/021; UC quy tắc in; schema 4.16 |
| SMS là kênh ngoài hệ thống của baseline | FR-NOT; UC-NOT; schema 4.60/4.61 |

## Các vấn đề ưu tiên

### F01 — P1 — Thời điểm tạo Patient mâu thuẫn với quy tắc repo

**Căn cứ:** Requirement FR-HC-011 và BR-019, UC-HC-03, schema 4.34/7.2/phần 12 đều yêu cầu import tạo hoặc tái sử dụng Patient ngay, company_employees.patient_id NOT NULL. Ngược lại, AGENTS.md mục 7 và PROJECT_RULES.md mục 14 yêu cầu import không tạo Patient; chỉ liên kết/tạo khi check-in.

**Ảnh hưởng:** Hai phương án khác nhau cả aggregate, dữ liệu bắt buộc, transaction import và thời điểm phát sinh hồ sơ y tế. Không thể triển khai đồng thời cả hai. Bulk print trước ngày khám còn phải được đối chiếu với yêu cầu Encounter + Patient bắt buộc của HealthCheckRecord.

**Đề xuất:** Chốt rõ baseline nào có thẩm quyền. Nếu giữ quy tắc repo, cần sửa cả ba tài liệu để mô tả roster độc lập và thời điểm provision khi check-in; đồng thời giải quyết nhu cầu in trước. Nếu chấp nhận Patient ngay khi import, cần quyết định được phê duyệt để cập nhật AGENTS/PROJECT_RULES. Không tự chọn phương án chỉ từ tên phiên bản.

### F02 — P1 — Đổi giá giữa đợt chưa bảo toàn đơn giá chung và công thức báo cáo

**Căn cứ:** FR-HC-010, UC-HC-11 và schema 4.35 cho phép thay đổi giá ngoài DRAFT với quyền/audit. BR-029 và UC-HC-15 quy định cùng dịch vụ trong batch có một đơn giá, thành tiền bằng số người × negotiatedUnitPrice. Schema 4.39 lại snapshot giá vào từng assignment tại lúc bác sĩ chọn.

**Ví dụ:** Nhân viên A nhận snapshot 100.000, đổi giá thành 120.000 rồi chọn dịch vụ cho B. SUM snapshot = 220.000 nhưng 2 × giá hiện tại = 240.000. Audit thay đổi giá không tự giải quyết sai khác này.

**Đề xuất:** Với MVP, ưu tiên khóa giá sau khi phát sinh assignment đầu tiên hoặc tách batch mới khi đổi giá. Nếu bắt buộc sửa giá trong batch, phải đặc tả rõ phạm vi cập nhật snapshot, bảo toàn lịch sử và cách tính hai báo cáo; không đồng thời giữ công thức một đơn giá nếu snapshot có thể khác nhau. READY freeze trong schema cũng cần được ghi rõ vào FR/UC thay vì chỉ nhắc mốc có kết quả/FINALIZED.

### F03 — P1 — Chưa rõ đường lưu dữ liệu hành chính từ Excel đến snapshot

**Căn cứ:** FR-HC-011 và UC-HC-12 yêu cầu import ngày/nơi cấp CCCD, dân tộc, đối tượng, nguồn chi trả, nhóm máu, nơi làm việc và lý do khám. Schema 4.1 patients, 4.34 company_employees và 4.37 batch_employees không chứa đầy đủ các trường này. Schema 4.41 có normalized_payload_json, nhưng bước provision 7.3 chỉ mô tả sao chép từ Patient/CompanyEmployee.

**Ảnh hưởng:** Dữ liệu có thể vẫn nằm trong staging JSON nhưng không có hợp đồng chỉ rõ row nào là nguồn, import lại ưu tiên bản nào và cách đưa dữ liệu vào HealthCheckRecord. Người dùng có thể phải nhập lại dữ liệu đã import. Với Patient đã tồn tại, cũng chưa rõ dữ liệu khác master là cảnh báo, sửa snapshot hay cập nhật master.

**Đề xuất:** Định nghĩa mapping từng field và nguồn ưu tiên: Patient, dữ liệu import đã xác nhận, context batch, chỉnh sửa của người dùng. Có thể tái sử dụng normalized_payload_json nếu giữ liên kết nguồn và vòng đời rõ ràng; không cần lập tức thêm nhiều bảng. Không âm thầm cập nhật Patient master từ danh sách doanh nghiệp.

### F04 — P1 — Thay HealthCheckRecord chưa giải quyết liên kết dịch vụ cũ

**Căn cứ:** BR-023/UC-HC-09 cho phép thay hồ sơ và vô hiệu SHS cũ. Schema 4.38 có replaces_health_check_record_id, UNIQUE(encounter_id) và chỉ một record active cho batch employee. Schema 4.39 lại UNIQUE(batch_employee_id, batch_service_id), mỗi row gắn một ServiceRequest thuộc một OrderRound/Encounter cụ thể.

**Ảnh hưởng:** Nếu thay record bằng Encounter mới sau khi đã có chỉ định/kết quả, assignment cũ vẫn chiếm unique key và trỏ Encounter cũ. Chưa rõ hồ sơ mới dùng dịch vụ nào, báo cáo tính một hay hai lần, và lịch sử kết quả được giữ ra sao. Chỉ đổi SHS không giải quyết được chuỗi liên kết.

**Đề xuất:** Đặc tả replacement theo mốc chưa chỉ định, đã chỉ định và đã có kết quả. Hướng MVP đơn giản là chỉ cho phép thay theo điều kiện hạn chế được phê duyệt; nếu cần thay sau khi có kết quả thì phải định nghĩa quan hệ assignment theo visit/lịch sử trước khi sửa schema. Nêu rõ replacement có tạo Encounter mới vì UNIQUE(encounter_id) hiện không cho hai record cùng Encounter.

### F05 — P1 — Chưa định nghĩa đủ required work và transition của Journey

**Căn cứ:** FR-CLN-008, FR-RES-003, UC-RES-01 và UC-CLN-06 phụ thuộc required results/work. Requirement mục 16 câu 7 còn để mở “kết quả nào được xem là required”. Schema 4.27 chưa chỉ ra nguồn chính sách này. Flow doanh nghiệp provision Encounter và NOT_REQUIRED đã rõ nhưng chưa mô tả đầy đủ cách vào Doctor Worklist và chuyển tiếp sau checklist; lưu đồ Journey chủ yếu mô tả nhánh có thanh toán.

**Ảnh hưởng:** Chưa thể viết acceptance test quyết định khi nào được kết luận, hoàn tất hoặc bỏ qua kết quả. Callback của round cũ, round mới chưa trả tiền, dịch vụ hủy hoặc gói không cần thu tiền có thể dẫn tới cách điều phối khác nhau giữa các implementation.

**Đề xuất:** Lập bảng transition gồm trigger, điều kiện, actor, trạng thái trước/sau và quyền override. Chốt required là quy tắc suy ra hay dữ liệu lưu, phạm vi theo Encounter hay Round, cách xử lý CANCELED/NOT_REQUIRED và kết quả đến muộn. Không tự thêm required flag khi chưa chốt nghiệp vụ.

### F06 — P1 — Quyền import kết quả chưa tách rõ khỏi quyền duyệt

**Căn cứ:** UC-HC-13 cho phép Front Desk/Quản lý/Nhân viên chuyên môn import và confirm dữ liệu vào LabResult/DiagnosticReport. FR-LAB-006 và UC-LAB-05 có VERIFIED/FINAL/CORRECTED; UC-NOT-02 yêu cầu người có thẩm quyền phát hành. Schema 4.39 quy định billable chỉ bật khi ServiceRequest COMPLETED.

**Khoảng trống:** Chưa chỉ rõ confirm import tạo DRAFT, VERIFIED hay có thể FINAL; ai được cập nhật tiến độ COMPLETED; dữ liệu thiếu analyte có được hoàn tất; actor nào chịu trách nhiệm chuyên môn. Đây là thiếu hợp đồng quyền, chưa phải bằng chứng implementation đã cho phép vượt quyền.

**Đề xuất:** Tách quyền upload/map/confirm nhập dữ liệu khỏi verify/final/correct. Hướng đơn giản là import hành chính chỉ tạo bản nháp, chuyên môn duyệt riêng; cần nghiệp vụ phê duyệt. Completion, billable và SMS chỉ phát sinh theo transition đã được chốt, không dựa đơn thuần vào trạng thái do Excel cung cấp.

### F07 — P2 — Phiên bản template đã khóa có thể bị resolver bỏ qua

**Căn cứ:** Schema 4.36 đóng băng document_template_version_id tại READY. FR-HC-016, UC-HC-16 và schema 7.4 bước 7 lại mô tả resolve qua service_template_mappings/template đang áp dụng. UC-HC-06 còn ghi A4 cố định dù quy tắc chung yêu cầu đọc paperSize từ version.

**Ảnh hưởng:** Thay active mapping/version sau READY có thể khiến nhân viên in sau dùng mẫu khác nếu người triển khai bám theo flow resolver chung.

**Đề xuất:** Ghi thứ tự ưu tiên thống nhất: reprint dùng version của generated_document; lần in mới trong batch dùng version đã khóa của batch/service; khám lẻ resolve version hợp lệ rồi lưu vào generated_document. Dùng paperSize từ version; nếu Mẫu 03 thực sự bắt buộc A4 thì ghi thành invariant và validate lúc cấu hình.

### F08 — P2 — Reprint giữ hành chính nhưng chưa đóng băng nội dung kết quả đã in

**Căn cứ:** FR-HC-006/016 và UC-HC-08 yêu cầu snapshot/version gốc. Schema 4.16 giữ template version, payload hash và file_attachment_id nullable; 4.17 chỉ gắn ServiceRequest, không chỉ rõ version LabResult/DiagnosticReport. Schema 4.38 cho sửa hành chính trước issue nhưng chưa định nghĩa đầy đủ thời điểm khóa.

**Ảnh hưởng:** Hash không đủ tái tạo payload. Sau correction kết quả, render lại cùng document có thể lấy kết quả mới. Preview trước khi sửa hành chính cũng có thể bị coi nhầm là bản đã phát hành.

**Đề xuất:** Chốt khác biệt giữa reprint bản đã phát hành và phát hành bản thay thế có kết quả mới. Nếu cần tái tạo nguyên bản, giữ payload bất biến hoặc version nguồn chính xác hoặc artifact bất biến; không mặc định cần cả ba. Định nghĩa rõ mốc khóa snapshot và định danh chuỗi phiên bản document.

### F09 — P2 — Quy tắc đối chiếu cùng Patient, Encounter và Batch còn thiếu

**Căn cứ:** Schema có nhiều FK độc lập: health_check_records.patient_id + encounter_id; invoice/prescription.patient_id + encounter_id; batch_employees.batch_id + company_employee_id; corporate assignment + service_request_id; document-service bridge. Một số quy tắc đã có, như cùng batch và Patient corporate, nhưng chưa thành danh sách toàn vẹn đầy đủ.

**Ảnh hưởng:** FK tồn tại riêng lẻ không mô tả được tất cả điều kiện cùng hồ sơ. Ví dụ document của Encounter A không được nhận ServiceRequest của Encounter B; nhân viên công ty A không được gắn batch công ty B; corporate ServiceRequest phải đúng dịch vụ và Encounter của employee visit.

**Đề xuất:** Lập ma trận invariant, module sở hữu và nơi enforce. Dùng ràng buộc DB cho invariant phù hợp, application transaction cho đối chiếu nhiều aggregate, kèm test từ chối liên kết sai. Không cần thêm FK trùng lặp chỉ để kiểm tra thuận tiện.

### F10 — P2 — Hợp đồng cạnh tranh cập nhật và chống lặp còn quá tổng quát

**Căn cứ:** Requirement BR-016 và UC phần 6.3 yêu cầu idempotency/concurrency guard. Schema mới mô tả rowversion cho một số bảng; các trạng thái nhạy cảm như ServiceRequest, PaymentAuthorization, invoice, batch và draft result chưa chỉ rõ chiến lược. integration_messages.external_message_id có thể NULL và chưa nêu unique chống lặp theo nguồn; idempotency_keys đã có nhưng quy tắc scope/claim chưa rõ.

**Đề xuất:** Với từng command, ghi rõ business key, idempotency scope, cách xử lý cùng key khác payload và cơ chế serialize/cập nhật có điều kiện. Không bắt buộc thêm rowversion khắp nơi: cập nhật theo expected status, khóa aggregate hoặc phương án khác cũng được nếu có hợp đồng và test. Với sự kiện integration, xác định khóa nguồn và chính sách chống xử lý lặp, kể cả sau timeout.

### F11 — P2 — Hoàn tiền và miễn phí khám còn thiếu đặc tả quyết định

**Căn cứ:** FR-BIL-007/UC-BIL-05 bao gồm hoàn/hủy/đối soát, nhưng requirement mục 16 câu 11 còn hỏi có cần hoàn một phần/đổi dịch vụ hay không. Schema 4.31 lưu adjustment cấp invoice và 4.32 lưu trạng thái refund, chưa chỉ rõ liên kết refund với payment/dịch vụ được hoàn. BR-003 cho phép authorization/miễn hợp lệ phí khám ban đầu, trong khi PaymentAuthorization 4.28 bắt buộc ServiceRequest và invoice tiếp nhận có thể không có ServiceRequest.

**Đề xuất:** Chốt phạm vi hoàn tiền của MVP, cách phân bổ cho nhiều payment/dịch vụ và tác động lên authorization khi dịch vụ đã bắt đầu. Ghi rõ cách biểu diễn miễn phí khám đầu vào, actor/lý do và điều kiện vào Worklist. Không tự coi invoice adjustment là chứng từ hoàn tiền đầy đủ hoặc tự tạo OrderRound bác sĩ cho phí tiếp nhận.

## Sai lệch biên tập và traceability

1. Requirement mục 10 còn PatientIdentifier/PatientContact, trong khi FR-PAT và schema 4.1 đã thống nhất lưu CCCD trực tiếp, không có các bảng này. Cần sửa sơ đồ để không gợi ý tạo abstraction cũ.
2. UC Inventory mục 3 có **73 dòng UC**, nhưng ghi tổng **72**; UC-HC-16 có đặc tả và mapping nhưng thiếu trong inventory. Nếu thêm UC-HC-16, inventory sẽ có **74** dòng.
3. UC Inventory còn nhãn “v1.3” trong tài liệu v2.4. Sau UC-HC-16 có đoạn rơi “h cho doanh nghiệp.”; UC-HC-10 đánh số bước 3 hai lần.
4. BR-030 nói các field hành chính khác CCCD có thể NULL, trong khi FR-HC-006 và schema 4.38 yêu cầu cả full name, sex, DOB, CCCD. Cần viết rõ bốn trường bắt buộc rồi mới liệt kê nhóm tùy chọn.
5. BR-009 viết Encounter chỉ outpatient trong khi schema có OUTPATIENT và HEALTH_CHECK. Nếu outpatient ở đây nghĩa là không nội trú thì diễn đạt lại để tránh nhầm với enum OUTPATIENT.
6. UC-HC-16 nói mỗi người/lần khám phải có record mới, trong khi luồng trước đã provision record ở UC-HC-07. Nên viết “resolve record của lần khám đã provision; chỉ tạo nếu chưa có theo command được phép”, tránh hiểu là mỗi lần sinh bộ phiếu đều tạo mới.
7. Requirement gọi appointment/portal/SMS trong MVP, UC đánh nhiều mục Should, table design hoãn auth portal ngoài corporate-first freeze. Có thể là phân kỳ hợp lý, nhưng cần một bảng release scope chung để phân biệt ngoài sprint với ngoài MVP.
8. UC-ORD-01 nhắc order set/favorite nhưng ba tài liệu chưa có hợp đồng đủ cho lưu/quản lý chúng. Nếu chỉ là tiện ích UI chưa thuộc MVP thì ghi rõ; không suy ra cần thêm bảng từ một câu trong main flow.

## Những tối ưu nên làm

- Tạo một ma trận FR → BR → UC → aggregate/bảng → acceptance test. Các vấn đề F01–F11 nên có mã quyết định và trạng thái xác nhận, không chỉ được sửa rải rác trong ba bản Word.
- Chuẩn hóa state dictionary và transition table. Các status chưa có enum/transition đầy đủ như invoice, order_round, batch_employee, import row cần được chốt theo phạm vi thực hiện, tránh developer tự đặt.
- Ghi ownership theo module cho từng nhóm bảng và read contract. Corporate report cần dữ liệu diagnostics/billing nhưng không có nghĩa mapper healthcheck được tùy ý đọc bảng nội bộ module khác; PROJECT_RULES mục 7 đã yêu cầu hợp đồng rõ ràng.
- Review index theo truy vấn thực tế khi có implementation. Đặc biệt luồng từ Encounter → OrderRound → ServiceRequest và lookup audit theo hồ sơ/thời gian cần kế hoạch truy cập. Tránh tạo hai index vật lý chỉ vì một unique constraint và một dòng index trong Word cùng mô tả CCCD/SHS. Chưa có query plan/dữ liệu tải để kết luận index nào thực sự thiếu hoặc dư.
- Với import lớn, đặc tả retry/partial commit và tái kiểm tra lúc confirm; preview hợp lệ không bảo đảm dữ liệu vẫn hợp lệ khi người dùng confirm sau đó. Không mặc định một transaction khổng lồ cho toàn batch khi chưa biết kích thước.
- Giữ những đơn giản hóa hiện có: result domain dùng chung, corporate wrapper không chứa kết quả riêng, không tổng tiền dẫn xuất riêng cho corporate report, không selected flag trùng với row existence, chưa thêm queue/pharmacy/broker/cache.
- NFR cần tải đo được: số Patient, số nhân viên/batch, số user đồng thời, phân vị thời gian phản hồi và điều kiện mạng. Các mốc “<2 giây trong điều kiện bình thường” chưa đủ làm tiêu chí nghiệm thu hiệu năng.

## Thứ tự xử lý đề xuất

1. Chốt F01 với người sở hữu nghiệp vụ/kiến trúc trước khi code Patient–CompanyEmployee import.
2. Chốt F02–F06 để ổn định giá, snapshot, replacement, completion và quyền duyệt.
3. Đồng bộ F07–F11, state dictionary và các lỗi traceability vào cả ba tài liệu.
4. Sau khi các quyết định được duyệt, mới sinh migration và acceptance tests cho module liên quan.

## Kiểm chứng và thay đổi trong phiên

- Đã đối chiếu nội dung cả ba DOCX với PROJECT_RULES/PROJECT_SKILLS và AGENTS được cung cấp.
- Đã kiểm đếm inventory UC; không thấy tracked insert/delete trong document.xml của ba nguồn.
- Chỉ thêm báo cáo Markdown này; không thay đổi module nghiệp vụ, migration, public API, test hay các DOCX nguồn.
- Không chạy Maven/test vì không thay đổi mã thực thi. Không khẳng định backend hiện đã đáp ứng hoặc vi phạm các điểm trên.
- Các quyết định nghiệp vụ nêu trong F01–F11 vẫn chưa được phê duyệt; báo cáo không tự thay thế baseline.
