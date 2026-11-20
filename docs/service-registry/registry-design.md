# Thiết kế Service Registry & Service Discovery

## 1. Tổng quan

Hệ thống **SOA-Student-Services** sử dụng **Netflix Eureka Server** (module `discovery-server`) làm trung tâm đăng ký và khám phá dịch vụ (Service Registry). Việc áp dụng Service Registry giúp các dịch vụ không cần hardcode địa chỉ IP hoặc cổng cố định khi giao tiếp, đồng thời hỗ trợ mở rộng nhiều instance và cân bằng tải tự động trong môi trường Docker.

## 2. Thành phần tham gia Registry

| Thành phần                   | Vai trò trong Registry              | Giao tiếp / Cơ chế khám phá                                                                                                               |
| :--------------------------- | :---------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------- |
| `discovery-server`           | Eureka Server (Service Registry)    | Quản lý danh sách instance đang hoạt động, cung cấp Eureka Dashboard (`http://localhost:8761`).                                           |
| `api-gateway`                | Eureka Client                       | Khám phá các dịch vụ nghiệp vụ qua Service ID (`lb://<service-id>`) để định tuyến yêu cầu từ Client/Frontend.                             |
| `profile-service`            | Eureka Client (Provider)            | Đăng ký dưới cùng một Service ID; hỗ trợ chạy nhiều instance (có `InstanceInfoController` để kiểm tra instance xử lý request).            |
| `document-service`           | Eureka Client (Consumer & Provider) | Đăng ký vào Eureka và gọi `profile-service` thông qua `ProfileClient` kết hợp `RestTemplate` + Spring Cloud LoadBalancer bằng Service ID. |
| `audit-notification-service` | Không đăng ký Eureka                | Dịch vụ Node.js giao tiếp bất đồng bộ qua **RabbitMQ**, nhận sự kiện audit/thông báo từ `profile-service` và `document-service`.          |

## 3. Luồng đăng ký và khám phá dịch vụ

1. **Khởi động Registry:** Container `discovery-server` khởi động trước và sẵn sàng nhận kết nối (kiểm tra qua healthcheck `/actuator/health`).
2. **Đăng ký dịch vụ (Registration):** Khi `profile-service`, `document-service` và `api-gateway` khởi động, mỗi instance gửi thông tin (Service ID, hostname/IP trong Docker Network, cổng nội bộ) về Eureka Server.
3. **Duy trì trạng thái (Heartbeat):** Các Eureka Client định kỳ gửi heartbeat về `discovery-server`. Nếu một instance dừng hoặc mất kết nối quá thời gian cấu hình, Eureka sẽ tự động loại bỏ instance đó khỏi danh sách khả dụng.
4. **Khám phá & Cân bằng tải (Discovery & Load Balancing):**
   - Client gửi request tới `api-gateway`.
   - `api-gateway` tra cứu danh sách instance từ Eureka và điều phối request.
   - Khi `document-service` cần xác thực/lấy thông tin hồ sơ sinh viên, `ProfileClient` gọi `profile-service` thông qua Service ID thay vì IP/port cố định. Nếu `profile-service` chạy nhiều instance, LoadBalancer sẽ phân phối request giữa các instance đang `UP`.

## 4. Cấu hình trong môi trường Docker Network

- Các dịch vụ giao tiếp với Eureka thông qua **tên service nội bộ trong Docker Network** (ví dụ: `http://discovery-server:8761/eureka/`), **không sử dụng `localhost`**.
- Toàn bộ URL Eureka và cấu hình kết nối được truyền qua biến môi trường (`.env` / `docker-compose.yml`), không hardcode trong mã nguồn.
- Kết hợp Spring Boot Actuator (`/actuator/health`) và Docker `healthcheck` (`condition: service_healthy`) để đảm bảo các dịch vụ nghiệp vụ chỉ đăng ký sau khi Eureka Server đã thực sự sẵn sàng.

## 5. Xử lý sự cố liên quan đến Registry

- **Một instance của `profile-service` dừng:** Eureka phát hiện mất heartbeat, ngừng định tuyến tới instance lỗi; instance còn lại tiếp tục phục vụ.
- **Toàn bộ `profile-service` dừng:** `document-service` không thể phân giải instance khả dụng, ghi nhận log lỗi và trả về phản hồi lỗi phù hợp cho Client.
- **Khởi động lại dịch vụ:** Dịch vụ tự động đăng ký lại vào Eureka và tiếp tục nhận yêu cầu mà không cần cấu hình lại thủ công.
