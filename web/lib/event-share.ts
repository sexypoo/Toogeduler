// 그룹 일정을 확정하면 단톡방에 알릴 문구를 만듭니다. 날짜·시각은 보는 사람 기기 시간대(zonedLabel)로 씁니다.
// 화면과 떼어 둔 순수 함수라 `npm test` 로 검증합니다.
import type { InviteShareData } from "./invite-share.ts";
import type { CalendarEvent } from "./types.ts";
import { zonedClock, zonedLabel } from "./zoned-time.ts";

export type ConfirmedEvent = Pick<CalendarEvent, "title" | "startAt" | "endAt" | "allDay"> & { id?: number };

/** 문구에 쓸 그룹 이름. 여러 그룹이면 첫 번째 이름 뒤에 "외 N개"를 붙인다. */
export function groupLabel(names: string[]) {
  return names.length > 1 ? `${names[0]} 외 ${names.length - 1}개` : (names[0] ?? "");
}

/** 예) "11월 5일 (수) 오후 7:00–9:00". 오전/오후가 같으면 끝 시각에서 뺀다. 날짜가 넘어가면 끝 날짜를 붙인다. */
export function confirmedWhen(event: ConfirmedEvent, zone: string) {
  const date = (iso: string) => zonedLabel(iso, { month: "long", day: "numeric", weekday: "short" }, zone);
  if (event.allDay) return `${date(event.startAt)} 종일`;
  const time = (iso: string) => zonedLabel(iso, { hour: "numeric", minute: "2-digit" }, zone);
  const start = time(event.startAt);
  const end = time(event.endAt);
  if (zonedClock(event.startAt, zone).day !== zonedClock(event.endAt, zone).day)
    return `${date(event.startAt)} ${start} – ${date(event.endAt)} ${end}`;
  const period = (label: string) => label.split(/\s/)[0];
  const sameHalf = period(start) === period(end);
  return `${date(event.startAt)} ${start}–${sameHalf ? end.slice(period(end).length).trim() : end}`;
}

/** 공유 시트·클립보드에 넘길 확정 안내. 단톡방은 text 만 보여 주는 경우가 많아 text 에 그룹·제목·일시를 모두 담는다. id 가 없으면 링크 없이 문구만 쓴다. */
export function confirmedShareData(
  event: ConfirmedEvent,
  groupName: string,
  zone: string,
  origin = "https://app.toogeduler.com",
): InviteShareData {
  const title = `[${groupName}] ${event.title.trim() || "그룹 일정"}`;
  const text = `${title} · ${confirmedWhen(event, zone)}`;
  return event.id ? { title, text, url: `${origin}/?event=${event.id}` } : { title, text };
}
