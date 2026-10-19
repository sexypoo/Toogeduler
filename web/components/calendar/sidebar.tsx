"use client";
import { BrandMark } from "@/components/brand-mark";
import type { Group, User } from "@/lib/types";
import { CalendarDays, Globe2, LogOut, Plus, UserRound, UsersRound, X } from "lucide-react";
import { useEffect } from "react";

export function Sidebar({
  me,
  active,
  setActive,
  groups,
  groupId,
  setGroupId,
  open,
  close,
  logout,
  onCreateGroup,
  onPublic,
  onProfile,
}: {
  me: User;
  active: string;
  setActive: (v: "calendar" | "groups" | "friends") => void;
  groups: Group[];
  groupId: number | null;
  setGroupId: (id: number) => void;
  open: boolean;
  close: () => void;
  logout: () => void;
  onCreateGroup: () => void;
  onPublic: () => void;
  onProfile: () => void;
}) {
  // 모바일에서 펼친 메뉴는 Escape 로도 닫습니다.
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") close();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, close]);
  return (
    <>
      <div className={`nav-scrim ${open ? "show" : ""}`} onClick={close} />
      <aside className={`sidebar ${open ? "open" : ""}`}>
        <div className="brand">
          <BrandMark />
          Toogeduler
          <button className="close-nav" aria-label="메뉴 닫기" onClick={close}>
            <X />
          </button>
        </div>
        <nav>
          <p>캘린더</p>
          <button
            className={active === "calendar" ? "active" : ""}
            onClick={() => {
              setActive("calendar");
              close();
            }}
          >
            <CalendarDays />내 캘린더
          </button>
          <button onClick={onPublic}>
            <Globe2 />
            공개 일정
          </button>
          <p>사람들</p>
          <button
            className={active === "friends" ? "active" : ""}
            onClick={() => {
              setActive("friends");
              close();
            }}
          >
            <UserRound />
            친구
          </button>
          <button
            className={active === "groups" ? "active" : ""}
            onClick={() => {
              setActive("groups");
              close();
            }}
          >
            <UsersRound />
            그룹
          </button>
        </nav>
        <div className="group-list">
          <div>
            <p>내 그룹</p>
            <button className="tiny-add" aria-label="그룹 추가" onClick={onCreateGroup}>
              <Plus />
            </button>
          </div>
          {groups.map((g) => (
            <button
              key={g.id}
              className={active === "groups" && groupId === g.id ? "selected" : ""}
              onClick={() => {
                setGroupId(g.id);
                setActive("groups");
                close();
              }}
            >
              <i style={{ background: g.color }} />
              {g.name}
              <span>{g.members.length}</span>
            </button>
          ))}
        </div>
        <div className="sidebar-user">
          <button className="avatar" onClick={onProfile}>
            {me.name.slice(-2)}
          </button>
          <button className="sidebar-profile" onClick={onProfile}>
            <b>{me.name}</b>
            <small>{me.email}</small>
          </button>
          <button onClick={logout} title="로그아웃" aria-label="로그아웃">
            <LogOut />
          </button>
        </div>
      </aside>
    </>
  );
}
