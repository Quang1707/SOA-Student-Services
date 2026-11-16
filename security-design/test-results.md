# BẢNG KẾT QUẢ KIỂM THỬ BẢO MẬT (SECURITY TEST RESULTS - BUỔI THỰC HÀNH 5)

| Mã TC | Nhóm kiểm thử | Request thực hiện | Tài khoản / Token sử dụng | Kết quả mong đợi | Kết quả thực tế | Trạng thái |
| :-: | :--- | :--- | :--- | :--- | :--- | :-: |
| **TC01** | Authentication | `POST /api/v1/auth/login` | `student01` / `DemoPassword123!` | `200 OK` + Cấp phát `accessToken` (Bearer JWT) | `200 OK` + Trả về JWT hợp lệ, `expiresIn: 900` | **ĐẠT** |
| **TC02** | Authentication | `POST /api/v1/auth/login` | `student01` / `SaiMatKhau123` | `401 Unauthorized` (Không tiết lộ user tồn tại hay không) | `401 Unauthorized` - `"Thông tin đăng nhập không hợp lệ"` | **ĐẠT** |
| **TC03** | Authentication | `POST /api/v1/auth/login` | `locked01` / `DemoPassword123!` | `401 Unauthorized` (Tài khoản bị khóa) | `401 Unauthorized` - `"Tài khoản đã bị vô hiệu hóa"` | **ĐẠT** |
| **TC04** | Authentication | `GET /api/v1/profiles/SV001` | Không gửi kèm Header `Authorization` | `401 Unauthorized` | `401 Unauthorized` - `"Chưa đăng nhập hoặc thiếu Bearer Token"` | **ĐẠT** |
| **TC05** | Authentication | `GET /api/v1/profiles/SV001` | Token bị sửa đổi hoặc Token đã hết hạn | `401 Unauthorized` | `401 Unauthorized` - `"Token không hợp lệ hoặc đã hết hạn"` | **ĐẠT** |
| **TC06** | Ownership (IDOR) | `GET /api/v1/profiles/SV001` | Token của `student01` (`SV001`) | `200 OK` (Xem hồ sơ của chính mình) | `200 OK` + Trả về JSON hồ sơ `SV001` | **ĐẠT** |
| **TC07** | Ownership (IDOR) | `GET /api/v1/profiles/SV002` | Token của `student01` (`SV001`) | `403 Forbidden` (Cấm xem hồ sơ của SV khác) | `403 Forbidden` - `"Bạn chỉ được phép truy cập hồ sơ của chính mình"` | **ĐẠT** |
| **TC08** | RBAC (Sai vai trò) | `POST /api/v1/profiles` | Token của `student01` (`ROLE_STUDENT`) | `403 Forbidden` (SV không được tạo hồ sơ mới) | `403 Forbidden` - `"Không đủ thẩm quyền thực hiện hành động này"` | **ĐẠT** |
| **TC09** | RBAC (Đúng vai trò) | `POST /api/v1/profiles` | Token của `staff01` (`ROLE_STAFF`) | `201 Created` (Chuyên viên tạo được hồ sơ) | `201 Created` + Lưu vào Database | **ĐẠT** |
| **TC10** | RBAC (Phê duyệt) | `PUT /api/v1/profiles/SV003/approve` | Token của `staff01` (`ROLE_STAFF`) | `403 Forbidden` (Chỉ `ADMIN` mới được duyệt) | `403 Forbidden` | **ĐẠT** |
| **TC11** | RBAC (Phê duyệt) | `PUT /api/v1/profiles/SV003/approve` | Token của `admin01` (`ROLE_ADMIN`) | `200 OK` + Chuyển `ACTIVE` + Bắn RabbitMQ | `200 OK` + Ghi Audit Log thành công | **ĐẠT** |
| **TC12** | Ownership (Document) | `POST /api/v1/documents/SV002` | Token của `student01` (`SV001`) | `403 Forbidden` (Cấm nộp tài liệu vào hồ sơ người khác) | `403 Forbidden` - `"Sinh viên chỉ được nộp tài liệu cho chính mình"` | **ĐẠT** |
| **TC13** | Business Rule | `DELETE /api/v1/documents/1` | Token của `student01` (Tài liệu `#1` đã `VERIFIED`) | `400 Bad Request` (Tài liệu đã duyệt không được tự xóa) | `400 Bad Request` - `"Tài liệu đã được xác thực (VERIFIED), sinh viên không thể tự xóa"` | **ĐẠT** |
