"use client";
import type { AppNotification } from "@/lib/types";
import { useDialog } from "@/lib/use-dialog";
import { formatDistanceToNow, parseISO } from "date-fns";
import { ko } from "date-fns/locale";
import { Bell, BellRing, CalendarPlus, UserPlus, UsersRound } from "lucide-react";

export function NotificationPanel({
  items,
  onOpen,
  onReadAll,
  onClose,
}: {
  items: AppNotification[];
  onOpen: (n: AppNotification) => void;
  onReadAll: () => void;
  onClose: () => void;
}) {
  const dialog = useDialog<HTMLElement>(onClose, { modal: false });
  const icon = (type: AppNotification["type"]) =>
    type === "EVENT_REMINDER" ? (
      <BellRing />
    ) : type === "GROUP_EVENT" ? (
      <CalendarPlus />
    ) : type === "GROUP_JOINED" ? (
      <UsersRound />
    ) : (
      <UserPlus />
    );
  return (
    <aside ref={dialog.ref} role="dialog" aria-labelledby={dialog.titleId} tabIndex={-1} className="notification-panel">
      <header>
        <div>
          <p id={dialog.titleId}>알림</p>
          <span>{items.filter((x) => !x.read).length}개 새 소식</span>
        </div>
        {items.some((x) => !x.read) && <button onClick={onReadAll}>모두 읽음</button>}
      </header>
      <div>
        {items.length === 0 ? (
          <div className="notification-empty">
            <Bell />
            <b>새 알림이 없어요</b>
            <span>일정과 사람들의 소식이 여기에 표시됩니다.</span>
          </div>
        ) : (
          items.map((item) => (
            <button key={item.id} className={item.read ? "" : "unread"} onClick={() => onOpen(item)}>
              <i>{icon(item.type)}</i>
              <span>
                <b>{item.title}</b>
                <small>{item.message}</small>
                <em>{formatDistanceToNow(parseISO(item.createdAt), { addSuffix: true, locale: ko })}</em>
              </span>
              {!item.read && <u />}
            </button>
          ))
        )}
      </div>
    </aside>
  );
}
