"use client";
import type { ScopeChoice as Choice } from "@/lib/recurrence-cut";
import { format, parseISO } from "date-fns";
import { useEffect, useRef } from "react";

// 반복 일정(첫 회차가 아님)을 고치거나 지울 때 일정 모달의 footer 자리에 대신 보이는 범위 선택 줄입니다.
// 모달 위에 대화상자를 겹치지 않고, 모달의 포커스 가두기·Escape 를 그대로 씁니다.
export function ScopeChoice({
  mode,
  instanceStart,
  pending,
  onChoose,
  onCancel,
}: {
  mode: "edit" | "delete";
  instanceStart: string;
  pending: Choice | null;
  onChoose: (choice: Choice) => void;
  onCancel: () => void;
}) {
  const groupRef = useRef<HTMLDivElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  // 열리면 Enter 오작동을 막도록 덜 위험한 "취소"에 포커스를 둡니다.
  useEffect(() => cancelRef.current?.focus(), []);
  // 모바일 키보드가 올라와 있어도 선택 줄이 가려지지 않게 화면 안으로 올립니다.
  useEffect(() => {
    groupRef.current?.scrollIntoView({ block: "nearest" });
  }, []);
  const busy = pending !== null;
  const edit = mode === "edit";
  const following = edit ? "이 일정부터 이후 수정" : "이 일정부터 이후 모두 삭제";
  const all = edit ? "모든 회차 수정" : "모든 회차 삭제";
  return (
    <div ref={groupRef} className={`scope-choice ${mode}`} role="group" aria-labelledby="scope-title" aria-busy={busy}>
      <p id="scope-title">반복 일정이에요. 어디까지 {edit ? "바꿀까요?" : "삭제할까요?"}</p>
      <button
        type="button"
        className="scope-option"
        disabled={busy}
        aria-describedby="scope-following-hint"
        onClick={() => onChoose("following")}
      >
        <strong>{pending === "following" ? (edit ? "바꾸는 중…" : "끝내는 중…") : following}</strong>
        <span id="scope-following-hint">
          {format(parseISO(instanceStart), "M월 d일")} 회차부터{" "}
          {edit ? "바뀌어요. 지난 회차는 그대로 남아요." : "반복이 끝나요. 지난 회차는 남아요."}
        </span>
      </button>
      <button
        type="button"
        className="scope-option all"
        disabled={busy}
        aria-describedby="scope-all-hint"
        onClick={() => onChoose("all")}
      >
        <strong>{pending === "all" ? (edit ? "저장하는 중…" : "삭제하는 중…") : all}</strong>
        <span id="scope-all-hint">
          {edit ? "지난 회차까지 모두 새 내용으로 바뀌어요." : "지난 회차까지 모두 사라져요."}
        </span>
      </button>
      <button ref={cancelRef} type="button" className="ghost" disabled={busy} onClick={onCancel}>
        취소
      </button>
    </div>
  );
}
