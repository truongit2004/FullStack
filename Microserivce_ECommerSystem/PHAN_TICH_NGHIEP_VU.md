# PHÂN TÍCH NGHIỆP VỤ HỆ THỐNG — MICROSERVICE E-COMMERCE

> Tài liệu được tổng hợp từ khảo sát trực tiếp mã nguồn tại `Microserivce_ECommerSystem/` (14 microservice + Eureka + API Gateway) và `FE/` (React SPA). Cập nhật: 2026-10-11.

> **Nhật ký cập nhật (2026-08-14):** Đã bổ sung một đợt tính năng còn thiếu theo mục 0 bên dưới: (1) đổi mật khẩu, cập nhật hồ sơ, quên mật khẩu qua OTP email; (2) trang Admin quản lý Users + CRUD Category; (3) gộp 2 cơ chế Holiday Sale trùng lặp trong Product Service thành 1; (4) phí vận chuyển tính động theo địa chỉ thay vì hard-code; (5) fix lỗi thiếu dependency `sockjs-client`/`stompjs` từng chặn build FE. Toàn bộ service Java liên quan đã build thành công (`mvn compile`) và FE đã build thành công (`npm run build`). Các dòng trạng thái đã cập nhật thành ✅ ở mục 0 là những gì vừa hoàn thành trong đợt này; các mục còn ❌/⚠️ vẫn là công việc còn lại.

> **Nhật ký cập nhật (2026-10-11):** Bổ sung **Point Service** (điểm thưởng tích lũy, port 8095) vào tài liệu — trước đó service đã có trong mã nguồn nhưng chưa được mô tả — và phần giao diện FE tương ứng: trang `/points` (số dư + lịch sử giao dịch điểm), ô dùng điểm trong `Checkout.jsx`, ô "Reward Points" trong `Profile.jsx`, link "Points" trên Navbar. Cùng ngày đã xử lý 3 lỗ hổng: (1) Cart Service chuyển tiếp `pointsToRedeem` sang Order Service; (2) Gateway phân quyền `/api/points/**` (chặn lệnh ghi từ bên ngoài, chỉ chủ sở hữu/ADMIN được xem); (3) số dư điểm không còn bị âm khi thu hồi. FE build thành công (`npm run build`); Cart Service, API Gateway, Point Service compile thành công (`mvn compile`); **chưa kiểm thử end-to-end** trên hệ thống đang chạy. Tiếp đó **đồng bộ quy tắc điểm về một nguồn**: tỉ lệ 10%/50% chỉ khai báo ở Point Service, FE đọc qua `GET /api/points/config`, Order Service bỏ hằng số riêng; đồng thời đổi cơ sở tích điểm từ `finalAmount` sang **giá trị gốc (tiền hàng)** của đơn. Order Service cũng đã compile thành công. Cuối ngày **hoàn thiện Chat Service**: sửa 2 lỗi khiến chat không hoạt động qua Gateway (Gateway chặn `/ws` vì thiếu token; tin không tới người nhận do sai cấu hình đích STOMP), thêm xác thực JWT ở STOMP, phân quyền lịch sử chat, màn hình hỗ trợ cho Admin, số tin chưa đọc. Gateway đổi CORS sang `allowedOriginPatterns` và thêm bộ lọc gộp header CORS trùng. Thêm 3 tài liệu hướng dẫn: đăng nhập Google, deploy Azure, tích hợp chatbot. Sau đó bổ sung **cơ chế chống lỗi lan truyền giữa các service** (mục 10): timeout, ngắt mạch và thử lại cho Feign, hàng đợi việc còn nợ ở Order Service, bảo đảm ghi nhận thanh toán ở Payment Service, thử lại và dead-letter cho Kafka, thử lại ở Gateway. Trong lúc làm phát hiện và sửa một lỗi có sẵn: đặt hàng **có dùng điểm** luôn lỗi 500 (danh sách sản phẩm của đơn là danh sách bất biến nên không lưu lại được lần hai). **(2026-10-12)** Đưa **Elasticsearch vào dùng thật cho ô tìm kiếm**: truy vấn mới xếp theo độ liên quan, không cần gõ dấu, chịu gõ sai nhẹ; đồng bộ chỉ mục khi tạo/sửa/xóa/đổi giá sản phẩm và đối soát định kỳ; FE gọi Search Service (tự quay về tìm bằng MySQL nếu Search Service lỗi). Cùng ngày thêm **mã giảm giá**: service mới Coupon Service (port 8096), ô nhập mã ở Checkout, trang quản lý mã cho Admin (mục 0.4b và 5.6).

## Mục lục

0. [Bảng trạng thái tính năng (đã làm / dở dang / chưa làm)](#0-bảng-trạng-thái-tính-năng)
1. [Tổng quan kiến trúc](#1-tổng-quan-kiến-trúc)
2. [Sơ đồ luồng liên service](#2-sơ-đồ-luồng-liên-service)
3. [Nhóm Hạ tầng & Định danh: Eureka, API Gateway, Auth Service, User Service](#3-nhóm-hạ-tầng--định-danh)
4. [Nhóm Catalog: Product, Inventory, Search, Review](#4-nhóm-catalog)
5. [Nhóm Đặt hàng/Thanh toán: Cart, Order, Payment, Shipping, Point, Coupon](#5-nhóm-đặt-hàngthanh-toán)
6. [Nhóm Giao tiếp: Notification, Chat](#6-nhóm-giao-tiếp)
7. [Frontend (FE React)](#7-frontend-fe-react)
8. [Luồng nghiệp vụ end-to-end](#8-luồng-nghiệp-vụ-end-to-end)
9. [Tổng hợp rủi ro & nợ kỹ thuật](#9-tổng-hợp-rủi-ro--nợ-kỹ-thuật)
10. [Chống lỗi lan truyền giữa các service](#10-chống-lỗi-lan-truyền-giữa-các-service)

---

## 0. Bảng trạng thái tính năng

Chú thích: ✅ Đã làm & hoạt động | ⚠️ Dở dang / hoạt động một phần / có lỗ hổng | ❌ Chưa làm (không thấy trong code)

### 0.1 Xác thực & Tài khoản

| Tính năng | Trạng thái | Ghi chú |
|---|---|---|
| Đăng ký tài khoản | ✅ | `Auth Service` → forward `User Service` |
| Đăng nhập (JWT) | ✅ | JWT HS256, sống cố định 24h |
| Đăng xuất (thu hồi token) | ✅ | Blacklist token trong Redis |
| Kiểm tra token hợp lệ | ✅ | `GET /auth/validate` |
| Refresh token | ❌ | Không có endpoint/luồng refresh, token hết hạn phải đăng nhập lại |
| Quên mật khẩu / OTP | ✅ *(mới)* | `POST /auth/forgot-password` sinh OTP 6 số lưu Redis (TTL 10 phút), gửi qua Notification Service (email); `POST /auth/reset-password` xác thực OTP và đổi mật khẩu |
| Đăng nhập mạng xã hội (Google/OAuth2) | ❌ | Không tìm thấy trong mã nguồn |
| Xem thông tin cá nhân | ✅ | `GET /api/users/{id}`, trang `Profile.jsx` |
| Cập nhật hồ sơ (đổi tên, email) | ✅ *(mới)* | `PUT /api/users/{id}` (User Service) + form "Edit Profile" trong `Profile.jsx`; có kiểm tra quyền sở hữu (chỉ tự sửa hồ sơ mình hoặc ADMIN) |
| Đổi mật khẩu | ✅ *(mới)* | `PUT /auth/change-password` (Auth Service, xác thực lại mật khẩu cũ trước khi đổi) + form trong `Profile.jsx` |
| Sổ địa chỉ giao hàng (nhiều địa chỉ) | ❌ | Chỉ nhập tay 1 địa chỉ mỗi lần checkout |
| Phân quyền ADMIN / USER | ✅ | Enforce tập trung ở API Gateway (`JwtGatewayFilter`) |
| Quản lý người dùng (Admin xem/xoá user) | ✅ *(mới)* | Thêm tab **Users** trong `Admin.jsx` (danh sách + xoá), dùng API `GET/DELETE /api/users` sẵn có |

### 0.2 Sản phẩm, danh mục, kho, tìm kiếm

| Tính năng | Trạng thái | Ghi chú |
|---|---|---|
| CRUD sản phẩm + upload ảnh | ✅ | `ProductService`, soft-delete & hard-delete |
| CRUD danh mục (backend) | ✅ | `CategoryController` đầy đủ |
| Quản lý danh mục trên giao diện Admin | ✅ *(mới)* | Thêm form thêm/sửa/xoá category (modal) trong `Admin.jsx`, dùng API `createCategory/updateCategory/deleteCategory` sẵn có ở backend |
| Lọc/tìm kiếm sản phẩm nâng cao | ✅ | Specification pattern (tên, giá, tồn kho, trạng thái) |
| Tìm kiếm full-text (Elasticsearch) | ✅ *(hoàn thiện 2026-10-12)* | Ô tìm kiếm của FE gọi `GET /api/search/products`. Tìm trên tên (ưu tiên) và mô tả, xếp theo độ liên quan, không cần gõ dấu, khớp từ đang gõ dở, chịu gõ sai ở từ ≥ 4 ký tự. Chỉ mục được cập nhật khi tạo/sửa/xóa/đổi giá sản phẩm (Kafka) và đối soát toàn bộ mỗi 30 phút. Đã test trên Elasticsearch thật (11 tình huống) |
| Khuyến mãi ngày lễ tự động | ✅ *(đã dọn dẹp)* | Đã gỡ bỏ `HolidaySaleScheduler` trùng lặp, chỉ còn `HolidayService` là cơ chế duy nhất (tránh 2 job cùng ghi đè giá) |
| Hệ thống sự kiện khuyến mãi (PromotionEvent) tuỳ chỉnh theo thời gian | ❌ | Có entity/repository nhưng **không có controller/service** thao tác — tính năng dang dở |
| Mã giảm giá / coupon | ✅ *(mới 2026-10-12)* | Coupon Service: mã theo % (có mức giảm tối đa) hoặc số tiền cố định, đơn tối thiểu, thời hạn, tổng lượt, lượt mỗi khách. Chi tiết ở mục 0.4b và 5.6 |
| Quản lý tồn kho tập trung, khoá khi trừ kho | ✅ | `InventoryService`, pessimistic lock, xử lý bulk chống deadlock |
| Giữ chỗ tồn kho có thời hạn (reserve/TTL) khi vào giỏ | ❌ | Chỉ có mô hình "trừ ngay khi đặt hàng – hoàn khi hủy/trả" |
| Đánh giá & rating sản phẩm (verified purchase) | ✅ | `ReviewService`, chỉ cho review khi đã mua & đã DELIVERED |
| Admin kiểm duyệt / xoá review vi phạm | ❌ | Không có quyền ADMIN riêng cho xoá review, chỉ chủ sở hữu review mới xoá được |
| Wishlist / sản phẩm yêu thích | ❌ | Không tìm thấy trong code |

### 0.3 Giỏ hàng, đặt hàng, thanh toán, vận chuyển

| Tính năng | Trạng thái | Ghi chú |
|---|---|---|
| Giỏ hàng (thêm/sửa/xoá, snapshot giá) | ✅ | `CartService`, lưu Redis TTL 7 ngày |
| Đặt hàng từ giỏ hàng (checkout) | ✅ | `Cart → Order`, trừ kho thật trong transaction |
| Vòng đời trạng thái đơn hàng đầy đủ | ✅ | PENDING→CONFIRMED→SHIPPING→DELIVERED, CANCELLED, PAID, REFUNDED, RETURNED |
| Thanh toán online qua VNPay | ✅ | Tạo URL, verify chữ ký callback (HMAC-SHA512) |
| Thanh toán Momo | ❌ | Không tìm thấy trong code |
| Thanh toán khi nhận hàng (COD) | ⚠️ | Không thấy luồng COD tường minh — nếu khách không thanh toán online, đơn vẫn ở trạng thái chưa PAID nhưng vẫn có thể ship/deliver thủ công |
| Hoàn tiền thật qua cổng thanh toán | ❌ | `refundOrder`/consumer `ORDER_RETURNED` chỉ đổi trạng thái DB nội bộ, **không gọi API hoàn tiền VNPay thật** |
| Tự động hoàn tiền khi huỷ đơn đã thanh toán | ❌ | Chỉ log cảnh báo, cần xử lý thủ công |
| Tạo & theo dõi vận đơn | ✅ | `ShippingService`, đồng bộ 2 chiều với Order |
| Tích hợp hãng vận chuyển thật (GHN/GHTK/VNPost) | ❌ | Carrier chỉ là chuỗi tự do, mã vận đơn tự sinh nội bộ |
| Tính phí ship động theo địa chỉ | ✅ *(mới)* | `GET /api/shipping/fee?address=` (Shipping Service) tính phí theo từ khoá địa danh (nội thành/tỉnh lân cận/tỉnh khác); Order Service gọi API này khi tạo đơn thay vì tin tưởng giá trị client gửi lên (có fallback nếu Shipping Service lỗi). Đây là quy tắc đơn giản hoá bằng từ khoá, **chưa dùng API định vị/khoảng cách thật**; thời gian giao hàng vẫn cố định +3 ngày |
| Thống kê doanh thu/đơn hàng (Admin dashboard) | ✅ | `GET /api/orders/stats`, `/stats/revenue`, `/stats/best-selling` |

### 0.4 Điểm thưởng tích lũy (Point Service)

| Tính năng | Trạng thái | Ghi chú |
|---|---|---|
| Tự động cộng điểm khi đơn DELIVERED | ✅ | Order Service publish Kafka `order-delivered-topic` → Point Service cộng 10% **giá trị gốc (tiền hàng, `totalAmount`)** của đơn, làm tròn xuống, idempotent theo `orderId` |
| Dùng điểm giảm giá khi đặt hàng (backend Order ↔ Point) | ✅ | `OrderRequestDTO.pointsToRedeem`, 1 điểm = 1 đơn vị tiền, tối đa 50% tiền hàng (để không có đơn 0đ); Order Service gọi Feign `POST /api/points/redeem`, Point Service kiểm tra giới hạn và số dư |
| Dùng điểm qua luồng checkout giỏ hàng (end-to-end) | ✅ *(đã fix)* | FE gửi `pointsToRedeem` lên `POST /api/cart/checkout`; Cart Service chuyển tiếp sang Order Service (`CheckoutRequest.pointsToRedeem`, tuỳ chọn). Chưa kiểm thử end-to-end |
| Hoàn điểm đã dùng / thu hồi điểm đã tích khi hủy, trả hàng | ✅ | `cancelOrder`, `returnOrder` gọi `POST /api/points/{orderId}/reverse` (lỗi chỉ log, không chặn) |
| Thu hồi điểm khi hoàn tiền (`refundOrder`) | ❌ | DELIVERED → REFUNDED không gọi reverse, khách giữ nguyên điểm đã tích |
| Xem số dư & lịch sử điểm (FE) | ✅ *(mới)* | Trang `Points.jsx` (`/points`), ô "Reward Points" trong `Profile.jsx` |
| Nhập điểm muốn dùng ở Checkout (FE) | ✅ *(mới)* | `Checkout.jsx`: ô nhập điểm, nút "Dùng tối đa", kiểm tra giới hạn, bảng tạm tính; tỉ lệ lấy từ `GET /api/points/config` |
| Admin xem điểm của khách hàng | ⚠️ | `/points?userId=<id>` hoạt động với role ADMIN nhưng chưa có nút dẫn tới từ tab Users trong `Admin.jsx` |
| Phân quyền theo chủ sở hữu cho API điểm | ✅ *(mới)* | `JwtGatewayFilter`: mọi lệnh không phải GET tới `/api/points/**` bị chặn 403 (kể cả ADMIN); GET `/api/points/{userId}[/history]` chỉ cho chủ sở hữu hoặc ADMIN. Gọi thẳng port 8095 (bỏ qua Gateway) vẫn không có phân quyền |
| Số dư điểm không bị âm | ✅ *(mới)* | `redeem` từ chối khi thiếu điểm; thu hồi (`REVERSE_EARN`) chỉ lấy tối đa bằng số dư hiện có |
| Quy tắc điểm cấu hình ở một nơi duy nhất | ✅ *(mới)* | Tỉ lệ 10%/50% chỉ khai báo ở `point.*` của Point Service; Order Service không còn hằng số riêng, FE đọc qua `GET /api/points/config` |
| Kiểm thử end-to-end luồng điểm trên hệ thống đang chạy | ❌ | Mới dừng ở mức compile (`mvn compile`) và build FE (`npm run build`) thành công |
| Hạn sử dụng điểm, hạng thành viên | ❌ | Không tìm thấy trong code |

**Tổng kết đợt làm việc 2026-10-11 (điểm thưởng):**

Đã xong:

- [x] FE: trang điểm thưởng `/points` (số dư, tổng đã tích/đã dùng, quy tắc, lịch sử có lọc)
- [x] FE: ô dùng điểm + bảng tạm tính trong `Checkout.jsx`
- [x] FE: ô "Reward Points" trong `Profile.jsx`, link "Points" trên Navbar, route `/points`
- [x] Cart Service chuyển tiếp `pointsToRedeem` sang Order Service (rủi ro 13)
- [x] Gateway phân quyền `/api/points/**`: chặn lệnh ghi từ bên ngoài, chỉ chủ sở hữu/ADMIN xem số dư và lịch sử (rủi ro 14)
- [x] Số dư điểm không bị âm khi thu hồi (rủi ro 15b)
- [x] Đồng bộ quy tắc điểm về một nguồn cấu hình + API `GET /api/points/config` (rủi ro 16)
- [x] Tích điểm 10% tính trên giá trị gốc (tiền hàng) thay vì số tiền cuối cùng
- [x] Bổ sung Point Service vào tài liệu này (mục 0.4, 1, 2, 3.2, 5.2, 5.5, 7, 8, 9)

Chưa xong:

- [ ] Kiểm thử end-to-end trên hệ thống đang chạy (cần khởi động lại Point, Order, Cart Service và Gateway)
- [ ] Nút xem điểm khách hàng trong tab Users của `Admin.jsx` (trang `/points?userId=<id>` đã sẵn sàng)
- [ ] `refundOrder` chưa thu hồi điểm đã tích (rủi ro 15c)
- [ ] Retry khi cộng/đảo ngược điểm thất bại (rủi ro 15a)
- [ ] Khóa chống ghi đè số dư khi 2 request đồng thời; unique constraint `orderId` + `type` (rủi ro 15d, 15e)
- [ ] Point Service vẫn `permitAll()` nếu gọi thẳng port 8095, bỏ qua Gateway (rủi ro 14, phần còn lại)
- [ ] Thống nhất đơn vị tiền `$` / VND giữa các trang (rủi ro 16, phần còn lại)
- [ ] Hạn sử dụng điểm, hạng thành viên

### 0.4b Mã giảm giá (Coupon Service)

| Tính năng | Trạng thái | Ghi chú |
|---|---|---|
| Admin tạo / sửa / tạm ngừng mã | ✅ | Trang `/admin/coupons`. Mã đã có người dùng thì không đổi được chuỗi mã, kiểu giảm, giá trị giảm |
| Khách xem trước mã ở Checkout | ✅ | `GET /api/coupons/preview` — chỉ kiểm tra và tính, chưa trừ lượt; báo đúng lý do khi mã không dùng được |
| Gợi ý các mã đang dùng được | ✅ | `GET /api/coupons/available`, hiện dạng nút bấm ở Checkout |
| Trừ lượt khi đặt hàng | ✅ | Order Service gọi `POST /api/coupons/redeem`; chống trừ trùng theo `orderId`; khóa dòng để nhiều khách tranh lượt cuối không vượt giới hạn |
| **Quy tắc 1**: mã không dùng chung với điểm thưởng | ✅ | Order Service từ chối đơn gửi cả hai; FE khóa ô còn lại khi đã chọn một |
| **Quy tắc 2**: hủy đơn / trả hàng thì hoàn mã | ✅ | Trả lại lượt cho mã và cho khách; Coupon Service chết thì tự hoàn lại sau (hàng đợi việc còn nợ) |
| **Quy tắc 3**: đơn có dùng mã không được tích điểm | ✅ | `deliverOrder` không phát sự kiện cộng điểm khi đơn có `couponCode` |
| Mã theo danh mục / sản phẩm, mã miễn phí vận chuyển, nhiều mã một đơn | ❌ | Mã hiện áp cho toàn bộ tiền hàng; mỗi đơn một mã |
| Mã riêng cho từng khách, mã tặng tự động | ❌ | Chưa làm |

### 0.5 Thông báo & Chat

| Tính năng | Trạng thái | Ghi chú |
|---|---|---|
| Gửi email tự động (đặt hàng, thanh toán, hoàn trả) | ✅ | `NotificationService`, template Thymeleaf, SMTP Gmail |
| Thông báo qua SMS | ❌ | Không tìm thấy trong code |
| Push notification / thông báo trong app | ❌ | Không tìm thấy trong code |
| Retry / dead-letter khi gửi email thất bại | ❌ | Chỉ log cảnh báo, không thử lại |
| Chat real-time khách hàng ↔ bộ phận hỗ trợ | ✅ *(hoàn thiện 2026-10-11)* | WebSocket/STOMP qua Gateway, lưu MongoDB. Khách nhắn cho bộ phận hỗ trợ (`admin-support`), mọi ADMIN cùng thấy và trả lời. Đã kiểm thử tự động: 27 tình huống gọi thẳng Chat Service + 16 tình huống qua Gateway bằng đúng thư viện FE dùng; chưa thử bằng tay trên trình duyệt |
| Xác thực/phân quyền tại tầng WebSocket | ✅ *(mới)* | JWT gửi trong frame STOMP CONNECT, kiểm tra ở `StompAuthChannelInterceptor`; người gửi lấy từ token (không mạo danh được); kênh `/topic/support` chỉ ADMIN nghe được. Chưa kiểm tra blacklist token đã logout |
| Giao diện Admin trả lời chat khách hàng | ✅ *(mới)* | Trang `/admin/chat` (`AdminChat.jsx`): danh sách hội thoại, số tin chưa đọc, tìm kiếm, trả lời tức thời. Vào từ link "Support" trên Navbar |
| Lịch sử chat chỉ chủ hội thoại hoặc ADMIN xem được | ✅ *(mới)* | Trước đây ai đăng nhập cũng đọc được hội thoại của người khác |
| Số tin chưa đọc, đánh dấu đã đọc | ✅ *(mới)* | Huy hiệu trên nút chat của khách và trên danh sách hội thoại của Admin |
| Chatbot AI trả lời tự động | ❌ | Chưa cài. Đã có tài liệu hướng dẫn: `HUONG_DAN_TICH_HOP_CHATBOT.md` |
| Gửi ảnh/file, phân trang lịch sử, thông báo "đang nhập" | ❌ | Chưa làm |
| Thư viện chat FE khai báo đầy đủ trong `package.json` | ✅ *(đã fix)* | Đã cài đặt và khai báo `sockjs-client`/`stompjs` vào `package.json`; `npm run build` chạy thành công trở lại (trước đây bị chặn hoàn toàn) |

### 0.6 Frontend tổng thể

| Tính năng | Trạng thái | Ghi chú |
|---|---|---|
| Trang danh sách/chi tiết sản phẩm, tìm kiếm, lọc, sắp xếp | ✅ | `Products.jsx`, `ProductDetail.jsx` |
| Giỏ hàng, Checkout, lịch sử đơn hàng | ✅ | `Cart.jsx`, `Checkout.jsx`, `OrderHistory.jsx` |
| Trang quản trị Admin: Dashboard, Products, Orders | ✅ | `Admin.jsx` |
| Trang quản trị Admin: Categories (CRUD đầy đủ) | ✅ *(mới)* | Đã có form thêm/sửa/xoá dạng modal |
| Trang quản trị Admin: Users | ✅ *(mới)* | Đã có tab danh sách + xoá người dùng (không cho tự xoá chính mình) |
| Trang cá nhân + đổi thông tin/mật khẩu | ✅ *(mới)* | Modal "Edit Profile" cho phép cập nhật họ tên/email và đổi mật khẩu (yêu cầu nhập đúng mật khẩu hiện tại) |
| Trang quên mật khẩu | ✅ *(mới)* | `ForgotPassword.jsx` — luồng 2 bước: gửi OTP qua email, sau đó nhập OTP + mật khẩu mới |
| Trang điểm thưởng + dùng điểm ở Checkout | ✅ *(mới 2026-10-11)* | `Points.jsx`, `PointService.js`, bổ sung `Checkout.jsx`/`Profile.jsx`/`Navbar.jsx` — chi tiết ở mục 0.4 và mục 7 |



---

## 1. Tổng quan kiến trúc

Hệ thống gồm **14 microservice Spring Boot**, 1 **Eureka Server** (service discovery) và 1 **API Gateway** (Spring Cloud Gateway) làm cổng vào duy nhất, phục vụ 1 **frontend React SPA** (`FE/`).

| Service | Port | Database | Message broker | Vai trò chính |
|---|---|---|---|---|
| Eureka Server | 8761 | – | – | Service discovery, "danh bạ" cho toàn hệ thống |
| API Gateway | 8085 | – | Redis (rate limit) | Cổng vào duy nhất: routing, xác thực JWT tập trung, phân quyền, CORS, rate limit |
| Auth Service | 8087 | – (stateless) | – | Đăng ký/đăng nhập, sinh & thu hồi JWT |
| User Service | 8088 | MySQL `user_service` | – | Nguồn dữ liệu tài khoản, xác thực credentials cho Auth Service, cache Redis |
| Product Service | 8082 | MySQL `product_service` | Kafka (producer + consumer) | Quản lý sản phẩm, danh mục, khuyến mãi, giá |
| Inventory Service | 8086 | MySQL `inventory_service` | – | Quản lý tồn kho tập trung (pessimistic lock) |
| Search Service | 8094 | Elasticsearch | Kafka (consumer) | Tìm kiếm sản phẩm full-text |
| Review Service | 8091 | MySQL `review_service` | Kafka (producer) | Đánh giá/rating sản phẩm (verified purchase) |
| Cart Service | 8083 | Redis (không có RDBMS) | – | Giỏ hàng tạm thời |
| Order Service | 8084 | MySQL `order_db` | Kafka (producer) | Vòng đời đơn hàng, trừ/hoàn kho, điều phối trạng thái |
| Payment Service | 8089 | MySQL `payment_service` | Kafka (producer + consumer) | Thanh toán VNPay, xác nhận/webhook |
| Shipping Service | 8093 | MySQL `shipping_service` | – | Vận đơn, đồng bộ trạng thái giao hàng với Order |
| Point Service | 8095 | MySQL `point_service` | Kafka (consumer) | Điểm thưởng tích lũy: cộng điểm khi đơn giao thành công, dùng điểm giảm giá, hoàn/thu hồi khi hủy/trả |
| Coupon Service | 8096 | MySQL `coupon_service` | – | Mã giảm giá: định nghĩa mã, kiểm tra điều kiện, trừ và hoàn lượt dùng |
| Notification Service | 8090 | – (stateless) | Kafka (consumer) | Gửi email tự động (SMTP Gmail) |
| Chat Service | 8092 | MongoDB `chat_service_db` | – | Chat real-time (WebSocket/STOMP) khách hàng ↔ hỗ trợ |
| Frontend (FE) | 5173 (dev) | – | – | SPA React, gọi mọi API qua Gateway (`:8085`) |

**Công nghệ giao tiếp:**
- Đồng bộ: REST qua **OpenFeign** (đa số) hoặc **RestTemplate** (một số nơi hard-code URL thay vì qua Eureka).
- Bất đồng bộ: **Kafka**, các topic chính: `product-topic`, `product-rating-topic`, `order-placed-topic`, `payment-success-topic`, `order-delivered-topic` (riêng cho việc cộng điểm).
- Xác thực: **JWT (HS256)**, secret dùng chung giữa Auth Service, User Service, API Gateway (và Point Service). Token sống cố định **24h**, **không có refresh token**.

---

## 2. Sơ đồ luồng liên service

```
                                   ┌─────────────┐
                                   │   Eureka    │  (service discovery, :8761)
                                   └──────┬──────┘
                                          │ đăng ký/tra cứu
        ┌─────────────────────────────────┼─────────────────────────────────┐
        │                                 │                                 │
 [Frontend React :5173] ───────▶ [API Gateway :8085] ───(routing/JWT/role/CORS/rate-limit)──▶ tất cả service bên dưới
        │
        │                        ┌─────────────────────────────────────────────┐
        │                        │              NHÓM ĐỊNH DANH                  │
        └───/auth/**────────────▶│  Auth Service (:8087) ──Feign──▶ User Service │
             /api/users/**       │  User Service (:8088) — MySQL, cache Redis    │
                                  └─────────────────────────────────────────────┘

┌───────────────────────────── NHÓM CATALOG ─────────────────────────────┐
│  Product Service (:8082) ──Feign──▶ Inventory Service (:8086)          │
│        │  └─Kafka(product-topic)──▶ Search Service (:8094, ES)         │
│        │  ◀─Kafka(product-rating-topic)── Review Service (:8091)       │
│  Review Service ──Feign──▶ Product Service (check tồn tại)             │
│  Review Service ──Feign──▶ Order Service (check đã mua & DELIVERED)    │
└──────────────────────────────────────────────────────────────────────┘

┌────────────────────── NHÓM ĐẶT HÀNG / THANH TOÁN ──────────────────────┐
│ Cart(:8083,Redis) ──▶ Order(:8084) ──Feign──▶ Inventory (trừ/hoàn kho) │
│                          │  ├─Feign─▶ Product (validate/giá)           │
│                          │  ├─Feign─▶ User (validate)                  │
│                          │  ├─Feign─▶ Shipping (tạo vận đơn khi ship)  │
│                          │  └─Kafka(order-placed-topic) publish         │
│ Payment(:8089) ──Feign──▶ Order (markOrderAsPaid)                      │
│         └─Kafka(payment-success-topic) publish                         │
│         └─Kafka(order-placed-topic) consume (chỉ xử lý ORDER_RETURNED) │
│ Shipping(:8093) ──Feign──▶ Order (đồng bộ ngược trạng thái ship)       │
│ Order ──Feign──▶ Point(:8095) (redeem khi tạo đơn, reverse khi hủy/trả)│
│ Order ──Kafka(order-delivered-topic)──▶ Point (cộng điểm khi DELIVERED)│
└──────────────────────────────────────────────────────────────────────┘

┌────────────────────────── NHÓM GIAO TIẾP ───────────────────────────────┐
│ Notification(:8090) ◀─Kafka(order-placed-topic, payment-success-topic)  │
│      └─Feign─▶ Order, User (làm giàu dữ liệu) ──▶ gửi Email (SMTP Gmail)│
│ Chat(:8092, MongoDB) — độc lập, WebSocket/STOMP, không Kafka            │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Nhóm Hạ tầng & Định danh

### 3.1 Eureka Server (`Eureka/Eureka`, port 8761)

Service Discovery Server (Netflix Eureka) — "danh bạ" trung tâm để mọi service đăng ký và tra cứu địa chỉ IP:port của nhau qua tên (dùng trong `lb://SERVICE-NAME` ở Gateway hoặc `@FeignClient(name=...)`). Bản thân không đăng ký chính nó, không có nghiệp vụ. **Tắt self-preservation mode** — phù hợp dev/test, cần cân nhắc lại khi lên production vì dễ mất instance khi mạng chập chờn.

### 3.2 API Gateway (`API Gateway/API-Gateway`, port 8085)

**Vai trò:** Điểm vào duy nhất của toàn hệ thống, là **lớp bảo mật quan trọng nhất** vì phần lớn service downstream không tự enforce security chặt.

**Routing chính** (qua Eureka load-balancer `lb://`):

| Route | Đích |
|---|---|
| `/api/cart/**` | CartService |
| `/api/products/**`, `/api/categories/**` | PRODUCT-SERVICE (route GET có rate-limit riêng: 1 req/s, burst 1, theo IP) |
| `/api/orders/**` | order-service |
| `/api/users/**` | user-service |
| `/auth/**` | auth-service |
| `/api/inventory/**` | inventory-service |
| `/api/payment/**` | payment-service |
| `/ws/**`, `/api/chat/**` | CHAT-SERVICE (`/ws/**` đi qua không cần token — Chat Service tự xác thực JWT ở STOMP CONNECT) |
| `/api/reviews/**` | review-service |
| `/api/shipping/**` | shipping-service |
| `/api/notifications/**` | notification-service |
| `/api/search/**` | search-service |
| `/api/points/**` | point-service (chỉ GET; số dư/lịch sử cho chủ sở hữu hoặc ADMIN, `/config` cho mọi user đã đăng nhập) |
| `/api/coupons/**` | coupon-service (`/preview`, `/available`: khách đã đăng nhập; `/redeem`, `/{orderId}/reverse`: chặn từ bên ngoài; còn lại: ADMIN) |

**`JwtGatewayFilter` (GlobalFilter áp dụng mọi route)** — logic bảo mật cốt lõi:
1. Public hoàn toàn: `/auth/**`, `/api/auth/**`, `POST /api/users` (đăng ký).
2. Cho xem tự do (GET only, không cần token): `/api/products`, `/api/categories`, `/api/reviews`.
3. Còn lại **bắt buộc JWT hợp lệ** (401 nếu thiếu/hỏng).
4. Phân quyền cụ thể theo nghiệp vụ:
   - `GET /api/orders`, `/api/orders/filter` → chỉ ADMIN.
   - `POST/PUT/DELETE /api/products`, `/api/categories` → chỉ ADMIN.
   - `DELETE /api/cart/{userId}` → chỉ chủ sở hữu hoặc ADMIN.
5. Gắn header `X-User-Id`, `X-User-Name`, `X-User-Role` vào request forward xuống service con — **đây là cách các service downstream (Cart, Order, Shipping...) nhận diện danh tính người dùng mà không tự verify JWT lại.**

CORS mở toàn bộ (`allowedOrigins: "*"`, `allowCredentials: true`). Rate limiter dùng Redis, key theo IP nguồn.

### 3.3 Auth Service (`Auth Service/Auth-Service`, port 8087)

**Vai trò:** Cổng xác thực tập trung, **stateless** — không tự lưu dữ liệu người dùng, mọi thao tác tài khoản ủy quyền sang User Service qua Feign. Chỉ dùng **Redis** để lưu blacklist token khi logout.

> Đã xác nhận qua khảo sát mã nguồn: hệ thống **không có** refresh token, OTP, quên mật khẩu, hay OAuth2/Google login.

**API chính:**

| Method | Path | Mục đích |
|---|---|---|
| POST | `/auth/register` | Forward tạo user sang User Service |
| POST | `/auth/login` | Xác thực username/password (qua User Service), sinh JWT (24h, HS256, chứa userId/username/role) |
| GET | `/auth/validate?token=` | Kiểm tra token còn hợp lệ & không trong blacklist |
| POST | `/auth/logout` | Đưa token vào Redis blacklist với TTL = thời gian còn lại của token |
| GET | `/proxy/products`, `/proxy/products/{id}`, `/proxy/products/all` | Proxy forward sang Product Service (dành cho client cũ) |
| GET | `/proxy/orders`, `/proxy/orders/{id}`, `/proxy/orders/user/{userId}` | Proxy forward sang Order Service (dành ADMIN) |

**Luồng đăng ký → đăng nhập → cấp token:**
1. `POST /auth/register` → forward `UserDto` sang User Service tạo tài khoản (User Service tự băm BCrypt, gán role mặc định `USER`).
2. `POST /auth/login` → `AuthenticationManager` gọi User Service lấy user + password hash, so khớp BCrypt.
3. Nếu hợp lệ → `JwtService.generateToken(userId, username, role)` sinh JWT 24h.
4. Trả `AuthResponse{token, message}`.

**Logout (thu hồi token):** tính TTL còn lại của token → ghi khóa `blacklist:<token>` vào Redis → token bị vô hiệu ngay cả khi chưa hết hạn tự nhiên.

### 3.4 User Service (`User Service/User-Service`, port 8088)

**Vai trò:** Nguồn dữ liệu chủ (source of truth) cho tài khoản, lưu MySQL. Entity `User` chỉ gồm trường cơ bản (username, email, password hash, firstName, lastName, role) — **chưa có profile mở rộng** (địa chỉ, avatar).

**API chính** (base `/api/users`):

| Method | Path | Mục đích |
|---|---|---|
| POST | `/api/users` | Tạo user (check trùng username/email, băm BCrypt) |
| GET | `/api/users/{id}` | Lấy user theo ID (cache Redis) |
| GET | `/api/users/{id}/profile-full` | Hồ sơ đầy đủ: user + lịch sử đơn hàng (gọi Order Service) + chi tiết sản phẩm từng item (gọi Product Service) — **dùng RestTemplate hard-code URL `localhost:8084`/`localhost:8082`, không qua Eureka/Gateway** |
| GET | `/api/users/email/{email}` | Lấy theo email (cache Redis) |
| GET | `/api/users` | Danh sách phân trang |
| DELETE | `/api/users/{id}` | Xóa user (evict cache) |
| GET | `/api/users/username/{username}/auth` | Endpoint nội bộ cho Auth Service — trả `AuthUserDto` kèm password hash |

**Cache (CacheConfig — vừa thêm mới):** Redis Cache TTL **60 phút**, áp dụng cho `getUserById`, `getUserByEmail`, `getAuthUserByUsername` — giảm tải MySQL cho truy vấn đọc lặp lại (đặc biệt login).

**Bảo mật:** Auth Service, User Service, API Gateway dùng **chung 1 JWT secret**. Cả Auth và User Service hiện `permitAll()` toàn bộ ở tầng Spring Security nội bộ — phân quyền role thực chất tập trung 100% ở API Gateway; nếu gọi thẳng vào port service (bỏ qua Gateway) sẽ không có phân quyền.

---

## 4. Nhóm Catalog

### 4.1 Product Service (`ProductService`, port 8082)

**Vai trò:** Nguồn sự thật về sản phẩm & danh mục. **Không tự lưu `stockQuantity`** — tồn kho ủy quyền hoàn toàn cho Inventory Service, chỉ "enrich" vào response khi trả về.

**API chính** (`/api/products`, `/api/categories`):

| Method | Path | Mục đích |
|---|---|---|
| POST | `/api/products` (multipart) | Tạo sản phẩm + ảnh, tự tạo Inventory tương ứng, publish Kafka `product-topic` |
| GET | `/api/products/filter` | Lọc/tìm kiếm có phân trang (Specification pattern: tên, category, khoảng giá, khoảng tồn kho, trạng thái) |
| GET | `/api/products/{id}` | Chi tiết (chỉ sản phẩm chưa xóa), enrich stock |
| PUT | `/api/products/{id}` | Cập nhật (có thể đổi ảnh) |
| DELETE | `/api/products/soft/{id}` | Xóa mềm (giữ dữ liệu, set `INACTIVE`) |
| DELETE | `/api/products/hard/{id}` | Xóa cứng + xóa file ảnh |
| GET | `/api/products/all` | Tất cả kể cả đã xóa mềm (dùng để SearchService đồng bộ thủ công) |
| PUT | `/api/products/{id}/stock/decrease`, `/increase` | Proxy sang Inventory Service |
| CRUD | `/api/categories` | Quản lý danh mục |

**Model:** `ProductEntity` (giá, `originalPrice`, `isOnSale`, `discountPercentage`, `activeEventId`, category, soft-delete, `productStatus: ACTIVE/INACTIVE/OUT_OF_STOCK/DRAFT`, `averageRating`, `reviewCount`).

**Nghiệp vụ đáng chú ý:**
- **Khuyến mãi ngày lễ tự động**: có **2 cơ chế song song trùng lặp** (`HolidayService` + `HolidaySaleScheduler`, đều chạy cron 0h hằng ngày) tự giảm giá 30% toàn bộ sản phẩm vào các ngày lễ VN cố định (Tết dương, 30/4, 1/5, Quốc khánh, Giáng sinh) và khôi phục giá gốc khi hết lễ.
- **Cập nhật rating**: consumer Kafka `product-rating-topic` (từ Review Service) — tính lại `averageRating` theo trung bình cộng gia quyền, tăng `reviewCount`.
- Bảo mật: `permitAll()` toàn bộ ở tầng Spring Security nội bộ (phân quyền dồn về Gateway).

### 4.2 Inventory Service (`InventoryService`, port 8086)

**Vai trò:** Quản lý tập trung số lượng tồn kho cho từng `productId`, đảm bảo nhất quán bằng **pessimistic lock DB** (timeout 3s) khi trừ kho đồng thời.

**API chính** (`/api/inventory`): `GET /{productId}`, `POST /decrease`, `/decrease-bulk`, `/increase`, `/increase-bulk`, `/create`.

**Nghiệp vụ:**
- Trừ kho không đủ → `InsufficientStockException` (400).
- Xử lý bulk: sắp xếp item theo `productId` trước khi xử lý tuần tự — **kỹ thuật chống deadlock**.
- **Không có cơ chế "reserve" (giữ chỗ có TTL)** — mô hình thực tế là "trừ kho ngay khi tạo đơn, hoàn kho khi hủy/trả" (do Order Service điều phối), không phải giữ chỗ 2 giai đoạn.

### 4.3 Search Service (`SearchService`, port 8094)

**Vai trò:** Tìm kiếm sản phẩm tốc độ cao trên Elasticsearch — "read model" đồng bộ gần-real-time từ Product Service qua Kafka.

**API chính** (`/api/search`): `GET /products?query=&page=&size=` (công khai, trả danh sách đã xếp theo độ liên quan), `GET /all` (phân trang), `GET /sync` (nạp lại toàn bộ từ Product Service — Gateway chỉ cho ADMIN).

**Chỉ mục `products_v2`** (tên đổi được qua `search.index-name`): lưu id, tên, mô tả, giá, giá gốc, cờ giảm giá, danh mục, ảnh, trạng thái. Chỉ sản phẩm `ACTIVE` được lập chỉ mục. Không lưu tồn kho (thay đổi liên tục). Tên và mô tả dùng bộ phân tích `vi_text` (tách từ → viết thường → bỏ dấu).

**Cách tìm** (`ProductSearchService`): 3 kiểu khớp gộp lại, đều yêu cầu đủ các từ — khớp nguyên từ (điểm cao nhất), khớp tiền tố cho từ cuối đang gõ dở, và khớp gần đúng cho từ ≥ 4 ký tự (chịu sai 1 ký tự, ≥ 8 ký tự chịu sai 2). Tên được nhân hệ số 3 so với mô tả.

**Đồng bộ** (`ProductIndexSyncService`): (1) Product Service phát sự kiện lên `product-topic` sau khi transaction commit, cho tạo, sửa, xóa mềm, xóa cứng và đổi giá ngày lễ (`ProductSearchPublisher`); sự kiện xóa có dạng `{"id": ..., "deleted": true}`. (2) Đối soát toàn bộ lúc khởi động nếu chỉ mục trống, mỗi 30 phút, và khi gọi `/sync`: thêm/cập nhật sản phẩm đang bán, gỡ sản phẩm không còn.

**Hạn chế còn lại:** thay đổi điểm đánh giá và tên danh mục không phát sự kiện (được cập nhật ở lần đối soát kế tiếp, tối đa 30 phút); tìm gần đúng không áp dụng cho từ dưới 4 ký tự; chưa lọc theo danh mục/khoảng giá và chưa gợi ý từ khóa ngay trong Search Service (FE vẫn lọc danh mục và sắp xếp ở trình duyệt); index cũ `products` không còn dùng, có thể xóa.

### 4.4 Review Service (`ReviewService`, port 8091)

**Vai trò:** Đánh giá/rating sản phẩm với ràng buộc nghiệp vụ chặt: **chỉ cho phép review khi đã mua và đơn hàng đã DELIVERED** (verified purchase).

**API chính** (`/api/reviews`): `POST /` (auth bắt buộc), `GET /product/{id}`, `GET /user/{id}` (public), `DELETE /{id}` (chỉ chủ sở hữu).

**Luồng tạo review (`createReview`) — quan trọng nhất:**
1. Đối chiếu `userId` trong token với `userId` trong request (chống mạo danh).
2. Gọi Feign `ProductService` xác nhận sản phẩm tồn tại.
3. Gọi Feign `OrderService.checkPurchased(userId, productId)` — **chặn nếu chưa mua/chưa giao hàng thành công**.
4. Chống review trùng (mỗi user 1 review/sản phẩm).
5. Lưu review, publish Kafka `product-rating-topic` bất đồng bộ (`CompletableFuture`, không block response) để Product Service cập nhật điểm trung bình.

---

## 5. Nhóm Đặt hàng/Thanh toán

### 5.1 Cart Service (`CartService`, port 8083)

**Vai trò:** Giỏ hàng tạm thời, lưu **100% trong Redis** (key `cart:{userId}`, TTL 7 ngày mặc định), không có RDBMS.

**API chính** (`/api/cart`): `GET /` (đồng bộ lại giá mới nhất từ Product Service), `POST /items`, `PUT /items/{productId}`, `DELETE /items/{productId}` (giảm 1 hoặc xóa hẳn), `DELETE /` (xóa sạch), `POST /checkout`.

**Luồng checkout:**
1. Soft-check tồn kho từng item (chỉ cảnh báo sớm, **không khóa/trừ kho**).
2. Build `OrderRequestDTO` → gọi `OrderService.createOrder()` (forward JWT) — **việc trừ kho thật sự nằm hoàn toàn ở Order Service**.
3. Xóa giỏ hàng trong Redis, trả `orderId`.

Bảo mật: không tự verify JWT, dựa vào header `X-User-Id`/`X-User-Role` do Gateway gắn sẵn.

### 5.2 Order Service (`OrderService`, port 8084)

**Vai trò:** "Trái tim" nghiệp vụ — sở hữu vòng đời đơn hàng, **thực sự trừ/hoàn kho**, điều phối trạng thái, phát Kafka, đồng bộ 2 chiều với Shipping.

**Trạng thái đơn hàng (`OrderStatus`):**
```
PENDING → CONFIRMED → SHIPPING → DELIVERED → REFUNDED
                                            ↘ RETURNED
(bất kỳ trạng thái nào trừ DELIVERED/REFUNDED) → CANCELLED
(qua markOrderAsPaid, không ràng buộc trạng thái trước) → PAID
```

**API chính** (`/api/orders`): `POST /` (tạo đơn), `GET /`, `GET /{id}`, `PUT /{id}`, `DELETE /{id}` (soft-delete), `PUT /{id}/confirm|ship|deliver|cancel|refund|return`, `POST /{id}/paid` (do Payment Service gọi), `GET /filter`, `GET /user/{userId}`, `GET /check-purchased`, `GET /stats`, `/stats/revenue`, `/stats/best-selling`.

**Luồng tạo đơn (`createOrder`, `@Transactional`):**
1. Validate user tồn tại (Feign User Service).
2. Với từng item: validate sản phẩm ACTIVE (Feign Product Service), kiểm tra đủ tồn kho (Feign Inventory), tính giá theo sale nếu có → snapshot vào `OrderItemEntity`.
3. Lưu Order với status **PENDING**, tạm tính `finalAmount = totalAmount + shippingFee` (discount = 0) để có `orderId` thật.
4. Nếu request có `pointsToRedeem > 0`: gọi Feign `PointService.redeem` (kèm `orderAmount = totalAmount`) trừ điểm ngay — Point Service tự kiểm tra giới hạn 50% tiền hàng và số dư, Order Service chuyển lỗi trả về thành `POINTS_EXCEED_LIMIT` hoặc `INSUFFICIENT_POINTS`; cập nhật `discountAmount` và `finalAmount = totalAmount + shippingFee − discountAmount`. `discountAmount` do client gửi lên không được dùng.
5. **Trừ kho thật sự**: `InventoryClient.decreaseStockBulk()` — nếu lỗi và đã dùng điểm thì gọi reverse để hoàn điểm.
6. Publish Kafka `order-placed-topic` (`type=ORDER_PLACED`).

**Giao hàng thành công (`deliverOrder`):** SHIPPING → DELIVERED, publish Kafka `order-delivered-topic` (`orderId`, `userId`, `amount = totalAmount` — giá trị gốc/tiền hàng) để Point Service cộng điểm bất đồng bộ.

**Ship đơn:** chuyển SHIPPING, tự động tạo vận đơn bên Shipping Service nếu chưa có (carrier mặc định `"GHTK"`), lấy tên/SĐT người nhận từ User Service.

**Hủy đơn:** chỉ chủ đơn/ADMIN; chặn nếu đã DELIVERED/REFUNDED; nếu đơn đã PAID **chỉ log cảnh báo, không tự động hoàn tiền** (khoảng trống nghiệp vụ); hoàn kho sau khi hủy; gọi Point Service reverse để hoàn điểm đã dùng.

**Trả hàng:** chỉ chấp nhận từ PAID/DELIVERED → RETURNED, hoàn kho, gọi Point Service reverse (hoàn điểm đã dùng, thu hồi điểm đã tích), publish Kafka `ORDER_RETURNED`.

**Hoàn tiền (`refundOrder`):** DELIVERED → REFUNDED — **chỉ đổi trạng thái nội bộ, không gọi API hoàn tiền VNPay thật**.

### 5.3 Payment Service (`PaymentService`, port 8089)

**Vai trò:** Xử lý thanh toán qua **VNPay** (sandbox). Không có tích hợp Momo/COD trong code — COD xử lý hoàn toàn phía Order Service (không qua Payment Service).

**API chính** (`/api/payment`): `GET /create-vnpay-url?amount=&orderId=`, `GET /vnpay-callback` (webhook), `GET /mock-success` (test).

**Luồng thanh toán:**
1. Tạo URL VNPay: build tham số chuẩn, ký **HMAC-SHA512**, chặn thanh toán lại nếu order đã PAID/SHIPPING/DELIVERED (best-effort).
2. Callback: verify chữ ký HMAC-SHA512 chống giả mạo; nếu `responseCode=00` → gọi `OrderService.markOrderAsPaid()`, lưu `PaymentRecordEntity(status=SUCCESS)`, publish Kafka `payment-success-topic`.
3. Consumer lắng nghe `order-placed-topic`, **chỉ xử lý `type=ORDER_RETURNED`**: set `PaymentRecordEntity.status=REFUNDED` — **chỉ là cập nhật sổ sách nội bộ, không có lệnh gọi VNPay Refund API thật**.

### 5.4 Shipping Service (`ShippingService`, port 8093)

**Vai trò:** Quản lý vận đơn 1-1 với Order. **Không tích hợp hãng vận chuyển thật** (GHN/GHTK/VNPost) — carrier chỉ là string tự do, `trackingNumber` tự sinh nội bộ dạng `{CARRIER}-{UUID8}`.

**Trạng thái (`ShippingStatus`):** `PENDING → SHIPPED → IN_TRANSIT → DELIVERED`, hoặc `→ RETURNED`, `→ CANCELED`.

**API chính** (`/api/shipping`): `POST /`, `PATCH /{id}/status`, `GET /order/{orderId}`, `GET /tracking/{trackingNumber}`.

**Luồng:** tạo vận đơn idempotent theo orderId, `estimatedDeliveryDate = now + 3 ngày` (cố định, không tính theo địa chỉ/khoảng cách). Khi cập nhật status → đồng bộ ngược Order Service: `SHIPPED→shipOrder`, `DELIVERED→deliverOrder`, `CANCELED→cancelOrder`, `RETURNED→returnOrder`.

### 5.5 Point Service (`PointService`, port 8095)

**Vai trò:** Quản lý điểm thưởng tích lũy của khách hàng. Lưu MySQL `point_service` gồm 2 bảng: `point_account` (số dư hiện tại, khóa chính `userId`, tự tạo với số dư 0 khi được truy cập lần đầu) và `point_transaction` — **sổ cái (ledger)**: mọi thay đổi số dư đều ghi 1 dòng (`userId`, `orderId`, `type`, `points` luôn dương, `balanceAfter`, `createdAt`) để audit và đảo ngược chính xác.

**Quy tắc nghiệp vụ:**
- **Tích điểm**: 10% **giá trị gốc của đơn** (`point.earn-rate: 0.10`, tính trên tiền hàng `totalAmount` — không gồm phí ship, không bị giảm bởi điểm đã dùng), làm tròn xuống số nguyên, cộng khi đơn chuyển **DELIVERED**.
- **Dùng điểm**: 1 điểm = 1 đơn vị tiền của đơn hàng; mỗi đơn đổi điểm tối đa bằng **50% tiền hàng** (`point.max-redeem-ratio: 0.50`, tính trên `totalAmount`) để **không có đơn 0đ**. Point Service kiểm tra cả giới hạn này (`POINTS_EXCEED_LIMIT`) lẫn số dư (`INSUFFICIENT_POINTS`), đều trả HTTP 400.
- **Một nguồn cấu hình duy nhất**: hai tỉ lệ trên chỉ khai báo ở `point.*` trong `application.yml` của Point Service. Order Service không còn hằng số riêng; FE đọc qua `GET /api/points/config`. Đổi tỉ lệ chỉ cần sửa file yml và khởi động lại Point Service.
- **Đảo ngược**: khi đơn bị hủy/trả, điểm đã dùng được hoàn lại, điểm đã tích từ đơn đó bị thu hồi.

**Loại giao dịch (`PointTransactionType`):**

| Type | Ý nghĩa | Chiều số dư |
|---|---|---|
| `EARN` | Cộng điểm khi đơn DELIVERED | + |
| `REDEEM` | Dùng điểm giảm giá khi đặt đơn | − |
| `REVERSE_EARN` | Thu hồi điểm đã tích (đơn bị hủy/trả) | − |
| `REVERSE_REDEEM` | Hoàn điểm đã dùng (đơn bị hủy/trả) | + |

**API chính** (`/api/points`):

| Method | Path | Mục đích | Bên gọi |
|---|---|---|---|
| GET | `/config` | Quy tắc đang áp dụng (`earnRate`, `maxRedeemRatio`) | FE |
| GET | `/{userId}` | Số dư điểm (`userId`, `balance`) | FE |
| GET | `/{userId}/history` | Lịch sử giao dịch, mới nhất trước (không phân trang) | FE |
| POST | `/earn` | Cộng điểm (`userId`, `orderId`, `orderAmount`) | Nội bộ (cùng logic với consumer Kafka) |
| POST | `/redeem` | Trừ điểm (`userId`, `orderId`, `points`, `orderAmount`), trả `discountAmount` | Order Service (Feign) |
| POST | `/{orderId}/reverse` | Đảo ngược mọi giao dịch EARN/REDEEM của đơn | Order Service (Feign) |

**Luồng:**
1. **Cộng điểm**: `OrderDeliveredConsumer` lắng nghe `order-delivered-topic` (group `point-service-group`) → `earn()`. Topic tách riêng khỏi `order-placed-topic` để không ảnh hưởng consumer sẵn có (Notification/Payment). Lỗi xử lý chỉ log, không retry/dead-letter.
2. **Dùng điểm**: Order Service gọi đồng bộ `redeem` ngay sau khi lưu đơn (bước 4 mục 5.2).
3. **Đảo ngược**: Order Service gọi `reverse` khi hủy đơn, trả hàng, hoặc khi trừ kho thất bại sau khi đã dùng điểm.

**Idempotent:** `earn`, `redeem` và từng loại reverse đều kiểm tra đã tồn tại giao dịch cùng `orderId` + `type` trước khi ghi → Kafka gửi lại hay gọi lặp không cộng/trừ 2 lần (kiểm tra ở tầng ứng dụng, không có unique constraint DB).

**Bảo mật:** `permitAll()` toàn bộ ở tầng Spring Security nội bộ, giống các service khác — phân quyền nằm ở Gateway: chặn 403 mọi lệnh ghi (`earn`/`redeem`/`reverse`) đi qua Gateway vì đây là API nội bộ (Order Service gọi thẳng qua Feign/Eureka, không qua Gateway); GET số dư/lịch sử chỉ cho chủ sở hữu (`userId` trên path trùng `userId` trong JWT) hoặc ADMIN.

**Số dư không âm:** `redeem` từ chối nếu thiếu điểm; khi thu hồi (`REVERSE_EARN`) mà khách đã tiêu bớt số điểm đó thì chỉ thu hồi phần còn lại — dòng sổ cái ghi số điểm thực thu (có thể bằng 0) và vẫn được lưu để đánh dấu đơn đã đảo ngược. Phần điểm không thu hồi được coi như bỏ qua (chỉ log cảnh báo).

### 5.6 Coupon Service (`CouponService`, port 8096)

**Vai trò:** Quản lý mã giảm giá. Lưu MySQL `coupon_service` gồm 2 bảng: `coupon` (định nghĩa mã và số lượt đang được tính) và `coupon_usage` — sổ ghi từng lần dùng (`couponId`, `userId`, `orderId` duy nhất, số tiền giảm, trạng thái `USED`/`REVERSED`).

**Một mã gồm:** chuỗi mã (lưu chữ hoa), mô tả, kiểu giảm (`PERCENT` kèm mức giảm tối đa, hoặc `FIXED`), đơn tối thiểu (tính trên tiền hàng), thời gian bắt đầu/kết thúc, tổng lượt dùng, số lần mỗi khách (mặc định 1), trạng thái bật/tắt. Ô để trống nghĩa là không giới hạn.

**Ba quy tắc nghiệp vụ đã chốt:**
1. Mã giảm giá **không dùng chung** với điểm thưởng trong cùng một đơn.
2. Đơn bị **hủy hoặc trả hàng** thì lượt dùng mã được **hoàn lại** (cho mã và cho khách).
3. Điểm tích lũy **chỉ được cộng khi đơn không áp mã giảm giá**.

**Cách tính:** `PERCENT`: tiền hàng × % (làm tròn xuống 2 chữ số), không vượt mức giảm tối đa. `FIXED`: đúng số tiền của mã. Cả hai không bao giờ vượt quá tiền hàng. Không giảm phí vận chuyển.

**Thứ tự kiểm tra khi dùng mã** (sai ở đâu báo đúng lý do đó): mã tồn tại → đang bật → đã tới ngày → chưa hết hạn → còn lượt → khách chưa dùng hết số lần → đạt đơn tối thiểu (thông báo nêu còn thiếu bao nhiêu).

**API chính** (`/api/coupons`):

| Method | Path | Mục đích | Bên gọi |
|---|---|---|---|
| GET | `/preview?code=&orderAmount=` | Kiểm tra mã và tính số tiền giảm, **không** trừ lượt | FE (khách đã đăng nhập) |
| GET | `/available` | Các mã đang dùng được | FE |
| POST | `/redeem` | Trừ lượt, ghi sổ, trả số tiền giảm. Chống trùng theo `orderId` | Order Service (Feign) |
| POST | `/{orderId}/reverse` | Hoàn lượt của đơn; gọi lại nhiều lần vẫn an toàn | Order Service (Feign) |
| GET / POST / PUT | `/`, `/{id}`, `/{id}/active` | Quản trị mã | FE (ADMIN) |

**Luồng đặt hàng có mã:** FE gửi `couponCode` (không gửi số tiền giảm) → Cart Service chuyển tiếp → Order Service từ chối nếu kèm cả điểm → lưu đơn → gọi `redeem` (Coupon Service kiểm tra lại mọi điều kiện, khóa dòng của mã, tăng lượt, ghi sổ trong một giao dịch) → ghi `couponCode` và `discountAmount` vào đơn → trừ kho. Trừ kho lỗi thì gọi `reverse`.

**Chống vượt lượt:** `redeem` đọc mã bằng khóa ghi (`PESSIMISTIC_WRITE`), nên hai khách cùng tranh lượt cuối sẽ lần lượt được xử lý và chỉ một người được.

**Khi Coupon Service chết:** đặt hàng có mã báo "dịch vụ mã giảm giá gián đoạn" (đặt không mã vẫn được); hủy/trả đơn vẫn thành công và việc hoàn mã được ghi vào hàng đợi việc còn nợ của Order Service để tự làm lại.

**Bảo mật:** service không tự phân quyền; Gateway phân quyền theo đường dẫn (xem bảng routing mục 3.2). `preview` lấy danh tính khách từ header `X-User-Id`.

---

## 6. Nhóm Giao tiếp

### 6.1 Notification Service (`NotificationService`, port 8090)

**Vai trò:** Gửi **email tự động** (SMTP Gmail) khi có sự kiện quan trọng — **stateless, không có database**, chỉ hoạt động theo mô hình lắng nghe Kafka → làm giàu dữ liệu qua Feign → render Thymeleaf → gửi mail.

**Lắng nghe Kafka:** `order-placed-topic` (dùng chung cho `ORDER_PLACED` và `ORDER_RETURNED`, phân biệt qua field `type`), `payment-success-topic` (`PAYMENT_SUCCESS`).

**Luồng xử lý:** nhận event → gọi Feign `OrderService`/`UserService` lấy thông tin đơn/khách hàng → chọn template tương ứng (`order-placed.html`, `payment-success.html`, `order-returned.html`) → gửi email. Không tìm được email → log cảnh báo, **không retry/dead-letter**.

### 6.2 Chat Service (`ChatService`, port 8092)

**Vai trò:** Chat real-time giữa **khách hàng** và **bộ phận hỗ trợ**, lưu **MongoDB** (`messages`, `conversations`). Bộ phận hỗ trợ là một danh tính chung `admin-support`: mọi tài khoản ADMIN đều thấy mọi hội thoại và trả lời dưới danh nghĩa này (tên nhân viên được lưu trong `senderName`). Không dùng Kafka, không gọi service khác.

**Kênh WebSocket/STOMP** (`/ws`, có SockJS):
- Kết nối: client gửi JWT trong header `Authorization` của frame CONNECT; `StompAuthChannelInterceptor` kiểm tra và gắn danh tính (`userId`, `username`, `role`) vào phiên.
- Gửi: `/app/chat.sendMessage` với `{ content, recipientId }`. Khách: tin luôn đi tới `admin-support` (bỏ qua `recipientId`). ADMIN: `recipientId` là id khách cần trả lời.
- Nhận: khách subscribe `/user/queue/messages` (chỉ nhận tin của chính mình); ADMIN subscribe `/topic/support` (mọi tin của mọi hội thoại).

**REST** (`/api/chat`, danh tính lấy từ header `X-User-*` của Gateway):

| Method | Path | Mục đích | Quyền |
|---|---|---|---|
| GET | `/messages/{customerId}/admin-support` | Lịch sử hội thoại | Chủ hội thoại hoặc ADMIN |
| GET | `/conversations` | Danh sách hội thoại, mới nhất trước | ADMIN |
| PUT | `/conversations/{customerId}/read` | Đánh dấu đã đọc | Chủ hội thoại hoặc ADMIN |
| GET | `/unread` | Số tin chưa đọc của người đang đăng nhập | Đã đăng nhập |

**Collection `conversations`:** mỗi khách 1 dòng tóm tắt (tên, tin cuối, thời gian, số chưa đọc của hỗ trợ và của khách), cập nhật mỗi khi có tin mới để màn hình Admin liệt kê nhanh.

**Giới hạn hiện tại:** broker STOMP chạy trong bộ nhớ nên chỉ chạy được 1 bản Chat Service; token đã logout (blacklist ở Redis) vẫn kết nối chat được cho tới khi hết hạn; thời gian tin nhắn lưu theo giờ máy chủ, không kèm múi giờ; hội thoại tạo trước đợt cập nhật 2026-10-11 không có dòng trong `conversations` cho tới khi khách nhắn tin mới.

---

## 7. Frontend (FE React)

**Kiến trúc:** React 18 + Vite, `react-router-dom`, `axios`, state đăng nhập qua `AuthContext`. Gọi API qua **API Gateway** (`VITE_API_URL=http://localhost:8085`), JWT lưu `localStorage`, tự động validate token và logout khi hết hạn.

**Trang chính:** `Login`/`Register`, `Products` (tìm kiếm/lọc/sắp xếp), `ProductDetail` (mua hàng + xem/viết review — chỉ cho phép nếu đã mua), `Cart`, `Checkout` (nhập địa chỉ, redirect sang VNPay), `OrderHistory` (theo dõi trạng thái, hủy đơn, xem vận chuyển), `Profile` (hồ sơ tổng hợp), `Points` (điểm thưởng), `Admin` (chỉ role ADMIN).

**Điểm thưởng trên FE (bổ sung 2026-10-11):**
- `services/PointService.js`: chỉ gọi 3 API tra cứu `GET /api/points/config`, `/{userId}` và `/{userId}/history`; không gọi `earn`/`redeem`/`reverse` (API nội bộ).
- `pages/Points.jsx` (route `/points`, cần đăng nhập): 3 ô số liệu (điểm hiện có, tổng đã tích, tổng đã dùng — tính từ lịch sử), bảng quy tắc, lịch sử giao dịch có lọc theo loại. Role ADMIN xem được điểm của user khác qua `/points?userId=<id>`. Bảng quy tắc hiển thị tỉ lệ lấy từ `/config`.
- `pages/Checkout.jsx`: tải giỏ hàng, số dư điểm và quy tắc điểm; ô nhập số điểm muốn dùng + nút "Dùng tối đa"; giới hạn `min(số dư, maxRedeemRatio × tiền hàng)` làm tròn xuống; bảng tạm tính (tiền hàng, phí ship, giảm bằng điểm, tổng, số điểm dự kiến nhận = `earnRate × tiền hàng`); gửi `pointsToRedeem` trong body checkout. Tỉ lệ lấy từ `GET /api/points/config`, FE không hard-code; nếu không tải được thì ẩn phần dùng điểm.
- `pages/Profile.jsx`: ô "Reward Points" trong Activity Summary, dẫn tới `/points`. `components/Navbar.jsx`: link "Points".

**Mã giảm giá trên FE (bổ sung 2026-10-12):**
- `services/CouponService.js`: xem trước, danh sách mã đang có, và các API quản trị.
- `pages/Checkout.jsx`: ô nhập mã + nút "Áp dụng", các mã đang có hiện dạng nút bấm, dòng giảm giá trong bảng tạm tính. Đã áp mã thì ô điểm bị khóa và ngược lại; khi có mã, dòng "điểm dự kiến nhận" không hiện.
- `pages/AdminCoupons.jsx` (route `/admin/coupons`, link "Coupons" trên Navbar cho ADMIN): bảng mã kèm trạng thái (đang chạy, chưa bắt đầu, hết hạn, hết lượt, tạm ngừng), form tạo/sửa, nút tạm ngừng/bật lại.
- `pages/OrderHistory.jsx`: dòng giảm giá ghi rõ là do mã nào hay do điểm thưởng.

**Trang Admin quản lý:**
- **Dashboard**: doanh thu, tổng đơn, đơn pending, số user — qua `GET /api/orders/stats`.
- **Products**: CRUD đầy đủ (thêm/sửa có upload ảnh multipart, xóa = soft-delete).
- **Orders**: theo dõi + chuyển trạng thái theo state machine (`confirm/cancel/ship/deliver/return`).
- **Categories**: hiện chỉ xem (chưa có form thêm/sửa/xóa dù backend đã hỗ trợ).
- **Chưa có tab quản lý Users** dù `UserService.getAllUsers/deleteUser` đã tồn tại ở tầng service.

**Chat:** `ChatWidget.jsx` (khách hàng, ẩn với ADMIN) giữ kết nối suốt lúc đăng nhập, hiện huy hiệu tin chưa đọc, tự kết nối lại khi rớt mạng. `AdminChat.jsx` (`/admin/chat`) là màn hình hỗ trợ cho ADMIN. Logic kết nối dùng chung nằm ở `services/ChatService.js`.

**Ghi chú kỹ thuật:** file `src/pages/Home.jsx` tồn tại nhưng không được route (code thừa); thư viện `sockjs-client`/`stompjs` được import trong `ChatWidget.jsx` nhưng thiếu trong `package.json`; tỷ giá quy đổi VNĐ trong Checkout hard-code 25.000 (giả lập, không lấy từ API thực).

---

## 8. Luồng nghiệp vụ end-to-end

### 8.1 Đăng ký → Đăng nhập
`FE Register` → `Auth Service /auth/register` → forward `User Service` tạo tài khoản (băm BCrypt) → `FE Login` → `Auth Service /auth/login` → xác thực qua User Service → sinh JWT 24h → FE lưu token, decode lấy `userId/username/role`, gắn vào mọi request qua Gateway.

### 8.2 Mua hàng (happy path)

```
1. Duyệt/tìm sản phẩm (Products/ProductDetail) — đọc qua Product Service (hoặc Search Service)
2. Thêm vào giỏ (Cart Service, Redis) — snapshot giá, check tồn kho sơ bộ
3. Checkout → Cart Service gọi Order Service tạo đơn:
   - Validate user, validate sản phẩm (ACTIVE, giá hiện tại)
   - Kiểm tra & TRỪ KHO THẬT (Inventory Service, pessimistic lock, decrease-bulk)
   - Lưu Order (PENDING); nếu có pointsToRedeem → Point Service trừ điểm, giảm finalAmount
   - Publish Kafka order-placed-topic (ORDER_PLACED)
   - Notification Service consume → gửi email "Đặt hàng thành công"
4. Thanh toán: FE gọi Payment Service tạo URL VNPay → redirect sang VNPay
   → callback verify chữ ký → Order.markOrderAsPaid (→ PAID)
   → publish Kafka payment-success-topic → Notification gửi email xác nhận thanh toán
5. Admin xử lý đơn (thủ công qua trang Admin):
   confirm → CONFIRMED → ship → SHIPPING (tự tạo vận đơn ở Shipping Service)
6. Shipping Service cập nhật trạng thái giao hàng → đồng bộ ngược Order
   (SHIPPED/DELIVERED/CANCELED/RETURNED)
   Khi DELIVERED: Order publish Kafka order-delivered-topic → Point Service
   cộng điểm (10% tiền hàng) → khách xem tại trang /points
7. Sau khi DELIVERED, khách có thể viết review (Review Service kiểm tra
   đã mua & đã giao qua check-purchased) → publish Kafka cập nhật rating
   sản phẩm ở Product Service
```

### 8.3 Hủy / Trả hàng
- **Hủy đơn** (PENDING/CONFIRMED...): hoàn kho (`increaseStockBulk`); nếu đơn đã thanh toán chỉ log cảnh báo, **không tự động hoàn tiền**.
- **Trả hàng** (từ PAID/DELIVERED): chuyển RETURNED, hoàn kho, publish Kafka `ORDER_RETURNED` → Payment Service đánh dấu `PaymentRecord = REFUNDED` (chỉ nội bộ) và Notification gửi email thông báo hoàn trả.
- **Điểm thưởng**: cả hủy đơn và trả hàng đều gọi Point Service reverse — hoàn điểm đã dùng (`REVERSE_REDEEM`) và thu hồi điểm đã tích (`REVERSE_EARN`). Lỗi khi gọi chỉ được log, đơn vẫn chuyển trạng thái. Luồng **hoàn tiền** (`refundOrder`) không đảo ngược điểm.

---

## 9. Tổng hợp rủi ro & nợ kỹ thuật

Các điểm này không ảnh hưởng tới việc đọc hiểu nghiệp vụ nhưng quan trọng nếu tài liệu này được dùng để lập kế hoạch phát triển tiếp:

1. **Không có hoàn tiền thật**: mọi luồng "refund/return" (Order Service lẫn Payment Service) chỉ đổi trạng thái trong DB nội bộ, không gọi API hoàn tiền VNPay thật.
2. **Hủy đơn đã thanh toán** không tự động trigger hoàn tiền — cần xử lý thủ công.
3. ~~`markOrderAsPaid` không kiểm tra transition hợp lệ~~ — **đã xử lý (2026-10-11)**: chỉ chuyển PAID từ PENDING/CONFIRMED; đơn đã PAID/đang giao/đã giao thì bỏ qua (gọi lặp an toàn); đơn đã CANCELLED thì không đổi trạng thái và ghi log cần hoàn tiền thủ công.
4. **Bảo mật phân quyền dồn hết về API Gateway**: hầu hết service con (Auth, User, Product, Order, Shipping...) đều `permitAll()` ở tầng Spring Security nội bộ — nếu gọi thẳng vào port service (bỏ qua Gateway) sẽ không có phân quyền, chỉ dựa vào header `X-User-*` do Gateway gắn.
5. ~~Dữ liệu Search Service dễ lệch (stale)~~ — **đã xử lý (2026-10-12)**: đồng bộ theo từng thay đổi qua Kafka + đối soát định kỳ 30 phút (mục 4.3).
6. **Không có cơ chế "reserve" tồn kho có TTL** — mô hình hiện tại là trừ ngay/hoàn sau, tồn tại khoảng hẹp race-condition giữa bước Cart soft-check và Order thực thi (dù bước trừ kho thật ở Inventory Service có pessimistic lock).
7. ~~2 cơ chế Holiday Sale trùng lặp trong Product Service~~ — **đã xử lý (2026-08-14)**: gỡ bỏ `HolidaySaleScheduler`, chỉ giữ lại `HolidayService` làm cơ chế duy nhất.
8. **Không tích hợp hãng vận chuyển ngoài thật** (carrier vẫn là chuỗi tự do, mã vận đơn tự sinh nội bộ), thời gian giao hàng cố định +3 ngày không theo địa chỉ thực tế. Phí ship **đã được tính động** theo địa chỉ (2026-08-14) nhưng dựa trên từ khoá địa danh đơn giản hoá, chưa dùng API định vị/khoảng cách thật.
9. **JWT không có refresh token, không có đăng nhập mạng xã hội/OAuth2** — vẫn chưa được xây dựng. Riêng luồng **quên mật khẩu qua OTP email và đổi mật khẩu đã được bổ sung (2026-08-14)**.
10. **Một số RestTemplate hard-code URL** (`localhost:8082`, `localhost:8084`, `localhost:8092`...) thay vì qua Eureka/Gateway — rủi ro khi triển khai đa instance/container. Các Feign client mới thêm trong đợt cập nhật này (`NotificationClient`, `UserClient.getUserByEmail/updatePassword`...) **giữ nguyên cùng kiểu cấu hình hard-code URL** để nhất quán với phần còn lại của Auth Service — cần refactor đồng loạt sau này.
11. ~~Frontend: Admin chưa có quản lý Users/CRUD Category; thiếu khai báo `sockjs-client`, `stompjs`~~ — **đã xử lý (2026-08-14)**: thêm tab Users + CRUD Category vào `Admin.jsx`, cài đặt và khai báo lại 2 thư viện chat vào `package.json` (trước đó khiến `npm run build` thất bại hoàn toàn).
12. **Endpoint tính phí ship mới** (`GET /api/shipping/fee`) và **cập nhật hồ sơ/đổi mật khẩu** hiện chưa có unit test — nên bổ sung test khi có thời gian.
13. ~~Cart Service chưa chuyển tiếp `pointsToRedeem`~~ — **đã xử lý (2026-10-11)**: thêm field vào `CartDTO.CheckoutRequest`, `OrderRequestDTO` (mirror) và set trong `CartService.checkout()`.
14. ~~API điểm không có phân quyền theo chủ sở hữu~~ — **đã xử lý ở Gateway (2026-10-11)**: chặn mọi lệnh ghi tới `/api/points/**`, GET chỉ cho chủ sở hữu hoặc ADMIN. Còn lại: Point Service vẫn `permitAll()` nên gọi thẳng port 8095 không bị chặn (giống điểm 4).
15. **Điểm không nhất quán khi lỗi/đảo ngược**: (a) `reversePointsSafely` và consumer cộng điểm chỉ log khi lỗi, không retry → có thể mất đồng bộ giữa đơn hàng và điểm; (b) ~~số dư âm khi thu hồi~~ — **đã xử lý (2026-10-11)**: chỉ thu hồi tối đa bằng số dư hiện có, hệ quả là khách đã tiêu điểm rồi trả hàng thì phần điểm đã tiêu không đòi lại được; (c) `refundOrder` không thu hồi điểm đã tích; (d) cập nhật số dư không dùng lock/version nên 2 request đồng thời cùng user có thể ghi đè nhau (về lý thuyết vẫn có thể làm lệch số dư); (e) chống trùng theo `orderId` + `type` chỉ ở tầng ứng dụng, không có unique constraint DB.
16. ~~Cấu hình điểm phân tán~~ — **đã xử lý (2026-10-11)**: tỉ lệ tích điểm và giới hạn dùng điểm chỉ còn khai báo ở `point.*` của Point Service (xem mục 5.5). Còn lại: đơn vị tiền chưa thống nhất (Cart/Checkout hiển thị `$`, OrderHistory format VND trên cùng con số) nên "1 điểm = 1 đơn vị tiền" hiển thị là `$1` ở Checkout; tỉ giá quy đổi 1 điểm = 1 đơn vị tiền vẫn cố định trong code Point Service.
---

## 10. Chống lỗi lan truyền giữa các service

Bổ sung ngày 2026-10-11. Trước đó dự án không có circuit breaker, retry hay timeout cho Feign; lỗi gọi service khác hoặc bị bỏ qua sau một dòng log, hoặc làm hỏng cả thao tác.

### 10.1 Các lớp bảo vệ

| Lớp | Áp dụng ở | Hành vi |
|---|---|---|
| **Timeout** | Mọi Feign client (Order, Payment, Review, Shipping, Auth, Product, Notification): bean `Request.Options` trong `ResilienceConfig`; `RestTemplate` của User và Search Service | 3 giây kết nối, 10 giây chờ phản hồi (trước là 10s/60s hoặc vô hạn) |
| **Ngắt mạch (circuit breaker)** | Mọi Feign client của Order, Payment, Review, Shipping, Auth, Product — `@CircuitBreaker` ở cấp interface (Resilience4j) | Trong 10 lời gọi gần nhất, từ 5 lời gọi trở lên mà ≥ 50% lỗi thì ngừng gọi service đó 20 giây và trả lỗi ngay; sau đó cho thử 2 lời gọi để quyết định đóng mạch lại. Lỗi 4xx (nghiệp vụ) không tính là lỗi |
| **Thử lại (retry)** | Chỉ các method an toàn khi lặp: mọi GET, và `PointClient.redeem/reverse`, `OrderClient.markOrderAsPaid` (đều tự chống trùng) | Tối đa 3 lần, cách 500ms, chỉ khi không kết nối được / quá thời gian / 502-503-504. Các lệnh trừ kho, cộng kho, tạo vận đơn, tạo user **không** thử lại |
| **Thông báo lỗi đúng nguyên nhân** | `ResilienceExceptionHandler` ở mỗi service; `ErrorCode` mới ở Order | Trả 503 "hệ thống tạm thời gián đoạn" thay vì 500, "không tìm thấy người dùng" hay "sai mật khẩu" |
| **Việc còn nợ, tự làm lại** | Order Service: bảng `order_pending_task` + `PendingTaskProcessor` (chạy mỗi 30 giây) | Hoàn điểm, tạo vận đơn, phát sự kiện cộng điểm mà lỗi thì được ghi lại và thử lại (30s, 1, 2, 4, 8 phút...) tối đa 40 lần (~5 giờ), sau đó đánh dấu `FAILED` và ghi log ERROR |
| **Bảo đảm ghi nhận thanh toán** | Payment Service: `PaymentCompletionService` | Lưu bản ghi thanh toán **trước**, rồi mới báo Order Service; báo không được thì thử lại mỗi 30 giây cho tới khi xong. Callback lặp không tạo bản ghi trùng |
| **Kafka: thử lại và dead-letter** | Consumer của Point, Notification, Payment, Product, Search: bean `DefaultErrorHandler` | Tin xử lý lỗi được thử lại 8 lần giãn dần 2s→60s (~4 phút); vẫn lỗi thì chuyển sang topic `<tên topic>.DLT`. Tin sai JSON chuyển thẳng. Trước đây consumer nuốt lỗi và tin mất vĩnh viễn |
| **Kafka: gửi không treo** | Order, Payment, Product | `max.block.ms` = 5 giây (mặc định 60) |
| **Gateway** | `application.yaml` | Thử lại 2 lần cho request **GET** khi nhận 502/503/504; timeout 3s kết nối / 30s phản hồi (route chat không giới hạn); làm mới danh sách service mỗi 10 giây |
| **Eureka** | `application.yaml` | Quét loại bản chạy hết hạn mỗi 10 giây (trước 60) |

### 10.2 Hành vi theo tình huống

| Service chết | Thao tác | Trước | Bây giờ |
|---|---|---|---|
| Order | VNPay gọi về báo thanh toán thành công | Không lưu gì; khách mất tiền, đơn vẫn chưa thanh toán | Lưu khoản thanh toán, khách về trang thành công; đơn chuyển PAID khi Order sống lại |
| Point | Hủy / trả đơn | Điểm không được hoàn, chỉ có log | Đơn vẫn hủy; hoàn điểm tự chạy lại tới khi xong |
| Point | Đặt hàng có dùng điểm | Báo sai "không đủ điểm" | Báo "dịch vụ điểm gián đoạn"; nếu điểm lỡ bị trừ thì tự hoàn |
| Shipping | Admin bấm giao hàng | Đơn SHIPPING nhưng không có vận đơn | Đơn SHIPPING; vận đơn tự được tạo khi Shipping sống lại |
| Kafka | Đơn giao thành công | Sự kiện cộng điểm mất; thao tác treo tới 60 giây | Đơn DELIVERED sau tối đa ~5 giây; sự kiện tự phát lại |
| Point (database lỗi) | Nhận sự kiện cộng điểm | Khách mất điểm vĩnh viễn | Thử lại ~4 phút, sau đó vào `order-delivered-topic.DLT` |
| User | Đăng nhập | Báo "sai mật khẩu"; mỗi lần chờ hết timeout | Báo "hệ thống đăng nhập gián đoạn" (503); sau vài lỗi thì trả ngay trong vài mili giây |
| User | Đặt hàng | Báo "người dùng không tồn tại" | Báo "dịch vụ tài khoản gián đoạn" (503) |
| Product / Inventory | Đặt hàng | Lỗi sau khi chờ tới 60 giây; đơn rollback | Lỗi sau tối đa ~10 giây (hoặc ngay nếu mạch đang ngắt); đơn rollback |
| Bất kỳ | Request GET qua Gateway | Lỗi ngay | Gateway thử lại 2 lần trước khi báo lỗi |

### 10.3 Đã kiểm chứng tới đâu

- **Cơ chế ngắt mạch / thử lại trên Feign**: test riêng với máy chủ giả, 7/7 đạt trên cả hai bộ phiên bản Spring đang dùng (Boot 3.4.4 và 3.2.4).
- **Kafka thử lại + dead-letter**: test riêng với broker Kafka nhúng, đạt (lỗi tạm thời được xử lý lại; tin sai JSON và tin lỗi mãi vào DLT; tin phía sau không bị chặn).
- **Order Service**: `OrderResilienceTest` — 12 tình huống, đạt (MySQL thật, các service khác và Kafka giả lập).
- **Payment Service**: `PaymentResilienceTest` — 5 tình huống, đạt.
- **Auth Service chạy thật, User Service tắt**: đăng nhập trả 503 đúng thông báo; từ lần thứ ba trả trong ~7ms (mạch đã ngắt). Cũng đúng khi đi qua Gateway.
- **Gateway + Chat**: chạy lại bộ kiểm thử chat qua Gateway sau khi thêm retry/timeout, vẫn hoạt động.
- **Chưa kiểm thử**: toàn hệ thống chạy cùng lúc rồi tắt từng service; Review, Shipping, Product, Notification, Search mới ở mức biên dịch thành công (dùng cùng cơ chế đã test ở trên).

### 10.4 Giới hạn còn lại

1. **Chưa có công cụ xử lý tin trong DLT**: tin vào `*.DLT` cần người xem và phát lại bằng tay.
2. **Việc còn nợ `FAILED`** (sau ~5 giờ) và khoản thanh toán chưa đồng bộ lâu chỉ được báo qua log ERROR; chưa có màn hình quản trị hay cảnh báo.
3. **Mất sự kiện khi Kafka chết** vẫn có thể xảy ra với email đặt hàng/thanh toán, đồng bộ tìm kiếm, cập nhật điểm đánh giá (chỉ sự kiện cộng điểm được bảo đảm phát lại).
4. **Bản chạy chết đột ngột** vẫn nằm trong danh sách Eureka tới ~90 giây (thời hạn mặc định của từng service chưa đổi). Trong lúc đó Gateway thử lại GET và các service ngắt mạch.
5. **Trạng thái ngắt mạch nằm trong bộ nhớ từng bản chạy**, không chia sẻ giữa các bản.
6. **Tạo đơn không hoàn toàn nguyên tử**: nếu trừ kho thành công mà bước lưu cuối cùng lỗi thì kho đã trừ nhưng đơn không tồn tại (hiếm; có từ trước).
7. Hủy đơn khi Inventory Service chết vẫn thất bại (có chủ ý: không hủy đơn nếu chưa cộng lại được kho).
