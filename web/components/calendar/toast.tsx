"use client";
import { BellOff, Check, CircleAlert } from "lucide-react";
import { useRef, useState } from "react";

/** 토스트 한 줄 안에 붙는 버튼. run 이 끝나면 버튼을 다시 누를 수 있다. */
export type ToastAction = { label: string; ariaLabel: string; run: () => Promise<void> };

// 화면 하단 토스트. alert 는 실패 안내라 role="alert" 로 바로 읽힙니다. 액션이 붙으면 버튼을 누를 시간이 필요하므로, 마우스를 올리거나 포커스가 있는 동안 hold(true)로 타이머를 멈춥니다.
export function Toast({
  message,
  notice,
  alert = false,
  action,
  hold,
  close,
}: {
  message: string;
  notice: boolean;
  alert?: boolean;
  action: ToastAction | null;
  hold: (paused: boolean) => void;
  close: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const hovered = useRef(false);
  const focused = useRef(false);
  const sharing = useRef(false);
  function track(kind: "hover" | "focus", on: boolean) {
    if (!action) return;
    (kind === "hover" ? hovered : focused).current = on;
    hold(hovered.current || focused.current || sharing.current);
  }
  async function act() {
    if (!action) return;
    setBusy(true);
    sharing.current = true;
    hold(true);
    try {
      await action.run();
    } finally {
      sharing.current = false;
      setBusy(false);
    }
  }
  return (
    <div
      className={`toast ${notice ? "notice" : ""} ${action ? "has-action" : ""}`}
      role={alert ? "alert" : "status"}
      aria-live={alert ? "assertive" : "polite"}
      onMouseEnter={() => track("hover", true)}
      onMouseLeave={() => track("hover", false)}
      onFocus={() => track("focus", true)}
      onBlur={() => track("focus", false)}
      onKeyDown={(e) => {
        if (e.key === "Escape") close();
      }}
    >
      {notice ? <BellOff size={16} /> : alert ? <CircleAlert size={16} /> : <Check size={16} />}
      {message}
      {action && (
        <button
          type="button"
          className="toast-action"
          aria-label={action.ariaLabel}
          aria-busy={busy}
          disabled={busy}
          onClick={act}
        >
          {action.label}
        </button>
      )}
    </div>
  );
}
