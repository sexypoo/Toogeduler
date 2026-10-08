# Toogeduler 모바일 앱 배포

`web`은 Capacitor로 만든 iOS·Android 네이티브 셸입니다. 운영 Next.js 사이트를 HTTPS로 불러오므로 **웹을 먼저 배포하고 실제로 동작하는 것을 확인한 뒤** 스토어에 제출합니다.

## 1. 운영 주소 준비

| 위치 | 변수 | 값 |
|---|---|---|
| Vercel | `API_ORIGIN` | `https://api.<도메인>` |
| Vercel | `NEXT_PUBLIC_OAUTH_URL` | `https://api.<도메인>` |
| Vercel | `NEXT_PUBLIC_SHOW_DEMO` | `false` |
| Railway | `WEB_URL` | `https://app.<도메인>` |
| Railway | `MOBILE_URL` | `toogeduler://auth/callback` |
| Railway | `OAUTH_REDIRECT_BASE_URL` | `https://api.<도메인>` |
| Railway | `JWT_SECRET` | `openssl rand -base64 48` 결과 |
| Railway | `SEED_DEMO` | `false` |

`NEXT_PUBLIC_OAUTH_URL`을 설정하지 않으면 운영 빌드에서 소셜 로그인 버튼이 아예 표시되지 않습니다. 이는 잘못된 주소로 사용자를 보내는 것보다 안전하도록 의도된 동작입니다.

> **`api.` 서브도메인이 아직 연결되지 않았습니다.** 2026-09-08 확인 기준 `api.toogeduler.com`은 인증서 불일치로 브라우저가 접속을 거부합니다. 소셜 로그인은 API 자신의 출처에서 시작해야 하므로 이 도메인이 살아나야 동작합니다. 진단과 조치는 [`docs/APP_STORE.md`](APP_STORE.md)의 2절을 참고하세요.

서버는 시작할 때 설정을 검증합니다(`ConfigGuard`). `WEB_URL`이 localhost가 아닌데 `JWT_SECRET`이 기본값이거나 `SEED_DEMO=true`이면 배포가 실패합니다.

웹 OAuth 콜백은 `WEB_URL/auth/callback`, 앱에서 시작한 OAuth 콜백은 `MOBILE_URL`로 돌아옵니다. 콜백 주소에는 토큰 대신 2분짜리 일회용 코드(`?code=`)가 실리며, 로그인을 시작한 기기가 `POST /api/auth/oauth/exchange`로 토큰과 바꿉니다. 소셜 로그인은 반드시 `/api/auth/oauth/{provider}?challenge=…`에서 시작해야 합니다(직접 `/oauth2/authorization/…`로 들어오면 거절됩니다). Google·Kakao에 등록하는 redirect URI는 API 주소인 `https://api.<도메인>/login/oauth2/code/{provider}`입니다.

## 2. 스토어 빌드

네이티브 셸이 로드할 주소는 **반드시** 환경변수로 지정합니다. 지정하지 않으면 `mobile:sync`가 명확한 오류와 함께 중단됩니다.

```bash
cd web
npm install
CAPACITOR_SERVER_URL=https://app.<도메인> npm run mobile:sync
npm run mobile:ios       # Xcode 열기
npm run mobile:android   # Android Studio 열기
```

`mobile:sync`는 오프라인 안내 페이지(`native-shell/error.html`)를 함께 생성해 네이티브 앱에 번들합니다. 네트워크 오류로 원격 사이트를 열 수 없을 때 흰 화면 대신 이 페이지가 표시되고, 연결이 회복되면 자동으로 복귀합니다.

### iOS

이 프로젝트는 Swift Package Manager를 사용하므로 `.xcworkspace`가 없습니다. `npm run mobile:ios`가 올바른 `ios/App/App.xcodeproj`를 엽니다.

Xcode에서 설정할 항목:

- `Signing & Capabilities → Team`: 본인 Apple Developer 팀 선택 (**저장소에 커밋되지 않은 유일한 필수 설정**)
- Bundle Identifier: `app.toogeduler.mobile` (이미 설정됨)
- Version `1.0` / Build `1` (이미 설정됨, 재제출할 때마다 Build를 올립니다)
- 기기: iPhone 전용 (`TARGETED_DEVICE_FAMILY = 1`). iPad를 지원하려면 iPad 스크린샷과 iPad 레이아웃 검증이 추가로 필요합니다.

`Product → Archive → Distribute App → App Store Connect`로 업로드합니다.

### Android

Android Studio에서 `android`를 열고 업로드 키스토어를 만든 뒤 `Build → Generate Signed App Bundle`로 `.aab`를 생성합니다. 키스토어 파일과 비밀번호는 **절대 저장소에 커밋하지 않습니다**. 분실하면 같은 앱을 업데이트할 수 없습니다.

## 3. 제출 전 실기기 점검

실제 HTTPS 도메인에서 다음을 모두 확인합니다.

- [ ] 이메일 회원가입 → 로그인 → 로그아웃
- [ ] Google 로그인 후 앱으로 복귀
- [ ] Kakao 로그인 후 앱으로 복귀
- [ ] 일정 생성·수정·삭제
- [ ] 그룹 생성 → 초대 링크 → 다른 계정으로 참여 → 전원 가능 시간 확인
- [ ] 친구 코드로 친구 요청·수락
- [ ] 마이페이지 → **계정 삭제** 동작 확인 (App Store 필수 요건)
- [ ] 기내 모드에서 앱 실행 → 오프라인 안내 화면 표시 → 연결 후 자동 복귀
- [ ] 노치 기기에서 상단·하단 안전영역 확인

## 4. 스토어 제출 자료

전체 절차와 심사 대응은 [`docs/APP_STORE.md`](APP_STORE.md)를 참고하세요.
