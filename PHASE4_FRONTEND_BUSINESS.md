# BÁO CÁO GIAI ĐOẠN 4 – FRONTEND VÀ NGHIỆP VỤ

## 1. Mục tiêu

Giai đoạn 4 tập trung đồng bộ giao diện với API và quyền truy cập ở Backend; khắc phục nguy cơ XSS; bổ sung kiểm tra dữ liệu đầu vào; triển khai luồng upload/tải file minh chứng thực tế; chuẩn hóa cách hiển thị lỗi cho người dùng.

Các chỉnh sửa được thực hiện trên bản đã tích hợp Giai đoạn 1–3. Không chủ động xóa dữ liệu hoặc thay thế nghiệp vụ cũ. API POST tài liệu dạng URL HTTPS vẫn được giữ để tương thích dữ liệu đã có.

## 2. Các thay đổi chính

### 2.1. Frontend và phòng chống XSS

- Viết lại phần render dữ liệu hồ sơ, tài liệu, thống kê và Audit theo `document.createElement`, `textContent`, `replaceChildren` và event listener.
- Không sử dụng `innerHTML`, `outerHTML`, `insertAdjacentHTML` hoặc `document.write` trong `frontend/app.js`.
- Không tự đăng nhập ADMIN khi mở trang. Người dùng phải chủ động đăng nhập.
- Khi chuyển tài khoản, phiên cũ và dữ liệu đã tải của tài khoản cũ được xóa trước khi xác thực tài khoản mới; tránh giữ quyền ADMIN nếu lần đăng nhập tiếp theo thất bại.
- Link tài liệu cũ chỉ được tạo khi URL là HTTPS hợp lệ, có hostname và không chứa user-info. Link mở tab mới có `rel="noopener noreferrer"`.
- Các thao tác tải file sử dụng Bearer JWT, Blob URL và thu hồi URL tạm sau khi tải.

### 2.2. Đồng bộ UI với phân quyền và API

- STAFF/ADMIN mới được hiển thị Dashboard và chức năng thêm hồ sơ; Audit chỉ hiển thị cho ADMIN.
- STUDENT chỉ nhìn thấy thao tác sửa hồ sơ của chính mình. Khi lưu, Frontend gửi `PATCH` với đúng ba trường `email`, `phone`, `address`.
- STAFF/ADMIN dùng `POST` để tạo và `PUT` để cập nhật hồ sơ.
- Các chức năng phân quyền ở giao diện chỉ nhằm cải thiện trải nghiệm; Backend vẫn là nơi quyết định quyền truy cập cuối cùng.
- API của Frontend tiếp tục đi qua API Gateway tại `http://localhost:8080`; không gọi trực tiếp Profile/Document/Audit bằng các cổng backend.
- CORS Gateway đã cho phép phương thức `PATCH`.

### 2.3. Kiểm tra dữ liệu biểu mẫu

- Mã sinh viên: 1–20 ký tự, chỉ chữ, số, dấu gạch dưới và gạch ngang.
- Họ tên: 3–100 ký tự.
- Ngày sinh: phải là ngày trong quá khứ.
- Email: kiểm tra định dạng và giới hạn 100 ký tự.
- Số điện thoại: đúng 10 chữ số.
- Chuyên ngành: tối đa 100 ký tự và không để trống khi tạo/cập nhật bởi STAFF/ADMIN.
- Địa chỉ: tối đa 255 ký tự.
- Backend Profile Service cũng kiểm tra dữ liệu PATCH, trường được phép cập nhật và các tham số truy vấn/phân trang; không phụ thuộc riêng kiểm tra ở trình duyệt.

### 2.4. Upload và tải file minh chứng

Document Service bổ sung API:

```http
POST /api/v1/documents/{studentId}/upload
Authorization: Bearer <JWT>
Content-Type: multipart/form-data

Fields:
  docType: loại tài liệu
  file: file minh chứng
  simulateDelay: tùy chọn, chỉ STAFF/ADMIN
  simulateRollback: tùy chọn, chỉ STAFF/ADMIN
```

Tải file:

```http
GET /api/v1/documents/{docId}/file
Authorization: Bearer <JWT>
```

Các kiểm soát được bổ sung:

- Chỉ nhận PDF, PNG, JPG/JPEG; dung lượng tối đa 10 MB.
- Backend kiểm tra phần mở rộng và chữ ký byte ban đầu của nội dung file; không tin riêng `Content-Type` do trình duyệt gửi.
- Tên file lưu trên đĩa được sinh ngẫu nhiên bằng UUID; tên gốc chỉ lưu làm metadata và được loại bỏ ký tự điều khiển/đường dẫn.
- File lưu ngoài thư mục static/public, mặc định tại `./uploads` theo working directory của Document Service hoặc thư mục trong `DOCUMENT_STORAGE_DIR`.
- Không trả file trực tiếp qua URL tĩnh. Endpoint tải file yêu cầu JWT và kiểm tra quyền sở hữu; STUDENT chỉ tải file thuộc mã sinh viên của mình.
- Header tải file đặt `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`.
- Khi xóa bản ghi, file được dọn sau khi transaction commit. Khi transaction upload rollback, file được dọn để tránh file mồ côi trong các lỗi nghiệp vụ thông thường.
- API URL cũ vẫn tồn tại nhưng chỉ chấp nhận HTTPS hợp lệ.

### 2.5. Cơ sở dữ liệu và xử lý lỗi

- Bổ sung Flyway migration `V2__add_document_file_metadata.sql` trên `document_db` để thêm `storage_key`, `original_filename`, `content_type`, `file_size`; không xóa bảng hoặc dữ liệu cũ.
- Cấu hình giới hạn multipart mặc định 10 MB cho file và 12 MB cho toàn request.
- Chuẩn hóa phản hồi lỗi chính: 400 dữ liệu không hợp lệ, 403 không đủ quyền, 404 không tìm thấy, 413 file quá lớn, 500 lỗi máy chủ với thông báo không tiết lộ stack trace.
- Bổ sung unit test cho thành phần lưu file: PDF hợp lệ, nội dung giả mạo, phần mở rộng không hỗ trợ, file quá dung lượng và storage key có dấu hiệu path traversal.

## 3. Những file chính được cập nhật/thêm mới

- `frontend/app.js`
- `frontend/index.html`
- `frontend/style.css`
- `api-gateway/src/main/java/com/example/api_gateway/config/CorsConfig.java`
- `profile-service/src/main/java/com/example/profile/model/StudentProfile.java`
- `profile-service/src/main/java/com/example/profile/service/ProfileService.java`
- `document-service/src/main/java/com/example/document/controller/DocumentController.java`
- `document-service/src/main/java/com/example/document/controller/GlobalExceptionHandler.java`
- `document-service/src/main/java/com/example/document/service/DocumentService.java`
- `document-service/src/main/java/com/example/document/storage/DocumentFileStorageService.java` (mới)
- `document-service/src/main/java/com/example/document/model/StudentDocument.java`
- `document-service/src/main/java/com/example/document/client/ProfileClient.java`
- `document-service/src/main/resources/db/migration/V2__add_document_file_metadata.sql` (mới)
- `document-service/src/test/java/com/example/document/storage/DocumentFileStorageServiceTest.java` (mới)
- `document-service/src/main/resources/application.properties`
- `.gitignore`, `README.md`

## 4. Kiểm tra đã thực hiện

| Kiểm tra | Kết quả |
|---|---|
| `node --check frontend/app.js` | Đạt |
| `node --check audit-notification-service/index.js` | Đạt |
| Tham chiếu ID từ JS sang HTML | 35 ID được tham chiếu, không thiếu ID |
| Quét unsafe HTML sinks trong Frontend | Không phát hiện |
| Kiểm tra tự động đăng nhập ADMIN | Không phát hiện |
| Quét Frontend gọi trực tiếp cổng 8081/8082/8083 | Không phát hiện trong JavaScript |
| 8 Frontend smoke checks | Đạt cả 8 |
| XML của 4 Maven POM | Đọc hợp lệ |
| YAML Docker Compose, Gateway, Eureka | Đọc hợp lệ |
| Java Maven build và JUnit | Chưa xác nhận |
| Chạy tích hợp PostgreSQL/RabbitMQ/Java services | Chưa xác nhận |

### Giới hạn xác minh

Đã thử chạy Maven Wrapper nhưng môi trường chỉnh sửa không tải được Maven distribution từ Maven Central (`wget` không lấy được Maven 3.10.0). Vì vậy, không khẳng định Java đã biên dịch thành công hoặc migration đã chạy trên một PostgreSQL đang hoạt động. Unit test Java đã được thêm vào mã nguồn nhưng chưa chạy được trong môi trường này.

## 5. Hướng dẫn kiểm thử trên Windows

1. Giải nén ZIP mới. Giữ lại `.env.example`; không đưa file `.env` thật hoặc secret cá nhân vào nơi công khai.
2. Tại thư mục gốc project, chạy script tạo secret theo hướng dẫn từ Giai đoạn 1–3:

```powershell
.\scripts\configure-local-secrets.ps1
```

3. Khởi động PostgreSQL và RabbitMQ theo `docker-compose.yml`/README hiện có. Không chạy `docker compose down -v`; lệnh đó có thể xóa volume dữ liệu.
4. Khởi động Discovery Server, Profile Service, Document Service và API Gateway theo thứ tự/hướng dẫn trong README. Khi Document Service kết nối `document_db`, Flyway sẽ tự áp dụng migration V2.
5. Mở `frontend/index.html` bằng Live Server. Đăng nhập thủ công bằng tài khoản demo được cấu hình trong môi trường lab.
6. Thử các tình huống: nhập chuỗi `<script>` trong tên/địa chỉ; đăng nhập STAFF rồi chuyển sang STUDENT; STUDENT cập nhật email/điện thoại/địa chỉ; upload PDF/ảnh hợp lệ; upload file HTML đổi đuôi PDF; upload file lớn hơn 10 MB; STUDENT thử xem/tải tài liệu người khác; STAFF/ADMIN duyệt tài liệu.

Lệnh chạy Maven Wrapper từ từng module (PowerShell, nếu Maven distribution đã tải được):

```powershell
cd .\document-service
.\mvnw.cmd test
```

Chạy module bằng `spring-boot:run` theo README và cấu hình môi trường tương ứng.

## 6. Lưu ý vận hành

- PostgreSQL lưu metadata; nội dung file nằm trong thư mục lưu file của Document Service. Cần sao lưu cả database `document_db` và thư mục file `uploads`.
- Khi chạy Document Service trong Docker/container, phải mount `DOCUMENT_STORAGE_DIR` vào volume bền vững; nếu chỉ lưu trong filesystem tạm của container, file có thể mất khi container bị thay thế.
- Không công khai thư mục `uploads` qua static web server và không bỏ kiểm tra quyền ở endpoint tải file.
- Đây là bản chỉnh sửa mã nguồn và kiểm tra tĩnh; cần chạy kiểm thử tích hợp trên máy nhóm trước khi dùng làm bản demo/bảo vệ chính thức.
