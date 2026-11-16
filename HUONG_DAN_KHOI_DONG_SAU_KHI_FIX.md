# Hướng dẫn khởi động sau khi sửa lỗi

## 1. Tạo cấu hình bí mật (Windows 10/11)

Mở PowerShell tại thư mục gốc chứa `docker-compose.yml`:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\configure-local-secrets.ps1
```

Lệnh tạo file `.env` dùng chung và lưu các biến cần thiết vào User Environment của Windows. Không gửi file `.env` cho người khác và không commit file này lên Git.

## 2. Khởi động PostgreSQL, RabbitMQ và Audit

Chạy tại thư mục gốc project:

```powershell
docker compose up -d --build postgres rabbitmq audit-notification-service
docker compose ps
```

Chờ `postgres`, `rabbitmq` và `audit-notification-service` có trạng thái `healthy`. Xem log Audit khi cần:

```powershell
docker compose logs -f audit-notification-service
```

Có thể kiểm tra Audit API:

```powershell
Invoke-RestMethod http://localhost:8083/health
```

Kết quả bình thường có `status = UP` và `database = UP`.

## 3. Khởi động các ứng dụng Java

Mở đúng thư mục gốc project trong VS Code. File `.vscode/launch.json` đã có sẵn các cấu hình chạy:

1. `1-Discovery` — Eureka, cổng `8761`.
2. `2-Profile` — `ProfileApplication`, cổng `8081`.
3. `3-Document` — `DocumentApplication`, cổng `8082`.
4. `4-Gateway` — API Gateway, cổng `8080`.

Nên chạy lần lượt từng cấu hình theo thứ tự trên và đợi service trước khởi động xong rồi mới chạy service tiếp theo. Cả Profile và Document đều đọc cùng file `.env` thông qua `envFile` trong launch configuration.

Sau khi chạy script tạo secrets, nếu VS Code đã mở từ trước thì đóng và mở lại VS Code để nạp biến môi trường mới.

## 4. Chạy Audit trực tiếp trên máy (tùy chọn)

Chỉ dùng cách này khi **không** chạy container `audit-notification-service` để tránh chiếm cổng `8083` hai lần:

```powershell
Set-Location .\audit-notification-service
npm ci
npm start
```

Audit tự đọc file `.env` ở thư mục gốc project. Cần Node.js 22 trở lên.

## 5. Nếu đã có volume PostgreSQL từ bản cũ

Không xóa volume để sửa lỗi đăng nhập. Sao lưu database trước, sau đó làm theo mục **Quan trọng nếu đã có PostgreSQL volume từ bản Giai đoạn 1/2** trong `README.md`, bao gồm chạy `scripts/provision-existing-databases.ps1` để đồng bộ role/mật khẩu với `.env` mới.

## 6. Cấu hình chính đã được sửa

- Profile và Document dùng lần lượt `PROFILE_DB_USER/PROFILE_DB_PASSWORD` và `DOCUMENT_DB_USER/DOCUMENT_DB_PASSWORD`, không còn tài khoản/mật khẩu PostgreSQL hard-code.
- Hai Java service dùng chung `JWT_SECRET`, `JWT_ISSUER` và `INTERNAL_SERVICE_KEY`.
- Hibernate dùng `validate`; Flyway quản lý thay đổi schema.
- Document dùng `@LoadBalanced RestTemplate` để gọi `profile-service` qua Eureka.
- Document khai báo exchange, queue và binding cho audit event; sự kiện JSON có `eventId`, `studentId`, `description` và timestamp.
- Audit Service tự nạp file `.env` ở thư mục project khi chạy trực tiếp bằng Node.js.
- Upload Document được cấu hình giới hạn 10 MB phù hợp với kiểm tra file trong service.
