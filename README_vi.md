<div align="center">

# 🐪 Camel Dashboard

**Triển khai các route Apache Camel mà không cần viết code Java.**

[English](README.md) | Tiếng Việt

Một dashboard quản lý hiện đại cho Apache Camel — tải lên các route YAML, triển khai trực tiếp,
giám sát cluster và để trợ lý AI của bạn thực hiện các tác vụ nặng thông qua tích hợp MCP gốc.

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-6DB33F?style=flat-square&logo=spring)](https://spring.io/projects/spring-boot)
[![Apache Camel](https://img.shields.io/badge/Apache%20Camel-4.x-E34F26?style=flat-square)](https://camel.apache.org/)
[![Vue 3](https://img.shields.io/badge/Vue-3.x-4FC08D?style=flat-square&logo=vue.js)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat-square&logo=redis)](https://redis.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square)](LICENSE)
[![Status](https://img.shields.io/badge/status-active%20development-blue?style=flat-square)](https://github.com/charlietran1407/apache-camel-dashboard)

· [**📸 Ảnh chụp màn hình**](#-ảnh-chụp-màn-hình) · [**🚀 Khởi động nhanh**](#-khởi-động-nhanh) · [**🤖 Cấu hình MCP**](#-mcp--vibe-coding)

</div>

---

> [!NOTE]
> **Trạng thái dự án**: Dự án này hiện đang trong **giai đoạn phát triển**. Các tính năng được bổ sung thường xuyên, và các cấu hình hoặc API có thể thay đổi. Mọi đóng góp và phản hồi đều được chào đón!

## ✨ Camel Dashboard là gì?

Camel Dashboard là một nền tảng để quản lý các route Apache Camel. Thay vì viết Java và build lại code, bạn:

1. ✍️ **Viết YAML** — sử dụng Apache Camel YAML DSL tiêu chuẩn (hoặc yêu cầu AI viết giúp)
2. ⬆️ **Tải lên** — xác thực và tải lên dashboard
3. 🚀 **Triển khai trực tiếp (Live Deploy)** — các route sẽ được nạp trực tiếp vào Camel context đang chạy mà không làm gián đoạn hệ thống (zero downtime)
4. 📊 **Giám sát** — theo dõi các route trên tất cả các node trong cluster ngay từ dashboard

**Hỗ trợ MCP**: Kết nối Claude, Cursor hoặc bất kỳ AI nào tương thích với MCP để triển khai các route bằng ngôn ngữ tự nhiên.

---

## 📸 Ảnh chụp màn hình

| Danh sách Route |
|---|
| ![Routes](docs/screenshot_routes.png)

---

## 🚀 Khởi động nhanh

### Yêu cầu hệ thống

- Docker & Docker Compose
- Java 21+
- PostgreSQL 16
- Redis 7 (tùy chọn, cho chế độ cluster)

### 1. Clone & Cấu hình

```bash
git clone https://github.com/charlietran1407/apache-camel-dashboard
cd apache-camel-dashboard
cp dockers/.env.example .env
```

Chỉnh sửa file `.env` và điền các giá trị cần thiết:

```env
# Bắt buộc
DATASOURCE_URL=jdbc:postgresql://localhost:5432/cameldash?options=-c%20timezone=UTC
DATASOURCE_USERNAME=cameldash
DATASOURCE_PASSWORD=your-secure-password
CAMEL_DASHBOARD_ENCRYPT_KEY=your-32-char-secret-key

# Tùy chọn
CAMEL_CONTEXT_PATH=/cameldash
SERVER_PORT=8080
```

### 2. Khởi chạy bằng Docker Compose

Khởi động các dịch vụ hạ tầng trước (PostgreSQL, Redis, Jaeger), sau đó khởi chạy ứng dụng Camel Dashboard:

```bash
# Khởi động hạ tầng
docker-compose -f dockers/docker-compose.infra.yaml up -d

# Khởi động Camel Dashboard
docker-compose -f dockers/example_docker-compose.yaml up -d
```

### 3. Mở Dashboard

- **Camel Dashboard**: Truy cập **http://localhost:8080** — bạn sẽ thấy giao diện Camel Dashboard!
- **Jaeger (Distributed Tracing)**: Truy cập **http://localhost:16686** để theo dõi và xem log tracing của các route.

### 4. Chạy ở chế độ Dev

```powershell
# Backend (Spring Boot với profile dev)
.\mvnw.cmd spring-boot:run -Pdev

# Frontend (Vue dev server với HMR)
cd frontend
pnpm install
pnpm dev
# → http://localhost:5173
```

---

## 🤖 MCP / Vibe Coding

Camel Dashboard tích hợp sẵn **MCP server** tại `/mcp`. Hãy kết nối với bất kỳ trợ lý AI nào tương thích với MCP để triển khai route bằng ngôn ngữ tự nhiên.

### Thêm vào AI client

Tùy thuộc vào IDE hoặc client bạn sử dụng, hãy áp dụng một trong các cấu hình sau:

#### Google Antigravity IDE (`mcp_config.json`)
```json
{
  "mcpServers": {
    "camel-dashboard": {
      "serverUrl": "http://localhost:8080/mcp",
      "headers": {
        "X-MCP-API-KEY": "MCP-key"
      }
    }
  }
}
```

#### Claude Desktop / Cursor (`claude_desktop_config.json`)
```json
{
  "mcpServers": {
    "camel-dashboard": {
      "type": "sse",
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

### Các công cụ MCP có sẵn (MCP Tools)

| Công cụ | Mô tả |
|---|---|
| `services_upsert` | Tạo hoặc cập nhật service để nhóm các phiên bản route |
| `routes_upload_version` | Tải lên một route YAML (.yaml hoặc .yml) dưới dạng phiên bản triển khai mới |
| `routes_validate` | Xác thực nhanh (cú pháp - FAST) hoặc trước khi triển khai (chạy thử - PRE_DEPLOY) |
| `routes_deploy_version` | Triển khai phiên bản route đã tải lên trước đó |
| `routes_deploy_and_start` | Triển khai và khởi động route ngay lập tức |
| `routes_start` | Khởi động một route đã dừng bằng ID |
| `routes_get_status` | Lấy trạng thái thời gian thực của route |

### Quy trình làm việc mẫu với AI

```
Bạn: "Tạo một endpoint REST GET /products trả về danh sách sản phẩm dưới dạng JSON"

AI (qua MCP):
  1. services_upsert → tạo service "Products API"
  2. routes_upload_version → tải lên file YAML được tạo
  3. routes_deploy_and_start → triển khai và khởi động route

Kết quả: ✅ Endpoint REST của bạn đã hoạt động chỉ sau vài giây
```

---

## 📋 Tính năng nổi bật

### Quản lý Route
- ▶️ Khởi động, ⏹️ dừng, ⏸️ tạm dừng, ▶️ tiếp tục các route theo thời gian thực
- Trạng thái chi tiết trên từng node trong toàn bộ cluster
- Lịch sử route và theo dõi phiên bản

### Quản lý Service & Phiên bản
- Nhóm các route liên quan vào các service được đặt tên
- Lưu trữ lịch sử phiên bản không giới hạn kèm theo mô tả và mốc thời gian
- Quay lại (rollback) phiên bản cũ chỉ với một cú click
- Tự động khôi phục (restore) khi khởi động lại ứng dụng

### Tải lên
- Kéo thả các file route YAML (.yaml hoặc .yml)
- **Chế độ FAST**: kiểm tra cú pháp + phát hiện trùng lặp ID route
- **Chế độ PRE_DEPLOY**: chạy thử hoàn chỉnh ở runtime trước khi triển khai thực tế
- Tự động rollback nếu nạp route thất bại — đảm bảo service không bị gián đoạn

### Hỗ trợ Cluster (Cụm máy chủ)
- Sử dụng Redis Streams + Pub/Sub để điều phối giữa các node
- Gửi tín hiệu heartbeat mỗi 10 giây, phát hiện node ngoại tuyến (offline) sau 30 giây
- Tự động loại bỏ node sau 5 phút ngoại tuyến
- Theo dõi trạng thái route của từng node từ bất kỳ instance dashboard nào

### Bảo mật
- Mã hóa AES cho các thuộc tính môi trường nhạy cảm
- Inject các secret cấu hình vào route khi chạy (runtime)
- Đảm bảo không để lộ secret trong file YAML

### Giám sát
- Chỉ số Micrometer (độ trễ route, thông lượng, tỷ lệ lỗi)
- Tích hợp OpenTelemetry / OTLP
- Theo dõi lịch sử chi tiết cho từng tin nhắn

---

## ⚙️ Cấu hình

### Các biến bắt buộc

| Biến môi trường | Mô tả |
|---|---|
| `DATASOURCE_URL` | JDBC URL kết nối PostgreSQL |
| `DATASOURCE_USERNAME` | Tên đăng nhập cơ sở dữ liệu |
| `DATASOURCE_PASSWORD` | Mật khẩu cơ sở dữ liệu |
| `CAMEL_DASHBOARD_ENCRYPT_KEY` | Khóa mã hóa AES cho các thuộc tính nhạy cảm |

### Các biến tùy chọn

| Biến môi trường | Mặc định | Mô tả |
|---|---|---|
| `SERVER_PORT` | `8080` | Cổng máy chủ HTTP |
| `CAMEL_CONTEXT_PATH` | `/cameldash` | Đường dẫn REST base của Camel |
| `CAMEL_DASHBOARD_CLUSTER_ENABLED` | `false` | Bật tính năng điều phối cluster bằng Redis |
| `CAMEL_DASHBOARD_CLUSTER_STREAM_KEY` | `{group}:cluster:event:stream` | Khóa dòng (stream key) của Redis |
| `CAMEL_DASHBOARD_CLUSTER_CHANNEL` | `{group}:cluster:event:channel` | Kênh pub/sub của Redis |
| `REDIS_HOST` | — | Host của Redis (bắt buộc nếu bật cluster) |
| `REDIS_PORT` | — | Port của Redis |
| `REDIS_PASSWORD` | — | Mật khẩu của Redis |
| `OTEL_LOGS_EXPORTER` | `none` | `otlp` hoặc `none` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | — | Endpoint của bộ thu thập OTLP |

### Cluster Groups (Nhóm cụm)

Nếu bạn chạy nhiều cluster logic trên cùng một hạ tầng Redis, hãy dùng group name làm namespace:

```env
CAMEL_DASHBOARD_CLUSTER_GROUP=payment
CAMEL_DASHBOARD_CLUSTER_STREAM_KEY=payment:cluster:event:stream
CAMEL_DASHBOARD_CLUSTER_CHANNEL=payment:cluster:event:channel
```

---

## 🏗️ Kiến trúc

```
┌──────────────────────────────────────────────────────┐
│                   Camel Dashboard                    │
├────────────────────┬─────────────────────────────────┤ 
│   Vue 3 Frontend   │      Spring Boot Backend        │
│   (PrimeVue +      │   ┌──────────┐ ┌────────────┐   │
│    TailwindCSS)    │   │  Camel   │ │ MCP Server │   │
│                    │   │ Context  │ │ (/mcp)     │   │
│  ┌──────────────┐  │   └──────────┘ └────────────┘   │
│  │ Routes View  │  │   ┌──────────────────────────┐  │
│  │ Services     │  │   │   Route Version Service  │  │
│  │ Upload       │◄─┤──►│   (YAML storage + index) │  │
│  │ Versions     │  │   └──────────────────────────┘  │
│  │ Deploy       │  │   ┌──────────────────────────┐  │
│  │ Cluster      │  │   │  Cluster Node Service    │  │
│  │ Beans        │  │   │  (Standalone / Redis)    │  │
│  │ Properties   │  │   └──────────────────────────┘  │
│  └──────────────┘  │                                 │
└────────────────────┴─────────────────────────────────┘
         │                        │
    REST API /api/*         ┌─────┴──────┐
                            │ PostgreSQL │
                            │ + Flyway   │
                            └─────┬──────┘
                                  │ (tùy chọn)
                            ┌─────┴──────┘
                            │   Redis    │
                            │ (cluster)  │
                            └────────────┘
```

---

## 🛠️ Công nghệ sử dụng

| Lớp | Công nghệ |
|---|---|
| Backend | Spring Boot 4, Apache Camel 4 |
| Frontend | Vue 3, PrimeVue, TailwindCSS, Vite |
| Cơ sở dữ liệu | PostgreSQL 16 + Flyway migrations |
| Cluster | Redis 7 (Streams + Pub/Sub) |
| AI / MCP | Spring AI MCP Server (Streamable HTTP) |
| Khả năng quan sát | Micrometer, OpenTelemetry |
| Build | Maven (thin JAR + libs/) |
| Container | Docker + Docker Compose |

## 🚢 Triển khai Production

### Build dự án

```powershell
# Build frontend và nhúng vào JAR
cd frontend && pnpm build

# Đóng gói thin JAR (không chứa các dependency dev)
.\mvnw.cmd clean package -DskipTests
```
### Chạy ứng dụng

```powershell
java -Dloader.path=libs -jar target/camel-dashboard-xxx.jar
```

> **Lưu ý**: Thư mục `libs/` (mặc định là `./libs` trong thư mục làm việc) phải được chỉ định qua `-Dloader.path` để tải các driver và component động hoạt động chính xác.

### Docker

```bash
docker build -t camel-dashboard .
docker run -p 8080:8080 --env-file .env camel-dashboard
```
---

## 🤝 Đóng góp ý kiến

Chúng tôi rất hoan nghênh các đóng góp cho dự án! Vui lòng làm theo các bước:

1. Fork repository này
2. Tạo nhánh feature mới: `git checkout -b feature/amazing-feature`
3. Commit thay đổi: `git commit -m 'feat: add amazing feature'`
4. Push lên nhánh vừa tạo: `git push origin feature/amazing-feature`
5. Tạo một Pull Request mới

---

## 📝 Bản quyền (License)
- Mã nguồn của dự án này được phát hành dưới giấy phép Apache License, Phiên bản 2.0 — xem file [LICENSE](LICENSE) để biết thêm chi tiết.
- PrimeVue được phát hành dưới giấy phép MIT.
- Apache Camel được phát triển bởi Apache Software Foundation và được phát hành dưới giấy phép [Apache License, Phiên bản 2.0](https://www.apache.org/licenses/LICENSE-2.0).

## Tuyên bố từ chối trách nhiệm (Disclaimer)
Sản phẩm này mang tính độc lập và không liên kết, không được xác nhận hoặc tài trợ bởi Apache Software Foundation. "Apache", "Camel", và "Apache Camel" là các thương hiệu của Apache Software Foundation.
