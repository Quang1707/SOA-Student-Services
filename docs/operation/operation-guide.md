# Hướng dẫn triển khai và vận hành TH8

## 1. Điều kiện

- Docker Desktop hoặc Docker Engine có Docker Compose v2.
- Git và tối thiểu 6 GB RAM trống khuyến nghị khi build lần đầu.
- Cổng 8080, 8081, 8082, 8083, 8084, 8761, 5432, 5672, 15672 chưa bị chương trình khác chiếm.

## 2. Chuẩn bị cấu hình

Windows PowerShell:

```powershell
.\scripts\configure-local-secrets.ps1
```

Linux/macOS: copy `.env.example` thành `.env`, thay toàn bộ `CHANGE_ME...` bằng secret ngẫu nhiên. Không commit/gửi `.env`.

## 3. Kiểm tra cấu hình và build

```bash
docker compose config --quiet
docker compose build --no-cache
```

Lệnh build yêu cầu truy cập registry Maven/Docker ở lần đầu.

## 4. Khởi động toàn hệ thống

```bash
docker compose up -d
docker compose ps
```

Đợi các service ở trạng thái `healthy`. Eureka Dashboard: `http://localhost:8761`. API Gateway: `http://localhost:8080`. Frontend tĩnh có thể mở `frontend/index.html` bằng Live Server; API base URL là Gateway.

## 5. Kiểm tra Actuator và Eureka

```bash
curl -fsS http://localhost:8761/actuator/health
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8082/actuator/health
curl -fsS http://localhost:8084/actuator/health
```

Audit health: `curl -fsS http://localhost:8083/health`. Kiểm tra Eureka Dashboard có `API-GATEWAY`, `PROFILE-SERVICE` (hai instance), `DOCUMENT-SERVICE`, `AUDIT-NOTIFICATION-SERVICE` ở trạng thái `UP`.

## 6. Xem log

```bash
docker compose logs --tail=200 discovery-server
docker compose logs --tail=200 profile-service profile-service-2
docker compose logs --tail=200 document-service
docker compose logs --tail=200 audit-notification-service
docker compose logs --tail=200 api-gateway
```

Theo dõi realtime: `docker compose logs -f --tail=100`. Không đưa JWT, mật khẩu, secret hoặc nội dung hồ sơ cá nhân vào báo cáo/log chia sẻ.

## 7. Dừng và khởi động lại

```bash
docker compose stop document-service
docker compose start document-service
docker compose restart profile-service
docker compose restart postgres
```

Để dừng stack nhưng giữ dữ liệu: `docker compose down`. **Không thêm `-v`** nếu cần giữ dữ liệu.

## 8. Sao lưu PostgreSQL

```bash
docker compose exec -T postgres pg_dumpall -U postgres > backup-before-maintenance.sql
```

Giữ bản sao lưu ở nơi an toàn, không commit nếu chứa dữ liệu thật.

## 9. Chẩn đoán nhanh

- Service không healthy: `docker compose logs <service>` và kiểm tra health endpoint.
- Không đăng ký Eureka: kiểm tra `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` hoặc `EUREKA_SERVER_URL` phải trỏ `http://discovery-server:8761/eureka/` từ trong Docker.
- Lỗi DB: kiểm tra `docker compose ps postgres`, credentials trong `.env` và log PostgreSQL. Mật khẩu đổi trong `.env` không tự thay đổi mật khẩu trên volume đã khởi tạo.
- Upload không còn sau recreate: kiểm tra volume `document_uploads` và `DOCUMENT_STORAGE_DIR=/app/uploads`.
- Gateway 503: xác nhận Provider đã `UP` trên Eureka và healthcheck đã healthy.
