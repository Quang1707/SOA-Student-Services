# Giai đoạn 1 – Sửa lỗi bảo mật và phân quyền

## Những thay đổi đã thực hiện

1. **Document Service bắt buộc xác thực**
   - Gỡ `permitAll()` khỏi các API tài liệu.
   - Tất cả API nghiệp vụ yêu cầu Bearer JWT hợp lệ.
   - Chỉ cho phép các vai trò `STUDENT`, `STAFF`, `ADMIN`.

2. **Kiểm tra quyền sở hữu và vai trò tại tầng Service**
   - `STUDENT` chỉ xem, nộp và xóa tài liệu thuộc mã sinh viên trong JWT.
   - Tài khoản `STUDENT` thiếu `studentId` bị từ chối.
   - `STUDENT` không được xóa tài liệu `VERIFIED`.
   - `STAFF`/`ADMIN` mới được xem danh sách toàn hệ thống, duyệt tài liệu hoặc thực hiện thao tác quản trị.
   - Kiểm tra xác thực/quyền được thực hiện trước khi gọi Profile Service và trước thao tác dữ liệu.
   - Thu hẹp `INTERNAL_SERVICE` trong Profile Service: chỉ cho phép tra cứu đúng một hồ sơ theo ID; không cho liệt kê, xem thống kê, PATCH hoặc dùng API phiên bản V2.
   - So sánh Internal Service Key theo cách constant-time và kiểm tra role JWT theo danh sách vai trò hợp lệ.

3. **Audit API được bảo vệ**
   - `GET /api/v1/audits` xác minh chữ ký JWT HS256, issuer, subject và hạn dùng.
   - Chỉ `ADMIN` được xem nhật ký.
   - Không có token hoặc token sai/hết hạn: `401`; token hợp lệ nhưng không phải ADMIN: `403`.
   - `GET /api/v1/audits/info` tiếp tục công khai vì chỉ trả thông tin kỹ thuật, không trả nhật ký.

4. **Loại bỏ khóa mặc định trong source code**
   - `JWT_SECRET` và `INTERNAL_SERVICE_KEY` được lấy từ biến môi trường, không còn fallback sang chuỗi bí mật cố định trong file cấu hình.
   - Có script `scripts/configure-local-secrets.ps1` để tạo và lưu khóa ngẫu nhiên vào biến môi trường người dùng Windows.

5. **Đồng bộ tài liệu bảo mật**
   - Cập nhật README và tài liệu thiết kế API để mô tả đúng quy tắc xác thực/phân quyền.
   - Tắt H2 Console và bỏ quyền truy cập công khai vào `/h2-console/**` trong các service vì hệ thống đang cấu hình PostgreSQL.

## Kết quả kiểm tra

- `node --check audit-notification-service/index.js`: đạt.
- Kiểm thử HTTP trực tiếp với Audit Service:
  - Không gửi token → `401`.
  - JWT của STUDENT → `403`.
  - JWT của ADMIN còn hạn → `200`.
  - JWT hết hạn → `401`.
  - JWT sai chữ ký → `401`.
- Đã rà soát để bảo đảm không còn chuỗi JWT secret/Internal Service Key mặc định cũ trong source code (không tính thư viện bên thứ ba và thư mục build cũ).
- **Chưa xác nhận biên dịch Java**: môi trường kiểm tra không có Maven cài sẵn; Maven Wrapper không tải được Maven từ Maven Central. Cần chạy build trên máy phát triển sau khi cấu hình biến môi trường.
- Chưa chạy kiểm thử tích hợp Document Service với PostgreSQL/Profile Service; cần thực hiện trong bước chạy hệ thống đầy đủ.

## Cách cấu hình trước khi chạy trên Windows

Mở PowerShell tại thư mục gốc project và chạy:

```powershell
.\scripts\configure-local-secrets.ps1
```

Sau đó đóng/mở lại terminal hoặc IDE. Các service cần dùng chung giá trị:

- `profile-service`, `document-service`, `audit-notification-service`: cùng `JWT_SECRET`.
- `profile-service`, `document-service`: cùng `INTERNAL_SERVICE_KEY`.

Không chia sẻ hoặc commit các giá trị này.
