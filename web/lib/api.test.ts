import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { ApiError, api, signedIn } from "./api.ts";

type Call = { url: string; method: string; headers: Headers; body?: string };
let calls: Call[] = [];
let respond: (call: Call) => Response | Promise<Response> = () => new Response(null, { status: 204 });
const store = new Map<string, string>();

beforeEach(() => {
  calls = [];
  store.clear();
  respond = () => new Response(null, { status: 204 });
  Object.assign(globalThis, {
    window: globalThis,
    document: { cookie: "" },
    localStorage: {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => store.set(k, v),
      removeItem: (k: string) => store.delete(k),
    },
    fetch: async (url: string, init: RequestInit = {}) => {
      const call = {
        url,
        method: init.method ?? "GET",
        headers: new Headers(init.headers),
        body: init.body as string | undefined,
      };
      calls.push(call);
      return respond(call);
    },
  });
});

const cookies = (value: string) =>
  ((globalThis as unknown as { document: { cookie: string } }).document.cookie = value);

test("상태 변경 요청에만 XSRF 쿠키 값을 헤더로 싣고, 운영 이름을 개발 이름보다 먼저 읽는다", async () => {
  cookies("tg_signed_in=1; XSRF-TOKEN=dev; __Host-XSRF-TOKEN=prod");
  await api("/api/events", { method: "POST", body: "{}" });
  await api("/api/events");
  assert.equal(calls[0].headers.get("X-XSRF-TOKEN"), "prod");
  assert.equal(calls[0].headers.get("X-Auth-Mode"), "cookie");
  assert.equal(calls[0].headers.get("Authorization"), null);
  assert.equal(calls[1].headers.get("X-XSRF-TOKEN"), null);
  cookies("XSRF-TOKEN=dev");
  await api("/api/events/1", { method: "delete" });
  assert.equal(calls[2].headers.get("X-XSRF-TOKEN"), "dev");
});

test("signedIn 은 표시 쿠키나 옛 토큰이 있을 때만 참이다", () => {
  assert.equal(signedIn(), false);
  cookies("other=1; tg_signed_in=1");
  assert.equal(signedIn(), true);
  cookies("");
  store.set("toogeduler_token", "old");
  assert.equal(signedIn(), true);
});

test("옛 토큰은 첫 요청 전에 한 번 쿠키로 옮기고 두 키를 지운다", async () => {
  store.set("toogeduler_token", "old");
  store.set("toogeduler_device", "dev");
  await Promise.all([api("/api/auth/me"), api("/api/groups")]);
  assert.deepEqual(
    calls.map((c) => c.url),
    ["/backend/api/auth/session", "/backend/api/auth/me", "/backend/api/groups"],
  );
  assert.equal(calls[0].headers.get("Authorization"), "Bearer old");
  assert.deepEqual(JSON.parse(calls[0].body!), { deviceToken: "dev" });
  assert.equal(store.size, 0);
});

test("옛 토큰 전환이 401 이면 키를 지우고, 네트워크 오류면 남겨 두고 실패한다", async () => {
  store.set("toogeduler_token", "expired");
  respond = (c) => new Response(null, { status: c.url.endsWith("/session") ? 401 : 204 });
  await api("/api/auth/me");
  assert.equal(store.has("toogeduler_token"), false);

  store.set("toogeduler_token", "old");
  respond = () => Promise.reject(new TypeError("offline"));
  await assert.rejects(api("/api/auth/me"), TypeError);
  assert.equal(calls.at(-1)!.url, "/backend/api/auth/session");
  assert.equal(store.get("toogeduler_token"), "old");
});

test("옛 토큰 전환이 404 면 키를 지우고 진행한다", async () => {
  store.set("toogeduler_token", "old");
  store.set("toogeduler_device", "dev");
  respond = (c) => new Response(null, { status: c.url.endsWith("/session") ? 404 : 204 });
  await api("/api/auth/me");
  assert.equal(store.size, 0);
  assert.equal(calls.at(-1)!.url, "/backend/api/auth/me");
});

test("옛 토큰 전환이 503 이면 키를 남겨 두고 실패한다", async () => {
  store.set("toogeduler_token", "old");
  store.set("toogeduler_device", "dev");
  respond = () => new Response(null, { status: 503 });
  await assert.rejects(api("/api/auth/me"), /session upgrade 503/);
  assert.equal(calls.length, 1);
  assert.equal(store.get("toogeduler_token"), "old");
  assert.equal(store.get("toogeduler_device"), "dev");
});

test("메시지 없는 403 은 새로고침 안내로 바꾼다", async () => {
  respond = () => new Response("{}", { status: 403 });
  await assert.rejects(api("/api/events", { method: "POST", body: "{}" }), (e: unknown) => {
    assert.ok(e instanceof ApiError);
    assert.equal(e.status, 403);
    assert.equal(e.message, "화면을 새로 불러온 뒤 다시 해주세요.");
    return true;
  });
});
