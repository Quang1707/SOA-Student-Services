# Danh sách service đăng ký trên Eureka

| Application / Service ID | Instance mặc định | Instance bổ sung | Chức năng |
|---|---|---|---|
| `PROFILE-SERVICE` | `profile-service:8081` | `profile-service:8084` | Hồ sơ và xác thực sinh viên |
| `DOCUMENT-SERVICE` | `document-service:8082` | — | Quản lý tài liệu; gọi Profile bằng Service ID |
| `AUDIT-NOTIFICATION-SERVICE` | `localhost:audit-notification-service:8083` | — | Nhật ký kiểm toán; Node.js Eureka REST Client |
| `API-GATEWAY` | `api-gateway:8080` | — | Gateway và định tuyến theo `lb://` |

Cổng Dashboard là `8761`. `DISCOVERY-SERVER` là Registry độc lập, không tự đăng ký vào chính nó.

> Số lượng instance ở cột trên là cấu hình dự kiến để chạy kiểm thử. Khi nghiệm thu, chụp ảnh Eureka Dashboard thực tế và cập nhật lại danh sách nếu khác.
