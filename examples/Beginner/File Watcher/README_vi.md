# Ví dụ Theo Dõi File (File Watcher)

Đây là một ví dụ Apache Camel thân thiện với người mới bắt đầu, minh họa cách theo dõi các thay đổi của tệp tin trong một thư mục sử dụng component `file-watch` trong Camel YAML DSL.

> [!TIP]
> Bạn có thể thiết kế trực quan và điều phối route này (hoặc các tích hợp phức tạp khác) bằng **[Kaoto](https://kaoto.io/)**, trình thiết kế trực quan cho Apache Camel, trước khi triển khai tại đây.

## Quy trình Triển khai sử dụng Camel Dashboard

Bạn có thể triển khai và kiểm tra route này một cách linh hoạt qua giao diện Camel Dashboard bằng các bước sau:

### Bước 1: Tạo một Service
1. Đi tới trang/giao diện **Services** trên thanh điều hướng bên cạnh.
2. Trong thẻ **Add service**, nhập **Service name** (ví dụ: `file-watcher-service`) và mô tả (description).
3. Nhấp vào nút **Save service**. Service mới sẽ xuất hiện trong bảng **Services**.

### Bước 2: Tải Route lên Service của bạn
1. Đi tới trang/giao diện **Upload** trên thanh điều hướng bên cạnh.
2. Chọn service bạn vừa tạo (`file-watcher-service`) từ danh sách thả xuống **Services**.
3. Kéo & thả tệp [file-watch.camel.yaml](./file-watch.camel.yaml) vào vùng thả (nhãn *Drop .yaml/.yml files here*), hoặc nhấp vào để chọn/duyệt tệp từ thiết bị của bạn.
4. Nhấp vào nút **Upload all**. Bạn sẽ thấy thông báo thành công (toast), và phiên bản sẽ được lưu trong bảng lịch sử.

### Bước 3: Triển khai Phiên bản Route
1. Đi tới trang/giao diện **Deploy** trên thanh điều hướng bên cạnh.
2. Lọc hoặc chọn service của bạn (`file-watcher-service`) để xem các phiên bản của nó.
3. Mở rộng tên service của bạn trong bảng Accordion.
4. Nhấp vào nút **Deploy** (biểu tượng máy bay giấy) bên cạnh tệp `file-watch.camel.yaml` (ví dụ: `v1`).
5. Ứng dụng sẽ chạy kiểm tra tĩnh trước khi triển khai. Sau khi vượt qua kiểm tra, hệ thống sẽ hiển thị thông báo thành công và trạng thái phiên bản sẽ cập nhật thành **Deployed** (Đã triển khai).

### Bước 4: Kiểm tra Route
1. Đảm bảo thư mục được theo dõi tồn tại trên máy nơi tích hợp Camel đang chạy. Theo mặc định, nó theo dõi thư mục `/app/logs`.
   *(Lưu ý: Bạn có thể tạo thư mục `/app/logs` ở thư mục gốc của ổ đĩa hệ thống, hoặc cập nhật tham số đường dẫn (path parameter) trong tệp [file-watch.camel.yaml](./file-watch.camel.yaml) thành thư mục mong muốn trước khi tải lên).*
2. Đi tới trang **Routes** để xác nhận rằng route đang ở trạng thái **Running** (Đang chạy).
3. Kiểm tra log của bảng điều khiển backend. Bạn sẽ thấy các dòng log được in ra như sau:
   ```text
   Detect event MODIFY on file /app/logs/app.log
   Detect event MODIFY on file /app/logs/app.log
   ...
   ```
