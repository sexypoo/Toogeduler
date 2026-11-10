const API = process.env.NEXT_PUBLIC_API_URL || "/backend";

// 소셜 로그인은 세션 쿠키를 API 도메인에 심어야 하므로 프록시(/backend)로 시작할 수 없습니다.
// 반드시 API의 실제 출처(origin)를 NEXT_PUBLIC_OAUTH_URL로 지정해야 합니다.
// 값이 없으면 빈 문자열이 되고, 로그인 화면이 소셜 버튼을 비활성화합니다.
const configuredOauth = (process.env.NEXT_PUBLIC_OAUTH_URL || "").replace(/\/+$/, "");
// 개발 환경 기본값은 API 서버 주소입니다. /backend 프록시로 시작하면 API 가 Next 주소(X-Forwarded-Host)로 되돌려 보내 404 가 납니다.
const OAUTH_ORIGIN = configuredOauth || (process.env.NODE_ENV === "production" ? "" : "http://localhost:8080");
const OAUTH_READY = OAUTH_ORIGIN !== "";

// 상태 코드를 함께 담아, 호출하는 쪽이 401(로그인 만료)과 일시적인 오류를 구분할 수 있게 합니다.
// 매개변수 속성(public status) 대신 필드를 써서 node --test 의 타입 제거만으로 불러올 수 있게 합니다.
export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
    this.name = "ApiError";
  }
}
// 로그인 토큰은 API 가 내려주는 HttpOnly 쿠키에만 있습니다(SEC-011, docs/AUTH_COOKIE_DESIGN.md). 아래 둘은 전환 전 웹이 남긴 키입니다.
const LEGACY_TOKEN = "toogeduler_token";
const LEGACY_DEVICE = "toogeduler_device";
const UNSAFE_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

function cookie(name: string) {
  const prefix = `${name}=`;
  const found = document.cookie.split("; ").find((c) => c.startsWith(prefix));
  return found ? decodeURIComponent(found.slice(prefix.length)) : null;
}

// 서버 왕복 없이 로그인 여부를 아는 신호입니다. 진짜 판단은 서버의 401 입니다.
// 옛 토큰이 남아 있으면 첫 요청 전에 쿠키로 옮기므로 로그인 상태로 봅니다.
export function signedIn() {
  if (typeof window === "undefined") return false;
  return cookie("tg_signed_in") === "1" || localStorage.getItem(LEGACY_TOKEN) !== null;
}

let upgrade: Promise<void> | null = null;
// 결정 4: 옛 localStorage 토큰을 한 번 쿠키로 바꿉니다. 성공·401 이면 두 키를 지우고, 네트워크 오류면 남겨 두고 실패시킵니다.
// 첫 데이터 요청이 쿠키 없이 나가 401 로 로그인 화면에 튕기지 않도록 api() 가 이것을 먼저 기다립니다.
function upgradeLegacySession() {
  if (typeof window === "undefined" || !localStorage.getItem(LEGACY_TOKEN)) return Promise.resolve();
  upgrade ??= (async () => {
    const res = await fetch(`${API}/api/auth/session`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${localStorage.getItem(LEGACY_TOKEN)}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ deviceToken: localStorage.getItem(LEGACY_DEVICE) }),
    });
    if (!res.ok && res.status !== 401) throw new Error(`session upgrade ${res.status}`);
    localStorage.removeItem(LEGACY_TOKEN);
    localStorage.removeItem(LEGACY_DEVICE);
  })().finally(() => {
    upgrade = null;
  });
  return upgrade;
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  await upgradeLegacySession();
  const headers = new Headers(options.headers);
  if (options.body) headers.set("Content-Type", "application/json");
  // 로그인·가입·소셜 교환 응답이 쿠키를 내려주도록 요청합니다(AuthCookies.MODE_HEADER).
  headers.set("X-Auth-Mode", "cookie");
  // CSRF 이중 제출. 쿠키 이름은 운영이 `__Host-XSRF-TOKEN`, http 개발 환경이 `XSRF-TOKEN` 입니다.
  const xsrf = cookie("__Host-XSRF-TOKEN") ?? cookie("XSRF-TOKEN");
  if (xsrf && UNSAFE_METHODS.has((options.method ?? "GET").toUpperCase())) headers.set("X-XSRF-TOKEN", xsrf);
  const res = await fetch(`${API}${path}`, { ...options, headers });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    // 서버 메시지 없는 403 은 대개 CSRF 쿠키가 아직 없던 요청입니다. 화면을 새로 불러오면 쿠키를 받습니다.
    const fallback = res.status === 403 ? "화면을 새로 불러온 뒤 다시 해주세요." : "요청을 처리하지 못했습니다.";
    throw new ApiError(res.status, body.message || fallback);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

export { API, OAUTH_ORIGIN, OAUTH_READY };
