# HƯỚNG DẪN TÍCH HỢP ĐĂNG NHẬP BẰNG TÀI KHOẢN GOOGLE

> Tài liệu viết riêng cho hệ thống này (`Microserivce_ECommerSystem/` + `FE/`), dựa trên mã nguồn hiện tại của Auth Service, User Service, API Gateway và FE React. Ngày viết: 2026-10-11.
>
> **Trạng thái:** đây là tài liệu hướng dẫn, tính năng **chưa được cài đặt** vào mã nguồn. Các đoạn code bên dưới được viết khớp với code hiện có nhưng chưa được biên dịch/chạy thử.

## Mục lục

1. [Tổng quan giải pháp](#1-tổng-quan-giải-pháp)
2. [Cấu hình trên Google Cloud Console](#2-cấu-hình-trên-google-cloud-console)
3. [Cấu hình trong dự án](#3-cấu-hình-trong-dự-án)
4. [Backend — Auth Service](#4-backend--auth-service)
5. [Frontend — React](#5-frontend--react)
6. [Các service không cần sửa](#6-các-service-không-cần-sửa)
7. [Kiểm thử](#7-kiểm-thử)
8. [Lỗi thường gặp](#8-lỗi-thường-gặp)
9. [Lưu ý bảo mật và giới hạn](#9-lưu-ý-bảo-mật-và-giới-hạn)
10. [Triển khai thật (production)](#10-triển-khai-thật-production)
11. [Danh sách file thay đổi](#11-danh-sách-file-thay-đổi)

---

## 1. Tổng quan giải pháp

### 1.1 Cách làm được chọn

Dùng **Google Identity Services (nút "Sign in with Google") ở FE + xác thực ID token ở Auth Service**.

```
[Trình duyệt]                    [Google]              [Gateway :8085]      [Auth :8087]        [User :8088]
     │  1. bấm nút Google           │                        │                   │                   │
     │─────────────────────────────▶│                        │                   │                   │
     │  2. ID token (JWT do Google ký)                       │                   │                   │
     │◀─────────────────────────────│                        │                   │                   │
     │  3. POST /auth/google { credential }                  │                   │                   │
     │──────────────────────────────────────────────────────▶│──────────────────▶│                   │
     │                              │  4. lấy khóa công khai, kiểm tra chữ ký     │                   │
     │                              │◀───────────────────────────────────────────│                   │
     │                              │                        │  5. tìm user theo email, chưa có thì tạo
     │                              │                        │                   │──────────────────▶│
     │  6. { token } — JWT của hệ thống, giống hệt đăng nhập thường              │                   │
     │◀──────────────────────────────────────────────────────│◀──────────────────│                   │
```

Sau bước 6, mọi thứ chạy như đăng nhập bằng username/password: FE lưu token vào `localStorage`, Gateway kiểm tra JWT như cũ. **Token của Google chỉ dùng một lần để chứng minh danh tính**, không dùng để gọi API của hệ thống.

### 1.2 Vì sao không dùng `spring-boot-starter-oauth2-client` (luồng redirect)

| | Cách được chọn (ID token) | Luồng redirect của Spring Security |
|---|---|---|
| Trạng thái phía server | Không cần — khớp với Auth Service đang stateless | Cần session hoặc cookie để giữ `state` trong lúc redirect |
| Client Secret | Không cần | Bắt buộc, phải bảo vệ |
| Redirect URI | Không cần cấu hình | Phải cấu hình và đi qua Gateway |
| Thay đổi ở Gateway | Không có (`/auth/**` đã public) | Phải mở thêm đường `/oauth2/**`, `/login/oauth2/**` |
| Lượng code | 1 endpoint + 1 lớp xác thực | Cấu hình security + success handler + trả token về FE qua URL |

Hệ thống này là SPA + JWT tự phát hành, nên cách ID token gọn và ít rủi ro hơn.

### 1.3 Quyết định nghiệp vụ

- **Định danh theo email.** Google trả về email đã xác minh; Auth Service tìm user theo email đó.
- **Chưa có tài khoản → tự tạo** với role `USER`, username sinh tự động, mật khẩu ngẫu nhiên (người dùng không biết mật khẩu này).
- **Đã có tài khoản cùng email (đăng ký thường trước đó) → đăng nhập vào chính tài khoản đó** (gộp tài khoản theo email). Xem rủi ro ở mục 9.
- **Không thêm cột vào bảng `users`.** Phương án mở rộng (lưu `provider`, `googleId`) nằm ở mục 9.4.

---

## 2. Cấu hình trên Google Cloud Console

Thực hiện một lần, tại <https://console.cloud.google.com>. Tên menu có thể hơi khác vì Google đổi giao diện theo thời gian (mục OAuth hiện nằm trong **Google Auth Platform**, trước đây là **APIs & Services → OAuth consent screen / Credentials**).

### Bước 1 — Tạo project

1. Bấm ô chọn project ở thanh trên cùng → **New Project**.
2. Đặt tên, ví dụ `ecommerce-fullstack` → **Create** → chọn project vừa tạo.

### Bước 2 — Cấu hình màn hình xin quyền (OAuth consent screen)

1. Vào **APIs & Services → OAuth consent screen** (hoặc **Google Auth Platform → Branding**).
2. Điền:
   - **App name**: tên hiện trên hộp thoại đăng nhập, ví dụ `E-Commerce Shop`.
   - **User support email**: email của bạn.
   - **Developer contact information**: email của bạn.
3. **Audience / User type**: chọn **External**.
4. **Scopes / Data access**: chỉ cần 3 scope mặc định `openid`, `email`, `profile`. Đây là scope không nhạy cảm, không phải qua xét duyệt.
5. **Test users**: khi app ở trạng thái **Testing**, chỉ các email trong danh sách này đăng nhập được (tối đa 100). Thêm email của bạn và người cùng nhóm.

> Muốn mọi tài khoản Google đều đăng nhập được: bấm **Publish app** để chuyển sang **In production**. Với 3 scope trên thì không cần Google xét duyệt.

### Bước 3 — Tạo OAuth Client ID

1. Vào **APIs & Services → Credentials** (hoặc **Google Auth Platform → Clients**) → **Create Credentials → OAuth client ID**.
2. **Application type**: **Web application**.
3. **Name**: ví dụ `ecommerce-web`.
4. **Authorized JavaScript origins** — thêm đủ các địa chỉ mà FE chạy:
   - `http://localhost:5173`
   - `http://localhost`
5. **Authorized redirect URIs**: **để trống** (cách làm này không dùng redirect).
6. **Create** → sao chép **Client ID**, dạng `1234567890-abcxyz.apps.googleusercontent.com`.

Quy tắc của ô origin:

- Chỉ gồm giao thức + tên miền + cổng. **Không** có đường dẫn, **không** có dấu `/` ở cuối.
- `http` chỉ được phép với `localhost`. Tên miền khác bắt buộc `https`.
- Nếu mở FE qua ngrok hay tên miền thật, phải thêm đúng origin đó (ví dụ `https://abc123.ngrok-free.app`).
- Sau khi lưu, thay đổi có thể mất từ vài phút đến vài giờ mới có hiệu lực.

### Bước 4 — Ghi lại thông tin

| Thông tin | Dùng ở đâu | Có bí mật không |
|---|---|---|
| **Client ID** | FE (`VITE_GOOGLE_CLIENT_ID`) và Auth Service (`GOOGLE_CLIENT_ID`) — **phải cùng một giá trị** | Không. Nó xuất hiện công khai trong mã FE |
| **Client Secret** | Không dùng trong cách làm này | Có. Không đưa vào FE, không commit lên git |

---

## 3. Cấu hình trong dự án

### 3.1 Frontend — `FE/.env`

Thêm một dòng:

```env
VITE_API_URL=http://localhost:8085
VITE_GOOGLE_CLIENT_ID=1234567890-abcxyz.apps.googleusercontent.com
```

Vite chỉ đọc `.env` lúc khởi động → phải **dừng và chạy lại `npm run dev`** sau khi sửa. Biến phải bắt đầu bằng `VITE_` mới dùng được trong code.

### 3.2 Auth Service — `application.yml`

File: `Auth Service/Auth-Service/src/main/resources/application.yml`. Thêm vào cuối:

```yaml
# Google Sign-In: Client ID của OAuth client loại "Web application".
# Phải trùng với VITE_GOOGLE_CLIENT_ID ở FE.
google:
  client-id: ${GOOGLE_CLIENT_ID:}
```

Truyền giá trị qua biến môi trường khi chạy:

- **IntelliJ**: Run → Edit Configurations → Environment variables → `GOOGLE_CLIENT_ID=1234567890-abcxyz.apps.googleusercontent.com`
- **PowerShell**: `$env:GOOGLE_CLIENT_ID = '1234567890-abcxyz.apps.googleusercontent.com'` rồi chạy service trong cùng cửa sổ.
- Chạy nhanh lúc dev: ghi thẳng giá trị vào sau dấu `:` trong yml cũng được, vì Client ID không phải bí mật.

### 3.3 Docker — `docker-compose.yml`

Trong khối `auth-service`, thêm biến môi trường:

```yaml
  auth-service:
    environment:
      - EUREKA_HOST=eureka-server
      - SPRING_DATA_REDIS_HOST=redis-cart
      - GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
```

Rồi tạo file `.env` cạnh `docker-compose.yml` chứa `GOOGLE_CLIENT_ID=...`. Container `auth-service` cần **ra được Internet** để tải khóa công khai của Google.

---

## 4. Backend — Auth Service

Tất cả thay đổi backend nằm trong `Auth Service/Auth-Service/`.

### 4.1 Thêm thư viện — `pom.xml`

Thêm vào `<dependencies>`:

```xml
<!-- Xác thực ID token của Google Sign-In -->
<dependency>
    <groupId>com.google.api-client</groupId>
    <artifactId>google-api-client</artifactId>
    <version>2.7.0</version>
</dependency>
```

Sau đó reload Maven (IntelliJ: biểu tượng Maven → Reload).

### 4.2 DTO nhận request — `dto/GoogleLoginRequest.java` (file mới)

```java
package com.example.Auth.Service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginRequest {
    // ID token (JWT) mà Google trả về cho FE sau khi người dùng chọn tài khoản
    private String credential;
}
```

### 4.3 Lớp xác thực token — `service/GoogleTokenVerifier.java` (file mới)

```java
package com.example.Auth.Service.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Slf4j
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${google.client-id}") String clientId) {
        // Verifier tự tải và cache khóa công khai của Google, nên chỉ tạo 1 lần
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    /**
     * Kiểm tra chữ ký, hạn dùng, nơi phát hành (Google) và nơi nhận (client-id của hệ thống).
     * Chỉ chấp nhận tài khoản có email đã được Google xác minh.
     */
    public GoogleIdToken.Payload verify(String credential) {
        if (credential == null || credential.isBlank()) {
            throw new RuntimeException("Google credential must not be blank");
        }

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(credential);
        } catch (Exception e) {
            log.error("Failed to verify Google ID token: {}", e.getMessage());
            throw new RuntimeException("Cannot verify Google token. Please try again.");
        }

        if (idToken == null) {
            throw new RuntimeException("Invalid or expired Google token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        if (payload.getEmail() == null || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new RuntimeException("Google account email is not verified");
        }
        return payload;
    }
}
```

`verifier.verify(...)` trả về `null` khi token sai chữ ký, hết hạn, hoặc không phải cấp cho Client ID của hệ thống — đây là chỗ chặn token giả hoặc token lấy từ ứng dụng khác.

### 4.4 Logic đăng nhập — sửa `service/AuthService.java`

Thêm import:

```java
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import feign.FeignException;
import java.util.Base64;
```

Thêm field (lớp đang dùng `@RequiredArgsConstructor` nên chỉ cần khai báo `final`):

```java
    private final GoogleTokenVerifier googleTokenVerifier;
```

Thêm các phương thức, đặt ngay sau `login(...)`:

```java
    /**
     * Đăng nhập bằng Google: xác thực ID token, tìm user theo email (chưa có thì tạo mới),
     * rồi phát hành JWT của hệ thống giống hệt đăng nhập thường.
     */
    public AuthResponse loginWithGoogle(String credential) {
        GoogleIdToken.Payload payload = googleTokenVerifier.verify(credential);
        UserDto user = findOrCreateGoogleUser(payload);
        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, "User logged in with Google successfully");
    }

    private UserDto findOrCreateGoogleUser(GoogleIdToken.Payload payload) {
        String email = payload.getEmail().trim().toLowerCase();
        try {
            return userClient.getUserByEmail(email);
        } catch (FeignException.NotFound e) {
            log.info("First Google login for {}, creating account", email);
        }

        UserDto newUser = UserDto.builder()
                .username(buildGoogleUsername(email, payload.getSubject()))
                .email(email)
                // User Service bắt buộc có mật khẩu để băm BCrypt -> sinh ngẫu nhiên, không ai biết.
                // Người dùng muốn có mật khẩu riêng thì dùng luồng "Quên mật khẩu" (OTP email).
                .password(randomPassword())
                .firstName((String) payload.get("given_name"))
                .lastName((String) payload.get("family_name"))
                .role("USER")
                .build();
        return userClient.createUser(newUser);
    }

    // Phần trước @ của email + 6 ký tự cuối mã định danh Google -> gần như không thể trùng username
    private String buildGoogleUsername(String email, String googleSubject) {
        String localPart = email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9._-]", "");
        String suffix = googleSubject.length() > 6
                ? googleSubject.substring(googleSubject.length() - 6)
                : googleSubject;
        return localPart + "_" + suffix;
    }

    private String randomPassword() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
```

Ghi chú về code hiện có mà đoạn trên dựa vào:

- `userClient.getUserByEmail(...)` trả HTTP 404 khi không tìm thấy (User Service ném `UserNotFoundException`) → Feign ném `FeignException.NotFound`.
- `userClient.createUser(...)` gọi `POST /api/users`; `UserMapper` băm mật khẩu bằng BCrypt và **sẽ lỗi nếu mật khẩu là `null`** — vì vậy bắt buộc phải sinh mật khẩu ngẫu nhiên.
- `secureRandom` là field đã có sẵn trong `AuthService`.

### 4.5 Endpoint — sửa `controller/AuthController.java`

Thêm import `com.example.Auth.Service.dto.GoogleLoginRequest` và phương thức sau, đặt cạnh `login(...)`:

```java
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithGoogle(request.getCredential()));
    }
```

### 4.6 Hợp đồng API

| | |
|---|---|
| Method / Path | `POST /auth/google` (gọi qua Gateway: `http://localhost:8085/auth/google`) |
| Request body | `{ "credential": "<ID token của Google>" }` |
| Thành công (200) | `{ "token": "<JWT của hệ thống>", "message": "User logged in with Google successfully" }` |
| Thất bại (400) | `{ "status": 400, "message": "Invalid or expired Google token", ... }` — do `GlobalExceptionHandler` sẵn có của Auth Service chuyển `RuntimeException` thành 400 |

JWT trả về có cùng cấu trúc với đăng nhập thường (`sub` = username, claim `userId`, `role`), nên Gateway và các service khác không cần biết người dùng đăng nhập bằng cách nào.

---

## 5. Frontend — React

Tất cả thay đổi nằm trong `FE/`.

### 5.1 Cài thư viện

```bash
cd FE
npm install @react-oauth/google
```

### 5.2 Bọc ứng dụng — sửa `src/main.jsx`

```jsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import './index.css'
import { AuthProvider } from './context/AuthContext'
import { BrowserRouter } from 'react-router-dom'
import { GoogleOAuthProvider } from '@react-oauth/google'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <GoogleOAuthProvider clientId={import.meta.env.VITE_GOOGLE_CLIENT_ID}>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </GoogleOAuthProvider>
  </React.StrictMode>,
)
```

### 5.3 Gọi API — sửa `src/services/AuthService.js`

Thêm phương thức vào lớp `AuthService`, sau `login(...)`:

```js
    async loginWithGoogle(credential) {
        return await axios.post(`${BASE}/auth/google`, { credential });
    }
```

### 5.4 Trạng thái đăng nhập — sửa `src/context/AuthContext.jsx`

Thêm hàm `loginWithGoogle` cạnh hàm `login` (phần xử lý token giống hệt `login`):

```jsx
  const loginWithGoogle = async (credential) => {
    try {
      const response = await authService.loginWithGoogle(credential);
      const newToken = response.data.token;

      axios.defaults.headers.common['Authorization'] = `Bearer ${newToken}`;
      localStorage.setItem('token', newToken);
      setToken(newToken);

      const decoded = decodeToken(newToken);
      if (decoded) {
        setUser({
          id: decoded.userId || decoded.id,
          username: decoded.sub || decoded.username,
          role: decoded.role
        });
      }

      return { success: true };
    } catch (error) {
      return { success: false, message: error.response?.data?.message || "Google login failed" };
    }
  };
```

Và đưa nó vào `value` của Provider:

```jsx
    <AuthContext.Provider value={{ user, token, loading, login, loginWithGoogle, register, logout }}>
```

### 5.5 Nút đăng nhập — sửa `src/pages/Login.jsx`

Thêm import:

```jsx
import { GoogleLogin } from '@react-oauth/google';
```

Lấy thêm `loginWithGoogle` từ context và thêm hàm xử lý:

```jsx
    const { login, loginWithGoogle, token } = useAuth();

    const handleGoogleSuccess = async (credentialResponse) => {
        setError('');
        setLoadingMsg(true);
        const res = await loginWithGoogle(credentialResponse.credential);
        if (res.success) {
            navigate('/success');
        } else {
            setError(res.message);
            setLoadingMsg(false);
        }
    };
```

Chèn nút ngay sau thẻ `</form>`, trước `<div className="auth-toggle">`:

```jsx
                <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', margin: '1.5rem 0', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                    <div style={{ flexGrow: 1, height: '1px', background: 'var(--glass-border)' }} />
                    hoặc
                    <div style={{ flexGrow: 1, height: '1px', background: 'var(--glass-border)' }} />
                </div>
                <div style={{ display: 'flex', justifyContent: 'center' }}>
                    <GoogleLogin
                        onSuccess={handleGoogleSuccess}
                        onError={() => setError('Đăng nhập Google thất bại. Vui lòng thử lại.')}
                        theme="filled_black"
                        text="signin_with"
                        shape="pill"
                    />
                </div>
```

Có thể đặt cùng nút này vào `Register.jsx` (đổi `text="signup_with"`), vì với Google thì đăng ký và đăng nhập là một thao tác.

Nút do Google vẽ trong một iframe nên không chỉnh CSS tùy ý được; chỉ đổi qua các thuộc tính `theme`, `size`, `text`, `shape`, `width`.

---

## 6. Các service không cần sửa

| Thành phần | Lý do |
|---|---|
| **API Gateway** | `JwtGatewayFilter` đã cho mọi đường dẫn chứa `/auth/` đi qua không cần token; route `/auth/**` đã trỏ về `auth-service`; CORS đã mở |
| **User Service** | Dùng lại `GET /api/users/email/{email}` và `POST /api/users` sẵn có |
| **Các service khác** | Chỉ nhìn JWT của hệ thống, không phân biệt cách đăng nhập |
| **`vite.config.js`** | Proxy `/auth` đã có |

---

## 7. Kiểm thử

### 7.1 Chuẩn bị

1. Chạy: Redis, MySQL, Eureka, User Service, Auth Service (đã đặt `GOOGLE_CLIENT_ID`), API Gateway.
2. Chạy lại FE (`npm run dev`) sau khi sửa `.env`.
3. Nếu app Google đang ở trạng thái **Testing**: email dùng để thử phải nằm trong danh sách **Test users**.

### 7.2 Danh sách kiểm tra

| # | Thao tác | Kết quả mong đợi |
|---|---|---|
| 1 | Mở `http://localhost:5173/login` | Thấy nút "Sign in with Google", console không báo lỗi origin |
| 2 | Đăng nhập bằng email Google **chưa có** trong bảng `users` | Vào được trang `/success`; bảng `users` có dòng mới với role `USER`, username dạng `tenemail_123456` |
| 3 | Đăng xuất rồi đăng nhập lại bằng cùng email | Vào đúng tài khoản cũ, **không** tạo dòng mới |
| 4 | Đăng nhập bằng email Google **trùng** với một tài khoản đăng ký thường | Vào tài khoản đã có (cùng `id`, cùng giỏ hàng/đơn hàng) |
| 5 | Sau khi đăng nhập, mở Giỏ hàng, Đơn hàng, Hồ sơ, Điểm thưởng | Các trang hoạt động bình thường |
| 6 | Đăng xuất | Token bị thu hồi như đăng nhập thường |
| 7 | Gửi `credential` sai (xem lệnh bên dưới) | HTTP 400, thông báo `Invalid or expired Google token` |

Lệnh thử trường hợp 7:

```bash
curl -i -X POST http://localhost:8085/auth/google \
  -H "Content-Type: application/json" \
  -d '{"credential":"abc.def.ghi"}'
```

Để thử backend bằng token thật mà không cần FE: đăng nhập trên FE, mở DevTools → tab Network → request `google` → sao chép giá trị `credential` trong payload, rồi gửi lại bằng `curl`/Postman. Token này sống khoảng 1 giờ.

---

## 8. Lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Console: `The given origin is not allowed for the given client ID`; nút không hiện hoặc lỗi 403 | Origin đang mở FE chưa nằm trong **Authorized JavaScript origins** | Thêm đúng origin (đúng giao thức, cổng, không có `/` cuối). Chờ vài phút, xóa cache trình duyệt |
| Hộp thoại Google: `Error 400: origin_mismatch` | Như trên | Như trên |
| Hộp thoại Google: `Error 403: access_denied` / "app chưa hoàn tất xác minh" | App ở trạng thái Testing và email không nằm trong Test users | Thêm email vào Test users, hoặc Publish app |
| Nút hiện nhưng backend trả `Invalid or expired Google token` | Client ID ở FE và ở Auth Service **khác nhau**, hoặc `GOOGLE_CLIENT_ID` đang rỗng | So lại hai giá trị; khởi động lại Auth Service sau khi đặt biến môi trường |
| Backend trả `Cannot verify Google token` | Auth Service không ra được Internet để tải khóa công khai của Google | Kiểm tra mạng/proxy/firewall của máy hoặc container |
| Token hợp lệ nhưng thỉnh thoảng bị từ chối | Đồng hồ máy chủ lệch nhiều so với giờ thật | Đồng bộ lại giờ hệ thống |
| Nút không hiện, không có lỗi rõ ràng | `VITE_GOOGLE_CLIENT_ID` chưa có hoặc chưa chạy lại Vite; hoặc trình chặn quảng cáo chặn `accounts.google.com/gsi/client` | Kiểm tra `.env`, chạy lại `npm run dev`, tắt trình chặn |
| Console cảnh báo `Cross-Origin-Opener-Policy policy would block the window.postMessage call` | Cảnh báo từ cửa sổ popup của Google | Thường vô hại. Chỉ cần xử lý nếu máy chủ FE có gửi header `Cross-Origin-Opener-Policy: same-origin` — khi đó đổi thành `same-origin-allow-popups` |
| Backend trả 400 `Username already exists` khi tạo tài khoản | Trùng username sinh tự động (rất hiếm) | Đăng nhập lại; nếu lặp lại, đổi cách sinh username trong `buildGoogleUsername` |
| Auth Service không khởi động, lỗi `Could not resolve placeholder 'google.client-id'` | Thiếu khối `google:` trong `application.yml` | Thêm cấu hình ở mục 3.2 |

---

## 9. Lưu ý bảo mật và giới hạn

### 9.1 Bắt buộc phải giữ

- **Luôn xác thực ID token ở backend.** Không bao giờ để FE tự giải mã token rồi gửi email lên — ai cũng giả mạo được.
- **Luôn kiểm tra audience** (`setAudience(clientId)`). Thiếu bước này, token cấp cho một ứng dụng khác cũng đăng nhập được vào hệ thống.
- **Chỉ nhận email đã xác minh** (`email_verified = true`).
- **Không ghi log ID token.**

### 9.2 Rủi ro chiếm tài khoản trước (do gộp theo email)

Hệ thống hiện **không xác minh email khi đăng ký thường**. Kịch bản: kẻ xấu đăng ký trước bằng email của nạn nhân và tự đặt mật khẩu; sau đó nạn nhân đăng nhập bằng Google và được đưa vào chính tài khoản đó — tài khoản mà kẻ xấu vẫn biết mật khẩu.

Cách giảm thiểu, chọn một:

1. Thêm bước xác minh email khi đăng ký thường (có thể tái dùng cơ chế OTP của "Quên mật khẩu").
2. Lần đầu một tài khoản đăng ký thường được đăng nhập bằng Google: đặt lại mật khẩu thành giá trị ngẫu nhiên (gọi `userClient.updatePassword`). Cần thêm cột đánh dấu ở mục 9.4 để biết đâu là "lần đầu".

### 9.3 Giới hạn của cách làm tối giản

- **Đổi mật khẩu:** form "Change Password" trong Hồ sơ yêu cầu mật khẩu cũ, mà tài khoản tạo từ Google có mật khẩu ngẫu nhiên → người dùng phải dùng "Forgot password" (OTP email) để đặt mật khẩu lần đầu.
- **Đổi email ở Google:** định danh theo email nên nếu người dùng đổi email của tài khoản Google, hệ thống sẽ coi là người mới.
- **Hai request đăng nhập lần đầu cùng lúc:** request thứ hai có thể nhận lỗi 400 `Email already exists`; đăng nhập lại là được.
- **Ảnh đại diện Google** (`picture` trong token) chưa được lưu vì bảng `users` chưa có cột avatar.
- **Token hệ thống vẫn sống 24h, không có refresh token** — không thay đổi so với hiện tại.

### 9.4 Mở rộng (không bắt buộc): lưu nhà cung cấp đăng nhập

Nếu muốn định danh chắc chắn hơn và xử lý được mục 9.2, 9.3:

1. `User` entity (User Service): thêm `private String provider;` (`LOCAL` / `GOOGLE`) và `@Column(unique = true) private String googleId;` (giá trị `sub` của Google — không đổi kể cả khi người dùng đổi email).
2. Thêm 2 field tương ứng vào `UserCreateDto`, `UserDto`, `UserMapper` và `UserDto` của Auth Service.
3. Thêm `GET /api/users/google/{googleId}` ở User Service; Auth Service tìm theo `googleId` trước, không thấy mới tìm theo email rồi gắn `googleId`.
4. FE: ẩn form đổi mật khẩu khi `provider = GOOGLE` và chưa đặt mật khẩu.

### 9.5 Lỗ hổng sẵn có liên quan

`POST /api/users` là endpoint công khai và nhận field `role` từ client (`UserMapper` dùng luôn giá trị đó). Nghĩa là ai cũng có thể tự đăng ký tài khoản `ADMIN`. Không do tính năng Google gây ra, nhưng nên sửa: bỏ qua `role` từ request và luôn gán `USER` khi tạo qua đường công khai.

---

## 10. Triển khai thật (production)

1. **Google Console:** thêm origin thật (ví dụ `https://shop.example.com`) vào Authorized JavaScript origins; **Publish app**; điền trang chủ, chính sách bảo mật ở mục Branding nếu muốn hiện logo/tên đã xác minh.
2. **HTTPS bắt buộc** cho mọi origin không phải `localhost`.
3. **FE:** `VITE_GOOGLE_CLIENT_ID` được nhúng vào bản build → phải đặt biến **trước khi** `npm run build`.
4. **Auth Service:** đặt `GOOGLE_CLIENT_ID` qua biến môi trường; cho phép kết nối ra `https://www.googleapis.com`.
5. Nên tách **hai OAuth client riêng** cho dev và production để không lẫn origin.

---

## 11. Danh sách file thay đổi

| File | Thay đổi |
|---|---|
| `Auth Service/Auth-Service/pom.xml` | Thêm dependency `google-api-client` |
| `Auth Service/Auth-Service/src/main/resources/application.yml` | Thêm `google.client-id` |
| `Auth Service/.../dto/GoogleLoginRequest.java` | **Mới** |
| `Auth Service/.../service/GoogleTokenVerifier.java` | **Mới** |
| `Auth Service/.../service/AuthService.java` | Thêm `loginWithGoogle` và các hàm phụ |
| `Auth Service/.../controller/AuthController.java` | Thêm `POST /auth/google` |
| `docker-compose.yml` | Thêm biến `GOOGLE_CLIENT_ID` cho `auth-service` (nếu chạy Docker) |
| `FE/.env` | Thêm `VITE_GOOGLE_CLIENT_ID` |
| `FE/package.json` | Thêm `@react-oauth/google` (qua `npm install`) |
| `FE/src/main.jsx` | Bọc `GoogleOAuthProvider` |
| `FE/src/services/AuthService.js` | Thêm `loginWithGoogle` |
| `FE/src/context/AuthContext.jsx` | Thêm `loginWithGoogle`, đưa vào Provider |
| `FE/src/pages/Login.jsx` | Thêm nút `GoogleLogin` |

Sau khi cài đặt xong, cập nhật `PHAN_TICH_NGHIEP_VU.md`: dòng "Đăng nhập mạng xã hội (Google/OAuth2)" ở mục 0.1 và rủi ro số 9 ở mục 9.
