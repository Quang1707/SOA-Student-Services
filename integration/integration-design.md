# THIẾT KẾ TÍCH HỢP DỊCH VỤ (SERVICE INTEGRATION DESIGN - TH6)

## 1. Nhu cầu tích hợp nghiệp vụ
Trong Hệ thống Quản lý Hồ sơ Sinh viên (Student Profile System) theo nguyên lý **Database-per-Service**, cơ sở dữ liệu hồ sơ (`profile_db`) và cơ sở dữ liệu tài liệu minh chứng (`document_db`) được tách biệt hoàn toàn và không có khóa ngoại vật lý (Physical Foreign Key) giữa hai database.

Khi cán bộ đào tạo hoặc sinh viên tải lên/thêm mới một tài liệu minh chứng tại **Document Service**, hệ thống bắt buộc phải xác minh:
1. Mã sinh viên (`studentId`) có thực sự tồn tại trong hệ thống hay không.
2. Lấy chính xác họ và tên sinh viên (`fullName`) từ hồ sơ gốc để gắn vào bản ghi tài liệu.

## 2. Phân định vai trò các dịch vụ tham gia
* **Consumer Service (`document-service` - Cổng 8082):**
  * Tiếp nhận yêu cầu thêm tài liệu từ Client qua API Gateway (`POST /api/v1/documents/{studentId}`).
  * Kiểm tra xác thực JWT, phân quyền RBAC/Ownership và tính hợp lệ của đường dẫn HTTPS.
  * Sử dụng thành phần `ProfileClient` (`@LoadBalanced RestTemplate`) gọi sang `profile-service` thông qua Eureka Discovery kèm Header nội bộ `X-Internal-Service-Key`.
  * Lưu bản ghi tài liệu vào `document_db` và bắn sự kiện nhật ký sang RabbitMQ nếu xác minh thành công; hoặc hủy giao dịch (Rollback) nếu phát sinh lỗi.
* **Provider Service (`profile-service` - Cổng 8081):**
  * Quản lý độc quyền bảng `student_profiles` trên `profile_db`.
  * Cung cấp endpoint `GET /api/v1/profiles/{id}` để xác thực khóa nội bộ và trả về thông tin chi tiết hồ sơ sinh viên (hoặc trả mã `404 Not Found` nếu không tồn tại).
* **Hạ tầng điều phối (`discovery-server` 8761 & `api-gateway` 8080):**
  * Eureka Server giúp `document-service` định vị `profile-service` theo tên định danh mà không phụ thuộc địa chỉ IP/cổng cứng.
  * API Gateway đóng vai trò điểm truy cập duy nhất (Single Entry Point) cho Client/Postman ở cổng `8080`.

## 3. Cơ chế xử lý lỗi tích hợp
* **Sinh viên không tồn tại (`404 Not Found`):** Когда `profile-service` trả về 404, `ProfileClient` bắt ngoại lệ `HttpClientErrorException.NotFound` và ném `NoSuchElementException("STUDENT_NOT_FOUND...")`. `DocumentController` trả về `404 Not Found` và không lưu dữ liệu.
* **Provider ngừng hoạt động hoặc phản hồi quá hạn (`503 Service Unavailable`):** `RestTemplate` được cấu hình `connectTimeout = 3s` và `readTimeout = 5s`. Nếu `profile-service` bị tắt hoặc phản hồi chậm quá 5 giây, `ProfileClient` bắt `ResourceAccessException` và trả về `503 Service Unavailable`.
* **Đảm bảo toàn vẹn giao dịch (`@Transactional` Rollback):** Nếu quá trình lưu gặp lỗi ở bước cuối (`simulateRollback = true`), Spring Transaction tự động rollback toàn bộ bản ghi vừa thêm trong `document_db`.