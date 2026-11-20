# Hệ thống Quản lý Hồ sơ Sinh viên - Nhóm 9

## Kiến trúc

Hệ thống gồm Profile/Auth Service (Java Spring Boot), Document Service (Java Spring Boot), Audit & Notification Service (Node.js), API Gateway, Eureka Discovery, RabbitMQ và PostgreSQL. Gateway là cổng API duy nhất cho Frontend. Profile, Document và Audit sở hữu database riêng (Database-per-Service).

| Service | Database | Tài khoản DB riêng | Bảng chính |
|---|---|---|---|
| Profile & Auth (`8081`) | `profile_db` | `profile_app` | `student_profiles`, `users` |
| Document (`8082`) | `document_db` | `document_app` | `student_documents` |
| Audit & Notification (`8083`) | `audit_db` | `audit_app` | `audit_logs` |

Không service nào dùng tài khoản quản trị PostgreSQL `postgres` để truy cập dữ liệu ứng dụng. `document-service` lưu `student_id` dạng tham chiếu logic, không tạo khóa ngoại vật lý sang database Profile. Nhật ký Audit được lưu PostgreSQL thay vì RAM; message RabbitMQ chỉ được ACK sau khi ghi DB thành công.

## Yêu cầu

- Windows 10/11 hoặc Linux/macOS.
- Docker Desktop / Docker Engine kèm Docker Compose v2.
- JDK 17 trở lên và Maven Wrapper của từng module.
- Node.js 22 trở lên nếu chạy Audit Service ngoài Docker.

## Cấu hình bí mật

Trên Windows PowerShell, mở terminal tại thư mục gốc project và chạy một lần:

```powershell
.\scripts\configure-local-secrets.ps1
```

Script tạo `.env` (được `.gitignore` loại trừ), sinh mật khẩu ngẫu nhiên cho ba tài khoản database ứng dụng, mật khẩu PostgreSQL quản trị, RabbitMQ, JWT secret và Internal Service Key. Script không in các giá trị bí mật ra màn hình. Không gửi/commit file `.env`.

Trên Linux/macOS, copy `.env.example` thành `.env`, thay tất cả `CHANGE_ME...` bằng giá trị ngẫu nhiên đủ mạnh trước khi chạy Docker Compose. Khi chạy Java service trực tiếp từ terminal Linux/macOS, nạp các biến vào shell bằng `set -a; source .env; set +a` trước khi chạy Maven Wrapper.

## Giai đoạn 3: PostgreSQL và Database-per-Service

### Khởi tạo mới

```powershell
.\scripts\configure-local-secrets.ps1
docker compose up -d --build postgres rabbitmq audit-notification-service
```

Chờ các container có trạng thái `healthy`:

```powershell
docker compose ps
```

Cấu hình PostgreSQL tự tạo ba database và ba role riêng; quyền `CONNECT` mặc định của PUBLIC bị thu hồi trên các database nghiệp vụ để các role không truy cập chéo. Các file Flyway trong Profile/Document Service quản lý schema theo version; Hibernate chạy `validate`, không tự ý thay đổi schema. Audit Service áp dụng migration SQL lúc khởi động.

### Quan trọng nếu đã có PostgreSQL volume từ bản Giai đoạn 1/2

PostgreSQL chỉ thực thi file trong `/docker-entrypoint-initdb.d` khi data directory còn trống. Không xóa volume cũ để ép chạy lại script, vì thao tác đó sẽ xóa dữ liệu. **Hãy chạy Docker Compose trong đúng thư mục project và cùng Compose project name đã dùng ở Giai đoạn 1/2**; nếu giải nén sang thư mục khác, hãy kiểm tra `docker compose ls` và dùng nhất quán `docker compose -p <ten-project-cu> ...`. Nếu đổi project name, Docker có thể tạo volume mới thay vì gắn volume dữ liệu cũ.

Nếu bạn đã chạy project trước Giai đoạn 3:

1. Bảo đảm đã chạy `configure-local-secrets.ps1` ở phần cấu hình bí mật để tạo `.env`; script giữ lại JWT/Internal Service Key cũ trong Windows User Environment.
2. Sao lưu dữ liệu trước khi cập nhật, ví dụ: `cmd /c "docker compose exec -T postgres pg_dumpall -U postgres > backup-before-phase3.sql"`.
3. Chạy `docker compose up -d postgres rabbitmq`.
4. Chạy script cấp database/role bổ sung cho volume cũ:

```powershell
.\scripts\provision-existing-databases.ps1
```

Script này tạo database/role còn thiếu, không xóa database hoặc volume. Nó cập nhật mật khẩu PostgreSQL quản trị và mật khẩu các role ứng dụng theo `.env`, đồng thời chuyển quyền sở hữu các bảng/sequence ứng dụng hiện có và cấp quyền schema.

Từ Giai đoạn 3, RabbitMQ có volume `rabbitmq_data` để lưu queue bền vững. Compose Giai đoạn 1/2 chưa mount volume cho RabbitMQ, nên nếu buộc phải tạo lại container RabbitMQ cũ, các message còn nằm trong lớp ghi của container cũ không được bảo đảm giữ lại. Dữ liệu PostgreSQL được xử lý riêng theo quy trình backup/provision ở trên.

Sau khi provision xong, khởi động Audit container:

```powershell
docker compose up -d --build audit-notification-service
```

### Kết nối service chạy ngoài Docker (IDE / Maven Wrapper)

`configure-local-secrets.ps1` cũng lưu các biến môi trường cần thiết vào User Environment của Windows. Đóng/mở lại VS Code/IntelliJ sau khi chạy script. Profile và Document mặc định kết nối `localhost:5432` tới database tương ứng, bằng đúng role riêng. RabbitMQ dùng `localhost:5672`.

Nếu chạy Java service thủ công từ VS Code/IntelliJ, trước tiên chạy `scripts/configure-local-secrets.ps1`, sau đó đóng và mở lại IDE để nhận biến môi trường. Khởi động theo thứ tự:

1. PostgreSQL và RabbitMQ (Docker Compose).
2. Eureka Discovery Server (cổng `8761`).
3. Profile Service (cổng `8081`) và Document Service (cổng `8082`).
4. API Gateway (cổng `8080`).

Audit Service mặc định đã chạy trong Docker tại `8083`; không chạy thêm một bản Node trên host cùng cổng. Audit đăng ký vào Eureka bằng REST API và Gateway gọi nó qua Service ID `audit-notification-service`. Khi chạy Node trên host, service dùng mặc định `http://localhost:8761/eureka`.

**Lưu ý cấu hình đồng bộ:** Profile và Document dùng chung `JWT_SECRET`, `JWT_ISSUER` và `INTERNAL_SERVICE_KEY`; mỗi service dùng đúng role/database riêng (`profile_app`, `document_app`, `audit_app`). Không sửa các thông tin này trực tiếp trong `application.properties`. Chạy script tạo secrets trước khi mở IDE để các biến môi trường Windows được nạp vào tiến trình Java.

## Migration và dữ liệu mẫu

Các file `data/*.mv.db` trong gói cũ là dữ liệu H2 thử nghiệm lịch sử; cấu hình hiện tại không kết nối các file này. Chúng được giữ lại để tham khảo, không được tự động nhập sang PostgreSQL.

- Flyway tạo/kiểm tra schema Profile và Document từ `src/main/resources/db/migration`.
- Hibernate `ddl-auto=validate`: nếu entity lệch schema, service dừng và báo lỗi thay vì tự sửa bảng. Khi thay đổi cấu trúc dữ liệu, cần thêm migration Flyway mới, không đổi sang `update`.
- Profile Service vẫn tạo dữ liệu demo (`SV001`–`SV003`) và tài khoản demo nếu bảng tương ứng đang trống.
- Audit migration tạo bảng `audit_logs`, các index phục vụ sắp xếp và lọc.
- Không xóa `postgres_data` khi đang có dữ liệu cần giữ.

## Bài thực hành số 7 – Service Discovery

- Thiết kế kiến trúc: `docs/architecture/microservices-design.md`.
- Thiết kế Eureka và danh sách service: `docs/service-registry/`.
- Kịch bản lỗi/failover: `tests/service-discovery/failure-tests.md`.
- Kết quả LoadBalancer và hướng dẫn thu thập bằng chứng: `tests/service-discovery/load-balancing-results.md`.
- Postman Collection gồm 1 request đăng nhập và 20 request discovery: `tests/service-discovery/postman-collection.json`.
- Script PowerShell tự động gửi tối thiểu 20 request và xuất CSV: `scripts/test-service-discovery.ps1`.

Để kiểm tra hai Profile instance, khởi động instance mặc định tại `8081`, sau đó mở PowerShell thứ hai và chạy `scripts/run-profile-instance-2.ps1` để mở instance `8084`. Cả hai dùng chung `profile_db` và cùng đăng ký dưới Service ID `profile-service`. Chỉ ghi nhận PASS sau khi có kết quả chạy thực tế; source không chứa sẵn kết quả giả lập.

## API / cổng

| Thành phần | Cổng | Vai trò |
|---|---:|---|
| API Gateway | 8080 | Cổng API cho Frontend |
| Profile Service | 8081 | Hồ sơ và xác thực |
| Document Service | 8082 | Tài liệu sinh viên |
| Audit Service | 8083 | Audit log, lưu PostgreSQL |
| Eureka | 8761 | Service Discovery |
| PostgreSQL | 5432 | Ba database độc lập |
| RabbitMQ | 5672 | Message broker |
| RabbitMQ Management | 15672 | Giao diện quản trị cục bộ |

Chỉ Gateway (`8080`) là cổng API mà Frontend cần gọi. PostgreSQL/RabbitMQ chỉ bind vào loopback `127.0.0.1` để tránh mở cổng database ra mạng LAN.

## Tài khoản demo

Mật khẩu mẫu: `DemoPassword123!` (chỉ sử dụng cho demo/lab; thay đổi trước khi triển khai thật).

- `student01` – STUDENT, `SV001`
- `student02` – STUDENT, `SV002`
- `staff01` – STAFF
- `admin01` – ADMIN
- `locked01` – STUDENT, tài khoản khóa

## Xử lý sự cố thường gặp

- `password authentication failed`: kiểm tra `.env` có khớp mật khẩu PostgreSQL đã dùng khi tạo volume hay không. Thay đổi `POSTGRES_PASSWORD` trong Compose không tự đổi mật khẩu của volume đang tồn tại.
- `database does not exist`: volume có từ bản cũ; chạy `scripts/provision-existing-databases.ps1` sau khi xác thực được bằng tài khoản quản trị.
- Flyway checksum/migration/schema validation error: không xóa volume để né lỗi; sao lưu DB, kiểm tra bảng thực tế và xử lý migration có kiểm soát.
- `Audit Service unhealthy`: xem `docker compose logs audit-notification-service`; kiểm tra `AUDIT_DB_*`, `JWT_SECRET`, RabbitMQ và trạng thái PostgreSQL.
- `npm install` được Dockerfile gọi để cài `pg` và đồng bộ lockfile. Nếu chạy Node trên host, chạy `cd audit-notification-service; npm ci; npm start`; Audit tự đọc file `.env` ở thư mục gốc project (cần chạy script tạo secrets trước).


## Giai đoạn 4: Frontend, xác thực dữ liệu và tài liệu minh chứng

### Frontend

- Frontend không tự đăng nhập tài khoản ADMIN khi mở trang. Người dùng phải đăng nhập chủ động.
- Dữ liệu API được hiển thị qua `textContent` và DOM API; không ghép dữ liệu trả về từ server vào `innerHTML`.
- Sinh viên chỉ thấy/sửa hồ sơ đã liên kết với tài khoản của mình và chỉ cập nhật Email, Số điện thoại, Địa chỉ bằng `PATCH`.
- STAFF/ADMIN có thể tạo và cập nhật hồ sơ bằng `POST`/`PUT`; thao tác duyệt/xóa hồ sơ chỉ hiển thị cho ADMIN. Quyền thực tế vẫn do Backend kiểm tra.
- Nhật ký Audit chỉ hiển thị với ADMIN; Dashboard thống kê dành cho STAFF/ADMIN.

### Upload tài liệu thật

Document Service hỗ trợ `multipart/form-data` qua API:

```text
POST /api/v1/documents/{studentId}/upload
Authorization: Bearer <JWT>
Content-Type: multipart/form-data
Fields: docType, file, simulateDelay (optional), simulateRollback (optional)
```

- Chỉ chấp nhận PDF, PNG, JPG/JPEG; tối đa 10 MB.
- Backend kiểm tra phần mở rộng và chữ ký byte của file, không tin riêng `Content-Type` do trình duyệt gửi.
- File được lưu trong `document-service/uploads` (hoặc thư mục cấu hình bởi `DOCUMENT_STORAGE_DIR`), không nằm trong thư mục static/public.
- Database chỉ lưu metadata và storage key ngẫu nhiên. File được tải qua `GET /api/v1/documents/{docId}/file`, endpoint kiểm tra JWT và quyền sở hữu trước khi trả file.
- Sinh viên chỉ nộp và tải tài liệu của chính mình; sinh viên không được xóa tài liệu `VERIFIED`. STAFF/ADMIN có quyền nghiệp vụ theo Role Permission.
- API POST cũ nhận `docUrl` vẫn được giữ để tương thích dữ liệu liên kết, nhưng chỉ chấp nhận URL HTTPS.
- File đã upload không được commit vào Git. Thư mục `uploads/` đã được đưa vào `.gitignore`.

Sau khi thêm metadata file, Flyway áp dụng migration `V2__add_document_file_metadata.sql` trên `document_db`. Không cần xóa database hoặc volume cũ.

### Kiểm thử Frontend và file

- Mở `frontend/index.html` qua Live Server hoặc web server tĩnh; mặc định API Gateway là `http://localhost:8080`.
- Đăng nhập bằng tài khoản demo trong môi trường lab; trang không tự đăng nhập.
- Thử hiển thị chuỗi HTML trong tên/địa chỉ hồ sơ để xác nhận được hiển thị như văn bản, không thực thi.
- Thử upload PDF/PNG/JPG dưới 10 MB; thử file HTML đổi đuôi `.pdf`, file >10 MB và tải file bằng tài khoản sinh viên khác để xác nhận bị từ chối.
- CORS Gateway cho phép `PATCH` để luồng cập nhật thông tin liên hệ của STUDENT hoạt động qua trình duyệt.

## Tài liệu thiết kế dữ liệu

Xem `data-and-service-design.md` để biết quyền sở hữu database, bảng và nguyên tắc không tạo FK vật lý xuyên service.


## Giai đoạn 7: Microservices và Service Registry (TH7)

Hệ thống dùng Eureka Server độc lập tại `8761`. `profile-service`, `document-service`, `api-gateway` và `audit-notification-service` tự đăng ký; Gateway định tuyến ba backend qua `lb://...`. Document Service gọi Profile Service bằng Service ID `profile-service` thông qua `@LoadBalanced RestTemplate`, không gọi IP/port cố định.

### Chạy nhiều instance Profile Service

Hai instance cùng Service ID `PROFILE-SERVICE`, cùng API contract và cùng database `profile_db`:

- Instance 1: `8081` (cấu hình mặc định).
- Instance 2: mở PowerShell tại thư mục gốc và chạy `.\scripts\run-profile-instance-2.ps1`.

Khởi động PostgreSQL/RabbitMQ, Eureka và Profile instance 1. Chờ instance 1 hoàn tất khởi động/migration và sẵn sàng tại `8081`, sau đó chạy instance 2, Document Service và Gateway. Nếu Audit chạy bằng Docker Compose, nó sẽ thử đăng ký lại cho đến khi Eureka hoạt động. Dashboard cần hiển thị cả hai Profile instance ở trạng thái `UP`. Có thể kiểm tra metadata trực tiếp tại `http://localhost:8081/api/v1/instance-info` và `http://localhost:8084/api/v1/instance-info`.

### Kiểm thử ít nhất 20 request

Sau khi các dịch vụ hoạt động, chạy:

```powershell
.\scripts\test-service-discovery.ps1
```

Script đăng nhập bằng tài khoản demo `admin01`, gửi 20 request từ Gateway → Document Service → Profile Service qua Eureka, in số request theo từng `instanceId` và lưu CSV vào `tests/service-discovery/results/`. Tài khoản demo chỉ dùng trong môi trường học tập; không dùng mật khẩu này khi triển khai thật.

### Kiểm thử lỗi

Dừng một Profile instance và đợi Eureka cập nhật lease; chạy lại script để xác nhận instance còn lại phục vụ. Sau đó dừng cả hai instance và gọi endpoint `GET /api/v1/documents/discovery/profile-instance-info` để kiểm tra Document Service trả lỗi `503 PROFILE_SERVICE_UNAVAILABLE`. Khởi động lại một instance và quan sát đăng ký lại.

Chi tiết sơ đồ, danh sách service, sequence flow và bảng test nằm trong `docs/architecture/`, `docs/service-registry/` và `tests/service-discovery/`. Kết quả CSV/ảnh Dashboard chỉ được xem là minh chứng sau khi nhóm thực sự chạy test trên máy của mình.


## TH8 – Triển khai và vận hành bằng Docker Compose

Hệ thống hiện được đóng gói đầy đủ bằng Docker Compose, gồm Eureka, Gateway, hai Profile instance, Document, Audit, PostgreSQL và RabbitMQ.

### Khởi chạy nhanh

```powershell
.\scripts\configure-local-secrets.ps1
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

Trên Linux/macOS, tạo `.env` từ `.env.example` và thay các giá trị `CHANGE_ME` trước khi chạy Compose.

- API Gateway: `http://localhost:8080`
- Eureka Dashboard: `http://localhost:8761`
- Actuator Gateway: `http://localhost:8080/actuator/health`
- Actuator Profile: `http://localhost:8081/actuator/health` và `http://localhost:8084/actuator/health`
- Actuator Document: `http://localhost:8082/actuator/health`
- Audit health: `http://localhost:8083/health`

### Vận hành và kiểm thử

Xem [Deployment Design](docs/deployment/deployment-design.md), [Operation Guide](docs/operation/operation-guide.md), [Deployment Test Cases](docs/test/deployment-test-cases.md) và [Failure Analysis](docs/operation/failure-analysis.md). Test case phải được chạy thực tế và cập nhật trạng thái/bằng chứng; không mặc định PASS.

Dừng hệ thống nhưng giữ dữ liệu: `docker compose down`. Không dùng `docker compose down -v` trừ khi chủ động muốn xóa toàn bộ volume và dữ liệu.
