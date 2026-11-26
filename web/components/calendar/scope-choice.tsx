"use client";
import type { ScopeChoice as Choice } from "@/lib/recurrence-cut";
import { format, parseISO } from "date-fns";
import { useEffect, useRef } from "react";

// 반복 일정을 고치거나 지울 때 일정 모달의 footer 자리에 대신 보이는 범위 선택 줄입니다.
// 모달 위에 대화상자를 겹치지 않고, 모달의 포커스 가두기·Escape 를 그대로 씁니다.
// confirmLast 면 같은 자리를 "마지막 남은 회차" 확인으로 바꿉니다("일정째 삭제"는 "all"로 고릅니다).
export function ScopeChoice({
  mode,
  instanceStart,
  choices,
  pending,
  confirmLast = false,
  quietGroup = false,
  onChoose,
  onCancel,
  onBack,
}: {
  mode: "edit" | "delete";
  instanceStart: string;
  choices: Choice[];
  pending: Choice | null;
  confirmLast?: boolean;
  quietGroup?: boolean;
  onChoose: (choice: Choice) => void;
  onCancel: () => void;
  onBack?: () => void;
}) {
  const groupRef = useRef<HTMLDivElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const thisRef = useRef<HTMLButtonElement>(null);
  const backRef = useRef<HTMLButtonElement>(null);
  // 열리면 Enter 오작동을 막도록 덜 위험한 "취소"(확인 상태면 "돌아가기")에, 확인에서 돌아오면 "이 일정만 삭제"에 포커스를 둡니다.
  const shown = useRef<boolean | null>(null);
  useEffect(() => {
    if (confirmLast) backRef.current?.focus();
    else (shown.current ? thisRef : cancelRef).current?.focus();
    shown.current = confirmLast;
    // 모바일 키보드가 올라와 있어도 선택 줄이 가려지지 않게 화면 안으로 올립니다.
    groupRef.current?.scrollIntoView({ block: "nearest" });
  }, [confirmLast]);
  const busy = pending !== null;
  const edit = mode === "edit";
  const following = edit ? "이 일정부터 이후 수정" : "이 일정부터 이후 모두 삭제";
  const all = edit ? "모든 회차 수정" : "모든 회차 삭제";
  if (confirmLast)
    return (
      <div
        ref={groupRef}
        className="scope-choice delete"
        role="group"
        aria-labelledby="scope-last-title"
        aria-busy={busy}
      >
        <p id="scope-last-title">마지막 남은 회차예요. 일정을 통째로 삭제할까요?</p>
        <span id="scope-last-hint">이 회차를 빼면 남는 회차가 없어요. 일정이 캘린더에서 사라져요.</span>
        <button
          type="button"
          className="scope-option all"
          disabled={busy}
          aria-describedby="scope-last-hint"
          onClick={() => onChoose("all")}
        >
          <strong>{pending === "all" ? "삭제하는 중…" : "일정째 삭제"}</strong>
        </button>
        <button ref={backRef} type="button" className="ghost" disabled={busy} onClick={onBack}>
          돌아가기
        </button>
      </div>
    );
  return (
    <div ref={groupRef} className={`scope-choice ${mode}`} role="group" aria-labelledby="scope-title" aria-busy={busy}>
      <p id="scope-title">반복 일정이에요. 어디까지 {edit ? "바꿀까요?" : "삭제할까요?"}</p>
      {choices.includes("this") && (
        <button
          ref={thisRef}
          type="button"
          className="scope-option"
          disabled={busy}
          aria-describedby="scope-this-hint"
          onClick={() => onChoose("this")}
        >
          <strong>{pending === "this" ? "빼는 중…" : "이 일정만 삭제"}</strong>
          <span id="scope-this-hint">
            {format(parseISO(instanceStart), "M월 d일")} 회차만 빠져요. 나머지 회차는 그대로예요.
            {quietGroup && " 그룹 멤버에게는 알리지 않아요."}
          </span>
        </button>
      )}
      {choices.includes("following") && (
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
      )}
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
