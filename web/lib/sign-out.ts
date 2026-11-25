import { ApiError, api } from "@/lib/api";
import { cancelAllReminders } from "@/lib/native-reminders";
import { clearBrowserPwaCaches } from "@/lib/pwa";
import { unsubscribeBrowserPush } from "@/lib/web-push-browser";

export const SIGN_OUT_FAILED = "로그아웃하지 못했어요. 인터넷 연결을 확인하고 다시 눌러 주세요.";

// 기기를 정리한 뒤 서버에 로그인 쿠키 삭제를 요청합니다(기기 토큰 쿠키는 남음).
// 푸시 구독 삭제는 로그인 쿠키로 인증되므로 로그아웃 요청보다 먼저 끝냅니다.
// 실패하면 던집니다. 쿠키가 남은 채 로그인 화면으로 가면 곧바로 "/" 로 돌아오므로, 화면은 이동하지 말고 다시 누르게 합니다.
// 기기 정리는 여러 번 해도 안전합니다. 이미 만료(401)됐다면 로그아웃된 것과 같습니다.
export async function signOutDevice() {
  cancelAllReminders();
  clearBrowserPwaCaches();
  await unsubscribeBrowserPush();
  try {
    await api("/api/auth/logout", { method: "POST" });
  } catch (e) {
    if (!(e instanceof ApiError && e.status === 401)) throw e;
  }
}
