# Giai đoạn 3 – PostgreSQL, Database-per-Service và quản lý schema

## 1. Mục tiêu

Đồng bộ cơ sở dữ liệu thực tế với tài liệu thiết kế, loại bỏ việc dùng chung tài khoản PostgreSQL quản trị, quản lý schema bằng migration có phiên bản và lưu trữ Audit Log bền vững.

## 2. Các thay đổi chính

### PostgreSQL và phân quyền database

- Chuẩn hóa database theo tên đang dùng trong cấu hình: `profile_db`, `document_db`, `audit_db`.
- Tạo role riêng `profile_app`, `document_app`, `audit_app` với mật khẩu ngẫu nhiên.
- Các service không dùng tài khoản quản trị `postgres`.
- Thu hồi quyền `CONNECT` từ `PUBLIC` trên ba database nghiệp vụ và cấp quyền rõ ràng cho đúng role sở hữu.
- PostgreSQL healthcheck và RabbitMQ healthcheck được thêm vào Compose. RabbitMQ có volume `rabbitmq_data` để giữ queue bền vững từ phiên bản này trở đi.
- Các cổng PostgreSQL/RabbitMQ chỉ bind vào `127.0.0.1` trên máy host.

### Quản lý schema

- Bổ sung Flyway cho Profile Service và Document Service.
- Migration `V1__create_profile_schema.sql` tạo bảng `users`, `student_profiles` và index cần thiết.
- Migration `V1__create_document_schema.sql` tạo bảng `student_documents` và index.
- Chuyển `spring.jpa.hibernate.ddl-auto` từ `update` sang `validate` để Hibernate không tự thay đổi schema.
- Bật `baseline-on-migrate` ở version `0` để Flyway tiếp nhận database đã có bảng từ phiên bản Hibernate trước. Migration V1 dùng `CREATE TABLE IF NOT EXISTS` nhằm không ghi đè bảng đang có.

### Audit Service

- Bổ sung PostgreSQL client `pg`.
- Thêm bảng `audit_logs` và các index.
- Audit log được lưu bền vững, không còn phụ thuộc bộ nhớ RAM.
- Consumer chỉ ACK RabbitMQ sau khi ghi DB thành công; message sai định dạng bị reject không requeue, lỗi cơ sở dữ liệu được requeue. Mỗi event có UUID duy nhất và `ON CONFLICT DO NOTHING` để tránh ghi lặp khi RabbitMQ redelivery; message cũ chưa có eventId được gán UUID xác định từ payload để vẫn có tính idempotent.
- API đọc log vẫn yêu cầu JWT hợp lệ và role `ADMIN`; thêm `limit`/`offset`, giới hạn tối đa 500 bản ghi.
- Thêm health endpoint `/health` kiểm tra PostgreSQL.
- Chuẩn hóa thời điểm phát sinh sự kiện từ Profile Service thành ISO-8601 UTC và lưu vào `TIMESTAMPTZ`.

### Cấu hình và tài liệu

- `.env.example` chứa danh sách biến cần thiết.
- `configure-local-secrets.ps1` tạo `.env`, sinh mật khẩu ngẫu nhiên và lưu biến môi trường cho terminal/IDE Windows.
- `init-databases.sh` tạo ba database/role cho volume mới.
- `provision-existing-databases.ps1` cập nhật volume cũ theo hướng không xóa database hay volume; tạo DB/role thiếu, cập nhật mật khẩu role và cấp quyền schema/object.
- Cập nhật `README.md` và `data-and-service-design.md` cho thống nhất.
- Giữ các file H2 cũ trong `data/` làm dữ liệu lịch sử; cấu hình mới không đọc chúng và không tự động nhập H2 sang PostgreSQL.

## 3. Hướng dẫn chạy trên Windows

### Volume PostgreSQL mới

```powershell
.\scripts\configure-local-secrets.ps1
docker compose up -d --build postgres rabbitmq audit-notification-service
docker compose ps
```

### Đã có volume PostgreSQL từ Giai đoạn 1/2

1. Chạy `configure-local-secrets.ps1` nếu chưa có `.env`; script giữ lại JWT/Internal Service Key cũ trong Windows User Environment.
2. Sao lưu trước khi cập nhật:

```powershell
cmd /c "docker compose exec -T postgres pg_dumpall -U postgres > backup-before-phase3.sql"
```

3. Khởi động PostgreSQL và RabbitMQ:

```powershell
docker compose up -d postgres rabbitmq
```

4. Provision các database/role mà không xóa volume:

```powershell
.\scripts\provision-existing-databases.ps1
```

5. Khởi động Audit Service:

```powershell
docker compose up -d --build audit-notification-service
```

6. Khởi động lại Profile/Document Service để Flyway baseline schema cũ và áp dụng migration V1.

**Chạy cùng Compose project name cũ để tiếp tục dùng đúng volume PostgreSQL; nếu đổi tên project, Compose có thể tạo volume mới. Không chạy `docker compose down -v` khi cần giữ dữ liệu. Thay đổi `POSTGRES_PASSWORD` trong Compose không tự đổi mật khẩu bên trong PostgreSQL volume đã tồn tại.

## 4. Kiểm tra đã thực hiện trong môi trường chỉnh sửa

| Kiểm tra | Kết quả |
|---|---|
| XML parsing của 4 Maven POM | Đạt |
| YAML parsing Compose, Gateway, Eureka | Đạt |
| `bash -n scripts/init-databases.sh` | Đạt |
| `node --check audit-notification-service/index.js` | Đạt |
| `npm ci --dry-run --ignore-scripts --offline` | Đạt; lockfile nhận diện đủ cây dependency `pg` |
| Đối chiếu tên DB trong README / thiết kế / cấu hình | Đạt |
| Docker Compose up / PostgreSQL migration thực tế | Chưa chạy được: môi trường chỉnh sửa không có Docker |
| Maven compile / Spring Boot startup | Chưa chạy được: môi trường chỉnh sửa không có Maven |
| Kết nối RabbitMQ/PostgreSQL end-to-end | Chưa xác nhận thực tế |

## 5. Giới hạn và lưu ý

- Chưa thể khẳng định runtime hoạt động 100% trước khi chạy Docker Compose và Maven trên máy có Docker/JDK/Maven Wrapper.
- Dữ liệu H2 trong các file `.mv.db` không được tự động chuyển sang PostgreSQL. Nếu dữ liệu H2 đó cần giữ, phải export/import riêng sau khi xác định đúng schema và dữ liệu.
- Với database cũ, migration V1 không tự sửa kiểu dữ liệu hoặc cột sai khác vì `CREATE TABLE IF NOT EXISTS` không thay đổi bảng hiện hữu. `ddl-auto=validate` sẽ phát hiện nhiều trường hợp lệch mapping và dừng service thay vì âm thầm sửa dữ liệu.
- Đây là cấu hình phù hợp môi trường bài tập/lab một PostgreSQL instance có ba database; production có thể cần instance, backup, TLS, secret manager và chính sách retention riêng.
