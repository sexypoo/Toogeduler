"use client";
import { inviteExpiry } from "@/components/calendar/shared";
import { ApiError, api } from "@/lib/api";
import type { Group } from "@/lib/types";
import { Copy } from "lucide-react";
import { useState } from "react";

// 그룹 관리 서랍의 "초대 링크" 섹션. 공유(복사)는 모든 멤버가, 새 링크 만들기는 그룹장만 합니다.
export function InviteLink({
  group,
  owner,
  show,
  reload,
}: {
  group: Group;
  owner: boolean;
  show: (m: string) => void;
  reload: () => Promise<void>;
}) {
  const [renewing, setRenewing] = useState(false);
  const expiry = group.inviteExpiresAt ? inviteExpiry(group.inviteExpiresAt) : null;
  const describedBy = expiry ? "invite-expiry" : undefined;
  // 바로 복사할 수 있게 서랍은 닫지 않습니다. 포커스도 버튼에 그대로 둡니다.
  async function renew() {
    if (!confirm("새 링크를 만들면 지금 링크는 바로 쓸 수 없어요. 계속할까요?")) return;
    setRenewing(true);
    try {
      await api(`/api/groups/${group.id}/invite/refresh`, { method: "POST" });
      await reload();
      show("새 링크를 만들었어요. 7일 동안 쓸 수 있어요.");
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) {
        show("그룹장만 새 링크를 만들 수 있어요.");
        // 그룹장이 바뀌었을 수 있으니 화면을 다시 맞춥니다.
        await reload().catch(() => undefined);
      } else show("새 링크를 만들지 못했어요. 잠시 후 다시 시도해주세요.");
    } finally {
      setRenewing(false);
    }
  }
  let note = "모든 멤버가 이 링크로 새로운 사람을 초대할 수 있어요.";
  if (expiry?.expired)
    note = owner
      ? "링크가 만료됐어요. 새 링크를 만들어 주세요."
      : "링크가 만료됐어요. 그룹장에게 새 링크를 요청하세요.";
  else if (!owner) note += " 새 링크는 그룹장이 만들 수 있어요.";
  return (
    <section>
      <label>초대 링크</label>
      <div className="invite-field">
        <input readOnly value={group.inviteUrl} aria-describedby={describedBy} />
        <button
          onClick={() => {
            navigator.clipboard.writeText(group.inviteUrl);
            show("초대 링크를 복사했어요.");
          }}
        >
          <Copy />
          복사
        </button>
      </div>
      {(expiry || owner) && (
        <div className="invite-expiry-row">
          {expiry && (
            <p id="invite-expiry" className={expiry.soon ? "invite-expiry soon" : "invite-expiry"}>
              {expiry.text}
            </p>
          )}
          {owner && (
            <button
              type="button"
              className="link-btn"
              onClick={renew}
              disabled={renewing}
              aria-busy={renewing}
              aria-describedby={describedBy}
            >
              {renewing ? "만드는 중..." : "새 링크 만들기"}
            </button>
          )}
        </div>
      )}
      <p>{note}</p>
    </section>
  );
}
