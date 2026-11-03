"use client";
import { untilLimit } from "@/lib/local-reminders";
import { format } from "date-fns";
import type { Ref } from "react";

// 반복 규칙 "FREQ=WEEKLY;UNTIL=20261220T145959Z"를 반복 종류와 끝나는 날(이 기기 로컬 날짜, yyyy-MM-dd)로 나누고 다시 합칩니다.
// 끝나는 날은 이 기기 시간대의 그날 23:59:59 를 UTC 로 바꿔 저장해, 서버·다른 기기에서도 같은 순간까지 반복합니다.
export function ruleFrequency(rule: string) {
  return rule.split(";").find((part) => part.startsWith("FREQ=")) || "";
}
export function ruleUntil(rule: string) {
  const until = untilLimit(rule);
  return until ? format(until, "yyyy-MM-dd") : "";
}
export function buildRule(frequency: string, until: string) {
  if (!frequency || !until) return frequency;
  return `${frequency};UNTIL=${new Date(`${until}T23:59:59`).toISOString().replace(/[-:]|\.\d+/g, "")}`;
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
