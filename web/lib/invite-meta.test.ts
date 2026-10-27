import { test } from "node:test";
import assert from "node:assert/strict";
import { fetchInvitePreview, INVITE_FALLBACK_TITLE, inviteMetadata } from "./invite-meta.ts";

const ORIGIN = "http://api.test";

function respond(status: number, body: unknown): typeof fetch {
  return (async () => new Response(JSON.stringify(body), { status })) as typeof fetch;
}

test("정상 응답이면 그룹 이름과 그룹장 외 인원을 보여 준다", () => {
  const meta = inviteMetadata({ name: "사진 동아리", memberCount: 5, ownerName: "고은" });
  assert.equal(meta.title, '"사진 동아리" 그룹에 초대받았어요');
  assert.equal(meta.description, "고은님 외 4명이 함께 되는 시간을 맞추고 있어요. Toogeduler");
  assert.deepEqual(meta.openGraph, {
    title: '"사진 동아리" 그룹에 초대받았어요',
    description: "고은님 외 4명이 함께 되는 시간을 맞추고 있어요. Toogeduler",
    images: ["/icons/icon-512.png"],
    type: "website",
  });
  assert.equal((meta.twitter as { card: string }).card, "summary");
});

test("그룹장 혼자면 '외 0명' 없이 그룹장 이름만 쓴다", () => {
  const meta = inviteMetadata({ name: "스터디", memberCount: 1, ownerName: "고은" });
  assert.equal(meta.description, "고은님이 함께 되는 시간을 맞추고 있어요. Toogeduler");
});

test("미리보기가 없으면 대체 문구를 쓴다", () => {
  const meta = inviteMetadata(null);
  assert.equal(meta.title, INVITE_FALLBACK_TITLE);
  assert.equal((meta.openGraph as { title: string }).title, INVITE_FALLBACK_TITLE);
});

test("200 응답이면 공개 필드만 꺼낸다", async () => {
  let url = "";
  const fetchImpl = (async (input: string | URL | Request) => {
    url = String(input);
    return new Response(JSON.stringify({ name: "밴드", color: "#000", memberCount: 3, ownerName: "민수" }));
  }) as typeof fetch;
  const preview = await fetchInvitePreview(ORIGIN, "a b", fetchImpl);
  assert.deepEqual(preview, { name: "밴드", memberCount: 3, ownerName: "민수" });
  assert.equal(url, "http://api.test/api/public/invites/a%20b");
});

test("404·410·모양이 다른 응답·네트워크 오류는 null 이다", async () => {
  assert.equal(await fetchInvitePreview(ORIGIN, "x", respond(404, { message: "없음" })), null);
  assert.equal(await fetchInvitePreview(ORIGIN, "x", respond(410, { message: "만료" })), null);
  assert.equal(await fetchInvitePreview(ORIGIN, "x", respond(200, { name: "밴드" })), null);
  const broken = (async () => {
    throw new TypeError("fetch failed");
  }) as typeof fetch;
  assert.equal(await fetchInvitePreview(ORIGIN, "x", broken), null);
});

test("응답이 늦으면 시간 초과로 null 이다", async () => {
  const hang = ((_: unknown, init?: RequestInit) =>
    new Promise((_resolve, reject) => {
      init?.signal?.addEventListener("abort", () => reject(init.signal?.reason));
    })) as typeof fetch;
  // AbortSignal.timeout 타이머는 이벤트 루프를 붙잡지 않으므로, 기다리는 동안 ref 된 타이머를 따로 둡니다.
  const keepAlive = setTimeout(() => {}, 1000);
  const started = Date.now();
  assert.equal(await fetchInvitePreview(ORIGIN, "x", hang, 50), null);
  clearTimeout(keepAlive);
  assert.ok(Date.now() - started < 1000);
});
