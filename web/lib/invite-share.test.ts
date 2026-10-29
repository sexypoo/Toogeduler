import { test } from "node:test";
import assert from "node:assert/strict";
import { inviteShareData, shareOrCopy, type InviteShareData } from "./invite-share.ts";

const DATA = inviteShareData({ name: "사진 동아리", inviteUrl: "https://toogeduler.app/invite/abc" });

function fakeNav(share?: (d: InviteShareData) => Promise<void>, writeText?: (t: string) => Promise<void>) {
  const calls = { share: 0, copied: [] as string[] };
  const nav = {
    share: share
      ? async (d: InviteShareData) => {
          calls.share++;
          await share(d);
        }
      : undefined,
    clipboard: {
      writeText: async (t: string) => {
        calls.copied.push(t);
        if (writeText) await writeText(t);
      },
    },
  };
  return { nav, calls };
}

test("공유 문구에 그룹 이름과 초대 링크를 담는다", () => {
  assert.deepEqual(DATA, {
    title: "Toogeduler",
    text: '"사진 동아리" 그룹에서 함께 되는 시간을 맞춰요',
    url: "https://toogeduler.app/invite/abc",
  });
});

test("share 가 있으면 share 를 한 번 부르고 복사하지 않는다", async () => {
  const { nav, calls } = fakeNav(async () => {});
  assert.equal(await shareOrCopy(nav, DATA), "shared");
  assert.equal(calls.share, 1);
  assert.deepEqual(calls.copied, []);
});

test("share 가 없으면 초대 링크가 들어간 문구를 복사한다", async () => {
  const { nav, calls } = fakeNav();
  assert.equal(await shareOrCopy(nav, DATA), "copied");
  assert.equal(calls.copied.length, 1);
  assert.ok(calls.copied[0].includes("https://toogeduler.app/invite/abc"));
});

test("사용자가 공유를 취소하면 복사하지 않고 cancelled 를 돌려준다", async () => {
  const { nav, calls } = fakeNav(async () => {
    throw new DOMException("Share canceled", "AbortError");
  });
  // UI 는 copied 일 때만 토스트를 띄우므로, cancelled 면 토스트도 없다.
  assert.equal(await shareOrCopy(nav, DATA), "cancelled");
  assert.equal(calls.share, 1);
  assert.deepEqual(calls.copied, []);
});

test("취소가 아닌 공유 오류면 복사로 내려간다", async () => {
  const { nav, calls } = fakeNav(async () => {
    throw new DOMException("Not allowed", "NotAllowedError");
  });
  assert.equal(await shareOrCopy(nav, DATA), "copied");
  assert.equal(calls.copied.length, 1);
});

test("복사까지 거부되면 failed 를 돌려준다", async () => {
  const { nav } = fakeNav(undefined, async () => {
    throw new Error("denied");
  });
  assert.equal(await shareOrCopy(nav, DATA), "failed");
});
