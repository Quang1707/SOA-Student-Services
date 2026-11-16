# THIẾT KẾ BẢO MẬT API VÀ GIAO TIẾP LIÊN DỊCH VỤ (API & SERVICE-TO-SERVICE SECURITY)

## 1. Kiến trúc Xác thực và Phân quyền với JWT
```text
Client / Frontend / Postman
   |
   | 1. POST /api/v1/auth/login {username, password}
   v
Profile & Auth Service (Port 8081) ---> Kiểm tra BCrypt trên bảng `users`
   |
   | 2. Trả về Bearer JWT (Signed HS256, expiresIn = 900s, claims: sub, role, studentId, iss)
   v
Client lưu JWT và gửi kèm Header: Authorization: Bearer <JWT>
   |
   +---> Gọi Profile Service (8081) ---> JwtAuthenticationFilter ---> Kiểm tra Role & Ownership
   |
   +---> Gọi Document Service (8082) --> JwtAuthenticationFilter ---> Kiểm tra Role, Ownership & Business Rule
```

## 2. Cơ chế Bảo vệ Giao tiếp Liên dịch vụ (Service-to-Service Security)
Khi người dùng gọi `POST /api/v1/documents/{studentId}` tại **Document Service (8082)**, dịch vụ này cần gọi sang **Profile Service (8081)** (`GET /api/v1/profiles/{studentId}`) để kiểm tra sinh viên có tồn tại hay không.

### Trả lời 4 câu hỏi kiến trúc của Buổi thực hành 5 (Giai đoạn 5 - Bước 3):
1. **Client gửi JWT đến service nào?**
   * Client gửi JWT trực tiếp đến service mà nó gọi (`Profile Service` hoặc `Document Service`) thông qua header `Authorization: Bearer <JWT>`.
2. **Service gọi service khác có cần xác thực không?**
   * **Bắt buộc có.** Vì `GET /api/v1/profiles/{id}` là API được bảo vệ, mọi lời gọi không có thông tin xác thực hợp lệ đều bị chặn lại với mã `401 Unauthorized`.
3. **Nếu service đích chỉ tin cậy token do một issuer xác định, nó kiểm tra điều gì?**
   * Service đích kiểm tra chữ ký số (Signature) bằng khóa bí mật chung, thời hạn (`exp`), định danh nhà phát hành (`iss = student-profile-auth-service`), và quyền hạn đi kèm.
4. **Có nên chuyển tiếp nguyên trạng token của người dùng cho mọi service không?**
   * Không nên lạm dụng chuyển tiếp mù quáng cho mọi dịch vụ nội bộ. Trong thiết kế này, `Document Service` sử dụng **Service Credential (`X-Internal-Service-Key`)** dành riêng cho giao tiếp Machine-to-Machine (M2M) giữa `document-service` và `profile-service`. Nhờ đó, `profile-service` nhận diện được cuộc gọi đến từ dịch vụ nội bộ tin cậy (`ROLE_INTERNAL_SERVICE`) chỉ phục vụ mục đích tra cứu thông tin sinh viên.


## 3. Quy tắc phân quyền đã áp dụng trong mã nguồn
- Toàn bộ API nghiệp vụ của `document-service` yêu cầu JWT hợp lệ; không mở công khai các đường dẫn `/api/v1/documents/**` hoặc `/api/documents/**`.
- `STUDENT` chỉ được truy vấn, nộp và xóa tài liệu có `studentId` trùng với claim `studentId` trong JWT. Sinh viên không được xóa tài liệu ở trạng thái `VERIFIED`.
- `STAFF` và `ADMIN` được tra cứu tài liệu theo sinh viên, duyệt tài liệu và xử lý các thao tác quản trị được cho phép.
- `GET /api/v1/audits` yêu cầu JWT hợp lệ và vai trò `ADMIN`; thiếu/sai JWT trả `401`, sai vai trò trả `403`. Endpoint `/api/v1/audits/info` chỉ công khai thông tin kỹ thuật không nhạy cảm.
- `JWT_SECRET` và `INTERNAL_SERVICE_KEY` phải được cung cấp qua biến môi trường; không dùng khóa mặc định trong source code.
