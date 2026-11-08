package app.toogeduler.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import app.toogeduler.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 그룹 공개 일정의 시간을 바꾸고 "바뀐 시간을 그룹 멤버에게 알리기"를 고르면 다른 멤버에게만, 시간이 실제로 바뀐 경우에만 한 번 알린다. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:group-event-moved;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "app.seed-demo=false"
        })
@AutoConfigureMockMvc
class GroupEventMovedTest {
    private static final String START = "2026-11-05T19:00:00+09:00",
            END = "2026-11-05T21:00:00+09:00",
            NEW_START = "2026-11-06T19:30:00+09:00",
            NEW_END = "2026-11-06T21:30:00+09:00";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired EventRepository events;
    @Autowired NotificationRepository notifications;

    private User owner, member, both;
    private Group hiking, study;
    private String run;

    @BeforeEach
    void setUp() {
        run = UUID.randomUUID().toString().substring(0, 8);
        owner = users.save(new User("owner-" + run + "@moved.test", "김민지", "hash"));
        member = users.save(new User("member-" + run + "@moved.test", "이준", "hash"));
        both = users.save(new User("both-" + run + "@moved.test", "박소라", "hash"));
        hiking = group("주말 탐험대", owner, member, both);
        study = group("스터디", owner, both);
    }

    @Test
    void movingAGroupEventNotifiesOtherMembersOnceButNotTheOwner() throws Exception {
        Event event = groupEvent(Set.of(Event.Visibility.GROUP), hiking);

        assertEquals(200, update(event, "주말 등산", NEW_START, NEW_END, true, hiking));

        List<Notification> received = notifications.findByUserId(member.getId());
        assertEquals(1, received.size());
        Notification n = received.getFirst();
        assertEquals(Notification.Type.GROUP_EVENT, n.getType());
        assertEquals("그룹 일정 시간이 바뀌었어요", n.getTitle());
        assertEquals("김민지님이 주말 탐험대의 '주말 등산' 일정을 11월 6일 (금) 오후 7:30으로 옮겼어요.", n.getMessage());
        assertEquals(
                "/?group=" + hiking.getId() + "&at=" + OffsetDateTime.parse(NEW_START).toInstant(),
                n.getLink());
        assertEquals(1, notifications.findByUserId(both.getId()).size());
        assertTrue(notifications.findByUserId(owner.getId()).isEmpty());
    }

    @Test
    void withoutAnnounceNothingIsSent() throws Exception {
        Event event = groupEvent(Set.of(Event.Visibility.GROUP), hiking);

        assertEquals(200, update(event, "주말 등산", NEW_START, NEW_END, false, hiking));
        assertEquals(200, update(event, "주말 등산", START, END, null, hiking));

        assertTrue(notifications.findByUserId(member.getId()).isEmpty());
    }

    @Test
    void changingOnlyTheTitleDoesNotNotify() throws Exception {
        Event event = groupEvent(Set.of(Event.Visibility.GROUP), hiking);

        // 같은 순간을 다른 오프셋으로 보내도 시간이 바뀐 것이 아니다.
        assertEquals(200, update(event, "제목만 바꿈", START, END, true, hiking));
        assertEquals(
                200,
                update(
                        event,
                        "제목만 또 바꿈",
                        "2026-11-05T10:00:00Z",
                        "2026-11-05T12:00:00Z",
                        true,
                        hiking));

        assertTrue(notifications.findByUserId(member.getId()).isEmpty());
    }

    @Test
    void eventsNotSharedWithAGroupDoNotNotify() throws Exception {
        Event event = groupEvent(Set.of(Event.Visibility.GROUP), hiking);

        assertEquals(
                200,
                update(
                        event,
                        "친구 공개로 바꿈",
                        NEW_START,
                        NEW_END,
                        true,
                        Set.of(Event.Visibility.FRIENDS)));

        assertTrue(notifications.findByUserId(member.getId()).isEmpty());
    }

    @Test
    void savingTheSameNewTimeTwiceNotifiesOnceAndAnotherMoveNotifiesAgain() throws Exception {
        Event event = groupEvent(Set.of(Event.Visibility.GROUP), hiking);

        assertEquals(200, update(event, "주말 등산", NEW_START, NEW_END, true, hiking));
        assertEquals(200, update(event, "주말 등산", NEW_START, NEW_END, true, hiking));
        // 저장된 값과 같은 시각이라 서버는 바뀐 것으로 보지 않는다. 먼저 원래 시각으로 되돌린 뒤 다시 같은 시각으로 옮겨도 1개다.
        assertEquals(200, update(event, "주말 등산", START, END, false, hiking));
        assertEquals(200, update(event, "주말 등산", NEW_START, NEW_END, true, hiking));
        assertEquals(1, notifications.findByUserId(member.getId()).size());

        assertEquals(
                200,
                update(
                        event,
                        "주말 등산",
                        "2026-11-07T10:00:00+09:00",
                        "2026-11-07T12:00:00+09:00",
                        true,
                        hiking));
        assertEquals(2, notifications.findByUserId(member.getId()).size());
    }

    @Test
    void aMemberOfTwoSharedGroupsGetsOneNotification() throws Exception {
        Event event = groupEvent(Set.of(Event.Visibility.GROUP), hiking, study);

        assertEquals(200, update(event, "주말 등산", NEW_START, NEW_END, true, hiking, study));

        List<Notification> received = notifications.findByUserId(both.getId());
        assertEquals(1, received.size());
        // 가장 먼저 만든 그룹 이름으로 받는다.
        assertTrue(received.getFirst().getMessage().contains("주말 탐험대의"));
        assertEquals(1, notifications.findByUserId(member.getId()).size());
    }

    private int update(
            Event event, String title, String start, String end, Boolean announce, Group... shared)
            throws Exception {
        return update(event, title, start, end, announce, Set.of(Event.Visibility.GROUP), shared);
    }

    private int update(
            Event event,
            String title,
            String start,
            String end,
            Boolean announce,
            Set<Event.Visibility> visibilities,
            Group... shared)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("startAt", start);
        body.put("endAt", end);
        body.put("allDay", false);
        body.put("visibilities", visibilities);
        body.put("recurrenceRule", "");
        body.put("color", "#168CF2");
        body.put("groupIds", Arrays.stream(shared).map(Group::getId).toList());
        if (announce != null) body.put("announce", announce);
        return mvc.perform(
                        put("/api/events/" + event.getId())
                                .header("Authorization", "Bearer " + jwt.create(owner.getId()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(body)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private Event groupEvent(Set<Event.Visibility> visibilities, Group... shared) {
        Event e = new Event();
        e.setOwner(owner);
        e.setTitle("주말 등산");
        e.setStartAt(OffsetDateTime.parse(START));
        e.setEndAt(OffsetDateTime.parse(END));
        e.getVisibilities().addAll(visibilities);
        e.setVisibility(Event.Visibility.GROUP);
        e.setColor("#168CF2");
        e.setPublicToken(UUID.randomUUID().toString());
        e.getGroups().addAll(List.of(shared));
        return events.save(e);
    }

    private Group group(String name, User... people) {
        Group g = new Group();
        g.setName(name);
        g.setColor("#19B7B1");
        g.setOwner(owner);
        g.renewInvite(name.hashCode() + "-" + run, Instant.now());
        g = groups.save(g);
        for (User u : people) {
            GroupMember m = new GroupMember();
            m.setGroup(g);
            m.setUser(u);
            m.setRole(u == owner ? GroupMember.Role.OWNER : GroupMember.Role.MEMBER);
            members.save(m);
        }
        return g;
    }
}
