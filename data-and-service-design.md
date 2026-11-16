# TÀI LIỆU THIẾT KẾ DỮ LIỆU VÀ KẾT NỐI DỊCH VỤ
**Đề tài:** Hệ thống Quản lý Hồ sơ Sinh viên – Nhóm 9

## 1. Danh sách service

1. **Profile & Auth Service** (`profile-service`, cổng 8081): quản lý tài khoản, xác thực JWT/BCrypt và hồ sơ sinh viên.
2. **Document Service** (`document-service`, cổng 8082): quản lý tài liệu minh chứng, quyền sở hữu và trạng thái nghiệp vụ.
3. **Audit & Notification Service** (`audit-notification-service`, cổng 8083): nhận sự kiện từ RabbitMQ, ghi nhật ký kiểm toán bền vững vào PostgreSQL và cung cấp API tra cứu cho ADMIN.

## 2. Database-per-Service (tên database triển khai thực tế)

| Service sở hữu | Database | Role DB riêng | Bảng chính |
|---|---|---|---|
| Profile Service | `profile_db` | `profile_app` | `users`, `student_profiles` |
| Document Service | `document_db` | `document_app` | `student_documents` |
| Audit Service | `audit_db` | `audit_app` | `audit_logs` |

Mỗi service chỉ dùng tài khoản database tương ứng, không dùng tài khoản quản trị PostgreSQL. Các database cùng nằm trên một PostgreSQL instance trong môi trường học tập, nhưng tách biệt về database và quyền sở hữu. Triển khai production có thể tách thành các PostgreSQL instance độc lập mà không thay đổi quyền sở hữu dữ liệu logic.

## 3. Entity / bảng

### 3.1. `users` – Profile Service

- `id` BIGINT, khóa chính tự tăng.
- `username` VARCHAR(50), duy nhất, bắt buộc.
- `password_hash` VARCHAR(255), BCrypt hash.
- `role` VARCHAR(20): `STUDENT`, `STAFF`, `ADMIN`.
- `student_id` VARCHAR(20), nullable, ánh xạ logic tới hồ sơ sinh viên.
- `active` BOOLEAN, trạng thái tài khoản.

### 3.2. `student_profiles` – Profile Service

- `student_id` VARCHAR(20), khóa chính.
- `full_name` VARCHAR(100), `date_of_birth` DATE.
- `gender` VARCHAR(10): `MALE`, `FEMALE`, `OTHER`.
- `email` VARCHAR(100), `phone` VARCHAR(15), `major` VARCHAR(100), `address` VARCHAR(255).
- `status` VARCHAR(20): `PENDING`, `ACTIVE`, `COMPLETED`.
- `created_at`, `updated_at` TIMESTAMP.

### 3.3. `student_documents` – Document Service

- `id` BIGINT, khóa chính tự tăng.
- `student_id` VARCHAR(20), mã sinh viên chủ sở hữu (tham chiếu logic).
- `student_name` VARCHAR(100), `doc_type` VARCHAR(100), `doc_url` VARCHAR(255).
- `doc_status` VARCHAR(20): `PENDING`, `VERIFIED`.
- `created_at` TIMESTAMP.

### 3.4. `audit_logs` – Audit Service

- `id` BIGINT, khóa chính tự tăng.
- `event_id` UUID, duy nhất để chống ghi trùng khi RabbitMQ phát lại message; payload cũ chưa có ID được tạo ID xác định từ nội dung sự kiện.
- `action` VARCHAR(100), `student_id` VARCHAR(20).
- `full_name` VARCHAR(100), `email` VARCHAR(255), `description` TEXT.
- `event_timestamp` TIMESTAMPTZ – thời điểm phát sinh sự kiện theo UTC.
- `received_at` TIMESTAMPTZ – thời điểm Audit Service nhận và lưu sự kiện.

Không ghi mật khẩu, JWT hoặc secret vào audit log. API xem log yêu cầu JWT hợp lệ và role ADMIN; kết quả có phân trang giới hạn.

## 4. Migration và tính toàn vẹn

- Profile và Document dùng Flyway (`V1__...sql`) để quản lý schema theo phiên bản. Hibernate đặt `ddl-auto=validate`, chỉ xác nhận entity khớp schema, không tự sửa bảng.
- Audit Service chạy migration SQL idempotent khi khởi động.
- PostgreSQL init script tạo database và role riêng cho từng service. Script này chỉ chạy tự động khi Docker volume PostgreSQL được tạo mới; volume đã tồn tại cần chạy script provision nâng cấp riêng.
- Chỉ tạo khóa ngoại bên trong database thuộc cùng service. Không tạo FK vật lý giữa `student_documents.student_id` và `student_profiles.student_id` vì hai bảng thuộc hai database/service khác nhau.

## 5. Quyền sở hữu và giao tiếp

| Dữ liệu | Service sở hữu | Cách truy cập |
|---|---|---|
| Tài khoản và hồ sơ | Profile | API Profile bảo vệ JWT/RBAC/ownership |
| Tài liệu | Document | API Document bảo vệ JWT, ownership và trạng thái |
| Nhật ký kiểm toán | Audit | Sự kiện RabbitMQ; API truy vấn chỉ ADMIN |

Document Service xác minh sinh viên bằng REST gọi Profile Service qua Eureka/LoadBalancer, gửi `X-Internal-Service-Key`. Không truy cập trực tiếp database của Profile Service.
