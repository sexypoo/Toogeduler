import { test } from "node:test";
import assert from "node:assert/strict";
import { deleteEventUrl, shouldOfferCancelAnnounce } from "./event-cancel-announce.ts";

const ME = 1;
const MY_GROUP_EVENT = { id: 7, visibilities: ["GROUP"], owner: { id: ME } };

test("새 일정에는 취소 알림 줄을 보이지 않는다", () => {
  assert.equal(shouldOfferCancelAnnounce({ ...MY_GROUP_EVENT, id: undefined }, ME), false);
});

test("GROUP 공개가 아닌 일정에는 보이지 않는다", () => {
  assert.equal(shouldOfferCancelAnnounce({ ...MY_GROUP_EVENT, visibilities: ["PRIVATE"] }, ME), false);
  assert.equal(shouldOfferCancelAnnounce({ ...MY_GROUP_EVENT, visibilities: ["FRIENDS", "PUBLIC"] }, ME), false);
  assert.equal(shouldOfferCancelAnnounce({ id: 7, visibilities: [], visibility: "FRIENDS" }, ME), false);
});

test("남의 일정에는 보이지 않는다", () => {
  assert.equal(shouldOfferCancelAnnounce({ ...MY_GROUP_EVENT, owner: { id: 2 } }, ME), false);
});

test("내 기존 그룹 일정에는 보인다", () => {
  assert.equal(shouldOfferCancelAnnounce(MY_GROUP_EVENT, ME), true);
  assert.equal(shouldOfferCancelAnnounce({ ...MY_GROUP_EVENT, visibilities: ["GROUP", "FRIENDS"] }, ME), true);
  // 예전 단일 공개 범위만 있는 일정도 GROUP 이면 보인다.
  assert.equal(shouldOfferCancelAnnounce({ id: 7, visibility: "GROUP", owner: { id: ME } }, ME), true);
});

test("체크하면 announce=true 를 붙이고, 아니면 쿼리 없이 지운다", () => {
  assert.equal(deleteEventUrl(7, true), "/api/events/7?announce=true");
  assert.equal(deleteEventUrl(7, false), "/api/events/7");
});
