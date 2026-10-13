# HƯỚNG DẪN TÍCH HỢP KEYCLOAK VÀO TOÀN BỘ DỰ ÁN

> Tài liệu viết riêng cho hệ thống này (`Microserivce_ECommerSystem/` + `FE/`), dựa trên khảo sát mã nguồn xác thực hiện có ở Gateway, Auth Service, User Service, các service con và FE. Ngày viết: 2026-10-11.
>
> **Trạng thái:** đây là tài liệu phân tích và hướng dẫn, Keycloak **chưa được tích hợp** vào mã nguồn.
> - Code Gateway ở mục 8.4 (`JwtGatewayFilter`, `LocalUserResolver`, `WebClientConfig`) **đã biên dịch thành công** với đúng phiên bản Spring của Gateway (Boot 3.2.4, Cloud 2023.0.1), nhưng **chưa chạy thật** với Keycloak.
> - Các đoạn code còn lại (User Service, Review Service, Chat Service, FE) **chưa biên dịch thử**.
> - Tên menu và biến môi trường theo Keycloak 26; các bản khác có thể lệch đôi chút.

## Mục lục

1. [Tóm tắt](#1-tóm-tắt)
2. [Hiện trạng xác thực và các vấn đề](#2-hiện-trạng-xác-thực-và-các-vấn-đề)
3. [Keycloak là gì và đảm nhận phần nào](#3-keycloak-là-gì-và-đảm-nhận-phần-nào)
4. [Kiến trúc sau khi tích hợp](#4-kiến-trúc-sau-khi-tích-hợp)
5. [Nghiệp vụ sau khi tích hợp](#5-nghiệp-vụ-sau-khi-tích-hợp)
6. [Các quyết định thiết kế](#6-các-quyết-định-thiết-kế)
7. [Ảnh hưởng tới từng thành phần](#7-ảnh-hưởng-tới-từng-thành-phần)
8. [Các bước thực hiện](#8-các-bước-thực-hiện)
9. [Triển khai Docker và Azure](#9-triển-khai-docker-và-azure)
10. [Kiểm thử](#10-kiểm-thử)
11. [Lỗi thường gặp](#11-lỗi-thường-gặp)
12. [Bảo mật](#12-bảo-mật)
13. [Danh sách file thay đổi](#13-danh-sách-file-thay-đổi)

---

## 1. Tóm tắt

Keycloak sẽ **thay thế phần đăng nhập tự viết** (Auth Service + mật khẩu trong User Service) bằng một máy chủ định danh chuyên dụng. Người dùng đăng nhập trên trang của Keycloak, FE nhận token, Gateway kiểm tra token đó rồi chuyển danh tính xuống các service như hiện nay.

Ba điều cốt lõi của phương án:

1. **Gateway vẫn là nơi duy nhất kiểm tra token và phân quyền.** Các service con tiếp tục nhận `X-User-Id`, `X-User-Name`, `X-User-Role` nên gần như không phải sửa.
2. **Giữ nguyên id người dùng nội bộ (số).** Keycloak dùng id dạng UUID, nhưng đơn hàng, giỏ hàng, điểm, chat đều đang gắn với id số của User Service. Gateway sẽ đổi UUID sang id số, dữ liệu cũ không phải chuyển đổi.
3. **Làm theo từng giai đoạn**, mỗi giai đoạn hệ thống vẫn chạy được.

Khối lượng công việc: lớn. Thay đổi chạm tới Gateway, User Service, Review Service, Chat Service, Auth Service và phần đăng nhập của FE.

---

## 2. Hiện trạng xác thực và các vấn đề

### 2.1 Cách đang hoạt động

```
FE ──POST /auth/login──▶ Gateway ──▶ Auth Service ──Feign──▶ User Service (so khớp BCrypt)
FE ◀── JWT (HS256, 24h, chứa userId / username / role) ──
FE ──mọi request kèm "Authorization: Bearer <JWT>"──▶ Gateway
        JwtGatewayFilter: kiểm tra chữ ký bằng khóa bí mật dùng chung, áp luật phân quyền,
        gắn X-User-Id / X-User-Name / X-User-Role ──▶ service con
```

| Thành phần | Vai trò hiện tại |
|---|---|
| **Auth Service** | Đăng ký, đăng nhập, phát hành JWT, đăng xuất (blacklist trong Redis), quên/đặt lại/đổi mật khẩu |
| **User Service** | Lưu tài khoản và **mật khẩu băm BCrypt**, cột `role` |
| **API Gateway** | Kiểm tra JWT, phân quyền theo đường dẫn, gắn header danh tính |
| **Cart Service** | Tin hoàn toàn vào header `X-User-*` |
| **Review Service** | **Tự kiểm tra JWT** bằng khóa dùng chung và yêu cầu đã xác thực |
| **Chat Service** | **Tự kiểm tra JWT** ở kết nối STOMP |
| Order, Product, Point, Shipping, User | Có bộ lọc đọc JWT nhưng `permitAll()`; thực tế dựa vào header của Gateway |
| **FE** | Tự giải mã JWT để lấy `userId`, `username`, `role`; lưu token trong `localStorage` |

### 2.2 Các vấn đề Keycloak giải quyết

| Vấn đề hiện tại | Sau khi có Keycloak |
|---|---|
| Không có refresh token; token sống cứng 24h | Access token ngắn (5 phút) + refresh token, tự gia hạn |
| Đăng xuất không có hiệu lực ở Gateway (Gateway chỉ kiểm tra hạn dùng, không đọc blacklist) | Token ngắn hạn nên lộ token ít nguy hiểm hơn; đăng xuất hủy phiên ở Keycloak |
| Khóa ký JWT đối xứng, ghi cứng trong 9 file cấu hình, đã commit lên git; service nào có khóa cũng **tự tạo được token** | Keycloak ký bằng khóa riêng (RS256); các service chỉ có khóa công khai, không tạo được token |
| `POST /api/users` công khai và nhận `role` từ client → ai cũng tự đăng ký ADMIN | Đăng ký do Keycloak xử lý, vai trò chỉ quản trị viên gán |
| Không xác minh email khi đăng ký | Bật "Verify email" là có |
| Chưa có đăng nhập Google | Cấu hình trong Keycloak, không viết code backend |
| Tự viết OTP quên mật khẩu, đổi mật khẩu | Có sẵn |
| Không chống dò mật khẩu, không có xác thực 2 lớp | Có sẵn (Brute Force Detection, OTP) |
| Không có nơi quản trị tài khoản (khóa, đặt lại mật khẩu, xem phiên) | Admin Console của Keycloak |

### 2.3 Cái giá phải trả

- Thêm một thành phần phải vận hành (Keycloak + database riêng của nó, khoảng 0,7–1 GB RAM).
- Trang đăng nhập do Keycloak hiển thị; muốn giống giao diện shop phải làm theme.
- Một đợt sửa lớn chạm vào đăng nhập — phần mà lỗi sẽ khóa toàn bộ người dùng. Cần làm theo giai đoạn và kiểm thử kỹ.

Nếu mục tiêu chỉ là thêm đăng nhập Google, cách trong `HUONG_DAN_DANG_NHAP_GOOGLE.md` nhẹ hơn rất nhiều. Keycloak đáng làm khi bạn muốn giải quyết cả bảng ở mục 2.2, hoặc muốn thể hiện kiến trúc định danh chuẩn (OAuth2/OpenID Connect) trong đồ án. **Nếu làm Keycloak thì bỏ qua tài liệu Google kia** — đăng nhập Google khi đó cấu hình ngay trong Keycloak (mục 8.9).

---

## 3. Keycloak là gì và đảm nhận phần nào

Keycloak là máy chủ định danh mã nguồn mở: lưu tài khoản, hiển thị trang đăng nhập/đăng ký, phát hành token theo chuẩn OAuth2 / OpenID Connect.

### 3.1 Khái niệm cần biết

| Khái niệm | Ý nghĩa | Trong dự án này |
|---|---|---|
| **Realm** | Một không gian người dùng độc lập | 1 realm tên `ecommerce` |
| **Client** | Một ứng dụng được phép dùng realm để đăng nhập | `ecommerce-web` (FE React), `ecommerce-backend` (service gọi Admin API) |
| **Realm role** | Vai trò toàn hệ thống | `USER`, `ADMIN` |
| **Access token** | JWT ký RS256, gửi kèm mỗi request API. Sống ngắn | Thay cho JWT hiện tại |
| **Refresh token** | Dùng để xin access token mới mà không đăng nhập lại | Mới, hiện chưa có |
| **ID token** | Thông tin người dùng cho FE | FE đọc tên, email |
| **Authorization Code + PKCE** | Luồng đăng nhập chuẩn cho ứng dụng chạy trên trình duyệt: chuyển sang trang Keycloak, quay về kèm mã, đổi mã lấy token | Luồng FE sẽ dùng |
| **JWKS** | Địa chỉ công bố khóa công khai của Keycloak | Gateway tải về để kiểm tra chữ ký token |
| **Identity Provider** | Nguồn đăng nhập bên ngoài | Google |

### 3.2 Phân chia trách nhiệm mới

| Việc | Trước | Sau |
|---|---|---|
| Lưu mật khẩu, kiểm tra mật khẩu | User Service + Auth Service | **Keycloak** |
| Trang đăng nhập, đăng ký, quên mật khẩu | FE (`Login.jsx`, `Register.jsx`, `ForgotPassword.jsx`) | **Keycloak** (FE chỉ chuyển hướng) |
| Phát hành và gia hạn token | Auth Service | **Keycloak** |
| Vai trò USER/ADMIN | Cột `role` ở User Service | **Keycloak** (realm role) |
| Kiểm tra token, phân quyền theo đường dẫn | Gateway | Gateway (đổi cách kiểm tra) |
| Hồ sơ nội bộ: id số, tên, email dùng cho đơn hàng và email thông báo | User Service | User Service (bản sao đồng bộ từ Keycloak) |
| Đơn hàng, giỏ hàng, điểm, chat... | Các service | Không đổi |

---

## 4. Kiến trúc sau khi tích hợp

```
                        ┌──────────────────────────┐
                        │  Keycloak (realm         │◀── Google (tùy chọn)
                        │  "ecommerce") + DB riêng │
                        └───────▲──────────┬───────┘
     1. chuyển hướng đăng nhập  │          │ 3. khóa công khai (JWKS), tải 1 lần rồi cache
     2. nhận token (PKCE)       │          ▼
[FE React + keycloak-js] ───────┘   ┌──────────────┐
        │                           │ API Gateway  │
        │ 4. request kèm            │              │
        │    Bearer <access token> ▶│ a. kiểm tra chữ ký, hạn dùng, issuer
        │                           │ b. đổi "sub" (UUID) -> id nội bộ ──▶ User Service /api/users/provision
        │                           │    (kết quả cache trong Redis)
        │                           │ c. lấy vai trò từ realm_access.roles
        │                           │ d. áp luật phân quyền theo đường dẫn
        │                           │ e. gắn X-User-Id / X-User-Name / X-User-Role
        │                           └──────┬───────┘
        │                                  ▼
        │              Cart · Order · Product · Point · Shipping · Review · User · ...
        │              (đọc header như hiện nay)
        │
        └── WebSocket /ws ──▶ Chat Service: tự kiểm tra token Keycloak ở STOMP CONNECT
```

Token của Keycloak (phần nội dung, rút gọn):

```json
{
  "iss": "http://localhost:8080/realms/ecommerce",
  "sub": "3f1c2b9e-....-uuid",
  "preferred_username": "truong",
  "email": "truong@example.com",
  "email_verified": true,
  "given_name": "Truong", "family_name": "Nguyen",
  "realm_access": { "roles": ["USER", "ADMIN"] },
  "azp": "ecommerce-web",
  "exp": 1790000300
}
```

So với token hiện tại: không còn `userId` (số) và `role` (chuỗi đơn). Gateway chịu trách nhiệm quy đổi hai thứ đó.

---

## 5. Nghiệp vụ sau khi tích hợp

### 5.1 Đăng ký

1. Khách bấm "Đăng ký" trên FE → FE gọi `keycloak.register()` → trình duyệt sang trang đăng ký của Keycloak.
2. Khách nhập tên đăng nhập, email, họ tên, mật khẩu. Keycloak kiểm tra trùng lặp và chính sách mật khẩu.
3. Nếu bật xác minh email: Keycloak gửi thư, khách bấm liên kết xác nhận.
4. Keycloak tự gán vai trò mặc định `USER`, đăng nhập luôn và chuyển về FE kèm token.
5. Request API đầu tiên đi qua Gateway → Gateway chưa biết người này → gọi User Service `POST /api/users/provision` → User Service **tạo hồ sơ nội bộ** (cấp id số) → Gateway cache lại.

Khác biệt nghiệp vụ: hồ sơ nội bộ được tạo ở lần gọi API đầu tiên, không phải lúc bấm đăng ký. Tài khoản chưa từng gọi API sẽ chưa có trong bảng `users`.

### 5.2 Đăng nhập

1. Khách bấm "Đăng nhập" → `keycloak.login()` → sang trang Keycloak.
2. Khách nhập tài khoản (hoặc bấm "Google"). Keycloak kiểm tra, áp chống dò mật khẩu.
3. Keycloak chuyển về FE kèm **mã một lần**; thư viện `keycloak-js` đổi mã lấy access token, refresh token, ID token (PKCE bảo đảm chỉ đúng trình duyệt đã bắt đầu đăng nhập mới đổi được).
4. FE gọi `GET /api/users/me` để lấy hồ sơ nội bộ (id số) và hiển thị.

Nếu khách đã đăng nhập Keycloak ở tab khác, bước 2 được bỏ qua (đăng nhập một lần — SSO).

### 5.3 Giữ phiên đăng nhập (gia hạn token)

- Access token sống 5 phút. Trước mỗi request, FE gọi `keycloak.updateToken(30)`: nếu token còn dưới 30 giây thì dùng refresh token xin token mới, người dùng không thấy gì.
- Phiên kết thúc khi: người dùng đăng xuất, hết thời gian phiên tối đa (cấu hình ở realm, ví dụ 10 giờ), không hoạt động quá lâu (ví dụ 30 phút), hoặc quản trị viên hủy phiên.
- Khi không gia hạn được, FE đưa người dùng về trang đăng nhập.

### 5.4 Đăng xuất

1. FE gọi `keycloak.logout()` → Keycloak hủy phiên và refresh token → chuyển về FE.
2. Access token đã phát vẫn hợp lệ cho tới khi hết hạn (tối đa 5 phút). Đây là đánh đổi chuẩn của JWT; thời hạn ngắn là biện pháp chính.
3. Cơ chế blacklist trong Redis của Auth Service không còn dùng.

### 5.5 Quên mật khẩu, đổi mật khẩu, sửa hồ sơ

- **Quên mật khẩu:** liên kết "Forgot password?" trên trang đăng nhập Keycloak → nhận email chứa liên kết đặt lại. Thay cho luồng OTP 6 số tự viết.
- **Đổi mật khẩu, đổi email, họ tên:** FE mở trang quản lý tài khoản của Keycloak (`keycloak.accountManagement()`). Form "Edit Profile / Change Password" trong `Profile.jsx` được thay bằng nút dẫn sang đó.
- **Đồng bộ về User Service:** hồ sơ nội bộ (email, họ tên) được cập nhật ở lần Gateway gọi `provision` kế tiếp (cache 1 giờ). Email thông báo đơn hàng lấy từ hồ sơ nội bộ, nên sau khi đổi email có thể trễ tối đa 1 giờ.

### 5.6 Đăng nhập Google

Cấu hình Google là Identity Provider trong Keycloak. Trang đăng nhập có thêm nút "Google". Lần đầu, Keycloak tạo tài khoản từ thông tin Google (hoặc đề nghị liên kết nếu email đã tồn tại và người dùng xác nhận quyền sở hữu). Backend và FE không cần code riêng.

### 5.7 Phân quyền

- Vai trò được gán trong Keycloak: `USER` (mặc định cho mọi người đăng ký), `ADMIN` (quản trị viên gán tay trong Admin Console).
- Gateway đọc `realm_access.roles`: có `ADMIN` thì `X-User-Role = ADMIN`, ngược lại `USER`.
- Các luật theo đường dẫn giữ nguyên: quản lý sản phẩm/danh mục, xem mọi đơn hàng, danh sách và xóa người dùng → ADMIN; điểm thưởng → chủ sở hữu hoặc ADMIN; v.v.
- Thay đổi vai trò có hiệu lực khi người dùng nhận token mới (tối đa 5 phút, hoặc đăng nhập lại).
- Cột `role` trong bảng `users` không còn là nguồn quyết định.

### 5.8 Quản lý người dùng (tab Users trong Admin)

| Thao tác | Cách làm sau khi có Keycloak |
|---|---|
| Xem danh sách | Vẫn `GET /api/users` (hồ sơ nội bộ) |
| Xóa / khóa người dùng | Phải tác động **cả hai nơi**: vô hiệu hóa tài khoản ở Keycloak (qua Admin API) và xóa/đánh dấu hồ sơ nội bộ. Chỉ xóa hồ sơ nội bộ thì người đó vẫn đăng nhập được và hồ sơ sẽ được tạo lại |
| Cấp quyền ADMIN, đặt lại mật khẩu, xem phiên đăng nhập | Làm trong Admin Console của Keycloak |

Khuyến nghị: **vô hiệu hóa** (`enabled = false`) thay vì xóa, để giữ liên kết với đơn hàng cũ.

### 5.9 Chat

Chat Service kiểm tra token ở frame STOMP CONNECT. Sau tích hợp: kiểm tra token Keycloak (RS256 qua JWKS) rồi cũng đổi `sub` sang id nội bộ bằng `provision`. Vì access token chỉ sống 5 phút trong khi kết nối chat kéo dài, token chỉ được kiểm tra **lúc kết nối**; khi tự kết nối lại, FE phải lấy token mới nhất.

### 5.10 Gọi giữa các service

Không đổi: service gọi nhau trực tiếp qua Eureka, truyền `X-User-Id` / `X-User-Role` khi cần. Một số service (Order, Shipping, Cart) đang chuyển tiếp header `Authorization`; token Keycloak vẫn được chuyển tiếp nhưng bên nhận không còn đọc được nó bằng khóa cũ — không gây lỗi vì các service đó `permitAll()` và dựa vào header danh tính (ngoại lệ là Review Service, xem mục 8.5).

---

## 6. Các quyết định thiết kế

### 6.1 Id người dùng: giữ id số nội bộ, ánh xạ từ `sub`

Đây là quyết định quan trọng nhất.

| Phương án | Mô tả | Đánh giá |
|---|---|---|
| **A. Giữ id số, Gateway ánh xạ** *(chọn)* | Bảng `users` thêm cột `keycloak_id`. Gateway đổi `sub` → id số qua User Service, cache Redis | Không đụng dữ liệu đơn hàng/giỏ/điểm/chat. Thêm 1 lần gọi nội bộ cho mỗi người dùng mỗi giờ |
| B. Dùng thẳng UUID của Keycloak làm `userId` | Bỏ id số | Phải chuyển đổi mọi dữ liệu đang lưu `userId`; User Service dùng `Long` làm khóa chính; rủi ro cao |
| C. Đưa id số vào token | User Service ghi id số vào thuộc tính người dùng ở Keycloak, token mang claim `app_user_id` | Gateway không cần gọi User Service. Nhưng phải gọi Admin API để ghi ngược, và token đầu tiên chưa có claim. Là bước tối ưu về sau |

Với phương án A, luồng `provision` ở User Service:

1. Tìm theo `keycloak_id` → có thì cập nhật email/họ tên nếu khác, trả về.
2. Không có → tìm theo **email** → có thì gắn `keycloak_id` vào hồ sơ đó (đây là cách tài khoản cũ được nối với Keycloak — xem mục 8.8).
3. Vẫn không có → tạo hồ sơ mới.

Bước 2 an toàn **chỉ khi email ở Keycloak đã được xác minh**. Vì vậy phải bật "Verify email" trong realm, và `provision` chỉ nối theo email khi token có `email_verified = true`.

### 6.2 Nơi kiểm tra token: chỉ ở Gateway (và Chat Service)

Giữ mô hình hiện tại. Các service con không trở thành "resource server" riêng. Lý do: ít thay đổi nhất, và cấu trúc tin-vào-header đã có sẵn. Điểm yếu sẵn có vẫn còn nguyên: gọi thẳng vào cổng service (bỏ qua Gateway) thì không có phân quyền, nên chỉ được để Gateway lộ ra ngoài.

Chat Service là ngoại lệ bắt buộc vì kết nối WebSocket không mang được header `Authorization` qua Gateway.

### 6.3 Số phận của Auth Service

| Endpoint hiện có | Sau tích hợp |
|---|---|
| `POST /auth/register`, `/auth/login` | Bỏ — Keycloak đảm nhận |
| `GET /auth/validate` | Bỏ — FE dùng `keycloak-js` |
| `POST /auth/logout` | Bỏ |
| `POST /auth/forgot-password`, `/auth/reset-password`, `PUT /auth/change-password` | Bỏ |
| `/proxy/**` | Không liên quan xác thực; giữ hoặc bỏ tùy còn dùng không |

Kết quả: Auth Service có thể **ngừng chạy** sau giai đoạn chuyển đổi. Route `/auth/**` ở Gateway gỡ bỏ.

### 6.4 Loại client cho FE

`ecommerce-web` là **public client** (không có client secret, vì mã FE ai cũng đọc được) dùng **Authorization Code + PKCE**. Không dùng luồng "Direct Access Grants" (FE tự thu mật khẩu rồi gửi cho Keycloak): luồng đó bỏ mất SSO, đăng nhập Google, xác thực 2 lớp, và đã bị loại khỏi khuyến nghị bảo mật hiện hành.

Hệ quả nghiệp vụ: form đăng nhập nằm trên trang Keycloak, không nằm trong FE.

### 6.5 Nơi lưu token ở FE

`keycloak-js` giữ token **trong bộ nhớ** của trang, không ghi `localStorage`. Tải lại trang thì thư viện kiểm tra phiên Keycloak (qua iframe ẩn hoặc chuyển hướng nhanh) để lấy token mới. An toàn hơn cách lưu `localStorage` hiện tại trước tấn công chèn mã (XSS).

---

## 7. Ảnh hưởng tới từng thành phần

| Thành phần | Mức thay đổi | Việc cần làm |
|---|---|---|
| **Keycloak** (mới) | Dựng mới | Container + DB, realm, client, role, email, theme |
| **API Gateway** | Lớn | Kiểm tra token bằng JWKS thay cho khóa dùng chung; đổi `sub` → id nội bộ; đọc vai trò từ `realm_access`; gỡ route và luật công khai của `/auth/**`, `POST /api/users` |
| **User Service** | Vừa | Cột `keycloak_id`; endpoint nội bộ `provision`; endpoint `GET /api/users/me`; bỏ băm/kiểm tra mật khẩu; chặn tạo user công khai |
| **Auth Service** | Ngừng dùng | Gỡ dần, cuối cùng tắt |
| **Review Service** | Vừa | Đang tự kiểm tra JWT và yêu cầu đã xác thực → đổi sang đọc header của Gateway (như Cart Service) |
| **Chat Service** | Vừa | Kiểm tra token Keycloak ở STOMP; đổi `sub` → id nội bộ |
| **Cart Service** | Không | Đã dùng header |
| **Order, Product, Point, Shipping** | Nhỏ (dọn dẹp) | Gỡ `JwtService`, `JwtAuthenticationFilter` và `jwt.secret` không còn tác dụng |
| **Notification, Inventory, Search, Payment** | Không | – |
| **FE** | Lớn | `keycloak-js`; viết lại `AuthContext`; bỏ `Login/Register/ForgotPassword`; interceptor gia hạn token; lấy hồ sơ qua `/api/users/me`; Chat dùng token mới; sửa `Profile` và tab Users của Admin |
| **Notification (email OTP quên mật khẩu)** | Bỏ 1 chức năng | Mẫu email đặt lại mật khẩu không còn dùng; Keycloak tự gửi email của nó |
| **Redis** | Thêm 1 loại khóa | `kc-user:<sub>` → id nội bộ; bỏ `blacklist:*`, `pwreset:*` |

---

## 8. Các bước thực hiện

Thứ tự được chọn để **sau mỗi giai đoạn hệ thống vẫn chạy**. Làm trên nhánh git riêng.

| Giai đoạn | Nội dung | Hệ thống sau giai đoạn |
|---|---|---|
| 1 | Dựng Keycloak, cấu hình realm | Chạy như cũ, Keycloak đứng riêng |
| 2 | User Service: `keycloak_id`, `provision`, `/me` | Chạy như cũ |
| 3 | Gateway chấp nhận token Keycloak (song song token cũ) | Cả hai loại token đều dùng được |
| 4 | Review Service, Chat Service | Cả hai loại token đều dùng được |
| 5 | FE chuyển sang `keycloak-js` | Người dùng đăng nhập qua Keycloak |
| 6 | Chuyển người dùng cũ | Tài khoản cũ dùng tiếp được |
| 7 | Gỡ bỏ cơ chế cũ | Chỉ còn Keycloak |
| 8 | Tùy chọn: Google, xác thực 2 lớp, theme | – |

### 8.1 Dựng Keycloak (giai đoạn 1)

Thêm vào `docker-compose.yml`:

```yaml
  keycloak-db:
    image: postgres:16
    container_name: keycloak-db
    environment:
      POSTGRES_DB: keycloak
      POSTGRES_USER: keycloak
      POSTGRES_PASSWORD: ${KEYCLOAK_DB_PASSWORD}
    volumes:
      - keycloak-db-data:/var/lib/postgresql/data
    networks:
      - ecommerce-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U keycloak"]
      interval: 10s
      timeout: 5s
      retries: 10

  keycloak:
    image: quay.io/keycloak/keycloak:26.0
    container_name: keycloak
    command: start-dev
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://keycloak-db:5432/keycloak
      KC_DB_USERNAME: keycloak
      KC_DB_PASSWORD: ${KEYCLOAK_DB_PASSWORD}
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
    ports:
      - "8080:8080"
    depends_on:
      keycloak-db:
        condition: service_healthy
    networks:
      - ecommerce-network
```

và khai báo `keycloak-db-data:` trong khối `volumes:` cuối file. Thêm `KEYCLOAK_DB_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD` vào `.env`.

Chỉ cần Keycloak để phát triển, không cần cả hệ thống trong Docker:

```bash
docker compose up -d keycloak-db keycloak
```

Mở <http://localhost:8080>, đăng nhập bằng `admin` và mật khẩu đã đặt. Cổng 8080 hiện chưa service nào dùng.

Ghi chú: `start-dev` chỉ dùng khi phát triển (HTTP, không cache). Bản chạy thật ở mục 9. Với Keycloak trước bản 26, hai biến tài khoản quản trị tên là `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`.

### 8.2 Cấu hình realm (giai đoạn 1)

Làm trong Admin Console.

**a. Tạo realm**
Góc trên trái → **Create realm** → Realm name: `ecommerce` → Create.

**b. Tạo vai trò**
**Realm roles → Create role**: tạo `USER`, rồi `ADMIN`.

**c. Gán vai trò mặc định**
**Realm settings → User registration → Default roles → Assign role** → chọn `USER`. Mọi tài khoản mới tự có vai trò này.

**d. Cài đặt đăng nhập**
**Realm settings → Login**, bật:

| Mục | Giá trị | Tác dụng |
|---|---|---|
| User registration | On | Cho tự đăng ký |
| Forgot password | On | Liên kết quên mật khẩu |
| Remember me | On | Tùy chọn |
| Login with email | On | Đăng nhập bằng email hoặc tên đăng nhập |
| Verify email | On | **Bắt buộc** để nối tài khoản cũ theo email an toàn (mục 6.1) |

**e. Gửi email**
**Realm settings → Email**: From = địa chỉ Gmail của shop; Host `smtp.gmail.com`; Port `587`; Enable StartTLS: On; Authentication: On; Username = địa chỉ Gmail; Password = App Password. Bấm **Test connection**.

**f. Thời hạn token và phiên**
**Realm settings → Tokens**: Access Token Lifespan = `5 minutes`.
**Realm settings → Sessions**: SSO Session Idle = `30 minutes`, SSO Session Max = `10 hours` (chỉnh theo nhu cầu).

**g. Chống dò mật khẩu**
**Realm settings → Security defenses → Brute force detection**: chọn "Lockout temporarily".

**h. Client cho FE**
**Clients → Create client**:

| Bước | Mục | Giá trị |
|---|---|---|
| General | Client type | OpenID Connect |
| | Client ID | `ecommerce-web` |
| Capability | Client authentication | **Off** (public client) |
| | Standard flow | On |
| | Direct access grants | Off |
| Login settings | Root URL | `http://localhost:5173` |
| | Valid redirect URIs | `http://localhost:5173/*` |
| | Valid post logout redirect URIs | `http://localhost:5173/*` |
| | Web origins | `http://localhost:5173` |

Sau khi tạo: tab **Advanced → Advanced settings → Proof Key for Code Exchange Code Challenge Method** = `S256`.

Khi deploy, thêm địa chỉ FE thật vào ba ô URL trên. Không dùng `*` đơn lẻ cho redirect URI.

**i. Client cho backend gọi Admin API** (dùng ở mục 8.7)
**Clients → Create client**: Client ID `ecommerce-backend`; Client authentication **On**; chỉ bật **Service accounts roles**; tắt Standard flow. Sau khi tạo: tab **Service accounts roles → Assign role → Filter by clients** → gán `manage-users` và `view-users` của client `realm-management`. Tab **Credentials** → sao chép Client secret (bí mật, đặt qua biến môi trường).

**j. Tạo tài khoản quản trị của shop**
**Users → Create new user** → điền thông tin, Email verified: On → tab **Credentials** đặt mật khẩu (Temporary: Off) → tab **Role mapping → Assign role** → `ADMIN`.

**k. Lưu cấu hình vào git**
Để người khác dựng lại giống hệt, xuất realm ra file (không kèm người dùng):

```bash
docker exec keycloak /opt/keycloak/bin/kc.sh export --realm ecommerce --users skip --file /tmp/ecommerce-realm.json
docker cp keycloak:/tmp/ecommerce-realm.json ./keycloak/ecommerce-realm.json
```

Lần sau mount thư mục `./keycloak` vào `/opt/keycloak/data/import` và thêm cờ `--import-realm` vào `command`. Trước khi commit, kiểm tra file không chứa mật khẩu SMTP hay client secret.

### 8.3 User Service (giai đoạn 2)

Chưa biên dịch thử.

**a. Entity `User`** — thêm cột:

```java
    // "sub" của Keycloak. Null với tài khoản cũ chưa đăng nhập qua Keycloak lần nào.
    @Column(name = "keycloak_id", unique = true)
    private String keycloakId;
```

`ddl-auto: update` sẽ tự thêm cột. Cột `password` giữ lại trong giai đoạn chuyển đổi, xóa ở giai đoạn 7.

**b. Repository** — thêm `Optional<User> findByKeycloakId(String keycloakId);`

**c. DTO `ProvisionUserRequest`**: `keycloakId`, `username`, `email`, `emailVerified`, `firstName`, `lastName`.

**d. Service — tìm hoặc tạo hồ sơ:**

```java
    @Transactional
    public UserDto provision(ProvisionUserRequest request) {
        // 1. Đã liên kết từ trước: đồng bộ email/họ tên nếu người dùng vừa đổi bên Keycloak
        Optional<User> linked = userRepository.findByKeycloakId(request.getKeycloakId());
        if (linked.isPresent()) {
            return userMapper.toDto(syncProfile(linked.get(), request));
        }

        // 2. Tài khoản cũ cùng email: nối lại, CHỈ KHI Keycloak đã xác minh email đó
        if (request.isEmailVerified() && request.getEmail() != null) {
            Optional<User> byEmail = userRepository.findByEmail(request.getEmail());
            if (byEmail.isPresent()) {
                User user = byEmail.get();
                user.setKeycloakId(request.getKeycloakId());
                return userMapper.toDto(syncProfile(user, request));
            }
        }

        // 3. Người dùng mới
        User user = User.builder()
                .keycloakId(request.getKeycloakId())
                .username(uniqueUsername(request.getUsername()))
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role("USER")   // chỉ để tương thích; vai trò thật nằm ở Keycloak
                .build();
        return userMapper.toDto(userRepository.save(user));
    }
```

- `syncProfile`: cập nhật `email`, `firstName`, `lastName` nếu khác rồi `save`. Nhớ xóa cache Redis của user đó (`@CacheEvict` cho `users`, `usersByEmail`), vì User Service đang cache hồ sơ 60 phút.
- `uniqueUsername`: nếu tên đã có người dùng (trùng với một tài khoản cũ khác email), thêm hậu tố.
- Trường hợp bước 2 không chạy vì email chưa xác minh mà email đã tồn tại: `save` ở bước 3 sẽ lỗi do cột `email` là duy nhất. Trả lỗi rõ ràng ("email chưa được xác minh") thay vì 500.

**e. Controller:**

```java
    /** NỘI BỘ: chỉ Gateway và Chat Service gọi. Gateway chặn đường dẫn này từ bên ngoài. */
    @PostMapping("/provision")
    public ResponseEntity<UserDto> provision(@RequestBody ProvisionUserRequest request) {
        return ResponseEntity.ok(userService.provision(request));
    }

    /** Hồ sơ của người đang đăng nhập (FE gọi sau khi đăng nhập để biết id nội bộ). */
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }
```

Khai báo `/me` và `/provision` **trước** `/{id}` không bắt buộc (Spring ưu tiên đường dẫn cố định), nhưng kiểm tra lại khi chạy vì `{id}` đang nhận kiểu `Long`.

### 8.4 API Gateway (giai đoạn 3)

**Phần này đã biên dịch thành công.**

**a. `pom.xml`** — thêm:

```xml
<!-- Kiểm tra token RS256 của Keycloak bằng khóa công khai (JWKS) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

`GatewaySecurityConfig` hiện có (cho phép tất cả ở tầng Spring Security) **giữ nguyên**; không bật `.oauth2ResourceServer()` ở đó, vì việc chặn 401/403 vẫn do `JwtGatewayFilter` đảm nhận. Thư viện mới chỉ dùng để có sẵn bean `ReactiveJwtDecoder`.

**b. `application.yaml`** — thêm:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # Nơi tải khóa công khai: địa chỉ Gateway nhìn thấy Keycloak (trong Docker là tên container)
          jwk-set-uri: ${KEYCLOAK_JWKS_URI:http://localhost:8080/realms/ecommerce/protocol/openid-connect/certs}
          # Giá trị "iss" phải có trong token: địa chỉ TRÌNH DUYỆT nhìn thấy Keycloak
          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8080/realms/ecommerce}
```

Hai địa chỉ tách riêng vì trong Docker chúng khác nhau: trình duyệt gọi `localhost:8080` (hoặc tên miền thật), còn Gateway gọi `keycloak:8080`. Khi có cả hai, Spring tải khóa từ `jwk-set-uri` và chỉ dùng `issuer-uri` để so khớp chuỗi `iss`.

**c. `config/WebClientConfig.java`** (file mới):

```java
package com.example.API.Gateway.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    // @LoadBalanced: cho phép gọi "http://user-service" theo tên đăng ký trên Eureka
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
```

**d. `security/LocalUserResolver.java`** (file mới):

```java
package com.example.API.Gateway.security;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Đổi "sub" của Keycloak (UUID) thành id người dùng nội bộ (số trong bảng users của User Service),
 * vì toàn bộ đơn hàng, giỏ hàng, điểm, chat đang gắn với id nội bộ.
 * Lần đầu gặp một người dùng: gọi User Service để tìm hoặc tạo hồ sơ; các lần sau đọc từ Redis.
 */
@Component
public class LocalUserResolver {

    private static final String CACHE_PREFIX = "kc-user:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final ReactiveStringRedisTemplate redis;
    private final WebClient userServiceClient;

    public LocalUserResolver(ReactiveStringRedisTemplate redis, WebClient.Builder loadBalancedWebClientBuilder) {
        this.redis = redis;
        this.userServiceClient = loadBalancedWebClientBuilder.baseUrl("http://user-service").build();
    }

    public Mono<String> resolve(Jwt jwt) {
        String cacheKey = CACHE_PREFIX + jwt.getSubject();
        return redis.opsForValue().get(cacheKey)
                .onErrorResume(e -> Mono.empty())          // Redis lỗi thì bỏ qua cache, vẫn phục vụ được
                .switchIfEmpty(Mono.defer(() -> provision(jwt)
                        .flatMap(userId -> redis.opsForValue().set(cacheKey, userId, CACHE_TTL)
                                .onErrorResume(e -> Mono.just(false))
                                .thenReturn(userId))));
    }

    private Mono<String> provision(Jwt jwt) {
        Map<String, Object> body = new HashMap<>();
        body.put("keycloakId", jwt.getSubject());
        body.put("username", jwt.getClaimAsString("preferred_username"));
        body.put("email", jwt.getClaimAsString("email"));
        body.put("emailVerified", Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")));
        body.put("firstName", jwt.getClaimAsString("given_name"));
        body.put("lastName", jwt.getClaimAsString("family_name"));

        return userServiceClient.post()
                .uri("/api/users/provision")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(user -> String.valueOf(user.get("id")));
    }
}
```

**e. `security/JwtGatewayFilter.java`** (thay toàn bộ file):

```java
package com.example.API.Gateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtGatewayFilter implements GlobalFilter {

    /** Danh tính sau khi xác thực: id nội bộ, tên đăng nhập, vai trò (USER/ADMIN). */
    private record Identity(String userId, String username, String role) {
        boolean isAdmin() {
            return "ADMIN".equals(role);
        }
    }

    // Tự cấu hình từ spring.security.oauth2.resourceserver.jwt.* : kiểm tra chữ ký RS256 bằng khóa công khai
    // của Keycloak (JWKS), hạn dùng và nơi phát hành (issuer)
    private final ReactiveJwtDecoder jwtDecoder;
    private final LocalUserResolver userResolver;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        // 1. Đường dẫn công khai
        if (isPublic(path, method)) {
            return chain.filter(exchange);
        }

        // API nội bộ giữa Gateway và User Service: không cho gọi từ bên ngoài
        if (path.equals("/api/users/provision")) {
            return reject(exchange, HttpStatus.FORBIDDEN);
        }

        // 2. Lấy token
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        // 3. Xác thực token với Keycloak rồi đổi sang danh tính nội bộ
        return jwtDecoder.decode(authHeader.substring(7))
                .flatMap(jwt -> userResolver.resolve(jwt)
                        .map(userId -> new Identity(userId, jwt.getClaimAsString("preferred_username"), roleOf(jwt))))
                .map(Optional::of)
                .onErrorResume(e -> {
                    log.debug("Từ chối token cho {}: {}", path, e.getMessage());
                    return Mono.just(Optional.empty());
                })
                .flatMap(identity -> identity.isEmpty()
                        ? reject(exchange, HttpStatus.UNAUTHORIZED)
                        : authorize(exchange, chain, identity.get(), path, method));
    }

    private boolean isPublic(String path, HttpMethod method) {
        // WebSocket/SockJS của Chat Service: Chat Service tự kiểm tra token ở frame STOMP CONNECT
        if (path.equals("/ws") || path.startsWith("/ws/")) {
            return true;
        }
        // Khách vãng lai được xem sản phẩm, danh mục, đánh giá
        return method == HttpMethod.GET
                && (path.startsWith("/api/products") || path.startsWith("/api/categories") || path.startsWith("/api/reviews"));
    }

    // Vai trò nằm trong claim realm_access.roles của token Keycloak
    private String roleOf(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        Object roles = realmAccess == null ? null : realmAccess.get("roles");
        return roles instanceof List<?> list && list.contains("ADMIN") ? "ADMIN" : "USER";
    }

    // 4. Phân quyền theo nghiệp vụ (giữ nguyên các luật đang có)
    private Mono<Void> authorize(ServerWebExchange exchange, GatewayFilterChain chain,
                                 Identity identity, String path, HttpMethod method) {
        if (isForbidden(identity, path, method)) {
            log.debug("Forbidden: {} {} bởi {} ({})", method, path, identity.username(), identity.role());
            return reject(exchange, HttpStatus.FORBIDDEN);
        }

        // 5. Gửi danh tính xuống service con qua header. Ghi đè mọi giá trị client tự gửi lên.
        ServerWebExchange mutated = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> {
                            headers.set("X-User-Id", identity.userId());
                            headers.set("X-User-Name", identity.username());
                            headers.set("X-User-Role", identity.role());
                        })
                        .build())
                .build();
        return chain.filter(mutated);
    }

    private boolean isForbidden(Identity identity, String path, HttpMethod method) {
        boolean write = method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE;

        // Order: xem tất cả / lọc đơn -> ADMIN
        if ((path.equals("/api/orders") || path.equals("/api/orders/filter")) && method == HttpMethod.GET) {
            return !identity.isAdmin();
        }
        // Product, Category: thêm/sửa/xóa -> ADMIN
        if ((path.startsWith("/api/products") || path.startsWith("/api/categories")) && write) {
            return !identity.isAdmin();
        }
        // User: danh sách và xóa người dùng -> ADMIN
        if ((path.equals("/api/users") && method == HttpMethod.GET)
                || (path.startsWith("/api/users/") && method == HttpMethod.DELETE)) {
            return !identity.isAdmin();
        }
        // Point: chỉ đọc; số dư/lịch sử chỉ chủ sở hữu hoặc ADMIN; /config ai đăng nhập cũng xem được
        if (path.startsWith("/api/points")) {
            if (method != HttpMethod.GET) {
                return true;
            }
            String[] parts = path.split("/");
            String targetUserId = parts.length > 3 ? parts[3] : null;
            return !path.equals("/api/points/config") && !identity.isAdmin()
                    && !identity.userId().equals(targetUserId);
        }
        // Cart: xóa cả giỏ theo id (/api/cart/{userId}) -> chủ sở hữu hoặc ADMIN
        if (path.startsWith("/api/cart") && method == HttpMethod.DELETE) {
            String[] parts = path.split("/");
            if (parts.length == 4 && !parts[3].equals("items")) {
                return !identity.isAdmin() && !identity.userId().equals(parts[3]);
            }
        }
        return false;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }
}
```

Khác biệt có chủ ý so với bộ lọc hiện tại:

- **Bỏ luật công khai `POST /api/users`** → đóng lỗ hổng tự đăng ký ADMIN. Thêm `POST /api/users` vào danh sách chỉ ADMIN nếu vẫn muốn giữ endpoint.
- **Bỏ luật công khai `/auth/**`** (áp dụng ở giai đoạn 7; trong giai đoạn 3–6 xem mục f).
- Dùng `path.startsWith` thay cho `path.contains` cho các đường dẫn công khai: bản cũ coi mọi đường dẫn *chứa* `/auth/` là công khai, ví dụ `/api/points/auth/history` cũng lọt qua.
- Header danh tính được **ghi đè** (`set`) thay vì thêm, để client không tự gửi `X-User-Id` giả chồng lên.
- Lỗi khi gọi User Service hiện cũng trả 401. Nên tách riêng thành 503 khi hoàn thiện.

**f. Chạy song song hai loại token (giai đoạn 3 → 6)**

Để FE cũ vẫn dùng được trong lúc chuyển đổi, giữ lại `JwtService` cũ và thử lần lượt: giải mã bằng Keycloak trước; nếu thất bại thì thử khóa dùng chung cũ và lấy `userId`/`role` từ claim như hiện nay. Giữ thêm luật công khai `/auth/**`. Gỡ nhánh cũ ở giai đoạn 7.

**g. Gỡ route** `auth-service` trong `application.yaml` ở giai đoạn 7.

### 8.5 Review Service và Chat Service (giai đoạn 4)

Chưa biên dịch thử.

**Review Service.** Hiện tự kiểm tra JWT bằng khóa dùng chung và yêu cầu `authenticated()` cho POST/DELETE → token Keycloak sẽ bị coi là chưa đăng nhập, **không viết được đánh giá**. Sửa:

1. Sao chép `GatewayHeaderAuthFilter` từ Cart Service (đọc `X-User-Id`, `X-User-Role`, đặt vào `SecurityContext` với principal là **id người dùng**).
2. Trong `SecurityConfig`, thay `jwtAuthenticationFilter` bằng bộ lọc đó.
3. `ReviewServiceImpl` đang lấy `currentUserId` từ `SecurityContext` để so với `userId` trong request. Bộ lọc JWT cũ đặt principal là **tên đăng nhập**, không phải id; sau khi đổi, principal là id nên phép so khớp mới đúng nghĩa. Kiểm thử kỹ luồng tạo và xóa đánh giá.

**Chat Service.** `StompAuthChannelInterceptor` gọi `JwtService.parse(token)`:

1. Thêm `spring-boot-starter-oauth2-resource-server` vào `pom.xml` và cấu hình `jwk-set-uri` / `issuer-uri` như Gateway. Chat Service không có Spring Security filter nên thêm một `SecurityFilterChain` cho phép tất cả, hoặc tự tạo bean:
   ```java
   @Bean
   JwtDecoder jwtDecoder(@Value("${keycloak.jwks-uri}") String jwksUri, @Value("${keycloak.issuer-uri}") String issuer) {
       NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
       decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
       return decoder;
   }
   ```
   (khi đó chỉ cần thư viện `spring-security-oauth2-jose`, không kéo cả Spring Security web vào).
2. Viết lại `JwtService.parse`: giải mã bằng `JwtDecoder`; lấy `preferred_username`; vai trò từ `realm_access.roles`; **id nội bộ** bằng cách gọi `POST http://user-service/api/users/provision` (Feign hoặc `RestClient`), cache trong bộ nhớ theo `sub`.
3. Phần còn lại của Chat Service không đổi vì `StompPrincipal` vẫn mang id nội bộ.

**Order, Product, Point, Shipping, User Service.** Bộ lọc JWT của các service này bỏ qua token không đọc được và `permitAll()`, nên **không hỏng** khi nhận token Keycloak. Dọn dẹp ở giai đoạn 7.

### 8.6 Frontend (giai đoạn 5)

Chưa chạy thử.

**a. Cài thư viện và cấu hình**

```bash
cd FE
npm install keycloak-js
```

`FE/.env`:

```env
VITE_API_URL=http://localhost:8085
VITE_KEYCLOAK_URL=http://localhost:8080
VITE_KEYCLOAK_REALM=ecommerce
VITE_KEYCLOAK_CLIENT_ID=ecommerce-web
```

Tạo `FE/public/silent-check-sso.html` (để kiểm tra phiên khi tải lại trang mà không chuyển hướng cả trang):

```html
<!doctype html>
<html><body><script>parent.postMessage(location.href, location.origin);</script></body></html>
```

**b. `src/services/keycloak.js`** (file mới):

```js
import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
    url: import.meta.env.VITE_KEYCLOAK_URL,
    realm: import.meta.env.VITE_KEYCLOAK_REALM,
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
});

// init() chỉ được gọi 1 lần cho mỗi đối tượng Keycloak. React.StrictMode chạy effect 2 lần
// lúc dev, nên giữ lại promise để lần gọi thứ hai dùng chung.
let initPromise = null;
export const initKeycloak = () => {
    if (!initPromise) {
        initPromise = keycloak.init({
            onLoad: 'check-sso',
            pkceMethod: 'S256',
            silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        });
    }
    return initPromise;
};

export default keycloak;
```

**c. `src/context/AuthContext.jsx`** — viết lại, giữ nguyên các trường mà phần còn lại của FE đang dùng (`user`, `token`, `loading`, `logout`):

```jsx
import React, { createContext, useContext, useEffect, useState } from 'react';
import keycloak, { initKeycloak } from '../services/keycloak';
import api from '../api/axios';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    initKeycloak()
      .then(async (authenticated) => {
        if (!authenticated) return;
        setToken(keycloak.token);
        // Id nội bộ (số) lấy từ User Service, vì token Keycloak chỉ có UUID
        const res = await api.get('/api/users/me');
        setUser({
          id: res.data.id,
          username: keycloak.tokenParsed?.preferred_username,
          role: keycloak.hasRealmRole('ADMIN') ? 'ADMIN' : 'USER',
        });
      })
      .catch(err => console.error('Keycloak init failed', err))
      .finally(() => setLoading(false));

    // Cập nhật state mỗi khi token được gia hạn (Chat dùng token này để kết nối lại)
    keycloak.onAuthRefreshSuccess = () => setToken(keycloak.token);
    keycloak.onAuthLogout = () => { setUser(null); setToken(null); };
  }, []);

  const login = () => keycloak.login();
  const register = () => keycloak.register();
  const logout = () => keycloak.logout({ redirectUri: window.location.origin });
  const openAccount = () => keycloak.accountManagement();

  return (
    <AuthContext.Provider value={{ user, token, loading, login, register, logout, openAccount }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
```

**d. `src/api/axios.js`** — gia hạn token trước mỗi request:

```js
import axios from 'axios';
import keycloak from '../services/keycloak';

const instance = axios.create({ baseURL: import.meta.env.VITE_API_URL });

instance.interceptors.request.use(async (config) => {
    if (keycloak.authenticated) {
        try {
            await keycloak.updateToken(30);   // gia hạn nếu còn dưới 30 giây
        } catch {
            keycloak.login();                 // phiên đã hết: đăng nhập lại
            throw new axios.Cancel('Phiên đăng nhập đã hết');
        }
        config.headers.Authorization = `Bearer ${keycloak.token}`;
    }
    return config;
});

export default instance;
```

**e. Các thay đổi còn lại**

| File | Thay đổi |
|---|---|
| `pages/Login.jsx`, `Register.jsx`, `ForgotPassword.jsx` | Xóa. Route `/login`, `/register` gọi `login()` / `register()` rồi hiển thị "Đang chuyển tới trang đăng nhập..." |
| `App.jsx` | Route cần đăng nhập: nếu chưa có `token` thì gọi `login()` thay vì `<Navigate to="/login" />` |
| `components/Navbar.jsx` | Nút Login gọi `login()`; `handleLogout` chỉ gọi `logout()` (Keycloak tự chuyển hướng) |
| `services/AuthService.js` | Xóa (không còn gọi `/auth/*`) |
| `pages/Profile.jsx` | Thay modal "Edit Profile / Change Password" bằng nút gọi `openAccount()` |
| `services/ChatService.js` | Trong hàm `open()`, lấy token mới nhất lúc kết nối: `await keycloak.updateToken(30)` rồi dùng `keycloak.token`, thay cho `token` truyền vào lúc đầu (token cũ sẽ hết hạn sau 5 phút, lần kết nối lại sẽ bị từ chối) |
| `pages/Admin.jsx` (tab Users) | Xóa người dùng → gọi API mới ở mục 8.7 |
| Mọi nơi đọc `localStorage.getItem('token')` | Bỏ; token nằm trong `keycloak.token` |

**f. Lưu ý trình duyệt.** Kiểm tra phiên ẩn (`check-sso` qua iframe) cần cookie của Keycloak. Khi FE và Keycloak khác tên miền gốc, một số trình duyệt chặn cookie bên thứ ba → người dùng phải đăng nhập lại mỗi lần tải trang. Khắc phục khi deploy: đặt Keycloak cùng tên miền gốc với FE (ví dụ `shop.example.com` và `auth.example.com`, hoặc cùng tên miền khác đường dẫn).

### 8.7 Quản trị người dùng qua Admin API (giai đoạn 5)

Để nút xóa ở tab Users có hiệu lực thật, User Service gọi Keycloak Admin API bằng client `ecommerce-backend`.

1. Lấy token dịch vụ (client credentials):
   ```
   POST {keycloak}/realms/ecommerce/protocol/openid-connect/token
   Content-Type: application/x-www-form-urlencoded

   grant_type=client_credentials&client_id=ecommerce-backend&client_secret=<bí mật>
   ```
2. Vô hiệu hóa tài khoản:
   ```
   PUT {keycloak}/admin/realms/ecommerce/users/{keycloakId}
   Authorization: Bearer <token dịch vụ>
   Content-Type: application/json

   { "enabled": false }
   ```
3. Hủy mọi phiên đang mở: `POST {keycloak}/admin/realms/ecommerce/users/{keycloakId}/logout`.
4. Xóa khóa cache `kc-user:<keycloakId>` trong Redis và xử lý hồ sơ nội bộ.

Bọc 4 bước này trong `DELETE /api/users/{id}` của User Service. Cấu hình `KEYCLOAK_BACKEND_CLIENT_SECRET` qua biến môi trường. Có thể dùng thư viện `org.keycloak:keycloak-admin-client` (chọn bản khớp với máy chủ) thay cho gọi HTTP thủ công.

### 8.8 Chuyển người dùng cũ (giai đoạn 6)

Tài khoản hiện có nằm trong MySQL với mật khẩu băm **BCrypt**. Keycloak mặc định không đọc được BCrypt.

| Cách | Mô tả | Phù hợp khi |
|---|---|---|
| **1. Đăng ký lại, nối theo email** *(đơn giản nhất)* | Không nhập gì vào Keycloak. Người dùng cũ bấm "Đăng ký" trên Keycloak bằng **đúng email cũ**, xác minh email. Lần gọi API đầu, `provision` thấy email trùng và đã xác minh → gắn `keycloak_id` vào hồ sơ cũ. Đơn hàng, điểm, giỏ hàng còn nguyên | Ít người dùng, là đồ án/demo |
| **2. Nhập sẵn tài khoản, bắt đặt lại mật khẩu** | Dùng Admin API tạo người dùng trong Keycloak (username, email, họ tên, `emailVerified: true`, `requiredActions: ["UPDATE_PASSWORD"]`), rồi gửi email đặt mật khẩu (`PUT .../users/{id}/execute-actions-email`). Người dùng không phải đăng ký lại, chỉ đặt mật khẩu mới | Muốn giữ tên đăng nhập, số người dùng vừa phải |
| **3. Nhập cả mật khẩu băm** | Cài extension BCrypt cộng đồng cho Keycloak, nhập người dùng kèm `credentialData`/`secretData` dạng BCrypt. Người dùng đăng nhập bằng mật khẩu cũ, không hay biết gì | Nhiều người dùng thật, không chấp nhận gián đoạn. Phụ thuộc extension bên thứ ba, phải kiểm thử kỹ theo phiên bản |

Với cả ba cách: **tài khoản ADMIN phải được gán lại vai trò `ADMIN` trong Keycloak** (cột `role` cũ không tự chuyển sang).

Sau khi mọi tài khoản đang dùng đã có `keycloak_id`, chuyển sang giai đoạn 7.

### 8.9 Gỡ bỏ cơ chế cũ và tính năng tùy chọn (giai đoạn 7–8)

**Gỡ bỏ:**

- Gateway: nhánh token cũ, `JwtService` (jjwt), thư viện `jjwt-*`, `jwt.secret`, route và luật công khai `/auth/**`.
- Auth Service: ngừng chạy; xóa khỏi `docker-compose.yml`.
- User Service: cột `password`, `PasswordEncoder`, endpoint `/username/{username}/auth` và `/{id}/password`, công khai `POST /api/users`.
- Order, Product, Point, Shipping, Review, User: `JwtService`, `JwtAuthenticationFilter`, `jwt.secret` trong cấu hình.
- Cart: `JwtUtil` nếu không còn dùng.
- Notification: mẫu và endpoint email OTP đặt lại mật khẩu.
- Redis: các khóa `blacklist:*`, `pwreset:*` tự hết hạn.
- Coi khóa JWT cũ là đã lộ (nó nằm trong lịch sử git); sau giai đoạn này nó không còn giá trị.

**Tùy chọn:**

- **Google:** tạo OAuth Client ID trên Google Cloud Console (loại Web application) với **Authorized redirect URI** = `{keycloak}/realms/ecommerce/broker/google/endpoint`. Trong Keycloak: **Identity providers → Google** → dán Client ID và Client Secret. Bật **Trust Email** để email từ Google được coi là đã xác minh.
- **Xác thực 2 lớp:** **Authentication → Required actions** → bật "Configure OTP" (bắt buộc cho ADMIN bằng cách gán required action cho các tài khoản đó).
- **Giao diện trang đăng nhập:** tạo theme riêng (thư mục mount vào `/opt/keycloak/themes/`), chọn ở **Realm settings → Themes**. Tối thiểu: đổi logo, màu, và bật tiếng Việt ở **Realm settings → Localization**.

---

## 9. Triển khai Docker và Azure

### 9.1 Biến môi trường cho các service (trong Docker)

```yaml
  api-gateway:
    environment:
      - KEYCLOAK_JWKS_URI=http://keycloak:8080/realms/ecommerce/protocol/openid-connect/certs
      - KEYCLOAK_ISSUER_URI=${KEYCLOAK_PUBLIC_URL}/realms/ecommerce
  chat-service:
    environment:
      - KEYCLOAK_JWKS_URI=http://keycloak:8080/realms/ecommerce/protocol/openid-connect/certs
      - KEYCLOAK_ISSUER_URI=${KEYCLOAK_PUBLIC_URL}/realms/ecommerce
  user-service:
    environment:
      - KEYCLOAK_ADMIN_URL=http://keycloak:8080
      - KEYCLOAK_BACKEND_CLIENT_SECRET=${KEYCLOAK_BACKEND_CLIENT_SECRET}
```

`KEYCLOAK_PUBLIC_URL` là địa chỉ trình duyệt dùng, ví dụ `http://localhost:8080` (dev) hay `https://<tên-miền>/kc` (Azure). Nếu Keycloak nằm sau đường dẫn `/kc`, thêm `/kc` vào cả `KEYCLOAK_JWKS_URI`.

### 9.2 Keycloak chạy thật (Azure, sau Caddy)

Thay khối `keycloak` trong `docker-compose.azure.yml`:

```yaml
  keycloak:
    command: start
    restart: unless-stopped
    environment:
      KC_HOSTNAME: https://${DOMAIN}/kc
      KC_HTTP_RELATIVE_PATH: /kc
      KC_HTTP_ENABLED: "true"
      KC_PROXY_HEADERS: xforwarded
      JAVA_OPTS_KC_HEAP: "-Xms256m -Xmx640m"
  keycloak-db:
    restart: unless-stopped
```

`Caddyfile`:

```
{$DOMAIN} {
	handle /kc/* {
		reverse_proxy keycloak:8080
	}
	handle {
		reverse_proxy api-gateway:8085
	}
}
```

Giải thích:

- Dùng đường dẫn `/kc` vì VM Azure trong tài liệu deploy chỉ có **một tên miền**. Không dùng `/auth` (đường dẫn truyền thống của Keycloak cũ) vì trùng với route `/auth/**` của Auth Service trong giai đoạn chuyển đổi.
- `start` (thay cho `start-dev`) yêu cầu khai báo hostname và chạy sau HTTPS; Caddy lo HTTPS, `KC_PROXY_HEADERS` cho Keycloak biết tin vào header chuyển tiếp.
- Không mở cổng 8080 ra Internet; NSG vẫn chỉ mở 22, 80, 443.
- Thêm khoảng 1 GB RAM vào ước lượng của VM (`Standard_B4ms` 16 GB vẫn đủ).
- FE: `VITE_KEYCLOAK_URL=https://<tên-miền>/kc`. Trong Keycloak, thêm địa chỉ FE thật vào redirect URIs và Web origins của client `ecommerce-web`.
- Sao lưu thêm database của Keycloak: `docker exec keycloak-db pg_dump -U keycloak keycloak | gzip > keycloak-<ngày>.sql.gz`.

---

## 10. Kiểm thử

### 10.1 Kiểm tra nhanh bằng dòng lệnh

Để thử Gateway mà chưa cần FE, tạm bật **Direct access grants** cho client `ecommerce-web` (tắt lại sau khi thử) rồi:

```bash
# Lấy token
TOKEN=$(curl -s -X POST http://localhost:8080/realms/ecommerce/protocol/openid-connect/token \
  -d grant_type=password -d client_id=ecommerce-web \
  -d username=<tài khoản> -d password=<mật khẩu> | sed 's/.*"access_token":"\([^"]*\)".*/\1/')

# Gọi API qua Gateway
curl -i http://localhost:8085/api/users/me -H "Authorization: Bearer $TOKEN"
```

Dán token vào <https://jwt.io> để xem các claim (`iss`, `sub`, `realm_access`).

### 10.2 Danh sách kiểm thử nghiệp vụ

| # | Tình huống | Kết quả mong đợi |
|---|---|---|
| 1 | Đăng ký tài khoản mới | Nhận email xác minh; sau khi xác minh vào được FE; bảng `users` có dòng mới với `keycloak_id` |
| 2 | Đăng nhập, mở Giỏ hàng, Đơn hàng, Hồ sơ, Điểm | Hoạt động; `user.id` trên FE là id số |
| 3 | Để yên quá 5 phút rồi thao tác tiếp | Không bị đăng xuất; tab Network thấy request gia hạn token |
| 4 | Tải lại trang (F5) | Vẫn đăng nhập |
| 5 | Đăng xuất | Về trạng thái khách; mở trang cần đăng nhập thì bị chuyển sang Keycloak |
| 6 | Gọi API với token đã sửa 1 ký tự | 401 |
| 7 | Gọi API với token hết hạn | 401 |
| 8 | Gọi API không token | `/api/orders/...` trả 401; `GET /api/products/filter` trả 200 |
| 9 | Tài khoản USER gọi `POST /api/products`, `GET /api/orders`, `GET /api/users` | 403 |
| 10 | Tài khoản ADMIN làm các việc trên | Thành công; vào được `/admin` |
| 11 | Gán thêm vai trò ADMIN cho một USER đang đăng nhập | Có hiệu lực sau tối đa 5 phút hoặc khi đăng nhập lại |
| 12 | Client tự gửi header `X-User-Id: 1` kèm token của người khác | Service nhận id của chủ token, không phải `1` |
| 13 | Gọi `POST /api/users/provision` từ bên ngoài | 403 |
| 14 | Gọi `POST /api/users` với `role: ADMIN` không token | 401 (lỗ hổng cũ đã đóng) |
| 15 | Tài khoản cũ: đăng ký trên Keycloak bằng email cũ, xác minh | Thấy lại đơn hàng và điểm cũ; không tạo dòng `users` mới |
| 16 | Đặt hàng → thanh toán → admin giao hàng → viết đánh giá | Cả chuỗi chạy; đánh giá tạo được (Review Service) |
| 17 | Chat: khách nhắn, admin trả lời; để quá 5 phút rồi tắt/bật mạng | Chat kết nối lại được bằng token mới |
| 18 | Quên mật khẩu | Nhận email từ Keycloak, đặt lại được |
| 19 | Đổi email trong trang tài khoản Keycloak | Sau tối đa 1 giờ, hồ sơ nội bộ và email thông báo dùng địa chỉ mới |
| 20 | Admin xóa (vô hiệu hóa) một người dùng | Người đó không đăng nhập được nữa; phiên đang mở bị hủy |
| 21 | Nhập sai mật khẩu nhiều lần | Tài khoản bị khóa tạm |
| 22 | Dừng Keycloak khi đã có người đang đăng nhập | Request vẫn được phục vụ trong lúc token còn hạn (Gateway đã cache khóa công khai); không đăng nhập mới được |

---

## 11. Lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Mọi request đều 401, log Gateway có `The iss claim is not valid` | `issuer-uri` khác với `iss` trong token (khác `localhost` / tên container / tên miền, thừa hoặc thiếu `/kc`) | Giải mã token xem `iss`, đặt `KEYCLOAK_ISSUER_URI` đúng y hệt |
| 401, log có lỗi tải khóa / `Connection refused` | Gateway không tới được `jwk-set-uri` | Trong Docker dùng `http://keycloak:8080/...`; kiểm tra Keycloak đã chạy |
| Gateway không khởi động: thiếu bean `ReactiveJwtDecoder` | Chưa đặt `jwk-set-uri` hoặc thiếu thư viện | Mục 8.4 a, b |
| Trang Keycloak báo `Invalid parameter: redirect_uri` | Địa chỉ FE không khớp **Valid redirect URIs** | Thêm đúng origin kèm `/*` |
| Console trình duyệt báo CORS khi gọi `/protocol/openid-connect/token` | **Web origins** của client chưa có origin của FE | Thêm origin (không có `/` cuối) |
| FE lặp vô hạn giữa trang shop và Keycloak | `keycloak.init` bị gọi nhiều lần (StrictMode), hoặc iframe kiểm tra phiên bị chặn cookie | Dùng `initKeycloak()` như mục 8.6b; xem mục 8.6f |
| Đăng nhập được nhưng `/api/users/me` trả 401 | Gateway chưa chuyển sang Keycloak, hoặc `provision` lỗi | Xem log Gateway và User Service |
| `provision` lỗi 500 do trùng email | Email đã có ở hồ sơ cũ nhưng token chưa `email_verified` | Bật Verify email; người dùng xác minh rồi thử lại |
| ADMIN đăng nhập nhưng không vào được `/admin` | Chưa gán realm role `ADMIN`; hoặc tạo nhầm thành *client role* | Gán ở **Users → Role mapping**, kiểm tra `realm_access.roles` trong token |
| Viết đánh giá báo 401/403 | Review Service chưa đổi sang đọc header | Mục 8.5 |
| Chat báo "Token không hợp lệ" sau vài phút | FE kết nối lại bằng token cũ | Mục 8.6e (dòng `ChatService.js`) |
| Sau khi đổi vai trò/khóa tài khoản vẫn thao tác được vài phút | Access token còn hạn | Đúng thiết kế; giảm Access Token Lifespan nếu cần chặt hơn |
| Keycloak (chế độ `start`) báo lỗi hostname / HTTPS required | Thiếu `KC_HOSTNAME`, `KC_PROXY_HEADERS`, hoặc truy cập bằng HTTP | Mục 9.2 |
| Trang Keycloak sau Caddy hiển thị vỡ, thiếu CSS | `KC_HTTP_RELATIVE_PATH` và đường dẫn ở Caddy không khớp | Cả hai cùng là `/kc` |
| Không nhận được email xác minh | SMTP sai, hoặc Gmail chặn | **Test connection** ở Realm settings → Email; dùng App Password |

---

## 12. Bảo mật

1. **Chỉ Gateway (và trang Keycloak) lộ ra Internet.** Các service con vẫn tin vào header `X-User-*`; gọi thẳng vào cổng service là giả mạo được bất kỳ ai.
2. **`/api/users/provision` là API nội bộ.** Gateway đã chặn; không mở cổng User Service ra ngoài. API này tin hoàn toàn vào dữ liệu được gửi tới.
3. **Chỉ nối tài khoản cũ theo email khi email đã xác minh.** Bỏ điều kiện này là cho phép chiếm tài khoản: đăng ký trên Keycloak bằng email của người khác rồi nhận luôn hồ sơ, đơn hàng, điểm của họ.
4. **Client FE là public, bắt buộc PKCE S256, redirect URI khai báo chính xác.** Không dùng `*`.
5. **Tắt Direct access grants** sau khi kiểm thử.
6. **Bí mật đặt qua biến môi trường:** mật khẩu admin Keycloak, mật khẩu DB, client secret của `ecommerce-backend`, mật khẩu SMTP. Không commit; kiểm tra file realm xuất ra trước khi đưa lên git.
7. **Tài khoản `admin` của Keycloak khác tài khoản ADMIN của shop.** Tài khoản quản trị Keycloak (realm `master`) có toàn quyền trên mọi người dùng: đặt mật khẩu mạnh, bật xác thực 2 lớp, không dùng hằng ngày.
8. **Quyền của `ecommerce-backend` ở mức tối thiểu** (`manage-users`, `view-users`); không gán `realm-admin`.
9. **Access token ngắn (5 phút).** Đừng tăng lên hàng giờ cho tiện — đó là thứ thay thế cho việc thu hồi token.
10. **Luôn HTTPS khi chạy thật**, cho cả FE, Gateway và Keycloak.
11. **Bật Brute force detection; cân nhắc bắt buộc xác thực 2 lớp cho ADMIN.**
12. **Cập nhật Keycloak định kỳ** — đây là thành phần bảo mật trung tâm, các bản vá thường xuyên có.

---

## 13. Danh sách file thay đổi

| File | Thay đổi | Đã biên dịch |
|---|---|---|
| `docker-compose.yml`, `.env` | Thêm `keycloak`, `keycloak-db`, volume, biến môi trường | – |
| `keycloak/ecommerce-realm.json` | **Mới** — cấu hình realm xuất ra | – |
| `API Gateway/.../pom.xml` | Thêm `spring-boot-starter-oauth2-resource-server`; về sau gỡ `jjwt-*` | Có |
| `API Gateway/.../application.yaml` | `jwk-set-uri`, `issuer-uri`; gỡ route `auth-service`, `jwt.secret` | – |
| `API Gateway/.../config/WebClientConfig.java` | **Mới** | Có |
| `API Gateway/.../security/LocalUserResolver.java` | **Mới** | Có |
| `API Gateway/.../security/JwtGatewayFilter.java` | Viết lại | Có |
| `API Gateway/.../security/JwtService.java` | Xóa ở giai đoạn 7 | – |
| `User Service/.../entity/User.java` | Thêm `keycloakId`; về sau bỏ `password` | Chưa |
| `User Service/.../repository/UserRepository.java` | `findByKeycloakId` | Chưa |
| `User Service/.../dto/ProvisionUserRequest.java` | **Mới** | Chưa |
| `User Service/.../service/impl/UserServiceImpl.java` | `provision`; xóa người dùng gọi Keycloak Admin API | Chưa |
| `User Service/.../controller/UserController.java` | `POST /provision`, `GET /me` | Chưa |
| `ReviewService/.../filter/GatewayHeaderAuthFilter.java` | **Mới** (sao chép từ Cart Service) | Chưa |
| `ReviewService/.../config/SecurityConfig.java` | Dùng bộ lọc header thay bộ lọc JWT | Chưa |
| `ChatService/.../security/JwtService.java`, `pom.xml`, `application.yml` | Kiểm tra token Keycloak; đổi `sub` → id nội bộ | Chưa |
| Order, Product, Point, Shipping, Review, User: `JwtService`, `JwtAuthenticationFilter`, `jwt.secret` | Xóa ở giai đoạn 7 | – |
| `Auth Service/` | Ngừng dùng ở giai đoạn 7 | – |
| `FE/package.json` | Thêm `keycloak-js` | – |
| `FE/.env`, `FE/public/silent-check-sso.html` | Cấu hình Keycloak | – |
| `FE/src/services/keycloak.js` | **Mới** | Chưa |
| `FE/src/context/AuthContext.jsx`, `src/api/axios.js` | Viết lại | Chưa |
| `FE/src/App.jsx`, `Navbar.jsx`, `Profile.jsx`, `Admin.jsx`, `services/ChatService.js` | Chỉnh theo mục 8.6e | Chưa |
| `FE/src/pages/Login.jsx`, `Register.jsx`, `ForgotPassword.jsx`, `services/AuthService.js` | Xóa | – |
| `PHAN_TICH_NGHIEP_VU.md` | Cập nhật mục 0.1, 3.2–3.4, 8.1, 9 sau khi hoàn tất | – |

**Liên quan tới các tài liệu khác:**

- `HUONG_DAN_DANG_NHAP_GOOGLE.md`: không áp dụng nữa nếu làm Keycloak (Google cấu hình trong Keycloak, mục 8.9).
- `HUONG_DAN_DEPLOY_AZURE.md`: mục 3.1 (sửa 4 Feign client của Auth Service) không còn cần sau giai đoạn 7; thêm Keycloak theo mục 9 của tài liệu này.
- `HUONG_DAN_TICH_HOP_CHATBOT.md`: không ảnh hưởng; bot nhận id nội bộ từ phiên chat như cũ.
