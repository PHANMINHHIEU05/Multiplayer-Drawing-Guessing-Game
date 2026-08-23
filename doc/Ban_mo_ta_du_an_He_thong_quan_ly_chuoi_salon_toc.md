# BẢN MÔ TẢ DỰ ÁN

## HỆ THỐNG QUẢN LÝ CHUỖI CỬA HÀNG CẮT TÓC / SALON TÓC

## 1. TỔNG QUAN DỰ ÁN

Dự án nhằm xây dựng một hệ thống quản lý tập trung cho chuỗi cửa hàng cắt tóc / salon tóc với nhiều chi nhánh. Hệ thống giúp số hóa toàn bộ quy trình vận hành, từ đặt lịch hẹn, điều phối stylist, thực hiện dịch vụ, đến quản lý bán sản phẩm và theo dõi tồn kho chuỗi. Mục tiêu là nâng cao trải nghiệm khách hàng, tối ưu hiệu suất nhân viên và cung cấp công cụ giám sát hoạt động toàn diện cho ban quản lý.

## 2. MỤC TIÊU

- Cung cấp cổng đặt lịch trực tuyến tiện lợi cho khách hàng với khả năng chọn chi nhánh, stylist, dịch vụ và thời gian.
- Hỗ trợ kiểm tra lịch trống thông minh và đẩy lùi xung đột lịch.
- Tự động điều phối stylist dựa trên kỹ năng, ca làm và tình trạng bận/rảnh.
- Quản lý chi tiết trạng thái lịch hẹn và quy trình phục vụ thực tế.
- Cho phép quản lý danh mục dịch vụ, combo và dịch vụ phát sinh trong quá trình thực hiện.
- Quản lý thông tin nhân sự, lịch làm việc, phân ca và nghỉ phép.
- Quản lý bán hàng, nhập/xuất và điều chuyển sản phẩm giữa các chi nhánh.
- Hỗ trợ báo cáo, thống kê hoạt động, doanh thu và hiệu suất nhân viên.

## 3. PHẠM VI

- **Đối tượng sử dụng:** Khách hàng, stylist/nhân viên, quản lý chi nhánh, quản trị viên hệ thống.
- **Phạm vi chức năng:** Quản lý lịch hẹn, quản lý dịch vụ, quản lý nhân sự và điều phối, quản lý bán hàng và tồn kho.
- **Phạm vi địa lý:** Áp dụng cho tối thiểu 5 chi nhánh ban đầu, có thể mở rộng lên 50+ chi nhánh.
- **Tích hợp:** Có thể tích hợp với cổng thanh toán, SMS/email thông báo, Google Calendar.

## 4. CÁC MODULE CHỨC NĂNG CHÍNH

### Module 1 – Quản lý lịch hẹn và điều phối khách hàng (Module trung tâm)

**Mô tả:**

Danh mục này đảm nhận toàn bộ vòng đời của một cuộc hẹn: từ tạo lịch, kiểm tra khả năng, xác nhận, check-in, xếp hàng đợi, phân công stylist, đến hoàn thành hoặc hủy bỏ.

**Chức năng chi tiết:**

- Đặt lịch hẹn: khách chọn chi nhánh, dịch vụ, stylist (hoặc để hệ thống tự chọn), ngày, khung giờ, ghi chú.
- Kiểm tra lịch trống: stylist có ca làm, không xung đột lịch, chi nhánh còn năng lực phục vụ.
- Quản lý trạng thái cuộc hẹn: BOOKED → CHECKED_IN → WAITING → IN_SERVICE → COMPLETED; hỗ trợ CANCELLED, NO_SHOW.
- Hàng đợi khách hàng: nếu chưa có stylist rảnh, khách được xếp hàng chờ và tự động điều phối khi có người trống.
- Tự động điều phối stylist dựa trên kỹ năng, ca làm và độ ưu tiên.

### Module 2 – Quản lý dịch vụ và quá trình thực hiện dịch vụ

**Mô tả:**

Quản lý danh mục, giá cả và thời lượng của các dịch vụ cũng như theo dõi chi tiết quá trình phục vụ từ lúc khách bắt đầu đến khi hoàn tất.

**Chức năng chi tiết:**

- Quản lý danh mục dịch vụ theo cấu trúc cây và gán stylist có kỹ năng thực hiện.
- Tạo gói combo dịch vụ với giá combo và lợi ích.
- Theo dõi trạng thái phục vụ: WAITING → ASSIGNED → IN_SERVICE → COMPLETED.
- Cho phép thêm dịch vụ phát sinh trong quá trình thực hiện và cập nhật hóa đơn.
- Ghi nhận thời gian thực tế, ghi chú kỹ thuật và đánh giá từ stylist.

### Module 3 – Quản lý nhân sự và điều phối stylist

**Mô tả:**

Quản lý hồ sơ nhân viên, kỹ năng chuyên môn, lịch làm việc, phân ca và hỗ trợ điều phối stylist tối ưu.

**Chức năng chi tiết:**

- Thông tin nhân viên: họ tên, chi nhánh, vai trò, chuyên môn, kỹ năng, trạng thái.
- Quản lý lịch làm việc: đăng ký ca, phân ca, nghỉ phép, đổi ca.
- “Smart Stylist Assignment”: tự động chọn stylist phù hợp dựa trên kỹ năng yêu cầu, lịch làm việc, số khách đang chờ và tình trạng bận/rảnh.
- Xem lịch sử phục vụ và đánh giá hiệu suất của từng stylist.

### Module 4 – Quản lý bán hàng và sản phẩm

**Mô tả:**

Quản lý quy trình bán sản phẩm chăm sóc tóc tại các chi nhánh, kèm theo kiểm soát tồn kho và điều chuyển hàng hóa nội bộ.

**Chức năng chi tiết:**

- Quản lý sản phẩm: tên, thương hiệu, danh mục, giá bán, giá nhập, số lượng, hạn sử dụng.
- Quản lý tồn kho theo từng chi nhánh, hỗ trợ nhập kho, xuất kho, kiểm kê.
- Tạo đơn bán hàng và tự động trừ tồn kho khi thanh toán.
- Quy trình điều chuyển hàng: tạo yêu cầu, duyệt, xuất kho, vận chuyển, nhập kho chi nhánh nhận.
- Cảnh báo tồn kho thấp và sản phẩm sắp hết hạn sử dụng.

## 5. CÁC BÊN LIÊN QUAN

| **VAI TRÒ** | **MÔ TẢ** |
| --- | --- |
| Khách hàng | Người đặt lịch, sử dụng dịch vụ và mua sản phẩm |
| Stylist / Nhân viên | Người thực hiện dịch vụ, kiểm tra lịch hẹn và cập nhật trạng thái |
| Quản lý chi nhánh | Quản lý hoạt động tại chi nhánh, điều phối nhân sự, báo cáo |
| Quản trị viên | Cấu hình hệ thống, quản lý danh mục toàn cục, xử lý vấn đề kỹ thuật |
| Giám đốc chuỗi | Xem báo cáo tổng hợp, quản lý chi nhánh và chiến lược kinh doanh |

## 6. YÊU CẦU PHI CHỨC NĂNG

- **Hiệu năng:** Xử lý đồng thời tối thiểu 200 người dùng mà không suy giảm tốc độ.
- **Bảo mật:** Phân quyền rõ ràng; dữ liệu khách hàng và thanh toán được mã hóa.
- **Độ sẵn sàng:** Hệ thống hoạt động 24/7, thời gian downtime không quá 0,1%.
- **Khả năng mở rộng:** Hỗ trợ thêm chi nhánh mới và mở rộng chức năng qua API.
- **Giao diện người dùng:** Thân thiện, tối ưu trên cả web và mobile.

## 7. CÔNG NGHỆ ĐỀ XUẤT

- **Backend:** Java / Spring Boot hoặc Node.js / Express
- **Frontend:** React / Angular / Vue
- **Mobile:** React Native hoặc Flutter
- **Database:** PostgreSQL hoặc MySQL
- **Cache:** Redis
- **Queue:** RabbitMQ / Kafka (cho hàng đợi và đồng bộ)
- **CI/CD:** Git, Docker, Jenkins/GitLab CI

## 8. LỢI ÍCH DỰ ÁN

- Tăng trải nghiệm khách hàng và giảm thời gian chờ.
- Giảm tải công việc thủ công cho nhân viên và quản lý.
- Tối ưu hóa phân công stylist, tăng hiệu suất phục vụ.
- Quản lý chuỗi minh bạch từ tồn kho đến doanh thu.
- Hỗ trợ ra quyết định dựa trên dữ liệu thống kê.
