"use client";
import { fmtDate } from "./shared";
import { personColor } from "@/components/owner-avatar";
import { api } from "@/lib/api";
import { bestBlock } from "@/lib/time";
import type { Group, Slot } from "@/lib/types";
import { useDialog } from "@/lib/use-dialog";
import { calendarDays, clockMinutes, viewerZone, zonedClock, zonedLabel } from "@/lib/zoned-time";
import { addDays } from "date-fns";
import { CalendarPlus, Check, Clock3, Copy, Sparkles, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";

// 함께 되는 시간: 기간 전체를 날짜 × 시간 지도로 보여줍니다. 모두 되는 구간은 앱 아이콘의 겹침 색으로, 일부만 되는 구간은 되는 사람이 많을수록 진하게 칠합니다.
// 필수 참석자: 고른 사람이 모두 되는 구간만 받아옵니다. 나중에 요금제 경계를 두기 쉽게 칩 줄은 RequiredPicker로 따로 둡니다.
export function AvailabilityPanel({
  group,
  meId,
  close,
  show,
  onPick,
}: {
  group: Group;
  meId?: number;
  close: () => void;
  show: (m: string) => void;
  onPick: (slot: Slot) => void;
}) {
  const dialog = useDialog<HTMLElement>(close);
  const today = new Date();
  const zone = useMemo(viewerZone, []);
  const [from, setFrom] = useState(fmtDate(today));
  const [to, setTo] = useState(fmtDate(addDays(today, 14)));
  const [start, setStart] = useState("09:00");
  const [end, setEnd] = useState("22:00");
  const [minutes, setMinutes] = useState(60);
  const [required, setRequired] = useState<number[]>([]);
  const [blocks, setBlocks] = useState<Slot[]>([]);
  const [searched, setSearched] = useState({ from, to, start, end, minutes, required: [] as number[] });
  const [picked, setPicked] = useState<Slot | null>(null);
  const pickedRef = useRef<HTMLElement>(null);
  const [loading, setLoading] = useState(false);
  async function search() {
    setLoading(true);
    const ids = required.filter((id) => group.members.some((m) => m.id === id));
    try {
      const found: Slot[] = await api(
        `/api/groups/${group.id}/availability/blocks?from=${from}&to=${to}&dayStart=${start}&dayEnd=${end}&minMinutes=${minutes}&timeZone=${encodeURIComponent(zone)}${ids.length ? `&required=${ids.join(",")}` : ""}`,
      );
      setBlocks(found);
      setSearched({ from, to, start, end, minutes, required: ids });
      setPicked(bestBlock(found));
    } catch (e) {
      show(e instanceof Error ? e.message : "시간을 찾지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    search();
  }, []);
  const best = bestBlock(blocks);
  const dayStart = clockMinutes(searched.start),
    span = clockMinutes(searched.end) - dayStart;
  const byDay = useMemo(() => {
    const map: Record<string, Slot[]> = {};
    for (const b of blocks) (map[zonedClock(b.startAt, zone).day] ??= []).push(b);
    return map;
  }, [blocks]);
  const ticks = Array.from({ length: Math.floor(span / 180) + 1 }, (_, i) => dayStart + i * 180).filter(
    (m) => m <= dayStart + span,
  );
  const time = (iso: string) => zonedLabel(iso, { hour: "numeric", minute: "2-digit" }, zone);
  const range = (b: Slot) => `${time(b.startAt)} – ${time(b.endAt)}`;
  const people = (names: string[]) => group.members.filter((m) => names.includes(m.name));
  const book = (b: Slot) =>
    onPick({
      ...b,
      endAt: new Date(Math.min(Date.parse(b.startAt) + searched.minutes * 60000, Date.parse(b.endAt))).toISOString(),
    });
  // 휴대폰에서는 지도를 내려 본 채로 고르므로, 고른 시간 카드가 화면 밖이면 보이는 곳까지 올려준다.
  const [reveal, setReveal] = useState(0);
  const choose = (b: Slot) => {
    setPicked(b);
    setReveal((n) => n + 1);
  };
  useEffect(() => {
    if (reveal)
      pickedRef.current?.scrollIntoView({
        block: "nearest",
        behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth",
      });
  }, [reveal]);
  const must = searched.required.length;
  const who = (b: Slot) => {
    if (b.everyoneAvailable) return must === b.totalCount ? `필수 ${must}명 모두 돼요` : `${b.totalCount}명 모두 돼요`;
    const away = group.members.filter((m) => !b.availablePeople.includes(m.name)).map((m) => m.name);
    if (!must) return `${b.totalCount}명 중 ${b.availableCount}명이 돼요. ${away.join(", ")}님은 안 돼요.`;
    return `필수 ${must}명 모두 + 나머지 ${b.totalCount - must}명 중 ${b.availableCount - must}명이 돼요. ${away.join(", ")}님은 안 돼요.`;
  };
  return (
    <div className="drawer-scrim" onMouseDown={(e) => e.target === e.currentTarget && close()}>
      <aside
        ref={dialog.ref}
        role="dialog"
        aria-modal="true"
        aria-labelledby={dialog.titleId}
        tabIndex={-1}
        className="availability"
      >
        <header>
          <div>
            <span className="spark">
              <Sparkles />
            </span>
            <div>
              <p>함께 되는 시간</p>
              <h2 id={dialog.titleId}>{group.name}</h2>
            </div>
          </div>
          <button className="icon-btn" onClick={close} aria-label="닫기">
            <X />
          </button>
        </header>
        <div className="availability-form">
          <label>
            기간
            <span>
              <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
              <i>–</i>
              <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
            </span>
          </label>
          <div>
            <label>
              시간대
              <span>
                <input type="time" value={start} onChange={(e) => setStart(e.target.value)} />
                <i>–</i>
                <input type="time" value={end} onChange={(e) => setEnd(e.target.value)} />
              </span>
            </label>
            <label>
              최소 시간
              <select value={minutes} onChange={(e) => setMinutes(Number(e.target.value))}>
                <option value={30}>30분</option>
                <option value={60}>1시간</option>
                <option value={90}>1시간 30분</option>
                <option value={120}>2시간</option>
              </select>
            </label>
          </div>
          {group.members.length > 1 && (
            <RequiredPicker members={group.members} meId={meId} value={required} onChange={setRequired} />
          )}
          <button className="primary full" onClick={search} disabled={loading}>
            {loading ? "맞춰보는 중..." : "다시 찾기"}
          </button>
        </div>
        <div className="overlap-results">
          {!loading &&
            blocks.length === 0 &&
            (must ? (
              <div className="empty-slots">
                <Clock3 />
                <p>필수 참석자가 모두 되는 시간이 없어요.</p>
                <small>필수를 줄이거나 기간을 늘려보세요.</small>
              </div>
            ) : (
              <div className="empty-slots">
                <Clock3 />
                <p>조건에 맞는 시간이 없어요.</p>
                <small>기간을 늘리거나 최소 시간을 줄여보세요.</small>
              </div>
            ))}
          {picked && (
            <section ref={pickedRef} className={picked.everyoneAvailable ? "picked all" : "picked"} aria-live="polite">
              <p>
                {picked === best
                  ? best.everyoneAvailable
                    ? "모두 되는 가장 빠른 시간"
                    : must
                      ? "필수 모두 + 가장 많이 되는 시간"
                      : "가장 많이 되는 시간"
                  : zonedLabel(picked.startAt, { month: "long", day: "numeric", weekday: "long" }, zone)}
              </p>
              <h3>
                {picked === best && (
                  <span>{zonedLabel(picked.startAt, { month: "long", day: "numeric", weekday: "short" }, zone)} </span>
                )}
                {range(picked)}
              </h3>
              <div className="picked-people">
                <span className="faces" aria-hidden="true">
                  {/* 얼굴은 다섯까지만(필수 멤버 먼저), 나머지는 +N. 겹치지 않게 나란히 둔다. */}
                  {(() => {
                    const faces = people(picked.availablePeople).sort(
                      (a, b) => Number(searched.required.includes(b.id)) - Number(searched.required.includes(a.id)),
                    );
                    return (
                      <>
                        {faces.slice(0, 5).map((m) => (
                          <i
                            key={m.id}
                            className={searched.required.includes(m.id) ? "must" : undefined}
                            style={{ background: personColor(m.id) }}
                          >
                            {m.name.slice(-1)}
                          </i>
                        ))}
                        {faces.length > 5 && <i className="more">+{faces.length - 5}</i>}
                      </>
                    );
                  })()}
                </span>
                <span>{who(picked)}</span>
              </div>
              <div className="picked-actions">
                <button type="button" className="primary" onClick={() => book(picked)}>
                  <CalendarPlus />이 시간으로 잡기
                </button>
                <button
                  type="button"
                  className="icon-btn"
                  onClick={() => {
                    navigator.clipboard.writeText(
                      `${zonedLabel(picked.startAt, { month: "long", day: "numeric", weekday: "short" }, zone)} ${range(picked)}`,
                    );
                    show("시간을 복사했어요.");
                  }}
                  aria-label="이 시간 복사"
                >
                  <Copy />
                </button>
              </div>
            </section>
          )}
          {blocks.length > 0 && (
            <div className="overlap-map" role="group" aria-label="날짜별로 되는 시간">
              <div className="map-scale" aria-hidden="true">
                <span />
                <div>
                  {ticks.map((m) => (
                    <small key={m} style={{ left: `${((m - dayStart) / span) * 100}%` }}>
                      {Math.floor(m / 60) % 12 || 12}
                      {m / 60 >= 12 ? "pm" : "am"}
                    </small>
                  ))}
                </div>
              </div>
              {calendarDays(searched.from, searched.to).map(({ day, noon }) => {
                const weekday = zonedLabel(noon, { weekday: "short" }, "UTC");
                return (
                  <div className="map-row" key={day}>
                    <span className={weekday === "일" ? "map-day sun" : weekday === "토" ? "map-day sat" : "map-day"}>
                      <b>
                        {Number(day.slice(5, 7))}.{Number(day.slice(8))}
                      </b>
                      {weekday}
                    </span>
                    <div className="map-track">
                      {(byDay[day] ?? []).map((b) => {
                        const s = zonedClock(b.startAt, zone).minutes,
                          e = zonedClock(b.endAt, zone).minutes || 24 * 60;
                        return (
                          <button
                            type="button"
                            key={b.startAt}
                            className={`${b.everyoneAvailable ? "seg all" : !best?.everyoneAvailable && b.availableCount === best?.availableCount ? "seg top" : "seg"}${picked === b ? " on" : ""}`}
                            style={{
                              left: `${((s - dayStart) / span) * 100}%`,
                              width: `${((e - s) / span) * 100}%`,
                              ["--share" as string]: b.availableCount / b.totalCount,
                              ["--l" as string]: (s - dayStart) / span,
                              ["--w" as string]: (e - s) / span,
                            }}
                            aria-pressed={picked === b}
                            aria-label={`${zonedLabel(b.startAt, { month: "long", day: "numeric" }, zone)} ${range(b)}, ${who(b)}`}
                            title={`${range(b)}, ${b.totalCount}명 중 ${b.availableCount}명`}
                            onClick={() => choose(b)}
                          />
                        );
                      })}
                    </div>
                  </div>
                );
              })}
              <div className="map-legend">
                {best?.everyoneAvailable ? (
                  <span>
                    <i className="all" />
                    모두 돼요
                  </span>
                ) : (
                  best && (
                    <span>
                      <i className="top" />
                      가장 많이 돼요 ({best.availableCount}명)
                    </span>
                  )
                )}
                <span>
                  <i className="some" />
                  일부만 돼요 (진할수록 많이)
                </span>
                {must > 0 && <span>필수 {must}명 모두 되는 시간만 보여요</span>}
              </div>
            </div>
          )}
        </div>
      </aside>
    </div>
  );
}

/** 꼭 와야 하는 사람 고르기. 고르기만 하고 검색은 "다시 찾기"를 눌러야 반영한다. 많으면 처음 몇 명만 보여 결과가 밀려나지 않게 한다. */

export function RequiredPicker({
  members,
  meId,
  value,
  onChange,
}: {
  members: Group["members"];
  meId?: number;
  value: number[];
  onChange: (ids: number[]) => void;
}) {
  const [open, setOpen] = useState(false);
  const [compact, setCompact] = useState(false);
  useEffect(() => {
    const q = matchMedia("(max-width:600px)");
    const sync = () => setCompact(q.matches);
    sync();
    q.addEventListener("change", sync);
    return () => q.removeEventListener("change", sync);
  }, []);
  const limit = compact ? 6 : 8;
  const shown = open ? members : members.slice(0, limit);
  const picked = value.filter((id) => members.some((m) => m.id === id)).length;
  return (
    <div className="must-row" role="group" aria-labelledby="must-label">
      <div className="must-head">
        <span id="must-label">꼭 와야 하는 사람</span>
        <small aria-live="polite">{picked}명 선택</small>
      </div>
      <div className="must-chips">
        {shown.map((m) => {
          const on = value.includes(m.id);
          return (
            <button
              type="button"
              key={m.id}
              className="must-chip"
              aria-pressed={on}
              onClick={() => onChange(on ? value.filter((id) => id !== m.id) : [...value, m.id])}
              style={{ ["--c" as string]: personColor(m.id) }}
            >
              <i aria-hidden="true">{m.name.slice(-1)}</i>
              {on && <Check aria-hidden="true" />}
              <b>{m.name}</b>
              {m.id === meId && <em>(나)</em>}
            </button>
          );
        })}
        {members.length > limit && (
          <button type="button" className="must-more" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
            {open ? "접기" : `더 보기 (${members.length - limit}명)`}
          </button>
        )}
      </div>
    </div>
  );
}
