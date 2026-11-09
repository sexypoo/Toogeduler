// 반복 일정을 "이 회차부터 그만"할 때 쓰는 순수 로직. 화면 없이 테스트할 수 있게 컴포넌트와 나눠 둡니다.
// 엔티티를 바꾸지 않고, 시리즈 규칙의 UNTIL 을 이 회차 시작 1초 전으로 당겨 이후 회차를 없앱니다.

type Occurrence = { recurrenceRule?: string; instanceStart?: string; seriesStartAt?: string };
export type DeleteChoice = "following" | "all";

/**
 * 규칙의 FREQ 등은 그대로 두고 UNTIL 만 instanceStart 1초 전(UTC, yyyyMMddTHHmmssZ)으로 바꾸거나 붙입니다.
 * 기존 UNTIL 이 그보다 이르면 이미 이 회차 전에 끝나므로 규칙을 바꾸지 않습니다.
 */
export function ruleEndingBefore(rule: string, instanceStart: string) {
  const cut = new Date(Date.parse(instanceStart) - 1000);
  const existing = untilOf(rule);
  if (existing && existing <= cut) return rule;
  const until = `UNTIL=${cut.toISOString().replace(/[-:]|\.\d+/g, "")}`;
  const parts = rule.split(";").filter(Boolean);
  const at = parts.findIndex((part) => part.startsWith("UNTIL="));
  if (at >= 0) parts[at] = until;
  else parts.push(until);
  return parts.join(";");
}

/** 반복 일정의 첫 회차가 아닌 회차에서만 "이 일정부터 이후 모두 삭제"를 고를 수 있습니다. */
export function deleteChoices(event: Occurrence): DeleteChoice[] {
  if (!event.recurrenceRule || !event.instanceStart || !event.seriesStartAt) return ["all"];
  return Date.parse(event.instanceStart) > Date.parse(event.seriesStartAt) ? ["following", "all"] : ["all"];
}

function untilOf(rule: string) {
  const until = /(?:^|;)UNTIL=(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})Z(?:;|$)/.exec(rule);
  if (!until) return null;
  const [year, month, day, hour, minute, second] = until.slice(1).map(Number);
  return new Date(Date.UTC(year, month - 1, day, hour, minute, second));
}
