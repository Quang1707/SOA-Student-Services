# Thiết kế Microservices – Student Profile System (Nhóm 9)

## Ranh giới dịch vụ và quyền sở hữu dữ liệu

| Service ID | Trách nhiệm | API chính | Dữ liệu sở hữu | Consumer / Provider |
|---|---|---|---|---|
| `PROFILE-SERVICE` | Đăng nhập, JWT, quản lý hồ sơ sinh viên | `/api/v1/auth/**`, `/api/v1/profiles/**`, `/api/v1/instance-info` | `profile_db` | Consumer: API Gateway, Document Service. Provider: Profile API |
| `DOCUMENT-SERVICE` | Quản lý metadata và file tài liệu sinh viên; xác minh chủ hồ sơ trước khi lưu | `/api/v1/documents/**`, `/api/v1/documents/discovery/profile-instance-info` | `document_db`, thư mục `uploads/` | Consumer: API Gateway; Provider: Profile Service qua Eureka |
| `AUDIT-NOTIFICATION-SERVICE` | Nhận sự kiện RabbitMQ và cung cấp nhật ký kiểm toán | `/api/v1/audits/**`, `/health` | `audit_db` | Consumer: RabbitMQ; Provider: API Gateway qua Eureka |
| `API-GATEWAY` | Cổng truy cập HTTP, định tuyến theo Service ID | Cùng các route `/api/v1/**` được khai báo | Không có DB nghiệp vụ | Consumer: Frontend; Provider: Profile, Document, Audit |
| `DISCOVERY-SERVER` | Đăng ký, lưu lease và cung cấp danh sách instance Eureka | Dashboard `:8761`, REST `/eureka/**` | Registry metadata, không phải DB nghiệp vụ | Được các service đăng ký và consumer truy vấn |

`PROFILE-SERVICE` và `DOCUMENT-SERVICE` là các tiến trình Spring Boot. `AUDIT-NOTIFICATION-SERVICE` là Node.js/Express, cũng đăng ký Eureka qua REST API. Mỗi service chỉ truy cập database do chính nó sở hữu; Document Service không truy vấn trực tiếp `profile_db`.

## Kiến trúc trước khi áp dụng Discovery

```mermaid
flowchart LR
    UI[Frontend] --> GW[API Gateway :8080]
    GW -->|URL/cổng cấu hình| P[Profile Service :8081]
    GW -->|URL/cổng cấu hình| D[Document Service :8082]
    GW -->|URL/cổng cấu hình| A[Audit Service :8083]
    D -->|URL cố định localhost:8081| P
    P --> PDB[(profile_db)]
    D --> DDB[(document_db)]
    A --> ADB[(audit_db)]
```

## Kiến trúc sau khi áp dụng TH7

```mermaid
flowchart TB
    UI[Frontend] --> GW[API Gateway :8080]
    E[Eureka Discovery Server :8761]
    P1[Profile instance :8081]
    P2[Profile instance :8084]
    D[Document Service :8082]
    A[Audit Notification Service :8083]
    GW -->|lb://profile-service| P1
    GW -->|lb://profile-service| P2
    GW -->|lb://document-service| D
    GW -->|lb://audit-notification-service| A
    D -->|Service ID profile-service + LoadBalancer| P1
    D -->|Service ID profile-service + LoadBalancer| P2
    P1 --> PDB[(profile_db - dùng chung)]
    P2 --> PDB
    D --> DDB[(document_db)]
    A --> ADB[(audit_db)]
    P1 -. đăng ký/heartbeat .-> E
    P2 -. đăng ký/heartbeat .-> E
    D -. đăng ký/heartbeat .-> E
    A -. đăng ký/heartbeat .-> E
    GW -. đăng ký/khám phá .-> E
```

Hai Profile instance cùng Service ID, cùng API contract và cùng PostgreSQL database. Instance thứ hai không tạo database độc lập, tránh hai bộ hồ sơ lệch dữ liệu.
