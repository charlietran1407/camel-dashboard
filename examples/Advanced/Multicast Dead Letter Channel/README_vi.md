# Multicast + Dead Letter Channel (Nâng Cao)

Ví dụ Apache Camel nâng cao minh họa pattern **Multicast EIP** kết hợp với **Dead Letter Channel (DLC)**. Đây là mô phỏng thực tế của một **notification dispatcher** — nhận một sự kiện qua REST và phân phối song song tới nhiều kênh giao tiếp (Email, SMS, Webhook), đồng thời xử lý lỗi một cách chủ động thông qua cơ chế retry tự động và hàng đợi dead-letter khi kênh bị lỗi liên tục.

> [!TIP]
> Bạn có thể thiết kế và điều phối trực quan route này (hoặc các integration phức tạp hơn) bằng **[Kaoto](https://kaoto.io/)** — trình thiết kế đồ họa cho Apache Camel — trước khi triển khai lên đây.

---

## 🏗️ Kiến Trúc

```
POST /notify
     │
     ▼
[notify-dispatcher] ── errorHandler: DeadLetterChannel (3 lần retry, delay 2s)
     │
     ├─ [Multicast - song song, stopOnException: false]
     │       ├─── direct:channel-email   (giả lập lỗi 30%)
     │       ├─── direct:channel-sms     (giả lập lỗi 20%)
     │       └─── direct:channel-webhook (luôn thành công)
     │
     └─ Khi hết số lần retry ──► direct:notify-dlc
                                      │
                                      └─ Log + Lưu trữ (review thủ công / Kafka DLQ)
```

---

## ✨ Các Khái Niệm Chính

| Khái niệm | Mô tả |
|---|---|
| **Multicast EIP** | Fan-out một message tới nhiều endpoint đồng thời |
| **`parallelProcessing: true`** | Tất cả kênh được gọi song song, không blocking |
| **`stopOnException: false`** | Khi một kênh lỗi, các kênh còn lại vẫn tiếp tục chạy |
| **Dead Letter Channel** | Bắt những message đã thất bại hết số lần retry và route tới fallback handler |
| **`maximumRedeliveries: 3`** | Tự động retry tối đa 3 lần trước khi từ bỏ |
| **`useOriginalMessage: true`** | DLC nhận payload gốc ban đầu (không bị thay đổi giữa chừng) |

---

## 🚀 Triển Khai Qua Camel Dashboard

### Bước 1: Tạo Service

1. Vào **Services** trên thanh điều hướng bên trái.
2. Click **Add service**, nhập tên `notification-dispatcher` và mô tả.
3. Click **Save service**.

### Bước 2: Upload Route

1. Vào **Upload** trên thanh điều hướng.
2. Chọn `notification-dispatcher` từ dropdown **Services**.
3. Kéo & thả file [`multicast-dlc.camel.yaml`](./multicast-dlc.camel.yaml) vào dropzone.
4. Click **Upload all**.

### Bước 3: Deploy Route Version

1. Vào **Deploy** trên thanh điều hướng.
2. Tìm service `notification-dispatcher` trong accordion panel.
3. Click nút **Deploy** (✈️) bên cạnh file `multicast-dlc.camel.yaml`.
4. Chờ bước pre-deploy validation pass và thông báo thành công hiện ra.

### Bước 4: Test bằng REST call

Gửi POST request để kích hoạt dispatcher:

```bash
curl -X POST http://localhost:8080/notify \
  -H "Content-Type: application/json" \
  -d '{"userId":"u123","event":"ORDER_PLACED","message":"Đơn hàng #456 của bạn đã được đặt."}'
```

Kết quả trả về:
```json
{ "status": "dispatched", "channels": ["email", "sms", "webhook"] }
```

### Bước 5: Quan Sát Log

Kiểm tra console log phía backend. Bạn sẽ thấy các kênh chạy song song:
```
[DISPATCHER] Received event '...' — multicasting to all channels
[EMAIL] ✅ Notification sent to user via email.
[SMS] ✅ Notification sent to user via SMS.
[WEBHOOK] ✅ Notification pushed to webhook endpoint.
```

Khi một kênh bị lỗi (ngẫu nhiên theo xác suất), bạn sẽ thấy cơ chế retry và DLC kích hoạt:
```
[DLC] ❌ Channel failed after retries. Storing failed message for manual review.
       Cause: Email server timeout — channel unavailable
```

---

## 🔧 Tùy Chỉnh

- **Thay thế các bước `log`** bằng integration thực tế: `smtp:` (email), HTTP call đến SMS gateway, hoặc `http:` cho webhook.
- **Thay thế bước log trong DLC** bằng `jpa:` để lưu message thất bại vào DB, hoặc `kafka:` để đẩy vào Kafka Dead Letter Queue.
- **Điều chỉnh retry** (`maximumRedeliveries`, `redeliveryDelay`) trong block `errorHandler` theo yêu cầu SLA của hệ thống.

---

## 📚 Tài Liệu Tham Khảo

- [Multicast EIP](https://camel.apache.org/components/latest/eips/multicast-eip.html)
- [Dead Letter Channel](https://camel.apache.org/components/latest/eips/dead-letter-channel.html)
- [Error Handler](https://camel.apache.org/manual/error-handler.html)
