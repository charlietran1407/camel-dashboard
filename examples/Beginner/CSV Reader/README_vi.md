# Ví dụ Trình Đọc File CSV

Đây là một ví dụ Apache Camel thân thiện với người mới bắt đầu, minh họa cách nhận một file CSV tải lên, giải tuần tự (unmarshal) nó thành các đối tượng Java, chia nhỏ các hàng (split the rows) và in từng hàng ra màn hình log console sử dụng định dạng dữ liệu `csv` gốc của Camel và EIP `split`.

> [!TIP]
> Bạn có thể thiết kế trực quan và điều phối route này (hoặc các tích hợp phức tạp khác) bằng **[Kaoto](https://kaoto.io/)**, trình thiết kế trực quan cho Apache Camel, trước khi triển khai tại đây.

## Quy trình Triển khai sử dụng Camel Dashboard

Bạn có thể triển khai và kiểm tra route này một cách linh hoạt qua giao diện Camel Dashboard bằng các bước sau:

### Bước 1: Tạo một Service
1. Đi tới trang/giao diện **Services** trên thanh điều hướng bên cạnh.
2. Trong thẻ **Add service**, nhập **Service name** (ví dụ: `csv-reader-service`) và mô tả (description).
3. Nhấp vào nút **Save service**. Service mới sẽ xuất hiện trong bảng **Services**.

### Bước 2: Tải Route lên Service của bạn
1. Đi tới trang/giao diện **Upload** trên thanh điều hướng bên cạnh.
2. Chọn service bạn vừa tạo (`csv-reader-service`) từ danh sách thả xuống **Services**.
3. Kéo & thả tệp [csv-reader.camel.yaml](./csv-reader.camel.yaml) vào vùng thả (nhãn *Drop .yaml/.yml files here*), hoặc nhấp vào để chọn/duyệt tệp từ thiết bị của bạn.
4. Nhấp vào nút **Upload all**. Bạn sẽ thấy thông báo thành công (toast), và phiên bản sẽ được lưu trong bảng lịch sử.

### Bước 3: Triển khai Phiên bản Route
1. Đi tới trang/giao diện **Deploy** trên thanh điều hướng bên cạnh.
2. Lọc hoặc chọn service của bạn (`csv-reader-service`) để xem các phiên bản của nó.
3. Mở rộng tên service của bạn trong bảng Accordion.
4. Nhấp vào nút **Deploy** (biểu tượng máy bay giấy) bên cạnh tệp `csv-reader.camel.yaml` (ví dụ: `v1`).
5. Ứng dụng sẽ chạy kiểm tra tĩnh trước khi triển khai.

> [!WARNING]
> **Lưu ý về Classpath & Tải Thư Viện Phụ Thuộc (Dependency Loading):**
> Vì thư viện phụ thuộc `camel-csv` không nằm trong classpath ban đầu của dashboard, lượt thử triển khai đầu tiên của bạn sẽ hiển thị cảnh báo xác thực:
> 
> *“Missing Camel components were downloaded and staged in './libs': [org.apache.camel:camel-csv:4.20.0]. Please restart the application (the JARs will be picked up automatically via -Dloader.path), then re-deploy this route version.”*
> 
> Để khắc phục điều này:
> 1. Khởi động lại ứng dụng Camel Dashboard để nó nhận các tệp JAR đã được tải xuống trong thư mục `./libs`.
> 2. Quay lại trang **Deploy** và nhấp lại vào nút **Deploy**.

### Bước 4: Kiểm tra Endpoint
Sau khi triển khai thành công, route Camel sẽ hiển thị đường dẫn `/csv` trực tiếp trên công cụ platform HTTP.

#### 1. Sử dụng File CSV Mẫu được cung cấp
Bạn có thể sử dụng file [users.csv](./users.csv) mẫu nằm trong thư mục này để kiểm tra.

#### 2. Gửi Yêu cầu Tải lên (Upload Request)
Sử dụng một công cụ HTTP client (như Postman hoặc curl) để gửi yêu cầu `POST` tới `http://localhost:8080/csv` với file CSV được tải lên trong phần body dưới dạng multipart form-data.

**Ví dụ lệnh `curl`:**
```bash
curl -X POST -F "file=@users.csv" http://localhost:8080/csv
```

**Phản hồi (Response) (200 OK):**
```json
{
  "status": "success",
  "message": "CSV file processed and logged successfully"
}
```

#### 3. Xác minh Log Đầu Ra
1. Đi tới trang **Routes** để kiểm tra trạng thái route của bạn là **Running** (Đang chạy).
2. Kiểm tra log của bảng điều khiển backend. Bạn sẽ thấy các dòng log được in ra cho từng hàng:
   ```text
   CSV Row: [id, name, role]
   CSV Row: [1, John Doe, Admin]
   CSV Row: [2, Jane Smith, User]
   ```
