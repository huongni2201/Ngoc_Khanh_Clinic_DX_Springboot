# Đối chiếu backend với 124 use case trong SRS

Ngày kiểm tra: 14/10/2026. Nguồn requirement: `C:/Users/PC/Downloads/Report-3_Software Requirement Specification.docx`, mục I.4.2 (UC-001–UC-124), I.4.4, II.1–2, III.2 và V.1. Dùng số UC của bản này, không dùng số UC cũ trong seed/Javadoc.

Backend hiện có các luồng quản trị danh sách khám doanh nghiệp, đối soát dịch vụ, báo cáo tiền theo dịch vụ và in phiếu trắng. Chưa có toàn bộ quy trình hồ sơ khám lâm sàng và phần lớn các luồng ngoại trú của SRS. Có tên bảng, domain method hoặc permission trong seed không đồng nghĩa với có use case chạy được từ API.

Phạm vi là repository backend hiện tại, gồm controllers, application, domain liên quan, mapper, migration, accepted ADR và test. Không kiểm tra frontend, triển khai production, tài liệu TDS, sơ đồ/ảnh mockup nhúng hoặc nội dung nằm ngoài file DOCX. Đây là review độ khớp nghiệp vụ, không phải chứng nhận mọi yêu cầu phi chức năng.

## Cách đọc kết quả

- **Có luồng chính**: có API và application flow tương ứng; không có nghĩa đã chứng minh mọi nhánh, quyền theo phạm vi hoặc mọi câu trong SRS.
- **Một phần / lệch**: có chức năng liên quan, nhưng thiếu hoặc khác điều kiện, dữ liệu, actor hoặc kết quả của SRS.
- **Chưa có luồng**: không thấy API/application flow phục vụ UC; có thể đã có nền tảng schema/domain.
- **Xung đột phạm vi**: SRS yêu cầu nhưng accepted decision trong repo đã loại khỏi sản phẩm.

Các chênh lệch với SRS không tự động cho phép sửa code trái accepted ADR. Cần thống nhất baseline trước khi đổi state, vai trò, identity hoặc contract. Review này chỉ tạo báo cáo, không sửa runtime, schema hay public API.

## Các điểm cần xử lý hoặc thống nhất trước

### 1. Vòng đời Batch và nghĩa của Start khác SRS

**UC-068, UC-069, UC-070, UC-071, UC-072; ưu tiên cao.** SRS II.2.17–2.21 cho sửa DRAFT/READY, Start READY → IN_PROGRESS, Close IN_PROGRESS → CLOSED và Cancel DRAFT/READY → CANCELLED có lý do, người thực hiện, thời điểm và lịch sử xem được.

Code Start gọi `markReady()`, rồi cập nhật với trạng thái cũ DRAFT. Domain cho sửa chỉ DRAFT và có chuỗi DRAFT → READY → FINALIZED → CLOSED; không có trạng thái IN_PROGRESS/CANCELLED cho Batch. Không có application/API Close hoặc Cancel. Ví dụ một batch READY hợp lệ theo SRS bị từ chối khi gọi Start; batch DRAFT lại được Start thành READY. DELETE draft rỗng không thay thế Cancel batch có Participant.

Bằng chứng: [StartHealthExaminationBatchUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/StartHealthExaminationBatchUseCase.java#L81), [HealthExaminationBatch](../../src/main/java/com/ngockhanh/clinic/healthexamination/domain/aggregate/HealthExaminationBatch.java#L171), [DeleteHealthExaminationBatchUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/DeleteHealthExaminationBatchUseCase.java#L73). Domain `close()` không phải một API Close đã hoàn thiện.

Hướng xử lý: thống nhất state machine giữa SRS và [ADR-0013](../adr/0013-clean-slate-application-contract.md), rồi mới sửa domain, migration, API, quyền và tests cùng nhau. Không chỉ đổi nhãn Start trên UI.

### 2. Nhập dữ liệu khám hiện chỉ là đối soát dịch vụ

**UC-080, UC-085–UC-088, UC-093; ưu tiên cao.** SRS II.2.10/2.14 và BR-33–36 yêu cầu findings/results theo trường, định dạng, đơn vị, dữ liệu thiếu và hồ sơ liên kết. Command hiện chỉ có attendance, actual date, tập service ID đã thực hiện và rowVersion. Reconciler đánh dấu X, ghi attendance và RECONCILED; không lưu chỉ số xét nghiệm, khám chuyên khoa, kết quả hay clinical conclusion.

Ví dụ tick toàn bộ dịch vụ sẽ thành RECONCILED dù chưa có một kết quả y khoa nào. Đây không đủ để công nhận UC nhập dữ liệu khám hoặc hoàn tất dữ liệu lâm sàng. `GetParticipantDetailUseCase` trả roster để edit; summary chỉ đếm attendance/reconciliation, không phát hiện kết quả bắt buộc còn thiếu. Excel export là ma trận người × dịch vụ, không phải đầy đủ báo cáo kết quả khám.

Bằng chứng: [RecordExaminationDetailCommand](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/command/RecordExaminationDetailCommand.java), [ExaminationDetailReconciler](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/service/ExaminationDetailReconciler.java#L50), [GetParticipantDetailUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/GetParticipantDetailUseCase.java#L44), [GetExaminationSummaryUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/GetExaminationSummaryUseCase.java#L40).

Hướng xử lý: giữ đối soát dịch vụ theo contract hiện hành; bổ sung luồng hồ sơ/kết quả lâm sàng và kiểm tra completeness theo performed items. Không tái sử dụng RECONCILED làm trạng thái hoàn tất y khoa.

### 3. Thiếu cả chuỗi chuẩn bị đến phát hành hồ sơ khám

**UC-081–UC-083, UC-089–UC-092; ưu tiên cao.** Chưa có application/API chuẩn bị hồ sơ, chọn/link Patient, xử lý xung đột, lưu form, ghi kết luận, hoàn tất, ký/phát hành và amendment. `HealthExaminationRecord.prepare/complete/issue` có trong domain nhưng không cung cấp các luồng được phân quyền, lưu phiên bản và audit từ HTTP.

BR-35/37–39 còn yêu cầu quyền bác sĩ kết luận, chữ ký chuyên môn/cơ sở và bảo vệ phiên bản chính thức. Không được xem PDF phiếu trắng hoặc báo cáo thanh toán là hồ sơ khám đã phát hành. Hợp đồng chính thức còn mở tại [open issuance](../architecture/07-open-items.md#official-document-issuance).

Hướng xử lý: chốt role, trường dữ liệu, quy trình ký và issuance contract; triển khai theo thứ tự prepare/conflict → clinical data → completeness/conclusion → finalize/sign/issue → amend.

### 4. In phiếu bỏ qua điều kiện prepared record và saved form

**UC-083/UC-084, BR-16/17/20; ưu tiên cao nếu áp dụng SRS.** SRS II.2.7/2.13 yêu cầu prepared examination record và saved form gắn source/template version. Loader hiện chỉ kiểm tra Batch READY/FINALIZED và roster ACTIVE, rồi lấy thông tin roster/tổ chức/đợt khám. Không đọc hoặc bắt buộc HealthExaminationRecord/saved form.

Ví dụ Participant mới thêm, chưa linked Patient và chưa prepare vẫn đủ điều kiện in. In lại đơn lẻ dùng template đang hiệu lực và roster hiện tại; không có đảm bảo nội dung/template giống form đã lưu trước đó. Bulk pin một template cho job là đúng một phần, nhưng không thay thế việc lưu form theo record.

Bằng chứng: [ExaminationFormSourceLoader](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/service/ExaminationFormSourceLoader.java#L60), [DocumentRenderingService](../../src/main/java/com/ngockhanh/clinic/document/application/service/DocumentRenderingService.java#L65), [current print contract](../api/examination-form-print.md). Đây là khác biệt với contract phiếu trắng hiện hành, cần quyết định giữ phiếu roster hay áp dụng luồng SRS trước khi sửa.

### 5. Phân quyền khám doanh nghiệp chưa khớp actor matrix

**UC-084–UC-087/UC-093 và I.4.4; ưu tiên cao.** SRS cho Nurse in/nhập/import dữ liệu; Receptionist và Clinic Manager không được in phiếu trong matrix. Seed hiện dùng `DATA_ENTRY_STAFF` thay cho role Nurse; thêm quyền in cho CLINIC_MANAGER và RECEPTIONIST. Quyền record/import/export service matrix chỉ cấp CLINIC_MANAGER, không cấp DATA_ENTRY_STAFF.

Ví dụ tài khoản Data Entry Staff có quyền `PARTICIPANT_EXAMINATION_DATA_ENTER` cũ nhưng không có `HEALTH_EXAMINATION_SERVICE_RECORD` sẽ không nhập được ở endpoint hiện hành; Receptionist lại được in phiếu dù SRS ghi No. Đổi tên role thôi không giải quyết các grant khác nhau. Nếu service matrix là luồng kế toán độc lập với clinical entry thì phải ghi rõ scope và actor riêng trong SRS.

Bằng chứng: [V002 roles/grants](../../src/main/resources/db/migration/V002__seed_roles_and_permissions.sql#L16), các grant tại dòng 231–245, 266–287; [ExaminationDetailAccessPolicy](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/service/ExaminationDetailAccessPolicy.java).

### 6. Import Participant dừng ở lỗi đầu tiên

**UC-075, BR-15; ưu tiên vừa.** SRS yêu cầu xử lý hết file và trả toàn bộ lỗi dòng. Reader và `validate()` hiện throw ngay tại lỗi đầu tiên, kể cả thiếu trường, CCCD sai hoặc trùng trong file. Ví dụ dòng 2 và 8 đều sai thì lần upload đầu chỉ thấy lỗi đầu tiên; không có summary Valid/Invalid Rows và danh sách lỗi đầy đủ như SRS.

Bằng chứng: [ImportParticipantsUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/ImportParticipantsUseCase.java#L120), [PoiParticipantExcelReader](../../src/main/java/com/ngockhanh/clinic/healthexamination/infrastructure/excel/PoiParticipantExcelReader.java#L104). All-or-nothing/idempotency có luồng tương ứng; điểm thiếu là hợp đồng validation nhiều lỗi. Thiết kế lỗi trả về cần tuân thủ quy định bảo mật/envelope hiện hành, không đưa nguyên CCCD hoặc giá trị ô vào lỗi.

SRS còn tự mâu thuẫn: II.2.6 có review rồi bấm Import, nhưng BR-14 yêu cầu validate xong import ngay. Repo đã chấp nhận single-step add-only/all-or-nothing trong ADR-0013. Không tự bổ sung preview/confirm/cancel chỉ để theo một đoạn chưa thống nhất của SRS.

### 7. Organization không hỗ trợ toàn bộ contract SRS

**UC-062–UC-065, BR-02/10; ưu tiên vừa.** SRS yêu cầu system-generated code, required organization type, sửa ACTIVE/INACTIVE và reactivate; email/address là optional trong luồng tạo chi tiết. Request hiện không có code/type/status, lại bắt email, address, contactEmail. Update giữ nguyên status; DELETE chỉ deactivate, không có activate workflow. Get lọc chỉ ACTIVE, nên không xem Organization INACTIVE theo luồng lịch sử mà SRS cho phép.

Ví dụ tạo tổ chức chỉ có tên, tên liên hệ và điện thoại như II.2.2 sẽ bị 400; sau deactivate không thể xem chi tiết qua GET hoặc khôi phục status qua PUT. Cần tách hai việc: thiếu reactivation/historical access và xung đột identity/required fields với contract hiện hành.

Bằng chứng: [CreateOrganizationRequest](../../src/main/java/com/ngockhanh/clinic/healthexamination/api/request/CreateOrganizationRequest.java#L9), [UpdateOrganizationRequest](../../src/main/java/com/ngockhanh/clinic/healthexamination/api/request/UpdateOrganizationRequest.java#L11), [UpdateOrganizationUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/UpdateOrganizationUseCase.java#L23), [GetOrganizationByIdUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/GetOrganizationByIdUseCase.java#L42). Việc bỏ code là owner decision rõ ràng tại [ADR-0016](../adr/0016-organization-tax-code-identity.md); không tự thêm lại theo SRS.

### 8. Vẫn sửa cấu hình Batch của Organization INACTIVE

**UC-068/UC-072, BR-10; ưu tiên cao.** Update chỉ xác nhận Organization tồn tại, không kiểm tra ACTIVE, rồi lưu ngày/dịch vụ/giá của Batch DRAFT. Javadoc và test còn nêu INACTIVE được phép. Ví dụ sau deactivate Organization, caller giữ quyền batch update vẫn đổi được giá/ngày của Batch draft, trái BR-10.

Bằng chứng: [UpdateHealthExaminationBatchUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/UpdateHealthExaminationBatchUseCase.java#L82), [existing test](../../src/test/java/com/ngockhanh/clinic/healthexamination/application/usecase/UpdateHealthExaminationBatchUseCaseTest.java#L287). Nếu BR-10 được chọn làm baseline, thêm guard ACTIVE tại use case và sửa test cho tình huống này; giải quyết quyết định hiện hành cho phép batch workflows tiếp tục sau deactivate.

### 9. Quyền sửa thông tin Batch cũng cho sửa dịch vụ và giá

**UC-072 và I.4.4; ưu tiên cao.** SRS II.2.21 yêu cầu service/price permissions. HTTP PUT chỉ gắn `HEALTH_EXAMINATION_BATCH_UPDATE`; hai permission service-configure/price-configure không có endpoint. Use case nhận actor UUID, không principal, và thay toàn bộ dịch vụ/giá mà không kiểm tra các permission này.

Tình huống: role có BATCH_UPDATE nhưng đã bị bỏ quyền PRICE_CONFIGURE vẫn gửi negotiatedPrice mới qua PUT được. Role seed mặc định hiện giữ cả ba quyền nên tình huống này cần role bị giới hạn; đây không phải khẳng định role mặc định đã bị lạm dụng.

Bằng chứng: [V002 mapping](../../src/main/resources/db/migration/V002__seed_roles_and_permissions.sql#L81), [UpdateHealthExaminationBatchUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/UpdateHealthExaminationBatchUseCase.java#L69), [EndpointPermissions](../../src/main/java/com/ngockhanh/clinic/accesscontrol/infrastructure/security/EndpointPermissions.java#L52). Hướng sửa nhỏ: kiểm tra quyền theo loại thay đổi ở application theo principal đã xác thực, giữ một endpoint nếu contract chọn vậy. Không nhất thiết phải tách UC thành nhiều API.

### 10. Sign In chưa đủ các postcondition của SRS

**UC-001 và BR-05; ưu tiên cao.** Đã có password verification, audit, session snapshot và timeout. Chưa có đếm số lần sai/temporary lock tự động, cập nhật last sign-in, kiểm tra owner Patient ACTIVE. Mapper không đọc patient status và `eligible()` chỉ cần patientId với PATIENT account. Session authentication chỉ đọc/touch Redis, không recheck account status; automatic revocation chưa có use case gọi.

Ví dụ Patient INACTIVE với account ACTIVE vẫn qua điều kiện eligible; account bị disable/lock sau khi đã đăng nhập có thể dùng snapshot tới khi hết hạn nếu không có cơ chế revoke bên ngoài. Sai password nhiều lần chỉ ghi audit, không tăng counter để tự khóa. Các điểm này được ghi là go-live blockers một phần trong repo, không phải tính năng đã đủ.

Bằng chứng: [LoginUseCase](../../src/main/java/com/ngockhanh/clinic/accesscontrol/application/usecase/LoginUseCase.java#L56), [UserAccount](../../src/main/java/com/ngockhanh/clinic/accesscontrol/domain/entity/UserAccount.java#L35), [login SQL](../../src/main/resources/mapper/accesscontrol/UserLoginMyBatisMapper.xml#L5), [AuthenticateSessionUseCase](../../src/main/java/com/ngockhanh/clinic/accesscontrol/application/usecase/AuthenticateSessionUseCase.java#L32), [ADR-0014 blockers](../adr/0014-session-cookie-redis-login.md#go-live-blockers). UC-003–010 chưa có public workflows; không tính enum LOCKED hoặc method revokeAll là đã có account administration.

## Use case có vẻ thừa và phần mở rộng cần ghi vào SRS

Không thấy bằng chứng đủ để đề nghị xoá một luồng nghiệp vụ chỉ vì không có tên UC riêng. Các trường hợp cụ thể:

| Source | Đánh giá |
|---|---|
| DeleteOrganizationUseCase | API bổ sung; thực chất deactivate, có thể phục vụ một phần UC-065. SRS cần ghi rõ action/permission; thiếu reactivate vẫn còn. |
| DeleteHealthExaminationBatchUseCase | API bổ sung xoá mềm draft rỗng, không phải UC-071 cancel. Nếu giữ thì ghi riêng điều kiện vào SRS. |
| Record/ImportExaminationDetails | Luồng đối soát dịch vụ được owner chấp nhận và có khái niệm Participation Service trong SRS; không coi là thừa, nhưng không tính thay cho nhập kết quả lâm sàng. |
| GetExaminationDetailImportTemplateUseCase | API hỗ trợ import đối soát, không phải UC nghiệp vụ độc lập bắt buộc. |
| CreateExaminationFormPrintJob/GetRenderJob/DownloadRenderJobPart | Hạ tầng phục vụ in nhiều người trong II.2.7, không phải ba use case nghiệp vụ thừa. |
| AuthenticateSessionUseCase và GET /auth/me | Hỗ trợ đăng nhập/khôi phục phiên, không cần phát minh UC mới. |
| ListServiceCatalogUseCase | Cần cho chọn dịch vụ UC-067/072; chỉ riêng nó chưa đủ UC-113 quản lý catalog. |

Seed còn giữ UC ID cũ: Start mô tả UC-071 trong khi SRS mới là UC-069; print/bulk mô tả UC-085/086 trong khi UC-086 mới là import examination data. Cần sửa bảng traceability/descriptions sau khi thống nhất SRS, không dùng các số cũ để đo coverage.

## Xung đột trong requirement và giữa SRS với accepted decisions

1. **UC-116 quản lý document template** có trong SRS, nhưng [ADR-0018](../adr/0018-it-managed-document-templates.md) ngày 13/10 loại UI/API template management khỏi sản phẩm. Đây là SRS chưa đồng bộ scope, không phải mặc định cần implement.
2. **Nhiều role/account** trong UC-005/010 và Sign In khác owner amendment 13/10: account chỉ có một `accounts.role_id`. Đã ghi trong [ADR-0013](../adr/0013-clean-slate-application-contract.md#access-control). Cần chỉnh requirement hoặc ra quyết định thay thế.
3. **Kết luận/finalize do Nurse hay bác sĩ**: UC-089/090 và permission matrix cấp Nurse; BR-37 yêu cầu chỉ General Practitioner có concluding-clinician permission. Đây là mâu thuẫn nội bộ SRS, không thể chọn một bên rồi triển khai như đã rõ.
4. **Signature/issuance**: BR-39 bắt buộc professional/facility digital signatures, nhưng II.2.16 còn ghi signature policy là assumption cần xác định. Cần hợp đồng loại chữ ký, người ký, provider và trạng thái lỗi.
5. **Import review/confirm hay immediate**: II.2.6 và III screen mô tả review, BR-14 lại immediate import; repo đã chọn single-step. Validation nhiều lỗi theo BR-15 vẫn là khoảng trống riêng, không phụ thuộc việc có preview endpoint.
6. **Organization identity**: BR-02 yêu cầu code/type, ADR-0016 loại code; current contact-required fields cũng khác. Cần baseline thống nhất thay vì trộn hai data model.
7. II.2.11/2.12 còn tham chiếu BR-30, nhưng appendix không có BR-30. Điều kiện chuẩn bị/link Patient còn thiếu nguồn chuẩn; SRS II.2.11 nói patient creation xử lý ở patient management, còn accepted architecture cho phép link/create theo exact CCCD trong visit preparation.

## Thứ tự hoàn thiện đề xuất

1. Chốt SRS/ADR cho Batch, Organization, role cardinality, Nurse/GP, import và template management.
2. Khép kín BF-02: prepare/conflict → clinical data → completeness/conclusion → finalize/sign/issue/amend; sau đó close/cancel batch và các báo cáo thực sự dùng medical data.
3. Sửa các guard/quyền của chức năng đang có: organization INACTIVE, service/price permissions, row-error aggregation và login lifecycle.
4. Triển khai các nhóm còn thiếu theo phạm vi release: account administration, patient/reception/clinical/diagnostics/billing/prescription/appointment/portal, reports/master data, notification và national health data.

Không cần tạo reporting module riêng: ADR-0012 yêu cầu query/report ở module nghiệp vụ sở hữu dữ liệu. Không triển khai theo số lượng class hoặc số permission seeded.

## Kiểm chứng và giới hạn

- Đã đối chiếu danh sách 124 UC bằng nội dung paragraph/table của DOCX với toàn bộ HTTP mappings và application inventory. Các khoảng trống lớn được kiểm tra thêm bằng [API inventory](../api/clean-slate-migration.md); phần sâu đọc luồng source và tests, không chỉ tên file.
- Chạy 7 test classes: LoginUseCaseTest, AuthenticateSessionUseCaseTest, StartHealthExaminationBatchUseCaseTest, RenderParticipantExaminationFormUseCaseTest, RecordExaminationDetailUseCaseTest, ExaminationDetailUseCasesTest, ExaminationDetailReconcilerTest.
- Lệnh thành công: `./mvnw.cmd -DargLine=-javaagent:C:\Users\PC\.m2\repository\org\mockito\mockito-core\5.23.0\mockito-core-5.23.0.jar -Dtest=LoginUseCaseTest,AuthenticateSessionUseCaseTest,StartHealthExaminationBatchUseCaseTest,RenderParticipantExaminationFormUseCaseTest,RecordExaminationDetailUseCaseTest,ExaminationDetailUseCasesTest,ExaminationDetailReconcilerTest test`.
- Kết quả: **76 tests, 0 failures, 0 errors, 0 skips; BUILD SUCCESS**. Các tests xác nhận contract hiện tại, không chứng minh source đã đáp ứng SRS mới.
- Lần chạy mặc định bị kẹt ở Mockito dynamic attach/named pipe trong sandbox và đã dừng; một lần retry chọn nhầm sources JAR nên JVM không khởi động (0 tests). Đã sửa đường dẫn binary JAR và chạy thành công bằng Java agent; không sửa pom hoặc cấu hình dự án.
- Chưa chạy full `verify`, PostgreSQL/Redis/Docker integration, browser end-to-end, Gotenberg, ký số, payment/SMS/national platform hoặc máy in thật. Không thêm/sửa test hay implementation. Runtime modules, migrations và public APIs giữ nguyên; chỉ thêm báo cáo này.

## Ma trận từng use case

Ma trận dưới đây không phải phần trăm hoàn thành sản phẩm: một UC lớn có thể chứa nhiều action/API. Các dòng “Một phần / lệch” gồm chức năng hỗ trợ gần nghĩa, không được tính là đã hoàn thành UC.


Tổng hợp theo cách phân loại trên: **17 Một phần / lệch**, **10 Có luồng chính**, **96 Chưa có luồng**, **1 Xung đột phạm vi**. Tổng 124 UC.

| UC | Yêu cầu SRS | Trạng thái | Source / khoảng trống |
|---|---|---|---|
| UC-001 | Sign In | Một phần / lệch | [LoginUseCase](../../src/main/java/com/ngockhanh/clinic/accesscontrol/application/usecase/LoginUseCase.java) — Có login/session; thiếu lockout, last sign-in, patient-active check, automatic revocation. Điểm 10. |
| UC-002 | Sign Out | Có luồng chính | [LogoutUseCase](../../src/main/java/com/ngockhanh/clinic/accesscontrol/application/usecase/LogoutUseCase.java) — Sign out current session; kiểm tra sâu mọi failure mode ngoài phạm vi. |
| UC-003 | Change Password | Chưa có luồng | Chưa có account/password/role administration application/API; permission/schema không phải workflow. |
| UC-004 | Reset Password | Chưa có luồng | Chưa có account/password/role administration application/API; permission/schema không phải workflow. |
| UC-005 | Create User Account | Chưa có luồng | Chưa có account/password/role administration application/API; permission/schema không phải workflow. |
| UC-006 | Deactivate User Account | Chưa có luồng | Chưa có account/password/role administration application/API; permission/schema không phải workflow. |
| UC-007 | Activate User Account | Chưa có luồng | Chưa có account/password/role administration application/API; permission/schema không phải workflow. |
| UC-008 | Manage Roles | Chưa có luồng | Chưa có account/password/role administration application/API; permission/schema không phải workflow. |
| UC-009 | Manage Role Permissions | Chưa có luồng | Chưa có account/password/role administration application/API; permission/schema không phải workflow. |
| UC-010 | Manage User Roles | Chưa có luồng | Chưa có account/password/role administration application/API; permission/schema không phải workflow. |
| UC-011 | Find Patient | Chưa có luồng | Chưa có public patient profile/history/safety/merge workflow. |
| UC-012 | View Patient Summary | Chưa có luồng | Chưa có public patient profile/history/safety/merge workflow. |
| UC-013 | Create Patient Profile | Chưa có luồng | Chưa có public patient profile/history/safety/merge workflow. |
| UC-014 | Update Patient Profile | Chưa có luồng | Chưa có public patient profile/history/safety/merge workflow. |
| UC-015 | View Patient Medical History | Chưa có luồng | Chưa có public patient profile/history/safety/merge workflow. |
| UC-016 | Record Patient Safety Information | Chưa có luồng | Chưa có public patient profile/history/safety/merge workflow. |
| UC-017 | Update Patient Safety Information | Chưa có luồng | Chưa có public patient profile/history/safety/merge workflow. |
| UC-018 | View Reception Worklist | Chưa có luồng | Chưa có public patient profile/history/safety/merge workflow. |
| UC-019 | Merge Duplicate Patient Profiles | Chưa có luồng | Chưa có public patient profile/history/safety/merge workflow. |
| UC-020 | Create Outpatient Encounter | Chưa có luồng | Chưa có public outpatient encounter/clinical workflow. |
| UC-021 | Change Encounter Room | Chưa có luồng | Chưa có public outpatient encounter/clinical workflow. |
| UC-022 | Cancel Encounter | Chưa có luồng | Chưa có public outpatient encounter/clinical workflow. |
| UC-023 | View General Practitioner Worklist | Chưa có luồng | Chưa có public outpatient encounter/clinical workflow. |
| UC-024 | Record Clinical Examination | Chưa có luồng | Chưa có public outpatient encounter/clinical workflow. |
| UC-025 | View Diagnostic Progress | Chưa có luồng | Chưa có public outpatient encounter/clinical workflow. |
| UC-026 | Review Diagnostic Results | Chưa có luồng | Chưa có public outpatient encounter/clinical workflow. |
| UC-027 | Record Final Diagnosis | Chưa có luồng | Chưa có public outpatient encounter/clinical workflow. |
| UC-028 | Record Clinical Conclusion | Chưa có luồng | Chưa có public outpatient encounter/clinical workflow. |
| UC-029 | Complete Encounter | Chưa có luồng | Chưa có public outpatient encounter/clinical workflow. |
| UC-030 | Amend Completed Clinical Record | Chưa có luồng | Chưa có public outpatient encounter/clinical workflow. |
| UC-031 | Create diagnostic service request | Chưa có luồng | Chưa có public diagnostic ordering/result/print workflow. |
| UC-032 | Update diagnostic service request | Chưa có luồng | Chưa có public diagnostic ordering/result/print workflow. |
| UC-033 | Cancel diagnostic service request | Chưa có luồng | Chưa có public diagnostic ordering/result/print workflow. |
| UC-034 | Print Diagnostic Order | Chưa có luồng | Chưa có public diagnostic ordering/result/print workflow. |
| UC-035 | View Diagnostic Results | Chưa có luồng | Chưa có public diagnostic ordering/result/print workflow. |
| UC-036 | Record Diagnostic Result | Chưa có luồng | Chưa có public diagnostic ordering/result/print workflow. |
| UC-037 | Finalize Diagnostic Result | Chưa có luồng | Chưa có public diagnostic ordering/result/print workflow. |
| UC-038 | Amend Final Diagnostic Result | Chưa có luồng | Chưa có public diagnostic ordering/result/print workflow. |
| UC-039 | Print Diagnostic Result to Patient | Chưa có luồng | Chưa có public diagnostic ordering/result/print workflow. |
| UC-040 | View Encounter Charges | Chưa có luồng | Chưa có public invoice/payment/refund/QR/reconcile/receipt workflow; batch payment summary không thay thế. |
| UC-041 | View Encounter Payment History | Chưa có luồng | Chưa có public invoice/payment/refund/QR/reconcile/receipt workflow; batch payment summary không thay thế. |
| UC-042 | Collect Encounter Payment | Chưa có luồng | Chưa có public invoice/payment/refund/QR/reconcile/receipt workflow; batch payment summary không thay thế. |
| UC-043 | Generate QR Payment | Chưa có luồng | Chưa có public invoice/payment/refund/QR/reconcile/receipt workflow; batch payment summary không thay thế. |
| UC-044 | Refund Payment | Chưa có luồng | Chưa có public invoice/payment/refund/QR/reconcile/receipt workflow; batch payment summary không thay thế. |
| UC-045 | Cancel Pending Payment | Chưa có luồng | Chưa có public invoice/payment/refund/QR/reconcile/receipt workflow; batch payment summary không thay thế. |
| UC-046 | Reconcile Payment | Chưa có luồng | Chưa có public invoice/payment/refund/QR/reconcile/receipt workflow; batch payment summary không thay thế. |
| UC-047 | Print Payment Receipt | Chưa có luồng | Chưa có public invoice/payment/refund/QR/reconcile/receipt workflow; batch payment summary không thay thế. |
| UC-048 | Create Prescription | Chưa có luồng | Chưa có public prescription draft/edit/issue-print workflow. |
| UC-049 | Edit Prescription | Chưa có luồng | Chưa có public prescription draft/edit/issue-print workflow. |
| UC-050 | Print Prescription | Chưa có luồng | Chưa có public prescription draft/edit/issue-print workflow. |
| UC-051 | View Appointment Schedule | Chưa có luồng | Chưa có public appointment schedule/booking/status workflow. |
| UC-052 | Create Appointment | Chưa có luồng | Chưa có public appointment schedule/booking/status workflow. |
| UC-053 | Reschedule Appointment | Chưa có luồng | Chưa có public appointment schedule/booking/status workflow. |
| UC-054 | Cancel Appointment | Chưa có luồng | Chưa có public appointment schedule/booking/status workflow. |
| UC-055 | Mark Appointment as No-show | Chưa có luồng | Chưa có public appointment schedule/booking/status workflow. |
| UC-056 | View Examination History | Chưa có luồng | Chưa có Patient Portal application/API cho own records. |
| UC-057 | View Examination Detail | Chưa có luồng | Chưa có Patient Portal application/API cho own records. |
| UC-058 | View Released Diagnostic Reports | Chưa có luồng | Chưa có Patient Portal application/API cho own records. |
| UC-059 | View Prescriptions | Chưa có luồng | Chưa có Patient Portal application/API cho own records. |
| UC-060 | View Appointments | Chưa có luồng | Chưa có Patient Portal application/API cho own records. |
| UC-061 | Update Contact Information | Chưa có luồng | Chưa có Patient Portal application/API cho own records. |
| UC-062 | Search Organizations | Một phần / lệch | [ListOrganizationUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/ListOrganizationUseCase.java) — Có search, nhưng chỉ ACTIVE và identity contract khác SRS. Điểm 7. |
| UC-063 | View Organization Details | Một phần / lệch | [GetOrganizationByIdUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/GetOrganizationByIdUseCase.java) — Có detail nhưng INACTIVE trả 404, không đáp ứng historical access. Điểm 7. |
| UC-064 | Create Organization | Một phần / lệch | [CreateOrganizationUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/CreateOrganizationUseCase.java) — Thiếu code/type của SRS; bắt thêm email/address/contactEmail. ADR-0016 khác SRS. Điểm 7. |
| UC-065 | Update Organization | Một phần / lệch | [UpdateOrganizationUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/UpdateOrganizationUseCase.java) — Không sửa status/reactivate; DELETE riêng chỉ deactivate. Điểm 7. |
| UC-066 | View Organization Health Examination Batches | Có luồng chính | [ListHealthExaminationBatchUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/ListHealthExaminationBatchUseCase.java) — Danh sách batch theo organization. |
| UC-067 | Create Health Examination Batch | Có luồng chính | [CreateHealthExaminationBatchUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/CreateHealthExaminationBatchUseCase.java) — Tạo DRAFT cùng days/services; Organization ACTIVE. |
| UC-068 | Update Health Examination Batch | Một phần / lệch | [UpdateHealthExaminationBatchUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/UpdateHealthExaminationBatchUseCase.java) — Chỉ DRAFT; cho organization INACTIVE update; khác SRS DRAFT/READY. Điểm 1,8. |
| UC-069 | Start Health Examination Batch | Một phần / lệch | [StartHealthExaminationBatchUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/StartHealthExaminationBatchUseCase.java) — DRAFT → READY, khác SRS READY → IN_PROGRESS. Điểm 1. |
| UC-070 | Close Health Examination Batch | Chưa có luồng | Domain có close(), chưa có Close application/API; state model khác SRS. |
| UC-071 | Cancel Health Examination Batch | Chưa có luồng | DELETE draft rỗng không phải cancel có reason/audit/history. |
| UC-072 | Update Batch Examination Services | Một phần / lệch | [UpdateHealthExaminationBatchUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/UpdateHealthExaminationBatchUseCase.java) — Gộp trong full PUT, chỉ DRAFT; thiếu service/price permission check theo loại thay đổi. Điểm 1,9. |
| UC-073 | View Batch Participants | Có luồng chính | [ListParticipantsUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/ListParticipantsUseCase.java) — Danh sách Participant theo batch, pagination/filter. |
| UC-074 | Download Participant Import Template | Có luồng chính | [GetParticipantImportTemplateUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/GetParticipantImportTemplateUseCase.java) — Download template XLSX theo batch/version. |
| UC-075 | Import Participant List | Một phần / lệch | [ImportParticipantsUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/ImportParticipantsUseCase.java) — Single-step/all-or-nothing có; validation dừng lỗi đầu tiên; SRS mâu thuẫn review/immediate. Điểm 6. |
| UC-076 | Update Batch Participant | Có luồng chính | [UpdateParticipantUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/UpdateParticipantUseCase.java) — Edit roster, version, CCCD lock khi linked, audit. |
| UC-077 | Remove Batch Participant | Có luồng chính | [CancelParticipantUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/CancelParticipantUseCase.java) — Cancel roster, giữ row/history, kiểm tra eligibility. |
| UC-078 | Add Batch Participant Manually | Có luồng chính | [CreateParticipantUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/CreateParticipantUseCase.java) — Manual add roster; không tự tạo Patient/Encounter. |
| UC-079 | Reactivate Batch Participant | Có luồng chính | [ReactivateParticipantUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/ReactivateParticipantUseCase.java) — Khôi phục Participant CANCELLED trên cùng row. |
| UC-080 | View Participant Health Examination Details | Một phần / lệch | [GetParticipantDetailUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/GetParticipantDetailUseCase.java) — Roster detail không trả clinical record/results/conclusion. Chỉ chức năng hỗ trợ gần nghĩa. Điểm 2. |
| UC-081 | Prepare Participant Health Examination Record | Chưa có luồng | Chưa có prepare/link Patient/Encounter và snapshot transaction. |
| UC-082 | Resolve Patient Data Conflict | Chưa có luồng | Chưa có compare/resolve roster–Patient conflict, permission và resolution audit. |
| UC-083 | Create Participant Health Examination Form | Chưa có luồng | Có on-demand render, chưa có Create/Save Form gắn prepared record/source/template versions. |
| UC-084 | Print Participant Health Examination Form | Một phần / lệch | [RenderParticipantExaminationFormUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/RenderParticipantExaminationFormUseCase.java) — Có PDF/bulk; không require prepared record/saved form và grants khác SRS. Điểm 4,5. |
| UC-085 | Enter Participant Examination Data | Một phần / lệch | [RecordExaminationDetailUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/RecordExaminationDetailUseCase.java) — Chỉ attendance/performed IDs, không nhập medical findings; grants cho manager. Điểm 2,5. |
| UC-086 | Import Batch Examination Data | Một phần / lệch | [ImportExaminationDetailsUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/ImportExaminationDetailsUseCase.java) — Import X/blank service reconciliation, không import findings/results vào clinical record. Điểm 2,5. |
| UC-087 | Review Examination Data Completion | Một phần / lệch | [GetExaminationSummaryUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/GetExaminationSummaryUseCase.java) — Chỉ đếm attendance/reconciliation, không clinical missing-required-data. Điểm 2. |
| UC-088 | Review Participant Examination Data | Một phần / lệch | [ListExaminationDetailsUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/ListExaminationDetailsUseCase.java) — Có ma trận dịch vụ; chưa review clinical record/results. Điểm 2. |
| UC-089 | Record Participant Health Examination Conclusion | Chưa có luồng | Chưa có clinical conclusion flow; role Nurse/GP đang mâu thuẫn BR-37. |
| UC-090 | Complete Participant Health Examination | Chưa có luồng | Chưa có clinical completion/finalization/completeness flow. |
| UC-091 | Issue Participant Health Examination Record | Chưa có luồng | Chưa có official issue/signature/version issuance flow. |
| UC-092 | Amend Issued Participant Health Examination Record | Chưa có luồng | Chưa có amendment flow của issued record. |
| UC-093 | Export Participant Examination Detail Report | Một phần / lệch | [ExportExaminationDetailsUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/ExportExaminationDetailsUseCase.java) — Excel người × dịch vụ, thiếu clinical details/results và quyền Nurse của SRS. Điểm 2,5. |
| UC-094 | Export Examination Service Summary Report | Chưa có luồng | Không có export service-summary riêng cho Nurse; payment DOCX chỉ là chức năng hỗ trợ liên quan. |
| UC-095 | Export Batch Data Completion Report | Chưa có luồng | Không có export medical data completion/missing-required-result report. |
| UC-096 | Export Batch Financial Summary | Có luồng chính | [ExportPaymentSummaryReportUseCase](../../src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/ExportPaymentSummaryReportUseCase.java) — DOCX tổng hợp performed count × historical price; chưa phải thu tiền invoice. |
| UC-097 | View Operations Dashboard | Chưa có luồng | Chưa có clinic-wide operational analytics/report application/API. |
| UC-098 | Analyze Patient Visit Volume | Chưa có luồng | Chưa có clinic-wide operational analytics/report application/API. |
| UC-099 | Analyze Encounter Outcomes | Chưa có luồng | Chưa có clinic-wide operational analytics/report application/API. |
| UC-100 | Analyze Service Utilization | Chưa có luồng | Chưa có clinic-wide operational analytics/report application/API. |
| UC-101 | Analyze Department Activity | Chưa có luồng | Chưa có clinic-wide operational analytics/report application/API. |
| UC-102 | Analyze Room Activity | Chưa có luồng | Chưa có clinic-wide operational analytics/report application/API. |
| UC-103 | Analyze Doctor Activity | Chưa có luồng | Chưa có clinic-wide operational analytics/report application/API. |
| UC-104 | Analyze Diagnostic Service Performance | Chưa có luồng | Chưa có clinic-wide operational analytics/report application/API. |
| UC-105 | Analyze Appointment Activity | Chưa có luồng | Chưa có clinic-wide operational analytics/report application/API. |
| UC-106 | Analyze Revenue | Chưa có luồng | Chưa có clinic-wide operational analytics/report application/API. |
| UC-107 | Analyze Payment Activity | Chưa có luồng | Chưa có clinic-wide operational analytics/report application/API. |
| UC-108 | Export Operational Reports | Chưa có luồng | Chưa có clinic-wide operational analytics/report application/API. |
| UC-109 | Manage Staff Profiles | Chưa có luồng | Chưa có public master-data administration workflow. |
| UC-110 | Manage Departments | Chưa có luồng | Chưa có public master-data administration workflow. |
| UC-111 | Configure Clinic Specialties | Chưa có luồng | Chưa có public master-data administration workflow. |
| UC-112 | Manage Rooms | Chưa có luồng | Chưa có public master-data administration workflow. |
| UC-113 | Manage Service Catalog | Một phần / lệch | [ListServiceCatalogUseCase](../../src/main/java/com/ngockhanh/clinic/catalog/application/usecase/ListServiceCatalogUseCase.java) — Chỉ read active services; chưa create/update/deactivate catalog. |
| UC-114 | Manage Service Prices | Chưa có luồng | Chưa có public master-data administration workflow. |
| UC-115 | Manage Medication Catalog | Chưa có luồng | Chưa có public master-data administration workflow. |
| UC-116 | Manage Document Templates | Xung đột phạm vi | SRS yêu cầu admin quản lý template nhưng ADR-0018 loại khỏi product scope. |
| UC-117 | Manage Notification Templates | Chưa có luồng | Chưa có notification-template management application/API. |
| UC-118 | Review Audit Logs | Chưa có luồng | Có audit write, chưa có authorized audit-log read/search application/API. |
| UC-119 | Prepare National Health Data Package | Chưa có luồng | Integration hiện phục vụ Excel import/history; chưa có national-health submission application/API. |
| UC-120 | Validate National Health Data Package | Chưa có luồng | Integration hiện phục vụ Excel import/history; chưa có national-health submission application/API. |
| UC-121 | Submit National Health Data | Chưa có luồng | Integration hiện phục vụ Excel import/history; chưa có national-health submission application/API. |
| UC-122 | View National Health Data Submission Status | Chưa có luồng | Integration hiện phục vụ Excel import/history; chưa có national-health submission application/API. |
| UC-123 | Review National Health Data Submission Error | Chưa có luồng | Integration hiện phục vụ Excel import/history; chưa có national-health submission application/API. |
| UC-124 | Retry National Health Data Submission | Chưa có luồng | Integration hiện phục vụ Excel import/history; chưa có national-health submission application/API. |
