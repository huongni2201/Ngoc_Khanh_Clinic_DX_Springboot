# Rà soát backend NKC-DX — 2026-09-22

## Kết luận

Định hướng DDD + modular monolith + ports/adapters phù hợp với mục tiêu bảo trì:
chia theo nghiệp vụ, giữ domain độc lập với persistence, và kiểm soát giao tiếp
giữa module. Chưa thể kết luận implementation đã tối ưu hoặc chịu tải tốt:
backend hiện chỉ có một application class và một context-load test, chưa có
module nghiệp vụ, SQL, API hay phép đo hiệu năng để đánh giá.

Không cần tạo đủ 13 module, chuyển Maven multi-module, thêm microservices,
cache hay message broker ở giai đoạn này. Quy tắc tạo cấu trúc theo nhu cầu
và làm một vertical slice trước khi nhân rộng là điểm tốt nên giữ.

## Phạm vi và giới hạn

Đã đọc bốn Markdown gốc: `AGENTS.md`, `PROJECT_RULES.md`, `PROJECT_SKILLS.md`,
`HELP.md`; kiểm tra toàn bộ hai file Java, POM, cấu hình application,
Docker Compose, Maven Wrapper và danh sách file backend.

Không có `.codegraph/` nên không dùng CodeGraph. `git status --short` báo
thư mục không phải Git repository; không có diff/history để đối chiếu.
Không tìm thấy `docs/adr/`, `docs/architecture/` hoặc các tài liệu
`requirement-v2.3`, `use-case-v2.4`, `table-design-v2.9` trong backend;
tìm theo tên trong workspace cha cũng không thấy ba tài liệu nguồn.
Vì vậy báo cáo chỉ đánh giá nền tảng và tính nhất quán, không xác nhận hoặc
thiết kế thêm business contract. Không sửa mã nghiệp vụ hay tự tạo schema.

## Phát hiện theo mức ưu tiên

### P1 — Chưa có build/test baseline chạy thành công

- `mvnw.cmd test` và `mvnw.cmd verify` đều báo `Cannot index into a null array`
  rồi `Cannot start maven from wrapper`, trước khi chạy Maven.
- Fallback `mvn -o test` thất bại ở `compile`; `mvn -o verify` thất bại ở
  `testCompile`. Cả hai báo `Fatal error compiling` liên quan tới
  `.m2/repository/org/projectlombok/lombok/1.18.46/lombok-1.18.46.jar`.
- Đây là triệu chứng quan sát được, chưa chứng minh Lombok không tương thích
  Java 25 hay xác định lỗi thuộc mã nguồn, cache hoặc môi trường.
- Ưu tiên tái hiện wrapper và lấy stack trace compiler để xác định nguyên nhân,
  sau đó đạt một build sạch trước khi thêm chức năng.

### P1 — Thiếu nguồn nghiệp vụ và tài liệu quyết định

`AGENTS.md` yêu cầu đọc tài liệu nguồn, ADR và kiến trúc trước thay đổi đáng kể,
nhưng những nguồn đó chưa có trong workspace được kiểm tra. Các quy tắc tóm tắt
không đủ thay thế hợp đồng đầy đủ cho status, permission, API field và schema.
Đưa đúng phiên bản vào nơi truy cập được và ghi đường dẫn chính xác; không tạo
nội dung giả để lấp các thư mục còn thiếu.

### P2 — Package thực tế khác chuẩn dự án

`src/main/java/com/ngockhanh/clinic/NgocKhanhClinicApplication.java:1` và test
dùng `com.ngockhanh.clinic`, trong khi `PROJECT_RULES.md` mục 3 quy định
`com.nkc.clinic`. Nên thống nhất trước khi sinh thêm module để tránh phải đổi
hàng loạt import/package về sau. Ví dụ Modulith trong mục 32 còn tham chiếu
`NgocKhanhClinicBackendApplication.class`, khác application class hiện có.

### P2 — Ranh giới kiến trúc mới được mô tả, chưa được kiểm chứng

`pom.xml` có Web MVC, Security, SQL Server driver và Flyway, nhưng chưa khai báo
MyBatis starter, Spring Modulith hay Testcontainers. Test duy nhất là
`contextLoads()`; chưa có kiểm tra ranh giới module/layer hoặc SQL Server.
Đây là thiếu nền tảng triển khai, chưa phải bằng chứng vi phạm xuyên module
vì các module chưa tồn tại.

Khi bắt đầu vertical slice, bổ sung các thành phần baseline cần thiết và test
phù hợp, xác định rõ contract nào được công khai. Không coi mọi Java `public`
class trong `application/` là contract dùng chung. Tách kiểm tra ranh giới module
và kiểm tra dependency giữa các layer; đừng giả định một test bao phủ cả hai.

### P2 — Chưa có quy trình khởi động backend có database

`application.yaml` chỉ chứa tên application. `docker-compose.yml` chỉ có
`services:` rỗng. Chưa có datasource setup, migration, test profile hay hướng
dẫn cấp biến môi trường. `HELP.md` là tài liệu scaffold và bị `.gitignore` bỏ qua.

Cần README được quản lý cùng project, mô tả JDK, Maven, cấu hình local bằng biến
môi trường, SQL Server, cách chạy test và migration. Chỉ viết migration sau khi
có table-design chuẩn. Chưa kiểm chứng khả năng startup hoặc kết nối database.

### P2 — Hướng dẫn skill có các tên không tương ứng khả năng thực tế

Tại thời điểm kiểm tra:

| Mục trong guide | Vấn đề |
| --- | --- |
| `run-tests` được mô tả chạy Maven | Skill thực tế chỉ hướng dẫn Gradle |
| `security-best-practices` dùng cho Spring Security | Skill khai báo hỗ trợ Python, JS/TS, Go; không hỗ trợ Java |
| `postgresql-table-design` | Không phù hợp làm mặc định cho SQL Server |
| `setup-pre-commit` | Workflow hướng Node/Husky, không phải mặc định Maven |
| `code-review` dùng chung | Workflow dành cho diff, yêu cầu base/spec, không phải audit toàn repository |

Đã thêm mục installed/compatibility ngay đầu `PROJECT_SKILLS.md` để phân biệt
capability mong muốn với skill thực sự có thể dùng. Phần catalog bên dưới còn
mô tả các capability mong muốn; phải đọc cùng các giới hạn này.

### P3 — Markdown lặp quy tắc và có một điểm mơ hồ về DTO

`AGENTS.md` và `PROJECT_RULES.md` lặp nhiều quy tắc về stack, layer, nghiệp vụ,
bảo mật và test; sửa một nơi dễ bỏ sót nơi khác. Nên dần rút `AGENTS.md` thành
điểm vào ngắn với thứ tự đọc và đường dẫn, giữ quy tắc chi tiết ở một nguồn.
Không tự xóa hoặc thay đổi quy tắc nghiệp vụ trong lần audit này.

`PROJECT_RULES.md` mục 5 cho application sở hữu "mapping between
transport/application/domain models", trong khi mục 6 quy định
`api -> application -> domain`. Cần làm rõ: API chuyển request thành application
command/query; application không import HTTP DTO. Đây là điểm mơ hồ trong mô tả,
chưa phải lỗi implementation.

## Cấu trúc nên tiến tới

Giữ một Maven application. Khi đã có contract và bắt đầu module đầu tiên:

```text
src/main/java/com/nkc/clinic/
  NgocKhanhClinicApplication.java
  patient/
    api/              # HTTP request/response và controller
    application/      # use case, command/query, transaction
    domain/           # invariant và repository port
    infrastructure/   # MyBatis adapter và persistence record
src/main/resources/
  mapper/patient/     # XML khi có query cần dùng
  db/migration/      # migration dựa trên table-design
src/test/java/com/nkc/clinic/
  patient/           # domain, use case, API, persistence tests
```

Đây là hướng cấu trúc từ quy tắc hiện có, không phải danh sách thư mục cần tạo
ngay. Chỉ tách interface, model hoặc mapper khi chúng giữ một boundary có thật.
Không tạo generic BaseService/Repository để giảm số dòng một cách hình thức.
Các quyết định về outbox, transaction xuyên aggregate, contract giữa module và
ownership cần được chốt theo workflow thực tế, không suy đoán từ tên module.

## Skill đã cài

Đã sao chép nguyên bộ từ `C:/Users/PC/.codex/skills/` sang `.agents/skills/`:

1. `architecture-decision-records`
2. `architecture-patterns`
3. `codebase-design`
4. `domain-modeling`
5. `api-design-principles`
6. `diagnosing-bugs`
7. `tdd`

Tổng 22 file gồm SKILL.md và tài liệu hỗ trợ. Đã đối chiếu số file và SHA-256
của từng file với bản nguồn. Đây là bản cài từ local, không tuyên bố là phiên
bản upstream mới nhất. Skills có thể được dùng từ lượt tiếp theo.

`error-handling-patterns` đã có tại `../.agents/skills/`; không sao chép trùng.
Khi tách backend khỏi workspace cha, cần mang skill này theo nếu muốn tiếp tục
sử dụng. Các skill global khác vẫn tồn tại, không bị gỡ khỏi máy.
Ví dụ generic trong các skill luôn chịu giới hạn stack và quy tắc của project.

## Kiểm chứng và thay đổi

| Kiểm tra | Kết quả |
| --- | --- |
| Java runtime | 25.0.4.1 |
| `.\mvnw.cmd test` | Thất bại khi khởi động wrapper |
| `.\mvnw.cmd verify` | Thất bại khi khởi động wrapper |
| `mvn -o test` | Thất bại ở compile; chưa chạy test |
| `mvn -o verify` | Thất bại ở testCompile; chưa chạy test |
| So khớp skill đã cài | 7 skill / 22 file khớp SHA-256 |
| SQL Server/Testcontainers/module integration | Chưa chạy; chưa có test tương ứng |

Files thay đổi: thêm báo cáo này, cập nhật `PROJECT_SKILLS.md`, thêm 22 file
trong bảy thư mục skill. Build thử sinh output trong `target/` đã được ignore.
Không đổi bounded context, source code, public API, migration hoặc test.

Thứ tự xử lý tiếp: khôi phục build/test baseline; cung cấp tài liệu nguồn;
thống nhất package; hoàn thiện foundation và một patient vertical slice có test;
sau đó mới nhân rộng pattern sang module khác. Chưa có số liệu để nhận định
hiệu năng, và chưa thể xác nhận tính đúng nghiệp vụ khi thiếu tài liệu nguồn.
