# Kế hoạch hoàn thiện 5 Organization Use Case

> Ngày lập: 2026-10-05. Trạng thái: kế hoạch, chưa triển khai.
> Khi thực thi: dùng `superpowers:executing-plans` theo từng task; chỉ dùng subagent khi được cho phép. Checklist dùng để theo dõi công việc thực tế.

**Mục tiêu:** hoàn thiện `CreateOrganizationUseCase`, `DeleteOrganizationUseCase`, `GetOrganizationByIdUseCase`, `ListOrganizationUseCase`, `UpdateOrganizationUseCase` trong module `healthexamination`.

**Kiến trúc:** controller nhận HTTP request và gọi use case; application điều phối domain, repository và audit; infrastructure thực thi MyBatis. Giữ các implementation đã có, bổ sung phần thiếu và test đúng hành vi. Không tạo generic CRUD service.

**Stack:** Java 25, Spring Boot 4 / Framework 7, PostgreSQL 18, MyBatis, Spring Modulith, Maven, Flyway. IDE báo Spring Boot 4.1.1 và MyBatis starter 4.1.0 tại thời điểm khảo sát.

**Nguồn contract:** [PROJECT_RULES](../../PROJECT_RULES.md), [PROJECT_SKILLS](../../PROJECT_SKILLS.md), [ADR-0012](../adr/0012-clean-slate-module-boundaries.md), [ADR-0013](../adr/0013-clean-slate-application-contract.md), [domain workflows](../architecture/03-domain-and-workflows.md), [API/security](../architecture/05-api-and-security.md), [HTTP inventory](../api/clean-slate-migration.md), [V001](../../src/main/resources/db/migration/V001__create_clean_slate_schema.sql).

## 1. Hiện trạng và phạm vi

| Use case | Hiện trạng | Công việc |
|---|---|---|
| CreateOrganizationUseCase | Đã tạo domain, kiểm tra trùng code, insert và audit trong transaction | Giữ flow; bổ sung kiểm chứng validation, race unique-code, audit rollback, access và Javadoc/logging |
| GetOrganizationByIdUseCase | Đã đọc theo UUID, trả OrganizationResponse, transaction read-only | Hoàn thiện test và contract lỗi; đồng bộ tên test với tên use case |
| UpdateOrganizationUseCase | Đã kiểm tra expected version, kiểm tra code, update, reload và audit | Giữ flow; kiểm chứng concurrent update, version trả về, audit rollback và HTTP/security |
| ListOrganizationUseCase | Class rỗng; ListOrganizationRequest và ListOrganizationCommand cũng rỗng | Định nghĩa input/read contract, truy vấn phân trang và GET collection |
| DeleteOrganizationUseCase | Class rỗng, chưa có HTTP handler | Chốt nghĩa của delete; sau đó triển khai mutation có expected version và audit |

`OrganizationRepository` hiện có `findById`, `existsByCode`, `save`, `update`; chưa có list. `Organization.deactivate()` đã tồn tại. Mapper update đã so sánh `row_version` và tăng version trong SQL.

Checkout có nhiều thay đổi chưa commit của người dùng, bao gồm rename Get và các class rỗng trên. Không ghi đè, reset hoặc khôi phục file đã xóa. API inventory ghi nhận một số batch caller/test còn tham chiếu class đã xóa; phải kiểm tra lại baseline trước khi thực thi, không tự mở rộng task này thành sửa toàn bộ Batch.

Phạm vi là Organization. Không thêm Batch/Participant/import workflow, frontend, cơ chế RBAC mới hoặc chuyển stack persistence. Tài liệu này không chứng nhận các implementation hiện tại đã build/test thành công.

## 2. Ràng buộc chung

- Module sở hữu: `healthexamination`; audit qua published contract `audit::recording` / `AuditWriter`. Không truy cập infrastructure của module khác.
- Domain không phụ thuộc HTTP, Spring hay MyBatis; application không import `api.*`; controller không chứa SQL, transaction hoặc business rule.
- Dùng `OrganizationRecord` hiện có cho bảng `organizations`. Projection khác hình dạng bảng phải nằm ở `infrastructure/persistence/view`.
- `code` unique; `taxCode` optional và không unique. Không tự thêm chuẩn hóa hoa/thường hoặc unique tax code.
- `organizationType`: `COMPANY`, `SCHOOL`, `GOVERNMENT`, `OTHER`; `status`: `ACTIVE`, `INACTIVE`.
- Business write và required audit dùng cùng transaction; audit lỗi phải rollback cả hai. Actor là account ID lấy từ principal, không lấy từ request và không dùng fallback actor.
- Backend giữ nguyên production deny policy; local/test chỉ cho STAFF có active role. Không thêm `@PreAuthorize` vào các use case health-examination trái contract hiện tại.
- HTTP filter chain là boundary được hỗ trợ hiện tại. Trước khi bổ sung caller ngoài HTTP phải có contract authorization ở application; một UUID actor không chứng minh quyền.
- Dùng `ApiResponse`, `PageResponse`, `PaginationConstants` và exception mapping hiện có. Không thêm wire field lỗi mới.
- Log chỉ metadata cần thiết; không log request, command, search text, tên, điện thoại hay email. Log trong transaction không khẳng định đã commit.
- Dùng wrapper cho nullable input, kiểm tra null trước unboxing; so sánh wrapper theo giá trị. Business collection dùng `List<T>`.
- Không sửa V001 đã áp dụng. Không cần migration cho đề xuất phân trang + deactivate; chỉ tạo migration mới nếu một thay đổi schema được chốt riêng.

## 3. Contract dùng làm đầu vào

### 3.1. Create / Get / Update đã được hỗ trợ

| API | Input | Output thành công |
|---|---|---|
| POST `/api/v1/organizations` | CreateOrganizationRequest | 201, ApiResponse chứa OrganizationResponse |
| GET `/api/v1/organizations/{organizationId}` | UUID path | 200, ApiResponse chứa OrganizationResponse |
| PUT `/api/v1/organizations/{organizationId}` | UUID path + UpdateOrganizationRequest | 200, ApiResponse chứa OrganizationResponse với version sau update |

Các field Create/Update giữ nguyên: `code`, `name`, `organizationType`, `taxCode`, `phone`, `email`, `address`, `contactFullName`, `contactPosition`, `contactPhone`, `contactEmail`. Update thêm `rowVersion` bắt buộc, không âm.

Giới hạn hiện tại: code 50, name 300, taxCode 50, contactFullName 200, contactPosition 200 ký tự. Các kênh liên hệ và address bắt buộc; email/contactEmail được validate ở HTTP. Optional blank được domain chuyển thành null. Không tự thêm giới hạn độ dài cho cột text.

Create khởi tạo `ACTIVE`, version 0, UUIDv7. Update thay đầy đủ các field request, giữ status và ID; không biến PUT thành partial update. Get hiện trả cả ACTIVE và INACTIVE; không tự đổi INACTIVE thành 404.

Response tiếp tục gồm ID, các field Organization, status và rowVersion; không tự thêm timestamp hoặc dữ liệu Batch.

### 3.2. List — đề xuất cần ghi nhận thành contract trước khi thêm handler

Đề xuất `GET /api/v1/organizations`, trả `ApiResponse<PageResponse<OrganizationResponse>>` với HTTP 200.

| Parameter | Đề xuất |
|---|---|
| page | Default 1, min 1; HTTP input nullable để áp dụng default |
| size | Default 10, min 1, max 100 |
| searchKey | Optional; trim, blank tương đương không tìm kiếm; tìm trên code/name |
| sortKey | Default id; allowlist id/code/name |
| sortBy | Default ASC; chỉ ASC/DESC |

- MVP không thêm filter type/status. Đề xuất trả cả ACTIVE/INACTIVE, status có trong từng item; nếu cần default ACTIVE-only phải chốt lại trước khi viết SQL/test.
- Đề xuất search không phân biệt hoa/thường, coi `%` và `_` trong input là ký tự literal. Chốt giới hạn searchKey trước implementation; không dùng wildcard không giới hạn làm public contract ngầm.
- Sort bằng code/name thêm ID làm tie-breaker theo cùng chiều. Sort mặc định theo ID. Không ghép tên cột từ input vào SQL.
- Page vượt cuối trả items rỗng nhưng giữ totalElements/totalPages của kết quả lọc. Dataset rỗng có totalElements = 0, totalPages = 0.
- Offset tính bằng kiểu đủ lớn và chặn overflow. Count và select dùng cùng predicate. Không load toàn bộ bảng hoặc gọi findById cho từng item.

### 3.3. Delete — quyết định nghiệp vụ còn thiếu

**Khuyến nghị:** `DeleteOrganizationUseCase` ngừng hoạt động Organization bằng `ACTIVE → INACTIVE` qua `Organization.deactivate()`, không xóa row hoặc cascade Batch. Lý do: giữ liên kết/lịch sử, tận dụng status và domain behavior hiện có; FK từ Batch là `ON DELETE RESTRICT`.

Đây là **đề xuất**, chưa phải workflow được chấp nhận. Sự tồn tại của `deactivate()` hoặc status trong schema không tự tạo quyền thêm endpoint. Trước Task 5 phải chốt:

1. Delete là deactivate hay hard delete. Kế hoạch chi tiết bên dưới chỉ áp dụng cho deactivate; hard delete cần thiết kế riêng về references và lịch sử.
2. Route đề xuất `DELETE /api/v1/organizations/{organizationId}?rowVersion=<expected>`; version bắt buộc, không âm. Nếu nghiệp vụ chọn action route khác phải cập nhật plan và inventory trước implementation.
3. Response đề xuất 204, body rỗng; không bọc ApiResponse cho 204. ID không tồn tại trả 404; stale version trả 409.
4. Đề xuất check version trước kiểm tra trạng thái: INACTIVE + version hiện tại là no-op 204, không tăng version/không thêm audit; stale version vẫn 409. Client muốn retry sau mutation cần đọc lại version.
5. Có cho deactivate khi đang có Batch hay không, và tác động lên việc tạo Batch mới. Đề xuất giữ nguyên Batch/lịch sử và Get; không tự sửa workflow Batch. Nếu yêu cầu chặn theo trạng thái Batch, phải có contract phối hợp và xử lý race trước khi làm.
6. Audit action đề xuất `DEACTIVATE_ORGANIZATION`, resource `ORGANIZATION`, before/after chứa status và rowVersion; actor account ID thật. Không suy diễn quyền production mới từ route này.

**Điểm dừng có phạm vi:** contract Delete chưa chốt thì không thực hiện Task 5. Contract List chưa chốt thì không thêm public handler List. Vẫn tiếp tục Create/Get/Update và verification độc lập; không làm giả success.

## 4. Thứ tự triển khai và cách kiểm thử

Thứ tự: kiểm tra baseline/chốt contract → Create → Get → Update → List → Delete → verification chung. List/Delete có thể chốt contract trong khi hoàn thiện ba use case đã hỗ trợ.

Đề xuất test-first cho behavior còn thiếu: thêm test, chạy thấy đúng failure, sửa tối thiểu, chạy lại. Đây là đề xuất của plan, không phải lựa chọn testing đã được người dùng xác nhận. Mỗi task code phải có test success và failure phù hợp trước khi chuyển tiếp.

Quy ước đường dẫn trong các task:

- `M` = `src/main/java/com/ngockhanh/clinic/healthexamination/`
- `T` = `src/test/java/com/ngockhanh/clinic/healthexamination/`
- `XML` = `src/main/resources/mapper/healthexamination/OrganizationMyBatisMapper.xml`

### Task 0: Kiểm tra baseline và chốt phần mở rộng

**Files:** đọc PROJECT_RULES, nguồn contract ở đầu plan và `docs/maintenance/code-follow-ups.md`; cập nhật chính file plan này khi quyết định được chốt.

- [ ] Ghi lại git status, khảo sát lại bằng CodeGraph; xác nhận các class/user edits chưa đổi so với snapshot.
- [ ] Chạy `.\mvnw.cmd verify` để có baseline. Nếu lỗi compile vì caller Batch đang dở, ghi đúng file/lỗi và xử lý dependency với chủ sở hữu; không sửa/xóa test để cho xanh.
- [ ] Ghi quyết định cụ thể cho List và sáu điểm của Delete vào plan; cập nhật domain/API contract tương ứng trước khi thêm public behavior.
- [ ] Chốt caller được hỗ trợ: HTTP qua security filter chain hiện có; không công bố direct application API đã được authorize chỉ vì có actor.

### Task 1: Hoàn thiện CreateOrganizationUseCase

**Files:**

- Modify: `M/application/usecase/CreateOrganizationUseCase.java`.
- Modify khi cần: `M/api/request/CreateOrganizationRequest.java`, `M/application/command/CreateOrganizationCommand.java`, `M/api/controller/OrganizationController.java`.
- Test: `T/application/usecase/CreateOrganizationUseCaseTest.java`, `T/api/controller/OrganizationControllerTest.java`, `T/infrastructure/persistence/MyBatisOrganizationRepositoryIntegrationTest.java`.

**Interface giữ nguyên:** `OrganizationResponse execute(CreateOrganizationCommand command, UUID actor)`.

- [ ] Bổ sung test cho required/blank, giới hạn field, type sai, optional null, command/actor null; HTTP email sai trả 400, không write/audit.
- [ ] Giữ flow domain create → existsByCode → save → audit → response; không thêm unique taxCode hoặc cơ chế idempotency chưa có contract.
- [ ] Thêm test `rejectsDuplicateCodeWithoutAudit` và PostgreSQL test `concurrentCreatesWithSameCodeProduceOneOrganization`: chỉ một insert thành công; request thua nhận conflict an toàn, không để audit mồ côi.
- [ ] Giữ/mở rộng `auditFailureRollsBackOrganizationInsertAndAuditRow` với real PostgreSQL và Spring transaction; kiểm tra response ACTIVE/version 0 và audit actor chính xác.
- [ ] Thêm English Javadoc class/execute, mô tả đúng guarantees; đổi log success trong transaction thành wording commit pending, không log contacts.
- [ ] Chạy nhóm test Create + controller + repository; success/duplicate/rollback đều có evidence, báo rõ Docker skip.

### Task 2: Hoàn thiện GetOrganizationByIdUseCase

**Files:**

- Modify: `M/application/usecase/GetOrganizationByIdUseCase.java`, `M/api/controller/OrganizationController.java` khi cần.
- Rename test sau khi kiểm tra references: `T/application/usecase/GetOrganizationUseCaseTest.java` → `T/application/usecase/GetOrganizationByIdUseCaseTest.java`.
- Test: `T/api/controller/OrganizationControllerTest.java`.

**Interface giữ nguyên:** `OrganizationResponse execute(UUID id)`.

- [ ] Test `returnsOrganizationIncludingInactiveStatus`: đúng tất cả field, status/version, không lộ persistence type; không tự load Batch.
- [ ] Test ID null ở application; UUID sai ở HTTP trả 400; ID không có trả 404 với thông báo an toàn.
- [ ] Giữ transaction read-only và mapping `OrganizationResponse.from`; không thêm audit mutation cho thao tác đọc.
- [ ] Cập nhật class/test/caller theo tên ById đã được người dùng chọn; thêm Javadoc với param/return/not-found.
- [ ] Chạy test Get và controller; xác nhận đọc không làm tăng version hoặc thay dữ liệu.

### Task 3: Hoàn thiện UpdateOrganizationUseCase

**Files:**

- Modify: `M/application/usecase/UpdateOrganizationUseCase.java`; request/command/controller tương ứng chỉ khi có gap.
- Modify adapter/mapper/XML chỉ nếu test phát hiện SQL hoặc translation sai.
- Test: `T/application/usecase/OrganizationCrudUseCaseTest.java`, `T/api/controller/OrganizationControllerTest.java`, `T/infrastructure/persistence/MyBatisOrganizationRepositoryIntegrationTest.java`.

**Interface giữ nguyên:** `OrganizationResponse execute(UUID id, UpdateOrganizationCommand command, UUID actor)`.

- [ ] Test rowVersion null/âm ở HTTP trả 400; stale version ở application trả conflict trước mutation; null id/command/actor không write.
- [ ] Giữ updateDetails, exclude current ID khi kiểm tra code, update theo expected version, reload version mới; PUT giữ nguyên status/ID và cho phép xóa optional field bằng null.
- [ ] Bổ sung `rejectsVersionLostBetweenReadAndWrite` và PostgreSQL test hai writes cùng expected version: một thành công, một 409; không mất dữ liệu/audit.
- [ ] Test code giữ nguyên thành công, code thuộc Organization khác trả 409 kể cả race tại unique constraint; không áp unique cho taxCode.
- [ ] Giữ/mở rộng `auditFailureRollsBackOrganizationUpdateAndAuditRow`; kiểm tra rowVersion chỉ tăng một lần, response/audit dùng version đã lưu.
- [ ] Hoàn thiện Javadoc/logging và chạy test Update + controller + repository. Không claim transaction rollback từ Mockito test.

### Task 4: Triển khai ListOrganizationUseCase sau khi chốt 3.2

**Files:**

- Modify: `M/api/request/ListOrganizationRequest.java`, `M/application/command/ListOrganizationCommand.java`, `M/application/usecase/ListOrganizationUseCase.java`, `M/api/controller/OrganizationController.java`.
- Create: `M/domain/repository/OrganizationSearchCriteria.java`, `M/domain/repository/OrganizationPage.java`.
- Modify: `M/domain/repository/OrganizationRepository.java`, `M/infrastructure/persistence/repository/MyBatisOrganizationRepository.java`, `M/infrastructure/persistence/mapper/OrganizationMyBatisMapper.java`, XML.
- Test create: `T/application/usecase/ListOrganizationUseCaseTest.java`; extend controller/repository integration tests.

**Interfaces đề xuất:**

- `ListOrganizationCommand(Integer page, Integer size, String searchKey, String sortKey, String sortBy)`; giữ input file đang có, không rename chỉ để đổi thuật ngữ.
- `PageResponse<OrganizationResponse> ListOrganizationUseCase.execute(ListOrganizationCommand command)`.
- `OrganizationSearchCriteria(int page, int size, String searchKey, String sortKey, String sortBy)` là input đã chuẩn hóa/validate, không chứa annotation HTTP/MyBatis.
- `OrganizationPage(List<Organization> items, long totalElements)` có defensive copy; `OrganizationRepository.search(OrganizationSearchCriteria criteria)` trả kiểu này.
- Adapter có `search` + `count` trong mapper; dùng OrganizationRecord và converter hiện có vì đọc đủ shape Organization, không tạo projection thừa.

- [ ] Test defaults 1/10/id/ASC, null command, page 0, size 0/101, sort sai và sortKey ngoài allowlist; HTTP lỗi trả 400. Cùng validation application bảo vệ caller được hỗ trợ, không nhờ riêng HTTP.
- [ ] Implement request → command tại controller; application chuẩn hóa input một lần thành criteria, gọi repository và map sang PageResponse.
- [ ] Thêm count/select với predicate giống nhau, explicit columns, bound params, LIMIT/OFFSET, allowlisted ORDER BY và ID tie-breaker. Không truyền `${sortKey}`/`${searchKey}` trực tiếp vào SQL.
- [ ] PostgreSQL tests: empty dataset, page vượt cuối, tổng sau filter, tên trùng qua nhiều page, ASC/DESC, tiếng Việt, search blank và literal `%`/`_`; áp đúng semantics đã chốt.
- [ ] Test endpoint GET collection trả đúng envelope/items/page/size/totalElements/totalPages; không query từng item và không log search text.
- [ ] Thêm Javadoc, chạy List/controller/repository tests; cập nhật inventory GET collection chỉ sau khi handler thực sự được thêm.

### Task 5: Triển khai DeleteOrganizationUseCase sau khi chốt 3.3

**Files:**

- Modify: `M/application/usecase/DeleteOrganizationUseCase.java`, `M/api/controller/OrganizationController.java`.
- Create: `M/api/request/DeleteOrganizationRequest.java`, `M/application/command/DeleteOrganizationCommand.java`.
- Reuse: `M/domain/aggregate/Organization.java` (`deactivate`), OrganizationRepository.update và SQL versioned update hiện có.
- Test create: `T/application/usecase/DeleteOrganizationUseCaseTest.java`; extend controller/repository integration tests.
- Modify: `docs/architecture/03-domain-and-workflows.md`, `docs/api/clean-slate-migration.md` theo quyết định đã chốt.

**Interfaces cho đề xuất deactivate:** `DeleteOrganizationCommand(Long rowVersion)`; `void DeleteOrganizationUseCase.execute(UUID id, DeleteOrganizationCommand command, UUID actor)`. Request nhận query rowVersion và validate required/non-negative tại HTTP boundary.

- [ ] Test not-found/null input, version thiếu/âm, stale version; không update/audit khi bị từ chối.
- [ ] Transaction: load → check expected version → nếu đã INACTIVE thì no-op theo contract → domain deactivate → repository.update(expected) → reload → audit before/after. Không gọi SQL DELETE.
- [ ] Test `deactivatesWithoutRemovingOrganizationOrBatchHistory`: row tồn tại, status INACTIVE, version tăng một lần, Get vẫn trả dữ liệu; hành vi với Batch tuân đúng quyết định ở 3.3.
- [ ] Test INACTIVE + current version no-op không thêm audit; stale retry 409; PostgreSQL race Update/Delete bảo đảm chỉ một expected-version write thành công.
- [ ] Test `auditFailureRollsBackOrganizationDeactivationAndAuditRow` bằng transaction thực; raw FK/SQL detail không xuất hiện trong HTTP error.
- [ ] Controller trả empty 204 và dùng actor từ principal. Thêm Javadoc/logging, chạy Delete/controller/repository tests, cập nhật inventory sau khi handler tồn tại.

### Task 6: Security, module boundaries và verification chung

**Files:**

- Test create: `T/api/controller/OrganizationSecurityTest.java`, reuse fixtures/filter configuration từ identity security tests.
- Test/inspect: `src/test/java/com/ngockhanh/clinic/identity/ProductionAuthSecurityTest.java`, `src/test/java/com/ngockhanh/clinic/identity/LocalAuthSecurityTest.java`.
- Test/inspect: `T/architecture/HealthExaminationApiSurfaceTest.java`.
- Reuse: `src/test/java/com/ngockhanh/clinic/architecture/ModuleVerificationTest.java`, `src/test/java/com/ngockhanh/clinic/shared/web/GlobalExceptionHandlerTest.java`, `src/test/java/com/ngockhanh/clinic/infrastructure/persistence/record/PersistenceRecordContractTest.java`, `src/test/java/com/ngockhanh/clinic/infrastructure/migration/CleanSlateMigrationContractTest.java`; không tạo bản sao.
- Modify: API inventory và file plan này cho trạng thái/verification thực tế.

- [ ] Full filter-chain tests cho từng route: unauthenticated 401, PATIENT/roleless STAFF denied, local/test STAFF có active role được xử lý theo policy, production vẫn denied.
- [ ] POST/PUT/DELETE thiếu hoặc sai CSRF bị chặn; valid CSRF + principal thật đi đúng flow; không cho request actor giả chi phối audit.
- [ ] Rà coverage HTTP 400/404/409, envelope code bằng HTTP status, DELETE 204 body rỗng; production denial không bị nhầm với handler success.
- [ ] Chạy module/layer và API-surface checks; GET list/DELETE chỉ được kỳ vọng khi task tương ứng đã làm, các route Excel import vẫn không tồn tại.
- [ ] Format đúng project config, review diff, kiểm tra không đụng user edits ngoài task; nếu rename/xóa source thì clean target trước verification.
- [ ] Chạy `.\mvnw.cmd verify`; cần PostgreSQL 18 Testcontainers cho SQL/rollback và Redis khi security setup phụ thuộc session thật. Ghi riêng failures, unavailable Docker/Redis và integration skips.
- [ ] Cập nhật API inventory, contract references và completion report. Chỉ đánh `[x]` cho task đã thực sự hoàn tất; contract chưa chốt hoặc integration skip phải ghi rõ.

## 5. Review Focus

| Tình huống dễ lỗi | Evidence bắt buộc |
|---|---|
| Hai request cùng tạo/chuyển sang một code | Task 1/3: unique constraint race; loser 409, không orphan audit |
| Version đúng lúc load nhưng bị thay trước write | Task 3/5: SQL predicate, zero-row conflict và atomic rollback |
| Audit đã insert rồi mới ném lỗi | Task 1/3/5: real PostgreSQL rollback cả business và audit |
| Search chứa wildcard, sort chứa SQL hoặc tên trùng | Task 4: literal search, allowlist và stable pagination |
| Deactivate lặp lại, Organization có Batch, request thiếu principal/CSRF | Task 5: lịch sử/no-op đúng contract; Task 6: filter-chain access thực |

## 6. Lệnh kiểm chứng khi triển khai

Sau khi baseline compile được, chạy nhóm test phù hợp; các lệnh dưới đây là **lệnh dự kiến, chưa chạy trong lần lập plan**. Tên Get test dùng sau rename ở Task 2; List/Delete/Security dùng sau khi tạo test.

```powershell
.\mvnw.cmd "-Dtest=CreateOrganizationUseCaseTest,GetOrganizationByIdUseCaseTest,OrganizationCrudUseCaseTest,ListOrganizationUseCaseTest,DeleteOrganizationUseCaseTest" test
.\mvnw.cmd "-Dtest=OrganizationControllerTest,OrganizationSecurityTest" test
.\mvnw.cmd "-Dtest=MyBatisOrganizationRepositoryIntegrationTest" test
.\mvnw.cmd verify
```

Đọc số tests run/failures/errors/skips, không chỉ exit code. Integration disabledWithoutDocker có thể skip dù Maven thành công. Nếu cần clean do rename/source removal, chạy `.\mvnw.cmd clean verify` thay cho lượt verify cuối. Không dùng Gradle hoặc H2.

## 7. Acceptance criteria và bàn giao

- Ba route hiện có giữ wire contract; List/Delete có contract được ghi nhận, handler/use case/test hoàn chỉnh trước khi tuyên bố được hỗ trợ.
- Mutation versioned phát hiện stale/race; required audit commit/rollback cùng business write.
- List bounded, sort ổn định, count đúng filter, response không lộ persistence model.
- Delete theo phương án đã chốt, bảo toàn references/lịch sử và có expected-version semantics rõ ràng.
- Security giữ policy hiện tại; không tuyên bố các route đã được mở cho production RBAC.
- Có evidence unit/API/security/PostgreSQL/module checks hoặc liệt kê đúng blockers/skips; không còn fake runtime implementation cho flow đã bàn giao.

Completion report cần nêu: module/file thay đổi, API được thêm, migration có/không, tests và commands/results, assumptions/contract decisions, Docker/Redis skips và rủi ro còn lại. Nếu chỉ hoàn tất một phần do contract mở, ghi đúng use case đã hoàn tất.

Sau triển khai, consuming frontend cần tích hợp List/Delete và gửi rowVersion theo contract đã chốt; deployment chỉ mở production access khi có RBAC contract riêng. Đây là thông tin bàn giao, không phải task frontend/deployment trong plan này.

**Phạm vi lần lập kế hoạch:** chỉ tạo tài liệu Markdown; runtime modules, schema và public APIs không thay đổi. Không chạy Maven tests vì chưa thay đổi runtime/build.
