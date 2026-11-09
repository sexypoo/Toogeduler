import { test } from "node:test";
import assert from "node:assert/strict";
import { deleteChoices, ruleEndingBefore } from "./recurrence-cut.ts";

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
