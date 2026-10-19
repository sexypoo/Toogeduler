"use client";
import { OwnerAvatar } from "@/components/owner-avatar";
import { overlapsDay } from "@/lib/time";
import type { CalendarEvent, User } from "@/lib/types";
import { addDays, format, isSameMonth, isToday, parseISO, startOfMonth, startOfWeek } from "date-fns";

export function MonthView({
  anchor,
  events,
  me,
  showOwner,
  onOpen,
  onCreate,
  onMove,
}: {
  anchor: Date;
  events: CalendarEvent[];
  me: User;
  showOwner: boolean;
  onOpen: (e: CalendarEvent) => void;
  onCreate: (d: Date) => void;
  onMove: (e: CalendarEvent, s: Date) => void;
}) {
  const first = startOfWeek(startOfMonth(anchor), { weekStartsOn: 0 });
  const days = Array.from({ length: 42 }, (_, i) => addDays(first, i));
  return (
    <div className="month-calendar">
      <div className="month-head">
        {["일", "월", "화", "수", "목", "금", "토"].map((x) => (
          <span key={x}>{x}</span>
        ))}
      </div>
      <div className="month-grid">
        {days.map((d) => (
          <div
            key={d.toISOString()}
            className={`${!isSameMonth(d, anchor) ? "outside" : ""} ${isToday(d) ? "today-cell" : ""} ${d.getDay() === 0 ? "sunday" : ""}`}
            role="button"
            tabIndex={0}
            aria-label={`${format(d, "M월 d일")} 일정 추가`}
            onClick={(e) => {
              if (!(e.target as HTMLElement).closest("button")) onCreate(d);
            }}
            onKeyDown={(e) => {
              if ((e.key === "Enter" || e.key === " ") && e.target === e.currentTarget) {
                e.preventDefault();
                onCreate(d);
              }
            }}
            onDragOver={(e) => e.preventDefault()}
            onDrop={(e) => {
              e.preventDefault();
              const item = events.find((x) => `${x.id}|${x.startAt}` === e.dataTransfer.getData("eventKey"));
              if (item && item.owner.id === me.id) {
                const old = parseISO(item.startAt);
                const s = new Date(d);
                s.setHours(old.getHours(), old.getMinutes());
                onMove(item, s);
              }
            }}
          >
            <b>{format(d, "d")}</b>
            {events
              .filter((e) => overlapsDay(e.startAt, e.endAt, d))
              .slice(0, 3)
              .map((e) => (
                <button
                  className="month-event"
                  style={{ "--c": e.color } as React.CSSProperties}
                  draggable={e.owner.id === me.id}
                  key={`${e.id}-${e.startAt}`}
                  onDragStart={(x) => x.dataTransfer.setData("eventKey", `${e.id}|${e.startAt}`)}
                  onClick={(x) => {
                    x.stopPropagation();
                    onOpen(e);
                  }}
                >
                  <i />
                  {showOwner && <OwnerAvatar owner={e.owner} />}
                  <span>{e.title}</span>
                  <small>{e.allDay ? "종일" : format(parseISO(e.startAt), "HH:mm")}</small>
                </button>
              ))}
            {events.filter((e) => overlapsDay(e.startAt, e.endAt, d)).length > 3 && (
              <span className="more-events">
                +{events.filter((e) => overlapsDay(e.startAt, e.endAt, d)).length - 3}개 더
              </span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
