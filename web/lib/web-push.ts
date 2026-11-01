// 알림 센터의 "이 기기에서 알림 받기"(웹 푸시) 상태 판단과 구독·해제 흐름입니다.
// 브라우저 전역과 API 클라이언트를 인자로 받으므로 `npm test` 로 검증합니다. 실제 브라우저 연결은 web-push-browser.ts 에 있습니다.
// 서버 엔드포인트: GET /api/push/vapid-public-key, POST·DELETE /api/push/subscriptions (api 의 PushController).

// hidden: 행을 그리지 않음(네이티브 앱, 미지원 브라우저, 서버에 VAPID 키 없음)
// install: iOS Safari 에서 홈 화면에 추가하지 않아 푸시 API 가 없는 경우. 스위치 대신 안내 한 줄
export type PushStatus = "hidden" | "install" | "off" | "on" | "blocked";
// 스위치를 누른 뒤 한 번만 보여 줄 보조 문구의 종류입니다.
export type PushNote = "dismissed" | "enableFailed" | "disableFailed";
export type PushResult = { status: PushStatus; note?: PushNote };

type Subscription = { endpoint: string; toJSON(): unknown; unsubscribe(): Promise<boolean> };
type PushManagerLike = {
  getSubscription(): Promise<Subscription | null>;
  subscribe(options: {
    userVisibleOnly: boolean;
    applicationServerKey: Uint8Array<ArrayBuffer>;
  }): Promise<Subscription>;
};

export type PushEnvironment = {
  // Capacitor.isNativePlatform() 결과. 앱 안에서는 로컬 알림을 쓰므로 이 행을 그리지 않습니다.
  native: boolean;
  // 서비스 워커와 PushManager 가 모두 있을 때만 넘깁니다.
  pushManager?: () => Promise<PushManagerLike>;
  notification?: {
    permission: NotificationPermission;
    requestPermission(): Promise<NotificationPermission>;
  };
  // iOS 이면서 홈 화면 앱(standalone)으로 열리지 않았는지
  iosBrowserTab: boolean;
  // lib/api 의 api() 와 같은 모양. 실패하면 status 가 담긴 오류를 던집니다.
  request: (path: string, init?: RequestInit) => Promise<unknown>;
};

const KEY_PATH = "/api/push/vapid-public-key";
const SUBSCRIPTIONS_PATH = "/api/push/subscriptions";

// 서버가 준 base64url 공개키를 pushManager.subscribe 가 받는 바이트 배열로 바꿉니다.
export function decodeBase64Url(value: string) {
  const base64 = (value + "=".repeat((4 - (value.length % 4)) % 4)).replace(/-/g, "+").replace(/_/g, "/");
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}

function supported(env: PushEnvironment) {
  return !env.native && !!env.pushManager && !!env.notification;
}

// 서버에 묻기 전에 바로 알 수 있는 숨김·설치 안내 여부입니다. 네이티브 앱에서 행이 잠깐 보였다 사라지지 않게 첫 렌더에 씁니다.
export function pushSupport(env: PushEnvironment): "hidden" | "install" | "supported" {
  if (env.native) return "hidden";
  if (!supported(env)) return env.iosBrowserTab ? "install" : "hidden";
  return "supported";
}

function register(env: PushEnvironment, subscription: Subscription) {
  return env.request(SUBSCRIPTIONS_PATH, { method: "POST", body: JSON.stringify(subscription.toJSON()) });
}

// 패널을 열 때 부릅니다. 권한 팝업은 띄우지 않습니다(requestPermission 은 enablePush 에서만 부릅니다).
// 공개키를 받지 못하면(404: 서버에 키 없음) 행을 숨기고, 받은 공개키는 켤 때 다시 쓰도록 돌려줍니다.
export async function readPushState(env: PushEnvironment): Promise<{ status: PushStatus; publicKey?: string }> {
  const support = pushSupport(env);
  if (support !== "supported") return { status: support };
  let publicKey: string;
  try {
    publicKey = ((await env.request(KEY_PATH)) as { publicKey: string }).publicKey;
  } catch {
    return { status: "hidden" };
  }
  if (env.notification!.permission === "denied") return { status: "blocked", publicKey };
  let existing: Subscription | null;
  try {
    existing = await (await env.pushManager!()).getSubscription();
  } catch {
    return { status: "hidden" };
  }
  if (!existing || env.notification!.permission !== "granted") return { status: "off", publicKey };
  // 브라우저 구독이 살아 있으면 서버와 어긋났을 수 있으니 조용히 다시 등록합니다(서버는 같은 endpoint 를 갱신합니다).
  register(env, existing).catch(() => {});
  return { status: "on", publicKey };
}

// 스위치를 켤 때 부릅니다. 권한을 허용받은 뒤에만 구독하고 서버에 등록합니다.
export async function enablePush(env: PushEnvironment, publicKey: string): Promise<PushResult> {
  const permission = await env.notification!.requestPermission();
  if (permission === "denied") return { status: "blocked" };
  if (permission !== "granted") return { status: "off", note: "dismissed" };
  let subscription: Subscription | undefined;
  try {
    const manager = await env.pushManager!();
    subscription =
      (await manager.getSubscription()) ??
      (await manager.subscribe({ userVisibleOnly: true, applicationServerKey: decodeBase64Url(publicKey) }));
    await register(env, subscription);
    return { status: "on" };
  } catch {
    // 서버에 등록하지 못한 구독이 브라우저에 남으면 다음에 "받는 중"으로 보이므로 되돌립니다.
    await subscription?.unsubscribe().catch(() => false);
    return { status: "off", note: "enableFailed" };
  }
}

// 스위치를 끌 때 부릅니다. 서버에서 먼저 지워, 실패하면 브라우저 구독을 남겨 둔 채 다시 시도할 수 있게 합니다.
export async function disablePush(env: PushEnvironment): Promise<PushResult> {
  try {
    const subscription = await (await env.pushManager!()).getSubscription();
    if (subscription) {
      await env.request(SUBSCRIPTIONS_PATH, {
        method: "DELETE",
        body: JSON.stringify({ endpoint: subscription.endpoint }),
      });
      await subscription.unsubscribe();
    }
    return { status: "off" };
  } catch {
    return { status: "on", note: "disableFailed" };
  }
}

// 로그아웃·탈퇴 때 부릅니다. 서버 삭제가 실패해도(이미 탈퇴 등) 이 브라우저의 구독은 반드시 해제합니다.
export async function unsubscribeOnLogout(env: PushEnvironment) {
  if (!supported(env)) return;
  try {
    const subscription = await (await env.pushManager!()).getSubscription();
    if (!subscription) return;
    await env
      .request(SUBSCRIPTIONS_PATH, { method: "DELETE", body: JSON.stringify({ endpoint: subscription.endpoint }) })
      .catch(() => {});
    await subscription.unsubscribe();
  } catch (e) {
    console.error("[push] 구독 해제 실패", e);
  }
}
