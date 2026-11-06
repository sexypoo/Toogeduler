# 로그인 토큰 HttpOnly 쿠키 전환 설계 (SEC-011, #47)

상태: 설계안. 고은님 승인 후 하위 이슈로 구현합니다. 이 문서는 코드를 바꾸지 않습니다.

## 0. 지금 구조 (2026-11-06 main 기준)

| 위치 | 지금 하는 일 |
|---|---|
| `web/lib/api.ts` | `localStorage.toogeduler_token` 을 읽어 `Authorization: Bearer` 로 붙입니다. 기본 API 주소는 같은 출처 `/backend` 입니다. |
| `web/next.config.ts` | `/backend/:path*` → `API_ORIGIN` 으로 rewrite 합니다. 브라우저가 보기에 API 는 `app.toogeduler.com` 과 같은 출처입니다. |
| `web/app/auth/callback/page.tsx` | `?code=` 와 `localStorage` 의 verifier 로 `POST /backend/api/auth/oauth/exchange` 를 부르고, 응답 `token` 을 `localStorage` 에 넣습니다. |
| `web/app/login/page.tsx` | 이메일 로그인 응답의 `token` 과 `deviceToken` 을 각각 `toogeduler_token`, `toogeduler_device` 에 넣습니다. |
| 여러 화면 | `token()` 으로 로그인 여부를 동기로 판단하고(`groups/new`, `mypage`, `friends/[id]`, `invite/[token]`, `public`), 로그아웃·401 때 `localStorage.removeItem` 합니다. `web-push-browser.ts` 는 Bearer 를 직접 붙입니다. |
| `JwtFilter` | `Authorization: Bearer` 만 읽습니다. 쿠키는 보지 않습니다. |
| `JwtService` | HS256, 만료 30일, 갱신(refresh) 없음. |
| `SecurityConfig` | CSRF 끔(쿠키 인증이 없으니 맞는 설정). 세션은 OAuth state·`OAuthStart` 보관용으로만 `api.toogeduler.com` 에 생깁니다. CORS 는 `WEB_URL` 하나, credentials 허용. |
| `DeviceTokens` | 로그인 시도 제한(SEC-012)용 기기 토큰. JWT 와 다른 키로 서명하며 API 인증에는 못 씁니다. 클라이언트가 로그인 요청 본문 `deviceToken` 으로 보냅니다. |
| `capacitor.config.ts` | 앱은 `https://app.toogeduler.com` 을 원격 로드합니다. 소셜 로그인은 시스템 브라우저 → `toogeduler://auth/callback?code=` → `native-deep-link.tsx` 가 WebView 를 `/auth/callback?code=` 로 이동시킵니다. |

핵심 관찰: **웹과 앱 모두 API 를 같은 출처(`app.toogeduler.com/backend`)로 부르고, 토큰을 받는 순간(로그인·exchange)도 그 출처에서 일어납니다.** 그래서 쿠키를 `app.toogeduler.com` 의 first-party 쿠키로 둘 수 있고, 서드파티 쿠키 차단·ITP 문제를 피할 수 있습니다.

## 1. 결정 1 — `/backend` 같은 출처 쿠키 + CSRF 이중 제출

### 결정

가능합니다. 아래처럼 합니다.

- **인증 쿠키** `__Host-tg_at` = JWT 그대로.
  `HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=2592000`(30일, JWT 만료와 같게). `Domain` 은 쓰지 않습니다(호스트 전용).
  API 가 `/backend` 프록시를 거친 응답에 `Set-Cookie` 를 실으면 브라우저는 `app.toogeduler.com` 에 저장합니다. API 는 `/backend` 접두사를 몰라도 됩니다(`Path=/` 이므로).
- **CSRF 쿠키** `__Host-XSRF-TOKEN` (HttpOnly 아님, `Secure; SameSite=Lax; Path=/`).
  Spring Security 의 `CookieCsrfTokenRepository.withHttpOnlyFalse()` 에 쿠키 이름·속성을 지정하고, SPA 용 `CsrfTokenRequestAttributeHandler`(XOR 마스킹 없이 원값 비교)를 씁니다.
  클라이언트 `api()` 는 `document.cookie` 에서 이 값을 읽어 POST/PUT/PATCH/DELETE 에 `X-XSRF-TOKEN` 헤더로 보냅니다.
  Spring 은 토큰을 지연 생성하므로, 응답마다 토큰을 한 번 건드려 쿠키가 항상 내려가게 하는 작은 필터를 둡니다(공식 문서의 SPA 예시와 같은 방식).
- **CSRF 검사 대상**: "인증 쿠키로 인증된 상태 변경 요청"만 검사합니다(`requireCsrfProtectionMatcher`).
  - `Authorization` 헤더가 있는 요청: 전환 기간의 기존 사용자. 다른 사이트가 이 헤더를 붙일 수 없으므로(CORS 사전 요청에서 막힘) 검사하지 않습니다.
  - `/api/auth/login`, `/register`, `/oauth/exchange`: 쿠키를 **받는** 요청이라 검사하지 않습니다. 비밀번호 또는 verifier 가 있어야 성공하므로 로그인 CSRF 위험이 낮습니다.
  - `/api/auth/logout` 은 검사합니다(남이 강제로 로그아웃시키는 것도 막음).
- 개발(`http://localhost`)에서는 `Secure`·`__Host-` 를 쓸 수 없으므로 `app.auth-cookie.secure` 설정(기본 `true`, `local` 에서 `false`)에 따라 이름을 `tg_at` / `XSRF-TOKEN` 으로 바꿉니다.
- 운영 웹은 `NEXT_PUBLIC_API_URL` 을 비워 둔 채(`/backend`) 써야 합니다. 직접 `api.toogeduler.com` 을 부르면 쿠키가 다른 호스트에 붙어 동작하지 않습니다. `DEPLOYMENT.md` 환경변수 표에 이 조건을 적습니다.

### 이유

- 같은 출처라 CORS·`credentials: "include"`·서드파티 쿠키가 전혀 필요 없습니다. `fetch` 기본값(`same-origin`)으로 쿠키가 실립니다.
- `SameSite=Lax` 만으로는 **같은 사이트(same-site)** 의 다른 호스트(`toogeduler.com`, 앞으로 생길 다른 서브도메인)에서 오는 요청을 막지 못합니다. 그래서 이중 제출 토큰을 함께 둡니다.
- 이중 제출의 약점은 형제 서브도메인이 쿠키를 덮어쓰는 "cookie tossing" 입니다. `__Host-` 접두사는 `Domain` 지정을 금지해 이것을 브라우저 차원에서 막습니다.
- 지금 `SecurityConfig` 주석이 "CSRF 를 끈 상태에서 쿠키 인증이 열리면 안 된다"고 경고하는 바로 그 상황이므로, 쿠키 인증과 CSRF 켜기는 **같은 PR** 에 들어가야 합니다.
- 서비스 워커는 이미 API 경로(`/backend`)를 캐시하지 않으므로(`web/lib/pwa.ts`) 쿠키 인증으로 바꿔도 다른 계정 응답이 캐시에 남지 않습니다.

### 대안

- `SameSite=Strict`: 우리 API 호출은 모두 같은 출처 `fetch` 라 동작은 하지만, 외부 링크·OAuth 리다이렉트를 거친 직후 일부 브라우저의 예외 동작을 검증할 비용에 비해 이득이 작습니다. CSRF 토큰이 있으니 `Lax` 로 둡니다.
- CSRF 토큰 없이 `Origin` 헤더 검사만: 구현은 가장 짧지만 Vercel 프록시를 거치며 헤더가 보존되는지에 기대야 하고, Spring 표준 기능이 아닙니다. 필요하면 나중에 추가 방어로 붙입니다.
- 서명형 이중 제출(HMAC 으로 세션과 묶음): 더 강하지만 `__Host-` 로 cookie tossing 을 막으면 지금 규모에서는 과합니다.
- 세션(JSESSIONID) 기반으로 되돌리기: 서버 상태와 Railway 다중 인스턴스 고려가 생겨 범위가 커집니다. JWT 를 담는 그릇만 바꿉니다.

## 2. 결정 2 — Capacitor 앱의 쿠키 유지와 기기 토큰 공존

### 결정

- 앱도 웹과 **같은 쿠키를 그대로** 씁니다. 앱 전용 Bearer 경로를 따로 두지 않습니다.
  - 앱 WebView 의 출처가 `https://app.toogeduler.com` 이고, exchange 도 WebView 안에서(`native-deep-link.tsx` → `/auth/callback`) 일어나므로 쿠키는 WebView 의 쿠키 저장소에 first-party 로 들어갑니다. 시스템 브라우저의 쿠키 저장소와는 섞이지 않고, 섞일 필요도 없습니다(시스템 브라우저는 `code` 만 돌려줍니다).
  - 반드시 `Max-Age` 를 줍니다. 만료 없는 세션 쿠키는 iOS WKWebView·Android WebView 에서 앱을 강제 종료하면 사라질 수 있습니다.
  - `CapacitorCookies`·`CapacitorHttp` 플러그인은 켜지 않습니다(지금도 꺼져 있음). 네이티브 HTTP 로 우회하면 WebView 쿠키와 갈라집니다.
- **기기 토큰도 HttpOnly 쿠키로** 옮깁니다. `__Host-tg_device`, `HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=1년`.
  - 서버는 로그인 요청에서 쿠키를 먼저 읽고, 없으면 전환 기간 동안 본문 `deviceToken` 을 읽습니다.
  - 인증 쿠키와는 **이름·서명 키·수명·지우는 시점이 모두 다릅니다**. `JwtFilter` 는 `tg_at` 만 읽고, `DeviceTokens.verify` 는 로그인 처리에서만 씁니다(지금과 같음).
  - 로그아웃은 `tg_at` 만 지우고 `tg_device` 는 남깁니다. 그래야 로그아웃 후 다시 로그인할 때도 "이 기기는 주인"이 유지됩니다(SEC-012 의 목적). 회원 탈퇴는 둘 다 지웁니다.

### 이유

- 웹과 앱이 같은 코드(`web/`)를 원격 로드하므로 저장 방식이 하나여야 분기와 버그가 줄어듭니다.
- 서버가 `Set-Cookie` 로 준 first-party 쿠키는 WKWebView 의 ITP "스크립트가 쓴 저장소 7일 제한" 대상이 아닙니다. 오히려 지금의 `localStorage` 보다 유지에 유리합니다.
- 기기 토큰이 `localStorage` 에 남아 있으면 XSS 로 그 기기의 "따로 세는 실패 횟수" 칸을 가져가 계정 잠금 우회에 쓸 수 있습니다. 같은 PR 흐름에서 옮기는 비용이 작습니다.

### 대안

- 앱은 Bearer 를 유지하고 Keychain/Keystore(예: Preferences·SecureStorage 플러그인)에 저장: 앱만 따로 분기해야 하고, 원격 로드 구조에서는 결국 JS 가 토큰을 읽어 헤더에 붙이므로 XSS 방어 효과가 없습니다.
- 기기 토큰은 그대로 `localStorage`: 동작에는 문제가 없지만 SEC-011 의 "JS 에서 읽을 수 있는 자격 증명 0개" 목표에 못 미칩니다.

### 확인이 필요한 것 (실기기)

iOS·Android 실기기에서 "소셜 로그인 → 앱 강제 종료 → 다시 열기" 후 로그인이 유지되는지. 하위 이슈 2의 완료 조건에 넣었습니다.

## 3. 결정 3 — OAuth exchange 응답을 쿠키 설정으로 바꾸는 순서

### 결정

서버를 먼저, 호환되게 바꾸고 → 웹을 바꾸고 → 마지막에 서버의 옛 경로를 지웁니다.

1. **API (하위 이슈 1)**: `login`·`register`·`oauth/exchange` 가 `Set-Cookie: __Host-tg_at` (+ `tg_device`, CSRF 쿠키)를 내려주면서 **본문 `token` 도 그대로** 돌려줍니다. `JwtFilter` 는 `Authorization` 헤더 → 없으면 `tg_at` 쿠키 순서로 읽습니다. `POST /api/auth/logout` 추가. 이 단계만 배포돼도 기존 웹은 아무 영향이 없습니다.
2. **웹 (하위 이슈 2)**: `callback/page.tsx`·`login/page.tsx` 가 본문 `token` 을 저장하지 않습니다. 로그인 여부는 서버가 함께 내려주는 비밀 아닌 표시 쿠키 `tg_signed_in=1`(HttpOnly 아님, 같은 Max-Age)로 판단하고, 로그아웃은 `POST /api/auth/logout` 으로 합니다.
3. **API 정리 (하위 이슈 3, 2단계 배포 후 30일 이상 지나서)**: 응답 본문의 `token`·`deviceToken` 제거, `Authorization` 헤더 인증 제거, 전환용 엔드포인트 제거.

exchange 자체의 보안 성질(일회용 `code` + PKCE 식 verifier)은 바꾸지 않습니다. 바뀌는 것은 "결과를 본문으로 주느냐 쿠키로 주느냐"뿐입니다.

### 이유

- API(Railway)와 웹(Vercel)은 따로 배포됩니다. 서버가 두 방식을 동시에 받아 주면 어느 쪽이 먼저 나가도 로그인이 끊기지 않습니다.
- `token()` 으로 로그인 여부를 동기로 판단하는 곳이 여러 화면(`groups/new`, `mypage`, `friends/[id]`, `invite/[token]`, `public`)에 있어, 서버 왕복 없이 로그인 여부를 아는 신호가 필요합니다. 그 신호를 서버가 인증 쿠키와 같은 수명으로 함께 설정·삭제해야 둘이 어긋나지 않습니다(값은 비밀이 아니므로 탈취돼도 의미 없음). 진짜 판단은 여전히 서버의 401 입니다.
- 본문 `token` 은 로그인 자격(비밀번호·verifier) 없이는 받을 수 없어, 1~2단계 동안 남아 있어도 XSS 가 새로 얻는 것은 없습니다. 그래도 최종적으로는 지웁니다.

### 대안

- exchange 를 `api.toogeduler.com` 에서 바로 처리하고 리다이렉트로 쿠키 설정: 쿠키가 `api.` 호스트에 붙어 `/backend` 요청에 실리지 않습니다. `Domain=toogeduler.com` 으로 넓히면 `__Host-` 를 못 쓰고 형제 서브도메인 전체에 쿠키가 노출됩니다.
- 한 번에 바꾸기(빅뱅): 배포 순서가 어긋나는 몇 분 동안 로그인이 깨지고, 되돌리기도 어렵습니다.
- 로그인 여부를 `/api/auth/me` 호출로만 판단: 정확하지만 모든 보호 화면에 로딩 상태를 새로 넣어야 해 하위 이슈 크기가 400줄을 넘습니다.

## 4. 결정 4 — 전환 기간에 기존 `localStorage` 토큰 사용자 유지

### 결정

- 웹 2단계 배포 후, 앱이 시작될 때 `localStorage.toogeduler_token` 이 남아 있으면 **한 번** `POST /api/auth/session` 을 `Authorization: Bearer <옛 토큰>` 으로 부릅니다.
  - 서버는 토큰을 검증하고 **새 JWT 로** 쿠키(`tg_at`, `tg_signed_in`, CSRF)를 설정한 뒤 204 를 줍니다. 본문에 토큰을 싣지 않습니다.
  - 성공하든 401 이든 클라이언트는 `toogeduler_token` 을 지웁니다(401 이면 로그인 화면으로). 네트워크 오류면 지우지 않고 다음 실행에 다시 시도합니다.
  - `toogeduler_device` 도 같은 방식으로 한 번 서버에 보내 `tg_device` 쿠키로 옮긴 뒤 지웁니다(같은 요청 본문에 실음).
- 이 업그레이드 요청은 `Authorization` 헤더 요청이므로 CSRF 검사 대상이 아닙니다(결정 1).
- 서버의 `Authorization` 헤더 인증과 `/api/auth/session` 은 **웹 2단계 배포 후 30일**(옛 JWT 최대 수명) 이 지나면 지웁니다(하위 이슈 3). 그때까지 업그레이드하지 않은 토큰은 어차피 만료됩니다.

### 이유

- 사용자 입장에서 아무 일도 일어나지 않습니다(다시 로그인할 필요 없음).
- 옛 토큰을 그대로 쿠키에 옮기지 않고 새로 발급하므로, 혹시 이미 유출된 토큰과 새 쿠키의 수명이 묶이지 않습니다. (JWT 는 서버에서 취소할 수 없으므로 옛 토큰은 남은 기간 동안 유효합니다. 이 한계는 이 이슈 범위 밖이며, 필요하면 `tokenVersion` 칼럼으로 다룹니다.)
- 업그레이드 경로를 한 곳(`api.ts` 의 시작 시점)에 모으면 정리 단계에서 지우기 쉽습니다.

### 대안

- 모두 강제 로그아웃: 가장 단순하지만 실사용자를 받기 시작한 뒤라면 불편이 큽니다. 지금 사용자가 적어 허용 가능한 선택지이긴 하므로, 고은님이 원하면 하위 이슈 2에서 업그레이드 코드만 빼면 됩니다.
- Bearer 헤더 인증을 계속 유지: 옛 토큰이 `localStorage` 에 영구히 남을 수 있어 목표에 어긋납니다.

## 5. 하위 이슈

| 순서 | 내용 | 배포 영향 |
|---|---|---|
| 1 | API: 쿠키 발급·읽기, CSRF(쿠키 인증 요청만), 로그아웃, `/api/auth/session` 업그레이드, 기기 토큰 쿠키. 본문 `token`·Bearer 유지 | 기존 웹 영향 없음 |
| 2 | 웹: `localStorage` 토큰 제거, `tg_signed_in` 으로 로그인 판단, CSRF 헤더, 로그아웃 API, 옛 토큰 1회 업그레이드, 실기기 확인 | 1 배포 후 |
| 3 | 정리: 본문 `token`·`deviceToken`, Bearer 인증, 업그레이드 엔드포인트 제거, 문서 SEC-011 DONE | 2 배포 30일 후 |

이슈 번호는 #47 코멘트에 링크합니다.

## 6. 범위 밖

- 액세스/리프레시 토큰 분리, 서버측 토큰 취소(`tokenVersion`), 만료 30일 단축.
- CSP(Content-Security-Policy) 강화. XSS 자체를 줄이는 다음 단계로 별도 이슈가 적당합니다.
