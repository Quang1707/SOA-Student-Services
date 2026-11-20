# Test cases triển khai và vận hành TH8

> Trạng thái mặc định là NOT RUN. Chỉ chuyển PASS/FAIL sau khi thực thi trên máy kiểm thử và lưu ảnh/log/kết quả thực tế.

| ID | Mục tiêu | Thao tác | Kết quả mong đợi | Bằng chứng cần lưu | Trạng thái |
|---|---|---|---|---|---|
| DEP01 | Khởi động stack | `docker compose up -d`; `docker compose ps` | Tất cả service healthy/running; không restart loop | `DEP01-compose-ps.png`, log khởi động | NOT RUN |
| DEP02 | Health ứng dụng | Gọi Actuator của Eureka, Gateway, Profile x2, Document; gọi Audit `/health` | HTTP 200 và `status=UP` | `DEP02-health.txt` | NOT RUN |
| DEP03 | Eureka discovery | Mở `http://localhost:8761`; quan sát registry | Gateway, Document, Audit và 2 Profile instance đăng ký UP | `DEP03-eureka.png` | NOT RUN |
| DEP04 | Dừng một Profile instance | `docker compose stop profile-service`; gọi API nghiệp vụ qua Gateway | Profile instance còn lại tiếp tục phục vụ; Eureka loại instance dừng sau lease | Request/response + Eureka screenshot | NOT RUN |
| DEP05 | Khởi động lại service | `docker compose start profile-service`; chờ healthy; gọi lại API | Instance đăng ký lại UP; request thành công | Log + Eureka screenshot | NOT RUN |
| DEP06 | Bảo toàn dữ liệu DB | Tạo/ghi nhận một hồ sơ test; `docker compose restart postgres`; chờ healthy; đọc lại hồ sơ | Dữ liệu vẫn tồn tại, không tạo bản ghi trùng | API trước/sau + compose ps | NOT RUN |
| DEP07 | Bảo toàn file upload | Upload file test; `docker compose up -d --force-recreate document-service`; tải file lại | File vẫn tải được từ `document_uploads` | Response upload/download | NOT RUN |
| DEP08 | Restart toàn hệ thống | `docker compose down` rồi `docker compose up -d`; kiểm tra DB/API | Các volume vẫn gắn; dữ liệu nghiệp vụ còn; services đăng ký lại | `docker compose ps`, API trước/sau | NOT RUN |

## Lệnh tiện ích

```bash
# Kiểm tra toàn bộ container
docker compose ps

# Dừng và khởi động lại một Provider
docker compose stop profile-service
docker compose start profile-service

# Restart DB, không xóa volume
docker compose restart postgres

# Restart stack, giữ volume
docker compose down
docker compose up -d
```

Ghi lại ngày giờ, commit hash, máy/OS, phiên bản Docker, thao tác, kết quả thực tế và đường dẫn bằng chứng cho từng test case.
