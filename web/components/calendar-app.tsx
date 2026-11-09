"use client";
import { AvailabilityPanel } from "./calendar/availability-panel";
import { EventModal } from "./calendar/event-modal";
import { FriendsPage } from "./calendar/friends-page";
import { GroupSettings } from "./calendar/group-settings";
import { MonthView } from "./calendar/month-view";
import { NotificationPanel } from "./calendar/notification-panel";
import { PeopleStrip } from "./calendar/people-strip";
import { Sidebar } from "./calendar/sidebar";
import { Toast, type ToastAction } from "./calendar/toast";
import { WeekView } from "./calendar/week-view";
import { REMINDERS_DENIED, offsetIso } from "./calendar/shared";
import { ApiError, api } from "@/lib/api";
import { EVENT_COLORS } from "@/lib/colors";
import { deleteEventUrl } from "@/lib/event-cancel-announce";
import { confirmedShareData, groupLabel } from "@/lib/event-share";
import { shareOrCopy } from "@/lib/invite-share";
import { ruleEndingBefore } from "@/lib/recurrence-cut";
import {
  askReminderPermissionOnce,
  cancelAllReminders,
  onAppForeground,
  remindersBlocked,
  syncReminders,
} from "@/lib/native-reminders";
import { clearBrowserPwaCaches } from "@/lib/pwa";
import { unsubscribeBrowserPush } from "@/lib/web-push-browser";
import { shiftSeries } from "@/lib/time";
import { viewerZone } from "@/lib/zoned-time";
import type { AppNotification, CalendarEvent, Group, Slot, User } from "@/lib/types";
import {
  addDays,
  addMonths,
  endOfMonth,
  endOfWeek,
  format,
  isSameDay,
  parseISO,
  startOfMonth,
  startOfWeek,
  subMonths,
} from "date-fns";
import { ko } from "date-fns/locale";
import { Bell, ChevronDown, ChevronLeft, ChevronRight, Menu, Plus, Sparkles } from "lucide-react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

export default function CalendarApp() {
  const router = useRouter();
  const [me, setMe] = useState<User | null>(null);
  const [events, setEvents] = useState<CalendarEvent[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);
  const [active, setActive] = useState<"calendar" | "groups" | "friends">("calendar");
  const [view, setView] = useState<"week" | "month">("week");
  const [anchor, setAnchor] = useState(new Date());
  const [modal, setModal] = useState<Partial<CalendarEvent> | null>(null);
  const [groupId, setGroupId] = useState<number | null>(null);
  const [availability, setAvailability] = useState(false);
  const [groupSettings, setGroupSettings] = useState(false);
  const [mobileNav, setMobileNav] = useState(false);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [notifications, setNotifications] = useState<AppNotification[]>([]);
  const [unread, setUnread] = useState(0);
  const [toast, setToast] = useState("");
  const [toastNotice, setToastNotice] = useState(false);
  const [toastAction, setToastAction] = useState<ToastAction | null>(null);
  const [remindersOff, setRemindersOff] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const toastTimer = useRef(0);
  const eventsRequest = useRef(0);
  // notice 는 길게 읽어야 하는 안내(알림 권한 거부)라 5초 동안 종 아이콘과 함께 보여줍니다.
  // action 이 붙으면 버튼을 누를 시간이 필요해 8초 동안 보여주고, 사용 중(hold)에는 타이머를 멈춥니다.
  const holdToast = useCallback((paused: boolean, ms = 8000) => {
    window.clearTimeout(toastTimer.current);
    if (!paused) toastTimer.current = window.setTimeout(() => setToast(""), ms);
  }, []);
  const show = useCallback(
    (message: string, notice = false, action?: ToastAction) => {
      setToast(message);
      setToastNotice(notice);
      setToastAction(action ?? null);
      holdToast(false, action ? 8000 : notice ? 5000 : 2400);
    },
    [holdToast],
  );
  const hideToast = useCallback(() => {
    window.clearTimeout(toastTimer.current);
    setToast("");
  }, []);
  // 리마인더 알림 링크(/?event=ID&at=회차시작)를 열어, 반복 일정이면 바로 그 회차를 보여줍니다.
  const openLinkedEvent = useCallback(
    (params: URLSearchParams) => {
      const id = Number(params.get("event"));
      if (!id) return;
      api<CalendarEvent>(`/api/events/${id}`)
        .then((e) => {
          const at = params.get("at");
          const start = at ? new Date(at) : parseISO(e.startAt);
          const end = new Date(start.getTime() + parseISO(e.endAt).getTime() - parseISO(e.startAt).getTime());
          setAnchor(start);
          setModal({
            ...e,
            startAt: start.toISOString(),
            endAt: end.toISOString(),
            instanceStart: start.toISOString(),
            seriesStartAt: e.startAt,
            seriesEndAt: e.endAt,
          });
        })
        .catch(() => show("일정을 찾을 수 없어요."));
    },
    [show],
  );
  const range = useMemo(
    () =>
      view === "week"
        ? { from: startOfWeek(anchor, { weekStartsOn: 0 }), to: endOfWeek(anchor, { weekStartsOn: 0 }) }
        : {
            from: startOfWeek(startOfMonth(anchor), { weekStartsOn: 0 }),
            to: endOfWeek(endOfMonth(anchor), { weekStartsOn: 0 }),
          },
    [anchor, view],
  );
  const load = useCallback(async () => {
    try {
      const [u, g] = await Promise.all([api<User>("/api/auth/me"), api<Group[]>("/api/groups")]);
      setMe(u);
      setGroups(g);
      const params = new URLSearchParams(location.search);
      const created = Number(sessionStorage.getItem("toogeduler_group"));
      const linkedGroup = Number(params.get("group"));
      if (created || linkedGroup) {
        sessionStorage.removeItem("toogeduler_group");
        setGroupId(created || linkedGroup);
        setActive("groups");
        const at = params.get("at");
        if (linkedGroup && at) setAnchor(new Date(at));
      } else if (params.get("tab") === "friends") setActive("friends");
      else setGroupId((x) => x ?? g[0]?.id ?? null);
      openLinkedEvent(params);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        localStorage.removeItem("toogeduler_token");
        router.replace("/login");
      } else
        setLoadError(
          e instanceof Error && e instanceof ApiError
            ? e.message
            : "서버에 연결하지 못했어요. 인터넷 연결을 확인해주세요.",
        );
    }
  }, [router, openLinkedEvent]);
  const loadNotifications = useCallback(async () => {
    try {
      const [data, count] = await Promise.all([
        api<AppNotification[]>("/api/notifications"),
        api<{ count: number }>("/api/notifications/unread-count"),
      ]);
      setNotifications(data);
      setUnread(count.count);
    } catch {}
  }, []);
  const loadEvents = useCallback(async () => {
    const request = ++eventsRequest.current;
    try {
      const path = active === "groups" && groupId ? `/api/groups/${groupId}/events` : `/api/events`;
      const data = await api<CalendarEvent[]>(
        `${path}?from=${range.from.toISOString()}&to=${addDays(range.to, 1).toISOString()}`,
      );
      if (request === eventsRequest.current) setEvents(data);
    } catch (e) {
      if (request === eventsRequest.current) show(e instanceof Error ? e.message : "일정을 불러오지 못했습니다.");
    }
  }, [active, groupId, range.from, range.to, show]);
  useEffect(() => {
    load().finally(() => setLoading(false));
  }, [load]);
  useEffect(() => {
    if (me && active !== "friends") loadEvents();
  }, [me, active, loadEvents]);
  useEffect(() => {
    if (!me) return;
    loadNotifications();
    const timer = window.setInterval(loadNotifications, 60000);
    return () => window.clearInterval(timer);
  }, [me, loadNotifications]);
  // 네이티브 앱에서는 로그인 후 앱 시작·포그라운드 복귀 때마다 휴대폰 일정 알림을 다시 예약합니다. (웹에서는 아무것도 하지 않습니다.)
  const meId = me?.id;
  useEffect(() => {
    if (!meId) return;
    const refresh = () => {
      syncReminders(meId);
      remindersBlocked().then(setRemindersOff);
    };
    refresh();
    return onAppForeground(refresh);
  }, [meId]);
  useEffect(() => {
    const saved = localStorage.getItem("toogeduler_calendar_view");
    if (saved === "week" || saved === "month") setView(saved);
    else if (window.matchMedia("(max-width: 600px)").matches) setView("month");
  }, []);
  useEffect(() => {
    if (!modal || !window.matchMedia("(max-width: 600px)").matches) return;
    const frame = window.requestAnimationFrame(() => {
      const focused = document.activeElement;
      if (focused instanceof HTMLElement && focused.closest(".event-modal")) focused.blur();
    });
    return () => window.cancelAnimationFrame(frame);
  }, [modal]);
  // "함께 되는 시간"에서 고른 시간을 그룹 공개 일정 초안으로 엽니다. 저장하면 그룹 캘린더에 바로 보입니다.
  function bookSlot(group: Group, slot: Slot) {
    setAvailability(false);
    setAnchor(parseISO(slot.startAt));
    setModal({
      title: `${group.name} 모임`,
      startAt: slot.startAt,
      endAt: slot.endAt,
      allDay: false,
      visibility: "GROUP",
      visibilities: ["GROUP"],
      reminderMinutes: 30,
      color: EVENT_COLORS.includes(group.color.toUpperCase()) ? group.color.toUpperCase() : EVENT_COLORS[0],
      groupIds: [group.id],
      announce: true,
    });
  }
  function logout() {
    cancelAllReminders();
    unsubscribeBrowserPush();
    clearBrowserPwaCaches();
    localStorage.removeItem("toogeduler_token");
    router.push("/login");
  }
  function newAt(date?: Date) {
    const now = new Date();
    const start = new Date(date || now);
    if (date) {
      start.setHours(9, 0, 0, 0);
      if (isSameDay(start, now) && start < now) start.setHours(now.getHours() + 1, 0, 0, 0);
    } else {
      start.setMinutes(start.getMinutes() < 30 ? 30 : 0, 0, 0);
      if (start < now) start.setHours(now.getHours() + 1, 0, 0, 0);
    }
    const end = new Date(start.getTime() + 60 * 60 * 1000);
    setModal({
      title: "",
      startAt: start.toISOString(),
      endAt: end.toISOString(),
      allDay: false,
      visibility: groupId && active === "groups" ? "GROUP" : "PRIVATE",
      visibilities: [groupId && active === "groups" ? "GROUP" : "PRIVATE"],
      reminderMinutes: 30,
      color: EVENT_COLORS[0],
      groupIds: groupId && active === "groups" ? [groupId] : [],
    });
  }
  async function openNotification(item: AppNotification) {
    if (!item.read) await api(`/api/notifications/${item.id}/read`, { method: "PATCH" }).catch(() => {});
    setNotificationOpen(false);
    loadNotifications();
    const link = item.link || "/";
    if (link.startsWith("/?event=")) {
      setActive("calendar");
      openLinkedEvent(new URLSearchParams(link.slice(2)));
    } else if (link.startsWith("/?group=")) {
      const params = new URLSearchParams(link.slice(2));
      const at = params.get("at");
      setGroupId(Number(params.get("group")));
      setActive("groups");
      if (at) setAnchor(new Date(at));
    } else router.push(link);
  }
  async function readAll() {
    try {
      await api("/api/notifications/read-all", { method: "PATCH" });
      setNotifications((x) => x.map((n) => ({ ...n, read: true })));
      setUnread(0);
    } catch (e) {
      show(e instanceof Error ? e.message : "알림을 읽음 처리하지 못했습니다.");
    }
  }
  // 일정이 바뀌면 휴대폰 알림을 다시 예약합니다. 알림을 고른 일정을 처음 저장한 경우에만 모달이 닫힌 뒤 권한을 한 번 묻습니다.
  function afterReminderChange(askPermission = false) {
    if (!me) return;
    const id = me.id;
    (askPermission ? askReminderPermissionOnce() : Promise.resolve("skipped"))
      .then((result) => {
        if (result === "denied") {
          setRemindersOff(true);
          show(REMINDERS_DENIED, true);
        }
      })
      .finally(() => syncReminders(id));
  }
  async function saveEvent(input: Partial<CalendarEvent>) {
    try {
      const times =
        input.id && input.recurrenceRule
          ? shiftSeries(input as CalendarEvent, offsetIso(input.startAt!), offsetIso(input.endAt!))
          : { startAt: offsetIso(input.startAt!), endAt: offsetIso(input.endAt!) };
      const body = { ...input, ...times, groupIds: input.groupIds || [] };
      if (input.id) await api(`/api/events/${input.id}`, { method: "PUT", body: JSON.stringify(body) });
      const created = input.id
        ? null
        : await api<CalendarEvent>("/api/events", { method: "POST", body: JSON.stringify(body) });
      setModal(null);
      if (created && body.groupIds.length) show("일정을 추가했어요.", false, shareAction(created, body.groupIds));
      else if (input.id && body.announce) show("일정을 수정하고 멤버에게 알렸어요.");
      else show(input.id ? "일정을 수정했어요." : "일정을 추가했어요.");
      loadEvents();
      afterReminderChange((input.reminderMinutes || 0) > 0);
    } catch (e) {
      show(e instanceof Error ? e.message : "저장하지 못했습니다.");
    }
  }
  // 그룹 일정을 새로 확정하면 토스트에 "단톡방에 알리기"를 붙입니다. 응답에 id 가 없으면 링크 없이 문구만 공유합니다.
  function shareAction(created: CalendarEvent, groupIds: number[]): ToastAction {
    const names = groupIds.map((id) => groups.find((g) => g.id === id)?.name).filter((n): n is string => !!n);
    return {
      label: "단톡방에 알리기",
      ariaLabel: "일정을 단톡방에 알리기",
      run: async () => {
        const data = confirmedShareData(created, groupLabel(names), viewerZone(), window.location.origin);
        const result = await shareOrCopy(navigator, data);
        if (result === "copied") show("확정 안내를 복사했어요.");
        else if (result === "failed") show("복사하지 못했어요.");
        else hideToast();
      },
    };
  }
  // scopeChosen: 모달의 삭제 범위 선택 줄에서 "모든 회차 삭제"를 이미 고른 경우라 확인창을 다시 띄우지 않습니다.
  async function deleteEvent(id: number, announce: boolean, scopeChosen = false) {
    if (
      !scopeChosen &&
      events.some((e) => e.id === id && e.recurrenceRule) &&
      !window.confirm("반복 일정이에요. 모든 회차가 함께 삭제됩니다.")
    )
      return;
    try {
      await api(deleteEventUrl(id, announce), { method: "DELETE" });
      setModal(null);
      show(announce ? "일정을 삭제하고 그룹에 알렸어요." : "일정을 삭제했어요.");
      loadEvents();
      afterReminderChange();
    } catch (e) {
      show(e instanceof Error ? e.message : "삭제하지 못했습니다.");
    }
  }
  // "이 일정부터 이후 모두 삭제": 시리즈의 원래 시작·끝과 나머지 필드는 그대로 두고 반복 규칙만 이 회차 직전에서 끝냅니다.
  async function endSeriesBefore(occurrence: Partial<CalendarEvent>) {
    const body = {
      ...occurrence,
      startAt: occurrence.seriesStartAt,
      endAt: occurrence.seriesEndAt,
      recurrenceRule: ruleEndingBefore(occurrence.recurrenceRule!, occurrence.instanceStart!),
      groupIds: occurrence.groupIds || [],
      announce: false,
    };
    try {
      await api(`/api/events/${occurrence.id}`, { method: "PUT", body: JSON.stringify(body) });
      setModal(null);
      show("이 일정부터 반복을 끝냈어요.");
      loadEvents();
      afterReminderChange();
    } catch (e) {
      show(e instanceof Error ? e.message : "반복을 끝내지 못했어요. 잠시 후 다시 시도해 주세요.");
    }
  }
  async function moveEvent(event: CalendarEvent, start: Date) {
    const duration = parseISO(event.endAt).getTime() - parseISO(event.startAt).getTime();
    if (start.getTime() === parseISO(event.startAt).getTime()) return;
    if (event.recurrenceRule && !window.confirm("반복 일정이에요. 모든 회차가 같은 만큼 함께 이동합니다.")) return;
    const times = shiftSeries(event, start.toISOString(), new Date(start.getTime() + duration).toISOString());
    try {
      await api(`/api/events/${event.id}/move`, { method: "PATCH", body: JSON.stringify(times) });
      loadEvents();
      afterReminderChange();
    } catch (e) {
      show(e instanceof Error ? e.message : "이동하지 못했습니다.");
    }
  }
  if (loadError && !me)
    return (
      <main className="center-state load-error">
        <p>{loadError}</p>
        <button
          className="primary"
          onClick={() => {
            setLoadError("");
            setLoading(true);
            load().finally(() => setLoading(false));
          }}
        >
          다시 시도
        </button>
      </main>
    );
  if (loading || !me)
    return (
      <main className="center-state">
        <span className="loader" />
        캘린더를 펼치고 있어요
      </main>
    );
  const currentGroup = groups.find((g) => g.id === groupId);
  return (
    <div className="app-shell">
      <Sidebar
        me={me}
        active={active}
        setActive={setActive}
        groups={groups}
        groupId={groupId}
        setGroupId={(id) => {
          setGroupId(id);
          setActive("groups");
        }}
        open={mobileNav}
        close={() => setMobileNav(false)}
        logout={logout}
        onCreateGroup={() => router.push("/groups/new")}
        onPublic={() => router.push("/public")}
        onProfile={() => router.push("/mypage")}
      />
      <main className="workspace">
        <header className="topbar">
          <button className="icon-btn mobile-menu" aria-label="메뉴 열기" onClick={() => setMobileNav(true)}>
            <Menu />
          </button>
          <div className="page-title">
            <span>
              {active === "calendar"
                ? "내 캘린더"
                : active === "friends"
                  ? "친구"
                  : currentGroup?.name || "그룹 캘린더"}
            </span>
            {active === "groups" && (
              <button className="group-select" onClick={() => setGroupSettings(true)}>
                멤버 {currentGroup?.members.length || 0}명 <ChevronDown size={14} />
              </button>
            )}
          </div>
          <div className="top-actions">
            <div className="notification-wrap">
              <button
                className={`icon-btn notification ${notificationOpen ? "active" : ""}`}
                aria-label={`알림 ${unread}개`}
                aria-expanded={notificationOpen}
                onClick={() => {
                  setNotificationOpen((x) => !x);
                  loadNotifications();
                }}
              >
                <Bell />
                {unread > 0 && <b>{unread > 9 ? "9+" : unread}</b>}
              </button>
              {notificationOpen && (
                <NotificationPanel
                  items={notifications}
                  onOpen={openNotification}
                  onReadAll={readAll}
                  onClose={() => setNotificationOpen(false)}
                />
              )}
            </div>
            <button
              className="avatar"
              aria-label="마이페이지"
              title="마이페이지"
              onClick={() => router.push("/mypage")}
            >
              {me.name.slice(-2)}
            </button>
          </div>
        </header>
        {active === "friends" ? (
          <FriendsPage show={show} />
        ) : (
          <>
            <section className="calendar-toolbar">
              <div className="period">
                <button className="today" onClick={() => setAnchor(new Date())}>
                  오늘
                </button>
                <button
                  className="nav-arrow"
                  aria-label={view === "week" ? "이전 주" : "이전 달"}
                  onClick={() => setAnchor(view === "week" ? addDays(anchor, -7) : subMonths(anchor, 1))}
                >
                  <ChevronLeft />
                </button>
                <button
                  className="nav-arrow"
                  aria-label={view === "week" ? "다음 주" : "다음 달"}
                  onClick={() => setAnchor(view === "week" ? addDays(anchor, 7) : addMonths(anchor, 1))}
                >
                  <ChevronRight />
                </button>
                <h1>{format(anchor, "yyyy년 M월", { locale: ko })}</h1>
              </div>
              <div className="toolbar-actions">
                {active === "groups" && (
                  <button className="find-time" onClick={() => setAvailability(true)}>
                    <Sparkles size={16} />
                    함께 되는 시간
                  </button>
                )}
                <div className="segmented">
                  <button
                    className={view === "week" ? "active" : ""}
                    onClick={() => {
                      setView("week");
                      localStorage.setItem("toogeduler_calendar_view", "week");
                    }}
                  >
                    주
                  </button>
                  <button
                    className={view === "month" ? "active" : ""}
                    onClick={() => {
                      setView("month");
                      localStorage.setItem("toogeduler_calendar_view", "month");
                    }}
                  >
                    월
                  </button>
                </div>
                <button className="primary add-event" onClick={() => newAt()}>
                  <Plus />
                  일정 추가
                </button>
              </div>
            </section>
            {active === "groups" && currentGroup && <PeopleStrip group={currentGroup} show={show} />}
            <section className="calendar-card">
              {view === "week" ? (
                <WeekView
                  anchor={anchor}
                  events={events}
                  me={me}
                  showOwner={active === "groups"}
                  onOpen={setModal}
                  onCreate={(s, e) =>
                    setModal({
                      title: "",
                      startAt: s.toISOString(),
                      endAt: e.toISOString(),
                      allDay: false,
                      visibility: active === "groups" ? "GROUP" : "PRIVATE",
                      visibilities: [active === "groups" ? "GROUP" : "PRIVATE"],
                      reminderMinutes: 30,
                      color: EVENT_COLORS[0],
                      groupIds: active === "groups" && groupId ? [groupId] : [],
                    })
                  }
                  onMove={moveEvent}
                />
              ) : (
                <MonthView
                  anchor={anchor}
                  events={events}
                  me={me}
                  showOwner={active === "groups"}
                  onOpen={setModal}
                  onCreate={newAt}
                  onMove={moveEvent}
                />
              )}
            </section>
          </>
        )}
      </main>
      {modal && (
        <EventModal
          value={modal}
          groups={groups}
          me={me}
          remindersOff={remindersOff}
          onClose={() => setModal(null)}
          onSave={saveEvent}
          onDelete={deleteEvent}
          onEndSeries={endSeriesBefore}
          show={show}
        />
      )}
      {availability && currentGroup && (
        <AvailabilityPanel
          group={currentGroup}
          meId={me?.id}
          close={() => setAvailability(false)}
          show={show}
          onPick={(slot) => bookSlot(currentGroup, slot)}
        />
      )}
      {groupSettings && currentGroup && (
        <GroupSettings group={currentGroup} me={me} close={() => setGroupSettings(false)} show={show} reload={load} />
      )}
      {toast && <Toast message={toast} notice={toastNotice} action={toastAction} hold={holdToast} close={hideToast} />}
    </div>
  );
}
