package app.toogeduler.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import app.toogeduler.domain.Event;
import app.toogeduler.domain.Group;
import app.toogeduler.domain.User;
import app.toogeduler.repo.*;
import jakarta.validation.Validation;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class EventControllerTest {
    private EventRepository events;
    private GroupRepository groups;
    private GroupMemberRepository members;
    private EventController controller;
    private User owner;
    private User otherUser;
    private Event event;

    @BeforeEach
    void setUp() {
        events = mock(EventRepository.class);
        groups = mock(GroupRepository.class);
        members = mock(GroupMemberRepository.class);
        controller =
                new EventController(
                        events,
                        groups,
                        members,
                        mock(app.toogeduler.service.NotificationService.class));
        owner = user(1L, "owner@toogeduler.app", "일정 주인");
        otherUser = user(2L, "other@toogeduler.app", "다른 사용자");
        event = new Event();
        event.setId(10L);
        event.setOwner(owner);
        event.setTitle("내 일정");
        event.setStartAt(OffsetDateTime.parse("2026-08-03T10:00:00+09:00"));
        event.setEndAt(OffsetDateTime.parse("2026-08-03T11:00:00+09:00"));
        when(events.findById(10L)).thenReturn(Optional.of(event));
    }

    @Test
    void publicEventDoesNotExposeOwnerEmailOrFriendCode() {
        // /api/public/events 는 로그인 없이 열려 있으므로 작성자 정보에 연락처가 실리면 안 된다.
        event.getVisibilities().add(Event.Visibility.PUBLIC);
        event.setPublicToken("public-token");
        when(events.findByPublicToken("public-token")).thenReturn(Optional.of(event));
        @SuppressWarnings("unchecked")
        Map<String, Object> publicOwner =
                (Map<String, Object>) controller.publicEvent("public-token").get("owner");
        assertEquals(Set.of("id", "name", "avatarUrl"), publicOwner.keySet());
    }

    @Test
    void anotherUserCannotMoveEvent() {
        ApiException error =
                assertThrows(
                        ApiException.class,
                        () ->
                                controller.move(
                                        auth(otherUser),
                                        10L,
                                        Map.of(
                                                "startAt",
                                                "2026-08-03T12:00:00+09:00",
                                                "endAt",
                                                "2026-08-03T13:00:00+09:00")));
        assertEquals(HttpStatus.FORBIDDEN, error.status);
        verify(events, never()).save(any());
    }

    @Test
    void anotherUserCannotDeleteEvent() {
        ApiException error =
                assertThrows(
                        ApiException.class, () -> controller.delete(auth(otherUser), 10L, false));
        assertEquals(HttpStatus.FORBIDDEN, error.status);
        verify(events, never()).delete(any());
    }

    @Test
    void anotherUserCannotUpdateEvent() {
        EventController.EventInput input =
                new EventController.EventInput(
                        "바꾼 제목",
                        "",
                        "",
                        event.getStartAt(),
                        event.getEndAt(),
                        false,
                        Set.of(Event.Visibility.PRIVATE),
                        null,
                        "",
                        null,
                        "#168CF2",
                        Set.of(),
                        null);
        ApiException error =
                assertThrows(
                        ApiException.class, () -> controller.update(auth(otherUser), 10L, input));
        assertEquals(HttpStatus.FORBIDDEN, error.status);
        verify(events, never()).save(any());
    }

    @Test
    void eventCanBeSharedWithGroupAndFriendsAtTheSameTime() {
        Group group = new Group();
        group.setId(7L);
        group.setName("여행 친구");
        group.setOwner(owner);
        group.setInviteToken("invite");
        when(members.existsByGroupIdAndUserId(7L, 1L)).thenReturn(true);
        when(groups.findById(7L)).thenReturn(Optional.of(group));
        when(events.save(any(Event.class)))
                .thenAnswer(
                        invocation -> {
                            Event saved = invocation.getArgument(0);
                            saved.setId(20L);
                            return saved;
                        });
        EventController.EventInput input =
                new EventController.EventInput(
                        "공유 일정",
                        "",
                        "",
                        OffsetDateTime.parse("2026-08-04T10:00:00+09:00"),
                        OffsetDateTime.parse("2026-08-04T11:00:00+09:00"),
                        false,
                        EnumSet.of(Event.Visibility.GROUP, Event.Visibility.FRIENDS),
                        null,
                        "",
                        30,
                        "#168CF2",
                        Set.of(7L),
                        null);

        Map<String, Object> result = controller.create(auth(owner), input);

        assertEquals(
                Set.of(Event.Visibility.GROUP, Event.Visibility.FRIENDS),
                new HashSet<>((Collection<Event.Visibility>) result.get("visibilities")));
        Event saved = verifyAndGetSavedEvent();
        assertTrue(saved.getGroups().contains(group));
        assertEquals(30, saved.getReminderMinutes());
    }

    @Test
    void weeklyRuleWithUntilStopsAfterTheUntilDate() {
        // 8월 3일(월)부터 매주, 끝나는 날은 2주 뒤 8월 17일(한국 시간 그날 끝): 3개월 범위에서도 3회만 나온다.
        event.setRecurrenceRule("FREQ=WEEKLY;UNTIL=20260817T145959Z");
        List<EventController.EventRange> ranges =
                EventController.occurrenceRanges(
                        event,
                        OffsetDateTime.parse("2026-08-01T00:00:00+09:00"),
                        OffsetDateTime.parse("2026-11-01T00:00:00+09:00"));
        assertEquals(
                List.of(
                        OffsetDateTime.parse("2026-08-03T10:00:00+09:00"),
                        OffsetDateTime.parse("2026-08-10T10:00:00+09:00"),
                        OffsetDateTime.parse("2026-08-17T10:00:00+09:00")),
                ranges.stream().map(EventController.EventRange::startAt).toList());
    }

    @Test
    void untilDateIsTheEndOfThatDayWhereTheEventWasWrittenEvenWhenStoredInUtc() {
        // 12월 18일부터 매일, 끝나는 날 12월 20일. DB 에서 읽으면 시작 시각은 Z 오프셋으로 온다.
        OffsetDateTime from = OffsetDateTime.parse("2026-12-01T00:00:00Z"),
                to = OffsetDateTime.parse("2027-01-01T00:00:00Z");
        // 한국 시간 매일 오전 8시: 18·19·20일 3회 (21일 오전 8시는 나오지 않는다).
        OffsetDateTime seoul = OffsetDateTime.parse("2026-12-18T08:00:00+09:00");
        String seoulRule = EventController.normalizeRule("FREQ=DAILY;UNTIL=20261220", seoul);
        assertEquals("FREQ=DAILY;UNTIL=20261220T145959Z", seoulRule);
        stored(seoul, seoulRule);
        List<EventController.EventRange> ranges = EventController.occurrenceRanges(event, from, to);
        assertEquals(3, ranges.size());
        assertEquals(OffsetDateTime.parse("2026-12-19T23:00:00Z"), ranges.getLast().startAt());
        // 태평양 표준시 매일 저녁 8시: UTC 로는 다음 날이지만 끝나는 날 당일(20일) 회차까지 3회.
        OffsetDateTime pacific = OffsetDateTime.parse("2026-12-18T20:00:00-08:00");
        stored(pacific, EventController.normalizeRule("FREQ=DAILY;UNTIL=20261220", pacific));
        ranges = EventController.occurrenceRanges(event, from, to);
        assertEquals(3, ranges.size());
        assertEquals(OffsetDateTime.parse("2026-12-21T04:00:00Z"), ranges.getLast().startAt());
    }

    // 저장 후 DB(timestamptz)에서 다시 읽은 것처럼 시작·종료 시각을 UTC 로 둔다.
    private void stored(OffsetDateTime start, String rule) {
        event.setStartAt(start.withOffsetSameInstant(java.time.ZoneOffset.UTC));
        event.setEndAt(start.plusHours(1).withOffsetSameInstant(java.time.ZoneOffset.UTC));
        event.setRecurrenceRule(rule);
    }

    @Test
    void rulesWithoutUntilKeepRepeatingAsBefore() {
        OffsetDateTime from = OffsetDateTime.parse("2026-08-01T00:00:00+09:00"),
                to = OffsetDateTime.parse("2026-11-01T00:00:00+09:00");
        event.setRecurrenceRule("FREQ=WEEKLY");
        assertEquals(13, EventController.occurrenceRanges(event, from, to).size());
        event.setRecurrenceRule("FREQ=DAILY");
        assertEquals(90, EventController.occurrenceRanges(event, from, to).size());
        event.setRecurrenceRule("FREQ=MONTHLY");
        assertEquals(3, EventController.occurrenceRanges(event, from, to).size());
    }

    @Test
    void endingASeriesBeforeAnOccurrenceHidesThatOccurrenceAndLaterOnes() {
        // 11월 4일(수) 오전 10시부터 매주. 웹의 "이 일정부터 이후 모두 삭제"는 시리즈 시작·끝은 그대로 두고
        // UNTIL 만 11월 18일 회차 1초 전(UTC)으로 바꿔 PUT 한다.
        OffsetDateTime seriesStart = OffsetDateTime.parse("2026-11-04T10:00:00+09:00");
        event.setStartAt(seriesStart);
        event.setEndAt(seriesStart.plusHours(1));
        event.setRecurrenceRule("FREQ=WEEKLY");
        when(events.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        controller.update(
                auth(owner),
                10L,
                new EventController.EventInput(
                        "내 일정",
                        "",
                        "",
                        seriesStart,
                        seriesStart.plusHours(1),
                        false,
                        Set.of(Event.Visibility.PRIVATE),
                        null,
                        "FREQ=WEEKLY;UNTIL=20261118T005959Z",
                        null,
                        "#168CF2",
                        Set.of(),
                        null));
        OffsetDateTime from = OffsetDateTime.parse("2026-11-01T00:00:00+09:00"),
                to = OffsetDateTime.parse("2026-12-31T00:00:00+09:00");
        when(events.ownedCalendar(1L, from, to)).thenReturn(List.of(event));
        assertEquals(
                List.of(
                        OffsetDateTime.parse("2026-11-04T10:00:00+09:00"),
                        OffsetDateTime.parse("2026-11-11T10:00:00+09:00")),
                controller.calendar(auth(owner), from, to).stream()
                        .map(m -> (OffsetDateTime) m.get("instanceStart"))
                        .toList());
    }

    @Test
    void recurrenceRuleFormatIsValidated() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertFalse(validator.validate(input("FREQ=YEARLY")).isEmpty());
        assertFalse(validator.validate(input("FREQ=WEEKLY;UNTIL=2026-12-20")).isEmpty());
        assertFalse(validator.validate(input("FREQ=WEEKLY;COUNT=3")).isEmpty());
        assertFalse(validator.validate(input("FREQ=WEEKLY;UNTIL=20261220T145959")).isEmpty());
        assertTrue(validator.validate(input("FREQ=WEEKLY;UNTIL=20261220")).isEmpty());
        assertTrue(validator.validate(input("FREQ=WEEKLY;UNTIL=20261220T145959Z")).isEmpty());
        assertTrue(validator.validate(input("FREQ=DAILY")).isEmpty());
        assertTrue(validator.validate(input("")).isEmpty());
    }

    @Test
    void untilBeforeTheStartDateOrNotARealDateIsRejected() {
        // 일정 시작은 8월 4일 오후 7시(한국 시간). 같은 날은 허용(그 한 번만), 하루 전·시작 전 시각·없는 날짜는 400.
        for (String rule :
                List.of(
                        "FREQ=WEEKLY;UNTIL=20260803",
                        "FREQ=WEEKLY;UNTIL=20260804T095959Z",
                        "FREQ=WEEKLY;UNTIL=20261340")) {
            ApiException error =
                    assertThrows(
                            ApiException.class, () -> controller.create(auth(owner), input(rule)));
            assertEquals(HttpStatus.BAD_REQUEST, error.status);
        }
        verify(events, never()).save(any());
        when(events.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        controller.create(auth(owner), input("FREQ=WEEKLY;UNTIL=20260804"));
        // 날짜만 온 UNTIL 은 시작 시각의 오프셋(+09:00) 기준 그날 끝, UTC 형식으로 저장된다.
        assertEquals(
                "FREQ=WEEKLY;UNTIL=20260804T145959Z", verifyAndGetSavedEvent().getRecurrenceRule());
    }

    private EventController.EventInput input(String recurrenceRule) {
        return new EventController.EventInput(
                "스터디",
                "",
                "",
                OffsetDateTime.parse("2026-08-04T19:00:00+09:00"),
                OffsetDateTime.parse("2026-08-04T20:00:00+09:00"),
                false,
                Set.of(Event.Visibility.PRIVATE),
                null,
                recurrenceRule,
                null,
                "#168CF2",
                Set.of(),
                null);
    }

    private Event verifyAndGetSavedEvent() {
        var captor = org.mockito.ArgumentCaptor.forClass(Event.class);
        verify(events).save(captor.capture());
        return captor.getValue();
    }

    private Authentication auth(User user) {
        return new UsernamePasswordAuthenticationToken(user, null, List.of());
    }

    private User user(Long id, String email, String name) {
        User user = new User(email, name, "hash");
        user.setId(id);
        return user;
    }
}
