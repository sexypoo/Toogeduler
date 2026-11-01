import { Capacitor } from "@capacitor/core";
import { api, token } from "@/lib/api";
import { settleWithin, unsubscribeOnLogout, type PushEnvironment } from "@/lib/web-push";

// 서비스 워커가 활성화되기를 기다리는 최대 시간입니다. 넘기면 푸시 행을 숨깁니다.
const READY_TIMEOUT_MS = 5000;

// web-push.ts 의 흐름을 실제 브라우저 전역에 연결합니다.
// 서비스 워커는 운영 빌드에서만 등록되므로(pwa-register.tsx) 개발 서버에서는 푸시 행을 숨깁니다.
export function browserPushEnvironment(request: PushEnvironment["request"] = api): PushEnvironment {
  const nav = navigator as Navigator & { standalone?: boolean };
  const ios = /iPad|iPhone|iPod/.test(nav.userAgent) || (nav.platform === "MacIntel" && nav.maxTouchPoints > 1);
  const standalone = nav.standalone === true || window.matchMedia("(display-mode: standalone)").matches;
  const canPush = process.env.NODE_ENV === "production" && "serviceWorker" in nav && "PushManager" in window;
  return {
    native: Capacitor.isNativePlatform(),
    pushManager: canPush ? () => readyPushManager(nav.serviceWorker) : undefined,
    notification: "Notification" in window ? Notification : undefined,
    iosBrowserTab: ios && !standalone,
    request,
  };
}

// 등록이 없으면(등록 거부·설치 실패) ready 를 기다리지 않고 바로 실패시키고, 등록이 있어도 활성화가 늦으면 시간 제한으로 끝냅니다.
async function readyPushManager(container: ServiceWorkerContainer) {
  if (!(await container.getRegistration())) throw new Error("서비스 워커 등록 없음");
  return (await settleWithin(container.ready, READY_TIMEOUT_MS)).pushManager;
}

// 로그아웃 직전에 부릅니다. 토큰을 지운 뒤에도 서버 삭제 요청이 이 계정으로 가도록 지금 토큰을 붙잡아 둡니다.
export function unsubscribeBrowserPush() {
  if (typeof window === "undefined") return Promise.resolve();
  const authToken = token();
  const request: PushEnvironment["request"] = (path, init) =>
    api(path, { ...init, headers: authToken ? { Authorization: `Bearer ${authToken}` } : undefined });
  return unsubscribeOnLogout(browserPushEnvironment(request));
}
