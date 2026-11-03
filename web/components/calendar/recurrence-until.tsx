"use client";
import type { Ref } from "react";

// 반복 규칙 "FREQ=WEEKLY;UNTIL=20261220"을 반복 종류와 끝나는 날(yyyy-MM-dd)로 나누고 다시 합칩니다.
export function ruleFrequency(rule: string) {
  return rule.split(";").find((part) => part.startsWith("FREQ=")) || "";
}
export function ruleUntil(rule: string) {
  const until = /(?:^|;)UNTIL=(\d{4})(\d{2})(\d{2})(?:;|$)/.exec(rule);
  return until ? `${until[1]}-${until[2]}-${until[3]}` : "";
}
export function buildRule(frequency: string, until: string) {
  if (!frequency) return "";
  return until ? `${frequency};UNTIL=${until.replaceAll("-", "")}` : frequency;
}

// 반복을 고른 일정에서만 보이는 "끝나는 날" 입력입니다. 비워 두면 끝없이 반복합니다.
export function RecurrenceUntilField({
  until,
  startDate,
  invalid,
  readOnly,
  inputRef,
  onChange,
}: {
  until: string;
  startDate: string;
  invalid: boolean;
  readOnly: boolean;
  inputRef: Ref<HTMLInputElement>;
  onChange: (until: string) => void;
}) {
  if (readOnly && !until) return null;
  const [, month, day] = until.split("-").map(Number);
  return (
    <div className="until-field">
      <label className="field-label">
        끝나는 날
        <input
          ref={inputRef}
          type="date"
          disabled={readOnly}
          min={startDate}
          value={until}
          aria-invalid={invalid || undefined}
          aria-describedby={readOnly ? undefined : "until-hint"}
          onChange={(e) => onChange(e.target.value)}
        />
      </label>
      {until && !readOnly && (
        <button type="button" className="ghost" aria-label="끝나는 날 지우기" onClick={() => onChange("")}>
          지우기
        </button>
      )}
      {invalid ? (
        <p id="until-hint" className="form-error" role="alert">
          끝나는 날은 시작일 이후여야 해요.
        </p>
      ) : (
        !readOnly && (
          <p id="until-hint" className="reminder-hint">
            {until ? `${month}월 ${day}일까지 반복돼요.` : "비워 두면 계속 반복돼요."}
          </p>
        )
      )}
    </div>
  );
}
