import { addDays, addMonths, addWeeks } from "date-fns";
import type { CalendarEvent } from "./types";

// 휴대폰에 예약할 일정 알림을 계산합니다. 플러그인·네트워크와 무관한 순수 함수라 `npm test` 로 검증합니다.
// (node --test 가 타입만 지우고 바로 실행하므로 이 파일은 상대 경로 런타임 import 를 쓰지 않습니다.)

export const REMINDER_HORIZON_DAYS = 14;
// iOS 는 앱 하나당 대기 중인 로컬 알림을 64개까지만 유지하므로 가까운 것부터 그만큼만 예약합니다.
export const MAX_SCHEDULED_REMINDERS = 64;

export type ReminderSource = Pick<
  CalendarEvent,
  | "id"
  | "title"
  | "location"
  | "startAt"
  | "endAt"
  | "allDay"
  | "recurrenceRule"
  | "reminderMinutes"
  | "seriesStartAt"
  | "seriesEndAt"
> & { owner: { id: number } };
export type PlannedReminder = { id: number; at: Date; title: string; body: string; link: string };

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

// 일정 화면의 알림 select 표기와 같은 문구입니다.
export function reminderLabel(minutes: number) {
  if (minutes % 1440 === 0) return minutes === 1440 ? "하루 전" : `${minutes / 1440}일 전`;
  if (minutes % 60 === 0) return `${minutes / 60}시간 전`;
  return `${minutes}분 전`;
}

// 기기 로컬 시간대 기준 "오후 3:00" 형식입니다.
export function koreanTime(date: Date) {
  const h = date.getHours(),
    m = date.getMinutes();
  return `${h < 12 ? "오전" : "오후"} ${h % 12 === 0 ? 12 : h % 12}:${String(m).padStart(2, "0")}`;
}
function koreanDate(date: Date) {
  return `${date.getMonth() + 1}월 ${date.getDate()}일(${WEEKDAYS[date.getDay()]})`;
}
function sameDay(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

// 본문: "10분 전 · 오후 3:00 · 장소". 알림이 일정과 다른 날 울리거나 종일 일정이면 날짜를 붙입니다.
export function reminderBody(start: Date, minutes: number, allDay: boolean, location?: string) {
  const remindAt = new Date(start.getTime() - minutes * 60000);
  const when = allDay
    ? `${koreanDate(start)} 종일`
    : sameDay(remindAt, start)
      ? koreanTime(start)
      : `${koreanDate(start)} ${koreanTime(start)}`;
  const place = location?.trim();
  return [reminderLabel(minutes), when, place].filter(Boolean).join(" · ");
}

// 같은 회차는 늘 같은 id 가 되도록 (일정 id, 회차 시작)으로 31비트 해시를 만듭니다. (Android 는 int id 만 받습니다.)
function reminderId(eventId: number, start: Date) {
  const key = `${eventId}:${start.getTime()}`;
  let hash = 0x811c9dc5;
  for (let i = 0; i < key.length; i++) {
    hash ^= key.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash & 0x7fffffff || 1;
}

// 서버(EventController.occurrenceRanges)와 같은 규칙으로 회차 시작 시각을 펼칩니다. 매월은 앞 회차에서 한 달씩 이어 갑니다.
function occurrenceStarts(seriesStart: Date, rule: string, from: Date, to: Date) {
  if (!rule) return seriesStart > from && seriesStart <= to ? [seriesStart] : [];
  const out: Date[] = [];
  let start = seriesStart;
  if (rule.includes("FREQ=DAILY") || !rule.includes("FREQ=MONTHLY")) {
    // 매일·매주는 오래전에 시작한 시리즈도 바로 창 근처로 건너뜁니다.
    const stepDays = rule.includes("FREQ=DAILY") ? 1 : 7;
    const skipped = Math.max(0, Math.floor((from.getTime() - seriesStart.getTime()) / (stepDays * 86400000)) - 1);
    start = stepDays === 1 ? addDays(seriesStart, skipped) : addWeeks(seriesStart, skipped);
    for (let guard = 0; start <= to && guard < 1000; guard++) {
      if (start > from) out.push(start);
      start = stepDays === 1 ? addDays(start, 1) : addWeeks(start, 1);
    }
    return out;
  }
  for (let guard = 0; start <= to && guard < 5000; guard++) {
    if (start > from) out.push(start);
    start = addMonths(start, 1);
  }
  return out;
}

/**
 * 앞으로 horizonDays 일 안에 시작하는 "내" 일정 회차 중 알림 시점이 아직 지나지 않은 것을 예약 목록으로 만듭니다.
 * 비공개 일정도 본인 기기이므로 실제 제목을 씁니다. 남의 일정(그룹 캘린더 등)은 넣지 않습니다.
 * 서버가 이미 회차로 펼친 목록을 받아도 같은 일정은 시리즈 기준으로 한 번만 펼칩니다.
 */
export function planReminders(
  events: ReminderSource[],
  meId: number,
  now: Date,
  horizonDays = REMINDER_HORIZON_DAYS,
): PlannedReminder[] {
  const until = new Date(now.getTime() + horizonDays * 86400000);
  const seen = new Set<number>();
  const usedIds = new Set<number>();
  const out: PlannedReminder[] = [];
  for (const event of events) {
    const minutes = event.reminderMinutes || 0;
    if (event.owner.id !== meId || minutes <= 0 || seen.has(event.id)) continue;
    seen.add(event.id);
    const seriesStart = new Date(event.seriesStartAt || event.startAt);
    for (const start of occurrenceStarts(seriesStart, event.recurrenceRule || "", now, until)) {
      const at = new Date(start.getTime() - minutes * 60000);
      if (at <= now) continue;
      let id = reminderId(event.id, start);
      while (usedIds.has(id)) id = (id % 0x7fffffff) + 1;
      usedIds.add(id);
      out.push({
        id,
        at,
        title: event.title,
        body: reminderBody(start, minutes, event.allDay, event.location),
        link: `/?event=${event.id}&at=${start.toISOString()}`,
      });
    }
  }
  return out.sort((a, b) => a.at.getTime() - b.at.getTime()).slice(0, MAX_SCHEDULED_REMINDERS);
}
