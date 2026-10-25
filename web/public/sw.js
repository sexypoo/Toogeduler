// Toogeduler 서비스 워커. 앱 셸(정적 자산)만 캐시하고, 네트워크가 끊기면 오프라인 안내 화면을 보여줍니다.
// API 응답과 Authorization 헤더가 붙은 요청은 다른 계정 데이터가 남지 않도록 절대 캐시하지 않습니다.
// 캐시 판별 규칙은 web/lib/pwa.ts 의 isCacheableRequest 와 같아야 하며, pwa.test.ts 가 두 쪽을 함께 검사합니다.

const CACHE_PREFIX = "toogeduler-";
const SHELL_CACHE = `${CACHE_PREFIX}shell-v1`;
const OFFLINE_URL = "/offline.html";
const PRECACHE_URLS = [OFFLINE_URL, "/icons/icon-192.png"];

const SHELL_PATHS = ["/_next/static/", "/icons/"];
const SHELL_FILES = ["/offline.html", "/manifest.webmanifest"];
const PRIVATE_PATHS = ["/backend/", "/api/"];

function isCacheableRequest(request, origin) {
  if (request.method !== "GET") return false;
  if (request.headers.has("Authorization")) return false;
  let url;
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

function precache() {
  return caches.open(SHELL_CACHE).then((cache) => cache.addAll(PRECACHE_URLS));
}

self.addEventListener("install", (event) => {
  // 오프라인 안내 화면을 받아 두지 못하면 설치를 실패시킵니다(브라우저 기본 오류 화면을 막기 위해).
  event.waitUntil(precache().then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((names) =>
        Promise.all(
          names.filter((name) => name.startsWith(CACHE_PREFIX) && name !== SHELL_CACHE).map((name) => caches.delete(name)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});

// 로그아웃하면 페이지가 캐시를 모두 지운 뒤 오프라인 안내 화면을 다시 받아 두라고 알립니다.
self.addEventListener("message", (event) => {
  if (event.data && event.data.type === "precache") event.waitUntil(precache());
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") return;

  // 페이지 이동은 항상 네트워크로 가고, 실패할 때만 오프라인 안내 화면을 보여줍니다. 페이지 HTML 은 캐시하지 않습니다.
  if (request.mode === "navigate") {
    event.respondWith(
      fetch(request).catch(() => caches.match(OFFLINE_URL).then((cached) => cached || Response.error())),
    );
    return;
  }

  if (!isCacheableRequest(request, self.location.origin)) return;

  event.respondWith(
    caches.open(SHELL_CACHE).then((cache) => {
      const fromNetwork = () =>
        fetch(request).then((response) => {
          if (response.ok && response.type === "basic") cache.put(request, response.clone());
          return response;
        });
      // /_next/static 은 파일 이름에 해시가 있어 내용이 바뀌지 않으므로 캐시를 먼저 씁니다.
      // 아이콘·매니페스트처럼 이름이 고정된 파일은 네트워크를 먼저 쓰고, 끊겼을 때만 캐시로 대신합니다.
      if (new URL(request.url).pathname.startsWith("/_next/static/")) {
        return cache.match(request).then((cached) => cached || fromNetwork());
      }
      return fromNetwork().catch(() => cache.match(request).then((cached) => cached || Response.error()));
    }),
  );
});
