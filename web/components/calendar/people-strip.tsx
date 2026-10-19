"use client";
import { personColor } from "@/components/owner-avatar";
import type { Group } from "@/lib/types";
import { Link2 } from "lucide-react";

export function PeopleStrip({ group, show }: { group: Group; show: (m: string) => void }) {
  return (
    <div className="people-strip">
      <div className="face-stack">
        {group.members.slice(0, 5).map((m) => (
          <span key={m.id} title={m.name} style={{ background: m.avatarUrl ? "#fff" : personColor(m.id) }}>
            {m.avatarUrl ? <img src={m.avatarUrl} alt="" referrerPolicy="no-referrer" /> : m.name.slice(-2)}
          </span>
        ))}
        {group.members.length > 5 && <span className="more">+{group.members.length - 5}</span>}
      </div>
      <p>
        <b>{group.members.map((m) => m.name).join(", ")}</b>의 공유 일정
      </p>
      <span className="privacy-note">
        <span className="lock-dot" />
        비공개 일정은 빈 시간 계산에만 반영돼요
      </span>
      <button
        className="invite-copy"
        onClick={() => {
          navigator.clipboard.writeText(group.inviteUrl);
          show("초대 링크를 복사했어요.");
        }}
      >
        <Link2 />
        초대 링크
      </button>
    </div>
  );
}
