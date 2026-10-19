import { test } from "node:test";
import assert from "node:assert/strict";
import { calendarDays, clockMinutes, zonedClock, zonedLabel } from "./zoned-time.ts";

test("같은 순간을 보는 사람의 시간대로 읽는다", () => {
  // 유럽에서 만든 오후 2시 비공개 일정 = UTC 12시 = 한국 밤 9시
  assert.deepEqual(zonedClock("2026-10-20T12:00:00Z", "Europe/Berlin"), { day: "2026-10-20", minutes: 14 * 60 });
  assert.deepEqual(zonedClock("2026-10-20T12:00:00Z", "Asia/Seoul"), { day: "2026-10-20", minutes: 21 * 60 });
  assert.deepEqual(zonedClock("2026-10-17T15:30:00Z", "Asia/Seoul"), { day: "2026-10-18", minutes: 30 });
});

test("시간대별 라벨", () => {
  assert.equal(
    zonedLabel("2026-10-20T12:00:00Z", { hour: "numeric", minute: "2-digit" }, "Europe/Berlin"),
    "오후 2:00",
  );
  assert.equal(zonedLabel("2026-10-20T12:00:00Z", { hour: "numeric", minute: "2-digit" }, "Asia/Seoul"), "오후 9:00");
});

test("HH:mm을 분으로 바꾼다", () => {
  assert.equal(clockMinutes("09:00"), 540);
  assert.equal(clockMinutes("22:30"), 1350);
});

test("기간의 날짜를 양 끝 포함해 나열하고 요일은 시간대와 무관", () => {
  const days = calendarDays("2026-10-30", "2026-11-02");
  assert.deepEqual(
    days.map((d) => d.day),
    ["2026-10-30", "2026-10-31", "2026-11-01", "2026-11-02"],
  );
  assert.equal(zonedLabel(days[2].noon, { weekday: "short" }, "UTC"), "일");
});
