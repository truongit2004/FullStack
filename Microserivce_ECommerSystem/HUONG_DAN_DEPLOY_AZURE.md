# HƯỚNG DẪN DEPLOY BACKEND LÊN AZURE

> Tài liệu viết riêng cho hệ thống này (`Microserivce_ECommerSystem/`), dựa trên `docker-compose.yml`, các `Dockerfile` và file cấu hình hiện có. Ngày viết: 2026-10-11.
>
> **Trạng thái:** đây là tài liệu hướng dẫn, **chưa deploy thật lên Azure**. File `docker-compose.azure.yml` ở mục 6 đã được kiểm tra hợp lệ khi ghép với `docker-compose.yml` hiện tại (`docker compose config`). Các đoạn sửa code Java ở mục 3 chưa được biên dịch thử. Giá và tên menu của Azure có thể thay đổi — kiểm tra lại trên trang chính thức.

## Mục lục

1. [Chọn phương án](#1-chọn-phương-án)
2. [Kiến trúc sau khi deploy](#2-kiến-trúc-sau-khi-deploy)
3. [Sửa code trước khi deploy (bắt buộc)](#3-sửa-code-trước-khi-deploy-bắt-buộc)
4. [Tạo tài nguyên trên Azure](#4-tạo-tài-nguyên-trên-azure)
5. [Cài đặt máy ảo](#5-cài-đặt-máy-ảo)
6. [Các file cấu hình triển khai](#6-các-file-cấu-hình-triển-khai)
7. [Build và chạy](#7-build-và-chạy)
8. [Kiểm tra sau khi chạy](#8-kiểm-tra-sau-khi-chạy)
9. [Nối với Frontend, VNPay, Google](#9-nối-với-frontend-vnpay-google)
10. [Vận hành](#10-vận-hành)
11. [Lỗi thường gặp](#11-lỗi-thường-gặp)
12. [Bảo mật](#12-bảo-mật)
13. [Hướng nâng cấp](#13-hướng-nâng-cấp)
14. [Danh sách kiểm tra cuối](#14-danh-sách-kiểm-tra-cuối)

---

## 1. Chọn phương án

Backend gồm khoảng 30 container: 15 ứng dụng Spring Boot (13 service + Eureka + Gateway), 8 MySQL, MongoDB, Redis, Elasticsearch, Kafka, Zookeeper.

| Phương án | Mô tả | Ưu điểm | Nhược điểm |
|---|---|---|---|
| **A. Một máy ảo + Docker Compose** *(chọn)* | Thuê 1 VM Linux, chạy lại đúng `docker-compose.yml` đang có | Tái dùng gần như toàn bộ cấu hình hiện tại; một hóa đơn; dễ hiểu, dễ sửa | Một điểm hỏng duy nhất; tự lo sao lưu, cập nhật; không tự co giãn |
| B. Azure Container Apps + dịch vụ quản lý | Mỗi service một Container App; MySQL Flexible Server, Azure Cache for Redis, Event Hubs (Kafka), Cosmos DB (Mongo) | Tự co giãn, Azure lo sao lưu và vá lỗi | Phải cấu hình ~15 app và 5 loại dịch vụ dữ liệu; đắt hơn nhiều; phải bỏ Eureka hoặc cấu hình lại |
| C. AKS (Kubernetes) | Viết manifest/Helm cho toàn hệ thống | Chuẩn công nghiệp cho microservice | Nặng nhất về công sức và chi phí |

Tài liệu này hướng dẫn **phương án A**: phù hợp đồ án/demo, và là bước đệm hợp lý trước khi tách dần sang dịch vụ quản lý (mục 13).

**Máy ảo đề xuất:** `Standard_B4ms` (4 vCPU, 16 GB RAM), ổ đĩa 64 GB, Ubuntu Server 24.04 LTS, vùng **Southeast Asia** (Singapore).

Ước lượng RAM khi chạy đủ hệ thống (đã giới hạn bộ nhớ JVM ở mục 6):

| Thành phần | Số lượng | RAM ước tính |
|---|---|---|
| Ứng dụng Spring Boot | 15 | ~6 GB |
| MySQL | 8 | ~3,2 GB |
| Elasticsearch | 1 | ~1,2 GB |
| Kafka + Zookeeper | 2 | ~1,2 GB |
| MongoDB, Redis, Caddy | 3 | ~0,5 GB |
| **Tổng** | | **~12 GB** |

Máy 8 GB không đủ. Chi phí `B4ms` chạy 24/7 vào khoảng hơn 100 USD/tháng (tra chính xác tại [Azure Pricing Calculator](https://azure.microsoft.com/pricing/calculator/)). Gói **Azure for Students** cho 100 USD tín dụng, nên hãy **tắt máy (deallocate) khi không dùng** — xem mục 10.5.

---

## 2. Kiến trúc sau khi deploy

```
Internet
   │  HTTPS 443 (và 80 để cấp chứng chỉ)
   ▼
┌──────────────────────── Azure VM (Ubuntu, Docker) ────────────────────────┐
│  NSG (tường lửa Azure): chỉ mở 22, 80, 443                                 │
│                                                                            │
│  [Caddy :80/:443] ── tự cấp chứng chỉ HTTPS ──▶ [API Gateway :8085]        │
│                                                       │                    │
│                              mạng Docker nội bộ "ecommerce-network"        │
│                                                       ▼                    │
│   Eureka · Auth · User · Product · Inventory · Order · Cart · Payment      │
│   Shipping · Review · Search · Notification · Chat · Point                 │
│                                                       │                    │
│   8 MySQL · MongoDB · Redis · Elasticsearch · Kafka · Zookeeper            │
│   (dữ liệu nằm trong Docker volume trên ổ đĩa của VM)                      │
└────────────────────────────────────────────────────────────────────────────┘
```

- Người dùng chỉ đi vào qua **một địa chỉ HTTPS**: `https://<tên-dns>.southeastasia.cloudapp.azure.com`.
- Các cổng 8082–8095, 3306x, 6379, 9200, 27017, 9092, 8761 vẫn được Docker mở trên VM (do file compose gốc), nhưng **NSG của Azure chặn hết từ Internet**.
- Không chạy trên Azure: `frontend`, `ngrok-be`, `ngrok-fe`, `kibana`.

---

## 3. Sửa code trước khi deploy (bắt buộc)

Một số chỗ trong code đang ghi cứng `localhost`. Trong Docker, mỗi service là một container riêng nên `localhost` trỏ về chính nó → các lời gọi này hỏng. **Lỗi này xảy ra với cả `docker-compose.yml` hiện tại ở máy cá nhân**, không riêng gì Azure.

Nguyên tắc sửa: đổi thành biến môi trường **có giá trị mặc định là `localhost`**, nên chạy bằng IDE như cũ vẫn không ảnh hưởng.

### 3.1 Auth Service — 4 Feign client (nghiêm trọng nhất: không sửa thì không đăng nhập được)

Thư mục: `Auth Service/Auth-Service/src/main/java/com/example/Auth/Service/client/`

| File | Hiện tại | Sửa thành |
|---|---|---|
| `UserClient.java` | `url = "http://localhost:8088"` | `url = "${USER_SERVICE_URL:http://localhost:8088}"` |
| `OrderClient.java` | `url = "http://localhost:8084"` | `url = "${ORDER_SERVICE_URL:http://localhost:8084}"` |
| `ProductClient.java` | `url = "http://localhost:8082"` | `url = "${PRODUCT_SERVICE_URL:http://localhost:8082}"` |
| `NotificationClient.java` | `url = "http://localhost:8090"` | `url = "${NOTIFICATION_SERVICE_URL:http://localhost:8090}"` |

### 3.2 User Service — trang hồ sơ đầy đủ (`/api/users/{id}/profile-full`)

File: `User Service/User-Service/src/main/java/com/example/User/Service/service/impl/UserIntegrationServiceImpl.java`

Thay 2 hằng số:

```java
    private static final String ORDER_SERVICE_URL = "http://localhost:8084/api/orders";
    private static final String PRODUCT_SERVICE_URL = "http://localhost:8082/api/products";
```

bằng 2 field được inject (thêm `import org.springframework.beans.factory.annotation.Value;`):

```java
    @Value("${ORDER_SERVICE_URL:http://localhost:8084}/api/orders")
    private String orderServiceUrl;

    @Value("${PRODUCT_SERVICE_URL:http://localhost:8082}/api/products")
    private String productServiceUrl;
```

rồi đổi mọi chỗ dùng `ORDER_SERVICE_URL` → `orderServiceUrl`, `PRODUCT_SERVICE_URL` → `productServiceUrl` trong file. Lớp đang dùng `@RequiredArgsConstructor`; field có `@Value` **không được** khai báo `final`.

### 3.3 Search Service — đồng bộ thủ công (`/api/search/sync`)

File: `SearchService/SearchService/src/main/java/com/example/SearchService/controller/SearchController.java`

Thêm field vào controller:

```java
    @Value("${PRODUCT_SERVICE_URL:http://localhost:8082}")
    private String productServiceUrl;
```

và sửa dòng gọi:

```java
            ProductDocument[] products = restTemplate.getForObject(productServiceUrl + "/api/products/all", ProductDocument[].class);
```

### 3.4 Payment Service — chuyển hướng về FE sau khi thanh toán VNPay

File: `PaymentService/PaymentService/src/main/java/com/example/PaymentService/controller/PaymentController.java`

Thêm field:

```java
    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;
```

và sửa 2 dòng redirect:

```java
                    .location(java.net.URI.create(frontendUrl + "/success?orderId=" + orderId))
```

```java
                    .location(java.net.URI.create(frontendUrl + "/orders?error=payment_failed"))
```

### 3.5 API Gateway — CORS

File: `API Gateway/API-Gateway/src/main/resources/application.yaml`

Cấu hình hiện tại dùng `allowedOrigins: "*"` cùng `allowCredentials: true`. Spring không chấp nhận tổ hợp này cho request khác origin (báo lỗi "allowedOrigins cannot contain the special value \*"). Khi FE nằm ở tên miền khác backend, đổi thành:

```yaml
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOriginPatterns: "*"
            allowedMethods: "*"
            allowedHeaders: "*"
            allowCredentials: true
```

Khi đã có tên miền FE cố định, nên thay `"*"` bằng đúng địa chỉ FE (ví dụ `https://shop.example.com`).

### 3.6 API Gateway — giới hạn tần suất theo IP

File: `API Gateway/API-Gateway/src/main/java/com/example/API/Gateway/config/RateLimiterConfig.java`

Route `GET /api/products/**`, `/api/categories/**` đang giới hạn **1 request/giây theo IP nguồn**. Sau khi đặt Caddy phía trước, Gateway thấy mọi request đều đến từ IP của Caddy → **tất cả người dùng dùng chung một hạn mức**, trang sản phẩm sẽ liên tục nhận lỗi 429.

Sửa `userKeyResolver` để lấy IP thật từ header `X-Forwarded-For` (Caddy tự gắn):

```java
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // Phần tử đầu tiên là IP của trình duyệt; Caddy ghi đè header này nên tin được
                return Mono.just(forwarded.split(",")[0].trim());
            }
            return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
        };
    }
```

Cũng nên nâng `replenishRate` / `burstCapacity` trong `application.yaml` (ví dụ 10 / 20), vì một lần mở trang sản phẩm đã gọi nhiều API cùng lúc.

### 3.7 Những chỗ ghi cứng khác — không cần sửa code

Các chỗ sau cũng ghi cứng `localhost` nhưng ghi đè được bằng biến môi trường; đã xử lý sẵn trong `docker-compose.azure.yml` (mục 6):

| Service | Vấn đề | Biến ghi đè |
|---|---|---|
| Inventory, Order | Địa chỉ Eureka ghi cứng `localhost:8761` | `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` |
| Order | `product.service.url: http://localhost:8082` | `PRODUCT_SERVICE_URL` |
| Review | Kafka ghép kiểu `${KAFKA_HOST}:9092`, khác các service còn lại | `SPRING_KAFKA_BOOTSTRAP_SERVERS` |
| Payment | `returnUrl` của VNPay trỏ `localhost` | `VNP_RETURNURL` |

### 3.8 Sau khi sửa

1. Build thử từng service đã sửa (`mvn compile`) và chạy thử ở máy cá nhân.
2. Commit và push lên GitHub — VM sẽ lấy code từ đó.

---

## 4. Tạo tài nguyên trên Azure

### 4.1 Chuẩn bị

- Tài khoản Azure có subscription đang hoạt động (sinh viên: đăng ký [Azure for Students](https://azure.microsoft.com/free/students/) bằng email trường, không cần thẻ).
- Máy cá nhân có `ssh` (Windows 10/11 có sẵn trong PowerShell).

### 4.2 Cách 1 — Azure Portal (giao diện web)

Vào <https://portal.azure.com> → **Virtual machines → Create → Azure virtual machine**.

**Tab Basics**

| Mục | Giá trị |
|---|---|
| Resource group | Tạo mới: `rg-ecommerce` |
| Virtual machine name | `vm-ecommerce` |
| Region | `(Asia Pacific) Southeast Asia` |
| Image | `Ubuntu Server 24.04 LTS - x64 Gen2` |
| Size | `Standard_B4ms` (bấm *See all sizes* nếu không thấy) |
| Authentication type | `SSH public key` |
| Username | `azureuser` |
| SSH public key source | `Generate new key pair`, tên `vm-ecommerce_key` |
| Public inbound ports | `Allow selected ports` → chọn `SSH (22)`, `HTTP (80)`, `HTTPS (443)` |

**Tab Disks:** OS disk size `64 GiB`, loại `Standard SSD`.

**Tab Networking:** giữ mặc định (tạo mới virtual network và Public IP).

**Tab Management:** bật **Auto-shutdown** nếu muốn máy tự tắt mỗi tối (ví dụ 23:00, múi giờ `SE Asia Standard Time`).

Bấm **Review + create → Create → Download private key and create resource**. Lưu file `.pem` cẩn thận — **chỉ tải được một lần**.

**Sau khi tạo xong:**

1. Mở VM → trang **Overview** → dòng **DNS name** → *Not configured* → đặt **DNS name label**, ví dụ `ecommerce-truong` → **Save**. Tên miền của bạn sẽ là `ecommerce-truong.southeastasia.cloudapp.azure.com`.
2. Trong cùng trang cấu hình IP, đổi **IP address assignment** sang **Static** để IP không đổi sau mỗi lần tắt/bật máy.
3. Vào **Networking → Network settings**, sửa rule SSH: đổi **Source** từ `Any` thành `My IP address` để chỉ máy bạn SSH được.

### 4.3 Cách 2 — Azure CLI (dòng lệnh)

Cài [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli) rồi chạy trong PowerShell (đổi `ecommerce-truong` thành tên của bạn, tên này phải duy nhất trong vùng):

```powershell
az login

az group create --name rg-ecommerce --location southeastasia

az vm create `
  --resource-group rg-ecommerce `
  --name vm-ecommerce `
  --image Ubuntu2404 `
  --size Standard_B4ms `
  --os-disk-size-gb 64 `
  --storage-sku StandardSSD_LRS `
  --admin-username azureuser `
  --generate-ssh-keys `
  --public-ip-sku Standard `
  --public-ip-address-dns-name ecommerce-truong

az vm open-port --resource-group rg-ecommerce --name vm-ecommerce --port 80 --priority 1010
az vm open-port --resource-group rg-ecommerce --name vm-ecommerce --port 443 --priority 1020
```

`--generate-ssh-keys` lưu khóa tại `C:\Users\<bạn>\.ssh\id_rsa`. Cổng 22 được mở sẵn khi tạo VM.

### 4.4 Kết nối SSH

```powershell
# Cách 1 (Portal): dùng file .pem đã tải
ssh -i C:\duong\dan\vm-ecommerce_key.pem azureuser@ecommerce-truong.southeastasia.cloudapp.azure.com

# Cách 2 (CLI): khóa mặc định
ssh azureuser@ecommerce-truong.southeastasia.cloudapp.azure.com
```

Nếu Windows báo quyền của file `.pem` quá rộng: chuột phải file → Properties → Security → Advanced → tắt kế thừa và chỉ để lại tài khoản của bạn.

---

## 5. Cài đặt máy ảo

Tất cả lệnh từ đây chạy **trên VM** (sau khi SSH vào).

### 5.1 Cập nhật và cài Docker

```bash
sudo apt-get update && sudo apt-get upgrade -y

# Cài Docker Engine + plugin Compose bằng script chính thức
curl -fsSL https://get.docker.com | sudo sh

# Cho phép user hiện tại chạy docker không cần sudo
sudo usermod -aG docker $USER
exit
```

SSH vào lại để quyền nhóm có hiệu lực, rồi kiểm tra:

```bash
docker --version
docker compose version
```

### 5.2 Tạo vùng nhớ đệm (swap) 4 GB

Phòng trường hợp RAM lên đỉnh lúc build hoặc lúc mọi service khởi động cùng lúc:

```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

### 5.3 Tham số nhân cho Elasticsearch

Elasticsearch yêu cầu `vm.max_map_count` tối thiểu 262144:

```bash
echo 'vm.max_map_count=262144' | sudo tee /etc/sysctl.d/99-elasticsearch.conf
sudo sysctl --system
```

### 5.4 Lấy mã nguồn

```bash
cd ~
git clone https://github.com/truongit2004/FullStack.git
cd FullStack/Microserivce_ECommerSystem
```

Nếu repo để riêng tư: tạo **Personal Access Token** trên GitHub (quyền chỉ đọc repo) và nhập khi `git clone` hỏi mật khẩu. **Không** nhúng token vào URL và không dùng lại token đang dùng ở máy cá nhân.

---

## 6. Các file cấu hình triển khai

Tạo 3 file trong thư mục `~/FullStack/Microserivce_ECommerSystem/` trên VM. Nên tạo `docker-compose.azure.yml` và `Caddyfile` ở máy cá nhân rồi commit lên git; **riêng `.env` chỉ tạo trên VM, không commit**.

### 6.1 `.env` — giá trị bí mật và theo môi trường

```bash
nano .env
```

```env
# Tên miền của VM (DNS name label + vùng), KHÔNG có https://
DOMAIN=ecommerce-truong.southeastasia.cloudapp.azure.com

# Địa chỉ FE sau khi deploy (dùng để VNPay chuyển hướng về), KHÔNG có / ở cuối
FRONTEND_URL=https://dia-chi-frontend-cua-ban

# Mật khẩu root dùng chung cho 8 MySQL — tự đặt, dài, không dùng ký tự $ và dấu cách
MYSQL_ROOT_PASSWORD=DoiMatKhauNay_2026

# Giới hạn bộ nhớ cho mỗi ứng dụng Spring Boot
JAVA_OPTS_SMALL=-Xms128m -Xmx320m -XX:+UseSerialGC

# VNPay (lấy trong tài khoản sandbox/merchant của bạn)
VNP_TMNCODE=ma_tmn_cua_ban
VNP_HASHSECRET=chuoi_bi_mat_cua_ban

# Gmail gửi thông báo: địa chỉ + App Password (không phải mật khẩu đăng nhập)
SPRING_MAIL_USERNAME=email_cua_ban@gmail.com
SPRING_MAIL_PASSWORD=app_password_16_ky_tu

# File compose gốc có tham chiếu biến này (service ngrok không chạy trên Azure)
NGROK_AUTHTOKEN=unused
```

```bash
chmod 600 .env
```

> Repo hiện đang theo dõi file `.env` bằng git (xem mục 12). Khi `git pull`, file `.env` trên VM có thể bị xung đột — xử lý dứt điểm bằng cách gỡ `.env` khỏi git trước.

### 6.2 `docker-compose.azure.yml` — lớp cấu hình bổ sung cho Azure

File này **không thay thế** `docker-compose.yml` mà ghép thêm lên trên: thêm Caddy, thêm volume giữ dữ liệu, đổi mật khẩu DB, giới hạn RAM, tự khởi động lại, và ghi đè các địa chỉ `localhost`.

```yaml
# Lớp cấu hình bổ sung khi chạy trên Azure VM. Dùng kèm file gốc:
#   docker compose -f docker-compose.yml -f docker-compose.azure.yml ...
# File gốc docker-compose.yml giữ nguyên để vẫn chạy được ở máy cá nhân.

x-restart: &restart
  restart: unless-stopped

services:
  # ── Cổng vào HTTPS ─────────────────────────────────────────────────────────
  caddy:
    image: caddy:2
    container_name: caddy
    <<: *restart
    ports:
      - "80:80"
      - "443:443"
    environment:
      - DOMAIN=${DOMAIN}
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
      - caddy-config:/config
    depends_on:
      - api-gateway
    networks:
      - ecommerce-network

  # ── Cơ sở dữ liệu: thêm volume để không mất dữ liệu, đổi mật khẩu root ─────
  user-db:
    <<: *restart
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    volumes:
      - user-db-data:/var/lib/mysql
  product-db:
    <<: *restart
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    volumes:
      - product-db-data:/var/lib/mysql
  order-db:
    <<: *restart
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    volumes:
      - order-db-data:/var/lib/mysql
  inventory-db:
    <<: *restart
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    volumes:
      - inventory-db-data:/var/lib/mysql
  payment-db:
    <<: *restart
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    volumes:
      - payment-db-data:/var/lib/mysql
  review-db:
    <<: *restart
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    volumes:
      - review-db-data:/var/lib/mysql
  shipping-db:
    <<: *restart
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    volumes:
      - shipping-db-data:/var/lib/mysql
  point-db:
    <<: *restart
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    volumes:
      - point-db-data:/var/lib/mysql
  coupon-db:
    <<: *restart
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    volumes:
      - coupon-db-data:/var/lib/mysql
  chat-db:
    <<: *restart
    volumes:
      - chat-db-data:/data/db
  redis-cart:
    <<: *restart
  elasticsearch:
    <<: *restart
    volumes:
      - es-data:/usr/share/elasticsearch/data
  zookeeper:
    image: confluentinc/cp-zookeeper:7.3.0
    <<: *restart
  kafka:
    <<: *restart
    environment:
      KAFKA_HEAP_OPTS: "-Xms256m -Xmx512m"

  # ── Hạ tầng ────────────────────────────────────────────────────────────────
  eureka-server:
    <<: *restart
    environment:
      - JAVA_TOOL_OPTIONS=${JAVA_OPTS_SMALL}
  api-gateway:
    <<: *restart
    environment:
      - JAVA_TOOL_OPTIONS=${JAVA_OPTS_SMALL}

  # ── Các service nghiệp vụ ──────────────────────────────────────────────────
  user-service:
    <<: *restart
    environment:
      - JAVA_TOOL_OPTIONS=${JAVA_OPTS_SMALL}
      - SPRING_DATASOURCE_PASSWORD=${MYSQL_ROOT_PASSWORD}
      - ORDER_SERVICE_URL=http://order-service:8084
      - PRODUCT_SERVICE_URL=http://product-service:8082
  product-service:
    <<: *restart
    environment:
      - JAVA_TOOL_OPTIONS=${JAVA_OPTS_SMALL}
      - SPRING_DATASOURCE_PASSWORD=${MYSQL_ROOT_PASSWORD}
  inventory-service:
    <<: *restart
    environment:
      - JAVA_TOOL_OPTIONS=${JAVA_OPTS_SMALL}
      - SPRING_DATASOURCE_PASSWORD=${MYSQL_ROOT_PASSWORD}
      # application.yml của service này ghi cứng localhost cho Eureka -> ghi đè tại đây
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka/
  order-service:
    <<: *restart
    environment:
      - JAVA_TOOL_OPTIONS=${JAVA_OPTS_SMALL}
      - SPRING_DATASOURCE_PASSWORD=${MYSQL_ROOT_PASSWORD}
      # application.yaml ghi cứng localhost cho Eureka và Product Service -> ghi đè tại đây
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka/
      - PRODUCT_SERVICE_URL=http://product-service:8082
  cart-service:
    <<: *restart
    environment:
      - JAVA_TOOL_OPTIONS=${JAVA_OPTS_SMALL}
  auth-service:
    <<: *restart
    environment:
      - JAVA_TOOL_OPTIONS=${JAVA_OPTS_SMALL}
      - REDIS_HOST=redis-cart
      - USER_SERVICE_URL=http://user-service:8088
      - ORDER_SERVICE_URL=http://order-service:8084
      - PRODUCT_SERVICE_URL=http://product-service:8082
      - NOTIFICATION_SERVICE_URL=http://notification-service:8090
  payment-service:
    <<: *restart
    environment:
      - JAVA_TOOL_OPTIONS=${JAVA_OPTS_SMALL}
      - SPRING_DATASOURCE_PASSWORD=${MYSQL_ROOT_PASSWORD}
      - VNP_TMNCODE=${VNP_TMNCODE}
      - VNP_HASHSECRET=${VNP_HASHSECRET}
      - VNP_RETURNURL=https://${DOMAIN}/api/payment/vnpay-callback
      - FRONTEND_URL=${FRONTEND_URL}
  notification-service:
    <<: *restart
    environment:
      - JAVA_TOOL_OPTIONS=${JAVA_OPTS_SMALL}
      - SPRING_MAIL_USERNAME=${SPRING_MAIL_USERNAME}
      - SPRING_MAIL_PASSWORD=${SPRING_MAIL_PASSWORD}
  chat-service:
    <<: *restart
    environment:
      - JAVA_TOOL_OPTIONS=${JAVA_OPTS_SMALL}
  review-service:
    <<: *restart
    environment:
      - JAVA_TOOL_OPTIONS=${JAVA_OPTS_SMALL}
      - SPRING_DATASOURCE_PASSWORD=${MYSQL_ROOT_PASSWORD}
      # application.yml ghép "${KAFKA_HOST}:9092" (khác các service còn lại) -> ghi đè thẳng
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092
  shipping-service:
    <<: *restart
    environment:
      - JAVA_TOOL_OPTIONS=${JAVA_OPTS_SMALL}
      - SPRING_DATASOURCE_PASSWORD=${MYSQL_ROOT_PASSWORD}
  search-service:
    <<: *restart
    environment:
      - JAVA_TOOL_OPTIONS=${JAVA_OPTS_SMALL}
      - PRODUCT_SERVICE_URL=http://product-service:8082
  point-service:
    <<: *restart
    environment:
      - JAVA_TOOL_OPTIONS=${JAVA_OPTS_SMALL}
      - SPRING_DATASOURCE_PASSWORD=${MYSQL_ROOT_PASSWORD}
  coupon-service:
    <<: *restart
    environment:
      - JAVA_TOOL_OPTIONS=${JAVA_OPTS_SMALL}
      - SPRING_DATASOURCE_PASSWORD=${MYSQL_ROOT_PASSWORD}

volumes:
  caddy-data:
  caddy-config:
  user-db-data:
  product-db-data:
  order-db-data:
  inventory-db-data:
  payment-db-data:
  review-db-data:
  shipping-db-data:
  point-db-data:
  coupon-db-data:
  chat-db-data:
  es-data:
```

### 6.3 `Caddyfile` — cổng HTTPS

```
{$DOMAIN} {
	reverse_proxy api-gateway:8085
}
```

Caddy tự xin và tự gia hạn chứng chỉ Let's Encrypt cho tên miền trong `DOMAIN`. Điều kiện: tên miền đã trỏ về IP của VM và cổng 80, 443 đã mở. WebSocket của Chat (`/ws`) đi qua được mà không cần cấu hình thêm.

### 6.4 Lệnh rút gọn

Mọi lệnh compose đều phải kèm 2 file. Tạo bí danh cho gọn:

```bash
echo "alias dc='docker compose -f docker-compose.yml -f docker-compose.azure.yml'" >> ~/.bashrc
source ~/.bashrc
```

Từ đây tài liệu dùng `dc` thay cho lệnh đầy đủ. Luôn chạy trong thư mục `~/FullStack/Microserivce_ECommerSystem`.

---

## 7. Build và chạy

### 7.1 Kiểm tra cấu hình

```bash
dc config --quiet && echo "Cau hinh hop le"
```

### 7.2 Build image — lần lượt từng service

Mỗi `Dockerfile` chạy Maven bên trong container. Build 16 service **cùng lúc** rất dễ hết RAM, nên build tuần tự:

```bash
SERVICES="eureka-server api-gateway user-service auth-service product-service inventory-service order-service cart-service payment-service notification-service chat-service review-service shipping-service search-service point-service coupon-service"

for s in $SERVICES; do
  echo "===== Build $s ====="
  dc build "$s" || { echo "LOI khi build $s"; break; }
done
```

Lần đầu mất khá lâu (mỗi service phải tải thư viện Maven; tổng có thể 30–60 phút). Các lần sau nhanh hơn nhờ cache của Docker.

### 7.3 Khởi động theo từng lớp

Khởi động theo thứ tự giúp tránh dồn tải lúc đầu:

```bash
# 1. Dữ liệu và message broker
dc up -d user-db product-db order-db inventory-db payment-db review-db shipping-db point-db coupon-db chat-db redis-cart elasticsearch zookeeper kafka

# Chờ đến khi các DB báo healthy (lặp lại lệnh này để xem)
dc ps

# 2. Eureka
dc up -d eureka-server
sleep 40

# 3. Các service nghiệp vụ
dc up -d user-service auth-service product-service inventory-service order-service cart-service payment-service notification-service chat-service review-service shipping-service search-service point-service coupon-service

# 4. Gateway và cổng HTTPS
dc up -d api-gateway caddy
```

Không chạy `dc up -d` trần (không kèm tên service) vì lệnh đó sẽ bật cả `frontend`, `kibana` và 2 container `ngrok`.

Sau khi mọi container lên, cần thêm **2–3 phút** để các service đăng ký với Eureka và Gateway lấy được danh sách.

---

## 8. Kiểm tra sau khi chạy

### 8.1 Trên VM

```bash
# Mọi container phải ở trạng thái Up (các DB kèm chữ healthy)
dc ps

# RAM từng container và tổng RAM còn trống
docker stats --no-stream
free -h

# Danh sách service đã đăng ký Eureka (phải thấy đủ 14 tên)
curl -s http://localhost:8761/eureka/apps | grep -o '<name>[^<]*</name>' | sort -u

# Gọi thử qua Gateway ngay trong VM
curl -i http://localhost:8085/api/products/filter
```

### 8.2 Từ máy cá nhân

```bash
# Chứng chỉ HTTPS và API công khai
curl -i https://ecommerce-truong.southeastasia.cloudapp.azure.com/api/products/filter

# Đăng ký và đăng nhập
curl -i -X POST https://ecommerce-truong.southeastasia.cloudapp.azure.com/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"azuretest","email":"azuretest@example.com","password":"Test@12345"}'

curl -i -X POST https://ecommerce-truong.southeastasia.cloudapp.azure.com/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"azuretest","password":"Test@12345"}'
```

Đăng nhập trả về `token` nghĩa là chuỗi Caddy → Gateway → Auth → User → MySQL đã thông.

### 8.3 Xem trang quản trị nội bộ mà không mở cổng ra Internet

Dùng đường hầm SSH từ máy cá nhân:

```powershell
ssh -L 8761:localhost:8761 azureuser@ecommerce-truong.southeastasia.cloudapp.azure.com
```

Giữ cửa sổ đó mở, rồi vào `http://localhost:8761` trên trình duyệt để xem bảng điều khiển Eureka. Làm tương tự với cổng 9200 (Elasticsearch) hoặc 3306x (MySQL) khi cần.

---

## 9. Nối với Frontend, VNPay, Google

### 9.1 Frontend

Tài liệu này chỉ deploy backend. Dù FE đặt ở đâu, cần:

1. Đặt `VITE_API_URL=https://ecommerce-truong.southeastasia.cloudapp.azure.com` **trước khi** `npm run build` (Vite nhúng giá trị này vào bản build).
2. Điền địa chỉ FE vào `FRONTEND_URL` trong `.env` trên VM, rồi `dc up -d payment-service`.
3. Lưu ý trong code FE: ảnh sản phẩm đang dùng đường dẫn tương đối `/api/products/files/...` (nhờ proxy của Vite lúc dev). Khi FE và BE khác tên miền, các đường dẫn này phải được ghép thêm `VITE_API_URL`, hoặc nơi host FE phải chuyển tiếp `/api/*` về backend.

Nơi host FE gợi ý trên Azure: **Azure Static Web Apps** (gói miễn phí, tự build từ GitHub, có sẵn HTTPS).

### 9.2 VNPay

- `VNP_RETURNURL` đã được đặt tự động thành `https://<DOMAIN>/api/payment/vnpay-callback`.
- Sau khi thanh toán, Payment Service chuyển người dùng về `FRONTEND_URL` (cần đã sửa code ở mục 3.4).
- Với tài khoản VNPay thật (không phải sandbox): khai báo URL trả về/IPN mới trong trang quản trị merchant.

### 9.3 Đăng nhập Google (nếu đã làm theo `HUONG_DAN_DANG_NHAP_GOOGLE.md`)

- Thêm địa chỉ FE thật vào **Authorized JavaScript origins** trên Google Cloud Console.
- Thêm `GOOGLE_CLIENT_ID=...` vào `.env` và dòng `- GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}` vào khối `auth-service` trong `docker-compose.azure.yml`.

---

## 10. Vận hành

### 10.1 Xem log

```bash
dc logs -f --tail=100 order-service        # theo dõi 1 service
dc logs --since=10m auth-service user-service
dc logs --tail=50 caddy                     # lỗi chứng chỉ HTTPS xem ở đây
```

### 10.2 Cập nhật code

```bash
cd ~/FullStack/Microserivce_ECommerSystem
git pull

# Chỉ build và khởi động lại service có thay đổi, ví dụ order-service
dc build order-service
dc up -d order-service
```

### 10.3 Khởi động lại, dừng

```bash
dc restart order-service     # khởi động lại 1 service
dc stop                      # dừng tất cả, GIỮ container và dữ liệu
dc down                      # xóa container, GIỮ volume dữ liệu
```

**Không bao giờ chạy `dc down -v`** trừ khi muốn xóa sạch dữ liệu: cờ `-v` xóa toàn bộ volume (mọi database).

### 10.4 Sao lưu dữ liệu

Sao lưu toàn bộ 8 MySQL và MongoDB ra thư mục `~/backup`:

```bash
mkdir -p ~/backup && cd ~/FullStack/Microserivce_ECommerSystem
set -a; source .env; set +a
STAMP=$(date +%Y%m%d-%H%M)

for db in user-db product-db order-db inventory-db payment-db review-db shipping-db point-db coupon-db; do
  docker exec "$db" mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --all-databases --single-transaction \
    | gzip > ~/backup/${db}-${STAMP}.sql.gz
done

docker exec chat-db mongodump --archive --gzip > ~/backup/chat-db-${STAMP}.archive.gz

# Ảnh sản phẩm đã upload
tar czf ~/backup/uploads-${STAMP}.tar.gz uploads
```

Tải bản sao lưu về máy cá nhân (chạy ở máy cá nhân):

```powershell
scp -r azureuser@ecommerce-truong.southeastasia.cloudapp.azure.com:~/backup C:\backup-azure
```

Có thể bật thêm **Azure Backup** cho VM trong Portal (VM → Backup) để chụp toàn bộ ổ đĩa theo lịch; dịch vụ này tính phí riêng.

### 10.5 Tắt máy để tiết kiệm tín dụng

Tắt trong Portal bằng nút **Stop**, hoặc:

```powershell
az vm deallocate --resource-group rg-ecommerce --name vm-ecommerce   # tắt, ngừng tính tiền máy
az vm start --resource-group rg-ecommerce --name vm-ecommerce        # bật lại
```

- Phải ở trạng thái **Stopped (deallocated)** mới ngừng tính tiền CPU/RAM. Lệnh `sudo shutdown` trong VM **không** ngừng tính tiền.
- Ổ đĩa và IP tĩnh vẫn tính phí nhỏ khi máy tắt.
- Khi bật lại, các container tự chạy lại nhờ `restart: unless-stopped`; chờ 3–5 phút cho hệ thống ổn định.

### 10.6 Dọn dẹp ổ đĩa

```bash
df -h /                      # dung lượng còn trống
docker system df             # Docker đang dùng bao nhiêu
docker image prune -f        # xóa image cũ không còn dùng
docker builder prune -f      # xóa cache build (lần build sau sẽ chậm hơn)
```

### 10.7 Xóa toàn bộ khi không dùng nữa

```powershell
az group delete --name rg-ecommerce --yes
```

Lệnh này xóa VM, ổ đĩa, IP và **toàn bộ dữ liệu**, không khôi phục được. Sao lưu trước.

---

## 11. Lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Gateway trả `503 Service Unavailable` | Service đích chưa đăng ký xong với Eureka, hoặc đã chết | Chờ 2–3 phút sau khi khởi động; `dc ps` và `dc logs <service>`; xem Eureka (mục 8.3) |
| Đăng nhập trả lỗi 500, log Auth có `Connection refused: localhost:8088` | Chưa sửa Feign client ở mục 3.1, hoặc chưa build lại image | Sửa code, `git pull`, `dc build auth-service`, `dc up -d auth-service` |
| Inventory hoặc Order không xuất hiện trong Eureka | Thiếu biến `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | Kiểm tra đã chạy lệnh kèm `-f docker-compose.azure.yml` (dùng bí danh `dc`) |
| Container bị `Exited (137)` hoặc tự khởi động lại liên tục | Hết RAM, bị hệ điều hành giết | `free -h`, `docker stats`; kiểm tra swap; giảm `-Xmx` hoặc nâng size VM |
| Elasticsearch thoát ngay, log có `max virtual memory areas vm.max_map_count [65530] is too low` | Chưa đặt tham số nhân | Làm lại mục 5.3 |
| Service báo `Access denied for user 'root'` | Volume MySQL đã được tạo với mật khẩu cũ; đổi `MYSQL_ROOT_PASSWORD` sau đó không có tác dụng | Dùng lại mật khẩu cũ, hoặc đổi mật khẩu bên trong MySQL bằng `ALTER USER` |
| `https://...` không vào được, log Caddy báo lỗi ACME/challenge | Tên miền chưa trỏ về VM, hoặc NSG chưa mở cổng 80/443 | Kiểm tra DNS name label, rule NSG; `dc logs caddy` |
| Trình duyệt báo lỗi CORS | Cấu hình CORS của Gateway (mục 3.5) | Sửa theo mục 3.5, build lại `api-gateway` |
| Trang sản phẩm thỉnh thoảng trả `429 Too Many Requests` | Giới hạn tần suất dùng chung một IP (mục 3.6) | Sửa theo mục 3.6 |
| Review tạo được nhưng điểm đánh giá sản phẩm không cập nhật | Review Service không nối được Kafka | Kiểm tra biến `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092` |
| Thanh toán xong bị chuyển về `localhost:5173` | Chưa sửa mục 3.4 hoặc chưa đặt `FRONTEND_URL` | Sửa code và `.env`, `dc up -d payment-service` |
| Không gửi được email | Sai App Password, hoặc Gmail chặn | Tạo App Password mới (cần bật xác minh 2 bước), cập nhật `.env`, `dc up -d notification-service` |
| Build báo `no space left on device` | Hết ổ đĩa | Mục 10.6; hoặc tăng dung lượng ổ trong Portal (VM → Disks) |
| SSH báo `Connection timed out` | IP của bạn đã đổi, rule SSH chỉ cho IP cũ | Sửa Source của rule SSH trong NSG thành IP hiện tại |
| Sau khi tắt/bật VM, tên miền không vào được | IP công khai là Dynamic và đã đổi | DNS label tự cập nhật sau vài phút; nên đặt IP là Static (mục 4.2) |

---

## 12. Bảo mật

Những việc nên làm trước khi đưa địa chỉ cho người khác dùng:

1. **Chỉ mở 3 cổng trong NSG: 22 (giới hạn theo IP của bạn), 80, 443.** Tuyệt đối không mở các cổng 3306x, 6379, 9200, 27017, 9092, 8761 hay 8082–8095. Redis, Elasticsearch và MongoDB trong cấu hình này **không có mật khẩu**; mở ra Internet là bị chiếm ngay. Lưu ý: tường lửa `ufw` trên Ubuntu **không** chặn được cổng do Docker mở, nên NSG là lớp bảo vệ duy nhất.
2. **Các service con không tự phân quyền** (đều `permitAll()`, tin vào header `X-User-*` của Gateway). Vì vậy chỉ được để Gateway (qua Caddy) lộ ra ngoài.
3. **Thay các bí mật đã bị đưa lên git.** Repo hiện chứa: mật khẩu ứng dụng Gmail (`NotificationService/application.yml`), mã bí mật VNPay (`PaymentService/application.yml`), khóa ký JWT (lặp lại ở nhiều service), mật khẩu MySQL, và token ngrok (`.env`, `.env.example`). Nếu repo công khai, coi như tất cả đã lộ: tạo lại App Password Gmail, token ngrok, và đổi các giá trị còn lại.
4. **Gỡ `.env` khỏi git** và thêm vào `.gitignore`:
   ```bash
   git rm --cached Microserivce_ECommerSystem/.env
   echo ".env" >> Microserivce_ECommerSystem/.gitignore
   ```
   `.env.example` chỉ nên chứa tên biến và giá trị giả.
5. **Khóa ký JWT** đang ghi cứng trong 9 file cấu hình và không ghi đè được bằng một biến chung. Muốn đổi cho môi trường thật: sửa các file đó thành `${JWT_SECRET:<giá trị cũ>}` rồi đặt `JWT_SECRET` trong `.env` và trong từng khối service.
6. **Chặn tự đăng ký quyền ADMIN:** `POST /api/users` là endpoint công khai và nhận `role` từ client.
7. **SSH chỉ bằng khóa**, không bật đăng nhập mật khẩu. Giữ file `.pem` an toàn.
8. **Cập nhật hệ điều hành định kỳ:** `sudo apt-get update && sudo apt-get upgrade -y`.

---

## 13. Hướng nâng cấp

Khi cần ổn định hơn mức demo, nâng cấp dần theo thứ tự lợi ích/công sức:

| Bước | Việc làm | Lợi ích |
|---|---|---|
| 1 | Gộp 8 container MySQL thành 1 (mỗi service một database riêng trong cùng máy chủ) | Giảm ~2,5 GB RAM; có thể hạ xuống VM nhỏ hơn |
| 2 | Build image bằng GitHub Actions, đẩy lên **Azure Container Registry**, VM chỉ `pull` | Không build trên VM; cập nhật nhanh; quay lui dễ |
| 3 | Chuyển MySQL sang **Azure Database for MySQL – Flexible Server** | Sao lưu tự động, khôi phục theo thời điểm |
| 4 | Chuyển Redis sang **Azure Cache for Redis**, MongoDB sang **Azure Cosmos DB for MongoDB** | Bớt việc vận hành |
| 5 | Chuyển các service sang **Azure Container Apps**; bỏ Eureka, dùng tên nội bộ của Container Apps | Tự co giãn, không còn một điểm hỏng duy nhất |
| 6 | Đưa bí mật vào **Azure Key Vault** | Không còn bí mật trong file |

Với bước 3–5, cấu hình hiện tại đã thuận lợi một phần: đa số service đọc địa chỉ DB, Redis, Kafka, Eureka từ biến môi trường.

---

## 14. Danh sách kiểm tra cuối

**Trước khi deploy**

- [ ] Đã sửa 6 mục code ở phần 3, build thử và push lên GitHub
- [ ] Đã có `docker-compose.azure.yml` và `Caddyfile` trong repo
- [ ] Đã có mã VNPay và App Password Gmail (đã tạo mới nếu repo công khai)

**Trên Azure**

- [ ] VM `Standard_B4ms`, Ubuntu 24.04, ổ 64 GB, vùng Southeast Asia
- [ ] Đã đặt DNS name label, IP ở chế độ Static
- [ ] NSG chỉ mở 22 (theo IP của bạn), 80, 443

**Trên VM**

- [ ] Đã cài Docker, tạo swap 4 GB, đặt `vm.max_map_count`
- [ ] Đã clone repo, tạo `.env` (quyền 600) với đủ các biến
- [ ] Build xong 16 image, khởi động theo từng lớp
- [ ] `dc ps` cho thấy mọi container Up; Eureka có đủ 15 service

**Xác nhận**

- [ ] `https://<DOMAIN>/api/products/filter` trả dữ liệu với chứng chỉ hợp lệ
- [ ] Đăng ký và đăng nhập qua `https://<DOMAIN>/auth/...` thành công
- [ ] FE trỏ `VITE_API_URL` về tên miền mới và gọi được API
- [ ] Đặt thử một đơn, thanh toán VNPay, được chuyển về đúng FE
- [ ] Đã chạy thử sao lưu (mục 10.4)
- [ ] Biết cách tắt máy để ngừng tính tiền (mục 10.5)
