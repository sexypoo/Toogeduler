"use client";
import { personColor } from "@/components/owner-avatar";
import { api } from "@/lib/api";
import type { Group, User } from "@/lib/types";
import { useDialog } from "@/lib/use-dialog";
import { Copy, LogOut, Trash2, X } from "lucide-react";

export function GroupSettings({
  group,
  me,
  close,
  show,
  reload,
}: {
  group: Group;
  me: User;
  close: () => void;
  show: (m: string) => void;
  reload: () => Promise<void>;
}) {
  const dialog = useDialog<HTMLElement>(close);
  const owner = group.ownerId === me.id;
  async function action(path: string, method = "POST", message = "변경했어요.") {
    try {
      await api(`/api/groups/${group.id}${path}`, { method });
      show(message);
      await reload();
      close();
    } catch (e) {
      show(e instanceof Error ? e.message : "처리하지 못했습니다.");
    }
  }
  return (
    <div className="drawer-scrim" onMouseDown={(e) => e.target === e.currentTarget && close()}>
      <aside
        ref={dialog.ref}
        role="dialog"
        aria-modal="true"
        aria-labelledby={dialog.titleId}
        tabIndex={-1}
        className="group-settings"
      >
        <header>
          <div>
            <p>그룹 관리</p>
            <h2 id={dialog.titleId}>{group.name}</h2>
          </div>
          <button className="icon-btn" aria-label="닫기" onClick={close}>
            <X />
          </button>
        </header>
        <section>
          <label>초대 링크</label>
          <div className="invite-field">
            <input readOnly value={group.inviteUrl} />
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
          <p>모든 멤버가 이 링크로 새로운 사람을 초대할 수 있어요.</p>
        </section>
        <section>
          <div className="settings-title">
            <label>멤버</label>
            <span>{group.members.length}명</span>
          </div>
          <div className="member-list">
            {group.members.map((m) => (
              <article key={m.id}>
                <span className="friend-avatar" style={{ background: personColor(m.id) }}>
                  {m.name.slice(-1)}
                </span>
                <div>
                  <b>
                    {m.name}
                    {m.id === me.id && " (나)"}
                  </b>
                  <small>{m.email}</small>
                </div>
                <em>{m.role === "OWNER" ? "그룹장" : "멤버"}</em>
                {owner && m.id !== me.id && (
                  <div className="member-actions">
                    <button
                      onClick={() =>
                        confirm(`${m.name}님에게 그룹장을 양도할까요?`) &&
                        action(`/transfer/${m.id}`, "POST", "그룹장을 양도했어요.")
                      }
                    >
                      그룹장 양도
                    </button>
                    <button
                      className="remove"
                      onClick={() =>
                        confirm(`${m.name}님을 그룹에서 내보낼까요?`) &&
                        action(`/members/${m.id}`, "DELETE", "멤버를 내보냈어요.")
                      }
                    >
                      내보내기
                    </button>
                  </div>
                )}
              </article>
            ))}
          </div>
        </section>
        <footer>
          {owner ? (
            <button
              className="danger"
              onClick={() => confirm("그룹과 공유 일정을 삭제할까요?") && action("", "DELETE", "그룹을 삭제했어요.")}
            >
              <Trash2 />
              그룹 삭제
            </button>
          ) : (
            <button
              className="danger"
              onClick={() => confirm("그룹에서 탈퇴할까요?") && action("/members/me", "DELETE", "그룹에서 탈퇴했어요.")}
            >
              <LogOut />
              그룹 탈퇴
            </button>
          )}
        </footer>
      </aside>
    </div>
  );
}
