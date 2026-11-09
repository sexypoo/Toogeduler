package app.toogeduler.service;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import app.toogeduler.web.EventController;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notifications;
    private final EventRepository events;
    private final GroupMemberRepository members;
    private final PushService pushes;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("M월 d일 (E) a h:mm", Locale.KOREAN);

    @Transactional
    public void notify(
            User user,
            Notification.Type type,
            String title,
            String message,
            String link,
            String dedupeKey) {
        if (notifications.existsByUserIdAndDedupeKey(user.getId(), dedupeKey)) return;
        Notification n = new Notification();
        n.setUser(user);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setLink(link);
        n.setDedupeKey(dedupeKey);
        notifications.save(n);
        pushes.sendAfterCommit(user, title, message, link);
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    @Transactional
    public void createDueEventReminders() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime until = now.plusDays(1);
        for (Event event : events.reminderCandidates(now, until)) {
            Integer minutes = event.getReminderMinutes();
            if (minutes == null || minutes <= 0) continue;
            for (EventController.EventRange occurrence :
                    EventController.occurrenceRanges(event, now, until)) {
                OffsetDateTime remindAt = occurrence.startAt().minusMinutes(minutes);
                if (remindAt.isAfter(now) || !occurrence.startAt().isAfter(now)) continue;
                String key = "event-reminder:" + event.getId() + ":" + occurrence.startAt();
                notify(
                        event.getOwner(),
                        Notification.Type.EVENT_REMINDER,
                        event.getTitle() + " 일정이 곧 시작해요",
                        reminderText(minutes),
                        "/?event=" + event.getId() + "&at=" + occurrence.startAt().toInstant(),
                        key);
            }
        }
    }

    /**
     * 그룹에 공유한 일정을 만들며 "그룹 멤버에게 알리기"를 고른 경우, 그 그룹의 다른 멤버에게 알린다. 여러 그룹에 함께 공유해도 한 사람에게는 한 번만 보낸다. 알림을
     * 누르면 그룹 캘린더의 그 날짜로 간다.
     */
    @Transactional
    public void announceGroupEvent(Event event) {
        User owner = event.getOwner();
        String when = event.getStartAt().atZoneSameInstant(SEOUL).format(WHEN);
        Set<Long> notified = new HashSet<>();
        notified.add(owner.getId());
        // 여러 그룹에 함께 있는 사람은 가장 먼저 만든 그룹 이름으로 한 번만 받는다. Set 순회 순서에 맡기면 문구가 실행마다 달라진다.
        for (Group group :
                event.getGroups().stream().sorted(Comparator.comparing(Group::getId)).toList())
            for (GroupMember member : members.findByGroupId(group.getId())) {
                User user = member.getUser();
                if (!notified.add(user.getId())) continue;
                notify(
                        user,
                        Notification.Type.GROUP_EVENT,
                        "새 그룹 일정이 잡혔어요",
                        owner.getName()
                                + "님이 "
                                + group.getName()
                                + "에 "
                                + when
                                + " '"
                                + event.getTitle()
                                + "' 일정을 잡았어요.",
                        "/?group=" + group.getId() + "&at=" + event.getStartAt().toInstant(),
                        "group-event:" + event.getId() + ":" + user.getId());
            }
    }

    /**
     * 그룹에 공유한 일정의 시간을 바꾸며 "바뀐 시간을 그룹 멤버에게 알리기"를 고른 경우, 그 그룹의 다른 멤버에게 새 시각을 알린다. 같은 시각으로 두 번 저장해도 한
     * 번만, 다른 시각으로 또 옮기면 다시 보낸다. 여러 그룹에 함께 있는 사람은 한 번만 받는다.
     */
    @Transactional
    public void announceGroupEventMoved(Event event) {
        User owner = event.getOwner();
        String when = event.getStartAt().atZoneSameInstant(SEOUL).format(WHEN);
        long startSecond = event.getStartAt().toEpochSecond();
        Set<Long> notified = new HashSet<>();
        notified.add(owner.getId());
        for (Group group :
                event.getGroups().stream().sorted(Comparator.comparing(Group::getId)).toList())
            for (GroupMember member : members.findByGroupId(group.getId())) {
                User user = member.getUser();
                if (!notified.add(user.getId())) continue;
                notify(
                        user,
                        Notification.Type.GROUP_EVENT,
                        "그룹 일정 시간이 바뀌었어요",
                        owner.getName()
                                + "님이 "
                                + group.getName()
                                + "의 '"
                                + event.getTitle()
                                + "' 일정을 "
                                + when
                                + "으로 옮겼어요.",
                        "/?group=" + group.getId() + "&at=" + event.getStartAt().toInstant(),
                        "group-event-moved:"
                                + event.getId()
                                + ":"
                                + startSecond
                                + ":"
                                + user.getId());
            }
    }

    /** 취소 알림을 보낼 그룹 하나. 일정을 지우기 전에 읽어 둔 그룹 ID·이름과 멤버 목록이다. */
    public record CanceledGroup(Long id, String name, List<User> members) {}

    /**
     * 그룹에 공유한 일정을 지우며 "그룹 멤버에게 취소 알리기"를 고른 경우, 그 그룹의 다른 멤버에게 취소를 알린다. 일정은 이미 지워졌으므로 제목·시작 시각·그룹을
     * 값으로 받는다. 여러 그룹에 함께 있는 사람은 앞 그룹(ID 순으로 넘겨받음) 이름으로 한 번만 받는다. 알림을 누르면 그 그룹 화면으로 간다.
     */
    @Transactional
    public void announceGroupEventCanceled(
            Long eventId,
            String title,
            OffsetDateTime startAt,
            User owner,
            List<CanceledGroup> groups) {
        String when = startAt.atZoneSameInstant(SEOUL).format(WHEN);
        Set<Long> notified = new HashSet<>();
        notified.add(owner.getId());
        for (CanceledGroup group : groups)
            for (User user : group.members()) {
                if (!notified.add(user.getId())) continue;
                notify(
                        user,
                        Notification.Type.GROUP_EVENT,
                        "그룹 일정이 취소됐어요",
                        owner.getName()
                                + "님이 "
                                + group.name()
                                + "의 "
                                + when
                                + " '"
                                + title
                                + "' 일정을 취소했어요.",
                        "/?group=" + group.id(),
                        "group-event-canceled:" + eventId + ":" + user.getId());
            }
    }

    private String reminderText(int minutes) {
        if (minutes == 1440) return "내일 시작하는 일정입니다.";
        if (minutes >= 60) return (minutes / 60) + "시간 뒤에 시작합니다.";
        return minutes + "분 뒤에 시작합니다.";
    }
}
