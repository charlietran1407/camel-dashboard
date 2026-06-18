# Ví dụ Xử Lý Ngoại Lệ (Exception Handling)

Đây là một ví dụ Apache Camel thân thiện với người mới bắt đầu, minh họa cách triển khai xử lý ngoại lệ ở cấp độ route sử dụng `onException`, xác thực các tham số truy vấn (query parameters) một cách linh hoạt và trả về các phản hồi lỗi HTTP tùy chỉnh (như `400 Bad Request`) sử dụng REST DSL.

> [!TIP]
> Bạn có thể thiết kế trực quan và điều phối route này (hoặc các tích hợp phức tạp khác) bằng **[Kaoto](https://kaoto.io/)**, trình thiết kế trực quan cho Apache Camel, trước khi triển khai tại đây.

## Quy trình Triển khai sử dụng Camel Dashboard

Bạn có thể triển khai và kiểm tra route này một cách linh hoạt qua giao diện Camel Dashboard bằng các bước sau:

### Bước 1: Tạo một Service
1. Đi tới trang/giao diện **Services** trên thanh điều hướng bên cạnh.
2. Trong thẻ **Add service**, nhập **Service name** (ví dụ: `exception-handling-service`) và mô tả (description).
3. Nhấp vào nút **Save service**. Service mới sẽ xuất hiện trong bảng **Services**.

### Bước 2: Tải Route lên Service của bạn
1. Đi tới trang/giao diện **Upload** trên thanh điều hướng bên cạnh.
2. Chọn service bạn vừa tạo (`exception-handling-service`) từ danh sách thả xuống **Services**.
3. Kéo & thả tệp [exception-handling.camel.yaml](./exception-handling.camel.yaml) vào vùng thả (nhãn *Drop .yaml/.yml files here*), hoặc nhấp vào để chọn/duyệt tệp từ thiết bị của bạn.
4. Nhấp vào nút **Upload all**. Bạn sẽ thấy thông báo thành công (toast), và phiên bản sẽ được lưu trong bảng lịch sử.

### Bước 3: Triển khai Phiên bản Route
1. Đi tới trang/giao diện **Deploy** trên thanh điều hướng bên cạnh.
2. Lọc hoặc chọn service của bạn (`exception-handling-service`) để xem các phiên bản của nó.
3. Mở rộng tên service của bạn trong bảng Accordion.
4. Nhấp vào nút **Deploy** (biểu tượng máy bay giấy) bên cạnh tệp `exception-handling.camel.yaml` (ví dụ: `v1`).
5. Ứng dụng sẽ chạy kiểm tra tĩnh trước khi triển khai. Sau khi vượt qua kiểm tra, hệ thống sẽ hiển thị thông báo thành công và trạng thái phiên bản sẽ cập nhật thành **Deployed** (Đã triển khai).

### Bước 4: Kiểm tra Endpoint
Sau khi triển khai thành công, route Camel sẽ hiển thị endpoint `/payment` được ánh xạ dưới đường dẫn REST servlet context là `/cameldash`.

#### 1. Yêu cầu Hợp lệ (Amount > 0)
Mở trình duyệt của bạn và truy cập:
[http://localhost:8080/cameldash/payment?amount=150](http://localhost:8080/cameldash/payment?amount=150)

**Phản hồi (Response) (200 OK):**
```json
{
  "status": "success",
  "message": "Payment of 150 processed successfully."
}
```

#### 2. Yêu cầu Không Hợp lệ (Amount <= 0)
Mở trình duyệt của bạn và truy cập:
[http://localhost:8080/cameldash/payment?amount=-5](http://localhost:8080/cameldash/payment?amount=-5)

**Phản hồi (Response) (400 Bad Request):**
```json
{
  "status": "error",
  "message": "Invalid amount: -5. Amount must be greater than 0."
}
```

#### 3. Yêu cầu Thiếu Tham Số (Missing Parameter Request)
Mở trình duyệt của bạn và truy cập:
[http://localhost:8080/cameldash/payment](http://localhost:8080/cameldash/payment)

**Phản hồi (Response) (400 Bad Request):**
```json
{
  "status": "error",
  "message": "Missing required query parameter: amount"
}
```
