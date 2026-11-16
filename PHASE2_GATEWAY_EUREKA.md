# Giai đoạn 2 – API Gateway và Eureka Discovery

## Mục tiêu và thay đổi

- Đồng bộ các module Spring Boot trên `4.1.2` và Spring Cloud `2025.1.4` (cặp phiên bản tương thích theo ma trận chính thức của Spring Cloud).
- API Gateway chạy ở cổng `8080`; dùng Spring Cloud Gateway Server Web MVC với prefix cấu hình hiện hành `spring.cloud.gateway.server.webmvc`. Route khai báo cả đường dẫn gốc và wildcard để hỗ trợ `GET/POST /api/v1/documents` và các endpoint con.
- Eureka Discovery Server chạy ở cổng `8761`; chế độ standalone, không tự đăng ký vào registry.
- `profile-service` và `document-service` có Eureka Client, khai báo `spring.application.name` và tự đăng ký vào Eureka.
- Gateway định tuyến API `/api/v1/...` và đường dẫn cũ `/api/...` tới đúng service.
- Frontend gọi Auth, Profile, Document và Audit qua `http://localhost:8080` thay vì gọi trực tiếp từng cổng backend.
- Document Service gọi Profile Service qua Spring Cloud LoadBalancer với service ID `profile-service`, không còn gắn cứng `localhost:8081`.
- CORS cho trình duyệt được quản lý tập trung bằng `WebMvcConfigurer` tại Gateway (không đặt nhầm cấu hình `globalcors` của WebFlux vào Gateway Web MVC); chỉ cho phép origin local `localhost` và `127.0.0.1`, không bật credentials.
- Audit Node Service đăng ký vào Eureka bằng REST API, heartbeat định kỳ và deregister khi tắt; Gateway định tuyến tới Audit bằng `lb://audit-notification-service`. Khi chạy Audit trong Docker và Eureka trên host, cấu hình `EUREKA_SERVER_URL=http://host.docker.internal:8761/eureka`; hostname instance được quảng bá là `localhost` vì cổng 8083 được publish về loopback của host.
- H2 Console đã được bỏ khỏi giao diện vì hệ thống đang cấu hình PostgreSQL.

## Phiên bản nền tảng

| Module | Spring Boot | Spring Cloud |
|---|---:|---:|
| API Gateway | 4.1.2 | 2025.1.4 |
| Discovery Server | 4.1.2 | 2025.1.4 |
| Profile Service | 4.1.2 | 2025.1.4 |
| Document Service | 4.1.2 | 2025.1.4 |

Java yêu cầu tối thiểu 17. Project dùng `springdoc-openapi` 3.2.0 cho hai service Java, phù hợp dòng Spring Boot 4.1.x.

## Chuẩn bị trên Windows

1. Mở PowerShell tại thư mục gốc project.
2. Cấu hình secret (nếu chưa cấu hình ở Giai đoạn 1):

   ```powershell
   .\scripts\configure-local-secrets.ps1
   ```

3. Đóng và mở lại terminal/IDE để tiến trình mới nhận biến `JWT_SECRET` và `INTERNAL_SERVICE_KEY`.
4. Đảm bảo PostgreSQL và RabbitMQ đang chạy trước khi khởi động các service nghiệp vụ.

## Khởi động theo thứ tự

Mỗi lệnh chạy trong một terminal riêng. Tại thư mục gốc project:

**Terminal 1 – Eureka:**

```powershell
cd .\discovery-server
.\mvnw.cmd spring-boot:run
```

Mở `http://localhost:8761` để xem dashboard.

**Terminal 2 – Profile Service:**

```powershell
cd .\profile-service
.\mvnw.cmd spring-boot:run
```

**Terminal 3 – Document Service:**

```powershell
cd .\document-service
.\mvnw.cmd spring-boot:run
```

**Terminal 4 – Audit Node Service:**

```powershell
cd .\audit-notification-service
node .\index.js
```

**Terminal 5 – API Gateway:**

```powershell
cd .\api-gateway
.\mvnw.cmd spring-boot:run
```

Khi Eureka sẵn sàng, dashboard cần hiển thị `PROFILE-SERVICE`, `DOCUMENT-SERVICE`, `AUDIT-NOTIFICATION-SERVICE` và `API-GATEWAY`. Với Audit chạy trong Docker, chờ log `[Eureka] Da dang ky ...`; nếu Registry vừa khởi động sau Audit, service sẽ tự thử lại theo chu kỳ heartbeat.

## Kiểm tra nhanh bằng PowerShell

**1. Kiểm tra Eureka có phản hồi:**

```powershell
Invoke-WebRequest http://localhost:8761 -UseBasicParsing
```

**2. Đăng nhập qua Gateway:**

```powershell
$body = @{ username = 'admin01'; password = 'DemoPassword123!' } | ConvertTo-Json
$login = Invoke-RestMethod `
  -Uri 'http://localhost:8080/api/v1/auth/login' `
  -Method Post `
  -ContentType 'application/json' `
  -Body $body

$headers = @{ Authorization = "Bearer $($login.accessToken)" }
```

**3. Đọc danh sách hồ sơ qua Gateway:**

```powershell
Invoke-RestMethod `
  -Uri 'http://localhost:8080/api/v1/profiles' `
  -Headers $headers
```

**4. Đọc Audit qua Gateway (tài khoản ADMIN):**

```powershell
Invoke-RestMethod `
  -Uri 'http://localhost:8080/api/v1/audits' `
  -Headers $headers
```

Nếu yêu cầu trả về `401`, hãy kiểm tra secret và token. Nếu Gateway trả `503` hoặc báo không tìm thấy instance, kiểm tra Eureka Dashboard xem service đã đăng ký `UP` chưa. Nếu Audit trả `502/503`, kiểm tra Node service ở cổng `8083`, log `[Eureka]` và Dashboard.

## Cổng dịch vụ sau Giai đoạn 2

| Dịch vụ | Cổng | Vai trò |
|---|---:|---|
| API Gateway | 8080 | Cổng API duy nhất cho Frontend |
| Profile Service | 8081 | Hồ sơ và xác thực tài khoản |
| Document Service | 8082 | Tài liệu sinh viên |
| Audit Node Service | 8083 | Nhật ký và xử lý sự kiện |
| Eureka Discovery | 8761 | Đăng ký và tìm kiếm service |
| PostgreSQL | 5432 | Cơ sở dữ liệu |
| RabbitMQ | 5672 / 15672 | Message broker / management |

## Kiểm thử đã thực hiện trong môi trường đóng gói

- Kiểm tra XML hợp lệ cho cả bốn file `pom.xml`.
- Kiểm tra cú pháp JavaScript bằng `node --check` đối với `frontend/app.js` và `audit-notification-service/index.js`.
- Kiểm tra Frontend không còn gọi API Auth/Profile/Document/Audit trực tiếp tới cổng `8081`, `8082`, `8083`.
- Kiểm tra các controller giữ cả route `/api/v1/...` và alias `/api/...` để tương thích ngược.
- Kiểm tra cấu hình Eureka và route Gateway đã khai báo đúng service ID.
- Kiểm tra cấu hình CORS tập trung bằng Java MVC config và loại bỏ cấu hình CORS không áp dụng cho Web MVC.

**Giới hạn xác minh:** Không thể chạy Maven build hoặc khởi động các service trong môi trường đóng gói này vì Maven Wrapper không tải được Maven distribution từ Maven Central. Vì vậy, cần thực hiện các lệnh kiểm tra/chạy ở trên trên máy Windows có kết nối Maven Central trước khi kết luận runtime đã thành công.
