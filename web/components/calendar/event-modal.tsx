"use client";
import { RecurrenceUntilField, buildRule, ruleFrequency, ruleUntil } from "./recurrence-until";
import { visibilityLabel } from "./shared";
import { COLOR_NAMES, EVENT_COLORS } from "@/lib/colors";
import { shouldOfferCancelAnnounce } from "@/lib/event-cancel-announce";
import { shouldOfferMoveAnnounce } from "@/lib/event-move-announce";
import type { CalendarEvent, Group, User, Visibility } from "@/lib/types";
import { useDialog } from "@/lib/use-dialog";
import { format, parseISO } from "date-fns";
import { Check, Clock3, Link2, Trash2, UserRound, X } from "lucide-react";
import { useRef, useState } from "react";

export function EventModal({
  value,
  groups,
  me,
  remindersOff,
  onClose,
  onSave,
  onDelete,
  show,
}: {
  value: Partial<CalendarEvent>;
  groups: Group[];
  me: User;
  remindersOff: boolean;
  onClose: () => void;
  onSave: (e: Partial<CalendarEvent>) => void;
  onDelete: (id: number, announce: boolean) => Promise<void>;
  show: (m: string) => void;
}) {
  const dialog = useDialog<HTMLFormElement>(onClose);
  const [v, setV] = useState({
    ...value,
    visibilities: value.visibilities?.length ? value.visibilities : [value.visibility || "PRIVATE"],
  });
  const readOnly = Boolean(v.id && v.owner && v.owner.id !== me.id);
  const selected = v.visibilities || ["PRIVATE"];
  const untilRef = useRef<HTMLInputElement>(null);
  const frequency = ruleFrequency(v.recurrenceRule || "");
  const until = ruleUntil(v.recurrenceRule || "");
  const startDate = format(parseISO(v.startAt!), "yyyy-MM-dd");
  const untilInvalid = !readOnly && Boolean(frequency && until && until < startDate);
  // 기존 그룹 일정의 시간을 바꾸면 "바뀐 시간을 그룹 멤버에게 알리기" 줄을 기본 체크로 보여 줍니다.
  // 줄이 사라지면(시간 원복·GROUP 해제) 체크도 풀어 PUT 에 announce=true 가 새어 나가지 않게 합니다.
  const offerMove = !readOnly && shouldOfferMoveAnnounce(value, v);
  const [offeredMove, setOfferedMove] = useState(false);
  if (v.id && offerMove !== offeredMove) {
    setOfferedMove(offerMove);
    setV((x) => ({ ...x, announce: offerMove }));
  }
  // 저장된 그룹 일정을 지울 때 "그룹 멤버에게 취소 알리기"를 기본 체크로 보여 줍니다.
  // 시간 변경 알림(v.announce, PUT 용)과 섞이지 않게 따로 두고, 모달을 열 때마다 다시 체크된 상태로 시작합니다.
  const offerCancel = shouldOfferCancelAnnounce(value, me.id);
  const [announceCancel, setAnnounceCancel] = useState(true);
  const [deleting, setDeleting] = useState(false);
  async function remove(id: number) {
    setDeleting(true);
    try {
      await onDelete(id, offerCancel && announceCancel);
    } finally {
      setDeleting(false);
    }
  }
  function set<K extends keyof CalendarEvent>(key: K, val: CalendarEvent[K]) {
    setV((x) => ({ ...x, [key]: val }));
  }
  function changeMoment(key: "startAt" | "endAt", part: "date" | "time", value: string) {
    setV((current) => {
      const old = parseISO(current[key]!);
      const nextDate = part === "date" ? value : format(old, "yyyy-MM-dd");
      const nextTime = part === "time" ? value : format(old, "HH:mm");
      const next = new Date(`${nextDate}T${nextTime}:00`);
      if (Number.isNaN(next.getTime())) return current;
      if (key === "startAt") {
        const duration = parseISO(current.endAt!).getTime() - old.getTime();
        return {
          ...current,
          startAt: next.toISOString(),
          endAt: new Date(next.getTime() + Math.max(duration, 30 * 60 * 1000)).toISOString(),
        };
      }
      return { ...current, endAt: next.toISOString() };
    });
  }
  function toggleVisibility(item: Visibility) {
    if (readOnly) return;
    if (item === "PRIVATE") {
      set("visibilities", ["PRIVATE"]);
      return;
    }
    const withoutPrivate = selected.filter((x) => x !== "PRIVATE");
    const next = withoutPrivate.includes(item) ? withoutPrivate.filter((x) => x !== item) : [...withoutPrivate, item];
    set("visibilities", next.length ? next : ["PRIVATE"]);
  }
  return (
    <div className="modal-scrim" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <form
        ref={dialog.ref}
        role="dialog"
        aria-modal="true"
        aria-labelledby={dialog.titleId}
        tabIndex={-1}
        className={`event-modal ${readOnly ? "read-only" : ""}`}
        onSubmit={(e) => {
          e.preventDefault();
          if (untilInvalid) untilRef.current?.focus();
          else if (!readOnly) onSave(v);
        }}
      >
        <header>
          <div>
            <span className="modal-color" style={{ background: v.color }} />
            <h2 id={dialog.titleId}>{readOnly ? "일정 보기" : v.id ? "일정 수정" : "새 일정"}</h2>
          </div>
          <button type="button" className="icon-btn" aria-label="닫기" onClick={onClose}>
            <X />
          </button>
        </header>
        {readOnly && (
          <div className="owner-notice">
            <UserRound />
            {v.owner?.name}님의 일정 · 보기만 가능해요
          </div>
        )}
        <label className="title-input">
          <input
            autoFocus={!readOnly}
            readOnly={readOnly}
            required
            maxLength={100}
            value={v.title || ""}
            onChange={(e) => set("title", e.target.value)}
            placeholder="일정 제목"
          />
        </label>
        <div className="time-editor">
          <Clock3 />
          <div className="time-row">
            <span>시작</span>
            <input
              aria-label="시작 날짜"
              required
              disabled={readOnly}
              type="date"
              value={startDate}
              onChange={(e) => changeMoment("startAt", "date", e.target.value)}
            />
            <input
              aria-label="시작 시간"
              required
              disabled={readOnly || v.allDay}
              type="time"
              step="1800"
              value={format(parseISO(v.startAt!), "HH:mm")}
              onChange={(e) => changeMoment("startAt", "time", e.target.value)}
            />
          </div>
          <div className="time-row">
            <span>종료</span>
            <input
              aria-label="종료 날짜"
              required
              disabled={readOnly}
              type="date"
              value={format(parseISO(v.endAt!), "yyyy-MM-dd")}
              onChange={(e) => changeMoment("endAt", "date", e.target.value)}
            />
            <input
              aria-label="종료 시간"
              required
              disabled={readOnly || v.allDay}
              type="time"
              step="1800"
              value={format(parseISO(v.endAt!), "HH:mm")}
              onChange={(e) => changeMoment("endAt", "time", e.target.value)}
            />
          </div>
        </div>
        <label className="check-line">
          <input
            disabled={readOnly}
            type="checkbox"
            checked={v.allDay || false}
            onChange={(e) => set("allDay", e.target.checked)}
          />
          <span>종일 일정</span>
        </label>
        <div className="field-label">
          <span>공개 범위 · 여러 개 선택 가능</span>
          <div className="visibility-options">
            {(Object.keys(visibilityLabel) as Visibility[]).map((item) => (
              <button
                type="button"
                disabled={readOnly}
                key={item}
                className={selected.includes(item) ? "selected" : ""}
                onClick={() => toggleVisibility(item)}
              >
                <Check />
                {visibilityLabel[item]}
              </button>
            ))}
          </div>
        </div>
        {selected.includes("GROUP") && (
          <fieldset disabled={readOnly}>
            <legend>공개할 그룹</legend>
            {groups.map((g) => (
              <label className="check-line" key={g.id}>
                <input
                  type="checkbox"
                  checked={v.groupIds?.includes(g.id) || false}
                  onChange={(e) =>
                    set(
                      "groupIds",
                      e.target.checked ? [...(v.groupIds || []), g.id] : (v.groupIds || []).filter((x) => x !== g.id),
                    )
                  }
                />
                <span className="group-dot" style={{ background: g.color }} />
                {g.name}
              </label>
            ))}
          </fieldset>
        )}
        {!readOnly && selected.includes("GROUP") && (!v.id || offerMove) && (
          <>
            <label className="check-line announce-line">
              <input
                type="checkbox"
                checked={v.announce || false}
                aria-describedby={v.id ? "move-announce-hint" : undefined}
                onChange={(e) => set("announce", e.target.checked)}
              />
              <span>{v.id ? "바뀐 시간을 그룹 멤버에게 알리기" : "그룹 멤버에게 알리기"}</span>
            </label>
            {v.id && (
              <span id="move-announce-hint" className="sr-only">
                시간이 바뀌어서 알릴 수 있어요.
              </span>
            )}
          </>
        )}
        <div className="form-grid">
          <label className="field-label">
            반복
            <select
              disabled={readOnly}
              value={frequency}
              onChange={(e) => set("recurrenceRule", buildRule(e.target.value, until))}
            >
              <option value="">반복 안 함</option>
              <option value="FREQ=DAILY">매일</option>
              <option value="FREQ=WEEKLY">매주</option>
              <option value="FREQ=MONTHLY">매월</option>
            </select>
          </label>
          {frequency && (
            <RecurrenceUntilField
              until={until}
              startDate={startDate}
              invalid={untilInvalid}
              readOnly={readOnly}
              inputRef={untilRef}
              onChange={(next) => set("recurrenceRule", buildRule(frequency, next))}
            />
          )}
          <label className="field-label">
            알림
            <select
              disabled={readOnly}
              aria-describedby={remindersOff && !readOnly ? "reminder-off-hint" : undefined}
              value={v.reminderMinutes || 0}
              onChange={(e) => set("reminderMinutes", Number(e.target.value))}
            >
              <option value={0}>알림 없음</option>
              <option value={10}>10분 전</option>
              <option value={30}>30분 전</option>
              <option value={60}>1시간 전</option>
              <option value={1440}>하루 전</option>
            </select>
          </label>
          {remindersOff && !readOnly && (
            <p id="reminder-off-hint" className="reminder-hint">
              휴대폰 알림이 꺼져 있어 울리지 않아요. 설정 &gt; Toogeduler에서 켤 수 있어요.
            </p>
          )}
        </div>
        <label className="field-label">
          색상
          <span className="color-picker">
            {EVENT_COLORS.map((c) => (
              <button
                disabled={readOnly}
                type="button"
                key={c}
                aria-label={COLOR_NAMES[c]}
                aria-pressed={v.color === c}
                className={v.color === c ? "active" : ""}
                style={{ background: c }}
                onClick={() => set("color", c)}
              />
            ))}
          </span>
        </label>
        <label className="field-label">
          장소
          <input
            readOnly={readOnly}
            maxLength={255}
            value={v.location || ""}
            onChange={(e) => set("location", e.target.value)}
            placeholder="장소 추가"
          />
        </label>
        <label className="field-label">
          메모
          <textarea
            readOnly={readOnly}
            maxLength={255}
            value={v.description || ""}
            onChange={(e) => set("description", e.target.value)}
            placeholder="메모 추가"
          />
        </label>
        {v.id && selected.includes("PUBLIC") && (
          <button
            type="button"
            className="copy-link"
            onClick={() => {
              navigator.clipboard.writeText(`${location.origin}/public/${v.publicToken}`);
              show("공개 링크를 복사했어요.");
            }}
          >
            <Link2 />
            공개 링크 복사
          </button>
        )}
        {offerCancel && (
          <>
            <label className="check-line announce-line cancel">
              <input
                type="checkbox"
                checked={announceCancel}
                disabled={deleting}
                aria-describedby="cancel-announce-hint"
                onChange={(e) => setAnnounceCancel(e.target.checked)}
              />
              <span>그룹 멤버에게 취소 알리기</span>
            </label>
            <span id="cancel-announce-hint" className="sr-only">
              삭제하면 같은 그룹 멤버에게 일정이 취소됐다고 알려요.
            </span>
          </>
        )}
        <footer>
          {!readOnly && v.id ? (
            <button type="button" className="danger" disabled={deleting} onClick={() => remove(v.id!)}>
              <Trash2 />
              삭제
            </button>
          ) : (
            <span />
          )}
          <div>
            <button type="button" className="ghost" onClick={onClose}>
              {readOnly ? "닫기" : "취소"}
            </button>
            {!readOnly && <button className="primary">저장</button>}
          </div>
        </footer>
      </form>
    </div>
  );
}
