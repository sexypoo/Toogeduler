# App Store 제출 가이드 (Toogeduler)

이 문서는 Apple Developer Program 가입부터 심사 통과까지의 순서를 정리한 것입니다. Google Play는 마지막 절에 별도로 정리했습니다.

---

## 0. 지금 남아 있는 필수 작업

코드 쪽 심사 차단 요소는 정리했지만, 아래는 사람이 직접 해야 합니다.

| # | 작업 | 비용/기간 |
|---|---|---|
| 1 | Apple Developer Program 가입 | 연 $99 (원화 환산 결제) / 승인 1~2일 (가끔 1주) |
| 2 | **`api.` 서브도메인을 Railway 에 연결** (웹은 이미 연결 완료) | 30분 + 인증서 발급 대기 |
| 3 | Google·Kakao OAuth 운영 키 등록 및 검증 | 무료 / 1~2시간 |
| 4 | 개인정보처리방침 이메일 주소를 실제 주소로 변경 | — |
| 5 | 스크린샷 촬영 (iPhone 6.9") | 1~2시간 |
| 6 | Xcode에서 Signing Team 선택 | 5분 |

---

## 1. Apple Developer Program 가입

1. iPhone 또는 Mac에서 **Apple Developer** 앱, 또는 <https://developer.apple.com/programs/enroll/>로 접속합니다.
2. 본인 Apple ID로 로그인합니다. 2단계 인증이 켜져 있어야 합니다.
3. **개인(Individual)** 또는 **조직(Organization)** 을 선택합니다.
   - **개인**: 신분증 확인만으로 승인됩니다. App Store에는 **본인 실명**이 개발자명으로 표시됩니다.
   - **조직**: D-U-N-S 번호, 법인 자격, 조직 도메인의 업무용 이메일, 조직 소유의 공개 웹사이트가 모두 필요하고 2~4주 걸립니다. 대신 단체명(예: SWEETS-Ewha)으로 표시됩니다. 학생 동아리는 법인 자격 요건을 충족하지 못하는 경우가 많습니다.
   - 학생 프로젝트를 단체명으로 내고 싶다면 조직 가입이 필요하지만, 처음 출시라면 개인으로 시작하고 나중에 이전하는 편이 빠릅니다.
4. 연회비 $99(결제 시 원화로 환산)를 결제합니다. 자동 갱신이며, 갱신하지 않으면 앱이 App Store에서 내려갑니다.
5. 승인 메일을 받으면 <https://appstoreconnect.apple.com>에 접속할 수 있습니다.

> 참고: 무료 Apple ID로도 Xcode에서 본인 기기에 7일간 설치해 테스트할 수 있습니다. 가입 승인을 기다리는 동안 실기기 점검을 먼저 진행하세요.

---

## 2. 도메인과 서비스 연결

### 현재 상태 (2026-09-08 실제 확인)

| 호스트 | 상태 |
|---|---|
| `toogeduler.com` | 정상 |
| `app.toogeduler.com` | 정상 — 로그인 화면까지 확인 |
| `toogeduler.vercel.app` | 정상 |
| `app.toogeduler.com/backend/actuator/health` | `{"status":"UP"}` — Vercel → Railway 프록시 정상 |
| **`api.toogeduler.com`** | **접속 불가** — TCP 443 은 열려 있으나 HTTPS 인증서가 이 호스트 이름과 맞지 않아 브라우저가 로드를 거부합니다 |

웹은 이미 연결이 끝났습니다. 남은 문제는 `api.` 서브도메인 하나입니다.

### `api.toogeduler.com` 문제 진단

`api.toogeduler.com` 이 `69.46.46.121` 로 해석되는데, 이 주소는 Vercel(`216.198.79.1`)도 Railway 도 아닙니다. 무언가가 443 포트에서 응답하지만 이 도메인용 인증서가 없는 상태입니다. 원인은 보통 둘 중 하나입니다.

1. **DNS 에 오래된 A 레코드가 남아 있음** — 도메인 구매처의 기본 파킹 레코드가 그대로 있으면 Railway 의 CNAME 이 무시됩니다. `api` 이름으로 된 **A 레코드를 삭제**하고 Railway 가 지정한 **CNAME 만** 남겨야 합니다.
2. **Railway 커스텀 도메인 검증이 끝나지 않음** — Railway 는 `CNAME` 과 소유권 확인용 `TXT` 를 **둘 다** 요구하며, 둘이 모두 확인돼야 인증서를 발급합니다.

조치 순서:

1. Railway → API 서비스 → `Settings → Networking → Custom Domain` 에서 `api.toogeduler.com` 이 등록되어 있는지, 상태가 초록색인지 확인합니다.
2. Railway 가 보여주는 `CNAME` 과 `TXT` 를 DNS 에 그대로 넣고, `api` 이름의 다른 A/AAAA/CNAME 레코드는 모두 지웁니다.
3. Railway 에서 인증서 발급이 완료될 때까지 기다립니다(보통 수 분, 최대 수십 분).
4. 아래가 통과하면 완료입니다.

```bash
curl https://api.toogeduler.com/actuator/health   # {"status":"UP"}
```

> `/backend` 프록시로도 API 가 동작하므로 일정 조회·저장 같은 일반 기능은 지금도 정상입니다. 하지만 **소셜 로그인은 `api.` 도메인이 반드시 필요합니다.** OAuth 는 세션 쿠키를 API 자신의 출처에 심어야 하는데, 프록시를 통하면 쿠키가 웹 도메인에 붙어 Kakao·Google 이 돌려보낼 때 세션이 사라집니다.

### 임시 대안

`api.` 연결을 기다리는 동안, Railway 가 기본으로 발급한 도메인(`*.up.railway.app`)을 그대로 쓸 수 있습니다. Vercel 에 `NEXT_PUBLIC_OAUTH_URL=https://<railway-도메인>` 을 넣고, Railway 의 `OAUTH_REDIRECT_BASE_URL` 과 Google·Kakao 의 redirect URI 도 같은 도메인으로 맞추면 소셜 로그인이 바로 동작합니다. 나중에 `api.` 로 옮길 때 세 곳을 함께 바꾸면 됩니다.

### 환경변수 (지금 반드시 설정해야 하는 것)

`NEXT_PUBLIC_OAUTH_URL` 이 Vercel 에 **설정되어 있지 않습니다.** 확인 결과 현재 배포된 로그인 화면의 소셜 버튼은 접속 불가 상태인 `https://api.toogeduler.com/oauth2/authorization/...` 을 가리키고 있어, 누르면 오류 화면으로 갑니다.

이번 수정으로 이 하드코딩 기본값을 제거했으므로, **다음 배포부터는 `NEXT_PUBLIC_OAUTH_URL` 이 없으면 소셜 로그인 버튼 자체가 표시되지 않습니다.** 죽은 링크를 사용자에게 보여주지 않기 위한 의도된 동작입니다. 반드시 설정하세요.

| 위치 | 변수 | 값 |
|---|---|---|
| Vercel | `API_ORIGIN` | 현재 동작 중인 값 유지 |
| Vercel | `NEXT_PUBLIC_OAUTH_URL` | `https://api.toogeduler.com` (또는 위 임시 대안의 Railway 도메인) |
| Vercel | `NEXT_PUBLIC_SHOW_DEMO` | `false` |
| Railway | `WEB_URL` | `https://app.toogeduler.com` |
| Railway | `MOBILE_URL` | `toogeduler://auth/callback` |
| Railway | `OAUTH_REDIRECT_BASE_URL` | `NEXT_PUBLIC_OAUTH_URL` 과 동일하게 |
| Railway | `JWT_SECRET` | `openssl rand -base64 48` |
| Railway | `SEED_DEMO` | `false` |

`WEB_URL` 은 앱이 로드하는 주소와 같아야 합니다. `app.toogeduler.com` 을 대표 주소로 쓰고 있으므로 `https://app.toogeduler.com` 입니다. 루트 도메인으로 두면 OAuth 후 앱이 다른 출처로 튕겨 로그인 상태가 유지되지 않습니다.

## 3. App Store Connect에 앱 등록

1. **My Apps → + → New App**
2. 입력값:
   - Platform: **iOS**
   - Name: `Toogeduler` (App Store 전체에서 유일해야 합니다. 이미 있으면 `Toogeduler: 함께 맞추는 시간` 처럼 변형)
   - Primary Language: **Korean**
   - Bundle ID: `app.toogeduler.mobile` — 목록에 없으면 [Certificates, Identifiers & Profiles](https://developer.apple.com/account/resources/identifiers/list)에서 먼저 App ID를 등록합니다.
   - SKU: `toogeduler-ios-001` (내부 식별자, 공개되지 않음)
   - User Access: Full Access

### App Privacy (필수, 여기서 막히는 경우가 가장 많습니다)

**App Privacy → Get Started**에서 아래대로 신고합니다. 이 앱은 광고 SDK나 분석 도구를 쓰지 않으므로 신고가 단순합니다.

| 데이터 유형 | 수집 | 용도 | 신원 연결 | 추적 목적 |
|---|---|---|---|---|
| Email Address | 예 | App Functionality, Account Management | 예 | 아니오 |
| Name | 예 | App Functionality | 예 | 아니오 |
| User ID | 예 | App Functionality | 예 | 아니오 |
| Photos (프로필 이미지 URL) | 예 | App Functionality | 예 | 아니오 |
| Other User Content (일정 내용) | 예 | App Functionality | 예 | 아니오 |

- **"Do you use data for tracking?" → No** (광고 식별자를 쓰지 않으므로 ATT 팝업이 필요 없습니다)
- Privacy Policy URL: `https://app.<도메인>/privacy`

### Account Deletion (2022년부터 필수)

App Review Information의 Notes에 아래 문장을 적어 두면 리뷰어가 삭제 경로를 찾지 못해 반송되는 일을 막을 수 있습니다.

```
Account deletion: 로그인 후 우측 상단 프로필 → 마이페이지 → 화면 하단
"계정 삭제" → 비밀번호 확인 → "영구 삭제".
계정과 사용자가 만든 모든 일정·친구 관계·알림이 즉시 영구 삭제됩니다.
```

### 심사용 데모 계정

리뷰어는 소셜 로그인을 쓸 수 없으므로 **이메일 계정을 반드시 제공**해야 합니다.

1. 운영 사이트에서 이메일로 계정을 하나 만듭니다 (예: `review@<도메인>`).
2. 그 계정으로 일정 3~4개, 그룹 1개, 친구 1명을 미리 만들어 둡니다. **빈 화면은 "기능이 부족하다"는 인상을 주어 반송 사유가 됩니다.**
3. App Review Information에 이메일과 비밀번호를 입력합니다.

---

## 4. 스크린샷

**필요한 크기**

| 크기 | 대응 기기 | 해상도(세로) | 비고 |
|---|---|---|---|
| 6.9" | iPhone Air, 17 Pro Max 등 | 1260 × 2736 | **이것만 올리면 충분합니다** |
| 6.5" | iPhone 11 Pro Max 등 | 1284 × 2778 | 6.9"를 올리지 않은 경우에만 필수 |

Apple은 6.9"를 올리면 나머지 크기를 자동으로 축소해 사용합니다. 따라서 **6.9" 한 세트만 준비하면 됩니다.** iPad를 지원하지 않도록 설정했으므로 iPad 스크린샷도 필요 없습니다.

알파 채널(투명도)이 있는 PNG는 거부되므로, 스크린샷은 시뮬레이터에서 그대로 저장한 파일을 쓰세요.

**촬영 방법**: Xcode Simulator에서 해당 기기를 띄우고 `⌘S`로 저장하면 정확한 해상도로 나옵니다. 최소 3장, 권장 5장.

**추천 구성**
1. 주간 캘린더 (일정이 채워진 상태)
2. 그룹의 "전원 가능한 시간" 결과 화면 — **이 앱의 핵심 차별점이므로 반드시 포함**
3. 일정 추가 모달 (공개 범위 선택이 보이게)
4. 친구 캘린더
5. 마이페이지

스크린샷에 데모 계정 이메일이나 실명이 노출되지 않게 확인하세요.

---

## 5. 스토어 문구

**Subtitle** (30자 이내): `함께 맞추는 시간`

**Promotional Text** (170자, 심사 없이 언제든 수정 가능)

```
각자의 일정은 필요한 만큼만 공유하고, 모두가 가능한 시간은 한 번에 찾으세요.
```

**Description** 초안

```
Toogeduler는 일정을 안전하게 공유하고 모두가 만날 수 있는 시간을 찾아주는 공유 캘린더입니다.

■ 공개 범위를 일정마다 선택
비공개, 그룹, 친구, 전체 공개를 조합해 설정할 수 있습니다.
비공개 일정은 제목이나 장소가 누구에게도 보이지 않고, 빈 시간 계산에만 "바쁨"으로 반영됩니다.

■ 모두가 가능한 시간 찾기
그룹을 만들고 초대 링크를 공유하면, 구성원 전원이 비어 있는 시간을 자동으로 계산합니다.
전원이 어려울 때는 몇 명이 가능한 후보 시간도 함께 보여줍니다.

■ 친구 캘린더
친구 코드나 이메일로 친구를 추가하고, 친구가 공개한 일정을 주·월 단위로 확인할 수 있습니다.

■ 알림
친구 요청, 그룹 가입, 일정 리마인더를 앱 안에서 확인할 수 있습니다.

■ 로그인
이메일 또는 Google·Kakao 계정으로 시작할 수 있습니다.
계정 삭제는 마이페이지에서 직접 할 수 있고, 삭제 시 모든 데이터가 즉시 영구 삭제됩니다.
```

**Keywords** (100자, 쉼표 구분, 공백 없이)

```
캘린더,일정,공유캘린더,모임,약속,스케줄,그룹일정,시간표,빈시간,팀일정
```

**Category**: Primary `Productivity` / Secondary `Social Networking`

**Age Rating**: 4+ — 단, 이용약관상 만 14세 미만 이용 불가로 정했습니다. 사용자 생성 콘텐츠(그룹 이름, 일정 제목)가 있으므로 Age Rating 설문에서 "User Generated Content" 항목을 정직하게 신고하세요. 신고에 따라 12+로 조정될 수 있습니다.

---

## 6. 빌드 업로드

```bash
cd web
npm install
CAPACITOR_SERVER_URL=https://app.<도메인> npm run mobile:sync
npm run mobile:ios
```

Xcode에서:

1. 좌측 프로젝트 → `App` 타겟 → **Signing & Capabilities** → Team 선택
2. 상단 기기 선택을 **Any iOS Device (arm64)** 로 변경
3. **Product → Archive**
4. Organizer 창에서 **Distribute App → App Store Connect → Upload**

업로드 후 App Store Connect에서 처리에 10~30분 걸립니다. 처리되면 버전 화면의 Build 항목에서 선택할 수 있습니다.

**Export Compliance**: `ITSAppUsesNonExemptEncryption = false`를 Info.plist에 넣어 두었으므로 업로드마다 암호화 질문이 뜨지 않습니다. (표준 HTTPS만 사용하므로 정확한 신고입니다.)

---

## 7. 심사 대응

첫 심사는 보통 24~48시간 걸립니다. 이 앱 구조에서 반송 가능성이 있는 항목과 대응입니다.

### Guideline 4.2 — Minimum Functionality (이 앱의 가장 큰 위험)

이 앱은 운영 웹사이트를 로드하는 Capacitor 셸입니다. Apple은 "웹사이트를 그대로 감싼 앱"을 반송합니다. 대응:

- **오프라인 화면을 넣어 두었습니다.** 네트워크가 끊겨도 흰 화면이 아니라 브랜드 안내 화면과 자동 복구가 동작합니다. 리뷰어가 기내 모드로 시험하는 경우가 실제로 있습니다.
- **네이티브 딥링크(`toogeduler://`)로 소셜 로그인이 앱으로 복귀합니다.** 웹 브라우저에서 할 수 없는 동작입니다.
- 세로 고정, 안전영역 대응, 네이티브 스플래시로 웹 브라우저와 구분되는 앱 경험을 제공합니다.

그래도 반송된다면, 다음 버전에서 **로컬 알림(`@capacitor/local-notifications`)** 을 추가하는 것이 가장 효과적입니다. 앱이 닫혀 있어도 일정 알림이 기기에서 울리게 되므로, 웹으로 대체할 수 없는 기능이 생깁니다. 이미 일정마다 `reminderMinutes` 값을 저장하고 있으므로 연결만 하면 됩니다.

### Guideline 5.1.1(v) — Account Deletion

마이페이지에 계정 삭제를 구현했습니다. Review Notes에 경로를 반드시 적어 주세요(3절 참고).

### Guideline 4.8 — Login Services

Google·Kakao를 쓰지만 **이메일 회원가입도 함께 제공**하므로 Sign in with Apple 없이 통과하는 것이 일반적입니다. 만약 심사에서 요구받으면 Sign in with Apple을 추가해야 합니다. 이메일 로그인 경로를 지우면 즉시 반송 사유가 되므로 유지하세요.

### Guideline 2.1 — App Completeness

리뷰어 계정이 빈 화면을 보면 반송됩니다. 데모 계정에 데이터를 채워 두세요(3절 참고).

### 반송되었을 때

Resolution Center에 반송 사유가 옵니다. 코드 수정이 필요 없는 오해라면 그 자리에서 설명과 스크린샷·화면 녹화로 반박할 수 있습니다. 수정이 필요하면 Build 번호(`CURRENT_PROJECT_VERSION`)만 올려 다시 Archive하면 됩니다. 반송은 흔한 일이고 계정에 불이익이 없습니다.

---

## 8. Google Play (참고)

- 등록비: **$25 일회성** (Apple보다 저렴)
- 심사: 첫 앱은 며칠~2주 걸릴 수 있습니다.
- 신규 **개인** 개발자 계정은 프로덕션 승인을 신청할 때 **테스터 12명이 비공개 테스트에 14일 연속 참여**한 상태여야 합니다. 중간에 빠지면 기간이 초기화되므로, Apple 심사와 병행해 미리 시작하세요. 조직 계정은 이 조건이 없습니다.
- 필요 자료: 512×512 아이콘, 1024×500 피처 그래픽, 스크린샷 2장 이상, 개인정보처리방침 URL, Data safety 신고, 콘텐츠 등급 설문.
- 업로드 키스토어는 분실하면 복구할 수 없습니다. 안전한 곳에 백업하세요.

---

## 9. 출시 후 남은 기술 부채

`docs/ROADMAP.md`에 있는 항목 중 사용자가 늘어나기 전에 처리해야 하는 것들입니다.

| ID | 내용 | 이유 |
|---|---|---|
| DB-001 | Flyway 마이그레이션 | 현재 `ddl-auto: update`는 컬럼 삭제·타입 변경을 반영하지 않고, 운영 스키마가 예측 불가능해집니다 |
| DB-002 | DB 백업·복원 리허설 | 사용자 데이터가 들어오면 복구 수단이 없습니다 |
| SEC-011 | JWT를 HttpOnly Secure 쿠키로 전환 | 현재 토큰이 localStorage에 있고 OAuth 콜백 시 URL 쿼리로 한 번 노출됩니다 |
| SEC-013 | 초대 링크 만료·재발급 | 한 번 유출된 링크가 영구히 유효합니다 |
| INFRA-002 | OAuth 키 회전 | 이전에 노출된 적이 있는 키는 교체하세요 |
