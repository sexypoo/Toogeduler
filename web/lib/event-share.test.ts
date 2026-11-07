import { test } from "node:test";
import assert from "node:assert/strict";
import { confirmedShareData, groupLabel } from "./event-share.ts";
import { shareOrCopy, type InviteShareData } from "./invite-share.ts";

// 시간대를 고정해 어느 기기에서 돌려도 같은 문구가 나오게 합니다. 2025-11-05 는 수요일입니다.
const ZONE = "Asia/Seoul";
const MEETING = {
  id: 42,
  title: "정기 회의",
  startAt: "2025-11-05T10:00:00Z", // 서울 오후 7:00
  endAt: "2025-11-05T12:00:00Z", // 서울 오후 9:00
  allDay: false,
};

test("확정 안내에 그룹 이름·제목·요일 포함 날짜·시작–종료 시각·딥링크가 들어간다", () => {
  assert.deepEqual(confirmedShareData(MEETING, "동아리 운영진", ZONE), {
    title: "[동아리 운영진] 정기 회의",
    text: "[동아리 운영진] 정기 회의 · 11월 5일 (수) 오후 7:00–9:00",
    url: "https://app.toogeduler.com/?event=42",
  });
});

test("제목이 비어 있으면 그룹 일정으로 대체한다", () => {
  const data = confirmedShareData({ ...MEETING, title: "  " }, "동아리 운영진", ZONE);
  assert.equal(data.title, "[동아리 운영진] 그룹 일정");
  assert.ok(data.text.startsWith("[동아리 운영진] 그룹 일정 · "));
});

test("날짜·시각은 보는 사람 시간대로 쓴다", () => {
  // 같은 일정이 베를린(UTC+1)에서는 오전 11:00–오후 1:00 이다.
  const data = confirmedShareData(MEETING, "동아리 운영진", "Europe/Berlin");
  assert.equal(data.text, "[동아리 운영진] 정기 회의 · 11월 5일 (수) 오전 11:00–오후 1:00");
});

test("날짜를 넘기는 일정은 끝 날짜를 붙인다", () => {
  const late = { ...MEETING, startAt: "2025-11-05T14:00:00Z", endAt: "2025-11-05T16:00:00Z" };
  assert.equal(
    confirmedShareData(late, "동아리", ZONE).text,
    "[동아리] 정기 회의 · 11월 5일 (수) 오후 11:00 – 11월 6일 (목) 오전 1:00",
  );
});

// 종일 일정은 모달에서 고른 시각이 그대로 저장되므로(예: 오전 9:00–10:00), 끝은 0시 정각일 수도 아닐 수도 있다.
const ALL_DAY = { ...MEETING, allDay: true };
test("하루짜리 종일 일정은 그날 종일로 쓴다", () => {
  const kept = { ...ALL_DAY, startAt: "2025-11-05T00:00:00Z", endAt: "2025-11-05T01:00:00Z" }; // 서울 9:00–10:00
  assert.equal(confirmedShareData(kept, "동아리", ZONE).text, "[동아리] 정기 회의 · 11월 5일 (수) 종일");
  // 끝이 다음 날 0시(배타적)여도 하루짜리다.
  const exclusive = { ...ALL_DAY, startAt: "2025-11-04T15:00:00Z", endAt: "2025-11-05T15:00:00Z" }; // 서울 5일 0시–6일 0시
  assert.equal(confirmedShareData(exclusive, "동아리", ZONE).text, "[동아리] 정기 회의 · 11월 5일 (수) 종일");
});

test("여러 날짜 종일 일정은 시작일과 끝 날짜를 함께 쓴다", () => {
  const kept = { ...ALL_DAY, startAt: "2025-11-05T00:00:00Z", endAt: "2025-11-07T01:00:00Z" }; // 서울 5일 9:00–7일 10:00
  const text = "[동아리] 정기 회의 · 11월 5일 (수) – 11월 7일 (금) 종일";
  assert.equal(confirmedShareData(kept, "동아리", ZONE).text, text);
  const exclusive = { ...ALL_DAY, startAt: "2025-11-04T15:00:00Z", endAt: "2025-11-07T15:00:00Z" }; // 서울 5일 0시–8일 0시
  assert.equal(confirmedShareData(exclusive, "동아리", ZONE).text, text);
});

test("종일 일정의 날짜도 보는 사람 시간대로 쓴다", () => {
  // 서울 5일 9:00–7일 10:00 은 로스앤젤레스(UTC-8)에서 4일 오후 4:00–6일 오후 5:00 이다.
  const kept = { ...ALL_DAY, startAt: "2025-11-05T00:00:00Z", endAt: "2025-11-07T01:00:00Z" };
  assert.equal(
    confirmedShareData(kept, "동아리", "America/Los_Angeles").text,
    "[동아리] 정기 회의 · 11월 4일 (화) – 11월 6일 (목) 종일",
  );
});

test("생성 응답에 id 가 없으면 링크 없이 문구만 만든다", () => {
  const data = confirmedShareData({ ...MEETING, id: undefined }, "동아리", ZONE);
  assert.equal(data.url, undefined);
});

test("그룹이 여러 개면 첫 번째 이름 뒤에 외 N개를 붙인다", () => {
  assert.equal(groupLabel(["동아리 운영진"]), "동아리 운영진");
  assert.equal(groupLabel(["동아리 운영진", "사진 동아리"]), "동아리 운영진 외 1개");
  assert.equal(groupLabel(["a", "b", "c"]), "a 외 2개");
});

test("공유 시트가 없으면 확정 안내와 링크를 클립보드에 복사한다", async () => {
  const copied: string[] = [];
  const nav = { clipboard: { writeText: async (t: string) => void copied.push(t) } };
  // UI 는 copied 일 때 "확정 안내를 복사했어요." 토스트를 띄운다.
  assert.equal(await shareOrCopy(nav, confirmedShareData(MEETING, "동아리 운영진", ZONE)), "copied");
  assert.deepEqual(copied, [
    "[동아리 운영진] 정기 회의 · 11월 5일 (수) 오후 7:00–9:00\nhttps://app.toogeduler.com/?event=42",
  ]);
});

test("링크가 없으면 공유 시트에 url 을 넘기지 않고, 복사도 문구만 한다", async () => {
  const shared: InviteShareData[] = [];
  const data = confirmedShareData({ ...MEETING, id: undefined }, "동아리", ZONE);
  assert.equal(await shareOrCopy({ share: async (d) => void shared.push(d) }, data), "shared");
  assert.deepEqual(shared, [
    { title: "[동아리] 정기 회의", text: "[동아리] 정기 회의 · 11월 5일 (수) 오후 7:00–9:00" },
  ]);
  const copied: string[] = [];
  assert.equal(await shareOrCopy({ clipboard: { writeText: async (t) => void copied.push(t) } }, data), "copied");
  assert.deepEqual(copied, ["[동아리] 정기 회의 · 11월 5일 (수) 오후 7:00–9:00"]);
});
