# Ghi chú cập nhật theo Bài thực hành số 7

## Đã cập nhật trong mã nguồn

1. Giữ Eureka Server độc lập tại cổng `8761` và cấu hình chu kỳ eviction cho môi trường thực hành.
2. Đặt instance ID riêng cho các tiến trình Profile Service (`profile-service:8081`, `profile-service:8084`), nhưng cùng Service ID `profile-service`.
3. Thêm `GET /api/v1/instance-info` trên Profile Service để nhận biết instance xử lý.
4. Bổ sung `GET /api/v1/documents/discovery/profile-instance-info`; endpoint này dùng `@LoadBalanced RestTemplate` từ Document Service để gọi Profile theo Service ID, đồng thời vẫn yêu cầu JWT ở Document Service và gửi Internal Service Key.
5. Bổ sung ánh xạ lỗi không có Provider thành HTTP `503 PROFILE_SERVICE_UNAVAILABLE`.
6. Bổ sung đăng ký, heartbeat và deregister Eureka cho Audit Node Service bằng REST API; Gateway chuyển Audit route sang `lb://audit-notification-service`.
7. Thêm script chạy Profile instance thứ hai và script gửi tối thiểu 20 request, phân nhóm kết quả theo `instanceId` và xuất CSV.
8. Thêm sơ đồ kiến trúc trước/sau, thiết kế Registry, danh sách service, Postman collection và bảng test TC01–TC06.

## Cách kiểm tra

- Chạy `scripts/configure-local-secrets.ps1` trên Windows nếu chưa có `.env`.
- Khởi động PostgreSQL/RabbitMQ và Eureka; chờ Profile instance 1 ở `8081` sẵn sàng.
- Chạy `scripts/run-profile-instance-2.ps1` để mở instance thứ hai ở `8084`.
- Khởi động Document Service và API Gateway; xác nhận Eureka Dashboard hiển thị hai Profile instance.
- Chạy `scripts/test-service-discovery.ps1`; CSV đầu ra được lưu trong `tests/service-discovery/results/`.
- Thực hiện các kịch bản dừng một instance, dừng toàn bộ Provider và khởi động lại trong `tests/service-discovery/failure-tests.md`.

## Giới hạn xác minh của bản cập nhật

Đã kiểm tra cú pháp JavaScript (`node --check`), YAML, JSON Postman collection và XML của các Maven POM. Không thể chạy Maven build vì Maven Wrapper cần tải Maven distribution từ Maven Central nhưng môi trường đóng gói không truy cập được nguồn tải; Docker CLI cũng không có trong môi trường này. Vì vậy, chưa xác nhận runtime, chưa có kết quả CSV 20 request và chưa tạo ảnh chụp Eureka Dashboard. Không ghi nhận các kiểm thử đó là PASS trước khi nhóm chạy thật.

## Cấu hình bí mật

File `.env` chứa mật khẩu/secret không được đưa vào gói bàn giao. Gói có `.env.example`; trên Windows hãy chạy `scripts/configure-local-secrets.ps1` để tạo `.env` riêng trên máy.


## Cập nhật rà soát và hoàn thiện source

- Giữ nguyên thư mục `frontend/` của gói ZIP người dùng gửi.
- Bổ sung kiểm tra `X-Internal-Service-Key` cho endpoint metadata `/api/v1/instance-info`; thiếu hoặc sai key trả HTTP 403.
- Thêm unit test cho trường hợp thiếu/sai key và trường hợp key hợp lệ.
- Chuyển `spring.jpa.hibernate.ddl-auto` của Profile và Document Service từ `update` sang `validate`, thống nhất với hướng dẫn Flyway trong README.
- Mở rộng Postman Collection TH7 thành 1 request đăng nhập + 20 request discovery; request cuối tổng hợp và yêu cầu quan sát được ít nhất hai instance.
- Bổ sung hướng dẫn thu thập bằng chứng Eureka, CSV/Collection Runner và mẫu ghi nhận TC04-TC06.

**Giới hạn xác minh:** Đã kiểm tra cấu trúc và JSON của Postman Collection. Không chạy được Maven test trong môi trường chỉnh sửa vì Maven Wrapper cần tải Maven 3.10.0 từ Maven Central nhưng môi trường không có kết nối tải xuống. Các kịch bản runtime phải được chạy trên máy có Docker/Maven; không có kết quả PASS nào được tạo giả.
