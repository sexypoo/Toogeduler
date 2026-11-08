import { test } from "node:test";
import assert from "node:assert/strict";
import { shouldOfferMoveAnnounce } from "./event-move-announce.ts";

const ORIGINAL = { id: 7, startAt: "2026-11-05T19:00:00+09:00", endAt: "2026-11-05T21:00:00+09:00" };
const GROUP_DRAFT = { ...ORIGINAL, visibilities: ["GROUP"], groupIds: [3] };

test("새 일정에는 시간 변경 알림 줄을 보이지 않는다", () => {
  const draft = { ...GROUP_DRAFT, id: undefined, startAt: "2026-11-06T19:00:00+09:00" };
  assert.equal(shouldOfferMoveAnnounce({ ...ORIGINAL, id: undefined }, draft), false);
});

test("시간을 바꾸지 않았으면 보이지 않는다", () => {
  assert.equal(shouldOfferMoveAnnounce(ORIGINAL, GROUP_DRAFT), false);
  // 같은 순간을 UTC 로 적은 값도 바뀐 것이 아니다.
  const sameInUtc = { ...GROUP_DRAFT, startAt: "2026-11-05T10:00:00.000Z", endAt: "2026-11-05T12:00:00.000Z" };
  assert.equal(shouldOfferMoveAnnounce(ORIGINAL, sameInUtc), false);
});

test("GROUP 을 고르지 않았거나 그룹이 하나도 없으면 보이지 않는다", () => {
  const moved = { startAt: "2026-11-06T19:00:00+09:00", endAt: "2026-11-06T21:00:00+09:00" };
  assert.equal(shouldOfferMoveAnnounce(ORIGINAL, { ...GROUP_DRAFT, ...moved, visibilities: ["FRIENDS"] }), false);
  assert.equal(shouldOfferMoveAnnounce(ORIGINAL, { ...GROUP_DRAFT, ...moved, visibilities: ["PRIVATE"] }), false);
  assert.equal(shouldOfferMoveAnnounce(ORIGINAL, { ...GROUP_DRAFT, ...moved, groupIds: [] }), false);
});

test("기존 그룹 일정의 시작이나 종료를 바꾸면 보인다", () => {
  assert.equal(shouldOfferMoveAnnounce(ORIGINAL, { ...GROUP_DRAFT, startAt: "2026-11-05T19:30:00+09:00" }), true);
  assert.equal(shouldOfferMoveAnnounce(ORIGINAL, { ...GROUP_DRAFT, endAt: "2026-11-05T22:00:00+09:00" }), true);
  const withFriends = { ...GROUP_DRAFT, visibilities: ["GROUP", "FRIENDS"], startAt: "2026-11-06T19:00:00+09:00" };
  assert.equal(shouldOfferMoveAnnounce(ORIGINAL, withFriends), true);
});
