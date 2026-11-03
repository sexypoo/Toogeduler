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
                assertThrows(ApiException.class, () -> controller.delete(auth(otherUser), 10L));
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
        // 8월 3일(월)부터 매주, 끝나는 날은 2주 뒤 8월 17일: 3개월 범위에서도 3회만 나온다.
        event.setRecurrenceRule("FREQ=WEEKLY;UNTIL=20260817");
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
    void untilIsReadAtTheEndOfThatDayInTheEventsOwnOffset() {
        // 밤 11시(한국 시간) 일정은 UTC로는 전날이지만, 끝나는 날 당일 회차는 포함된다.
        event.setStartAt(OffsetDateTime.parse("2026-08-03T23:00:00+09:00"));
        event.setEndAt(OffsetDateTime.parse("2026-08-03T23:30:00+09:00"));
        event.setRecurrenceRule("FREQ=DAILY;UNTIL=20260805");
        List<EventController.EventRange> ranges =
                EventController.occurrenceRanges(
                        event,
                        OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                        OffsetDateTime.parse("2026-09-01T00:00:00Z"));
        assertEquals(3, ranges.size());
        assertEquals(OffsetDateTime.parse("2026-08-05T23:00:00+09:00"), ranges.getLast().startAt());
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
    void recurrenceRuleFormatIsValidated() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertFalse(validator.validate(input("FREQ=YEARLY")).isEmpty());
        assertFalse(validator.validate(input("FREQ=WEEKLY;UNTIL=2026-12-20")).isEmpty());
        assertFalse(validator.validate(input("FREQ=WEEKLY;COUNT=3")).isEmpty());
        assertTrue(validator.validate(input("FREQ=WEEKLY;UNTIL=20261220")).isEmpty());
        assertTrue(validator.validate(input("FREQ=DAILY")).isEmpty());
        assertTrue(validator.validate(input("")).isEmpty());
    }

    @Test
    void untilBeforeTheStartDateOrNotARealDateIsRejected() {
        // 일정 시작일은 8월 4일. 같은 날은 허용(그 한 번만), 하루 전·없는 날짜는 400.
        for (String rule : List.of("FREQ=WEEKLY;UNTIL=20260803", "FREQ=WEEKLY;UNTIL=20261340")) {
            ApiException error =
                    assertThrows(
                            ApiException.class, () -> controller.create(auth(owner), input(rule)));
            assertEquals(HttpStatus.BAD_REQUEST, error.status);
        }
        verify(events, never()).save(any());
        when(events.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        controller.create(auth(owner), input("FREQ=WEEKLY;UNTIL=20260804"));
        assertEquals("FREQ=WEEKLY;UNTIL=20260804", verifyAndGetSavedEvent().getRecurrenceRule());
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
