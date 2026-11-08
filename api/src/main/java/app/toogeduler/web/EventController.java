package app.toogeduler.web;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class EventController {
    private final EventRepository events;
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final app.toogeduler.service.NotificationService notificationService;

    // 문자열 한도는 DB 컬럼(varchar 255)에 맞춘다. 넘기면 DB 오류(500)가 나므로 여기서 400으로 막는다.
    public record EventInput(
            @NotBlank(message = "제목을 입력해주세요.") @Size(max = 100, message = "제목은 100자까지 입력할 수 있어요.")
                    String title,
            @Size(max = 255, message = "메모는 255자까지 입력할 수 있어요.") String description,
            @Size(max = 255, message = "장소는 255자까지 입력할 수 있어요.") String location,
            @NotNull OffsetDateTime startAt,
            @NotNull OffsetDateTime endAt,
            boolean allDay,
            Set<Event.Visibility> visibilities,
            Event.Visibility visibility,
            @Pattern(
                            regexp = "|FREQ=(DAILY|WEEKLY|MONTHLY)(;UNTIL=[0-9]{8}(T[0-9]{6}Z)?)?",
                            message = "반복 설정이 올바르지 않습니다.")
                    String recurrenceRule,
            @Min(0) @Max(10080) Integer reminderMinutes,
            @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "색상 값이 올바르지 않습니다.") String color,
            Set<Long> groupIds,
            Boolean announce) {}

    @GetMapping("/api/events")
    List<Map<String, Object>> calendar(
            Authentication auth,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to) {
        return events.ownedCalendar(ApiSupport.user(auth).getId(), from, to).stream()
                .flatMap(e -> occurrenceViews(e, from, to).stream())
                .toList();
    }

    @PostMapping("/api/events")
    Map<String, Object> create(Authentication auth, @Valid @RequestBody EventInput body) {
        User user = ApiSupport.user(auth);
        validate(body);
        Event e = new Event();
        e.setOwner(user);
        apply(e, body, user);
        e.setPublicToken(UUID.randomUUID().toString());
        Event saved = events.save(e);
        // "그룹 멤버에게 알리기"를 고른 그룹 공개 일정이면 같은 그룹 멤버에게 알린다. 수정은 update에서 시간이 바뀐 경우만 따로 알린다.
        if (Boolean.TRUE.equals(body.announce())
                && effectiveVisibilities(saved).contains(Event.Visibility.GROUP))
            notificationService.announceGroupEvent(saved);
        return view(saved, true);
    }

    @PutMapping("/api/events/{id}")
    Map<String, Object> update(
            Authentication auth, @PathVariable Long id, @Valid @RequestBody EventInput body) {
        User user = ApiSupport.user(auth);
        Event e = owned(id, user);
        validate(body);
        // 시작·종료는 같은 순간이면 오프셋이 달라도(예: DB에서 읽은 Z와 입력의 +09:00) 바뀌지 않은 것으로 본다.
        boolean moved =
                !e.getStartAt().isEqual(body.startAt()) || !e.getEndAt().isEqual(body.endAt());
        apply(e, body, user);
        Event saved = events.save(e);
        // "바뀐 시간을 그룹 멤버에게 알리기"를 고른 그룹 공개 일정이고 시간이 실제로 바뀐 경우에만 알린다.
        if (moved
                && Boolean.TRUE.equals(body.announce())
                && effectiveVisibilities(saved).contains(Event.Visibility.GROUP))
            notificationService.announceGroupEventMoved(saved);
        return view(saved, true);
    }

    @PatchMapping("/api/events/{id}/move")
    Map<String, Object> move(
            Authentication auth, @PathVariable Long id, @RequestBody Map<String, String> body) {
        User user = ApiSupport.user(auth);
        Event e = owned(id, user);
        OffsetDateTime start = OffsetDateTime.parse(body.get("startAt")),
                end = OffsetDateTime.parse(body.get("endAt"));
        if (!end.isAfter(start))
            throw new ApiException(HttpStatus.BAD_REQUEST, "종료 시간은 시작 시간보다 뒤여야 합니다.");
        e.setStartAt(start);
        e.setEndAt(end);
        return view(events.save(e), true);
    }

    // 리마인더 알림 링크(/?event=ID)로 들어왔을 때 내 일정 하나를 열기 위해 씁니다.
    @GetMapping("/api/events/{id}")
    Map<String, Object> one(Authentication auth, @PathVariable Long id) {
        return view(owned(id, ApiSupport.user(auth)), true);
    }

    @DeleteMapping("/api/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication auth, @PathVariable Long id) {
        events.delete(owned(id, ApiSupport.user(auth)));
    }

    @GetMapping("/api/public/events")
    List<Map<String, Object>> publicCalendar(
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(defaultValue = "") String q) {
        String keyword = q.trim().toLowerCase(Locale.ROOT);
        return events.publicCalendar(from, to).stream()
                .filter(
                        e ->
                                keyword.isEmpty()
                                        || e.getTitle().toLowerCase(Locale.ROOT).contains(keyword)
                                        || (e.getDescription() != null
                                                && e.getDescription()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(keyword)))
                .flatMap(e -> occurrenceViews(e, from, to).stream())
                .limit(100)
                .toList();
    }

    @GetMapping("/api/public/events/{token}")
    Map<String, Object> publicEvent(@PathVariable String token) {
        Event e =
                events.findByPublicToken(token)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."));
        if (!effectiveVisibilities(e).contains(Event.Visibility.PUBLIC))
            throw new ApiException(HttpStatus.NOT_FOUND, "공개되지 않은 일정입니다.");
        return view(e, true);
    }

    private Event owned(Long id, User u) {
        Event e =
                events.findById(id)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."));
        if (!e.getOwner().getId().equals(u.getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "내 일정만 수정할 수 있습니다.");
        return e;
    }

    private void validate(EventInput b) {
        Set<Event.Visibility> selected = inputVisibilities(b);
        if (!b.endAt().isAfter(b.startAt()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "종료 시간은 시작 시간보다 뒤여야 합니다.");
        if (selected.contains(Event.Visibility.PRIVATE) && selected.size() > 1)
            throw new ApiException(HttpStatus.BAD_REQUEST, "나만 보기는 다른 공개 범위와 함께 선택할 수 없습니다.");
        if (selected.contains(Event.Visibility.GROUP)
                && (b.groupIds() == null || b.groupIds().isEmpty()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "공개할 그룹을 선택해주세요.");
        if (b.recurrenceRule() != null && b.recurrenceRule().contains("UNTIL=")) {
            OffsetDateTime until = untilInstant(normalizeRule(b.recurrenceRule(), b.startAt()));
            if (until == null)
                throw new ApiException(HttpStatus.BAD_REQUEST, "반복 끝나는 날이 올바르지 않습니다.");
            if (until.isBefore(b.startAt()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "끝나는 날은 시작일 이후여야 해요.");
        }
    }

    private void apply(Event e, EventInput b, User u) {
        Set<Event.Visibility> selected = inputVisibilities(b);
        e.setTitle(b.title());
        e.setDescription(b.description());
        e.setLocation(b.location());
        e.setStartAt(b.startAt());
        e.setEndAt(b.endAt());
        e.setAllDay(b.allDay());
        e.getVisibilities().clear();
        e.getVisibilities().addAll(selected);
        e.setVisibility(legacyVisibility(selected));
        e.setRecurrenceRule(
                b.recurrenceRule() == null || b.recurrenceRule().isBlank()
                        ? null
                        : normalizeRule(b.recurrenceRule(), b.startAt()));
        e.setReminderMinutes(
                b.reminderMinutes() == null || b.reminderMinutes() <= 0
                        ? null
                        : b.reminderMinutes());
        e.setColor(b.color() == null ? "#168CF2" : b.color());
        e.getGroups().clear();
        if (b.groupIds() != null)
            for (Long id : b.groupIds()) {
                if (!members.existsByGroupIdAndUserId(id, u.getId()))
                    throw new ApiException(HttpStatus.FORBIDDEN, "소속된 그룹에만 일정을 공개할 수 있습니다.");
                e.getGroups().add(groups.findById(id).orElseThrow());
            }
    }

    private Set<Event.Visibility> inputVisibilities(EventInput b) {
        if (b.visibilities() != null && !b.visibilities().isEmpty())
            return EnumSet.copyOf(b.visibilities());
        if (b.visibility() != null) return EnumSet.of(b.visibility());
        throw new ApiException(HttpStatus.BAD_REQUEST, "공개 범위를 하나 이상 선택해주세요.");
    }

    private Event.Visibility legacyVisibility(Set<Event.Visibility> selected) {
        if (selected.contains(Event.Visibility.PUBLIC)) return Event.Visibility.PUBLIC;
        if (selected.contains(Event.Visibility.GROUP)) return Event.Visibility.GROUP;
        if (selected.contains(Event.Visibility.FRIENDS)) return Event.Visibility.FRIENDS;
        return Event.Visibility.PRIVATE;
    }

    public static Set<Event.Visibility> effectiveVisibilities(Event e) {
        if (e.getVisibilities() != null && !e.getVisibilities().isEmpty())
            return EnumSet.copyOf(e.getVisibilities());
        return EnumSet.of(e.getVisibility() == null ? Event.Visibility.PRIVATE : e.getVisibility());
    }

    public static Map<String, Object> view(Event e, boolean details) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("title", details ? e.getTitle() : "바쁨");
        m.put("startAt", e.getStartAt());
        m.put("endAt", e.getEndAt());
        m.put("allDay", e.isAllDay());
        m.put("visibility", e.getVisibility());
        m.put("visibilities", effectiveVisibilities(e));
        m.put("color", e.getColor());
        m.put("owner", AuthController.publicUser(e.getOwner()));
        m.put("description", details && e.getDescription() != null ? e.getDescription() : "");
        m.put("location", details && e.getLocation() != null ? e.getLocation() : "");
        m.put("recurrenceRule", e.getRecurrenceRule() == null ? "" : e.getRecurrenceRule());
        m.put("reminderMinutes", e.getReminderMinutes() == null ? 0 : e.getReminderMinutes());
        m.put("publicToken", e.getPublicToken() == null ? "" : e.getPublicToken());
        m.put("groupIds", e.getGroups().stream().map(Group::getId).toList());
        return m;
    }

    public record EventRange(Long ownerId, OffsetDateTime startAt, OffsetDateTime endAt) {}

    public static List<Map<String, Object>> occurrenceViews(
            Event e, OffsetDateTime from, OffsetDateTime to) {
        return occurrenceRanges(e, from, to).stream()
                .map(
                        r -> {
                            Map<String, Object> m = new LinkedHashMap<>(view(e, true));
                            m.put("startAt", r.startAt());
                            m.put("endAt", r.endAt());
                            m.put("instanceStart", r.startAt());
                            m.put("seriesStartAt", e.getStartAt());
                            m.put("seriesEndAt", e.getEndAt());
                            return m;
                        })
                .toList();
    }

    public static List<EventRange> occurrenceRanges(
            Event e, OffsetDateTime from, OffsetDateTime to) {
        List<EventRange> out = new ArrayList<>();
        OffsetDateTime start = e.getStartAt(), end = e.getEndAt();
        String rule = e.getRecurrenceRule();
        if (rule == null || rule.isBlank()) {
            if (start.isBefore(to) && end.isAfter(from))
                out.add(new EventRange(e.getOwner().getId(), start, end));
            return out;
        }
        // UNTIL(UTC 시각)보다 늦게 시작하는 회차는 만들지 않는다. UNTIL 시각에 딱 맞게 시작하는 회차는 포함(RFC 5545).
        OffsetDateTime until = untilInstant(rule);
        int guard = 0;
        while (!end.isAfter(from) && guard++ < 5000) {
            start = next(start, rule);
            end = next(end, rule);
        }
        while (start.isBefore(to) && (until == null || !start.isAfter(until)) && guard++ < 5500) {
            if (end.isAfter(from)) out.add(new EventRange(e.getOwner().getId(), start, end));
            start = next(start, rule);
            end = next(end, rule);
        }
        return out;
    }

    private static final java.time.format.DateTimeFormatter UNTIL_UTC =
            java.time.format.DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'")
                    .withZone(ZoneOffset.UTC)
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT);

    // 날짜만 온 UNTIL(8자리, 예: 20261220)은 입력 startAt의 오프셋(작성자 기기의 로컬 오프셋) 기준 그날 23:59:59로 보고
    // RFC 5545 UTC 형식(UNTIL=20261220T145959Z)으로 바꿔 저장한다. DB·JVM 시간대와 상관없이 같은 순간을 가리키게 하려는 것.
    static String normalizeRule(String rule, OffsetDateTime startAt) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(?<=UNTIL=)([0-9]{8})(?=;|$)").matcher(rule);
        if (!m.find()) return rule;
        try {
            LocalDate day =
                    LocalDate.parse(m.group(1), java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            OffsetDateTime end = day.atTime(23, 59, 59).atOffset(startAt.getOffset());
            return m.replaceFirst(UNTIL_UTC.format(end));
        } catch (DateTimeException ex) {
            return rule;
        }
    }

    // "FREQ=WEEKLY;UNTIL=20261220T145959Z"의 UNTIL 시각(UTC). 없거나 형식이 맞지 않으면 null.
    static OffsetDateTime untilInstant(String rule) {
        if (rule == null) return null;
        for (String part : rule.split(";")) {
            if (!part.startsWith("UNTIL=")) continue;
            try {
                return OffsetDateTime.ofInstant(
                        Instant.from(UNTIL_UTC.parse(part.substring(6))), ZoneOffset.UTC);
            } catch (DateTimeException ex) {
                return null;
            }
        }
        return null;
    }

    private static OffsetDateTime next(OffsetDateTime value, String rule) {
        if (rule.contains("FREQ=DAILY")) return value.plusDays(1);
        if (rule.contains("FREQ=MONTHLY")) return value.plusMonths(1);
        return value.plusWeeks(1);
    }
}
