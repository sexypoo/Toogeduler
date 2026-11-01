"use client";
import { useEffect, useId, useRef, useState } from "react";
import { browserPushEnvironment } from "@/lib/web-push-browser";
import { disablePush, enablePush, pushSupport, readPushState, type PushNote, type PushStatus } from "@/lib/web-push";

const HINT: Record<"on" | "off" | "blocked", string> = {
  off: "그룹 일정이 잡히면 잠금 화면에서 알려드려요",
  on: "이 기기로 알림을 받고 있어요",
  blocked: "브라우저에서 알림이 차단돼 있어요. 주소창 왼쪽 자물쇠에서 사이트 설정의 알림을 허용해 주세요",
};
const NOTE: Record<PushNote, string> = {
  dismissed: "알림 허용을 선택하지 않았어요. 다시 눌러 주세요",
  enableFailed: "알림을 켜지 못했어요. 잠시 후 다시 시도해 주세요",
  disableFailed: "해제하지 못했어요. 다시 시도해 주세요",
};

// 알림 센터 하단의 "이 기기에서 알림 받기" 행입니다. 권한 팝업은 스위치를 눌렀을 때만 띄웁니다.
export function PushToggle() {
  const titleId = useId();
  const hintId = useId();
  // 패널은 버튼을 눌러야 열리므로 첫 렌더부터 브라우저에서 돕니다.
  const [status, setStatus] = useState<PushStatus | "checking">(() => {
    const support = pushSupport(browserPushEnvironment());
    return support === "supported" ? "checking" : support;
  });
  const [busy, setBusy] = useState<"on" | "off" | null>(null);
  const [note, setNote] = useState<PushNote>();
  const publicKey = useRef<string | undefined>(undefined);

  // 패널을 열 때마다 다시 읽어, 브라우저 설정에서 권한을 바꾼 경우도 반영합니다.
  useEffect(() => {
    let alive = true;
    readPushState(browserPushEnvironment()).then((state) => {
      if (!alive) return;
      publicKey.current = state.publicKey;
      setStatus(state.status);
    });
    return () => {
      alive = false;
    };
  }, []);

  if (status === "hidden") return null;
  if (status === "install")
    return (
      <footer className="notification-push">
        <span>
          <small>홈 화면에 추가하면 알림을 받을 수 있어요</small>
        </span>
      </footer>
    );

  const on = status === "on";
  async function toggle() {
    if (busy || status === "checking" || status === "blocked" || !publicKey.current) return;
    setBusy(on ? "off" : "on");
    setNote(undefined);
    const env = browserPushEnvironment();
    const result = on ? await disablePush(env) : await enablePush(env, publicKey.current);
    setStatus(result.status);
    setNote(result.note);
    setBusy(null);
  }

  const hint =
    status === "checking" ? "" : busy ? (busy === "on" ? "켜는 중…" : "끄는 중…") : note ? NOTE[note] : HINT[status];
  return (
    <footer className="notification-push">
      <span>
        <b id={titleId}>이 기기에서 알림 받기</b>
        <small id={hintId} className={status === "blocked" ? "blocked" : undefined} aria-live="polite">
          {hint}
        </small>
      </span>
      <button
        type="button"
        role="switch"
        aria-checked={on}
        aria-labelledby={titleId}
        aria-describedby={hintId}
        aria-busy={status === "checking" || !!busy}
        aria-disabled={status === "checking" || status === "blocked" || !!busy}
        onClick={toggle}
      >
        <i />
      </button>
    </footer>
  );
}
