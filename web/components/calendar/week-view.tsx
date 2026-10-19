"use client";
import { offsetLabel } from "@/lib/zoned-time";
import { blockPosition, capLanes, overlapsDay, sideBySide, weekHourRange } from "@/lib/time";
import type { CalendarEvent, User } from "@/lib/types";
import { addDays, format, isSameDay, isToday, parseISO, startOfWeek } from "date-fns";
import { ko } from "date-fns/locale";
import { useEffect, useRef, useState } from "react";

export function WeekView({
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
  onCreate: (s: Date, e: Date) => void;
  onMove: (e: CalendarEvent, s: Date) => void;
}) {
  const start = startOfWeek(anchor, { weekStartsOn: 0 });
  const days = Array.from({ length: 7 }, (_, i) => addDays(start, i));
  const dragRef = useRef<{ day: Date; startMin: number } | null>(null);
  // 기본 9–19시, 이번 주에 더 이르거나 늦은 일정이 있으면 그만큼 위아래로 늘린다. 안쪽 스크롤 없이 한 주가 한 장에 보인다.
  const { first, last } = weekHourRange(events, days);
  const [more, setMore] = useState<string | null>(null);
  useEffect(() => {
    if (!more) return;
    const close = (x: PointerEvent) => {
      if (!(x.target as HTMLElement).closest(".lane-more")) setMore(null);
    };
    document.addEventListener("pointerdown", close);
    return () => document.removeEventListener("pointerdown", close);
  }, [more]);
  const span = last - first;
  const hours = Array.from({ length: span }, (_, i) => first + i);
  function minuteAt(e: React.PointerEvent<HTMLDivElement>) {
    const r = e.currentTarget.getBoundingClientRect();
    return Math.max(0, Math.min(span * 60, Math.round((((e.clientY - r.top) / r.height) * span * 60) / 30) * 30));
  }
  function createAtDay(d: Date) {
    const now = new Date();
    const s = new Date(d);
    s.setHours(9, 0, 0, 0);
    if (isSameDay(s, now) && s < now) s.setHours(now.getHours() + 1, 0, 0, 0);
    const end = new Date(s.getTime() + 60 * 60 * 1000);
    onCreate(s, end);
  }
  return (
    <div className="week-calendar">
      <div className="week-head">
        <div className="tz" suppressHydrationWarning>
          {offsetLabel()}
        </div>
        {days.map((d) => (
          <div
            key={d.toISOString()}
            className={isToday(d) ? "today-col" : ""}
            role="button"
            tabIndex={0}
            aria-label={`${format(d, "M월 d일")} 일정 추가`}
            onClick={() => createAtDay(d)}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                createAtDay(d);
              }
            }}
          >
            <span>{format(d, "EEE", { locale: ko })}</span>
            <b>{format(d, "d")}</b>
          </div>
        ))}
      </div>
      <div className="all-day-row">
        <div>종일</div>
        {days.map((d) => (
          <div key={d.toISOString()}>
            {events
              .filter((e) => e.allDay && overlapsDay(e.startAt, e.endAt, d))
              .map((e) => (
                <button
                  key={`${e.id}-${e.startAt}`}
                  className="all-day-event"
                  style={{ background: e.color }}
                  onClick={() => onOpen(e)}
                >
                  {e.title}
                </button>
              ))}
          </div>
        ))}
      </div>
      <div className="week-scroll" style={{ ["--hours" as string]: span }}>
        <div className="time-axis">
          {hours.map((h) => (
            <span key={h} style={{ top: `${((h - first) / span) * 100}%` }}>
              {(h === first || h === 12) && <small>{h < 12 ? "오전" : "오후"}</small>}
              {h % 12 || 12}
            </span>
          ))}
        </div>
        {days.map((d) => (
          <div
            key={d.toISOString()}
            className={`day-lane ${isToday(d) ? "is-today" : ""}`}
            onPointerDown={(e) => {
              if ((e.target as HTMLElement).closest(".event-block")) return;
              dragRef.current = { day: d, startMin: minuteAt(e) };
              (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
            }}
            onPointerUp={(e) => {
              if (!dragRef.current) return;
              const endMin = minuteAt(e);
              const a = Math.min(dragRef.current.startMin, endMin),
                b = Math.max(dragRef.current.startMin, endMin) + 30;
              const s = new Date(d);
              s.setHours(first + Math.floor(a / 60), a % 60, 0, 0);
              const end = new Date(d);
              end.setHours(first + Math.floor(b / 60), b % 60, 0, 0);
              dragRef.current = null;
              onCreate(s, end);
            }}
            onDragOver={(e) => e.preventDefault()}
            onDrop={(e) => {
              e.preventDefault();
              const item = events.find((x) => `${x.id}|${x.startAt}` === e.dataTransfer.getData("eventKey"));
              if (!item) return;
              const rect = e.currentTarget.getBoundingClientRect();
              const mins = Math.round((((e.clientY - rect.top) / rect.height) * span * 60) / 30) * 30;
              const s = new Date(d);
              s.setHours(first + Math.floor(mins / 60), mins % 60, 0, 0);
              onMove(item, s);
            }}
          >
            {hours.map((h) => (
              <i className="hour-line" style={{ top: `${((h - first) / span) * 100}%` }} key={h} />
            ))}
            {(() => {
              const dayEvents = events.filter((e) => !e.allDay && overlapsDay(e.startAt, e.endAt, d));
              const lanes = capLanes(sideBySide(dayEvents));
              // 접힌 일정은 묶음마다 "+N" 칩 하나로. 칩은 접힌 일정들의 가장 이른 시작부터 가장 늦은 끝까지 걸친다.
              const folded = new Map<number, CalendarEvent[]>();
              dayEvents.forEach((e, k) => {
                if (lanes[k].hidden) folded.set(lanes[k].cluster, [...(folded.get(lanes[k].cluster) ?? []), e]);
              });
              return (
                <>
                  {dayEvents.map((e, k) => {
                    if (lanes[k].hidden) return null;
                    const s = parseISO(e.startAt);
                    const pos = blockPosition(e.startAt, e.endAt, d, first, span);
                    const { col, cols } = lanes[k];
                    return (
                      <button
                        draggable={e.owner.id === me.id}
                        onDragStart={(x) => x.dataTransfer.setData("eventKey", `${e.id}|${e.startAt}`)}
                        onPointerDown={(x) => x.stopPropagation()}
                        key={`${e.id}-${e.startAt}`}
                        className={`event-block${showOwner ? " with-owner" : ""}`}
                        style={{
                          top: `${pos.top}%`,
                          height: `${pos.height}%`,
                          left: `calc(${(col / cols) * 100}% + 2px)`,
                          width: `calc(${100 / cols}% - 4px)`,
                          ["--c" as string]: e.color,
                        }}
                        onClick={() => onOpen(e)}
                      >
                        <span>{e.title}</span>
                        <small>
                          {format(s, "HH:mm")}
                          {showOwner && ` ${e.owner.name}`}
                        </small>
                      </button>
                    );
                  })}
                  {[...folded].map(([cluster, items]) => {
                    const startAt = items.reduce((a, e) => (e.startAt < a ? e.startAt : a), items[0].startAt),
                      endAt = items.reduce((a, e) => (e.endAt > a ? e.endAt : a), items[0].endAt);
                    const pos = blockPosition(startAt, endAt, d, first, span);
                    const key = `${d.toISOString()}-${cluster}`;
                    return (
                      <div
                        key={key}
                        className="lane-more"
                        style={{ top: `${pos.top}%`, height: `${pos.height}%` }}
                        onPointerDown={(x) => x.stopPropagation()}
                      >
                        <button
                          type="button"
                          className="lane-more-chip"
                          aria-expanded={more === key}
                          aria-label={`겹친 일정 ${items.length}개 더 보기`}
                          onClick={() => setMore(more === key ? null : key)}
                        >
                          +{items.length}
                        </button>
                        {more === key && (
                          <div
                            className="lane-more-list"
                            role="menu"
                            onKeyDown={(x) => {
                              if (x.key === "Escape") setMore(null);
                            }}
                          >
                            {items.map((e) => (
                              <button
                                type="button"
                                role="menuitem"
                                key={`${e.id}-${e.startAt}`}
                                style={{ ["--c" as string]: e.color }}
                                onClick={() => {
                                  setMore(null);
                                  onOpen(e);
                                }}
                              >
                                <i />
                                <span>{e.title}</span>
                                <small>
                                  {format(parseISO(e.startAt), "HH:mm")}
                                  {showOwner && ` ${e.owner.name}`}
                                </small>
                              </button>
                            ))}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </>
              );
            })()}
          </div>
        ))}
      </div>
    </div>
  );
}
