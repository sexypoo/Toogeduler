"use client";
import { inviteShareData, shareOrCopy } from "@/lib/invite-share";
import type { Group } from "@/lib/types";
import { Clock3 } from "lucide-react";
import { useState } from "react";

// 멤버가 나 혼자인 그룹에서 "함께 되는 시간"을 열면, 빈 결과 대신 초대 링크 보내기를 먼저 권합니다.
export function InviteAlone({ group, show }: { group: Group; show: (m: string) => void }) {
  const [sharing, setSharing] = useState(false);
  async function send() {
    setSharing(true);
    try {
      const result = await shareOrCopy(navigator, inviteShareData(group));
      if (result === "copied") show("초대 링크를 복사했어요.");
      if (result === "failed") show("복사하지 못했어요. 설정에서 링크를 복사해 주세요.");
    } finally {
      setSharing(false);
    }
  }
  return (
    <div className="empty-slots alone" role="status">
      <Clock3 aria-hidden="true" />
      <p>아직 나만 있어요</p>
      {group.inviteUrl ? (
        <>
          <small>친구를 초대하면 모두 되는 시간을 바로 찾아 드려요.</small>
          <button type="button" className="primary" onClick={send} disabled={sharing}>
            초대 링크 보내기
          </button>
        </>
      ) : (
        <small>그룹 설정에서 초대 링크를 확인해 주세요.</small>
      )}
    </div>
  );
}
