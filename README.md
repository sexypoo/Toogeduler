# Toogeduler

사람들의 일정을 안전하게 공유하고, 모두가 만날 수 있는 시간을 찾는 공유 캘린더입니다.

## 구성

- `web`: Next.js 15 + TypeScript 프론트엔드
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

## 소셜 로그인 설정

`api/src/main/resources/application.yml`의 환경변수에 Google/Kakao OAuth 키를 설정하면 로그인 버튼이 활성화됩니다.

- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`

## 주요 API

- `/api/auth/*` 회원가입·로그인
- `/api/events/*` 일정 CRUD 및 공개 URL
- `/api/groups/*` 그룹·초대 링크·그룹 캘린더
- `/api/groups/{id}/availability` 전원 및 후보 가능 시간 계산

