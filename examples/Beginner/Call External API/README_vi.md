# Ví dụ Gọi API Bên Ngoài

Đây là một ví dụ Apache Camel thân thiện với người mới bắt đầu, minh họa cách lập lịch một tác vụ bằng component `timer` và lấy dữ liệu từ một endpoint REST HTTP bên ngoài bằng component `http` trong Camel YAML DSL.

> [!TIP]
> Bạn có thể thiết kế trực quan và điều phối route này (hoặc các tích hợp phức tạp khác) bằng **[Kaoto](https://kaoto.io/)**, trình thiết kế trực quan cho Apache Camel, trước khi triển khai tại đây.

## Quy trình Triển khai sử dụng Camel Dashboard

Bạn có thể triển khai và giám sát route này một cách linh hoạt qua giao diện Camel Dashboard bằng các bước sau:

### Bước 1: Tạo một Service
1. Đi tới trang/giao diện **Services** trên thanh điều hướng bên cạnh (sidebar).
2. Trong thẻ **Add service**, nhập **Service name** (ví dụ: `call-external-api-service`) và mô tả (description).
3. Nhấp vào nút **Save service**. Service mới sẽ xuất hiện trong bảng **Services**.

### Bước 2: Tải Route lên Service của bạn
1. Đi tới trang/giao diện **Upload** trên thanh điều hướng bên cạnh.
2. Chọn service bạn vừa tạo (`call-external-api-service`) từ danh sách thả xuống **Services**.
3. Kéo & thả tệp [call-external-api.camel.yaml](./call-external-api.camel.yaml) vào vùng thả (nhãn *Drop .yaml/.yml files here*), hoặc nhấp vào để chọn/duyệt tệp từ thiết bị của bạn.
4. Nhấp vào nút **Upload all**. Bạn sẽ thấy thông báo thành công (toast), và phiên bản sẽ được lưu trong bảng lịch sử (history table).

### Bước 3: Triển khai Phiên bản Route
1. Đi tới trang/giao diện **Deploy** trên thanh điều hướng bên cạnh.
2. Lọc hoặc chọn service của bạn (`call-external-api-service`) để xem các phiên bản của nó.
3. Mở rộng tên service của bạn trong bảng Accordion.
4. Nhấp vào nút **Deploy** (biểu tượng máy bay giấy) bên cạnh tệp `call-external-api.camel.yaml` (ví dụ: `v1`).
5. Ứng dụng sẽ chạy kiểm tra tĩnh trước khi triển khai (static pre-deployment check). Sau khi vượt qua kiểm tra, hệ thống sẽ hiển thị thông báo thành công và trạng thái phiên bản sẽ cập nhật thành **Deployed** (Đã triển khai).

### Bước 4: Xác minh Thực thi
Vì route này được kích hoạt tự động bởi component `timer` khi khởi động, bạn không cần gửi bất kỳ yêu cầu (request) bên ngoài nào để kiểm tra.

1. Đi tới trang **Routes** trên thanh điều hướng bên cạnh để xác nhận rằng route `route-3540` của bạn đang ở trạng thái **Running** (Đang chạy).
2. Kiểm tra log của bảng điều khiển backend (backend console logs). Bạn sẽ thấy nội dung JSON được lấy từ API bên ngoài được in ra màn hình console:
   ```json
   {
     "userId": 1,
     "id": 1,
     "title": "sunt aut facere repellat provident occaecati excepturi optio reprehenderit",
     "body": "quia et suscipit\nsuscipit recusandae... "
   }
   ```
   *(Lưu ý: timer được thiết lập để chạy mỗi 1000ms và lặp lại 10 lần, vì vậy bạn sẽ thấy log này xuất hiện tổng cộng 10 lần sau khi triển khai).*
