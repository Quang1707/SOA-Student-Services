# YÊU CẦU BẢO MẬT HỆ THỐNG QUẢN LÝ HỒ SƠ SINH VIÊN (BUỔI THỰC HÀNH 5)

## 1. Tài sản cần bảo vệ (Protected Assets)
1. **Thông tin định danh & Hồ sơ cá nhân sinh viên (`StudentProfile`):** Bao gồm ngày sinh, số điện thoại, email, địa chỉ, trạng thái học vụ. Ngăn chặn sinh viên xem hoặc sửa đổi trái phép hồ sơ của sinh viên khác (IDOR - Insecure Direct Object Reference).
2. **Tài liệu minh chứng nhạy cảm (`StudentDocument`):** Bản sao CCCD, học bạ, bằng tốt nghiệp. Chỉ chủ hồ sơ hoặc cán bộ có thẩm quyền mới được xem/nộp, và không cho phép sinh viên tự ý xóa tài liệu đã được nhà trường xác thực (`VERIFIED`).
3. **Chức năng xét duyệt & quản trị học vụ (`Approve Profile`, `Delete Profile`, `Verify Document`):** Chỉ dành cho Quản trị viên (`ADMIN`) hoặc Cán bộ đào tạo (`STAFF`) theo đúng phân quyền.
4. **Thông tin xác thực tài khoản (`AppUser`):** Mật khẩu người dùng phải được băm một chiều bằng thuật toán **BCrypt** trước khi lưu xuống Database.

## 2. Các nguyên tắc bảo mật cốt lõi được áp dụng
* **Xác thực không trạng thái (Stateless Authentication) với JWT:** Cấp phát Access Token ký bằng thuật toán `HMAC-SHA256` (`HS256`), có thời hạn (`expiresIn = 900` giây = 15 phút), chứa các claims: `sub` (username), `role`, `studentId`, `iss` (issuer).
* **Phân quyền kết hợp (RBAC + Resource Ownership + Business Condition):**
  * Kiểm tra vai trò (`ROLE_STUDENT`, `ROLE_STAFF`, `ROLE_ADMIN`) thông qua Spring Security và `@PreAuthorize`.
  * Kiểm tra quyền sở hữu (`Ownership Check`) tại tầng Service/Controller: đối chiếu `studentId` trong JWT đã xác thực với `studentId` của tài nguyên đang truy cập.
  * Kiểm tra điều kiện nghiệp vụ (`Business Rule Check`): sinh viên sở hữu tài liệu nhưng nếu tài liệu đã chuyển sang trạng thái `VERIFIED` thì không được phép xóa.
* **Bảo mật giao tiếp Service-to-Service:** Khi `document-service` gọi sang `profile-service` để kiểm tra sự tồn tại của sinh viên, sử dụng cơ chế xác thực dịch vụ nội bộ qua Header `X-Internal-Service-Key` kết hợp kiểm tra phạm vi truy cập.
* **Ghi nhật ký kiểm toán an toàn (Secure Audit Logging):** Ghi nhận người thực hiện, hành động, đối tượng và thời gian qua RabbitMQ sang `audit-notification-service`, tuyệt đối không ghi mật khẩu hoặc chuỗi JWT đầy đủ vào log.
