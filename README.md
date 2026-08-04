# Toogeduler

사람들의 일정을 안전하게 공유하고, 모두가 만날 수 있는 시간을 찾는 공유 캘린더입니다.

## 구성

- `web`: Next.js 16 + TypeScript 프론트엔드
- `api`: Spring Boot 3 + Spring Security + JPA API
- PostgreSQL 16

## 빠른 시작

### 1. 데이터베이스

```bash
docker compose up -d db
```

### 2. API

JDK 21 이상과 Maven이 필요합니다.

```bash
cd api
./mvnw spring-boot:run
```

API는 `http://localhost:8080`에서 실행됩니다. 처음 실행할 때 데모 계정과 일정이 자동 생성됩니다.

### 3. 웹

```bash
cd web
cp .env.example .env.local
npm install
npm run dev
```

웹은 `http://localhost:3000`에서 실행됩니다.

데모 로그인: `minji@toogeduler.app` / `password123!`

## 주요 API

- `/api/auth/*` 회원가입·로그인
- `/api/events/*` 일정 CRUD 및 공개 URL
- `/api/groups/*` 그룹·초대 링크·그룹 캘린더
- `/api/groups/{id}/availability` 전원 및 후보 가능 시간 계산
- `/api/notifications/*` 알림 목록·읽음 처리

## Google·Kakao 소셜 로그인 설정

애플리케이션 코드는 준비되어 있으며, 발급받은 키는 저장소에 작성하지 않고 실행 환경변수로 전달합니다.

### Google

Google Cloud Console의 OAuth 2.0 클라이언트에 다음 URI를 등록합니다.

- 승인된 JavaScript 원본: `http://localhost:3000`
- 승인된 리디렉션 URI: `http://localhost:8080/login/oauth2/code/google`

### Kakao

Kakao Developers의 `[앱] → [플랫폼 키] → [REST API 키]`에서 Redirect URI와 Client Secret을 설정하고, 동의 항목에서 닉네임을 활성화합니다. Toogeduler는 서버 REST API 방식이므로 JavaScript 키는 사용하지 않습니다. 이메일 권한이 없는 일반 앱에서도 카카오 고유 회원번호로 가입할 수 있습니다.

- Redirect URI: `http://localhost:8080/login/oauth2/code/kakao`
- REST API 키를 `KAKAO_CLIENT_ID`로 사용
- Client Secret을 활성화한 경우 `KAKAO_CLIENT_SECRET`으로 사용

### `.env` 작성

```bash
cd api
cp .env.example .env
```

`api/.env` 파일의 아래 항목에 발급값을 입력합니다.

```dotenv
GOOGLE_CLIENT_ID=발급값
GOOGLE_CLIENT_SECRET=발급값
KAKAO_CLIENT_ID=발급값
KAKAO_CLIENT_SECRET=발급값
```

이후 API를 실행하면 Spring Boot가 `.env`를 자동으로 읽습니다.

```bash
./mvnw spring-boot:run
```

운영 환경에서는 `WEB_URL`과 `OAUTH_REDIRECT_BASE_URL`도 각각 실제 웹·API HTTPS 주소로 지정합니다.

## Railway + Vercel 배포

Railway에 Spring Boot API와 PostgreSQL을, Vercel에 Next.js 웹을 배포하는 전체 절차는 [배포 가이드](docs/DEPLOYMENT.md)를 참고하세요.
