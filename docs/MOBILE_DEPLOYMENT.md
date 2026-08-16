# Toogeduler 모바일 앱 배포

`web`은 Capacitor로 생성한 iOS·Android 네이티브 셸입니다. 운영 Next.js 사이트를 HTTPS로 불러오므로 웹을 먼저 배포한 뒤 스토어에 제출합니다.

## 운영 주소 설정

`web/capacitor.config.ts`의 `server.url`을 실제 웹 도메인으로 바꿉니다. Vercel에는 `NEXT_PUBLIC_APP_URL=https://app.toogeduler.com`, Railway에는 `WEB_URL=https://app.toogeduler.com`, `MOBILE_URL=toogeduler://auth/callback`을 설정합니다.

웹 OAuth 콜백은 `WEB_URL`, 앱에서 시작한 OAuth 콜백은 `MOBILE_URL`로 돌아옵니다. Google·Kakao의 redirect URI는 API의 `https://api.<YOUR_DOMAIN>/login/oauth2/code/{provider}`를 유지합니다.

## 스토어 빌드

```bash
cd web
npm install
npm run mobile:sync
npm run mobile:ios
npm run mobile:android
```

Xcode에서 `ios/App/App.xcworkspace`를 열어 Signing Team, Bundle Identifier와 버전을 설정한 뒤 Archive → Distribute App을 실행합니다. Android Studio에서는 `android`를 열어 서명 키를 설정하고 `Generate Signed Bundle / APK`로 Play Console용 `.aab`를 생성합니다.

제출 전 실제 HTTPS 도메인에서 이메일·Google·Kakao 로그인과 앱 복귀를 테스트하고, 앱 아이콘, 개인정보처리방침 URL, 스크린샷, Apple App Privacy 및 Google Data safety 정보를 준비합니다. 운영에서는 `SEED_DEMO=false`를 사용합니다.
