# Kế hoạch triển khai Organization Batch CRUD

> Dành cho người triển khai: dùng skill `superpowers:executing-plans` để thực hiện lần lượt các task và đối chiếu checklist. Chỉ dùng subagent khi được người dùng yêu cầu. Tài liệu này là kế hoạch, không xác nhận API đã được triển khai hoặc kiểm thử đã chạy.

**Ngày:** 2026-10-06

**Trạng thái:** Đã chốt hướng nghiệp vụ với chủ dự án; chưa triển khai.

**Mục tiêu:** Hoàn thiện getById, create, update, list và delete cho đợt khám thuộc Organization.

**Kiến trúc:** Controller → application command/query → use case → domain/repository port → MyBatis. `healthexamination` sở hữu nghiệp vụ; catalog cung cấp giá dịch vụ, audit ghi lịch sử và integration cung cấp kiểm tra tham chiếu lịch sử.

**Stack:** Java 25, Spring Boot 4 / Framework 7, PostgreSQL 18, MyBatis, Spring Modulith, Maven, Flyway.

## 1. Cơ sở và các quyết định đã chốt

### 1.1. Nguồn áp dụng

- [PROJECT_RULES](../../PROJECT_RULES.md): layering, transaction, version, validation, API envelope, logging và verification.
- [PROJECT_SKILLS](../../PROJECT_SKILLS.md) và [nkc-backend-use-case](../../.agents/skills/nkc-backend-use-case/SKILL.md): quy trình triển khai endpoint.
- [ADR-0012](../adr/0012-clean-slate-module-boundaries.md), [ADR-0013](../adr/0013-clean-slate-application-contract.md): ownership và baseline nghiệp vụ.
- [Domain workflows](../architecture/03-domain-and-workflows.md), [module contracts](../architecture/02-module-contracts.md), [API/security](../architecture/05-api-and-security.md).
- [API inventory](../api/clean-slate-migration.md) và [schema hiện hành](../../src/main/resources/db/migration/V001__create_clean_slate_schema.sql).

Quyết định trực tiếp của chủ dự án trong phiên lập kế hoạch:

1. Delete là **xóa mềm**.
2. Chỉ cho xóa batch **DRAFT chưa có Participant hoặc lịch sử tham chiếu**.
3. Giữ chính sách truy cập hiện tại: local/test yêu cầu STAFF có role đang hoạt động; production tiếp tục chặn business endpoints.

Phần xóa mềm là contract mới do chủ dự án lựa chọn. Khi triển khai phải cập nhật tài liệu nghiệp vụ và API; không suy diễn đây là khả năng đã có trong V001.

### 1.2. Hiện trạng đã kiểm tra

- `OrganizationBatchController` đang trống.
- Có `HealthExaminationBatch`, repository port, MyBatis adapter/mapper, request cấu hình, list query và detail/summary response.
- Repository hiện có insert, đọc scoped detail/reference, list và count; chưa có update hoặc soft delete.
- Request hiện chuyển sang update command nhưng command chưa có expected version. Create command hiện dùng update command làm cấu hình: cần tách nghĩa khi triển khai.
- Test vẫn tham chiếu `CreateHealthExaminationBatchUseCase`, `ListHealthExaminationBatchUseCase` và `BatchDraftEditor` không còn trong source hiện tại.
- API inventory còn mô tả create/list trước đây; phải đối chiếu source khi cập nhật, không xem tài liệu đó là bằng chứng build đang chạy được.
- Worktree có thay đổi Organization và tài liệu từ trước. Không reset, ghi đè hoặc gom các thay đổi đó vào phạm vi batch.

### 1.3. Giới hạn

- Không thêm endpoint chuyển trạng thái, restore, xem thùng rác hoặc `includeDeleted`.
- Không triển khai Participant CRUD, frontend, Excel roster import, repricing lịch sử hoặc RBAC production mới.
- Không thay `BatchStatus`: vẫn DRAFT, READY, FINALIZED, CLOSED.
- Không thêm generic CRUD base class, JPA, H2, cache hoặc outbox khi không có external effect.
- Không tự commit/push chỉ vì checklist hoàn tất; việc lưu tài liệu không phải yêu cầu triển khai runtime.

## 2. Contract HTTP

Base path: `/api/v1/organizations/{organizationId}/health-examination-batches`.

`organizationId` và `batchId` là UUID. Actor chỉ lấy từ principal đã xác thực. Mọi truy vấn tài nguyên con phải scope bằng cả organization và batch.

| Hành vi | Method / suffix | Request | Response thành công |
|---|---|---|---|
| create | POST base path | CreateHealthExaminationBatchRequest | 201 + ApiResponse<BatchDetailResponse> + Location |
| getById | GET /{batchId} | Path IDs | 200 + ApiResponse<BatchDetailResponse> |
| update | PUT /{batchId} | UpdateHealthExaminationBatchRequest | 200 + ApiResponse<BatchDetailResponse> |
| list | GET base path | HealthExaminationBatchListRequest | 200 + ApiResponse<PageResponse<BatchSummaryResponse>> |
| delete | DELETE /{batchId}?rowVersion=N | DeleteHealthExaminationBatchRequest | 204, body rỗng |

### 2.1. Create và update

Giữ tên trường cấu hình hiện hành; PUT là thay toàn bộ cấu hình, không phải PATCH.

| Trường | Kiểu / ràng buộc | Nguồn / ý nghĩa |
|---|---|---|
| batchCode | String, bắt buộc, tối đa 50 | Trim; unique toàn hệ thống |
| batchName | String, bắt buộc, tối đa 300 | Trim |
| examinationDates | List<LocalDate>, không rỗng, không có null hoặc ngày trùng | Format YYYY-MM-DD; không tự cấm ngày quá khứ |
| examinationSiteType | CLINIC hoặc ORGANIZATION_SITE | Bắt buộc |
| examinationSiteName | String, không blank | Tên địa điểm |
| examinationSiteAddress | String, không blank | Địa chỉ địa điểm |
| services | List<ServicePriceRequest>, không rỗng | Không lặp serviceId; thứ tự xác định displayOrder |
| services[].serviceId | UUID, bắt buộc | ID dịch vụ catalog |
| services[].negotiatedPrice | BigDecimal, bắt buộc, >= 0, tối đa 12 chữ số nguyên và 2 chữ số thập phân | VND; không tự làm tròn input vượt scale |
| rowVersion | Long, @NotNull, >= 0; chỉ update | Expected header version |

Ví dụ POST:

```json
{
  "batchCode": "NKC-2026-001",
  "batchName": "Đợt khám định kỳ tháng 10",
  "examinationDates": ["2026-10-20", "2026-10-21"],
  "examinationSiteType": "CLINIC",
  "examinationSiteName": "Phòng khám mẫu",
  "examinationSiteAddress": "Địa chỉ mẫu",
  "services": [
    {
      "serviceId": "0199a000-0000-7000-8000-000000000001",
      "negotiatedPrice": 150000.00
    }
  ]
}
```

PUT gửi cùng cấu hình và thêm `"rowVersion": 0`. Không nhận trạng thái, createdBy, referencePriceSnapshot, displayOrder hoặc ID của BatchDay/BatchService từ client.

Create trả `Location` trỏ tới route detail của batch vừa tạo. Detail giữ cấu trúc hiện có: ID, organizationId, code/name, days, date bounds, site, status, createdBy, timestamps, rowVersion, services. Service detail có ID nội bộ, serviceId, hai loại giá, displayOrder, active và rowVersion.

### 2.2. List

| Query | Mặc định | Ràng buộc |
|---|---|---|
| page | 1 | >= 1 |
| size | 10 | 1..100 |
| searchKey | null | Trim, tối đa 100; blank tương đương không tìm kiếm |
| sortKey | id | id, batchCode, batchName, startDate, status, createdAt |
| sortBy | ASC | ASC hoặc DESC |

- Search contains trên code/name, không phân biệt hoa thường; `%`, `_`, `\` là ký tự literal.
- Dùng SQL allowlist cố định cho sort, không nội suy trực tiếp chuỗi người dùng vào ORDER BY.
- Giữ tie-breaker `id ASC` của batch mapper hiện tại; nếu sortKey=id thì chỉ dùng một biểu thức id theo sortBy.
- Không thêm filter status/ngày trong v1.
- Count và items dùng cùng điều kiện organization/search/chưa xóa. Page vượt cuối trả items rỗng nhưng giữ totals; không có kết quả thì totalPages=0.
- Summary giữ trường hiện có; không tải days/services theo vòng lặp cho từng batch. Client cần rowVersion dùng GET detail.

### 2.3. Quy tắc scope và lỗi

| Trường hợp | Kết quả |
|---|---|
| UUID/JSON/date sai, field thiếu, pagination/sort/version sai | 400 |
| Không xác thực / không đủ quyền / CSRF sai | 401 hoặc 403 theo filter-chain hiện có |
| Organization không tồn tại | 404 |
| Batch không tồn tại, sai organization hoặc đã xóa | 404 |
| Create vào Organization INACTIVE | 409 |
| Code trùng, dịch vụ mới thiếu/inactive, vi phạm invariant | 409 |
| Update/delete sai version | 409 |
| Update ngoài DRAFT; delete có dữ liệu tham chiếu hoặc ngoài DRAFT | 409 |

Organization INACTIVE không chặn đọc/sửa/xóa batch đã tồn tại. Không gọi use case get Organization vốn chỉ trả ACTIVE để kiểm tra scope của các thao tác này.

Giữ lỗi chung `{result:"NG", code:<HTTP status>, message:<safe message>}`; không thêm public error code hoặc trả SQL/stack trace. DELETE đã xóa trước đó trả 404, kể cả gọi lặp cùng version; không thêm no-op 204 cho trường hợp này.

## 3. Thiết kế application và domain

### 3.1. Use case và interface

Các class nằm trong `healthexamination/application/usecase`; mỗi class có một public `execute`:

```text
CreateHealthExaminationBatchUseCase
  execute(UUID organizationId, CreateHealthExaminationBatchCommand command, UUID actor)
  -> BatchDetailResponse

GetHealthExaminationBatchByIdUseCase
  execute(UUID organizationId, UUID batchId)
  -> BatchDetailResponse

ListHealthExaminationBatchUseCase
  execute(UUID organizationId, HealthExaminationBatchListQuery query)
  -> PageResponse<BatchSummaryResponse>

UpdateHealthExaminationBatchUseCase
  execute(UUID organizationId, UUID batchId, UpdateHealthExaminationBatchCommand command, UUID actor)
  -> BatchDetailResponse

DeleteHealthExaminationBatchUseCase
  execute(UUID organizationId, UUID batchId, DeleteHealthExaminationBatchCommand command, UUID actor)
  -> void
```

- Create command mang cấu hình, không có actor hoặc update version. Update command mang cấu hình và expected rowVersion; delete command mang expected rowVersion.
- Dùng `BatchConfiguration` trong application/command cho phần cấu hình dùng chung của create/update; nested `ServicePrice` mang serviceId và negotiatedPrice. HTTP JSON vẫn phẳng như mục 2, không thêm wrapper configuration trên wire.
- Tách create/update request, dùng chung `ServicePriceRequest`; loại request cũ sau khi chuyển hết caller/test.
- Tái sử dụng list request/query, detail/summary response. Copy phòng vệ các List trong command/response; giữ kiểu record hiện có và Lombok builder phù hợp.
- Nếu cần cộng tác dùng chung cho dựng/cập nhật cấu hình, đặt `BatchConfigurationAssembler` trong application/service, không đặt helper vào usecase. Assembler gọi published catalog query và ghép entities, không sở hữu transaction/audit.
- Invariant ngày/service trùng, cấu hình tối thiểu và trạng thái sửa thuộc domain. Bean Validation chỉ kiểm tra cấu trúc HTTP; application kiểm tra organization, catalog, reference và concurrency theo trách nhiệm riêng.

### 3.2. Create

1. Kiểm tra command và authenticated actor; load Organization, yêu cầu ACTIVE.
2. Tra catalog một lần cho các serviceId; từ chối dịch vụ không có/inactive.
3. Sinh UUIDv7 cho batch/days/services; referencePriceSnapshot lấy từ catalog, negotiatedPrice lấy từ command, active=true, displayOrder từ 1 theo input, rowVersion=0.
4. Tạo aggregate DRAFT; days sort theo ngày; startDate/endDate tính từ days, không lưu thêm cột bounds.
5. Insert header và children qua repository port trong cùng transaction.
6. Ghi audit; đọc lại detail trong transaction để trả timestamps/version thực tế từ DB.

Unique constraint trên batch_code là hàng rào cuối cùng cho concurrent creates. Không chỉ dựa vào precheck; lỗi duplicate phải được handler chuyển thành 409 và rollback toàn bộ children/audit.

### 3.3. Get/list

- Kiểm tra organization tồn tại nhưng không bắt buộc ACTIVE.
- Detail phải trả cấu hình nhất quán giữa header/days/services: dùng read-only transaction với snapshot REPEATABLE_READ vì mapper đọc nhiều câu SQL.
- List cũng dùng read-only REPEATABLE_READ để count và items nhất quán trong một response.
- SQL projection nằm trong infrastructure/persistence/view; map sang typed read contract ở repository port. Không để MyBatis join projection trở thành HTTP DTO.
- Dùng PageResponse/PaginationConstants hiện có; application query được kiểm tra cho caller ngoài HTTP mà không tham chiếu api/request.

### 3.4. Update

1. Load header scoped bằng organizationId/batchId với FOR UPDATE, chỉ lấy batch chưa xóa.
2. Kiểm tra expected version trước thay đổi, yêu cầu DRAFT.
3. Ghép days theo examinationDate: giữ ngày cũ thì giữ ID; ngày mới cấp ID; ngày bị bỏ phải chưa có Participant tham chiếu. Đổi ngày là bỏ ngày cũ/thêm ngày mới, không sửa âm thầm lịch Participant.
4. Ghép services theo catalog serviceId: giữ ID, referencePriceSnapshot và active cho mục còn lại. Dịch vụ mới phải active trong catalog và chụp giá mới. Catalog đổi giá/inactive sau lần thêm không làm mất snapshot hoặc tự chặn giữ mục cũ.
5. Dịch vụ bị bỏ chỉ được xóa nếu không có ParticipantService tham chiếu, kể cả is_performed=false. Không sửa lịch sử performed prices hoặc ServiceRequest links.
6. Cập nhật negotiatedPrice/order của mục giữ lại theo command; tăng child rowVersion khi mục đó thay đổi, với expected version lấy từ bản đã load dưới khóa batch.
7. Versioned header update tăng rowVersion đúng một lần, kể cả PUT cùng giá trị; đồng bộ children và audit trong cùng transaction.
8. Đọc lại detail để response chứa version và timestamps mới.

Repository dùng diff, không delete/reinsert toàn bộ children. Đổi displayOrder dùng hai pha trong transaction: chuyển các mục giữ lại sang dải số dương tạm ngoài các order cũ/cuối, sau đó ghi order cuối. Pha tạm không tăng version; pha cuối tăng một lần cho mục thực sự đổi. Tính toán bằng long và kiểm tra giới hạn integer trước khi ghi để tránh overflow; vẫn giữ CHECK display_order > 0 và unique (batch_id, display_order).

### 3.5. Delete

1. Load header scoped chưa xóa với FOR UPDATE.
2. Kiểm tra rowVersion và DRAFT.
3. Kiểm tra không có Participant của batch, bất kể roster_status. Days/services cấu hình không được coi là lịch sử cản xóa.
4. Kiểm tra không có import_jobs lịch sử qua contract `integration::batch-history`.
5. Domain đánh dấu xóa bằng Instant từ Clock của application. Repository ghi deleted_at, updated_at và tăng row_version một lần.
6. Ghi audit trong cùng transaction; trả void để controller trả 204.

Published contract mới do integration sở hữu:

```text
integration.application.query.BatchHistoryQuery
  boolean hasBatchReferences(UUID batchId)
```

Adapter integration dùng EXISTS trên bảng import_jobs của chính module. Thêm NamedInterface `batch-history` và dependency rõ ràng cho healthexamination. Đây chỉ là truy vấn bảo vệ lịch sử, không khôi phục imports facade, staging hoặc endpoint import.

### 3.6. Concurrency và quyền truy cập

- Mọi writer thao tác batch/Participant liên quan phải lấy khóa header batch và kiểm tra chưa xóa trước khi ghi. Cập nhật các writer hiện có bị ảnh hưởng trong phạm vi tối thiểu cần để bảo đảm delete.
- FOR UPDATE và FK đơn thuần không ngăn một writer bỏ qua deleted_at chèn Participant sau soft delete. Test phải chứng minh contract kiểm tra header của writer thực tế; không tuyên bố bảo vệ arbitrary SQL ngoài ứng dụng.
- SQL update/delete có id, organization_id, row_version, deleted_at IS NULL; zero-row write là ConcurrentUpdateException sau bước load xác định 404.
- Giữ policy filter-chain hiện tại. Actor UUID chỉ là danh tính audit, không phải bằng chứng authorization. Không thêm @PreAuthorize hoặc cho phép production qua task này.
- Use case chỉ phục vụ controller được bảo vệ và test trong scope này; mở caller runtime khác phải bảo đảm cùng access contract.

## 4. Persistence, migration, audit và tài liệu

### 4.1. Migration

Tạo `src/main/resources/db/migration/V002__add_health_examination_batch_soft_delete.sql` (đổi sang version chưa dùng kế tiếp nếu repository có migration mới khi triển khai):

```sql
ALTER TABLE public.health_examination_batches
    ADD COLUMN deleted_at timestamptz(3) NULL;
```

- Không sửa V001 đã áp dụng; batch cũ có deleted_at=NULL.
- Giữ unique batch_code kể cả row đã xóa; không tạo partial unique để tái sử dụng code.
- Không cascade, không xóa days/services, không thêm deleted_by: audit lưu actor.
- Map deleted_at sang Instant nullable trong persistence record và trạng thái domain. Không đưa trường này vào response hiện hành vì API không trả batch đã xóa.
- Bổ sung điều kiện deleted_at IS NULL cho scoped detail/reference/list/count và các truy vấn phục vụ business writers; không sửa lịch sử thành mất liên kết.
- Chưa thêm index mới chỉ vì có deleted_at. Dùng index organization hiện tại; tối ưu riêng khi có dữ liệu/EXPLAIN chứng minh nhu cầu.

### 4.2. Các nhóm file

| Nhóm | Thay đổi dự kiến |
|---|---|
| healthexamination/api | Hoàn thiện OrganizationBatchController; tách create/update request, thêm delete request; giữ list request |
| healthexamination/application | 5 use case; cấu hình/command/query; assembler; response mapping |
| healthexamination/domain | Hành vi updateDraft/softDelete; deletedAt nullable; repository write/reference-check contracts |
| healthexamination/infrastructure | Record/converter/projection/mapper/repository, SQL scoped/versioned/filter |
| integration | Published BatchHistoryQuery, NamedInterface và adapter/mapper EXISTS lịch sử |
| resources/db/migration | Migration thêm deleted_at |
| test | Domain, use case, controller/security, PostgreSQL, module contracts |
| docs | API inventory, domain workflows, module published contracts, follow-ups được giải quyết thực tế |

### 4.3. Audit và logging

| Operation | Audit action | Resource type |
|---|---|---|
| Create | CREATE_HEALTH_EXAMINATION_BATCH | HEALTH_EXAMINATION_BATCH |
| Update | UPDATE_HEALTH_EXAMINATION_BATCH | HEALTH_EXAMINATION_BATCH |
| Delete | DELETE_HEALTH_EXAMINATION_BATCH | HEALTH_EXAMINATION_BATCH |

- AuditWriter là contract `audit::recording`; lỗi audit rollback business writes.
- Metadata có organizationId, version, trạng thái và before/after cấu hình liên quan. Delete có deletedAt; update có thay đổi days/services/giá cần truy vết. Không đưa Participant/clinical payload vào audit batch.
- Operational logs chỉ IDs, versions, counts và safe outcome; không serialize request/response/domain, tên, địa chỉ hay raw search.
- Mutation log trong transaction dùng wording pending commit; không tuyên bố committed khi chưa xác nhận commit.

### 4.4. Documentation và rollout

- Cập nhật API inventory đủ 5 endpoint, field/version, status, soft-delete, lỗi và chính sách production.
- Cập nhật domain workflows: điều kiện xóa, giữ lịch sử, code không tái sử dụng; loại mô tả không còn đúng về endpoint batch.
- Cập nhật module contracts cho integration::batch-history; không công bố integration::imports.
- Chỉ xóa follow-up về class/test đã được sửa; giữ các vấn đề mixed-profile không thuộc task này.
- Migration áp dụng trước code mới; kiểm tra startup với V001→V002 và database mới. Database thuộc lịch sử V001–V003 cũ vẫn cần quy trình conversion riêng.
- Sau deploy dùng request được phép để smoke-test create→get→list→update→delete; xác minh 404 sau xóa và audit. Production vẫn deny theo quyết định đã chốt.
- Không DROP deleted_at để rollback ứng dụng: code cũ không hiểu soft delete có thể hiện lại batch đã xóa. Rollback phải giữ đường batch bị chặn hoặc rollback bằng bản vá hiểu deleted_at.

## 5. Kế hoạch thực hiện theo task

### Task 1 — Chốt DTO/command và khôi phục nền kiểm thử batch

- [ ] Recheck git status, dùng CodeGraph trước tìm/đọc code; ghi nhận thay đổi có sẵn và compiler/test baseline.
- [ ] Tách create/update/delete request/command theo mục 2–3; thêm BatchConfiguration/ServicePrice dùng chung ở application.
- [ ] Chuyển test đang dùng Update command làm cấu hình create sang contract mới; chuyển coverage BatchDraftEditor sang assembler/use case thay thế.
- [ ] Giữ test bị thiếu dependency được sửa đồng bộ cùng class tương ứng; không thêm fake production bean chỉ để compile.
- [ ] Thêm validation/serialization tests: thiếu version, null nested item, giá vượt scale, duplicate dates/services, JSON date/site.

**Đầu ra:** DTO wire shape và application input độc lập transport, test mới mô tả hành vi thay cho class bị gỡ.

### Task 2 — Tạo/đọc/list end-to-end

- [ ] Viết test create/get/list theo contract; triển khai assembler, domain factory, 3 use case và controller mappings tương ứng.
- [ ] Dùng catalog published query, UUIDv7, insert header/children và audit cùng transaction.
- [ ] Hoàn thiện mapping projection, detail và pagination/search/sort ổn định; giữ scope organization.
- [ ] Test PostgreSQL duplicate code, rollback children, snapshot giá và count/items; test HTTP 201/Location/200/envelope.

**Đầu ra:** Tạo và đọc được batch DRAFT; organization INACTIVE chỉ chặn create.

### Task 3 — Update draft với version và bảo toàn danh tính

- [ ] Viết domain/use-case test giữ ID/snapshot, thay giá, thêm/bỏ/đổi thứ tự và chặn tham chiếu.
- [ ] Bổ sung domain updateDraft, repository versioned update và SQL diff children; triển khai Update use case/PUT mapping.
- [ ] Test thực PostgreSQL swap service order, child version, FK, stale header và rollback audit.
- [ ] Test concurrent PUT: với cùng expected version chỉ một request thành công; response trả version mới và không mất dữ liệu.

**Đầu ra:** PUT đầy đủ cấu hình DRAFT, giữ lịch sử, thất bại atomically khi conflict.

### Task 4 — Xóa mềm và bảo vệ tham chiếu

- [ ] Thêm migration, deletedAt mapping/domain và filter chưa xóa cho các đường đọc/ghi.
- [ ] Thêm integration::batch-history cùng mapper/adapter và module declarations; test EXISTS có/không có import history.
- [ ] Triển khai Delete use case và DELETE mapping, version/state/reference checks cùng audit.
- [ ] Kiểm tra writer Participant hiện hữu; bảo đảm khóa/kiểm tra header để không thêm vào batch đã xóa.
- [ ] Test giữ rows/children, ẩn get/list, 404 khi delete lặp, không tái dùng code, stale delete và rollback audit.
- [ ] Test concurrent update/delete và writer Participant/delete trên PostgreSQL, bao gồm trường hợp writer chờ khóa rồi phải từ chối batch đã xóa.

**Đầu ra:** Chỉ nháp chưa có Participant/lịch sử được ẩn; không phá lịch sử hoặc module boundary.

### Task 5 — Security, documentation và verification toàn bộ

- [ ] Thêm filter-chain tests cho đủ endpoint: anonymous, PATIENT, STAFF không role, STAFF hợp lệ trong local/test và production denial.
- [ ] Test CSRF thiếu/sai cho POST/PUT/DELETE; không dùng standalone MockMvc để tuyên bố security chain đã được chứng minh.
- [ ] Cập nhật HealthExaminationApiSurfaceTest, persistence-record/migration checks và Spring Modulith cho interface mới.
- [ ] Cập nhật tài liệu mục 4.4; rà soát Javadoc, safe logging và không lộ API/domain/persistence model sai tầng.
- [ ] Format chỉ source đã sửa theo formatter cấu hình; không format toàn worktree đang có thay đổi khác.
- [ ] Chạy wrapper verify, review diff cuối và báo kết quả thực tế, skips, migration/API changes, risks còn lại.

## 6. Ma trận kiểm thử và tiêu chí nghiệm thu

| Nhóm | Case bắt buộc | Kết quả cần chứng minh |
|---|---|---|
| Create | Đủ ngày/services và organization ACTIVE | DRAFT, version 0, UUIDv7, giá snapshot, audit |
| Create | Org thiếu/INACTIVE, service thiếu/inactive, code trùng | 404/409; không có partial rows |
| Input | Version null/âm, invalid UUID/date/site/price, list null/empty | 400 phù hợp; không ghi DB |
| Scope | Dùng batchId của organization khác | 404; không lộ cấu hình |
| Read | Organization INACTIVE, batch còn hiệu lực | Batch vẫn đọc được |
| List | Search có %, _, \\, sort không hợp lệ, page vượt cuối | Literal search, 400 sort sai, totals đúng |
| Detail | Nhiều ngày/services, writer update đồng thời | Bounds đúng; response nhất quán snapshot |
| Update | Giữ/sửa/thêm/bỏ ngày và dịch vụ | IDs/snapshots giữ đúng; không tạo lại hàng còn dùng |
| Update | Bỏ referenced day/service hoặc không DRAFT | 409; rollback toàn bộ |
| Update | Catalog thay giá/inactive với service đã giữ | Không thay reference snapshot; không chặn giữ service |
| Order | Swap order, chèn giữa, bỏ rồi đổi thứ tự | Không vướng unique tạm; order/version cuối đúng |
| Delete | DRAFT chưa Participant/lịch sử | deleted_at có giá trị; rows/children còn nguyên; audit |
| Delete | READY/FINALIZED/CLOSED, Participant CANCELLED, import history | 409; chưa đánh dấu xóa |
| Deleted | Get/update/delete lại; list/count; tạo lại code | 404; bị loại khỏi list/count; duplicate code 409 |
| Concurrency | PUT/PUT, PUT/DELETE cùng version | Chỉ một write thắng; loser 409 hoặc 404 nếu resource đã bị xóa trước lúc load |
| Concurrency | Thêm Participant đua với DELETE | Không có batch đã xóa nhận thêm Participant qua writer được hỗ trợ |
| Audit | Writer audit thất bại create/update/delete | Rollback header, children, versions và deleted_at |
| Security | Auth/role/profile/CSRF | Đúng policy hiện tại, actor là account đã xác thực |
| Architecture | Modulith, record/schema, API surface | Ownership và named interfaces hợp lệ |
| Migration | DB mới và V001 có dữ liệu trước migration | Khởi động được, row cũ chưa bị xóa |

Các test class dự kiến gồm: `HealthExaminationBatchCrudTest`, `BatchConfigurationAssemblerTest`, test riêng cho 5 use case, `OrganizationBatchControllerTest`, `OrganizationBatchSecurityTest`, `HealthExaminationBatchCrudIntegrationTest`, integration test của `BatchHistoryQuery`, và các architecture test hiện hành.

Các lỗi dễ bỏ sót phải có bằng chứng test: giữ inactive catalog service đã thêm; Participant CANCELLED vẫn chặn xóa; swap displayOrder có unique constraint; deleted batch không thể được writer đang chờ khóa nhận thêm Participant; audit lỗi không để version/deleted_at thay đổi.

Lệnh kiểm chứng runtime sau triển khai, từ root backend:

```powershell
.\mvnw.cmd verify
```

Không dùng H2 thay PostgreSQL 18; mocks không chứng minh SQL/transaction. Nếu Docker/Redis không khả dụng, ghi rõ integration/security tests nào skip hoặc fail và phần chưa được kiểm chứng. Không báo hoàn tất runtime nếu verification bắt buộc còn lỗi chưa giải quyết.

## 7. Checklist bàn giao

- [ ] Đủ 5 endpoint với status/envelope và input/output đã chốt.
- [ ] Migration mới, V001 không đổi; không hard delete batch/history.
- [ ] Update/delete kiểm tra version, không vượt organization scope.
- [ ] Xóa mềm chỉ áp dụng nháp chưa có Participant/lịch sử; các đường đọc/ghi tôn trọng deleted_at.
- [ ] Business writes và required audit atomic trên PostgreSQL thực.
- [ ] Production policy không bị nới; CSRF và authenticated actor được kiểm thử.
- [ ] Không khôi phục roster import; integration chỉ thêm read contract lịch sử cần thiết.
- [ ] Docs phản ánh runtime cuối cùng; không ghi đè công việc Organization đang có.
- [ ] Báo cáo cuối nêu module/files, migration, public API, tests, commands/results, skips và risks thực tế.

**Tình trạng khi tạo tài liệu:** chỉ tạo file kế hoạch này. Runtime modules, database schema và public APIs chưa thay đổi bởi tác vụ viết kế hoạch; chưa chạy Maven hoặc integration tests cho triển khai tương lai.
