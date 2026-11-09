import { test } from "node:test";
import assert from "node:assert/strict";
import { emptyMembersNotice } from "./empty-members.ts";

const MEMBERS = [
  { id: 1, name: "나" },
  { id: 2, name: "민수" },
  { id: 3, name: "지아" },
  { id: 4, name: "소라" },
  { id: 5, name: "하준" },
];

test("일정 없는 멤버가 없으면 안내하지 않는다", () => {
  assert.equal(emptyMembersNotice([], MEMBERS, 1), null);
});

test("본인을 빼고 두 명이면 두 이름을 모두 알린다", () => {
  assert.equal(
    emptyMembersNotice([1, 2, 3], MEMBERS, 1),
    "민수님, 지아님은 이 기간에 일정이 하나도 없어요. 아직 캘린더를 안 채웠을 수 있어요.",
  );
});

test("한 명이면 그 이름만 알린다", () => {
  assert.equal(
    emptyMembersNotice([3], MEMBERS, 1),
    "지아님은 이 기간에 일정이 하나도 없어요. 아직 캘린더를 안 채웠을 수 있어요.",
  );
});

test("세 명 이상이면 앞 한 명 이름과 나머지 수를 알린다", () => {
  const notice = emptyMembersNotice([2, 3, 4, 5], MEMBERS, 1);
  assert.ok(notice?.startsWith("민수님 외 3명은"));
  assert.ok(notice?.includes("외 3명"));
});

test("본인만 해당하면 내 일정 문구를 보여준다", () => {
  assert.equal(emptyMembersNotice([1], MEMBERS, 1), "이 기간에 내 일정이 하나도 없어요. 일정을 넣으면 더 정확해져요.");
});

test("그룹에 없는 id 는 무시한다", () => {
  assert.equal(emptyMembersNotice([99], MEMBERS, 1), null);
});
