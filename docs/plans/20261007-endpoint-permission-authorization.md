# Plan hoàn thiện kiểm tra endpoint và permission cho auth

> Triển khai từng task bằng `superpowers:executing-plans`; đây là bản đề xuất, chưa phải quyết định cấp thêm quyền. Chỉ đánh dấu checklist sau khi có kết quả kiểm chứng.

**Goal:** Mỗi business endpoint kiểm tra HTTP method, path, loại tài khoản và permission; application bảo vệ cả caller ngoài HTTP.

**Architecture:** Giữ opaque session cookie và Redis theo ADR-0014. `EndpointPermissions` là danh sách rule HTTP của ADR-0015; permission lấy từ principal được backend khôi phục từ session. Use case kiểm tra quyền, phạm vi tài nguyên và điều kiện nghiệp vụ theo contract của module.

**Stack:** Java 25, Spring Boot 4 / Framework 7, Spring Security 7, PostgreSQL 18, MyBatis, Spring Modulith, Maven, Flyway.

**Spec:** [ADR-0014](../adr/0014-session-cookie-redis-login.md), [ADR-0015](../adr/0015-endpoint-permission-rbac.md), [PROJECT_RULES](../../PROJECT_RULES.md), [API/security](../architecture/05-api-and-security.md).

## 1. Phạm vi và hiện trạng

Lập ngày 2026-10-07 trên worktree đang có nhiều thay đổi. Chỉ thêm tài liệu này; chưa sửa runtime, schema, API hoặc test. Khi triển khai phải xác nhận lại source hiện hành và giữ nguyên thay đổi ngoài phạm vi.

Phân biệt hai bước:

1. Authentication: cookie hợp lệ → Redis session → `UserPrincipal`.
2. Authorization: principal đó được phép gọi endpoint nào và tác động tài nguyên nào.

Kết quả đối chiếu source và inventory Amplicode:

- Có 28 endpoint production: 3 auth và 25 business. Inventory có thêm `/api/v1/test-business` trong test; không tính vào production.
- `EndpointPermissions.RULES` có 22 business rule; mỗi rule dùng `hasAllAuthorities("ACCOUNT_STAFF", "PERM_" + permission)`.
- `SecurityConfiguration` cho POST login/logout public, GET me authenticated, rồi áp dụng business rule và `anyRequest().denyAll()`.
- Ba handler business chưa có rule là DELETE Organization, DELETE Batch và GET catalog/services. Request có session hợp lệ vẫn bị chặn 403.
- Session filter đã tạo riêng `ACCOUNT_`, `ROLE_`, `PERM_` authorities. Có role STAFF hoặc role ADMINISTRATOR tự nó không mở mọi endpoint.
- `ParticipantAccessPolicy` đã kiểm tra permission cho application caller. Các Organization/Batch use case hiện nhận UUID actor ở write, hoặc không nhận principal ở read; actor không chứng minh quyền.
- `ListServiceCatalogUseCase.execute(ServiceCatalogListQuery)` chưa kiểm tra principal.
- Permission là snapshot lúc login. Thay đổi grant trong DB không tự cập nhật session hiện có; đây là hành vi đã chốt trong ADR-0015.

Module sở hữu: `accesscontrol` sở hữu auth/session/rule HTTP; `healthexamination` sở hữu quyền và scope Organization/Batch/Participant; `catalog` sở hữu truy cập danh mục. `audit` giữ contract ghi audit hiện có.

## 2. Thiết kế đề xuất

Giữ `EndpointPermissions.Rule(HttpMethod method, String pattern, String permission)` và `apply(...)`. Không cần dựng bảng endpoint trong DB, một authorization framework mới hoặc thêm annotation permission lặp với rule HTTP. Role vẫn là nơi gom permission; quyết định truy cập dựa trên permission thực có.

Luồng request:

```text
Request → CORS/Origin check → session cookie authentication
        → method + path rule → ACCOUNT_STAFF và PERM_<code>
        → controller → application permission/scope/state → business operation
```

Các exception auth giữ nguyên contract:

| Route | Chính sách |
|---|---|
| POST `/api/v1/auth/login` | Public; vẫn kiểm tra Origin và credentials |
| POST `/api/v1/auth/logout` | Public; vẫn kiểm tra Origin, cookie được xử lý bởi logout |
| GET `/api/v1/auth/me` | Session hợp lệ; STAFF và PATIENT đều được gọi |
| Business endpoint | STAFF + permission của rule |
| Endpoint chưa có rule | Deny; anonymous 401, authenticated 403 |

Giữ xử lý ERROR dispatcher và CORS preflight hiện tại, có regression test; không mở rộng `permitAll("/api/**")`. PATIENT mang permission business vẫn bị từ chối. Khi portal có contract mới, bổ sung rule ACCOUNT_PATIENT + OWN_* trong task riêng.

Rule literal như `/participants/import-template` phải đứng trước `/participants/*`. GET, POST, PUT, DELETE cùng path vẫn có permission riêng. Với HEAD: mặc định chưa được cấp qua rule GET; ghi rõ và test 401/403 theo chính sách deny hiện hành. CORS preflight hợp lệ được xử lý ở CORS, không phải blanket permission cho mọi OPTIONS request.

## 3. Matrix cần bổ sung và điểm cần chốt

Prefix viết tắt `B` = `/api/v1/organizations/{organizationId}/health-examination-batches`.

| Endpoint | Permission hiện hành / đề xuất | Trạng thái |
|---|---|---|
| GET `/api/v1/organizations` | `ORGANIZATION_SEARCH` | Đã có rule |
| GET `/api/v1/organizations/{id}` | `ORGANIZATION_VIEW` | Đã có rule |
| POST `/api/v1/organizations` | `ORGANIZATION_CREATE` | Đã có rule |
| PUT `/api/v1/organizations/{id}` | `ORGANIZATION_UPDATE` | Đã có rule |
| DELETE `/api/v1/organizations/{id}` | Đề xuất `ORGANIZATION_DEACTIVATE` | Chưa có code này trong seed; cần chốt contract |
| GET `B`, GET `B/{batchId}` | `HEALTH_EXAMINATION_BATCH_VIEW` | Đã có rule |
| POST `B` | `HEALTH_EXAMINATION_BATCH_CREATE` | Đã có rule |
| PUT `B/{batchId}` | `HEALTH_EXAMINATION_BATCH_UPDATE` | Đã có rule |
| DELETE `B/{batchId}` | Đề xuất `HEALTH_EXAMINATION_BATCH_DELETE` | Chưa có code này trong seed; cần chốt contract |
| GET `/api/v1/catalog/services` | Đề xuất dùng `MASTER_DATA_SERVICE_CATALOG_MANAGE` cho phạm vi hiện tại | Có permission/grant CLINIC_MANAGER, thiếu mapping route; cần xác nhận quyền đọc danh mục khi chọn dịch vụ |
| Participant routes | Giữ `PARTICIPANT_VIEW/TEMPLATE_DOWNLOAD/IMPORT/CREATE/UPDATE/REMOVE/REACTIVATE` | Đã có rule |
| Examination details/report routes | Giữ `HEALTH_EXAMINATION_SERVICE_READ/RECONCILE`, `HEALTH_EXAMINATION_REPORT_READ` | Đã có rule |

Không tự suy diễn batch soft delete là `HEALTH_EXAMINATION_BATCH_CANCEL`: permission CANCEL đã tồn tại nhưng mô tả nghiệp vụ khác. Nếu chủ dự án chọn dùng UPDATE cho DELETE, thay hai đề xuất DELETE bằng permission UPDATE tương ứng và không thêm seed cho chúng. Không tự cấp quyền cho ADMINISTRATOR hoặc đổi các grant Participant đã chốt.

Với hai permission DELETE riêng, đề xuất chỉ grant CLINIC_MANAGER theo phạm vi Organization/Batch hiện hành; role grant này cũng cần được chốt cùng code permission trước khi viết migration.

Những phần phụ thuộc matrix chưa chốt giữ deny. Có thể tiếp tục viết coverage test, củng cố rule đã có và bảo vệ application caller của những hành vi đã có contract.

### Mâu thuẫn tài liệu / phần chưa được xác nhận

- `docs/architecture/05-api-and-security.md` và Javadoc `EndpointPermissions` còn ghi seed V004; ADR-0013/0015 và migration hiện tại dùng V002 cho seed, V004 cho account-role pairing. Khi triển khai sửa tham chiếu theo accepted ADR.
- Plan Organization/Batch ngày 2026-10-06 còn mô tả local/test role và production deny; đây là supporting plan cũ, không ghi đè ADR-0015 đã accepted ngày 2026-10-07.
- `PROJECT_RULES` còn nhắc identity trong inventory; ADR-0012/0014 xác định owner hiện tại là accesscontrol. Không đổi module để khớp tên cũ.
- Các link `docs/api/clean-slate-migration.md` và `docs/maintenance/code-follow-ups.md` trong nguồn hướng dẫn không có file trên worktree đã kiểm tra. Không tạo nội dung hợp đồng thay thế từ suy đoán.
- Seed còn code legacy `HEALTH_EXAMINATION_PARTICIPANT_MANAGE` và điều kiện role `ADMIN`, trong khi role matrix là `ADMINISTRATOR`. Không chấp nhận legacy MANAGE để mở Participant và không tự sửa thành grant ADMINISTRATOR. Việc dọn seed/grant nằm ngoài task endpoint này nếu chưa có contract.

## 4. Global constraints và tiêu chí chấp nhận

- Backend lấy permission và actor từ authenticated principal; không nhận permission, role hoặc actor có quyền từ request body/header.
- `accesscontrol` không phụ thuộc business module; business caller chỉ dùng published application contract. Domain không import Spring Security hoặc HTTP.
- Rule HTTP và policy application thống nhất permission; scope/state của tài nguyên vẫn thuộc use case/domain.
- Authorization thất bại trước khi đọc payload nhạy cảm, ghi business data hoặc audit mutation; không dùng actor-only overload để bypass policy.
- Giữ DTO, URL, status thành công, envelope, version conflict, idempotency, audit transaction và lịch sử hiện tại.
- Không thay JWT/OIDC, session TTL, CSRF/Origin strategy hay thêm DB lookup permission mỗi request trong scope này.
- Nếu thêm permission/grant, dùng migration mới sau version cao nhất lúc triển khai; không sửa V001/V002 đã áp dụng. Không đổi schema bảng cho rule endpoint.

Review focus: sai HTTP method; rule literal bị wildcard bắt trước; PATIENT/role-only bị nhận nhầm; caller application truyền actor UUID để bypass; session cũ sau đổi grant. Test tương ứng nằm trong các task dưới đây.

## 5. Implementation steps

### Task 1 — Chốt matrix và tạo test phát hiện thiếu rule

**Files:**

- Create: `src/test/java/com/ngockhanh/clinic/accesscontrol/api/EndpointPermissionCoverageTest.java`.
- Modify: `docs/adr/0015-endpoint-permission-rbac.md`, `docs/architecture/05-api-and-security.md`.
- Create: `docs/api/endpoint-permissions.md` để ghi matrix HTTP đầy đủ và các exception auth.

- [ ] Chốt hai permission DELETE và mapping catalog; ghi rõ loại account, action thực tế và role được grant, phân biệt proposal với accepted contract.
- [ ] Viết `everyProductionBusinessEndpointHasAnExplicitPermissionRule`: lấy mapping production bằng `RequestMappingHandlerMapping` trong Spring test context, loại test-only controller; phân loại ba auth endpoint riêng. Kiểm tra cặp HTTP method/path được khai báo và matcher tương ứng với path mẫu có UUID hợp lệ.
- [ ] Viết `everyPermissionRuleTargetsAProductionEndpoint` và `specificTemplateRuleWinsOverParticipantWildcard`: bắt rule orphan, cặp rule trùng và rule specific bị che. Kiểm tra template chỉ có VIEW thì bị chặn, chỉ có TEMPLATE_DOWNLOAD thì đi qua security.
- [ ] Chạy test để xác nhận phát hiện đúng ba route thiếu trên baseline; không bỏ qua route chưa chốt chỉ để test xanh. Sau Task 2, chạy lại phải PASS.
- [ ] Cập nhật matrix và tham chiếu seed theo ADR hiện hành; đánh dấu những link source bị thiếu là giới hạn tài liệu, không tạo workflow mới.

### Task 2 — Bổ sung rule và kiểm chứng security chain thật

**Files:**

- Modify: `src/main/java/com/ngockhanh/clinic/accesscontrol/infrastructure/security/EndpointPermissions.java`.
- Modify only if needed: `src/main/java/com/ngockhanh/clinic/accesscontrol/infrastructure/configuration/SecurityConfiguration.java`.
- Modify: `src/test/java/com/ngockhanh/clinic/accesscontrol/api/AuthWebMvcTest.java`.
- Create: `src/test/java/com/ngockhanh/clinic/accesscontrol/api/OrganizationBatchSecurityWebMvcTest.java`, `src/test/java/com/ngockhanh/clinic/accesscontrol/api/CatalogSecurityWebMvcTest.java`.
- Reuse/update: `ParticipantSecurityWebMvcTest`, `ExaminationDetailSecurityWebMvcTest` ở cùng package.

**Interfaces:** Giữ `Rule(method, pattern, permission)` và `EndpointPermissions.apply(registry)`; chỉ thêm mapping đã được chốt ở Task 1.

- [ ] Viết test ba route thiếu trước khi thêm rule. Trường hợp đủ quyền dùng request hợp lệ và mock use case thành công; assert status/body chính xác và use case được gọi.
- [ ] Thêm DELETE Organization, DELETE Batch, GET catalog rule theo matrix accepted; giữ `anyRequest().denyAll()` và thứ tự literal trước wildcard.
- [ ] Test mỗi rule bằng cookie → mock `AuthenticateSessionUseCase` → authorities → security chain: anonymous 401; STAFF thiếu/sai permission 403; PATIENT có cùng permission 403; STAFF có đúng permission gọi được. Khi bị chặn, `verifyNoInteractions` với business use case.
- [ ] Test role-only ADMINISTRATOR/CLINIC_MANAGER không bypass; permission giả `ACCOUNT_STAFF` không nâng PATIENT thành STAFF; sai method, HEAD chưa cấp và URL không rule bị deny. Không dùng standalone MockMvc để chứng minh filter chain.
- [ ] Regression login/logout/me, allowed/disallowed Origin, preflight, duplicate/expired cookie, Redis unavailable 503, error envelope và `Cache-Control: no-store`; chạy coverage test Task 1 đến PASS.

### Task 3 — Bảo vệ Organization/Batch và catalog tại application

**Files:**

- Create: `src/main/java/com/ngockhanh/clinic/healthexamination/application/service/OrganizationBatchAccessPolicy.java`.
- Modify trong `src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/`: `CreateOrganizationUseCase.java`, `GetOrganizationByIdUseCase.java`, `ListOrganizationUseCase.java`, `UpdateOrganizationUseCase.java`, `DeleteOrganizationUseCase.java`.
- Modify trong cùng package usecase: `CreateHealthExaminationBatchUseCase.java`, `GetHealthExaminationBatchByIdUseCase.java`, `ListHealthExaminationBatchUseCase.java`, `UpdateHealthExaminationBatchUseCase.java`, `DeleteHealthExaminationBatchUseCase.java`.
- Modify: `OrganizationController.java`, `OrganizationBatchController.java` trong `healthexamination/api/controller`.
- Create: `src/main/java/com/ngockhanh/clinic/catalog/application/service/ServiceCatalogAccessPolicy.java`.
- Modify: `src/main/java/com/ngockhanh/clinic/catalog/application/usecase/ListServiceCatalogUseCase.java`, `src/main/java/com/ngockhanh/clinic/catalog/api/controller/ServiceCatalogController.java`.
- Create tests: `OrganizationBatchAccessPolicyTest.java`, `ServiceCatalogAccessPolicyTest.java` trong package application/service của module sở hữu.
- Modify: test của 11 use case và ba controller trên; call sites production/integration test tìm bằng CodeGraph và bổ sung lookup cho file chưa indexed.

**Interfaces:**

- `OrganizationBatchAccessPolicy`: `void requireOrganizationSearch/View/Create/Update/Delete(UserPrincipal principal)` và `void requireBatchView/Create/Update/Delete(UserPrincipal principal)`. Mỗi method map đúng một action permission của Task 1; principal null, thiếu userId, sai account type hoặc thiếu permission đều ACCESS_DENIED, tương tự Participant policy.
- `ServiceCatalogAccessPolicy`: `void requireRead(UserPrincipal principal)` dùng permission catalog đã chốt.
- Write use case: thay tham số cuối `UUID actor` bằng `UserPrincipal principal`; audit/createdBy lấy `principal.userId()` sau authorization.
- Read use case: thêm `UserPrincipal principal` làm tham số cuối; giữ command/query/IDs/response hiện có. Không giữ actor-only/read overload không kiểm tra quyền.

- [ ] Viết test gọi trực tiếp từng use case: principal null, PATIENT hoặc thiếu permission bị ACCESS_DENIED trước repository/query/audit; principal hợp lệ giữ kết quả nghiệp vụ hiện tại.
- [ ] Thêm hai policy ở module sở hữu; reuse `UserPrincipal` là published contract hiện hành, không đưa Spring Security context vào application/domain.
- [ ] Chuyển controller truyền principal cho cả read và write; update toàn bộ caller đã xác định. Không biến UUID actor từ job/event thành principal tự tạo có permission. Caller ngoài HTTP chưa có authenticated context phải xác định contract trước khi đổi.
- [ ] Giữ scope organization/batch/participant và state/version checks đã có. Không tự thêm phạm vi role theo phòng ban/batch vì schema grant không định nghĩa chúng. Với SRS Restricted footnote thiếu mô tả thực thi, ghi blocker phần phụ thuộc.
- [ ] Run policy/use-case/controller tests đến PASS; regression rollback audit, lost update và lịch sử xóa mềm trên PostgreSQL 18 khi các chữ ký call sites bị ảnh hưởng.

### Task 4 — Seed/grant và session snapshot theo contract

**Files:**

- Create only if needed: migration `V<next>__add_endpoint_authorization_permissions.sql` trong `src/main/resources/db/migration/`; chọn version chưa dùng tại lúc triển khai.
- Modify: `src/test/java/com/ngockhanh/clinic/infrastructure/migration/LocalAccessControlSeedIntegrationTest.java` và test migration PostgreSQL hiện có.
- Modify: `src/test/java/com/ngockhanh/clinic/accesscontrol/AuthIntegrationTest.java` khi cần chứng minh DB grant → login snapshot → HTTP permission.
- Modify docs: `docs/api/endpoint-permissions.md`, `docs/api/login.md`.

- [ ] Nếu chọn permission DELETE riêng, thêm code và role_permissions đúng matrix approved bằng Flyway migration mới. Nếu chọn UPDATE, dùng seed có sẵn; không tạo migration rỗng.
- [ ] Test PostgreSQL 18: permission tồn tại, grant đúng role, không cấp rộng cho toàn STAFF/PATIENT/ADMINISTRATOR. Catalog dùng grant đã accepted; nếu muốn read permission riêng phải bổ sung contract trước.
- [ ] Test login trả snapshot chứa grant và cookie thật gọi route được; grant thiếu dẫn đến 403. Không chỉ inject authorities vào MockMvc để tuyên bố seed/session đúng.
- [ ] Test/document snapshot: đổi grant DB giữ permission session cũ đến login lại hoặc revoke; sau revoke cookie cũ 401, sau login lại dùng grant mới. Không claim automatic revoke-on-grant-change đã có nếu workflow chưa triển khai.
- [ ] Run migration/auth integration tests đến PASS với PostgreSQL 18 và Redis khả dụng; ghi rõ Docker/Redis thiếu hoặc skip.

### Task 5 — Verification và hoàn thiện tài liệu

**Files:** Plan này, `docs/api/endpoint-permissions.md`, `docs/adr/0015-endpoint-permission-rbac.md`, `docs/architecture/05-api-and-security.md`; `ModuleVerificationTest.java` chỉ sửa nếu published contract bị thay đổi có chủ đích.

- [ ] Chạy focused checks: `./mvnw.cmd "-Dtest=AuthWebMvcTest,EndpointPermissionCoverageTest,OrganizationBatchSecurityWebMvcTest,CatalogSecurityWebMvcTest,ParticipantSecurityWebMvcTest,ExaminationDetailSecurityWebMvcTest" test`.
- [ ] Chạy policy/use-case tests và PostgreSQL/Redis checks của Task 3–4; ghi commands, tổng test, failure/skip và nguyên nhân.
- [ ] Chạy `./mvnw.cmd verify`, gồm module verification; không đổi H2 hoặc mock để thay thế PostgreSQL integration. Không claim full PASS nếu integration skip.
- [ ] Review diff: chỉ endpoint mapping, authorization tại module sở hữu, caller/tests/docs/migration thực sự cần; format các Java file thay đổi bằng formatter dự án. Giữ mọi thay đổi worktree có từ trước.
- [ ] Chốt matrix gồm 25 business endpoint, ba exception auth, behavior HEAD/preflight, 401/403/503 và session snapshot. Báo cáo module, API/schema/migration impact, checks và điểm contract chưa chốt.

## 6. Ngoài phạm vi và thứ tự thực hiện

Không triển khai quản trị role/permission UI, portal mới, tự động revoke khi account lock/grant change, login rate limit, đổi CSRF strategy hoặc frontend trong plan này. Đây là các workflow riêng, không phải điều kiện để lập xong tài liệu.

Thứ tự: chốt matrix → coverage test chỉ ra khoảng trống → rule HTTP → application guards → seed/session integration → full verify. Task 1 tạo regression đỏ có chủ đích và Task 2 sửa ngay trước khi chuyển các task độc lập tiếp theo. Với từng thay đổi code, viết test thất bại đúng hành vi rồi sửa nhỏ nhất và chạy lại.

Sau triển khai, frontend tiếp tục dùng permission từ `/api/v1/auth/me` để hiển thị thao tác; backend quyết định cuối cùng. Deploy migration trước khi dùng permission mới và tổ chức login lại/revoke session theo chính sách vận hành đã chốt.

## 7. Trạng thái verification của phiên lập plan

Đã kiểm tra code qua CodeGraph, endpoint inventory Amplicode và contract/migration trên worktree. Cấu hình method/path matching và denyAll được đối chiếu bằng Context7 với [Spring Security 7 authorization](https://docs.spring.io/spring-security/reference/7.0/servlet/authorization/authorize-http-requests.html).

Đây là thay đổi documentation-only. Chưa chạy Maven test/verify, chưa chứng minh runtime đã sửa, chưa thêm migration. Những lệnh trên là bước triển khai dự kiến, không phải kết quả đã PASS.
