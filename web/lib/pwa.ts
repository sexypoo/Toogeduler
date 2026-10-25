// 홈 화면 설치(PWA)용 서비스 워커의 캐시 규칙과 등록·정리 함수입니다.
// 플러그인·브라우저 전역에 직접 기대지 않고 필요한 것을 인자로 받으므로 `npm test` 로 검증합니다.
// 캐시 판별 규칙은 public/sw.js 에도 같은 내용이 있고, pwa.test.ts 가 두 쪽을 같은 표로 함께 검사합니다.

// 이 접두사로 시작하는 캐시만 서비스 워커가 만들고 지웁니다.
export const PWA_CACHE_PREFIX = "toogeduler-";
export const SERVICE_WORKER_URL = "/sw.js";

// 앱 셸로 캐시해도 되는 정적 자산 경로입니다. 페이지 HTML 과 API 응답은 넣지 않습니다.
const SHELL_PATHS = ["/_next/static/", "/icons/"];
const SHELL_FILES = ["/offline.html", "/manifest.webmanifest"];
// 계정마다 내용이 다른 응답이 오는 경로입니다. 다른 계정 데이터가 기기에 남지 않게 절대 캐시하지 않습니다.
const PRIVATE_PATHS = ["/backend/", "/api/"];

export type CacheCandidate = {
  method: string;
  url: string;
  headers: { has(name: string): boolean };
};

// 서비스 워커가 이 요청의 응답을 캐시에 넣어도 되는지 판단합니다.
// 같은 출처의 GET 이면서 앱 셸 정적 자산이고, API 경로도 아니고 Authorization 헤더도 없어야 합니다.
export function isCacheableRequest(request: CacheCandidate, origin: string) {
  if (request.method !== "GET") return false;
  if (request.headers.has("Authorization")) return false;
  let url: URL;
  try {
    url = new URL(request.url);
  } catch {
    return false;
  }
  if (url.origin !== origin) return false;
  const path = url.pathname;
  if (PRIVATE_PATHS.some((prefix) => path.startsWith(prefix))) return false;
  return SHELL_FILES.includes(path) || SHELL_PATHS.some((prefix) => path.startsWith(prefix));
}

export type PwaEnvironment = {
  // Capacitor.isNativePlatform() 결과. 네이티브 셸은 자체 오프라인 화면(native-shell/error.html)을 쓰므로 등록하지 않습니다.
  native: boolean;
  production: boolean;
  serviceWorker?: Pick<ServiceWorkerContainer, "register">;
};

// 운영 빌드의 일반 브라우저에서만 서비스 워커를 등록합니다. 등록하지 않으면 null 을 돌려줍니다.
export async function registerServiceWorker(env: PwaEnvironment) {
  if (env.native || !env.production || !env.serviceWorker) return null;
  try {
    return await env.serviceWorker.register(SERVICE_WORKER_URL, { scope: "/" });
  } catch (e) {
    console.error("[pwa] 서비스 워커 등록 실패", e);
    return null;
  }
}

// 로그아웃·계정 삭제 때 서비스 워커 캐시를 모두 비웁니다.
// 오프라인 안내 화면은 지운 뒤 서비스 워커에게 다시 받아 두라고 알립니다(정적 파일이라 계정과 무관합니다).
export async function clearPwaCaches(
  cacheStorage: Pick<CacheStorage, "keys" | "delete"> | undefined,
  controller?: Pick<ServiceWorker, "postMessage"> | null,
) {
  if (!cacheStorage) return;
  try {
    const names = await cacheStorage.keys();
    await Promise.all(
      names.filter((name) => name.startsWith(PWA_CACHE_PREFIX)).map((name) => cacheStorage.delete(name)),
    );
    controller?.postMessage({ type: "precache" });
  } catch (e) {
    console.error("[pwa] 캐시 정리 실패", e);
  }
}

// 브라우저에서 로그아웃할 때 부릅니다. 캐시 API 가 없는 환경(서버 렌더링, 비보안 출처)에서는 아무것도 하지 않습니다.
export function clearBrowserPwaCaches() {
  if (typeof window === "undefined" || !("caches" in window)) return Promise.resolve();
  const controller = "serviceWorker" in navigator ? navigator.serviceWorker.controller : null;
  return clearPwaCaches(window.caches, controller);
}
