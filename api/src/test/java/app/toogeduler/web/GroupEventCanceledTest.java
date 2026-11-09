package app.toogeduler.web;

import static org.junit.jupiter.api.Assertions.*;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import app.toogeduler.security.JwtService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** 그룹 공개 일정을 "그룹 멤버에게 취소 알리기"로 지우면 다른 멤버에게만 한 번 취소 알림이 가고, 고르지 않으면 지금처럼 조용히 지운다. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:group-event-canceled;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "app.seed-demo=false"
        })
@AutoConfigureMockMvc
class GroupEventCanceledTest {
    private static final String START = "2026-11-05T19:00:00+09:00",
            END = "2026-11-05T21:00:00+09:00";

    @Autowired MockMvc mvc;
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
        owner = users.save(new User("owner-" + run + "@canceled.test", "김민지", "hash"));
        member = users.save(new User("member-" + run + "@canceled.test", "이준", "hash"));
        both = users.save(new User("both-" + run + "@canceled.test", "박소라", "hash"));
        hiking = group("주말 탐험대", owner, member, both);
        study = group("스터디", owner, both);
    }

    @Test
    void cancelingAGroupEventNotifiesOtherMembersOnceButNotTheOwner() throws Exception {
        Event event = groupEvent(Set.of(Event.Visibility.GROUP), hiking);

        assertEquals(204, delete(event, owner, "?announce=true"));

        assertTrue(events.findById(event.getId()).isEmpty());
        List<Notification> received = notifications.findByUserId(member.getId());
        assertEquals(1, received.size());
        Notification n = received.getFirst();
        assertEquals(Notification.Type.GROUP_EVENT, n.getType());
        assertEquals("그룹 일정이 취소됐어요", n.getTitle());
        // 시작 시각은 서울 시간으로 적는다(UTC로는 10:00).
        assertEquals("김민지님이 주말 탐험대의 11월 5일 (목) 오후 7:00 '주말 등산' 일정을 취소했어요.", n.getMessage());
        assertEquals("/?group=" + hiking.getId(), n.getLink());
        assertEquals(1, notifications.findByUserId(both.getId()).size());
        assertTrue(notifications.findByUserId(owner.getId()).isEmpty());
    }

    @Test
    void withoutAnnounceNothingIsSent() throws Exception {
        Event first = groupEvent(Set.of(Event.Visibility.GROUP), hiking);
        Event second = groupEvent(Set.of(Event.Visibility.GROUP), hiking);

        assertEquals(204, delete(first, owner, ""));
        assertEquals(204, delete(second, owner, "?announce=false"));

        assertTrue(events.findById(first.getId()).isEmpty());
        assertTrue(events.findById(second.getId()).isEmpty());
        assertTrue(notifications.findByUserId(member.getId()).isEmpty());
        assertTrue(notifications.findByUserId(both.getId()).isEmpty());
    }

    @Test
    void eventsNotSharedWithAGroupDoNotNotify() throws Exception {
        // 예전에 연결된 그룹이 남아 있어도 지금 GROUP 공개가 아니면 알리지 않는다.
        Event event = groupEvent(Set.of(Event.Visibility.FRIENDS), hiking);

        assertEquals(204, delete(event, owner, "?announce=true"));

        assertTrue(notifications.findByUserId(member.getId()).isEmpty());
        assertTrue(notifications.findByUserId(both.getId()).isEmpty());
    }

    @Test
    void someoneElsesEventIsRefusedAndNothingIsSent() throws Exception {
        Event event = groupEvent(Set.of(Event.Visibility.GROUP), hiking);

        assertEquals(403, delete(event, member, "?announce=true"));

        assertTrue(events.findById(event.getId()).isPresent());
        assertTrue(notifications.findByUserId(both.getId()).isEmpty());
        assertTrue(notifications.findByUserId(owner.getId()).isEmpty());
    }

    @Test
    void aMemberOfTwoSharedGroupsGetsOneNotification() throws Exception {
        Event event = groupEvent(Set.of(Event.Visibility.GROUP), hiking, study);

        assertEquals(204, delete(event, owner, "?announce=true"));

        List<Notification> received = notifications.findByUserId(both.getId());
        assertEquals(1, received.size());
        // 가장 먼저 만든 그룹 이름으로 받는다.
        assertTrue(received.getFirst().getMessage().contains("주말 탐험대의"));
        assertEquals("/?group=" + hiking.getId(), received.getFirst().getLink());
        assertEquals(1, notifications.findByUserId(member.getId()).size());
    }

    private int delete(Event event, User as, String query) throws Exception {
        return mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                        "/api/events/" + event.getId() + query)
                                .header("Authorization", "Bearer " + jwt.create(as.getId())))
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
        e.setVisibility(visibilities.iterator().next());
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
