# HƯỚNG DẪN TÍCH HỢP KUBERNETES VÀO DỰ ÁN

> Tài liệu viết riêng cho hệ thống này (`Microserivce_ECommerSystem/`), dựa trên `docker-compose.yml`, các `Dockerfile` và file cấu hình hiện có. Ngày viết: 2026-10-11.
>
> **Đi kèm tài liệu này là bộ manifest thật trong thư mục [`k8s/`](k8s/)** (32 file), dùng được ngay sau khi làm xong mục 4.
>
> **Trạng thái kiểm chứng:**
> - Bộ manifest đã được ghép thành công bằng `kubectl kustomize` cho cả 2 môi trường (máy cá nhân: 47 tài nguyên; AKS: 51 tài nguyên), tức là cú pháp và cấu trúc hợp lệ.
> - **Chưa áp dụng lên cluster thật nào.** Máy viết tài liệu không chạy được Docker lúc đó nên chưa build image và chưa `kubectl apply`. Lần chạy đầu tiên có thể cần chỉnh nhỏ.
> - Lệnh Azure và giá cả có thể thay đổi; kiểm tra lại trên trang chính thức.

## Mục lục

1. [Tóm tắt và khi nào nên dùng](#1-tóm-tắt-và-khi-nào-nên-dùng)
2. [Khái niệm Kubernetes áp vào dự án](#2-khái-niệm-kubernetes-áp-vào-dự-án)
3. [Kiến trúc trên Kubernetes](#3-kiến-trúc-trên-kubernetes)
4. [Sửa code trước khi triển khai (bắt buộc)](#4-sửa-code-trước-khi-triển-khai-bắt-buộc)
5. [Bộ manifest `k8s/`](#5-bộ-manifest-k8s)
6. [Chạy trên máy cá nhân](#6-chạy-trên-máy-cá-nhân)
7. [Lệnh vận hành thường dùng](#7-lệnh-vận-hành-thường-dùng)
8. [Triển khai lên Azure (AKS)](#8-triển-khai-lên-azure-aks)
9. [Cập nhật phiên bản và quay lui](#9-cập-nhật-phiên-bản-và-quay-lui)
10. [Tăng số bản chạy (scale)](#10-tăng-số-bản-chạy-scale)
11. [Lỗi thường gặp](#11-lỗi-thường-gặp)
12. [Bảo mật](#12-bảo-mật)
13. [Hướng nâng cấp](#13-hướng-nâng-cấp)
14. [Danh sách kiểm tra](#14-danh-sách-kiểm-tra)

---

## 1. Tóm tắt và khi nào nên dùng

Kubernetes (K8s) là hệ thống điều phối container: bạn khai báo "muốn chạy những gì", K8s lo chạy, giám sát, khởi động lại khi hỏng, cập nhật không gián đoạn và tăng giảm số bản chạy.

So với cách đang có (một máy ảo + Docker Compose, trong `HUONG_DAN_DEPLOY_AZURE.md`):

| | Docker Compose trên 1 VM | Kubernetes |
|---|---|---|
| Container hỏng | Tự khởi động lại trên cùng máy | Tự khởi động lại; máy hỏng thì chuyển sang máy khác |
| Cập nhật phiên bản | Dừng bản cũ rồi chạy bản mới (gián đoạn vài chục giây) | Thay dần từng bản, không gián đoạn; quay lui bằng 1 lệnh |
| Tăng tải | Nâng cấu hình VM | Thêm bản chạy của riêng service bị tải, thêm máy |
| Kiểm tra sức khỏe | Chỉ biết container còn chạy | Biết ứng dụng đã sẵn sàng nhận request hay chưa |
| Độ phức tạp | Thấp | Cao: nhiều khái niệm, nhiều file |
| Chi phí trên Azure | 1 VM | Ít nhất 1–2 node + ổ đĩa + IP; thường đắt hơn |

**Nên dùng khi:** muốn học và thể hiện kiến trúc triển khai microservice chuẩn, cần cập nhật không gián đoạn, hoặc cần scale riêng từng service.
**Chưa cần khi:** chỉ cần một bản demo chạy được — Docker Compose trên 1 VM rẻ và đơn giản hơn nhiều.

Phương án trong tài liệu được thiết kế để **thay đổi ít nhất** so với hiện tại:

- Giữ nguyên Eureka và cách các service tìm nhau (bỏ Eureka là bước nâng cấp sau, mục 13.1).
- Tên các Service trong cluster trùng tên container trong `docker-compose.yml`, nên địa chỉ nội bộ giữ nguyên dạng.
- Gom 8 container MySQL thành 1 máy chủ MySQL chứa 8 database, để tiết kiệm tài nguyên.

---

## 2. Khái niệm Kubernetes áp vào dự án

| Trong Docker Compose | Trong Kubernetes | Dùng cho gì ở dự án này |
|---|---|---|
| 1 mục trong `services:` (ứng dụng) | **Deployment** | 15 ứng dụng Spring Boot, Redis, Kafka, Zookeeper |
| 1 mục trong `services:` (có dữ liệu) | **StatefulSet** | MySQL, MongoDB, Elasticsearch — mỗi cái gắn với một ổ đĩa cố định |
| Tên container dùng làm địa chỉ (`user-service:8088`) | **Service** | Mỗi thành phần có 1 Service cùng tên; đó là địa chỉ ổn định dù pod đổi IP |
| Container đang chạy | **Pod** | Một bản đang chạy của Deployment/StatefulSet |
| `environment:` | **ConfigMap** (không bí mật) và **Secret** (bí mật) | `ecommerce-config`, `ecommerce-secret` |
| `volumes:` | **PersistentVolumeClaim (PVC)** | Dữ liệu MySQL/Mongo/ES, ảnh sản phẩm |
| `ports:` mở ra máy chủ | **Service loại NodePort / LoadBalancer** | Chỉ Gateway (máy cá nhân) hoặc Caddy (AKS) được mở ra ngoài |
| `healthcheck:` + `depends_on:` | **Probe** (startup / readiness / liveness) | K8s không có `depends_on`: ứng dụng tự thử lại cho tới khi phụ thuộc sẵn sàng |
| `networks:` | **Namespace** | Mọi thứ nằm trong namespace `ecommerce` |
| `docker-compose.azure.yml` (lớp ghi đè) | **Kustomize overlay** | `overlays/local`, `overlays/aks` |

Công cụ dòng lệnh là `kubectl`. Docker Desktop đã cài sẵn `kubectl` (kèm Kustomize) trên máy bạn.

---

## 3. Kiến trúc trên Kubernetes

```
Internet
   │  HTTPS 443
   ▼
┌───────────────────────────── Cluster Kubernetes, namespace "ecommerce" ─────────────────────────────┐
│                                                                                                      │
│  [Service caddy: LoadBalancer] ──▶ [Pod caddy] ──▶ [Service api-gateway] ──▶ [Pod api-gateway]       │
│        (chỉ có trên AKS; máy cá nhân dùng NodePort 30085 vào thẳng api-gateway)      │               │
│                                                                                      │ lb:// qua Eureka
│                                                                                      ▼               │
│   [eureka-server]   ◀── đăng ký bằng IP của pod ──   13 service nghiệp vụ (mỗi cái 1 Deployment +    │
│                                                      1 Service): user, auth, product, inventory,     │
│                                                      order, cart, payment, notification, chat,       │
│                                                      review, shipping, search, point                 │
│                                                                    │                                 │
│   [mysql] 8 database   [mongo]   [redis]   [elasticsearch]   [kafka] ── [zookeeper]                  │
│      PVC 5Gi           PVC 2Gi              PVC 5Gi                                                  │
│                                                                                                      │
│   ConfigMap ecommerce-config ──┐                                                                     │
│   Secret    ecommerce-secret ──┴──▶ nạp vào mọi pod ứng dụng dưới dạng biến môi trường               │
└──────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 3.1 Các quyết định thiết kế

**Giữ Eureka.** Gateway đang định tuyến bằng `lb://<tên trên Eureka>` và nhiều Feign client tìm nhau theo tên Eureka. Giữ nguyên thì không phải sửa route nào. Điều kiện để chạy được trong K8s: mỗi service phải đăng ký với Eureka bằng **IP của pod** (`EUREKA_INSTANCE_PREFER_IP_ADDRESS=true`, đã đặt trong ConfigMap), vì tên pod không phân giải được từ pod khác.

**Một MySQL cho 8 database.** Mỗi service vẫn có database riêng (`user_service`, `order_db`, ...), nhưng cùng nằm trên một máy chủ MySQL. Tiết kiệm khoảng 2,5 GB RAM so với 8 máy chủ. Database tự được tạo nhờ `createDatabaseIfNotExist=true` trong chuỗi kết nối.

**Tắt "service links" ở mọi pod (`enableServiceLinks: false`).** Mặc định, với mỗi Service trong namespace, K8s tự chèn vào mọi pod các biến môi trường kiểu `REDIS_PORT=tcp://10.0.0.5:6379`, `MONGO_PORT=tcp://...`, `KAFKA_PORT=tcp://...`. Dự án này đang đọc đúng các tên đó trong cấu hình: Auth Service dùng `${REDIS_PORT:6379}`, Chat Service dùng `${MONGO_PORT:27017}`, và image `cp-kafka` từ chối khởi động khi thấy `KAFKA_PORT`. Nếu không tắt, ba thành phần này sẽ hỏng với lỗi rất khó hiểu.

**Chỉ một cổng vào.** Chỉ Gateway được mở ra ngoài cluster. Mọi service khác chỉ có địa chỉ nội bộ. Điều này quan trọng vì các service con tin vào header `X-User-*` và không tự phân quyền.

**Kiểm tra sức khỏe bằng cổng TCP.** Dự án chưa có `spring-boot-starter-actuator`, nên probe chỉ kiểm tra "cổng đã mở". Đủ dùng, nhưng không phát hiện được trường hợp ứng dụng treo mà cổng vẫn mở. Nâng cấp ở mục 13.2.

### 3.2 Service nào chạy được nhiều bản

| Service | Nhiều bản? | Lý do |
|---|---|---|
| api-gateway, user, auth, inventory, order, cart, payment, review, shipping, search, point, notification | Được | Không giữ trạng thái trong bộ nhớ; hạn mức tần suất, giỏ hàng, cache đều ở Redis; tồn kho dùng khóa ở database |
| **chat-service** | **Không** | Broker STOMP nằm trong bộ nhớ: khách và admin nối vào 2 bản khác nhau sẽ không thấy tin của nhau |
| **product-service** | **Không** | Ảnh sản phẩm lưu trên ổ đĩa chỉ gắn được vào 1 pod; job khuyến mãi ngày lễ (`@Scheduled`) sẽ chạy trùng nếu có nhiều bản |
| eureka-server | Giữ 1 | Chạy nhiều bản cần cấu hình các bản biết nhau |
| mysql, mongo, redis, elasticsearch, kafka, zookeeper | Giữ 1 | Tăng bản cho cơ sở dữ liệu cần cấu hình nhân bản riêng, không chỉ đổi con số |

Hai service "Không" được đặt sẵn `strategy: Recreate` trong manifest (dừng bản cũ rồi mới chạy bản mới), nghĩa là cập nhật chúng **có** gián đoạn ngắn.

### 3.3 Tài nguyên cần có

| Nhóm | Số pod | RAM yêu cầu (requests) | RAM tối đa (limits) |
|---|---|---|---|
| Ứng dụng Spring Boot | 15 | 15 × 320 MiB ≈ 4,7 GiB | 15 × 640 MiB ≈ 9,4 GiB |
| MySQL, Mongo, Redis, Elasticsearch, Kafka, Zookeeper | 6 | ≈ 2,8 GiB | ≈ 4,8 GiB |
| **Tổng** | 21 | **≈ 7,5 GiB** | **≈ 14 GiB** |

Cluster cần ít nhất khoảng 10 GB RAM khả dụng để chạy thoải mái; CPU yêu cầu tổng khoảng 2,5 nhân.

---

## 4. Sửa code trước khi triển khai (bắt buộc)

Trong container, `localhost` là chính container đó. Một số chỗ trong code ghi cứng `localhost` nên sẽ hỏng trên Kubernetes y như trên Docker. Đây là **cùng danh sách** với mục 3 của `HUONG_DAN_DEPLOY_AZURE.md`; nếu đã sửa ở đó thì bỏ qua mục này.

| # | Chỗ cần sửa | Hậu quả nếu không sửa | Biến môi trường (đã có trong ConfigMap) |
|---|---|---|---|
| 1 | Auth Service: 4 Feign client ghi cứng `http://localhost:808x` | **Không đăng nhập, không đăng ký được** | `USER_SERVICE_URL`, `ORDER_SERVICE_URL`, `PRODUCT_SERVICE_URL`, `NOTIFICATION_SERVICE_URL` |
| 2 | User Service: `UserIntegrationServiceImpl` ghi cứng URL Order/Product | Trang Hồ sơ không tải được | `ORDER_SERVICE_URL`, `PRODUCT_SERVICE_URL` |
| 3 | Search Service: `SearchController` ghi cứng `localhost:8082` | Đồng bộ tìm kiếm thủ công hỏng | `PRODUCT_SERVICE_URL` |
| 4 | Payment Service: chuyển hướng về `localhost:5173` sau khi thanh toán | Thanh toán xong về sai địa chỉ | `FRONTEND_URL` |
| 5 | Gateway: hạn mức 1 request/giây tính theo IP nguồn | Sau cổng vào, mọi người dùng chung một hạn mức → trang sản phẩm báo 429 | – (sửa `RateLimiterConfig` đọc `X-Forwarded-For`) |

Đoạn code sửa cho từng mục nằm trong `HUONG_DAN_DEPLOY_AZURE.md`, mục 3.1–3.6. (Mục CORS ở đó đã được xử lý trong đợt hoàn thiện Chat Service.)

Các chỗ ghi cứng khác **không cần sửa code**, ConfigMap đã ghi đè: địa chỉ Eureka của Inventory và Order (`EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`), địa chỉ Product của Order (`PRODUCT_SERVICE_URL`), Kafka của Review (`SPRING_KAFKA_BOOTSTRAP_SERVERS`), URL trả về của VNPay (`VNP_RETURNURL`).

---

## 5. Bộ manifest `k8s/`

### 5.1 Cấu trúc

```
k8s/
├── build-images.sh              # build (và push) image cho 16 service, lần lượt từng cái
├── .gitignore                   # chặn secret.yaml khỏi git
├── base/                        # phần dùng chung cho mọi môi trường
│   ├── kustomization.yaml       # danh sách tài nguyên, gán namespace "ecommerce"
│   ├── namespace.yaml
│   ├── config.yaml              # ConfigMap ecommerce-config: toàn bộ cấu hình không bí mật
│   ├── secret.example.yaml      # MẪU Secret — sao chép thành secret.yaml rồi điền giá trị thật
│   ├── data/                    # mysql, mongo, redis, elasticsearch, kafka (+ zookeeper)
│   ├── infra/                   # eureka-server, api-gateway
│   └── services/                # 13 service nghiệp vụ, mỗi file gồm Deployment + Service
└── overlays/
    ├── local/                   # máy cá nhân: mở Gateway tại cổng 30085
    └── aks/                     # Azure: đổi tên image sang registry, thêm Caddy (HTTPS), đổi tên miền
```

Kustomize ghép `base` với một `overlay` để ra bộ manifest cuối cùng cho từng môi trường, tương tự cách `docker-compose.azure.yml` ghép lên `docker-compose.yml`. Xem trước kết quả mà không áp dụng:

```bash
kubectl kustomize k8s/overlays/local
```

### 5.2 Một service trông như thế nào

Cả 15 ứng dụng dùng chung một khuôn. Ví dụ `k8s/base/services/order-service.yaml` (rút gọn chú thích):

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 1                          # số bản chạy
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      enableServiceLinks: false        # xem mục 3.1
      terminationGracePeriodSeconds: 40
      containers:
        - name: order-service
          image: ecommerce/order-service:latest
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8084
          envFrom:                     # nạp toàn bộ ConfigMap và Secret thành biến môi trường
            - configMapRef:
                name: ecommerce-config
            - secretRef:
                name: ecommerce-secret
          env:                         # riêng service này: database của nó
            - name: SPRING_DATASOURCE_URL
              value: jdbc:mysql://mysql:3306/order_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
          resources:
            requests: { cpu: 100m, memory: 320Mi }   # mức K8s dành sẵn khi xếp pod vào node
            limits:   { memory: 640Mi }              # vượt mức này pod bị dừng (OOMKilled)
          startupProbe:                # cho tối đa 5 phút để khởi động
            tcpSocket: { port: 8084 }
            periodSeconds: 5
            failureThreshold: 60
          readinessProbe:              # chưa đạt thì chưa nhận request
            tcpSocket: { port: 8084 }
          livenessProbe:               # không đạt 3 lần liên tiếp thì khởi động lại
            tcpSocket: { port: 8084 }
            periodSeconds: 20
          lifecycle:
            preStop:                   # trước khi tắt: chờ Eureka/Gateway ngừng gửi request tới
              exec:
                command: ["sleep", "15"]
---
apiVersion: v1
kind: Service
metadata:
  name: order-service                  # địa chỉ nội bộ: http://order-service:8084
spec:
  selector:
    app: order-service
  ports:
    - port: 8084
      targetPort: 8084
```

### 5.3 Bảng service

| Service (tên trong K8s) | Thư mục Dockerfile | Cổng | Database MySQL | Ghi chú |
|---|---|---|---|---|
| `eureka-server` | `Eureka/Eureka` | 8761 | – | |
| `api-gateway` | `API Gateway/API-Gateway` | 8085 | – | Cổng vào duy nhất |
| `user-service` | `User Service/User-Service` | 8088 | `user_service` | |
| `auth-service` | `Auth Service/Auth-Service` | 8087 | – | |
| `product-service` | `ProductService/ProductService` | 8082 | `product_service` | 1 bản; PVC `product-uploads` 2Gi tại `/app/uploads/products` |
| `inventory-service` | `InventoryService/InventoryService` | 8086 | `inventory_db` | |
| `order-service` | `OrderService/OrderService` | 8084 | `order_db` | |
| `cart-service` | `CartService/CartService` | 8083 | – | Redis |
| `payment-service` | `PaymentService/PaymentService` | 8089 | `payment_db` | Dùng `VNP_*`, `FRONTEND_URL` |
| `notification-service` | `NotificationService/NotificationService` | 8090 | – | Dùng `SPRING_MAIL_*` |
| `chat-service` | `ChatService/ChatService` | 8092 | – | 1 bản; MongoDB |
| `review-service` | `ReviewService/ReviewService` | 8091 | `review_service_db` | |
| `shipping-service` | `ShippingService/ShippingService` | 8093 | `shipping_service` | |
| `search-service` | `SearchService/SearchService` | 8094 | – | Elasticsearch |
| `point-service` | `PointService/PointService` | 8095 | `point_service` | |
| `coupon-service` | `CouponService/CouponService` | 8096 | `coupon_service` | |

### 5.4 Cấu hình và bí mật

- **`base/config.yaml`** (ConfigMap `ecommerce-config`): địa chỉ Eureka, Kafka, Redis, MongoDB, Elasticsearch; địa chỉ gọi thẳng giữa các service; `FRONTEND_URL`, `VNP_RETURNURL`; tùy chọn JVM (`-XX:MaxRAMPercentage=70` để JVM tự co theo giới hạn bộ nhớ của container). Sửa file này rồi áp dụng lại và khởi động lại pod để có hiệu lực (mục 7).
- **`base/secret.example.yaml`**: mẫu Secret `ecommerce-secret` gồm mật khẩu MySQL, mã VNPay, tài khoản Gmail. **Không điền giá trị thật vào file mẫu.** Sao chép thành `secret.yaml` (đã bị `.gitignore` chặn) rồi điền.
- Để đơn giản, mọi pod ứng dụng nạp **toàn bộ** ConfigMap và Secret. Biến nào service không dùng thì bị bỏ qua. Hệ quả: service nào cũng đọc được mọi bí mật; tách nhỏ ở mục 12.

Muốn thêm biến cho riêng một service (ví dụ `ANTHROPIC_API_KEY` cho chatbot, `GOOGLE_CLIENT_ID` cho đăng nhập Google, các biến `KEYCLOAK_*`): thêm vào Secret/ConfigMap chung, hoặc thêm mục `env:` trong file của service đó.

---

## 6. Chạy trên máy cá nhân

Mục tiêu: chạy toàn bộ backend trong Kubernetes của Docker Desktop, FE vẫn chạy bằng `npm run dev`.

### 6.1 Bật Kubernetes

1. Mở **Docker Desktop → Settings → Kubernetes → Enable Kubernetes → Apply & Restart**. Chờ biểu tượng Kubernetes chuyển xanh.
2. **Settings → Resources**: cấp ít nhất **12 GB RAM** và 4 CPU. Nếu Docker Desktop dùng WSL 2, giới hạn RAM đặt trong file `C:\Users\<bạn>\.wslconfig`:
   ```ini
   [wsl2]
   memory=12GB
   processors=4
   ```
   rồi chạy `wsl --shutdown` và mở lại Docker Desktop.
3. Kiểm tra:
   ```bash
   kubectl config use-context docker-desktop
   kubectl get nodes
   ```
   Phải thấy 1 node ở trạng thái `Ready`.

Máy dưới 16 GB RAM sẽ rất chật. Khi đó chạy tập con (mục 6.6) hoặc dùng thẳng AKS (mục 8).

### 6.2 Build image

Chạy trong **Git Bash**, tại thư mục `Microserivce_ECommerSystem`:

```bash
./k8s/build-images.sh
```

Script build 16 image tên `ecommerce/<tên-service>:latest`, lần lượt từng cái. Lần đầu mất 30–60 phút vì mỗi service tải thư viện Maven. Kubernetes của Docker Desktop dùng chung kho image với Docker nên không cần registry.

Kiểm tra: `docker images "ecommerce/*"` phải liệt kê đủ 16 image.

### 6.3 Tạo namespace và Secret

```bash
kubectl apply -f k8s/base/namespace.yaml

cp k8s/base/secret.example.yaml k8s/base/secret.yaml
# Mở k8s/base/secret.yaml, điền giá trị thật (MYSQL_ROOT_PASSWORD và SPRING_DATASOURCE_PASSWORD phải GIỐNG NHAU)

kubectl apply -n ecommerce -f k8s/base/secret.yaml
```

### 6.4 Triển khai

```bash
kubectl apply -k k8s/overlays/local
```

Theo dõi các pod khởi động (Ctrl+C để thoát):

```bash
kubectl get pods -n ecommerce -w
```

Diễn biến bình thường:

- Các pod dữ liệu (`mysql-0`, `mongo-0`, `kafka-...`) lên trước, mất 1–2 phút.
- Các pod ứng dụng có thể hiện `CrashLoopBackOff` hoặc `RESTARTS` tăng vài lần trong 2–3 phút đầu. Đó là vì Kubernetes không có `depends_on`: ứng dụng khởi động khi MySQL/Kafka chưa sẵn sàng, thoát, rồi được khởi động lại. **Tự hết** khi tầng dữ liệu sẵn sàng.
- Sau khoảng 5 phút, mọi pod phải ở `Running` với cột `READY` là `1/1`.
- Thêm 1–2 phút nữa để các service đăng ký với Eureka và Gateway lấy danh sách.

### 6.5 Kiểm tra

```bash
# Tổng quan
kubectl get pods,svc -n ecommerce

# Eureka: mở đường hầm rồi vào http://localhost:8761 — phải thấy 15 service
kubectl port-forward -n ecommerce svc/eureka-server 8761:8761

# Gọi API qua Gateway (cổng 30085 trên máy cá nhân)
curl -i http://localhost:30085/api/products/filter

curl -i -X POST http://localhost:30085/auth/register -H "Content-Type: application/json" \
  -d '{"username":"k8stest","email":"k8stest@example.com","password":"Test@12345"}'

curl -i -X POST http://localhost:30085/auth/login -H "Content-Type: application/json" \
  -d '{"username":"k8stest","password":"Test@12345"}'
```

Đăng nhập trả về `token` nghĩa là chuỗi Gateway → Auth → User → MySQL đã thông.

**Nối FE:** trong `FE/.env` đặt `VITE_API_URL=http://localhost:30085`, chạy lại `npm run dev`. Thanh toán VNPay sẽ quay về `http://localhost:30085/api/payment/vnpay-callback` rồi chuyển tiếp tới `http://localhost:5173` (hai giá trị này nằm trong `base/config.yaml`).

### 6.6 Chạy tập con khi máy yếu

Áp dụng từng file thay vì cả bộ. Ví dụ chỉ chạy luồng đăng nhập và xem sản phẩm:

```bash
kubectl apply -n ecommerce -f k8s/base/config.yaml
kubectl apply -n ecommerce -f k8s/base/data/mysql.yaml -f k8s/base/data/redis.yaml -f k8s/base/data/kafka.yaml
kubectl apply -n ecommerce -f k8s/base/infra/
kubectl apply -n ecommerce -f k8s/base/services/user-service.yaml -f k8s/base/services/auth-service.yaml \
  -f k8s/base/services/product-service.yaml -f k8s/base/services/inventory-service.yaml
kubectl apply -n ecommerce -f k8s/overlays/local/gateway-nodeport.yaml
```

### 6.7 Dừng và xóa

```bash
# Dừng mọi pod nhưng GIỮ dữ liệu (PVC)
kubectl scale deployment,statefulset --all --replicas=0 -n ecommerce

# Chạy lại
kubectl scale deployment,statefulset --all --replicas=1 -n ecommerce

# Xóa sạch, KỂ CẢ DỮ LIỆU
kubectl delete namespace ecommerce
```

### 6.8 Dùng minikube thay cho Docker Desktop

Khác biệt: minikube có kho image riêng. Sau khi build, nạp image vào minikube (`minikube image load ecommerce/user-service:latest`, lặp cho 16 image), hoặc build thẳng trong minikube bằng cách chạy `eval $(minikube docker-env)` trước `build-images.sh`. Truy cập Gateway bằng `minikube service api-gateway -n ecommerce --url`. Khởi động với đủ tài nguyên: `minikube start --memory=12g --cpus=4`.

---

## 7. Lệnh vận hành thường dùng

Đặt namespace mặc định để khỏi gõ `-n ecommerce` mỗi lần:

```bash
kubectl config set-context --current --namespace=ecommerce
```

| Việc cần làm | Lệnh |
|---|---|
| Xem mọi pod | `kubectl get pods` |
| Xem pod kèm node và IP | `kubectl get pods -o wide` |
| Vì sao pod không chạy | `kubectl describe pod <tên-pod>` (đọc phần **Events** ở cuối) |
| Log của một service | `kubectl logs deploy/order-service --tail=100 -f` |
| Log của lần chạy trước (pod vừa bị khởi động lại) | `kubectl logs <tên-pod> --previous` |
| Vào trong container | `kubectl exec -it deploy/order-service -- sh` |
| Xem biến môi trường pod đang có | `kubectl exec deploy/order-service -- env \| sort` |
| Khởi động lại một service | `kubectl rollout restart deploy/order-service` |
| Mở MySQL từ máy cá nhân | `kubectl port-forward svc/mysql 3307:3306` rồi nối tới `localhost:3307` |
| Xem RAM/CPU đang dùng | `kubectl top pods` (cần metrics-server; AKS có sẵn) |
| Xem sự kiện gần đây của namespace | `kubectl get events --sort-by=.lastTimestamp \| tail -20` |
| Xem ổ đĩa | `kubectl get pvc` |

**Sau khi sửa ConfigMap hoặc Secret:** pod đang chạy **không** tự nhận giá trị mới (vì nạp qua biến môi trường). Áp dụng lại rồi khởi động lại:

```bash
kubectl apply -k k8s/overlays/local
kubectl rollout restart deployment -n ecommerce
```

**Sao lưu dữ liệu:**

```bash
PASS=$(kubectl get secret ecommerce-secret -o jsonpath='{.data.MYSQL_ROOT_PASSWORD}' | base64 -d)
kubectl exec mysql-0 -- mysqldump -uroot -p"$PASS" --all-databases --single-transaction | gzip > mysql-$(date +%Y%m%d).sql.gz
kubectl exec mongo-0 -- mongodump --archive --gzip > mongo-$(date +%Y%m%d).archive.gz
```

Giao diện trực quan (tùy chọn): **k9s** (trong terminal) hoặc **Lens / Headlamp** (ứng dụng desktop) giúp xem pod, log, sự kiện nhanh hơn nhiều so với gõ lệnh.

---

## 8. Triển khai lên Azure (AKS)

AKS là Kubernetes do Azure quản lý: Azure lo phần điều khiển cluster, bạn trả tiền cho các máy (node) chạy pod.

### 8.1 Tài nguyên và chi phí

| Tài nguyên | Đề xuất | Ghi chú |
|---|---|---|
| Cluster AKS | Gói **Free** | Phần điều khiển miễn phí ở gói này |
| Node | 2 × `Standard_D4s_v5` (4 vCPU, 16 GB) | 1 node 16 GB cũng chạy được nhưng mất lợi ích "máy hỏng thì chuyển sang máy khác" |
| Registry | Azure Container Registry, gói **Basic** | Lưu 16 image |
| Ổ đĩa | 4 PVC, tổng 15 GiB | Azure tự tạo Managed Disk |
| IP công khai + Load Balancer | 1 | Cho cổng vào Caddy |

Chi phí chủ yếu là 2 node chạy 24/7 — cao hơn đáng kể so với 1 VM trong tài liệu deploy. Tra số chính xác tại [Azure Pricing Calculator](https://azure.microsoft.com/pricing/calculator/). Khi không dùng, **dừng cluster** (mục 8.8) để ngừng tính tiền node.

**Hạn mức vCPU:** tài khoản Azure for Students và tài khoản mới thường bị giới hạn số vCPU mỗi vùng (có thể chỉ 4–6). 2 node D4s cần 8 vCPU. Kiểm tra tại **Subscriptions → Usage + quotas**; nếu không đủ, dùng 1 node `Standard_D4s_v5` (chật nhưng chạy được nếu hạ `requests`), hoặc xin tăng hạn mức.

### 8.2 Chuẩn bị công cụ

- [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli) (`az`).
- `kubectl` (đã có từ Docker Desktop).
- Docker Desktop đang chạy (để build và push image).

### 8.3 Tạo registry và cluster

PowerShell (đổi `myecommerceacr` thành tên của bạn — chỉ chữ thường và số, duy nhất toàn Azure):

```powershell
az login

az group create --name rg-ecommerce-k8s --location southeastasia

az acr create --resource-group rg-ecommerce-k8s --name myecommerceacr --sku Basic

az aks create `
  --resource-group rg-ecommerce-k8s `
  --name aks-ecommerce `
  --tier free `
  --node-count 2 `
  --node-vm-size Standard_D4s_v5 `
  --attach-acr myecommerceacr `
  --generate-ssh-keys

# Nối kubectl với cluster vừa tạo
az aks get-credentials --resource-group rg-ecommerce-k8s --name aks-ecommerce

kubectl get nodes
```

`--attach-acr` cho phép cluster kéo image từ registry mà không cần khai báo mật khẩu trong manifest. Tạo cluster mất 5–10 phút.

Chuyển qua lại giữa cluster máy cá nhân và AKS:

```bash
kubectl config get-contexts
kubectl config use-context aks-ecommerce      # hoặc docker-desktop
```

**Luôn kiểm tra đang ở context nào trước khi chạy lệnh xóa.**

### 8.4 Build và đẩy image

Git Bash, tại `Microserivce_ECommerSystem`:

```bash
az acr login --name myecommerceacr
./k8s/build-images.sh myecommerceacr.azurecr.io v1
```

Dùng tag có phiên bản (`v1`, `v2`, hoặc mã commit) thay vì `latest`: Kubernetes chỉ cập nhật khi tên image thay đổi, và tag rõ ràng mới quay lui được.

Nếu máy yếu hoặc mạng tải lên chậm, build ngay trên Azure (không cần Docker ở máy):

```bash
az acr build --registry myecommerceacr --image ecommerce/order-service:v1 "OrderService/OrderService"
```

(lặp cho từng service theo bảng ở mục 5.3).

### 8.5 Chỉnh overlay AKS

Sửa 3 chỗ, đều trong `k8s/overlays/aks/`:

1. **`kustomization.yaml`**: thay mọi `myecommerceacr` bằng tên registry của bạn, `newTag` bằng tag đã đẩy.
2. **`caddy.yaml`**: dòng `service.beta.kubernetes.io/azure-dns-label-name` — đặt nhãn DNS của bạn (duy nhất trong vùng), ví dụ `ecommerce-truong`. Tên miền sẽ là `ecommerce-truong.southeastasia.cloudapp.azure.com`.
3. **`config-patch.yaml`**: `DOMAIN` = đúng tên miền trên (không có `https://`); `VNP_RETURNURL` = `https://<tên miền>/api/payment/vnpay-callback`; `FRONTEND_URL` = địa chỉ FE.

Xem trước kết quả ghép:

```bash
kubectl kustomize k8s/overlays/aks | less
```

### 8.6 Triển khai

```bash
kubectl apply -f k8s/base/namespace.yaml
kubectl apply -n ecommerce -f k8s/base/secret.yaml        # dùng mật khẩu mạnh, khác với máy cá nhân
kubectl apply -k k8s/overlays/aks

kubectl get pods -n ecommerce -w
```

Lấy địa chỉ công khai:

```bash
kubectl get svc caddy -n ecommerce
```

Cột `EXTERNAL-IP` hiện IP sau 1–2 phút. Tên miền `<nhãn>.southeastasia.cloudapp.azure.com` trỏ vào IP đó. Khi có request đầu tiên tới tên miền, Caddy tự xin chứng chỉ Let's Encrypt (mất khoảng nửa phút).

### 8.7 Kiểm tra

```bash
curl -i https://ecommerce-truong.southeastasia.cloudapp.azure.com/api/products/filter
```

Rồi chạy lại các bước đăng ký/đăng nhập ở mục 6.5 với địa chỉ HTTPS. Đặt `VITE_API_URL` của FE thành địa chỉ này trước khi build FE.

Lỗi chứng chỉ: `kubectl logs deploy/caddy -n ecommerce`.

### 8.8 Dừng, chạy lại, xóa

```powershell
# Dừng cluster: ngừng tính tiền node, GIỮ dữ liệu và cấu hình
az aks stop --resource-group rg-ecommerce-k8s --name aks-ecommerce

# Chạy lại (mất vài phút; pod tự lên lại)
az aks start --resource-group rg-ecommerce-k8s --name aks-ecommerce

# Xóa toàn bộ, KỂ CẢ DỮ LIỆU — không khôi phục được
az group delete --name rg-ecommerce-k8s --yes
```

Khi cluster dừng, ổ đĩa, registry và IP vẫn tính phí nhỏ.

---

## 9. Cập nhật phiên bản và quay lui

### 9.1 Cập nhật một service

Ví dụ sửa code Order Service:

```bash
# 1. Build và đẩy image với tag mới
az acr build --registry myecommerceacr --image ecommerce/order-service:v2 "OrderService/OrderService"

# 2. Đổi tag: sửa newTag của order-service trong k8s/overlays/aks/kustomization.yaml thành v2, rồi
kubectl apply -k k8s/overlays/aks

# 3. Theo dõi
kubectl rollout status deploy/order-service -n ecommerce
```

Kubernetes tạo pod mới, chờ nó vượt `readinessProbe`, rồi mới tắt pod cũ. Với service 1 bản, đây là lúc tạm thời có 2 pod. Người dùng không bị gián đoạn — trừ một khoảng ngắn do Eureka cập nhật chậm; đoạn `preStop: sleep 15` trong manifest giảm thiểu việc này.

Trên máy cá nhân với tag `latest`: build lại rồi `kubectl rollout restart deploy/order-service`.

### 9.2 Quay lui

```bash
kubectl rollout undo deploy/order-service -n ecommerce          # về phiên bản ngay trước
kubectl rollout history deploy/order-service -n ecommerce       # xem các phiên bản đã có
```

Sau đó sửa lại `newTag` trong file cho khớp, để lần `apply` sau không đẩy lại bản lỗi.

### 9.3 Lưu ý về cơ sở dữ liệu

Các service dùng `ddl-auto: update`: bản mới tự thêm cột/bảng khi khởi động. Trong lúc cập nhật có một khoảng bản cũ và bản mới cùng chạy trên cùng database. Thêm cột thì an toàn; **đổi tên hoặc xóa cột** sẽ làm bản cũ lỗi, và quay lui code không quay lui được cấu trúc database. Với thay đổi kiểu đó, làm thành hai lần phát hành (thêm mới trước, xóa cũ sau).

---

## 10. Tăng số bản chạy (scale)

### 10.1 Thủ công

```bash
kubectl scale deploy/order-service --replicas=3 -n ecommerce
```

Mỗi bản tự đăng ký với Eureka; Gateway và Feign chia tải giữa các bản. Để cố định, sửa `replicas:` trong file của service đó và `apply`.

Chỉ áp dụng cho các service ở cột "Được" trong mục 3.2. **Không** tăng bản cho `chat-service` và `product-service`.

### 10.2 Tự động theo tải (HPA)

Kubernetes có thể tự thêm/bớt bản theo mức dùng CPU. Cần metrics-server (AKS có sẵn; Docker Desktop phải cài thêm). Ví dụ cho Order Service, lưu thành `k8s/overlays/aks/order-hpa.yaml` và thêm vào `resources:` của `kustomization.yaml`:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service
  namespace: ecommerce
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 1
  maxReplicas: 4
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70     # % so với cpu requests (100m)
```

Lưu ý với ứng dụng Java: lúc khởi động CPU tăng vọt, dễ kích hoạt thêm bản không cần thiết. Nên nâng `cpu requests` của service đó lên 250–500m trước khi bật HPA, và quan sát vài ngày.

### 10.3 Khi một node hỏng

Với 2 node, pod trên node hỏng được tạo lại trên node còn lại (nếu đủ RAM). Riêng pod có ổ đĩa (`mysql-0`, `mongo-0`, `elasticsearch-0`, `product-service`, `caddy`) phải chờ ổ đĩa được tháo khỏi node cũ, có thể mất vài phút. Dữ liệu trên ổ đĩa không mất, nhưng đây **không** phải cơ sở dữ liệu có dự phòng: muốn thế phải dùng dịch vụ database quản lý (mục 13.4).

---

## 11. Lỗi thường gặp

Luôn bắt đầu bằng: `kubectl get pods -n ecommerce`, rồi `kubectl describe pod <tên>` và `kubectl logs <tên> --previous`.

| Hiện tượng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Pod `ImagePullBackOff` / `ErrImagePull` | Chưa build image; sai tên/tag; AKS chưa được gắn với ACR | Máy cá nhân: chạy `build-images.sh`. AKS: kiểm tra `kustomization.yaml`, `az aks update -g rg-ecommerce-k8s -n aks-ecommerce --attach-acr <acr>` |
| Pod `CrashLoopBackOff` trong vài phút đầu rồi tự hết | Ứng dụng lên trước MySQL/Kafka | Bình thường (mục 6.4) |
| Pod `CrashLoopBackOff` mãi | Lỗi thật | `kubectl logs <pod> --previous` để đọc lỗi khởi động |
| Log có `Access denied for user 'root'` | `SPRING_DATASOURCE_PASSWORD` khác `MYSQL_ROOT_PASSWORD`; hoặc đổi mật khẩu sau khi ổ đĩa MySQL đã được tạo | Hai giá trị phải giống nhau. Mật khẩu chỉ được đặt lúc tạo ổ đĩa lần đầu: đổi bên trong MySQL bằng `ALTER USER`, hoặc xóa PVC `data-mysql-0` (mất dữ liệu) |
| Pod `Pending` | Cluster hết RAM/CPU; hoặc PVC chưa có ổ đĩa | `kubectl describe pod` → Events. Tăng RAM cho Docker Desktop / thêm node; kiểm tra `kubectl get pvc` |
| Pod `OOMKilled` (thấy trong `describe`) | Vượt `limits.memory` | Tăng `limits.memory` của service đó (ví dụ 768Mi) |
| Auth Service lỗi `NumberFormatException: "tcp://10..."`; Kafka báo `port is deprecated`; Chat không nối được Mongo | Thiếu `enableServiceLinks: false` | Đã có sẵn trong manifest; nếu tự viết manifest mới phải thêm dòng này |
| Gateway trả `503` | Service đích chưa đăng ký Eureka hoặc đang khởi động | Chờ 1–2 phút; xem Eureka qua `port-forward` |
| Gateway trả `500`, log có `Connection refused` hoặc `UnknownHostException: <tên-pod>` | Service đăng ký Eureka bằng tên máy thay vì IP | Kiểm tra pod có biến `EUREKA_INSTANCE_PREFER_IP_ADDRESS=true` (`kubectl exec ... -- env`) |
| Inventory hoặc Order không có trên Eureka | Hai service này ghi cứng `localhost:8761` | Kiểm tra biến `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` đã vào pod |
| Đăng nhập lỗi, log Auth có `localhost:8088` | Chưa sửa code ở mục 4, hoặc chưa build lại image | Sửa, build, `rollout restart` |
| Sửa ConfigMap rồi mà không có tác dụng | Pod chưa khởi động lại | `kubectl rollout restart deployment -n ecommerce` |
| `elasticsearch-0` kẹt ở `Init` | Cluster không cho chạy container đặc quyền để đặt `vm.max_map_count` | Đặt tham số đó trên node bằng cách khác, hoặc tạm bỏ Search Service và Elasticsearch |
| Trang sản phẩm báo `429` | Hạn mức tần suất tính theo IP của cổng vào | Mục 4, dòng 5 |
| Ảnh sản phẩm mất sau khi pod khởi động lại | Pod không gắn PVC | Kiểm tra `kubectl get pvc product-uploads` ở trạng thái `Bound` |
| Chat rớt khi cập nhật `chat-service` | Service 1 bản, cập nhật kiểu `Recreate` | Đúng thiết kế hiện tại; FE tự kết nối lại. Muốn hết: mục 13.5 |
| AKS: `EXTERNAL-IP` của `caddy` cứ `<pending>` | Hết hạn mức IP công khai, hoặc nhãn DNS đã có người dùng | `kubectl describe svc caddy -n ecommerce` → Events |
| AKS: Caddy không xin được chứng chỉ | `DOMAIN` không khớp nhãn DNS; hoặc tên miền chưa kịp trỏ về IP | So `config-patch.yaml` với `caddy.yaml`; `kubectl logs deploy/caddy` |
| `kubectl` báo `connection refused` tới `localhost` hoặc `kubernetes.docker.internal` | Docker Desktop chưa chạy, hoặc đang ở sai context | Mở Docker Desktop; `kubectl config use-context ...` |

---

## 12. Bảo mật

1. **Chỉ một cổng vào.** Không đổi Service nào khác sang `NodePort`/`LoadBalancer`. Redis, MongoDB, Elasticsearch trong cấu hình này không có mật khẩu, và các service con không tự phân quyền.
2. **Secret không đưa lên git.** `k8s/.gitignore` đã chặn `base/secret.yaml`. Lưu ý Secret của Kubernetes mặc định chỉ được mã hóa base64 — ai đọc được Secret trong cluster là đọc được giá trị. Trên AKS, bước tiếp theo là lưu bí mật trong **Azure Key Vault** và nạp qua Secrets Store CSI Driver.
3. **Thay các bí mật đã lộ trong git** trước khi triển khai thật: mật khẩu ứng dụng Gmail, mã VNPay, khóa ký JWT, mật khẩu MySQL, token ngrok đang nằm trong repo.
4. **Khóa ký JWT** vẫn ghi cứng trong các file cấu hình của service. Muốn đổi theo môi trường: sửa các file đó thành `${JWT_SECRET:...}` rồi thêm `JWT_SECRET` vào Secret.
5. **Mỗi service chỉ nên thấy bí mật của mình.** Hiện mọi pod nạp toàn bộ Secret. Cải thiện: tách thành nhiều Secret (`mysql-secret`, `mail-secret`, `vnpay-secret`) và chỉ gắn vào service cần dùng.
6. **Chặn gọi tắt giữa các pod bằng NetworkPolicy.** Ví dụ chỉ cho `api-gateway` và các service gọi nhau, không cho pod lạ trong cluster gọi thẳng `order-service`. Cần bật network policy khi tạo AKS (`--network-policy azure` hoặc `cilium`).
7. **Giới hạn quyền truy cập cluster.** File kubeconfig tải về bằng `az aks get-credentials` có toàn quyền; không chia sẻ, không commit.
8. **Container đang chạy bằng quyền root** (theo `Dockerfile` hiện tại). Cải thiện: thêm người dùng không đặc quyền trong `Dockerfile` và `securityContext.runAsNonRoot: true`.

---

## 13. Hướng nâng cấp

Theo thứ tự nên làm:

### 13.1 Bỏ Eureka, dùng tên Service của Kubernetes

Kubernetes đã có sẵn việc mà Eureka làm: mỗi Service là một tên DNS ổn định, tự chia tải giữa các pod.

- Gateway: đổi `uri: lb://order-service` thành `uri: http://order-service:8084` cho mọi route.
- Feign client đang tìm theo tên Eureka: thêm `url = "${ORDER_SERVICE_URL:http://localhost:8084}"` (cùng kiểu đã làm ở mục 4).
- Tắt client Eureka (`EUREKA_CLIENT_ENABLED=false`) rồi gỡ `eureka-server`.

Lợi ích: bớt một thành phần, hết độ trễ đăng ký 1–2 phút, cập nhật không gián đoạn chính xác hơn vì K8s biết ngay pod nào đã sẵn sàng.

### 13.2 Thêm Actuator để kiểm tra sức khỏe thật

Thêm `spring-boot-starter-actuator` vào mỗi service, rồi đổi probe:

```yaml
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: 8084 }
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: 8084 }
```

Khi đó K8s chỉ gửi request tới pod khi ứng dụng đã nối được database, và phát hiện được ứng dụng treo. Nhớ không để `/actuator/**` lộ ra qua Gateway.

### 13.3 Tự động hóa build và triển khai (CI/CD)

GitHub Actions: mỗi lần đẩy code lên nhánh chính → build image của service có thay đổi → đẩy lên ACR với tag là mã commit → cập nhật `newTag` → `kubectl apply -k k8s/overlays/aks`. Bước tiếp theo là GitOps (Argo CD hoặc Flux): cluster tự đồng bộ theo nội dung thư mục `k8s/` trong git.

### 13.4 Chuyển dữ liệu sang dịch vụ quản lý

MySQL → Azure Database for MySQL Flexible Server; Redis → Azure Cache for Redis; MongoDB → Cosmos DB for MongoDB. Chỉ cần đổi các biến trong ConfigMap/Secret và gỡ StatefulSet tương ứng. Được sao lưu tự động và dự phòng thật sự.

### 13.5 Cho Chat Service chạy nhiều bản

Thay broker STOMP trong bộ nhớ bằng broker ngoài (RabbitMQ có bật STOMP) qua `enableStompBrokerRelay`. Khi đó các bản chat chia sẻ tin nhắn và có thể cập nhật không gián đoạn.

### 13.6 Lưu ảnh sản phẩm ngoài ổ đĩa

Chuyển ảnh sang Azure Blob Storage (hoặc PVC loại ReadWriteMany dùng Azure Files) để `product-service` chạy được nhiều bản. Job khuyến mãi ngày lễ cần thêm khóa phân tán (ví dụ ShedLock) để chỉ chạy ở một bản.

### 13.7 Cổng vào theo chuẩn Kubernetes

Tài liệu dùng Caddy cho đơn giản và giống cách triển khai trên VM. Chuẩn của Kubernetes cho việc này là **Gateway API** (kế nhiệm Ingress), cho phép chạy nhiều bản cổng vào và quản lý chứng chỉ bằng cert-manager. Lưu ý khi tìm hướng dẫn trên mạng: bộ điều khiển `ingress-nginx` rất phổ biến trong các bài viết cũ đã được cộng đồng Kubernetes thông báo ngừng phát triển; hãy chọn một bộ điều khiển còn được bảo trì và ưu tiên Gateway API.

### 13.8 Đóng gói bằng Helm

Khi cần triển khai nhiều môi trường với nhiều tham số khác nhau, chuyển 15 file service gần giống nhau thành một Helm chart dùng chung.

---

## 14. Danh sách kiểm tra

**Trước khi bắt đầu**

- [ ] Đã sửa 5 mục code ở phần 4 và chạy thử ở máy cá nhân
- [ ] Docker Desktop chạy được, đã bật Kubernetes, cấp ≥ 12 GB RAM

**Máy cá nhân**

- [ ] `./k8s/build-images.sh` xong, có đủ 16 image `ecommerce/*`
- [ ] Đã tạo `k8s/base/secret.yaml` từ file mẫu và áp dụng
- [ ] `kubectl apply -k k8s/overlays/local` chạy không lỗi
- [ ] Mọi pod `Running` và `1/1` sau khoảng 5 phút
- [ ] Eureka có đủ 15 service
- [ ] Đăng ký và đăng nhập qua `http://localhost:30085` thành công
- [ ] FE (`VITE_API_URL=http://localhost:30085`) duyệt sản phẩm, đặt hàng, chat được

**AKS**

- [ ] Đủ hạn mức vCPU; đã tạo ACR và cluster, `kubectl get nodes` thấy node `Ready`
- [ ] Đã đẩy 16 image với tag có phiên bản
- [ ] Đã sửa tên registry, tag, nhãn DNS, `DOMAIN`, `VNP_RETURNURL`, `FRONTEND_URL` trong `overlays/aks`
- [ ] Secret trên AKS dùng mật khẩu mạnh, khác máy cá nhân
- [ ] `https://<tên miền>/api/products/filter` trả dữ liệu với chứng chỉ hợp lệ
- [ ] Thử cập nhật 1 service và quay lui (mục 9)
- [ ] Đã thử sao lưu MySQL và MongoDB (mục 7)
- [ ] Biết cách `az aks stop` để ngừng tính tiền
