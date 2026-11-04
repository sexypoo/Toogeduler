import { test } from "node:test";
import assert from "node:assert/strict";
import { canEditGroup, groupPatch } from "./group-edit.ts";

const GROUP = { ownerId: 1, name: "사진 동아리", color: "#19B7B1" };

test("그룹장만 이름·색상 입력을 본다", () => {
  assert.equal(canEditGroup(GROUP, 1), true);
  assert.equal(canEditGroup(GROUP, 2), false);
});

test("바뀐 값이 없으면 저장할 수 없다", () => {
  assert.equal(groupPatch(GROUP, "사진 동아리", "#19B7B1"), null);
  assert.equal(groupPatch(GROUP, "  사진 동아리  ", "#19b7b1"), null);
});

test("공백만 있거나 40자를 넘는 이름은 저장할 수 없다", () => {
  assert.equal(groupPatch(GROUP, "   ", "#8B6FF7"), null);
  assert.equal(groupPatch(GROUP, "가".repeat(41), "#19B7B1"), null);
});

test("바뀐 값만 다듬어서 보낸다", () => {
  assert.deepEqual(groupPatch(GROUP, " 사진 동아리 2기 ", "#19B7B1"), { name: "사진 동아리 2기" });
  assert.deepEqual(groupPatch(GROUP, "사진 동아리", "#8B6FF7"), { color: "#8B6FF7" });
  assert.deepEqual(groupPatch(GROUP, "2기", "#8B6FF7"), { name: "2기", color: "#8B6FF7" });
});
