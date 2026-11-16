# Kịch bản kiểm thử Discovery và xử lý lỗi

| Mã | Tình huống | Cách kiểm tra | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|---|
| TC01 | Khởi động Registry và các service | Mở `http://localhost:8761` | Profile, Document, Audit và Gateway đăng ký `UP` | Chưa chạy |
| TC02 | Consumer gọi Provider theo Service ID | Chạy GET `/api/v1/documents/discovery/profile-instance-info` qua Gateway với JWT | Trả metadata Profile instance, không dùng URL/port Provider cố định | Chưa chạy |
| TC03 | Hai Profile instance | Chạy script 20 request | CSV có tối thiểu hai `instanceId` khác nhau | Chưa chạy |
| TC04 | Dừng một Profile instance | Dừng tiến trình ở cổng `8081` hoặc `8084`; đợi Eureka cập nhật lease; chạy lại test | Instance còn lại tiếp tục trả response | Chưa chạy |
| TC05 | Dừng toàn bộ Profile instance | Dừng cả hai tiến trình, gọi endpoint diagnostic | Consumer trả lỗi HTTP 503 rõ ràng, không chờ vô hạn | Chưa chạy |
| TC06 | Khởi động lại Provider | Khởi động lại một Profile instance | Instance đăng ký lại và có thể được chọn | Chưa chạy |

## Lưu ý về độ trễ

Eureka phát hiện instance dừng theo heartbeat/lease và eviction; không đảm bảo loại bỏ ngay lập tức. Sau khi dừng một tiến trình, chờ Dashboard phản ánh thay đổi trước khi đánh giá kết quả failover. Nếu cần kiểm tra sát thời điểm dừng, ghi riêng lỗi kết nối tức thời và lỗi sau khi Registry cập nhật.

## Thao tác demo

- Một instance: dừng tiến trình Profile tại `8084`, giữ `8081` hoạt động.
- Không còn instance: dừng cả `8081` và `8084`, sau đó gọi endpoint diagnostic qua Gateway.
- Phục hồi: chạy lại `8081` hoặc `8084`, quan sát đăng ký lại trên Dashboard.


## Mẫu ghi nhận khi thực thi

| Trường | Giá trị cần điền |
|---|---|
| Ngày/giờ kiểm thử | |
| Máy / hệ điều hành | |
| Java version | |
| Eureka Dashboard (ảnh) | |
| Profile instance IDs trước kiểm thử | |
| Kết quả 20 request (CSV/Collection Runner) | |
| TC04: còn một instance | |
| TC05: không còn instance | |
| TC06: instance đăng ký lại | |
| Người thực hiện | |

Không điền kết quả dự kiến vào cột kết quả thực tế. Nếu một kịch bản chưa chạy, ghi `Chưa chạy` và bổ sung lý do.
