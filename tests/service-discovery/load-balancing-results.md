# Kết quả kiểm thử LoadBalancer

## Cách thực hiện

1. Khởi chạy Registry, PostgreSQL, RabbitMQ và các service nghiệp vụ.
2. Chạy Profile Service mặc định ở `8081`.
3. Mở terminal thứ hai và chạy `scripts/run-profile-instance-2.ps1` để mở instance `8084`.
4. Xác nhận Eureka Dashboard có hai instance `PROFILE-SERVICE` ở trạng thái `UP`.
5. Chạy `scripts/test-service-discovery.ps1`. Script đăng nhập bằng tài khoản demo, gửi tối thiểu 20 request từ API Gateway tới Document Service; Document Service tiếp tục gọi endpoint Profile qua Eureka/LoadBalancer.
6. Hoặc import `postman-collection.json` vào Postman, đặt `baseUrl` nếu cần, chạy toàn bộ collection (không chạy riêng lẻ từng request). Collection đăng nhập một lần và gửi 20 request discovery liên tiếp; request thứ 20 kiểm tra đã quan sát ít nhất hai `instanceId`.
7. CSV từ PowerShell ghi lại `instanceId`, cổng và thời gian nhận phản hồi. Với Postman, lưu kết quả Collection Runner và ảnh chụp màn hình.

## Kết quả

Chưa chạy thử trong môi trường của người dùng ở lần cập nhật source này. Sau khi chạy script, đính kèm CSV phát sinh trong `tests/service-discovery/results/` và điền bảng dưới đây.

| Instance | Số request | Kết quả |
|---|---:|---|
| `profile-service:8081` | Chờ chạy test | Chưa xác minh |
| `profile-service:8084` | Chờ chạy test | Chưa xác minh |
| Tổng | Tối thiểu 20 | Chưa xác minh |

Không bắt buộc hai instance nhận chính xác 50% request. Cần quan sát được request tới cả hai instance.


## Bằng chứng cần nộp

- Ảnh Eureka Dashboard có đồng thời hai dòng `PROFILE-SERVICE` ở trạng thái `UP`.
- CSV `load-balancing-*.csv` sinh bởi script PowerShell hoặc kết quả Collection Runner đủ 20 request.
- Bảng tổng hợp số request theo từng `instanceId` (không yêu cầu chia đều 50/50).
- Kết quả kiểm tra failover TC04-TC06 trong `failure-tests.md`, kèm thời điểm dừng service và thời điểm Eureka cập nhật trạng thái.

**Trạng thái trong source:** Chưa có bằng chứng runtime được tạo sẵn. Không đánh dấu PASS trước khi chạy trên môi trường thực tế.
