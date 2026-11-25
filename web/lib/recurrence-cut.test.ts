import { test } from "node:test";
import assert from "node:assert/strict";
import { deleteChoices, editChoices, ruleEndingBefore, splitFollowing } from "./recurrence-cut.ts";

const INSTANCE = "2026-11-18T10:00:00+09:00";

test("회차 시작 1초 전(UTC)을 UNTIL 로 붙인다", () => {
  assert.equal(ruleEndingBefore("FREQ=WEEKLY", INSTANCE), "FREQ=WEEKLY;UNTIL=20261118T005959Z");
});

test("기존 UNTIL 이 더 늦으면 새 값으로 바꾼다", () => {
  assert.equal(ruleEndingBefore("FREQ=WEEKLY;UNTIL=20261231T145959Z", INSTANCE), "FREQ=WEEKLY;UNTIL=20261118T005959Z");
});

test("기존 UNTIL 이 회차보다 이르면 바꾸지 않는다", () => {
  assert.equal(ruleEndingBefore("FREQ=WEEKLY;UNTIL=20261110T145959Z", INSTANCE), "FREQ=WEEKLY;UNTIL=20261110T145959Z");
});

test("반복 일정의 첫 회차가 아니면 이후 삭제와 전체 삭제를 고를 수 있다", () => {
  const event = { recurrenceRule: "FREQ=WEEKLY", instanceStart: INSTANCE, seriesStartAt: "2026-11-04T01:00:00Z" };
  assert.deepEqual(deleteChoices(event), ["following", "all"]);
});

test("첫 회차나 반복이 아닌 일정은 전체 삭제만 있다", () => {
  // 오프셋 표기만 다른 같은 순간도 첫 회차로 본다.
  assert.deepEqual(deleteChoices({ recurrenceRule: "FREQ=WEEKLY", instanceStart: INSTANCE, seriesStartAt: INSTANCE }), [
    "all",
  ]);
  assert.deepEqual(
    deleteChoices({ recurrenceRule: "FREQ=WEEKLY", instanceStart: INSTANCE, seriesStartAt: "2026-11-18T01:00:00Z" }),
    ["all"],
  );
  assert.deepEqual(deleteChoices({ recurrenceRule: "", instanceStart: INSTANCE, seriesStartAt: INSTANCE }), ["all"]);
  assert.deepEqual(deleteChoices({ recurrenceRule: "FREQ=WEEKLY" }), ["all"]);
});

// 11월 4일(수) 오전 10시(KST)부터 매주 1시간. 11월 18일 회차를 열어 오후 2시로 바꾼다.
function weeklyStudy(rule: string) {
  const original = {
    id: 10,
    title: "스터디",
    recurrenceRule: rule,
    startAt: "2026-11-18T01:00:00Z",
    endAt: "2026-11-18T02:00:00Z",
    instanceStart: "2026-11-18T01:00:00Z",
    seriesStartAt: "2026-11-04T01:00:00Z",
    seriesEndAt: "2026-11-04T02:00:00Z",
    groupIds: [] as number[],
  };
  const edited = { ...original, title: "스터디(변경)", startAt: "2026-11-18T05:00:00Z", endAt: "2026-11-18T06:00:00Z" };
  return { original, ...splitFollowing(original, edited) };
}

test("이후 수정: 원래 시리즈는 이 회차 직전에 끝나고 이 회차부터 고친 값으로 새 시리즈가 된다", () => {
  const { cut, next } = weeklyStudy("FREQ=WEEKLY");
  assert.equal(cut.recurrenceRule, "FREQ=WEEKLY;UNTIL=20261118T005959Z");
  assert.equal(Date.parse(cut.startAt!), Date.parse("2026-11-04T10:00:00+09:00"));
  assert.equal(cut.title, "스터디");
  assert.equal(cut.announce, false);
  assert.equal(Date.parse(next.startAt), Date.parse("2026-11-18T14:00:00+09:00"));
  assert.equal(next.title, "스터디(변경)");
  assert.equal(next.recurrenceRule, "FREQ=WEEKLY");
  assert.equal("id" in next, false);
  assert.equal("instanceStart" in next, false);
});

test("이후 수정: 원래 UNTIL 은 새 시리즈에 그대로 이어진다", () => {
  const { cut, next } = weeklyStudy("FREQ=WEEKLY;UNTIL=20261231T145959Z");
  assert.equal(cut.recurrenceRule, "FREQ=WEEKLY;UNTIL=20261118T005959Z");
  assert.equal(next.recurrenceRule, "FREQ=WEEKLY;UNTIL=20261231T145959Z");
});

test("수정 범위 선택은 삭제와 같은 규칙: 첫 회차나 반복이 아닌 일정은 모든 회차뿐이다", () => {
  const { original } = weeklyStudy("FREQ=WEEKLY");
  assert.deepEqual(editChoices(original), ["following", "all"]);
  assert.deepEqual(editChoices({ ...original, instanceStart: "2026-11-04T10:00:00+09:00" }), ["all"]);
  assert.deepEqual(editChoices({ ...original, recurrenceRule: "" }), ["all"]);
  assert.equal(editChoices, deleteChoices);
});
