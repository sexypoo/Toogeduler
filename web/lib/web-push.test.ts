import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import {
  decodeBase64Url,
  disablePush,
  enablePush,
  readPushState,
  settleWithin,
  unsubscribeOnLogout,
} from "./web-push.ts";
import type { PushEnvironment } from "./web-push.ts";

const KEY = "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U";
const ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";

type Call = { path: string; method: string; body?: unknown };

function fakeEnv(
  options: {
    native?: boolean;
    noPush?: boolean;
    iosBrowserTab?: boolean;
    permission?: NotificationPermission;
    answer?: NotificationPermission;
    subscribed?: boolean;
    fail?: (call: Call) => number | undefined;
  } = {},
) {
  const state = { asked: 0, subscribed: options.subscribed ?? false, calls: [] as Call[] };
  const subscription = {
    endpoint: ENDPOINT,
    toJSON: () => ({ endpoint: ENDPOINT, keys: { p256dh: "p", auth: "a" } }),
    unsubscribe: async () => {
      state.subscribed = false;
      return true;
    },
  };
  const env: PushEnvironment = {
    native: options.native ?? false,
    iosBrowserTab: options.iosBrowserTab ?? false,
    pushManager: options.noPush
      ? undefined
      : async () => ({
          getSubscription: async () => (state.subscribed ? subscription : null),
          subscribe: async () => {
            state.subscribed = true;
            return subscription;
          },
        }),
    notification: options.noPush
      ? undefined
      : {
          permission: options.permission ?? "default",
          requestPermission: async () => {
            state.asked++;
            return options.answer ?? "granted";
          },
        },
    request: async (path, init) => {
      const call = {
        path,
        method: init?.method ?? "GET",
        body: init?.body ? JSON.parse(String(init.body)) : undefined,
      };
      state.calls.push(call);
      const status = options.fail?.(call);
      if (status) throw Object.assign(new Error("fail"), { status });
      return call.method === "GET" ? { publicKey: KEY } : undefined;
    },
  };
  return { env, state };
}

test("패널을 열기만 해서는 권한을 묻지 않는다", async () => {
  const { env, state } = fakeEnv();
  assert.deepEqual(await readPushState(env), { status: "off", publicKey: KEY });
  assert.equal(state.asked, 0);
});

test("네이티브 앱에서는 행을 숨기고 서버에도 묻지 않는다", async () => {
  const { env, state } = fakeEnv({ native: true });
  assert.equal((await readPushState(env)).status, "hidden");
  assert.equal(state.calls.length, 0);
});

test("푸시 API 가 없으면 숨기고, iOS 브라우저 탭이면 홈 화면 안내를 보여 준다", async () => {
  assert.equal((await readPushState(fakeEnv({ noPush: true }).env)).status, "hidden");
  assert.equal((await readPushState(fakeEnv({ noPush: true, iosBrowserTab: true }).env)).status, "install");
});

test("공개키가 404 면 지원 안 됨으로 숨긴다", async () => {
  const { env } = fakeEnv({ fail: (c) => (c.method === "GET" ? 404 : undefined) });
  assert.equal((await readPushState(env)).status, "hidden");
});

test("서비스 워커 준비가 실패하면 숨긴다", async () => {
  const { env } = fakeEnv();
  env.pushManager = () => Promise.reject(new Error("등록 없음"));
  assert.equal((await readPushState(env)).status, "hidden");
});

test("서비스 워커 준비가 끝나지 않아도 시간 제한 뒤 숨긴다", async () => {
  const { env } = fakeEnv();
  env.pushManager = () => settleWithin(new Promise<never>(() => {}), 10);
  assert.equal((await readPushState(env)).status, "hidden");
});

test("시간 안에 끝나면 결과를 그대로 돌려준다", async () => {
  assert.equal(await settleWithin(Promise.resolve(1), 10), 1);
  await assert.rejects(settleWithin(Promise.reject(new Error("x")), 10), /x/);
});

test("브라우저에서 차단돼 있으면 차단 상태로 보여 준다", async () => {
  const { env } = fakeEnv({ permission: "denied" });
  assert.equal((await readPushState(env)).status, "blocked");
});

test("살아 있는 구독은 받는 중으로 보이고 서버에 조용히 다시 등록한다", async () => {
  const { env, state } = fakeEnv({ permission: "granted", subscribed: true });
  assert.equal((await readPushState(env)).status, "on");
  assert.equal(state.calls.at(-1)?.method, "POST");
});

test("권한을 거부하면 서버를 부르지 않고 차단 상태가 된다", async () => {
  const { env, state } = fakeEnv({ answer: "denied" });
  assert.deepEqual(await enablePush(env, KEY), { status: "blocked" });
  assert.equal(state.calls.length, 0);
  assert.equal(state.subscribed, false);
});

test("팝업을 그냥 닫으면 꺼짐으로 두고 다시 누르라고 안내한다", async () => {
  const { env, state } = fakeEnv({ answer: "default" });
  assert.deepEqual(await enablePush(env, KEY), { status: "off", note: "dismissed" });
  assert.equal(state.calls.length, 0);
});

test("구독에 성공하면 endpoint 와 keys 를 서버에 등록한다", async () => {
  const { env, state } = fakeEnv();
  assert.deepEqual(await enablePush(env, KEY), { status: "on" });
  assert.deepEqual(state.calls, [
    {
      path: "/api/push/subscriptions",
      method: "POST",
      body: { endpoint: ENDPOINT, keys: { p256dh: "p", auth: "a" } },
    },
  ]);
});

test("서버 등록이 실패하면 브라우저 구독도 되돌리고 꺼짐으로 둔다", async () => {
  const { env, state } = fakeEnv({ fail: () => 500 });
  assert.deepEqual(await enablePush(env, KEY), { status: "off", note: "enableFailed" });
  assert.equal(state.subscribed, false);
});

test("끄면 서버에서 지우고 브라우저 구독을 해제한다", async () => {
  const { env, state } = fakeEnv({ permission: "granted", subscribed: true });
  assert.deepEqual(await disablePush(env), { status: "off" });
  assert.deepEqual(state.calls, [{ path: "/api/push/subscriptions", method: "DELETE", body: { endpoint: ENDPOINT } }]);
  assert.equal(state.subscribed, false);
});

test("끄기 중 서버 오류면 켜짐을 유지하고 다시 시도할 수 있게 구독을 남긴다", async () => {
  const { env, state } = fakeEnv({ permission: "granted", subscribed: true, fail: () => 500 });
  assert.deepEqual(await disablePush(env), { status: "on", note: "disableFailed" });
  assert.equal(state.subscribed, true);
});

test("로그아웃 때는 서버 삭제가 실패해도 브라우저 구독을 해제한다", async () => {
  const { env, state } = fakeEnv({ permission: "granted", subscribed: true, fail: () => 401 });
  await unsubscribeOnLogout(env);
  assert.equal(state.subscribed, false);
});

test("공개키를 subscribe 가 받는 65바이트로 바꾼다", () => {
  const bytes = decodeBase64Url(KEY);
  assert.equal(bytes.length, 65);
  assert.equal(bytes[0], 4);
});

test("서비스 워커는 payload 의 제목·내용을 그대로 띄우고 url 로 묶는다", async () => {
  const source = readFileSync(new URL("../public/sw.js", import.meta.url), "utf8");
  const handlers: Record<string, (event: unknown) => void> = {};
  const shown: [string, { body: string; tag: string; data: { url: string } }][] = [];
  const self = {
    addEventListener: (type: string, fn: (event: unknown) => void) => (handlers[type] = fn),
    location: { origin: "https://app.toogeduler.com" },
    registration: { showNotification: async (...args: (typeof shown)[number]) => shown.push(args) },
  };
  runInNewContext(source, { self, URL, caches: {} });
  const payload = { title: "그룹 일정이 잡혔어요", message: "사진 동아리 · 11월 3일 19:00", url: "/?group=7" };
  let pending: Promise<unknown> | undefined;
  handlers.push({ data: { json: () => payload }, waitUntil: (p: Promise<unknown>) => (pending = p) });
  await pending;
  assert.equal(shown[0][0], payload.title);
  assert.equal(shown[0][1].body, payload.message);
  assert.equal(shown[0][1].tag, "/?group=7");
  assert.equal(shown[0][1].data.url, "/?group=7");
});

function notificationClick(windows: { url: string; navigate?: (url: string) => Promise<unknown> }[]) {
  const source = readFileSync(new URL("../public/sw.js", import.meta.url), "utf8");
  const handlers: Record<string, (event: unknown) => void> = {};
  const opened: string[] = [];
  const clients = windows.map((w) => ({ ...w, focus: async () => null }));
  const self = {
    addEventListener: (type: string, fn: (event: unknown) => void) => (handlers[type] = fn),
    location: { origin: "https://app.toogeduler.com" },
    clients: {
      matchAll: async () => clients,
      openWindow: async (url: string) => opened.push(url),
    },
  };
  runInNewContext(source, { self, URL, caches: {} });
  let pending: Promise<unknown> | undefined;
  handlers.notificationclick({
    notification: { close: () => {}, data: { url: "/?group=7" } },
    waitUntil: (p: Promise<unknown>) => (pending = p),
  });
  return { done: pending!, opened };
}

test("알림을 누르면 열린 창을 그 화면으로 이동시킨다", async () => {
  const navigated: string[] = [];
  const { done, opened } = notificationClick([
    { url: "https://app.toogeduler.com/mypage", navigate: async (url) => navigated.push(url) },
  ]);
  await done;
  assert.deepEqual(navigated, ["https://app.toogeduler.com/?group=7"]);
  assert.deepEqual(opened, []);
});

test("제어받지 않는 창이라 이동이 실패하면 새 창으로 연다", async () => {
  const { done, opened } = notificationClick([
    { url: "https://app.toogeduler.com/mypage", navigate: () => Promise.reject(new TypeError("not controlled")) },
  ]);
  await done;
  assert.deepEqual(opened, ["https://app.toogeduler.com/?group=7"]);
});
