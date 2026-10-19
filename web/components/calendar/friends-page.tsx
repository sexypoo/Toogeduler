"use client";
import { personColor } from "@/components/owner-avatar";
import { api } from "@/lib/api";
import type { User } from "@/lib/types";
import { MoreHorizontal, Plus, UserRound } from "lucide-react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

type FriendRecord = User & { requestId: number; status: "PENDING" | "ACCEPTED"; incoming: boolean };
export function FriendsPage({ show }: { show: (m: string) => void }) {
  const router = useRouter();
  const [friends, setFriends] = useState<FriendRecord[]>([]);
  const [identifier, setIdentifier] = useState("");
  const [loading, setLoading] = useState(true);
  const load = useCallback(
    () =>
      api<FriendRecord[]>("/api/friends")
        .then(setFriends)
        .catch((e) => show(e.message))
        .finally(() => setLoading(false)),
    [show],
  );
  useEffect(() => {
    load();
  }, [load]);
  async function request(e: React.FormEvent) {
    e.preventDefault();
    try {
      await api("/api/friends/requests", { method: "POST", body: JSON.stringify({ identifier }) });
      setIdentifier("");
      show("친구 요청을 보냈어요.");
      load();
    } catch (e) {
      show(e instanceof Error ? e.message : "요청을 보내지 못했습니다.");
    }
  }
  async function accept(id: number) {
    await api(`/api/friends/requests/${id}/accept`, { method: "POST" });
    show("친구가 되었어요.");
    load();
  }
  async function remove(id: number) {
    if (!confirm("친구 관계를 삭제할까요?")) return;
    await api(`/api/friends/${id}`, { method: "DELETE" });
    show("친구 관계를 삭제했어요.");
    load();
  }
  const accepted = friends.filter((f) => f.status === "ACCEPTED"),
    pending = friends.filter((f) => f.status === "PENDING");
  return (
    <section className="people-page">
      <header>
        <div>
          <h1>일정을 나눌 사람들</h1>
          <span>친구를 누르면 나에게 공유한 캘린더를 볼 수 있어요.</span>
        </div>
        <form onSubmit={request}>
          <input
            type="text"
            required
            value={identifier}
            onChange={(e) => setIdentifier(e.target.value)}
            placeholder="이메일 또는 친구 코드"
          />
          <button className="primary">
            <Plus />
            요청 보내기
          </button>
        </form>
      </header>
      {pending.length > 0 && (
        <div className="friend-section">
          <h2>
            대기 중 <span>{pending.length}</span>
          </h2>
          <div className="friend-grid">
            {pending.map((f) => (
              <article key={f.requestId}>
                <span className="friend-avatar" style={{ background: personColor(f.id) }}>
                  {f.name.slice(-1)}
                </span>
                <div>
                  <b>{f.name}</b>
                  <small>{f.email}</small>
                  <em>{f.incoming ? "나에게 친구 요청을 보냈어요" : "응답을 기다리는 중"}</em>
                </div>
                {f.incoming && (
                  <button className="accept" onClick={() => accept(f.requestId)}>
                    수락
                  </button>
                )}
              </article>
            ))}
          </div>
        </div>
      )}
      <div className="friend-section">
        <h2>
          친구 <span>{accepted.length}</span>
        </h2>
        {loading ? (
          <div className="center-state small">
            <span className="loader" />
          </div>
        ) : accepted.length === 0 ? (
          <div className="people-empty">
            <UserRound />
            <b>아직 친구가 없어요</b>
            <span>이메일이나 친구 코드로 첫 친구를 초대해보세요.</span>
          </div>
        ) : (
          <div className="friend-grid">
            {accepted.map((f) => (
              <article key={f.requestId}>
                <button className="friend-profile-link" onClick={() => router.push(`/friends/${f.id}`)}>
                  <span className="friend-avatar" style={{ background: personColor(f.id) }}>
                    {f.name.slice(-1)}
                  </span>
                  <span>
                    <b>{f.name}</b>
                    <small>{f.email}</small>
                    <em className="connected">
                      <i />
                      캘린더 보기
                    </em>
                  </span>
                </button>
                <button
                  className="icon-btn"
                  title="친구 삭제"
                  aria-label={`${f.name}님 친구 삭제`}
                  onClick={() => remove(f.requestId)}
                >
                  <MoreHorizontal />
                </button>
              </article>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
