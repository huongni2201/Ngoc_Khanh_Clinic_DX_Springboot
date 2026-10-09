# Tài liệu backend Ngọc Khánh Clinic

Cập nhật theo worktree ngày 2026-10-08. Bắt đầu từ trang này để tìm hợp đồng
hiện hành; trạng thái triển khai API được ghi riêng với quy tắc nghiệp vụ.

## Đọc theo nhu cầu

| Nhu cầu | Tài liệu |
|---|---|
| Quy tắc kỹ thuật và nguồn chuẩn | [PROJECT_RULES](../PROJECT_RULES.md), [AGENTS](../AGENTS.md) |
| Thuật ngữ Organization, Batch, Participant | [CONTEXT](../CONTEXT.md) |
| Kiến trúc, module và luồng nghiệp vụ | [Architecture](architecture/README.md) |
| API đang có và tương thích client/database | [API inventory](api/clean-slate-migration.md) |
| Đăng nhập, cookie, Redis và cấu hình | [Login](api/login.md) |
| Đơn vị và đợt khám | [Organization/Batch API](api/organizations-and-batches.md) |
| Danh sách người khám và nhập roster Excel | [Participant import/list](api/participant-import-and-list.md) |
| Thêm, sửa, hủy và kích hoạt lại người khám | [Participant manual changes](api/participant-manual-crud.md) |
| Đối soát dịch vụ Excel và báo cáo thanh toán Word | [Examination details/report](api/examination-details-and-report.md) |
| Quyết định kiến trúc đã chấp nhận | [ADR index](adr/README.md) |
| Phần chưa triển khai hoặc cần chốt | [Open items](architecture/07-open-items.md) |
| Kiểm thử và triển khai | [Operations](architecture/06-testing-and-operations.md) |

## Cách hiểu trạng thái

Architecture và ADR ghi quy tắc đã chấp nhận. API inventory ghi route có handler;
route có handler vẫn có thể bị chặn nếu chưa có permission rule. Bảng/record trong
schema không chứng minh một tính năng đã có API hoặc đã đủ điều kiện production.

Các plan cũ đã được bỏ; hợp đồng đã triển khai nằm trong architecture/API docs.
Phần chưa chốt được giữ trong Open items, không dùng làm chỉ dẫn triển khai đã duyệt.
Lịch sử quyết định import chỉ được giữ ở ADR liên quan.
