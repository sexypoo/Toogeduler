import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import manifest from "../app/manifest.ts";
import { clearPwaCaches, isCacheableRequest, registerServiceWorker, type CacheCandidate } from "./pwa.ts";

const ORIGIN = "https://app.toogeduler.com";
const web = new URL("..", import.meta.url);

function request(path: string, init: { method?: string; headers?: Record<string, string> } = {}): CacheCandidate {
  const headers = new Headers(init.headers);
  return { method: init.method ?? "GET", url: new URL(path, ORIGIN).href, headers };
}

// public/sw.js 는 브라우저가 그대로 받는 일반 스크립트라 lib/pwa.ts 를 import 하지 못합니다.
// 서비스 워커 전역을 흉내 낸 샌드박스에서 실행해 그 안의 판별 함수를 꺼내, 같은 표로 함께 검사합니다.
function serviceWorkerPredicate() {
  const source = readFileSync(new URL("public/sw.js", web), "utf8");
  const sandbox = { self: { addEventListener() {}, location: { origin: ORIGIN } }, URL, caches: {} };
  runInNewContext(`${source}\nself.isCacheableRequest = isCacheableRequest;`, sandbox);
  return (sandbox.self as unknown as { isCacheableRequest: typeof isCacheableRequest }).isCacheableRequest;
}

const cases: [string, CacheCandidate, boolean][] = [
  ["Next 정적 자산", request("/_next/static/chunks/main-abc123.js"), true],
  ["홈 화면 아이콘", request("/icons/icon-192.png"), true],
  ["오프라인 안내 화면", request("/offline.html"), true],
  ["매니페스트", request("/manifest.webmanifest"), true],
  ["API 프록시", request("/backend/api/events?from=1&to=2"), false],
  ["API 경로", request("/api/auth/me"), false],
  [
    "Authorization 헤더가 붙은 정적 자산",
    request("/_next/static/a.js", { headers: { Authorization: "Bearer x" } }),
    false,
  ],
  ["페이지 HTML", request("/groups"), false],
  ["GET 이 아닌 요청", request("/_next/static/a.js", { method: "POST" }), false],
  ["다른 출처", { ...request("/"), url: "https://api.toogeduler.com/_next/static/a.js" }, false],
];

for (const [name, candidate, expected] of cases) {
  test(`캐시 판별(lib/pwa.ts): ${name}`, () => {
    assert.equal(isCacheableRequest(candidate, ORIGIN), expected);
  });
}

test("서비스 워커(public/sw.js)의 캐시 판별이 lib/pwa.ts 와 같다", () => {
  const predicate = serviceWorkerPredicate();
  for (const [name, candidate, expected] of cases) {
    assert.equal(predicate(candidate, ORIGIN), expected, name);
  }
});

function fakeContainer() {
  const calls: string[] = [];
  const container = {
    register: async (url: string | URL) => {
      calls.push(String(url));
      return {} as ServiceWorkerRegistration;
    },
  };
  return { calls, container };
}

test("네이티브(Capacitor) 앱에서는 서비스 워커를 등록하지 않는다", async () => {
  const { calls, container } = fakeContainer();
  assert.equal(await registerServiceWorker({ native: true, production: true, serviceWorker: container }), null);
  assert.deepEqual(calls, []);
});

test("개발 빌드에서는 서비스 워커를 등록하지 않는다", async () => {
  const { calls, container } = fakeContainer();
  assert.equal(await registerServiceWorker({ native: false, production: false, serviceWorker: container }), null);
  assert.deepEqual(calls, []);
});

test("운영 빌드의 브라우저에서는 /sw.js 를 등록한다", async () => {
  const { calls, container } = fakeContainer();
  assert.notEqual(await registerServiceWorker({ native: false, production: true, serviceWorker: container }), null);
  assert.deepEqual(calls, ["/sw.js"]);
});

test("서비스 워커를 지원하지 않는 브라우저에서는 아무것도 하지 않는다", async () => {
  assert.equal(await registerServiceWorker({ native: false, production: true }), null);
});

test("로그아웃 때 앱 캐시만 지우고 오프라인 화면을 다시 받아 두게 알린다", async () => {
  const deleted: string[] = [];
  const messages: unknown[] = [];
  const storage = {
    keys: async () => ["toogeduler-shell-v1", "other-site"],
    delete: async (name: string) => {
      deleted.push(name);
      return true;
    },
  };
  await clearPwaCaches(storage, { postMessage: (message: unknown) => messages.push(message) });
  assert.deepEqual(deleted, ["toogeduler-shell-v1"]);
  assert.deepEqual(messages, [{ type: "precache" }]);
});

// PNG 머리(IHDR)에서 가로·세로 크기를 읽습니다.
function pngSize(path: string) {
  const bytes = readFileSync(new URL(`public${path}`, web));
  return `${bytes.readUInt32BE(16)}x${bytes.readUInt32BE(20)}`;
}

test("매니페스트는 standalone 으로 / 에서 시작하고 192·512px 아이콘을 포함한다", () => {
  const m = manifest();
  assert.equal(m.name, "Toogeduler");
  assert.equal(m.display, "standalone");
  assert.equal(m.start_url, "/");
  const icons = m.icons ?? [];
  assert.ok(icons.some((icon) => icon.sizes === "192x192" && icon.purpose === "any"));
  assert.ok(icons.some((icon) => icon.sizes === "512x512" && icon.purpose === "any"));
  assert.ok(icons.some((icon) => icon.sizes === "512x512" && icon.purpose === "maskable"));
  for (const icon of icons) {
    assert.ok(!String(icon.purpose).includes(" "), `${icon.src}: any 와 maskable 을 한 항목에 합치지 않는다`);
    assert.equal(pngSize(icon.src), icon.sizes, icon.src);
  }
  assert.equal(pngSize("/icons/apple-touch-icon.png"), "180x180");
});
