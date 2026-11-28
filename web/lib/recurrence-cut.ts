// 반복 일정을 "이 회차만 빼거나", "이 회차부터 그만"하거나 "이 회차부터 바꿀" 때 쓰는 순수 로직. 화면 없이 테스트할 수 있게 컴포넌트와 나눠 둡니다.
// 엔티티를 바꾸지 않고, 시리즈 규칙의 UNTIL 을 이 회차 시작 1초 전으로 당기거나 EXDATE 에 이 회차 시작 시각을 더합니다.

type Occurrence = { recurrenceRule?: string; instanceStart?: string; seriesStartAt?: string };
type SeriesFields = Occurrence & {
  id?: number;
  startAt?: string;
  endAt?: string;
  seriesEndAt?: string;
  announce?: boolean;
};
export type ScopeChoice = "this" | "following" | "all";
export type DeleteChoice = ScopeChoice;

// 서버(EventController)와 같은 RFC 5545 UTC 형식: 20261118T010000Z
const utcStamp = (at: Date) => at.toISOString().replace(/[-:]|\.\d+/g, "");

/**
 * 규칙의 FREQ 등은 그대로 두고 UNTIL 만 instanceStart 1초 전(UTC, yyyyMMddTHHmmssZ)으로 바꾸거나 붙입니다.
 * 기존 UNTIL 이 그보다 이르면 이미 이 회차 전에 끝나므로 규칙을 바꾸지 않습니다.
 */
export function ruleEndingBefore(rule: string, instanceStart: string) {
  const cut = new Date(Date.parse(instanceStart) - 1000);
  const existing = untilOf(rule);
  if (existing && existing <= cut) return rule;
  const until = `UNTIL=${utcStamp(cut)}`;
  const parts = rule.split(";").filter(Boolean);
  const at = parts.findIndex((part) => part.startsWith("UNTIL="));
  // 서버 형식은 FREQ;UNTIL;EXDATE 순서라 UNTIL 은 FREQ 바로 뒤에 넣습니다.
  if (at >= 0) parts[at] = until;
  else parts.splice(1, 0, until);
  return parts.join(";");
}

/** "이 일정만 삭제": 이 회차 시작 시각(UTC)을 EXDATE 에 더합니다. 이미 있으면 규칙을 바꾸지 않고, FREQ·UNTIL 은 그대로 둡니다. */
export function ruleExcluding(rule: string, instanceStart: string) {
  const stamp = utcStamp(new Date(instanceStart));
  const parts = rule.split(";").filter(Boolean);
  const at = parts.findIndex((part) => part.startsWith("EXDATE="));
  if (at < 0) return [...parts, `EXDATE=${stamp}`].join(";");
  const dates = parts[at].slice(7).split(",").filter(Boolean);
  if (dates.includes(stamp)) return rule;
  parts[at] = `EXDATE=${[...dates, stamp].join(",")}`;
  return parts.join(";");
}

// 이 회차를 빼면 남는 회차가 없는지 서버(occurrenceRanges)처럼 UNTIL 까지 세어 봅니다. 끝없는 반복·판정 불가는 false(그냥 뺌).
export function isLastOccurrence(event: Occurrence) {
  const { recurrenceRule: rule, instanceStart, seriesStartAt } = event;
  const until = rule ? untilOf(rule) : null;
  // 매월은 말일·시간대에 따라 서버와 어긋날 수 있어 판정하지 않습니다.
  if (!rule || !until || !instanceStart || !seriesStartAt || rule.includes("FREQ=MONTHLY")) return false;
  const exdate = rule.split(";").find((part) => part.startsWith("EXDATE="));
  const skip = new Set([...(exdate?.slice(7).split(",") || []), utcStamp(new Date(instanceStart))]);
  // 서버는 시리즈 시작의 오프셋 기준으로 더하지만, 일·주는 고정 길이라 UTC 로 더해도 같습니다.
  const step = (rule.includes("FREQ=DAILY") ? 1 : 7) * 86400000;
  for (let at = Date.parse(seriesStartAt), n = 0; at <= until.getTime(); at += step, n++)
    if (n > 5000 || !skip.has(utcStamp(new Date(at)))) return false;
  return true;
}

/** 반복 일정의 첫 회차가 아닌 회차에서만 "이 일정부터 이후"(삭제·수정)를 고를 수 있습니다. */
export function scopeChoices(event: Occurrence): ScopeChoice[] {
  if (!event.recurrenceRule || !event.instanceStart || !event.seriesStartAt) return ["all"];
  return Date.parse(event.instanceStart) > Date.parse(event.seriesStartAt) ? ["following", "all"] : ["all"];
}
/** 삭제는 반복 일정의 어느 회차든(첫 회차 포함) "이 일정만"을 맨 위에 둡니다. 수정에는 "이 일정만"이 없습니다. */
export function deleteChoices(event: Occurrence): ScopeChoice[] {
  const choices = scopeChoices(event);
  return event.recurrenceRule && event.instanceStart && event.seriesStartAt ? ["this", ...choices] : choices;
}
export const editChoices = scopeChoices;

/**
 * "이 일정부터 이후 수정"의 새 시리즈 규칙: 이 회차 이전의 EXDATE 는 버리고, 이후 것은 새 시작과의 시간 차이만큼 옮깁니다.
 * 반복 종류(FREQ)를 바꿨으면 맞는 회차가 없어지므로 EXDATE 를 모두 버립니다.
 * ("모든 회차 수정"·드래그 이동은 서버 EventController.shiftExdates 가 옮깁니다.)
 */
export function ruleFollowing(rule: string, originalRule: string, instanceStart: string, startAt: string) {
  const parts = rule.split(";").filter(Boolean);
  const at = parts.findIndex((part) => part.startsWith("EXDATE="));
  if (at < 0) return rule;
  const from = Date.parse(instanceStart);
  const delta = Date.parse(startAt) - from;
  const freq = (value: string) => value.split(";").find((part) => part.startsWith("FREQ=")) || "";
  const kept =
    freq(rule) === freq(originalRule)
      ? parts[at]
          .slice(7)
          .split(",")
          .map(stampTime)
          .filter((time): time is number => time !== null && time >= from)
      : [];
  if (!kept.length) parts.splice(at, 1);
  else parts[at] = `EXDATE=${kept.map((time) => utcStamp(new Date(time + delta))).join(",")}`;
  return parts.join(";");
}

/**
 * "이 일정부터 이후 수정": 원래 시리즈(cut)는 시작·끝·내용을 그대로 두고 이 회차 직전에서 끝내고,
 * 이 회차부터는 사용자가 고친 값으로 새 시리즈(next, id 없음)를 만듭니다.
 * next 의 규칙은 사용자가 고른 규칙이라, 원래 UNTIL 을 바꾸지 않았다면 그대로 이어집니다. EXDATE 는 ruleFollowing 으로 맞춥니다.
 */
export function splitFollowing<T extends SeriesFields>(original: T, edited: T) {
  const cut: T & { announce: boolean } = {
    ...original,
    startAt: original.seriesStartAt,
    endAt: original.seriesEndAt,
    recurrenceRule: ruleEndingBefore(original.recurrenceRule!, original.instanceStart!),
    announce: false,
  };
  const { id: _id, instanceStart: _at, seriesStartAt: _start, seriesEndAt: _end, ...rest } = edited;
  const rule = edited.recurrenceRule || "";
  const next = {
    ...rest,
    recurrenceRule: ruleFollowing(rule, original.recurrenceRule!, original.instanceStart!, edited.startAt!),
  };
  return { cut, next };
}

function untilOf(rule: string) {
  const until = /(?:^|;)UNTIL=(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})Z(?:;|$)/.exec(rule);
  if (!until) return null;
  const [year, month, day, hour, minute, second] = until.slice(1).map(Number);
  return new Date(Date.UTC(year, month - 1, day, hour, minute, second));
}

// "20261111T010000Z" 를 밀리초로. 형식이 다르면 null.
function stampTime(stamp: string) {
  const at = /^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})Z$/.exec(stamp);
  if (!at) return null;
  const [year, month, day, hour, minute, second] = at.slice(1).map(Number);
  return Date.UTC(year, month - 1, day, hour, minute, second);
}
