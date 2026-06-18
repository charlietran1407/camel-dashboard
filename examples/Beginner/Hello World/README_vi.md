# Ví dụ Cron Log

Đây là một ví dụ Apache Camel thân thiện với người mới bắt đầu, minh họa cách lập lịch một thông báo log lặp lại sử dụng bộ kích hoạt cron (`cron:tab`) và phân giải các thuộc tính động (dynamic properties) trong Camel YAML DSL.

> [!TIP]
> Bạn có thể thiết kế trực quan và điều phối route này (hoặc các tích hợp phức tạp khác) bằng **[Kaoto](https://kaoto.io/)**, trình thiết kế trực quan cho Apache Camel, trước khi triển khai tại đây.

## Quy trình Triển khai sử dụng Camel Dashboard

Bạn có thể cấu hình, triển khai và kiểm tra route này một cách linh hoạt qua giao diện Camel Dashboard bằng các bước sau:

### Bước 1: Thêm Thuộc Tính Yêu Cầu (Required Property)
Route này tham chiếu đến một trình giữ chỗ thuộc tính (property placeholder) `{{cron.message}}`. Bạn cần định nghĩa nó trước khi triển khai:
1. Đi tới trang/giao diện **Properties** trên thanh điều hướng bên cạnh.
2. Trong thẻ **Add property**, nhập:
   - **Key**: `cron.message`
   - **Value**: `Hello World! current time is`
3. Nhấp vào nút **Save**.

### Bước 2: Tạo một Service
1. Đi tới trang/giao diện **Services** trên thanh điều hướng bên cạnh.
2. Trong thẻ **Add service**, nhập **Service name** (ví dụ: `cron-log-service`) và mô tả (description).
3. Nhấp vào nút **Save service**. Service mới sẽ xuất hiện trong bảng **Services**.

### Bước 3: Tải Route lên Service của bạn
1. Đi tới trang/giao diện **Upload** trên thanh điều hướng bên cạnh.
2. Chọn service bạn vừa tạo (`cron-log-service`) từ danh sách thả xuống **Services**.
3. Kéo & thả tệp [cron-log.camel.yaml](./cron-log.camel.yaml) vào vùng thả (nhãn *Drop .yaml/.yml files here*), hoặc nhấp vào để chọn/duyệt tệp từ thiết bị của bạn.
4. Nhấp vào nút **Upload all**. Bạn sẽ thấy thông báo thành công (toast), và phiên bản sẽ được lưu trong bảng lịch sử.

### Bước 4: Triển khai Phiên bản Route
1. Đi tới trang/giao diện **Deploy** trên thanh điều hướng bên cạnh.
2. Lọc hoặc chọn service của bạn (`cron-log-service`) để xem các phiên bản của nó.
3. Mở rộng tên service của bạn trong bảng Accordion.
4. Nhấp vào nút **Deploy** (biểu tượng máy bay giấy) bên cạnh tệp [cron-log.camel.yaml](./cron-log.camel.yaml) (ví dụ: `v1`).
5. Ứng dụng sẽ chạy kiểm tra tĩnh trước khi triển khai. Sau khi vượt qua kiểm tra, hệ thống sẽ hiển thị thông báo thành công và trạng thái phiên bản sẽ cập nhật thành **Deployed** (Đã triển khai).

### Bước 5: Xác minh Thực thi
1. Đi tới trang **Routes** trên thanh điều hướng bên cạnh để xác nhận rằng route `cron-log` đang ở trạng thái **Running** (Đang chạy).
2. Kiểm tra log của bảng điều khiển backend. Bạn sẽ thấy thông báo được in ra mỗi 5 giây:
   ```text
   Hello World! current time is 15:20:00
   Hello World! current time is 15:20:05
   ```
