# **USE CASE SPECIFICATION HOÀN CHỈNH**

## **Hệ thống hỗ trợ chuyển đổi số phòng khám đa khoa ngoại trú**

Phiên bản: 2.5
Baseline và cập nhật: 22/09/2026
Liên kết Requirement: requirement-v2.4
Liên kết schema: table-design-v2.10

# **1. Mục tiêu**

Tài liệu này mô tả toàn bộ Use Case của hệ thống theo requirement mới nhất, bao gồm các thay đổi quan trọng:

- Payment gate trước khi thực hiện dịch vụ.
- Multiple Order Round trong một Encounter.
- Quick-view Side Drawer (Popup trượt bên phải xem nhanh hồ sơ bệnh nhân từ màn tra cứu).
- Xuất Giấy chỉ định CLS kèm địa điểm phòng chuyên môn và hướng dẫn lộ trình di chuyển (kết quả trả tự động về máy bác sĩ, không chờ bản in tại phòng CLS).
- Xuất Hóa đơn tạm thu & Bảng kê viện phí kèm mã VietQR động hỗ trợ nộp tiền nhanh.
- Lab chuyên biệt theo panel/analyte.
- Ultrasound Workspace có image/metadata.
- Result auto-return.
- Prescription được phát hành/in và hiển thị trên Patient Portal; không có dispensing workflow.
- Web-first Patient Portal.
- Role-aware UI.
- Không sử dụng queue-number workflow; Front Desk tạo Encounter và Doctor Worklist điều phối bằng Journey state.
- Ngoại trú, không bao gồm inpatient.
- Khám sức khỏe người lớn (18+) theo Mẫu số 03 theo Thông tư 25 và khám sức khỏe doanh nghiệp thuộc scope.
Mỗi Patient trong một hồ sơ/lượt khám sức khỏe có một healthCheckRecordCode/SHS duy nhất dùng chung cho toàn bộ phiếu của lượt khám. Mẫu số 03 theo Thông tư 25 là phiếu tổng và bắt buộc in barcode Code 128 của SHS để quét và mở đúng bệnh nhân/hồ sơ.

Kênh thông báo ngoài hệ thống trong baseline chỉ sử dụng SMS.

Chuẩn bị hồ sơ và in Mẫu số 03 trước check-in; khám tại phòng khám hoặc doanh nghiệp; check-in dùng lại hồ sơ/SHS. Chỉ Quản lý bệnh viện import nhân viên; đổi giá một hạng mục áp dụng toàn bộ nhân viên của batch.

# **2. Actors**

# **Front Desk**

# **- Tiếp nhận bệnh nhân.**

# **- Tra cứu/tạo/cập nhật hồ sơ bệnh nhân.**

# **- Tra cứu chính xác identificationNumber trước khi tạo Patient.**

# **- Tạo Encounter.**

# **- Chọn phòng/bác sĩ.**

# **- Thu phí khám ban đầu và phí CLS sau khi bác sĩ tạo chỉ định.**

# **- Quản lý lịch hẹn/tái khám.**

# **- Quản lý doanh nghiệp/nhân sự/đợt khám sức khỏe và in Mẫu số 03 theo Thông tư 25 tại quầy.**

# **- Quét mã vạch trên phiếu Mẫu số 03 theo Thông tư 25 để mở đúng hồ sơ khám sức khỏe.**

# **Doctor**

# **- Xem danh sách bệnh nhân chờ khám.**

# **- Khám lâm sàng.**

# **- Xem Patient Safety và lịch sử; trong MVP, thuốc đang dùng được suy ra từ các prescription do phòng khám phát hành còn hiệu lực/gần nhất.**

# **- Tạo Order Round/Service Request.**

# **- Tạo chỉ định CLS/Order Round khi cần; bác sĩ không thực hiện thu phí.**

# **- Xem kết quả tự động trả về.**

# **- Tạo chỉ định bổ sung.**

# **- Kết luận.**

# **- Kê đơn.**

# **- Tạo lịch tái khám; lịch tái khám sinh từ Encounter phải giữ sourceEncounterId.**

# **- Hoàn tất Encounter.**

# **- Quét mã vạch trên phiếu Mẫu số 03 theo Thông tư 25 để mở đúng hồ sơ/lượt khám trước khi nhập thông tin.**

# **- Với khám sức khỏe doanh nghiệp, chỉ Doctor được chọn/tick subset hạng mục cho từng nhân viên trong scope HealthCheckBatchService; không được thêm dịch vụ ngoài scope.**

# **Front Desk**

# **- Tra cứu khoản phí CLS phát sinh từ Order Round.**

# **- Xác nhận thanh toán.**

# **- Cập nhật Payment và Payment Authorization.**

# **- In phiếu chỉ định/lộ trình CLS sau khi thanh toán.**

# **Lab Technician**

# **- Xem worklist dịch vụ xét nghiệm đã được phép thực hiện.**

# **- Xác minh đúng bệnh nhân.**

# **- Quản lý specimen.**

# **- Nhập/nhận kết quả.**

# **- Verify/Final/Correct result.**

# **Imaging/ECG Staff**

# **- Xem worklist dịch vụ đã được phép thực hiện.**

# **- Thực hiện siêu âm/X-quang/ECG.**

# **- Lập và Final report.**

# **Quản lý bệnh viện**

# **- Theo dõi vận hành phòng khám, Journey, SLA/TAT, doanh thu, khám sức khỏe doanh nghiệp và báo cáo; không quản trị cấu hình kỹ thuật hệ thống.**

- Chỉ Quản lý bệnh viện (CLINIC_MANAGER) được upload, map, preview và confirm import danh sách nhân viên; Front Desk không được thực hiện các bước này.

# **Quản trị hệ thống**

# **- Quản trị user/role/permission, cấu hình hệ thống, catalog kỹ thuật, template, integration, mapping và audit; không mặc định phê duyệt nghiệp vụ khám.**

# **Patient**

# **- Cung cấp thông tin.**

# **- Thanh toán.**

# **- Đi đến các phòng CLS theo phiếu chỉ định.**

# **- Xem dữ liệu được phép trên Patient Portal.**

# **3. Use Case Inventory**

# **Inventory v2.5 giữ các use case nghiệp vụ đang áp dụng, bao gồm chuẩn bị trước check-in, check-in tại điểm khám và điều chỉnh giá đồng loạt; không có queue/dispensing.**

# **ID | Use Case | Actor chính | Priority**

# **UC-PAT-01 | Tìm bệnh nhân & Quick-View | Front Desk/Bác sĩ | Must**

# **UC-PAT-02 | Tạo/Cập nhật hồ sơ bệnh nhân | Front Desk | Must**

# **UC-PAT-03 | Tra cứu identificationNumber & chặn tạo Patient trùng identificationNumber | Front Desk | Must**

# **UC-PAT-04 | Xem Patient Profile & Timeline | Nhân viên có quyền | Must**

# **UC-PAT-05 | Xem tài liệu/hình ảnh/lịch sử liên hệ | Nhân viên có quyền | Should**

# **UC-REC-01 | Tiếp nhận bệnh nhân, tạo Encounter & thu phí khám | Front Desk | Must**

# **UC-REC-02 | Chuyển phòng/bác sĩ sau tiếp nhận | Front Desk/Người có quyền | Should**

# **UC-JRN-01 | Theo dõi Patient Journey | Front Desk/Bác sĩ/Quản lý bệnh viện | Must**

# **UC-JRN-02 | Theo dõi thời gian chờ & SLA/TAT | Quản lý bệnh viện | Should**

# **UC-CLN-01 | Doctor Worklist & Bắt đầu khám | Bác sĩ | Must**

# **UC-CLN-02 | Ghi nhận khám lâm sàng | Bác sĩ | Must**

# **UC-CLN-03 | Patient Safety, lịch sử & Timeline trong lúc khám | Bác sĩ | Must**

# **UC-CLN-04 | Review kết quả & cảnh báo bất thường | Bác sĩ | Must**

# **UC-CLN-05 | Tạo chỉ định bổ sung | Bác sĩ | Must**

# **UC-CLN-06 | Kết luận & Hoàn tất Encounter | Bác sĩ | Must**

# **UC-ORD-01 | Tạo/Quản lý Order Round & Service Request | Bác sĩ | Must**

# **UC-ORD-02 | Hủy/Điều chỉnh Service Request | Bác sĩ/Người có quyền | Should**

# **UC-ORD-03 | Chuyển khoản CLS cần thu về Front Desk | Hệ thống/Front Desk | Must**

# **UC-ORD-04 | In phiếu chỉ định & lộ trình CLS sau thanh toán | Front Desk/Hệ thống | Must**

# **UC-BIL-01 | Thu phí khám ban đầu | Front Desk | Must**

# **UC-BIL-02 | Thu phí dịch vụ CLS theo Order Round | Front Desk | Must**

# **UC-BIL-03 | Authorize Service Request sau thanh toán/miễn hợp lệ | Hệ thống | Must**

# **UC-BIL-04 | Thu thêm cho Order Round bổ sung | Front Desk | Must**

# **UC-BIL-05 | Hoàn/Hủy/Đối soát | Front Desk/Quản lý bệnh viện | Should**

# **UC-LAB-01 | Lab Worklist & xác nhận đúng bệnh nhân | Lab | Must**

# **UC-LAB-02 | Quản lý specimen & barcode | Lab | Must**

# **UC-LAB-03 | Nhận/nhập kết quả xét nghiệm | Lab/LIS | Must**

# **UC-LAB-04 | Xử lý panel/analyte & abnormal flag | Lab/Hệ thống | Must**

# **UC-LAB-05 | Verify/Final/Correct kết quả xét nghiệm | Lab có quyền | Must**

# **UC-LAB-06 | Quản lý Lab Catalog & machine mapping | Quản trị hệ thống | Should**

# **UC-US-01 | Ultrasound Worklist/Workspace & template | Siêu âm | Must**

# **UC-US-02 | Nhận/xem ảnh & metadata siêu âm | Siêu âm/Hệ thống | Should**

# **UC-US-03 | Lập & Final báo cáo siêu âm | Siêu âm | Must**

# **UC-IMG-01 | Imaging/X-ray Worklist & study reference | X-quang | Must**

# **UC-IMG-02 | Trả/Final report X-quang | X-quang | Must**

# **UC-ECG-01 | ECG Worklist & thực hiện dịch vụ | ECG | Must**

# **UC-ECG-02 | Trả kết quả ECG | ECG | Must**

# **UC-RES-01 | Auto-return result & thông báo bác sĩ | Hệ thống | Must**

# **UC-RX-01 | Tạo/Chỉnh sửa prescription | Bác sĩ | Must**

# **UC-RX-02 | Cảnh báo an toàn kê đơn | Hệ thống/Bác sĩ | Should**

# **UC-RX-03 | Phát hành/Version prescription & in đơn | Bác sĩ/Hệ thống | Must**

# **UC-APT-01 | Tạo/Điều chỉnh lịch hẹn | Front Desk/Bác sĩ | Should**

# **UC-APT-02 | Tạo lịch tái khám từ Encounter | Bác sĩ | Should**

# **UC-APT-03 | Xác nhận/Arrived/No-show/Cancel appointment | Front Desk/Bệnh nhân | Should**

# **UC-NOT-01 | Gửi SMS nhắc lịch | Hệ thống | Should**

# **UC-NOT-02 | Gửi SMS thông báo kết quả đã sẵn sàng/thay đổi lịch hẹn | Hệ thống | Should**

# **UC-PORT-01 | Patient đăng nhập/truy cập Portal | Bệnh nhân | Should**

# **UC-PORT-02 | Xem/tải lịch sử, kết quả & tài liệu | Bệnh nhân | Should**

# **UC-PORT-03 | Xem prescription & lịch hẹn | Bệnh nhân | Should**

# **UC-PORT-04 | Cập nhật thông tin liên hệ | Bệnh nhân | Could**

# **UC-RPT-01 | Dashboard vận hành | Quản lý bệnh viện | Should**

# **UC-RPT-02 | Báo cáo TAT/SLA | Quản lý bệnh viện | Should**

# **UC-RPT-03 | Báo cáo doanh thu & xuất dữ liệu | Quản lý bệnh viện | Should**

# **UC-ADM-01 | Quản lý User/Role/Permission theo phòng | Quản trị hệ thống | Must**

# **UC-ADM-02 | Quản lý phòng/dịch vụ/bảng giá/danh mục | Quản trị hệ thống | Must**

# **UC-ADM-03 | Quản lý template | Quản trị hệ thống | Must**

# **UC-ADM-04 | Quản lý integration & mapping | Quản trị hệ thống | Should**

# **UC-ADM-05 | Audit log & cấu hình notification | Quản trị hệ thống | Must**

# **UC-HC-01 | Xem/tìm danh sách doanh nghiệp khám sức khỏe | Front Desk/Quản lý bệnh viện | Must**

# **UC-HC-10 | Tạo doanh nghiệp khám sức khỏe | Front Desk/Quản lý bệnh viện | Must**

# **UC-HC-11 | Chọn hạng mục & giá doanh nghiệp theo đợt | Front Desk/Quản lý bệnh viện | Must**

# **UC-HC-12 | Tải file Excel mẫu nhân viên | Quản lý bệnh viện | Must**

# **UC-HC-13 | Import kết quả khám từ Excel | Front Desk/Quản lý bệnh viện/Nhân viên chuyên môn | Must**

# **UC-HC-14 | Báo cáo chi tiết hạng mục theo nhân viên | Front Desk/Quản lý bệnh viện | Must**

# **UC-HC-15 | Báo cáo tổng hợp theo hạng mục | Front Desk/Quản lý bệnh viện | Must**

# **UC-HC-02 | Xem chi tiết doanh nghiệp, nhân sự và đợt khám | Front Desk/Quản lý bệnh viện | Must**

# **UC-HC-03 | Import danh sách nhân sự từ Excel | Quản lý bệnh viện | Must**

# **UC-HC-04 | Validate nhân sự và điều kiện đủ 18 tuổi | Hệ thống | Must**

# **UC-HC-05 | Tạo/Quản lý HealthCheckBatch | Front Desk/Quản lý bệnh viện | Must**

# **UC-HC-06 | Tiếp nhận & in Mẫu số 03 theo Thông tư 25 có mã vạch cho bệnh nhân cá nhân | Front Desk | Must**

# **UC-HC-07 | Chuẩn bị hồ sơ và in hàng loạt Mẫu số 03 trước check-in | Front Desk/Quản lý bệnh viện có quyền chuẩn bị | Must**

# **UC-HC-08 | Reprint giấy khám sức khỏe | Front Desk/Quản lý bệnh viện | Should**

# **UC-HC-09 | Quét barcode SHS trên Mẫu số 03 và mở đúng hồ sơ/lượt khám | Front Desk/Bác sĩ/Nhân viên chuyên môn | Must**

UC-HC-16 | Sinh bộ phiếu theo template đã cấu hình | Front Desk/Quản lý bệnh viện | Must

UC-HC-17 | Check-in hồ sơ đã chuẩn bị tại phòng khám hoặc doanh nghiệp | Front Desk/Nhân viên tiếp nhận có quyền | Must

UC-HC-18 | Điều chỉnh giá hạng mục đồng loạt toàn batch | Quản lý bệnh viện/Người có quyền điều chỉnh giá | Must

# **Tổng số Use Case cấp nghiệp vụ: 76**

# **4. Mapping theo module**

# **Patient Management**

# **UC-PAT-01 → UC-PAT-05**

# **Front Desk**

# **UC-REC-01 → UC-REC-02**

# **UC-BIL-01**

# **UC-APT-01 → UC-APT-03**

# **Clinical**

# **UC-CLN-01 → UC-CLN-06**

# **UC-ORD-01 → UC-ORD-04**

# **UC-RX-01 → UC-RX-03**

# **Front Desk**

# **UC-BIL-02 → UC-BIL-05**

# **Laboratory**

# **UC-LAB-01 → UC-LAB-06**

# **Ultrasound / Imaging / ECG**

# **UC-US-01 → UC-US-03**

# **UC-IMG-01 → UC-IMG-02**

# **UC-ECG-01 → UC-ECG-02**

# **UC-RES-01**

# **Portal / Notification / Reporting / Quản trị hệ thống**

# **UC-PORT-01 → UC-PORT-04**

# **UC-NOT-01 → UC-NOT-02**

# **UC-RPT-01 → UC-RPT-03**

# **UC-ADM-01 → UC-ADM-05**

## Corporate / Adult Health Check

# **UC-HC-01 → UC-HC-18**

# **5. Use Case chi tiết các luồng lõi**

# **UC-REC-01 — Tiếp nhận bệnh nhân, tạo Encounter & thu phí khám**

# **Actor: Front Desk**

# **Tiền điều kiện**

# **- Nhân viên đã đăng nhập.**

# **- Đã xác định đúng Patient hoặc vừa tạo Patient mới.**

# **Main flow**

# **1. Front Desk tìm/chọn Patient.**

# **2. Quick View hiển thị cảnh báo an toàn, bệnh nền, thuốc đang dùng và lần khám gần nhất.**

# **3. Front Desk nhập lý do khám, loại khám, chuyên khoa và mức ưu tiên.**

# **4. Chọn phòng và bác sĩ.**

# **5. Hệ thống xác định dịch vụ khám ban đầu và đơn giá hiện hành.**

# **6. Front Desk xác nhận tiếp nhận.**

# **7. Trong một transaction/idempotent command, hệ thống:**

# **- tạo Encounter với status=IN_PROGRESS;**

# **- tạo Encounter Assignment;**

# **- tạo Journey;**

# **- tạo invoice phí khám ban đầu;**

# **- ghi audit/outbox.**

# **8. Front Desk thu phí khám bằng tiền mặt/POS/QR.**

# **9. Payment thành công → invoice được cập nhật và Journey.currentStage=WAITING_FOR_DOCTOR.**

# **10. Patient xuất hiện trong Doctor Worklist.**

# **Business rules**

# **- Không có Reception Queue Ticket hoặc Exam Queue Ticket.**

# **- Không tạo Encounter chỉ bằng thao tác mở form.**

# **- Retry/double-click không được tạo hai Encounter hoặc hai payment.**

# **- Phí khám ban đầu được Front Desk thu trước khi patient vào Doctor Worklist.**

# **UC-CLN-01 — Doctor Worklist & Bắt đầu khám**

# **1. Bác sĩ mở worklist theo phòng/ca.**

# **2. Worklist có ít nhất hai nhóm:**

# **- Chờ khám lần đầu.**

# **- Chờ bác sĩ kết luận.**

# **3. Bác sĩ chọn Patient/Encounter.**

# **4. Xác minh Patient Safety Header.**

# **5. Bấm Bắt đầu khám.**

# **6. Journey.currentStage=IN_EXAM.**

# **7. Encounter vẫn IN_PROGRESS.**

# **UC-CLN-02 — Khám lâm sàng**

# **1. Ghi lý do khám, bệnh sử, tiền sử và sinh hiệu.**

# **2. Ghi nhận khám lâm sàng và chẩn đoán sơ bộ.**

# **3. Bác sĩ chọn:**

# **- Kết luận ngay nếu đủ thông tin; hoặc**

# **- Tạo Order Round nếu cần CLS.**

# **UC-ORD-01 — Tạo/Quản lý Order Round & Service Request**

# **1. Bác sĩ tạo Order Round 1 hoặc Round bổ sung.**

# **2. Chọn dịch vụ lẻ/order set/favorite.**

# **3. Hệ thống tính giá snapshot theo bảng giá hiện hành.**

# **4. Khi xác nhận:**

# **- ServiceRequest.status=ORDERED.**

# **- PaymentAuthorization.status=PENDING đối với dịch vụ cần thu phí.**

# **- sinh invoice/khoản phải thu cho Order Round.**

# **- Journey.currentStage=WAITING_FOR_DIAGNOSTIC_PAYMENT.**

# **5. Encounter vẫn IN_PROGRESS.**

# **UC-ORD-03 — Chuyển khoản CLS cần thu về Front Desk**

# **1. Sau khi bác sĩ xác nhận Order Round, hệ thống tự tạo khoản phải thu và đưa Encounter vào danh sách Front Desk cần thu phí CLS.**

# **2. Front Desk mở Encounter/Patient/Order Round và xem dịch vụ, đơn giá, tổng tiền cùng trạng thái thanh toán.**

# **3. Bác sĩ chỉ tạo chỉ định CLS; không thu tiền và không cần in phiếu thanh toán để kích hoạt luồng.**

# **4. Retry/double-click không được tạo Invoice, khoản phải thu hoặc Order trùng.**

# **UC-BIL-02 — Thu phí dịch vụ CLS**

# **Actor: Front Desk**

# **1. Front Desk tra cứu theo Encounter/Patient/Invoice hoặc danh sách khoản CLS đang chờ thu.**

# **2. Hệ thống hiển thị các khoản phải thu của Order Round.**

# **3. Chọn phương thức tiền mặt/POS/QR.**

# **4. Xác nhận thanh toán.**

# **5. Payment.status=CONFIRMED.**

# **6. Các Service Request đủ điều kiện có PaymentAuthorization.status=AUTHORIZED.**

# **7. Journey.currentStage=WAITING_FOR_DIAGNOSTIC.**

# **8. Cho phép in Phiếu chỉ định/lộ trình CLS.**

# **UC-ORD-04 — In phiếu chỉ định & lộ trình CLS sau thanh toán**

# **1. Chỉ các Service Request có authorization hợp lệ mới được đưa vào phiếu thực hiện.**

# **2. Phiếu gồm:**

# **- Patient/Encounter;**

# **- danh sách dịch vụ;**

# **- phòng/tầng;**

# **- hướng dẫn chuẩn bị;**

# **- hướng dẫn di chuyển;**

# **- hướng dẫn quay lại khu vực phòng bác sĩ.**

# **3. Bệnh nhân không phải chờ lấy bản in kết quả tại phòng CLS.**

# **4. Result Final được auto-return về bác sĩ.**

# **UC-LAB-01 — Lab Worklist & xác nhận đúng bệnh nhân**

# **1. Worklist hiển thị Service Request Lab có authorization hợp lệ.**

# **2. KTV đối chiếu tối thiểu hai thông tin nhận diện.**

# **3. Bắt đầu specimen/workflow.**

# **4. ServiceRequest.status chuyển IN_PROGRESS khi thực hiện.**

# **UC-LAB-05 — Verify/Final/Correct kết quả xét nghiệm**

# **1. Review dữ liệu Patient/Specimen/Result.**

# **2. Verify → VERIFIED.**

# **3. Final → FINAL.**

# **4. FINAL không bị overwrite.**

# **5. Correction tạo version mới CORRECTED.**

# **6. Result Final kích hoạt UC-RES-01.**

# **UC-RES-01 — Auto-return result & điều phối patient sang phòng/bác sĩ tiếp theo**

# **1. Mỗi result Final gắn đúng Patient + Encounter + Service Request.**

# **2. Hệ thống cập nhật tiến độ Order Round.**

# **3. Bác sĩ xem được partial results ngay khi có.**

# **4. Khi tất cả required Service Requests đã hoàn tất, Journey.currentStage=WAITING_FOR_CONCLUSION.**

# **5. Encounter được điều phối sang phòng/bác sĩ tiếp theo bằng Journey.current_department_id/current_room_id và Encounter Assignment; Worklist của phòng đích tự hiển thị patient ở nhóm phù hợp, bao gồm Chờ bác sĩ kết luận khi currentStage=WAITING_FOR_CONCLUSION.**

# **6. Không yêu cầu xác nhận bệnh nhân đã quay lại và không tạo queue ticket mới.**

# **UC-CLN-04 — Review kết quả & cảnh báo bất thường**

# **1. Bác sĩ mở Encounter ở nhóm Chờ kết luận.**

# **2. Xem result theo Order Round.**

# **3. Abnormal result phải có text/icon, không chỉ màu.**

# **4. Bác sĩ quyết định:**

# **- Kết luận; hoặc**

# **- UC-CLN-05 nếu cần thêm CLS.**

# **UC-CLN-05 — Tạo chỉ định bổ sung**

# **1. Bác sĩ tạo Order Round N+1.**

# **2. Flow lặp:**

# **Bác sĩ chỉ định → Hệ thống tạo khoản cần thu → Front Desk thu phí → Authorization → Diagnostic → Result.**

# **3. Các Round trước không bị thay đổi.**

# **4. Encounter vẫn IN_PROGRESS.**

# **UC-CLN-06 — Kết luận & Hoàn tất Encounter**

# **1. Bác sĩ review toàn bộ dữ liệu cần thiết.**

# **2. Ghi diagnosis/kết luận cuối.**

# **3. Có thể kê đơn.**

# **4. Có thể tạo lịch tái khám.**

# **5. Hệ thống kiểm tra còn required Service Request chưa hoàn tất hay không.**

# **6. Nếu đủ điều kiện:**

# **- Encounter.status=COMPLETED.**

# **- Journey.currentStage=COMPLETED.**

# **7. Override completion phải có quyền và audit.**

# **UC-RX-01 — Tạo/Chỉnh sửa prescription**

# **1. Bác sĩ tạo prescription trong Encounter.**

# **2. Thêm thuốc, hàm lượng, liều, đường dùng, thời gian và hướng dẫn.**

# **3. Hệ thống hiển thị cảnh báo an toàn nếu có rule/dữ liệu.**

# **4. Bác sĩ phát hành đơn.**

# **5. Đơn đã phát hành được version hóa khi sửa.**

# **6. Có thể in đơn hoặc hiển thị trên Patient Portal.**

# **7. Không có workflow bán/cấp phát thuốc trong scope hiện tại.**

# 5A. Use Case — Khám sức khỏe người lớn & doanh nghiệp

## Flow doanh nghiệp ưu tiên

## Company

## → Tạo HealthCheckBatch

## → Chọn hạng mục khám từ Service Catalog + chốt giá doanh nghiệp theo batch

## → Quản lý bệnh viện tải mẫu và import roster, chưa tạo Patient/Encounter

## → Validate danh sách nhân viên

## → Chuẩn bị: exact lookup/tạo Patient theo identificationNumber + Encounter HEALTH_CHECK PREPARED + HealthCheckRecord/SHS + snapshot hành chính

## → Bulk print Mẫu số 03 trước check-in; chưa sinh phiếu chuyên môn

→ Tại phòng khám hoặc doanh nghiệp: quét SHS, đối chiếu định danh và xác nhận check-in trên hồ sơ đã chuẩn bị; không tạo lượt/SHS hoặc in phiếu mới

## → Bác sĩ mở Clinical Workspace, chọn subset hạng mục trong scope HealthCheckBatchService

## → Backend tạo OrderRound + ServiceRequest + PaymentAuthorization=NOT_REQUIRED + HealthCheckBatchEmployeeService cho các hạng mục bác sĩ chọn; không tạo hóa đơn cá nhân cho dịch vụ thuộc gói doanh nghiệp

## → Sinh phiếu chuyên môn tương ứng theo template A3/A4/A5/A6/CUSTOM

## → Thực hiện khám trên worklist dùng chung Lab/Siêu âm/X-quang/ECG

## → Có thể nhập trực tiếp hoặc Import kết quả Excel vào cùng ServiceRequest/result domain

## → Báo cáo chi tiết theo nhân viên

## → Báo cáo tổng hợp theo hạng mục

## → Chốt/xuất báo cáo doanh nghiệp

## UC-HC-01 — Xem/tìm danh sách doanh nghiệp khám sức khỏe

# **Actor: Front Desk / Quản lý bệnh viện**

# **1. User mở phân hệ Khám sức khỏe doanh nghiệp.**

# **2. Hệ thống hiển thị Company list theo mô hình company-first.**

# **3. User tìm/lọc theo tên, mã doanh nghiệp, mã số thuế, trạng thái hoặc đợt khám.**

# **4. Mỗi Company hiển thị số nhân sự, số phiếu đã tạo, số đã khám và HealthCheckBatch gần nhất.**

# **5. User click Company để mở chi tiết.**

## UC-HC-02 — Xem chi tiết doanh nghiệp, nhân sự và đợt khám

# **Actor: Front Desk / Quản lý bệnh viện**

# **1. Hệ thống hiển thị thông tin Company, liên hệ và nguồn chi trả mặc định.**

# **2. User chuyển giữa Tổng quan, Danh sách nhân sự và Đợt khám.**

# **3. Trong từng HealthCheckBatch, CTA tải mẫu/import nhân sự chỉ hiển thị cho Quản lý bệnh viện; Front Desk có quyền xem roster/tiếp nhận nhưng không import.**

# **4. Một Company có thể có nhiều HealthCheckBatch.**

## UC-HC-03 — Import danh sách nhân sự từ Excel theo đợt

# **Actor: Quản lý bệnh viện (CLINIC_MANAGER). Backend từ chối Front Desk ở mọi bước upload/mapping/preview/confirm; SYSTEM_ADMIN không mặc định có quyền này.**

# **1. User mở HealthCheckBatch và chọn Import nhân sự; có thể tải file mẫu qua UC-HC-12 trước khi import.**

# **2. Upload file Excel.**

# **3. Hệ thống đọc header và đề xuất mapping cột.**

# **4. User kiểm tra/chỉnh mapping.**

# **5. Hệ thống normalize dữ liệu và gọi UC-HC-04.**

# **6. Hiển thị Validation Preview theo tổng dòng, hợp lệ, warning và blocking error.**

# **7. User xác nhận import các dòng hợp lệ.**

# **Rule: identificationNumber giữ kiểu text; không tự sinh dữ liệu thiếu. Confirm chỉ tạo/cập nhật roster CompanyEmployee và HealthCheckBatchEmployee, lưu dữ liệu hành chính theo batch; không tự tạo Patient/Encounter. patientId có thể NULL trước chuẩn bị; liên kết Patient đã có không bị đổi ngầm khi import lại. Bước chuẩn bị hồ sơ mới exact lookup/tạo Patient theo identificationNumber và liên kết nhân viên.**

## UC-HC-04 — Validate nhân sự và điều kiện đủ 18 tuổi

# **Actor: Hệ thống**

# **1. Kiểm tra employeeCode, fullName, gender, dateOfBirth, identificationNumber và duplicate employeeCode/identificationNumber trong file.**

# **2. Kiểm tra tuổi theo ngày khám dự kiến khi chuẩn bị/in trước; check-in kiểm tra lại tuổi theo ngày thực tế. Thiếu ngày khám dự kiến thì chưa được chuẩn bị/in hồ sơ.**

# **3. Nếu chưa đủ 18 tuổi → blocking error.**

# **4. Kiểm tra DOB không hợp lệ, identificationNumber không hợp lệ và duplicate identificationNumber trong file.**

# **5. Trả warning cho các field tùy chọn thiếu.**

# **6. Chỉ bản ghi hợp lệ mới được chọn vào print batch.**

# **Boundary: không chuyển tự động sang Mẫu số 01/Mẫu số 02 vì khám sức khỏe trẻ em ngoài scope.**

## UC-HC-05 — Tạo/quản lý HealthCheckBatch

# **Actor: Front Desk / Quản lý bệnh viện**

# **1. User tạo đợt khám từ Company Detail.**

# **2. Nhập mã/tên đợt, khoảng ngày khám dự kiến, lý do, nguồn chi trả và địa điểm khám (phòng khám hoặc doanh nghiệp), tên/địa chỉ điểm khám.**

# **3. User cấu hình hạng mục khám và giá doanh nghiệp theo UC-HC-11.**

# **4. Hệ thống gắn HealthCheckBatch với Company.**

# **5. Batch dùng template Mẫu số 03 theo Thông tư 25 cho người từ đủ 18 tuổi.**

# **6. User theo dõi tiến độ và dùng batch làm ngữ cảnh cho import nhân viên, bulk print, import kết quả và báo cáo.**

## UC-HC-06 — Tiếp nhận và in Mẫu số 03 theo Thông tư 25 có mã vạch cho bệnh nhân cá nhân

# **Actor: Front Desk**

# **1. Tại Reception, user chọn Khám sức khỏe thay vì Khám bệnh ngoại trú.**

# **2. Hệ thống tái sử dụng dữ liệu Patient.**

# **3. Tính tuổi tại ngày khám; nếu <18 thì chặn flow.**

# **4. User bổ sung trường hành chính còn thiếu.**

# **5. Nếu có hồ sơ chuẩn bị cho lần khám, resolve bằng SHS và thực hiện UC-HC-17; không tạo mới. Nếu chưa có hồ sơ, xác định Patient và tạo lượt HEALTH_CHECK/HealthCheckRecord/SHS idempotently, check-in trong ngày. Chỉ in khi chưa có phiếu hoặc người dùng yêu cầu.**

# **6. User chọn Xem trước giấy khám sức khỏe.**

# **7. Frontend render Print Preview theo paperSize/orientation của template version, có barcode Code 128 và SHS đọc được; không hard-code A4 khác với version.**

# **8. User chọn In → browser/OS mở Print Dialog.**

# **Postcondition: ghi nhận PRINT_PREVIEW_GENERATED / PRINT_REQUESTED; không tự khẳng định giấy đã in vật lý thành công.**

# UC-HC-07 — Chuẩn bị hồ sơ và in hàng loạt Mẫu số 03 trước check-in

# Actor: Front Desk / Quản lý bệnh viện có quyền chuẩn bị hồ sơ và in; quyền này không bao gồm import nhân viên.

# 1. User chọn một hoặc nhiều nhân viên thuộc đúng batch, roster đã được Quản lý import/xác nhận; xác định ngày khám dự kiến.

# 2. Hệ thống loại các row có blocking error khỏi khả năng chọn.

# 3. Với từng nhân viên hợp lệ, exact lookup Patient theo identificationNumber, tạo nếu chưa có và liên kết CompanyEmployee. Trong command idempotent, tạo hoặc tái sử dụng Encounter HEALTH_CHECK PREPARED, Journey REGISTERED, HealthCheckRecord/SHS và snapshot từ roster của batch. Không tạo ServiceRequest/HealthCheckBatchEmployeeService; chưa đưa vào Doctor Worklist.

# 4. Hệ thống render/in Mẫu số 03 bằng master template version của batch. Phiếu chuyên môn chưa được sinh ở bước này vì hạng mục của từng nhân viên sẽ do bác sĩ chọn trong Clinical Workspace. Mỗi Encounter dùng một SHS và barcode Code 128 nằm trên Mẫu số 03.

# **5. UI hiển thị số phiếu và số trang dự kiến.**

# **6. User xác nhận In.**

# **7. Browser/OS chịu trách nhiệm chọn printer và gửi job tới printer.**

# **Architecture boundary: backend không chọn printer và không truy cập USB printer/Windows spooler của workstation.**

Postcondition: có bộ phiếu mang SHS cho từng hồ sơ chuẩn bị trước khi người khám đến. In lại/retry giữ hồ sơ cũ. UC-HC-17 chỉ xác nhận có mặt và tiếp tục lượt này; không yêu cầu in lại khi thông tin không đổi.

Failure paths: identificationNumber/DOB không hợp lệ hoặc khác định danh Patient chưa được đối chiếu; thiếu ngày dự kiến; không đủ tuổi; hồ sơ bị hủy/thay thế; concurrent provision. Các trường hợp này phải được xử lý trước khi phát hành, không tạo hồ sơ trùng.

# UC-HC-08 — Reprint giấy khám sức khỏe

# Actor: Front Desk / Quản lý bệnh viện

# 1. User mở HealthCheckRecord/Patient/CompanyEmployee tương ứng với lần khám cần in lại.

# 2. Chọn Xem trước hoặc In lại.

# 3. Hệ thống tạo lại AdultHealthCheckPrintData từ snapshot hành chính đã lưu trên chính HealthCheckRecord và template version gốc; không đọc lại các field hành chính hiện tại từ Patient. Giữ nguyên healthCheckRecordCode/SHS và barcode của hồ sơ.

# **4. Frontend mở Print Preview và browser Print Dialog.**

# **5. Hệ thống ghi REPRINT_REQUESTED.**

# **Rule: reprint không tạo Patient, CompanyEmployee, Encounter, HealthCheckRecord hoặc HealthCheckBatch mới; reprint phải giữ nguyên snapshot hành chính của lần khám gốc.**

## Quy tắc thực thi in áp dụng cho mọi Use Case in

# **- Frontend/browser: render Print Preview theo paperSize/orientation của từng template (A3/A4/A5/A6/CUSTOM), page break và gọi print flow.**

# **- Browser/OS: chọn printer, copies, page range và dispatch job.**

# **- Backend: dữ liệu/DTO, authorization, template/version, audit.**

# **- window.print()/Print Dialog không đồng nghĩa PRINTED_SUCCESSFULLY.**

# **- Nếu tương lai cần silent printing, printer routing hoặc spooler acknowledgement thì dùng Local Print Agent: Web FE → Local Print Agent → Windows Print Spooler → Printer.**

# **UC-HC-09 — Quét barcode SHS trên Mẫu số 03 để mở hồ sơ**

# **Actor chính: Front Desk / Doctor / Nhân viên chuyên môn có quyền**

# **Tiền điều kiện**

# **- Người dùng đã đăng nhập và có quyền xem/nhập hồ sơ khám sức khỏe tại phòng hiện tại.**

# **- Mẫu số 03 thuộc một hồ sơ/lượt khám sức khỏe hợp lệ và đã được in barcode của healthCheckRecordCode/SHS.**

# **- Máy quét USB hoạt động ở chế độ HID/keyboard wedge hoặc người dùng có thể nhập mã đọc được dưới mã vạch.**

# **Main flow**

# **1. Người dùng đặt con trỏ tại vùng nhận mã quét hoặc sử dụng scanner listener của màn hình.**

# **2. Người dùng quét barcode SHS trên phiếu tổng Mẫu số 03 theo Thông tư 25.**

# **3. Hệ thống chuẩn hóa mã và tra cứu chính xác theo healthCheckRecordCode/SHS duy nhất; không tìm gần đúng theo họ tên.**

# **4. Hệ thống kiểm tra trạng thái mã, trạng thái hồ sơ/lượt khám và quyền truy cập của người dùng.**

# **5. Hệ thống resolve đúng Patient + hồ sơ/lượt khám sức khỏe + Company/HealthCheckBatch nếu có.**

# **6. Hệ thống mở đúng workspace của hồ sơ. Nếu Encounter PREPARED thì hiển thị chưa check-in; việc quét/xem không tự xác nhận có mặt. Người có quyền dùng UC-HC-17 để check-in.**

# **7. Patient Safety Header hiển thị tối thiểu họ tên, ngày sinh, giới tính, mã bệnh nhân, doanh nghiệp/đợt khám và trạng thái hồ sơ.**

# **8. Người dùng đối chiếu thông tin trên màn hình với phiếu/người khám rồi bắt đầu nhập thông tin khám sức khỏe.**

# **Alternative / Error flow**

# **A1. Mã không đúng định dạng hoặc không tồn tại → hiển thị Không tìm thấy phiếu; không mở hồ sơ khác.**

# **A2. Mã đã bị vô hiệu hóa do phiếu/hồ sơ bị hủy hoặc thay thế → hiển thị trạng thái và chặn nhập liệu.**

# **A3. Người dùng không đủ quyền → trả Access denied, không để lộ thông tin y tế ngoài mức cho phép và ghi audit.**

# **A4. Máy quét không đọc được → cho phép nhập thủ công SHS được in dưới barcode trên Mẫu số 03.**

# **A5. Có nhiều bản ghi cùng mã → coi là lỗi toàn vẹn dữ liệu, không tự chọn một hồ sơ và gửi cảnh báo cho Quản trị hệ thống.**

# **Postcondition**

# **- Đúng hồ sơ/lượt khám được mở, không tạo Patient, Encounter hay HealthCheckBatch mới.**

# **- Ghi audit gồm mã quét, actor, thời điểm, workstation, kết quả tra cứu và hồ sơ được mở.**

# **Business rules**

# **- Mỗi Patient trong một hồ sơ/lượt khám sức khỏe có đúng một healthCheckRecordCode/SHS hoạt động dùng chung cho Mẫu số 03 và toàn bộ phiếu chuyên môn của lượt khám. Bulk print tạo một SHS riêng cho từng nhân viên, không tạo mã riêng từng tài liệu.**

# **- Barcode trên Mẫu số 03 encode healthCheckRecordCode/SHS hoặc reference opaque tương đương, không chứa họ tên, identificationNumber, SĐT, chẩn đoán hoặc kết quả.**

# **- Reprint bất kỳ phiếu nào giữ nguyên SHS của hồ sơ; reprint Mẫu số 03 giữ nguyên barcode. Chỉ khi toàn bộ hồ sơ/lượt khám bị hủy hoặc thay thế thì SHS cũ mới mất hiệu lực.**

# **UC-HC-10 — Tạo doanh nghiệp khám sức khỏe**

# **Actor: Front Desk / Quản lý bệnh viện**

# **Tiền điều kiện: user có quyền quản lý khách hàng doanh nghiệp.**

# **Main flow**

# **1. User chọn Tạo doanh nghiệp tại phân hệ Khám sức khỏe doanh nghiệp.**

# **2. Nhập mã doanh nghiệp, tên, mã số thuế nếu có, một người liên hệ chính, chức vụ người liên hệ, SĐT người liên hệ, địa chỉ và ghi chú.**

# **3. Baseline chỉ quản lý một người liên hệ chính cho mỗi Company; chỉnh thông tin liên hệ là cập nhật trực tiếp Company, không quản lý danh sách contact riêng.**

# **4. Hệ thống kiểm tra trùng mã doanh nghiệp/mã số thuế và cảnh báo các bản ghi nghi ngờ trùng.**

# **5. User xác nhận tạo.**

# **6. Hệ thống tạo Company và mở Company Detail.**

# **Postcondition: Company tồn tại độc lập và có thể có nhiều HealthCheckBatch.**

# **UC-HC-11 — Chọn hạng mục khám và giá doanh nghiệp cho đợt**

# **Actor: Front Desk / Quản lý bệnh viện**

# **Tiền điều kiện: Company đã tồn tại; user đang tạo hoặc sửa HealthCheckBatch ở trạng thái cho phép chỉnh.**

# **Main flow**

# **1. User mở cấu hình HealthCheckBatch.**

# **2. Hệ thống hiển thị toàn bộ Service Catalog đang hoạt động, ví dụ 50 hạng mục.**

# **3. User tích chọn các hạng mục doanh nghiệp ký cho đợt này, ví dụ 10/50 hạng mục.**

# **4. Với mỗi hạng mục đã chọn, hệ thống hiển thị giá chuẩn tham chiếu.**

# **5. User có quyền nhập negotiatedUnitPrice riêng cho doanh nghiệp/đợt.**

# **6. Hệ thống lưu basePriceSnapshot và negotiatedUnitPrice cho từng HealthCheckBatchService.**

# **7. Hệ thống tính giá dự kiến dựa trên số nhân viên dự kiến × danh mục đã chọn nhưng không coi đây là giá quyết toán cuối cùng.**

# **Rules**

# **- Giá doanh nghiệp không thay đổi bảng giá bệnh nhân lẻ.**

# **- Cùng một hạng mục trong batch luôn dùng chung negotiatedUnitPrice. Muốn đổi giá sau cấu hình phải dùng UC-HC-18 để cập nhật tất cả assignment/snapshot liên quan, không sửa giá riêng một người.**

# **- READY khóa cấu hình thông thường. Thay scope/template sau đó phải có quyền, lý do và audit; điều chỉnh giá dùng UC-HC-18. FINALIZED/CLOSED phải mở lại theo quyền trước chỉnh giá và chốt lại sau điều chỉnh.**

# **UC-HC-12 — Tải file Excel mẫu nhân viên**

# **Actor: Quản lý bệnh viện (CLINIC_MANAGER). Front Desk không được truy cập workflow import nhân viên.**

# **Main flow**

# **1. User mở Company Detail hoặc HealthCheckBatch.**

# **2. Chọn Tải file Excel mẫu.**

# **3. Hệ thống tạo file mẫu có header chuẩn cho import nhân viên.**

# **4. File gồm employeeCode, fullName, dateOfBirth, gender, identificationNumber bắt buộc; identificationNumberIssueDate, identificationNumberIssuePlace, ethnicity, subjectType, payerSource, bloodGroup, province, ward, addressDetail, occupation/jobTitle, workplaceOrSchool và healthCheckReason tùy chọn theo nghiệp vụ. Dữ liệu đã confirm được giữ theo nhân viên trong batch để tạo snapshot, không chỉ nằm ở file tạm.**

# **5. User tải file, điền dữ liệu và sử dụng lại trong UC-HC-03.**

# **Rule: identificationNumber trong file mẫu phải được định dạng Text.**

# **UC-HC-13 — Import kết quả khám doanh nghiệp từ Excel**

# **Actor: Front Desk / Quản lý bệnh viện / Nhân viên chuyên môn có quyền**

# **Tiền điều kiện: HealthCheckBatch đã có nhân viên; bác sĩ đã chọn hạng mục cho nhân viên tương ứng và ServiceRequest đã được tạo.**

# **Main flow**

# **1. User chọn Import kết quả tại HealthCheckBatch.**

# **2. Upload file Excel kết quả.**

# **3. Hệ thống đọc file và cho mapping các cột.**

# **4. Mỗi dòng phải resolve được nhân viên bằng employeeCode hoặc identificationNumber và hạng mục bằng serviceCode; không match gần đúng theo tên.**

# **5. Hệ thống kiểm tra serviceCode vừa thuộc scope của batch vừa đã được bác sĩ gán cho nhân viên thông qua HealthCheckBatchEmployeeService; service chỉ thuộc batch nhưng chưa được gán cho nhân viên không được nhận kết quả.**

# **6. Hệ thống hiển thị Validation Preview: hợp lệ, warning, blocking error và conflict.**

# **7. User xác nhận các dòng hợp lệ.**

# **8. Hệ thống resolve HealthCheckBatchEmployeeService → ServiceRequest. Kết quả được ghi vào cùng clinical result domain dùng cho toàn phòng khám: LabResult/LabResultValue hoặc DiagnosticReport; corporate wrapper chỉ cập nhật billable nếu cần, không lưu kết quả y khoa riêng.**

# **Error flow**

# **- Không tìm thấy nhân viên → blocking error.**

# **- Hạng mục ngoài scope batch → blocking error.**

# **- Trùng dòng trong file → blocking error hoặc yêu cầu chọn bản ghi.**

# **- Kết quả đã Final và dữ liệu import khác → không overwrite; yêu cầu correction/version theo quyền.**

# **Postcondition: kết quả gắn đúng Patient + Encounter + ServiceRequest và có audit import job; HealthCheckBatchEmployeeService chỉ giữ liên kết doanh nghiệp/giá.**

# **UC-HC-14 — Báo cáo chi tiết hạng mục theo từng nhân viên**

# **Actor: Quản lý bệnh viện / Front Desk có quyền báo cáo**

# **Main flow**

# **1. User chọn Báo cáo chi tiết theo nhân viên tại HealthCheckBatch.**

# **2. Hệ thống tạo ma trận: mỗi hàng là một nhân viên, mỗi cột là một hạng mục trong batch.**

# **3. Nếu HealthCheckBatchEmployeeService billable và ServiceRequest liên kết đã được thực hiện theo rule nghiệp vụ, ô tương ứng được đánh dấu.**

# **4. Hệ thống lấy unitPriceSnapshot của hạng mục; tổng tiền được tính trực tiếp từ các row billable, không lưu cột thành tiền dẫn xuất.**

# **5. Mỗi hàng có Tổng tiền nhân viên = SUM(unitPriceSnapshot) của các ô billable được đánh dấu.**

# **6. Cuối báo cáo hiển thị Grand Total chi tiết.**

# **Ví dụ: nhân viên A khám A/B/C thì đánh dấu ba ô A/B/C và tổng tiền A là giá A + B + C; nhân viên B khám B/C/D tương tự.**

# **Rule: báo cáo tài chính MVP lấy dữ liệu từ HealthCheckBatchEmployeeService; billable chỉ chuyển true khi ServiceRequest hoàn tất hợp lệ. Trạng thái/kết quả chuyên môn lấy từ ServiceRequest/result domain chung. Báo cáo chi tiết nội dung kết quả theo từng analyte/report để sprint sau.**

# **UC-HC-15 — Báo cáo tổng hợp theo hạng mục**

# **Actor: Quản lý bệnh viện / Front Desk có quyền báo cáo**

# **Main flow**

# **1. User chọn Báo cáo tổng hợp theo hạng mục.**

# **2. Với mỗi hạng mục của batch, hệ thống đếm số nhân viên thực tế khám và billable.**

# **3. Hiển thị: serviceCode/tên hạng mục, số người khám, negotiatedUnitPrice, thành tiền = số người × đơn giá.**

# **4. Hệ thống tính Grand Total tổng hợp trực tiếp từ SUM(unitPriceSnapshot) của cùng tập HealthCheckBatchEmployeeService billable đang dùng cho UC-HC-14.**

# **5. Báo cáo chi tiết và báo cáo tổng hợp chỉ khác cách GROUP BY; backend không tạo trạng thái hoặc bảng đối soát riêng.**

# **6. Automated test phải chứng minh Grand Total của hai báo cáo bằng nhau trên cùng dataset.**

# **UC-HC-16 — Sinh bộ phiếu theo template đã cấu hình**

# **Actor: Front Desk / Quản lý bệnh viện**

# **Tiền điều kiện:**

# **- HealthCheckBatch đã có danh sách hạng mục.**

# **- Mỗi hạng mục bắt buộc in đã có template mapping hợp lệ.**

# **- Template/version có paperSize, orientation và renderMode.**

# **Main flow**

# **1. User chọn nhân viên và resolve HealthCheckRecord đã chuẩn bị của đúng lần khám. Nếu chưa có thì thực hiện UC-HC-07 theo quyền; không tạo record/SHS mới chỉ vì sinh thêm bộ phiếu.**

# **2. Hệ thống luôn thêm Mẫu số 03 vào print plan.**

# **3. Hệ thống lấy các HealthCheckBatchEmployeeService và ServiceRequest của từng nhân viên.**

# **4. Với batch đã READY, dùng specialist template version đã khóa trên HealthCheckBatchService. Khám lẻ resolve mapping/version hợp lệ rồi lưu vào generated document. Reprint ưu tiên version gốc của document, kể cả đã retired.**

# **5. Nếu renderMode=ONE_PER_SERVICE, sinh một generated_document riêng và tạo generated_document_service_requests trỏ tới ServiceRequest tương ứng.**

# **6. Nếu renderMode=MERGE_BY_TEMPLATE, chỉ gom các ServiceRequest cùng map vào đúng logical template/version thành một generated_document; mỗi ServiceRequest được liên kết qua generated_document_service_requests.**

# **7. Nếu renderMode=MASTER_FORM, document là phiếu tổng Mẫu số 03.**

# **8. Mỗi document lấy paperSize/orientation từ template version; hỗ trợ A3/A4/A5/A6/CUSTOM.**

# **9. Tất cả document của cùng hồ sơ hiển thị cùng healthCheckRecordCode/SHS; Mẫu số 03 in barcode Code 128 của SHS.**

# **10. Hệ thống hiển thị Print Preview theo đúng thứ tự và khổ giấy của print plan.**

# **11. User xác nhận in; browser/OS chịu trách nhiệm dispatch tới printer.**

# **Error flow**

# **- Dịch vụ bắt buộc in thiếu version đã khóa/hợp lệ theo ngữ cảnh → chặn và liệt kê đúng service. Không yêu cầu mapping active mới thay thế version đã khóa của batch hoặc document reprint.**

# **- Template version đã retired nhưng là version gốc của document reprint → cho phép reprint theo version gốc, không tự đổi layout.**

# **- Template không khai báo paperSize/renderMode → coi là cấu hình không hợp lệ.**

# **Rules**

# **- Không suy luận “10 hạng mục = 1 phiếu” hoặc “1 hạng mục = 1 phiếu”.**

# **- Số phiếu thực tế phụ thuộc mapping + renderMode của template upload.**

# **- Không merge hai template khác nhau chỉ vì cùng service_type hoặc cùng department. Template engine dùng chung cho bệnh nhân lẻ và doanh nghiệp; generated_documents ↔ ServiceRequest là many-to-many qua generated_document_service_requests.**

# **Rule: cả hai báo cáo bắt buộc dùng cùng nguồn HealthCheckBatchEmployeeService billable và cùng unitPriceSnapshot; tính nhất quán là invariant của backend/query.**

UC-HC-17 — Check-in hồ sơ đã chuẩn bị tại điểm khám

Actor: Front Desk / Nhân viên tiếp nhận được phân quyền tại điểm khám.

Tiền điều kiện: người khám có HealthCheckRecord/SHS hợp lệ và Encounter PREPARED; điểm khám có kết nối hệ thống theo phương án triển khai đã chốt.

1. Quét/nhập SHS và resolve hồ sơ đã chuẩn bị, không tìm gần đúng theo tên.

2. Đối chiếu người thật với thông tin định danh; kiểm tra quyền, trạng thái hồ sơ và đủ 18 tuổi theo ngày khám thực tế.

3. Xác nhận có mặt; backend cập nhật PREPARED → IN_PROGRESS, checked-in time/actor, ngày khám thực tế và Journey/worklist theo điều kiện tiếp nhận trong một command idempotent.

4. Tiếp tục khám bằng Patient/Encounter/HealthCheckRecord/SHS cũ. Dùng phiếu đã in; không tự tạo hoặc yêu cầu in phiếu mới. Phân công theo department và bàn/khu khám tại doanh nghiệp khi không có phòng vật lý tại phòng khám.

Error flows: SHS sai/hủy/thay thế, không đủ quyền/tuổi hoặc không đúng định danh → chặn; đã check-in → trả hồ sơ hiện có, không tạo dữ liệu trùng. Sửa thông tin bắt buộc trên phiếu phải audit và in lại khi cần, giữ SHS của cùng hồ sơ.

Postcondition: người khám xuất hiện trong worklist phù hợp; người chưa đến vẫn PREPARED/REGISTERED, không billable chỉ vì đã in. Offline capture/synchronization không tự thuộc use case này.

UC-HC-18 — Điều chỉnh giá một hạng mục cho toàn bộ batch

Actor: Quản lý bệnh viện / Người có quyền điều chỉnh giá; không mặc định cấp cho mọi Front Desk.

1. Chọn batch, hạng mục và giá mới; xem số assignment bị ảnh hưởng, gồm cả đã hoàn tất/billable; nhập lý do.

2. Nếu batch FINALIZED/CLOSED, thực hiện mở lại theo quyền trước điều chỉnh; không sửa ngầm báo cáo đã xuất.

3. Backend serialize với command chọn dịch vụ trên cùng batch service; cập nhật negotiatedUnitPrice, tất cả unitPriceSnapshot của assignment hiện có và unit_price_snapshot trên ServiceRequest gói liên quan trong cùng transaction. Giữ nguyên basePriceSnapshot, giá bán lẻ và batch khác.

4. Audit giá cũ/mới, actor, thời điểm, lý do. Assignment tạo sau dùng giá mới. Nếu transaction lỗi thì không lưu một phần.

5. Sinh lại báo cáo hiện hành trên cùng tập dữ liệu: SUM snapshot = quantity billable × đơn giá mới; chốt lại batch theo quyền khi cần. Bản báo cáo đã xuất vẫn là bản lịch sử.

Acceptance: hai assignment giá cũ 100.000 đổi đồng loạt sang 120.000 cho tổng 240.000; bao gồm row billable, chưa billable, tạo đồng thời và tạo sau; không có đường API sửa giá riêng từng nhân viên.

# **UC-NOT-01 — Gửi SMS nhắc lịch**

# **Actor: Hệ thống**

# **Tiền điều kiện: có lịch hẹn hợp lệ và Patient có số điện thoại hiện tại để gửi SMS. Không yêu cầu xác thực số điện thoại trước khi gửi.**

# **Main flow**

# **1. Scheduler lấy các lịch đến thời điểm nhắc theo cấu hình.**

# **2. Hệ thống render SMS từ template đã duyệt.**

# **3. Hệ thống snapshot patients.phone vào notifications.recipient_phone, tạo notification idempotently và gửi qua SMS Provider adapter.**

# **4. Lưu QUEUED/SENT/DELIVERED khi provider hỗ trợ; cập nhật FAILED/RETRYING khi lỗi.**

# **5. Ghi audit và provider message ID.**

# **Error flow: số điện thoại sai, provider timeout hoặc từ chối gửi → không làm thay đổi trạng thái lịch hẹn; retry theo policy và cho phép gửi lại theo quyền.**

# **UC-NOT-02 — Gửi SMS thông báo kết quả đã sẵn sàng**

# **Actor: Hệ thống / Nhân viên có quyền gửi lại**

# **Tiền điều kiện: kết quả/hồ sơ đã được người có thẩm quyền phát hành và có số điện thoại hợp lệ.**

# **Main flow**

# **1. Sự kiện phát hành kết quả kích hoạt notification.**

# **2. Hệ thống tạo SMS không chứa chẩn đoán hoặc kết quả y tế chi tiết.**

# **3. SMS chỉ thông báo kết quả đã sẵn sàng và cung cấp hướng dẫn hoặc đường dẫn bảo mật khi Patient Portal được bật.**

# **4. Gửi qua SMS Provider adapter, lưu trạng thái và provider message ID.**

# **5. Nhân viên được phép xem lỗi và gửi lại mà không tạo gửi trùng ngoài ý muốn.**

# **Boundary: mọi thông báo ngoài hệ thống trong baseline hiện tại chỉ đi qua SMS.**

# **6. State Model & Use Case Dependencies**

# **6.1. State ownership**

# **Encounter.status**

# **PREPARED | IN_PROGRESS | COMPLETED | CANCELED. PREPARED chỉ dùng HEALTH_CHECK chuẩn bị trước check-in; tiếp nhận ngoại trú thông thường vẫn tạo IN_PROGRESS.**

# **Journey.currentStage**

# **REGISTERED**

# **→ WAITING_FOR_DOCTOR**

# **→ IN_EXAM**

# **→ WAITING_FOR_DIAGNOSTIC_PAYMENT**

# **→ WAITING_FOR_DIAGNOSTIC**

# **→ DIAGNOSTIC_IN_PROGRESS**

# **→ WAITING_FOR_RESULTS**

# **→ WAITING_FOR_CONCLUSION**

# **→ IN_CONCLUSION**

# **→ COMPLETED**

Trong luồng HEALTH_CHECK chuẩn bị trước, Journey giữ REGISTERED khi Encounter PREPARED. Quét xem/in không đưa vào worklist; check-in hợp lệ mới kích hoạt luồng khám.

# **ServiceRequest.status**

# **ORDERED | IN_PROGRESS | COMPLETED | CANCELED**

# **PaymentAuthorization.status**

# **NOT_REQUIRED | PENDING | AUTHORIZED | WAIVED | REVOKED**

# **Payment.status**

# **PENDING | CONFIRMED | FAILED | PARTIALLY_REFUNDED | REFUNDED**

# **DiagnosticResult.status**

# **DRAFT | VERIFIED | FINAL | CORRECTED**

# **Prescription.status**

# **DRAFT | ISSUED | CANCELED | CORRECTED**

# **6.2. Dependency flow**

# **Patient**

# **→ Front Desk Reception**

# **→ Encounter + Initial Exam Payment**

# **→ WAITING_FOR_DOCTOR**

# **→ Doctor Exam**

# **Nếu không cần CLS:**

# **→ Conclusion**

# **→ Prescription nếu cần**

# **→ Complete**

# **Nếu cần CLS:**

# **→ Order Round N**

# **→ Hệ thống chuyển khoản CLS cần thu về Front Desk**

# **→ Front Desk thu phí CLS**

# **→ Payment Authorization**

# **→ Lab/Ultrasound/X-ray/ECG**

# **→ Result Final**

# **→ WAITING_FOR_CONCLUSION**

# **→ Doctor Review**

# **→ Conclusion hoặc Order Round N+1**

# **→ Prescription nếu cần**

# **→ Complete**

# **6.3. Idempotency bắt buộc**

# **- Create Encounter.**

# **- Initial consultation payment.**

# **- Submit Order Round.**

# **- Diagnostic payment/webhook.**

# **- Final/Correct result.**

# **- Issue prescription.**

# **- Transfer room/doctor.**

# **- Reprint không được tạo entity nghiệp vụ mới.**

- Chuẩn bị hồ sơ/bulk print: một lượt/SHS hợp lệ cho nhân viên trong batch; retry không tạo thêm.

- Check-in hồ sơ đã chuẩn bị.

- Điều chỉnh giá đồng loạt toàn batch, serialize với bác sĩ chọn dịch vụ.

# **7. Priority Definition**

- Must: cần có để demo/triển khai flow lõi.
- Should: quan trọng nhưng có thể triển khai ngay sau core flow.
- Could: nâng cao, không block MVP.

# **8. Use Case ngoài phạm vi**

Không tạo Use Case cho:

- Admission.
- Inpatient bed.
- Ward round.
- Daily inpatient medical order.
- Inpatient discharge.
- Full pharmaceutical procurement/multi-store management.
- Native mobile app.
- Mandatory AI diagnosis.

# **9. Traceability quan trọng**

# **Requirement | Use Cases**

# **Unified Patient & Quick-View Drawer | UC-PAT-01 → UC-PAT-05**

# **Front Desk Reception & Initial Exam Payment | UC-REC-01, UC-BIL-01**

# **Encounter & Assignment | UC-REC-01 → UC-REC-02**

# **Patient Journey | UC-JRN-01 → UC-JRN-02**

# **Doctor Worklist & Clinical Examination | UC-CLN-01 → UC-CLN-06**

# **Multiple Order Round | UC-ORD-01, UC-CLN-05**

# **Chuyển khoản CLS cần thu về Front Desk | UC-ORD-03**

# **Front Desk CLS Payment & Authorization | UC-BIL-02 → UC-BIL-05**

# **Diagnostic Routing Sheet | UC-ORD-04**

# **Lab Workflow | UC-LAB-01 → UC-LAB-06**

# **Ultrasound / X-ray / ECG | UC-US-01 → UC-US-03, UC-IMG-01 → UC-IMG-02, UC-ECG-01 → UC-ECG-02**

# **Auto Result Return | UC-RES-01**

# **Prescription | UC-RX-01 → UC-RX-03**

# **Appointment | UC-APT-01 → UC-APT-03**

# **Patient Portal | UC-PORT-01 → UC-PORT-04**

# **RBAC / Catalog / Integration / Audit | UC-ADM-01 → UC-ADM-05**

# **SMS-only Notification | UC-NOT-01 → UC-NOT-02**

# **Adult & Corporate Health Check | UC-HC-01 → UC-HC-18**

# **Web Printing Boundary & pre-check-in preparation | UC-HC-06, UC-HC-07, UC-HC-08, UC-HC-16, UC-HC-17**

# **10. Definition of Done cho core Use Case**

Một core UC chỉ được coi là hoàn thành khi:

- Có UI.
- Có authorization.
- Có validation.
- Có audit cần thiết.
- Có state transition.
- Có error path.
- Có API/domain behavior.
- Có test nghiệp vụ cho happy path + ít nhất một failure path.- Với UC-HC-09: có test quét đúng, mã không tồn tại, mã đã vô hiệu hóa và người dùng không đủ quyền.
- - Với UC-NOT: có test gửi SMS thành công, provider lỗi/retry và chống gửi trùng.
- UC-HC-03: Front Desk bị từ chối ở upload/map/preview/confirm; Quản lý hợp lệ được phép; import không tự tạo Patient/Encounter.

- UC-HC-07/17: chuẩn bị/in trước, quét và check-in lặp giữ nguyên toàn bộ identity; chưa check-in không vào Doctor Worklist; tuổi kiểm tra lại theo ngày thực tế; hỗ trợ địa điểm doanh nghiệp.

- UC-HC-18: đổi giá tất cả assignment, không riêng một người; cùng kết quả giữa hai báo cáo, rollback khi lỗi và không giữ giá cũ do concurrent assignment.

11. Các hợp đồng còn cần chốt trước triển khai liên quan

Required work/override completion; quyền và trạng thái đích của RESULTS import; replacement sau khi đã có kết quả; version kết quả dùng cho reprint; hoàn tiền một phần và điều kiện offline tại điểm khám. Không suy ra những quyền này từ CLINIC_MANAGER hoặc từ quyền EMPLOYEE_LIST import.
