# Kế hoạch CRUD đợt khám sức khỏe theo Organization

> Bản review của `ngoc-khanh-clinic-health-examination-batch-crud-implementation-plan.md` (30/09/2026). Đây là kế hoạch triển khai, không phải chỉ dẫn từ tài liệu đính kèm. Bản plan đã được triển khai; kiểm chứng toàn workspace còn bị chặn bởi các lỗi import được ghi ở nhật ký bên dưới.

## Mục tiêu và nguồn quyết định

Sau khi CRUD `Organization` ổn định, cho Front Desk và Quản lý bệnh viện tạo, xem, tìm, sửa và xóa mềm `HealthExaminationBatch` trong trạng thái `DRAFT`. Mỗi hạng mục trong đợt chỉ lưu một giá do người dùng nhập. Không tạo participant, Patient, Encounter, phiếu hay kết quả trong phase này.

Nguồn nghiệp vụ: `requirement-v2.5_FINAL.docx` FR-HC-009/010, BR-025/026; `use-case-v2.7_FINAL.docx` UC-HC-02/05; `table-design-v2.11_FINAL.docx` mục 4.33/4.34, 5.8, 7.1/7.7. Tên bảng/cột và kiểu PostgreSQL theo `V001__create_final_schema.sql`, ADR-0005/0006 và `docs/architecture/05-persistence-and-database.md`. Quy tắc dự án và ADR được ưu tiên khi tài liệu cũ còn gọi là `Company`/`HealthCheckBatch`. Quyết định mới của chủ dự án chỉ lưu một giá và thêm `DELETED` thay đổi giá tham chiếu/status trong bản FINAL; cập nhật hợp đồng dữ liệu khi triển khai migration.

## Kết quả review plan gốc

| Mức | Điểm cần sửa | Cơ sở |
| --- | --- | --- |
| Bắt buộc | Nhận `batchCode` khi tạo và cho sửa trong `DRAFT`; bỏ bộ sinh mã. UC-HC-05 cho người dùng nhập/chỉnh mã đợt, DB ràng buộc unique `(organization_id, batch_code)`. | Plan gốc mục 4.3, 9.3; UC-HC-05; V001 |
| Bắt buộc | `startDate`/`endDate` có thể trống trong `DRAFT`; chỉ kiểm tra thứ tự khi cả hai có giá trị. Ngày khám dự kiến của từng hồ sơ là một field khác ở phase sau. | Plan gốc mục 4.3, 5.4; mục 4.33; V001 |
| Bắt buộc | Dùng `CLINIC`/`COMPANY`, không dùng `ORGANIZATION`. Tên địa điểm bắt buộc; địa chỉ tại `COMPANY` phải có trước khi chuẩn bị/in hồ sơ, chưa bắt buộc lúc tạo nháp. | Plan gốc mục 5.4; mục 4.33; `ExaminationSiteType` hiện tại |
| Bắt buộc | Request chỉ nhận `serviceId` và một `negotiatedUnitPrice`. Backend lấy mã/tên dịch vụ từ catalog; bỏ `base_price_snapshot` bằng migration mới, giữ `displayOrder`, `currency`, trạng thái và template version. | Quyết định của chủ dự án thay FR-HC-010/mục 4.34 về giá tham chiếu |
| Bắt buộc | Danh sách dùng `page` bắt đầu từ **1**, `size` tối đa 100, `searchKey` là từ khóa và `sortKey` là tên field. Không có `searchBy` trong `BasePagination`. | Plan gốc mục 4.1; `BasePagination`, `PaginationConstants` |
| Bắt buộc | `DELETE` là xóa mềm, không phải nghiệp vụ hủy. Thêm `DELETED` vào status bằng migration mới; không đưa batch về `DRAFT` vì trạng thái đó vẫn cho sửa. | Quyết định của chủ dự án; plan gốc mục 4.5; V001 hiện chưa có `DELETED` |
| Bắt buộc | Java command dùng `createdBy` do controller truyền xuống; persistence map vào cột `created_by_user_id`. UUID mock trong local/test phải trỏ đến `users.id` có thật vì cột có FK. Authentication/phân quyền sẽ làm sau. | Quyết định của chủ dự án; V001 |
| Nên sửa | Tái sử dụng repository/mapper/record và `ApiResponse` hiện có. Plan gốc đề xuất nhiều port, mapper và file mới chưa cần thiết; `HealthExaminationBatchRepository.findById` hiện là read reference cho participant, không được phá caller này. | Plan gốc mục 8–10, 23; code hiện tại |
| Nên sửa | Không thêm index theo danh sách giả định. V001 đã có unique code, index `(organization_id,status,start_date)` và index batch-service. Migration mới chỉ thêm `DELETED` và bỏ `base_price_snapshot`. | Plan gốc mục 11; V001 |

## Quyết định đã chốt và giới hạn của phase này

1. **Một giá duy nhất:** mỗi service trong request chỉ có `negotiatedUnitPrice`, lưu vào `health_examination_batch_services.negotiated_unit_price`. Không lấy giá tham chiếu từ `service_prices`, không lưu `base_price_snapshot` và không cần `price_type` trong luồng batch CRUD. Bảng `service_prices` của catalog nằm ngoài thay đổi này.
2. **Người tạo:** `CreateHealthExaminationBatchCommand.createdBy` là `UUID`, được controller truyền xuống. Khi chưa có authentication, chỉ môi trường local/test dùng UUID mock trỏ tới một `users.id` fixture có thật; không nhận UUID này từ body. Sau khi có Spring Security, controller lấy cùng giá trị từ principal. Không xây RBAC trong phase CRUD này.
3. **Xóa:** `DELETE` chuyển `DRAFT -> DELETED`, giữ nguyên row và các service snapshots. Danh sách mặc định ẩn `DELETED`, detail/update trả 404; DELETE lặp lại trả 200 mà không ghi audit lần hai. Phase này không xóa batch đã rời `DRAFT` hoặc có dữ liệu phụ thuộc. `CANCELED` vẫn là trạng thái lịch sử của lifecycle hiện có, nhưng CRUD này không gọi `cancel(reason)`.

Không phát hành cấu hình UUID mock ra production. Profile production phải lấy actor thật hoặc từ chối mutation cho đến khi authentication hoàn thành.

## Contract đề xuất cho phase CRUD

Base path: `/api/v1/organizations/{organizationId}/health-examination-batches`.

| Method/path | Hành vi | Kết quả |
| --- | --- | --- |
| `POST /` | Tạo `DRAFT` trong organization đang hoạt động, lưu header và services cùng transaction. | `201`, `ApiResponse<BatchDetailResponse>` |
| `GET /` | Danh sách có phân trang, tìm theo mã/tên, lọc trạng thái nếu UI cần; luôn ẩn `DELETED`, thứ tự ổn định bằng `id` làm tie-breaker. | `200`, `ApiResponse<PageResponse<BatchSummaryResponse>>` |
| `GET /{batchId}` | Chi tiết và toàn bộ service snapshots; ràng buộc cả `organizationId` lẫn `batchId`, `DELETED` trả 404. | `200`, `ApiResponse<BatchDetailResponse>` |
| `PUT /{batchId}` | Thay cấu hình trong `DRAFT` cùng transaction; `DELETED` trả 404, trạng thái khác trả business conflict. | `200`, `ApiResponse<BatchDetailResponse>` |
| `DELETE /{batchId}` | Xóa mềm batch còn `DRAFT`; lưu `DELETED` và audit actor, không xóa row. | `200`, `ApiResponse<Void>` theo convention Organization hiện tại |

Không tạo lệnh `/cancel` trong phase này. Không dùng generic PUT để đổi `status` hoặc điều chỉnh giá sau `READY`.

Input create/PUT: `batchCode`, `batchName`, `startDate?`, `endDate?`, `reason?`, `payerType?`, `examinationSiteType`, `examinationSiteName`, `examinationSiteAddress?`, `services[] = {serviceId, negotiatedUnitPrice}`. `displayOrder` lấy theo thứ tự danh sách nếu UI không có thao tác sắp xếp riêng. Không nhận `id`, `organizationId`, `status`, tên/mã dịch vụ, timestamp hoặc `createdBy` từ body. Controller điền `createdBy` vào create command. Backend chọn và kiểm tra đúng version Mẫu số 03 đang có hiệu lực để lưu `master_template_version_id`; nếu cần chọn version thủ công, phải bổ sung contract UI/document rõ ràng trước khi thêm request field.

Danh sách dùng `BasePagination`: `page=1`, `size=10`, `size<=100`, `searchKey` tối đa 100 ký tự, `sortKey` allowlist và `sortBy=ASC|DESC`. Bắt đầu bằng allowlist nhỏ: `id`, `batchCode`, `batchName`, `startDate`, `status`, `createdAt`. Map field API sang SQL cố định; escape `%`, `_`, `\` trong keyword; không nối raw user input vào SQL.

`batchCode` unique trong một organization, cho phép trùng giữa hai organization. Không đổi `organizationId` khi PUT. `services` phải có ít nhất một mục, không trùng `serviceId`, giá không âm, service phải active và `health_examination_eligible=true`. Scope đã lưu là tập tối đa cho phase doctor-selection sau này. Đối với service còn giữ lại khi PUT, giữ `health_examination_batch_services.id`; không xóa toàn bộ rồi tạo ID mới. Thêm/bỏ service chỉ trong `DRAFT`; DB FK ngăn xóa service đã được tham chiếu. Mã của batch đã xóa vẫn được giữ chỗ bởi unique constraint hiện tại.

Response service dùng snapshot (`service_code_snapshot`, `service_name_snapshot`, `negotiated_unit_price`, `currency`, `display_order`, `status`, `document_template_version_id`), không đọc lại tên/giá hiện tại của catalog để trình bày lịch sử.

## Bước triển khai

### 1. Khóa contract còn thiếu và baseline

- [x] Catalog read contract chỉ trả mã/tên, trạng thái active và điều kiện dùng cho khám sức khỏe của các `serviceId` được chọn; không truy vấn bảng giá.
- [x] Chuẩn bị UUID actor fixture có row `users` tương ứng cho local/test; controller truyền `createdBy` vào create command. Production không dùng UUID mock.
- [!] Contract Organization được giữ nguyên trong scope này; các test Organization/import hiện có còn lỗi ngoài batch, nên kiểm chứng toàn workspace chưa hoàn tất.
- [x] Xác nhận document module có cách lấy đúng Mẫu số 03 effective version; nếu chưa có, thêm read contract tối thiểu qua public application API. Catalog cũng cần một public read contract, không truy cập mapper/table của module khác từ `healthexamination`.
- [x] Kiểm tra audit writer hiện có; nếu chưa có, bổ sung đường ghi `audit_logs` tối thiểu dùng chung cho create/update/delete trong cùng transaction.
- [x] Chốt request/response JSON và HTTP error status từ `ApiResponse`/global handler hiện có; ghi mẫu request vào API docs nếu repo có nơi duy trì.

### 2. Hoàn thiện aggregate và persistence đang có

**Sửa:** `HealthExaminationBatch.java`, `HealthExaminationBatchService.java`, `HealthExaminationBatchRepository.java`, `MyBatisHealthExaminationBatchRepository.java`, `HealthExaminationBatchMyBatisMapper.java`, `HealthExaminationBatchMyBatisMapper.xml`; tái sử dụng `HealthExaminationBatchRecord.java` và `HealthExaminationBatchServiceRecord.java`.

- [x] Bổ sung `batchName`, `reason`, `payerType` và các field snapshot/service order cần cho nghiệp vụ vào aggregate/child entity, bỏ `basePrice`, cùng `create/restore/updateDraft/deleteDraft` và validation; mở rộng `BatchStatus` bằng `DELETED` nhưng không đổi các transition nghiệp vụ hiện có. `createdBy` và timestamps được persistence/read model giữ đúng mà không ép mọi metadata thành domain state. Sửa `ExaminationSite` để địa chỉ COMPANY có thể thiếu khi còn là nháp, rồi chặn tại điểm chuẩn bị/in.
- [x] Giữ read reference `findById` cho `ListBatchParticipantUseCase`; thêm cách load full aggregate có scope organization cho mutation. Converter phải restore đủ services, site, template, status, lifecycle timestamp.
- [x] SQL insert/update/select header + services trong một transaction; PUT cập nhật existing service rows theo ID, thêm/bỏ rows đúng diff; kiểm tra trạng thái bằng row lock trước mutation để PUT không chạy xuyên qua chuyển trạng thái.
- [x] Test pure domain: code/name/date/site, service list rỗng/trùng, giá âm, edit/delete ngoài `DRAFT`, restore đủ state kể cả `DELETED`. Test PostgreSQL 18 Testcontainers: roundtrip, unique `(organization_id,batch_code)` khi POST và PUT, FK, diff service, rollback nếu một service lỗi, cross-organization lookup.

### 3. Use cases và API

**Thêm theo nhu cầu:** request/command/query/response và 4 use case create/list/get/update trong `healthexamination`; `OrganizationBatchController` với Javadoc endpoint. Dùng `OrganizationRepository`, `ApiResponse`, `PageResponse`, pagination và global exception handler hiện có.

- [x] Create: xác nhận organization ACTIVE; controller truyền `createdBy`, use case lấy service code/name/eligibility và Mẫu số 03 version qua public contracts; tạo UUIDv7; lưu giá nhập, header, services và audit atomically. Conflict mã đợt trùng phải được trả có kiểm soát cả khi hai request đua nhau.
- [x] List/detail: `WHERE organization_id = ?`, chi tiết dùng cả hai ID; organization không tồn tại trả 404, organization có danh sách rỗng trả trang rỗng. Query list chỉ lấy field cần hiển thị, không load full aggregate cho mỗi dòng.
- [x] Update: chỉ `DRAFT`, không thay ID/organization hay audit fields; lấy lại catalog snapshot cho service mới, giữ snapshot của service không đổi, dùng transaction ngắn và kiểm tra số hàng cập nhật.
- [x] Test use case cho success/invalid/duplicate/cross-organization/rollback; controller test cho validation, envelope, status code và truyền `createdBy`; Testcontainers xác nhận UUID mock có user fixture, UUID không tồn tại bị FK từ chối. Kiểm tra endpoint không log dữ liệu bệnh nhân hoặc toàn body. Test 401/403 thuộc phase authentication sau.

### 4. Xóa mềm đợt khám nháp

- [x] Thêm `V003__simplify_health_examination_batch_price_and_status.sql`: mở rộng status check cho `DELETED`, bỏ check constraint của `base_price_snapshot` rồi bỏ cột đó. Không sửa V001. Ghi cả hai thay đổi vào phiên bản tiếp theo của hợp đồng dữ liệu; xác nhận tác động dữ liệu trước khi áp dụng trên DB đã có batch. Repository chỉ update status `DELETED`, không xóa header/service rows.
- [x] `DELETE /{batchId}` chỉ áp dụng cho `DRAFT` chưa có dữ liệu phụ thuộc; audit actor/thời điểm. Delete loader vẫn nhận ra `DELETED` để trả kết quả idempotent, còn list/detail/PUT không thấy batch đã xóa. DELETE lặp không tạo audit mới; batch khác organization trả 404.
- [x] Test PostgreSQL 18 cho migration, `base_price_snapshot` không còn tồn tại, soft delete giữ rows/FK và mã batch; test API cho DELETE thành công/lặp/sai organization/trạng thái cấm. Cập nhật persistence record/schema contract test để chỉ còn `negotiated_unit_price`.

### 5. Kiểm chứng và chuyển sang phase sau

- [ ] Chạy `./mvnw.cmd test` và `./mvnw.cmd verify`, gồm PostgreSQL 18 Testcontainers và Spring Modulith verification. Ghi rõ check nào không chạy được nếu Docker unavailable.
- [x] Chạy flow: tạo Organization → tạo DRAFT có service/giá → list/detail → PUT DRAFT → detail vẫn giữ ID/snapshot service cũ → DELETE → list/detail không còn thấy batch → PUT bị từ chối → truy cập bằng organization khác trả 404.
- [x] Áp dụng V001, V002 và V003 trên PostgreSQL 18 mới; không sửa V001. Nếu kiểm chứng phát hiện gap schema khác, thêm migration tiếp theo thay vì sửa migration đã áp dụng.

## Ngoài phase CRUD nhưng cần trước khi dùng batch cho participant

`READY` cần một command riêng để khóa scope/template và freeze specialist template versions từ `service_template_mappings`; không thể coi CRUD nháp là đủ cho quy trình participant. Giá sau `READY` dùng command repricing riêng có quyền, lý do, audit và cập nhật đồng thời batch price, assignment snapshot và ServiceRequest snapshot theo FR-HC-010/mục 7.7. Không để generic PUT làm việc này. Participant import, Patient/Encounter/SHS, result và báo cáo vẫn ở phase sau.

## Rủi ro còn lại

- Chưa có runtime principal/authorization contract và resolver catalog/document trong code hiện tại. UUID mock chỉ phục vụ local/test; API mutation chưa đủ điều kiện phát hành cho production.
- Batch table chưa có `row_version`; row lock bảo vệ transaction và status race, nhưng hai màn chỉnh sửa nháp cũ vẫn có thể ghi đè tuần tự. Nếu sản phẩm cần trả 409 khi chỉnh trên dữ liệu cũ, thêm concurrency token bằng migration và API precondition được duyệt.
- Xóa mềm batch sau `DRAFT` hoặc sau khi có participant/hồ sơ chuẩn bị chưa thuộc phase này; cần quy tắc nghiệp vụ riêng trước khi mở rộng.

## Nhật ký triển khai 2026-09-30

- Ruling: triển khai trên checkout hiện tại để giữ các thay đổi Organization/import mà batch phụ thuộc; không chuyển sang checkout thiếu các thay đổi chưa commit.
- Ruling: dùng V003 vì V002 đã được phần roster import sử dụng. V001 không được sửa.
- Ruling: catalog retirement không thay snapshot của service đã giữ lại; eligibility được kiểm tra cho hạng mục mới chọn.
- Đã thêm API CRUD, use cases, domain operations, full aggregate restore, bulk service upsert/diff và audit cùng transaction.
- Đã thêm public contracts trong catalog/document, actor mock local/test với local user fixture, và API docs.
- Reviewer độc lập không thấy blocker chức năng; đã bổ sung test rollback khi audit thất bại cùng test thêm/bỏ service và conflict PUT.
- Kiểm chứng workspace chung đang bị chặn bởi thay đổi import chưa hoàn tất: HealthExaminationImportRow dùng Lombok getters nhưng caller vẫn dùng fluent accessors.
- Bản sao kiểm chứng nằm dưới target/batch-validation-20260930. Chỉ bản sao có getter tương thích và bỏ class-level Builder chưa hoàn tất của import; source import trong workspace không bị sửa bởi task batch.
- Full-suite trong bản sao còn lỗi ngoài batch ở ListOrganizationsUseCaseTest, ListBatchParticipantUseCaseTest và wiring EncryptedLocalImportFileStorage; suite batch dùng Spring context chỉ chứa feature cần kiểm chứng.

## Kết quả kiểm chứng

- Trong workspace chung, cả mvnw test và mvnw verify đã chạy nhưng dừng ở compile vì phần import hiện có thiếu fluent getters và class-level Builder chưa khớp constructors.
- Trong bản sao kiểm chứng có bổ sung compatibility cho phần import đang sửa, feature-scoped mvnw verify thành công: 129 tests, 0 failures/errors/skips, gồm 5 test PostgreSQL 18 cho batch, Flyway migration chain và Spring Modulith.
- Test PostgreSQL xác nhận snapshot, thêm/bỏ hạng mục, giữ service IDs, giá nhập, unique-code conflict, soft-delete idempotency, creator FK, local actor fixture và rollback header/services khi audit lỗi.
- Không áp dụng migration lên database của người dùng trong task này; Flyway đã được chạy trên PostgreSQL Testcontainers.
- Logs: target/batch-maven-test.log, target/batch-maven-verify.log, target/batch-validation-20260930/batch-scoped-verify-final.log.
- Full workspace vẫn cần sửa phần import và chạy lại test/verify trước khi merge/release.

## Files của implementation batch

Các file đã tồn tại có thay đổi của Organization/import trước task được giữ nguyên về phần không liên quan. Danh sách dưới đây chỉ ghi phần batch được thêm/sửa.

- `src/main/java/com/ngockhanh/clinic/catalog/application/ServiceCatalogQuery.java`
- `src/main/java/com/ngockhanh/clinic/catalog/application/package-info.java`
- `src/main/java/com/ngockhanh/clinic/catalog/infrastructure/persistence/mapper/ServiceCatalogMapper.java`
- `src/main/java/com/ngockhanh/clinic/catalog/infrastructure/persistence/repository/MyBatisServiceCatalogQuery.java`
- `src/main/java/com/ngockhanh/clinic/document/application/MasterHealthExaminationTemplateQuery.java`
- `src/main/java/com/ngockhanh/clinic/document/application/package-info.java`
- `src/main/java/com/ngockhanh/clinic/document/infrastructure/persistence/mapper/MasterHealthExaminationTemplateMapper.java`
- `src/main/java/com/ngockhanh/clinic/document/infrastructure/persistence/repository/MyBatisMasterHealthExaminationTemplateQuery.java`
- `src/main/java/com/ngockhanh/clinic/shared/audit/AuditWriter.java`
- `src/main/java/com/ngockhanh/clinic/shared/audit/package-info.java`
- `src/main/java/com/ngockhanh/clinic/shared/infrastructure/persistence/mapper/AuditLogMapper.java`
- `src/main/java/com/ngockhanh/clinic/shared/infrastructure/persistence/repository/MyBatisAuditWriter.java`
- `src/main/java/com/ngockhanh/clinic/shared/exception/BusinessRuleException.java`
- `src/main/java/com/ngockhanh/clinic/shared/web/GlobalExceptionHandler.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/api/controller/OrganizationBatchController.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/api/request/HealthExaminationBatchRequest.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/api/request/HealthExaminationBatchListRequest.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/application/command/BatchConfigurationCommand.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/application/command/CreateHealthExaminationBatchCommand.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/application/query/HealthExaminationBatchListQuery.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/application/response/BatchDetailResponse.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/application/response/BatchSummaryResponse.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/BatchDraftEditor.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/CreateHealthExaminationBatchUseCase.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/GetHealthExaminationBatchUseCase.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/ListHealthExaminationBatchUseCase.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/UpdateHealthExaminationBatchUseCase.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/DeleteHealthExaminationBatchUseCase.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/domain/aggregate/HealthExaminationBatch.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/domain/entity/HealthExaminationBatchService.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/domain/enums/BatchStatus.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/domain/repository/HealthExaminationBatchRepository.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/domain/valueobject/ExaminationSite.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/domain/valueobject/Money.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/infrastructure/persistence/mapper/HealthExaminationBatchMyBatisMapper.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/infrastructure/persistence/repository/MyBatisHealthExaminationBatchRepository.java`
- `src/main/java/com/ngockhanh/clinic/healthexamination/infrastructure/persistence/record/HealthExaminationBatchServiceRecord.java`
- `src/main/resources/mapper/health-examination/HealthExaminationBatchMyBatisMapper.xml`
- `src/main/resources/db/migration/V003__simplify_health_examination_batch_price_and_status.sql`
- `src/main/resources/db/local/R__batch_mock_actor.sql`
- `src/main/resources/application-local.yaml`
- `src/test/java/com/ngockhanh/clinic/healthexamination/domain/HealthExaminationBatchCrudTest.java`
- `src/test/java/com/ngockhanh/clinic/healthexamination/domain/HealthExaminationDomainTest.java`
- `src/test/java/com/ngockhanh/clinic/healthexamination/application/usecase/BatchDraftEditorTest.java`
- `src/test/java/com/ngockhanh/clinic/healthexamination/application/usecase/HealthExaminationBatchUseCasesTest.java`
- `src/test/java/com/ngockhanh/clinic/healthexamination/api/controller/OrganizationBatchControllerTest.java`
- `src/test/java/com/ngockhanh/clinic/healthexamination/infrastructure/persistence/HealthExaminationBatchMapperTest.java`
- `src/test/java/com/ngockhanh/clinic/healthexamination/infrastructure/persistence/HealthExaminationBatchCrudIntegrationTest.java`
- `src/test/java/com/ngockhanh/clinic/infrastructure/migration/HealthExaminationDatabaseMigrationIntegrationTest.java`
- `src/test/java/com/ngockhanh/clinic/infrastructure/persistence/record/PersistenceRecordContractTest.java`
- `docs/api/health-examination-batches.md`
- `docs/architecture/05-persistence-and-database.md`
