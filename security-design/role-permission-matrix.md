# MA TRẬN PHÂN QUYỀN NGHIỆP VỤ (ROLE - PERMISSION MATRIX)

## 1. Danh sách Vai trò (Roles) và Tài khoản mẫu

| Tài khoản (`username`) | Mật khẩu | Vai trò (`role`) | Mã SV gắn kết (`studentId`) | Trạng thái (`active`) | Mô tả |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `student01` | `DemoPassword123!` | `STUDENT` | `SV001` | `true` | Sinh viên Nguyễn Văn A |
| `student02` | `DemoPassword123!` | `STUDENT` | `SV002` | `true` | Sinh viên Trần Thị B |
| `staff01` | `DemoPassword123!` | `STAFF` | `null` | `true` | Chuyên viên Phòng Đào tạo / Tiếp nhận hồ sơ |
| `admin01` | `DemoPassword123!` | `ADMIN` | `null` | `true` | Quản trị viên hệ thống |
| `locked01` | `DemoPassword123!` | `STUDENT` | `SV003` | `false` | Tài khoản bị khóa (Dùng để kiểm thử từ chối đăng nhập) |

---

## 2. Ma trận phân quyền chi tiết trên từng Endpoint API

| STT | Endpoint API | Công khai / Bảo vệ | `STUDENT` | `STAFF` (Chuyên viên) | `ADMIN` (Quản trị) | Kiểm tra Quyền sở hữu & Điều kiện nghiệp vụ | Mã lỗi khi vi phạm |
| :-: | :--- | :---: | :---: | :---: | :---: | :--- | :---: |
| 1 | `POST /api/v1/auth/login` | **Công khai** | Được phép | Được phép | Được phép | Kiểm tra `active == true` và BCrypt hash | `401` |
| 2 | `GET /api/v1/profiles/info` | **Công khai** | Được phép | Được phép | Được phép | Không yêu cầu token | `200` |
| 3 | `GET /api/v1/profiles` | **Bảo vệ** | Chỉ trả về hồ sơ của chính mình | Xem danh sách toàn bộ SV | Xem danh sách toàn bộ SV | Tự động lọc theo `studentId` nếu role là `STUDENT` | `401` |
| 4 | `GET /api/v1/profiles/{id}` | **Bảo vệ** | **Chỉ xem hồ sơ của mình** (`id == token.studentId`) | Được phép xem mọi SV | Được phép xem mọi SV | **Ownership Check:** `student01` xem `SV002` $\rightarrow$ Từ chối | `401` / `403` |
| 5 | `POST /api/v1/profiles` | **Bảo vệ** | **Từ chối** | Được phép tạo mới | Được phép tạo mới | Chỉ `STAFF` và `ADMIN` được tiếp nhận hồ sơ mới | `401` / `403` |
| 6 | `PUT /api/v1/profiles/{id}` | **Bảo vệ** | **Từ chối** | Được phép cập nhật | Được phép cập nhật | Cập nhật toàn bộ thông tin học vụ | `401` / `403` |
| 7 | `PATCH /api/v1/profiles/{id}` | **Bảo vệ** | **Chỉ sửa hồ sơ của mình** (`id == token.studentId`) | Được phép | Được phép | **Ownership Check:** `STUDENT` chỉ được sửa SĐT/Email/Địa chỉ của chính mình | `401` / `403` |
| 8 | `PUT /api/v1/profiles/{id}/approve` | **Bảo vệ** | **Từ chối** | **Từ chối** | **Chỉ `ADMIN`** | Quyết định phê duyệt chính thức hồ sơ sinh viên | `401` / `403` |
| 9 | `DELETE /api/v1/profiles/{id}` | **Bảo vệ** | **Từ chối** | **Từ chối** | **Chỉ `ADMIN`** | Xóa hồ sơ khỏi CSDL | `401` / `403` |
| 10 | `GET /api/v1/documents` | **Bảo vệ** | Chỉ xem tài liệu của mình | Xem tất cả tài liệu | Xem tất cả tài liệu | Kiểm tra `studentId` đối với `STUDENT` | `401` / `403` |
| 11 | `POST /api/v1/documents/{studentId}` | **Bảo vệ** | **Chỉ nộp cho chính mình** | Được phép nộp hộ SV | Được phép | **Ownership Check:** `student01` nộp vào `SV002` $\rightarrow$ Từ chối | `401` / `403` |
| 12 | `PUT /api/v1/documents/{docId}/verify` | **Bảo vệ** | **Từ chối** | Được phép xác thực | Được phép xác thực | Chuyển trạng thái tài liệu sang `VERIFIED` | `401` / `403` |
| 13 | `DELETE /api/v1/documents/{docId}` | **Bảo vệ** | **Chỉ chủ sở hữu & khi `status == PENDING`** | Được phép | Được phép | **Ownership + Business Rule:** Tài liệu đã `VERIFIED` thì `STUDENT` không thể xóa | `400` / `403` |
