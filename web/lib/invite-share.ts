// 초대 링크 보내기: 공유 시트가 있으면 공유 시트를, 없으면 클립보드 복사를 씁니다.
// navigator 를 인자로 받으므로 `npm test` 로 브라우저 없이 검증합니다.
import type { Group } from "./types.ts";

export type InviteShareData = { title: string; text: string; url: string };

/** shared: 공유 시트를 띄웠다. cancelled: 사용자가 시트를 닫았다. copied: 클립보드에 복사했다. failed: 복사도 못 했다. */
export type ShareResult = "shared" | "cancelled" | "copied" | "failed";

/** 공유 시트와 클립보드에 필요한 것만 받는다. 브라우저 navigator 를 그대로 넘겨도 된다. */
export type ShareNavigator = {
  share?: (data: InviteShareData) => Promise<void>;
  clipboard?: { writeText: (text: string) => Promise<void> };
};

export function inviteShareData(group: Pick<Group, "name" | "inviteUrl">): InviteShareData {
  return {
    title: "Toogeduler",
    text: `"${group.name}" 그룹에서 함께 되는 시간을 맞춰요`,
    url: group.inviteUrl,
  };
}

export async function shareOrCopy(nav: ShareNavigator, data: InviteShareData): Promise<ShareResult> {
  if (typeof nav.share === "function") {
    try {
      await nav.share(data);
      return "shared";
    } catch (e) {
      // 사용자가 공유 시트를 닫은 것은 오류가 아니므로 아무것도 하지 않는다. 그 밖의 실패는 복사로 내려간다.
      if ((e as { name?: string } | null)?.name === "AbortError") return "cancelled";
    }
  }
  try {
    if (!nav.clipboard) return "failed";
    await nav.clipboard.writeText(`${data.text}\n${data.url}`);
    return "copied";
  } catch {
    return "failed";
  }
}
