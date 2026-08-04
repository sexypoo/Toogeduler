# Toogeduler 배포 가이드

구성은 다음처럼 분리합니다.

- `https://<YOUR_DOMAIN>`: Vercel의 Next.js 웹
- `https://api.<YOUR_DOMAIN>`: Railway의 Spring Boot API
- PostgreSQL: API와 같은 Railway 프로젝트의 데이터베이스 서비스

`<YOUR_DOMAIN>`은 실제 구매한 도메인으로 바꾸고, URL 끝에는 `/`를 붙이지 않습니다.

## 0. 배포 전 준비

모든 변경 사항을 GitHub의 배포할 브랜치에 올립니다. 운영 비밀값은 `.env`나 GitHub에 커밋하지 않고 Railway와 Vercel의 Variables 화면에만 입력합니다.

새 JWT 키는 로컬 터미널에서 만들 수 있습니다.

```bash
openssl rand -base64 48
```

## 1. Railway에 PostgreSQL 만들기

1. Railway에서 새 프로젝트를 만듭니다.
2. 프로젝트 캔버스의 `+ New`에서 PostgreSQL을 추가합니다.
3. 기본 서비스 이름이 `Postgres`인지 확인합니다. 다른 이름이면 아래 참조 변수의 `Postgres`도 같은 이름으로 바꿉니다.

## 2. Railway에 API 배포하기

1. 같은 Railway 프로젝트에서 `+ New → GitHub Repo`로 Toogeduler 저장소를 연결합니다.
2. API 서비스의 `Settings → Root Directory`를 `/api`로 지정합니다.
3. `Config File Path`를 `/api/railway.toml`로 지정합니다.
4. API 서비스의 `Variables`에 아래 값을 등록합니다.

```dotenv
DATABASE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
DATABASE_USERNAME=${{Postgres.PGUSER}}
DATABASE_PASSWORD=${{Postgres.PGPASSWORD}}
JWT_SECRET=<openssl로 만든 긴 임의 문자열>
WEB_URL=https://<YOUR_DOMAIN>
OAUTH_REDIRECT_BASE_URL=https://api.<YOUR_DOMAIN>
GOOGLE_CLIENT_ID=<Google 클라이언트 ID>
GOOGLE_CLIENT_SECRET=<Google 클라이언트 보안 비밀>
KAKAO_CLIENT_ID=<Kakao REST API 키>
KAKAO_CLIENT_SECRET=<Kakao Client Secret>
SEED_DEMO=false
```

`PORT`는 직접 만들지 않습니다. Railway가 제공한 `PORT`를 Spring Boot가 자동으로 사용합니다.

5. 배포를 실행합니다. Dockerfile이 Java 21로 API를 빌드하고 `/actuator/health`를 상태 확인에 사용합니다.
6. `Settings → Networking → Public Networking`에서 우선 Railway 도메인을 생성해 API가 실행되는지 확인합니다.

```bash
curl https://<RAILWAY_DOMAIN>/actuator/health
```

응답의 `status`가 `UP`이면 정상입니다.

## 3. Railway API 도메인 연결하기

1. API 서비스의 `Settings → Networking → + Custom Domain`에서 `api.<YOUR_DOMAIN>`을 추가합니다.
2. Railway가 보여주는 `CNAME`과 소유권 확인용 `TXT` 레코드를 도메인의 DNS 관리 화면에 모두 추가합니다.
3. Railway에서 도메인 옆에 정상 표시가 생기고 아래 주소가 열릴 때까지 기다립니다.

```bash
curl https://api.<YOUR_DOMAIN>/actuator/health
```

Railway 커스텀 도메인은 `CNAME`과 `TXT`가 모두 있어야 하며, 인증서가 발급되면 HTTPS가 자동 적용됩니다.

## 4. Vercel에 웹 배포하기

1. Vercel에서 `Add New → Project`를 선택하고 같은 GitHub 저장소를 가져옵니다.
2. `Root Directory`를 `web`으로 지정합니다.
3. Framework Preset은 `Next.js`를 사용합니다.
4. Production 환경변수에 아래 값을 등록합니다.

```dotenv
API_ORIGIN=https://api.<YOUR_DOMAIN>
NEXT_PUBLIC_SHOW_DEMO=false
```

`NEXT_PUBLIC_API_URL`은 설정하지 않습니다. 브라우저는 같은 출처의 `/backend`를 호출하고, Vercel의 Next.js rewrite가 Railway API로 전달합니다.

5. 배포한 뒤 Vercel 기본 도메인에서 로그인 화면과 `/backend/actuator/health`를 확인합니다.

## 5. Vercel 웹 도메인 연결하기

1. Vercel 프로젝트의 `Settings → Domains`에 `<YOUR_DOMAIN>`과 `www.<YOUR_DOMAIN>`을 추가합니다.
2. 둘 중 하나를 대표 주소로 정하고 나머지는 대표 주소로 redirect합니다.
3. 도메인 구매처의 DNS 화면에 Vercel이 제시한 `A` 또는 `CNAME` 레코드를 그대로 추가합니다.
4. Vercel의 도메인 상태와 SSL 발급이 완료될 때까지 기다립니다.

대표 주소를 `www.<YOUR_DOMAIN>`으로 정했다면 Railway의 `WEB_URL`도 반드시 `https://www.<YOUR_DOMAIN>`으로 변경합니다.

## 6. Google·Kakao 운영 URL 등록하기

### Google Cloud Console

- 승인된 JavaScript 원본: `https://<YOUR_DOMAIN>`
- 승인된 리디렉션 URI: `https://api.<YOUR_DOMAIN>/login/oauth2/code/google`

### Kakao Developers

- Web 플랫폼 사이트 도메인: `https://<YOUR_DOMAIN>`
- Kakao Login Redirect URI: `https://api.<YOUR_DOMAIN>/login/oauth2/code/kakao`

대표 주소가 `www`라면 웹 원본과 사이트 도메인도 `www` 주소로 맞춥니다. Redirect URI는 Railway API 주소입니다.

## 7. 최종 확인

- 이메일 회원가입과 로그인
- Kakao·Google 로그인 후 웹 도메인으로 복귀
- 일정 생성·수정·삭제
- 친구 코드 요청·수락 및 친구 캘린더 조회
- 그룹 초대 링크를 새 브라우저에서 열기
- 전체 공개 일정을 로그아웃 상태에서 열기
- Railway 재배포 후 기존 데이터 유지

## 공개 운영 전에 남은 필수 작업

현재 구성으로 소규모 베타 배포는 가능하지만, 공개 운영 전에는 `docs/ROADMAP.md`의 아래 항목을 우선 처리합니다.

- `DB-001`: Flyway 마이그레이션
- `DB-002`: PostgreSQL 백업·복원
- `SEC-011`: JWT를 HttpOnly Secure 쿠키로 전환
- `SEC-012`: 로그인 시도 제한
- `INFRA-002`: 노출된 적 있는 OAuth 키 회전과 비밀값 관리
