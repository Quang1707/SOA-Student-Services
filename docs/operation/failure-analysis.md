# Phân tích sự cố và phục hồi TH8

| Sự cố | Dấu hiệu | Cách kiểm tra | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|---|---|
| Eureka không truy cập được | Service không đăng ký, Gateway 503 | `docker compose logs discovery-server`; kiểm tra health | Eureka chưa healthy hoặc cấu hình URL sai | Đợi discovery healthy; dùng DNS `discovery-server:8761` trong container |
| Consumer gọi Provider lỗi | Gateway/Document trả 503 | Eureka Dashboard, log Gateway/Document | Provider chưa đăng ký, sai Service ID, DNS/port sai | Kiểm tra `spring.application.name`, Eureka URL, network và health |
| PostgreSQL unhealthy | Service không start hoặc DB connection refused | `docker compose logs postgres`; `pg_isready` | DB chưa sẵn sàng, volume/credential không khớp | Kiểm tra `.env`, volume hiện tại và quyền DB; không xóa volume tùy tiện |
| Authentication failed | JDBC báo password authentication failed | Kiểm tra tên role/database và `.env` | Secret thay đổi sau khi volume đã init | Dùng quy trình provision/mật khẩu có backup; không xóa volume để né lỗi |
| Audit không healthy | `/health` trả 503 | `docker compose logs audit-notification-service` | DB/RabbitMQ không sẵn sàng hoặc thiếu secret | Kiểm tra `AUDIT_DB_*`, RabbitMQ và JWT secret |
| File upload mất | Metadata còn nhưng tải file lỗi | Kiểm tra `docker volume inspect` và mount | Thư mục lưu file không nằm trên named volume | Đặt `DOCUMENT_STORAGE_DIR=/app/uploads`, mount `document_uploads` |
| Container chạy nhưng app chưa sẵn sàng | `docker compose ps` báo starting/unhealthy | Gọi Actuator và đọc log | JVM đang migrate DB hoặc lỗi cấu hình | Chờ start_period; xử lý lỗi thực tế, không chỉ dựa vào container state |

## Nguyên tắc nghiệm thu

Phân biệt ba lớp: (1) container state, (2) application health qua Actuator, (3) business availability bằng request nghiệp vụ thực tế. Restart container không chứng minh dữ liệu và nghiệp vụ đã phục hồi. Không chạy `docker compose down -v` trong các bài kiểm tra bảo toàn dữ liệu.
