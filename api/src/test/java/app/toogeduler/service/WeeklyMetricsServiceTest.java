package app.toogeduler.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@ExtendWith(OutputCaptureExtension.class)
class WeeklyMetricsServiceTest {
    /** 2026-10-26 월요일 09:00 서울 = 00:00 UTC. */
    static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");

    static final Instant INSIDE = NOW.minus(Duration.ofDays(7)).plusSeconds(1);
    static final Instant OUTSIDE = NOW.minus(Duration.ofDays(7)).minusSeconds(1);

    @Autowired TestEntityManager em;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired EventRepository events;

    WeeklyMetricsService service;

    @BeforeEach
    void setUp() {
        service =
                new WeeklyMetricsService(
                        users, groups, members, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void countsOnlyWhatHappenedInsideTheSevenDayWindow() {
        User oldOwner = user("old@example.com", "예전 그룹장", OUTSIDE);
        User newOwner = user("new@example.com", "새 그룹장", INSIDE);
        User oldJoiner = user("old-joiner@example.com", "예전 멤버", OUTSIDE);
        User newJoiner = user("new-joiner@example.com", "새 멤버", INSIDE);

        Group oldGroup = group(oldOwner, "예전 그룹", "t-old", OUTSIDE);
        member(oldGroup, oldOwner, GroupMember.Role.OWNER, OUTSIDE);
        member(oldGroup, oldJoiner, GroupMember.Role.MEMBER, OUTSIDE);
        member(oldGroup, newJoiner, GroupMember.Role.MEMBER, INSIDE);

        Group newGroup = group(newOwner, "새 그룹", "t-new", INSIDE);
        member(newGroup, newOwner, GroupMember.Role.OWNER, INSIDE);
        member(newGroup, oldJoiner, GroupMember.Role.MEMBER, INSIDE);

        event(oldOwner, "예전 그룹 일정", OUTSIDE, oldGroup);
        event(newOwner, "새 그룹 일정", INSIDE, newGroup);
        event(newJoiner, "두 그룹 함께", INSIDE, oldGroup, newGroup);
        // 정확히 now에 만든 것은 다음 주 집계로 넘어간다.
        event(newOwner, "경계 일정", NOW, newGroup);
        em.flush();

        WeeklyMetricsService.WeeklyMetrics m = service.compute(NOW);

        assertThat(m.from()).isEqualTo(NOW.minus(Duration.ofDays(7)));
        assertThat(m.to()).isEqualTo(NOW);
        assertThat(m.newUsers()).isEqualTo(2);
        assertThat(m.newGroups()).isEqualTo(1);
        assertThat(m.joins()).isEqualTo(2);
        assertThat(m.groupEvents()).isEqualTo(2);
        assertThat(m.activeGroups()).isEqualTo(2);
    }

    @Test
    void personalEventsAreNotGroupEvents() {
        User owner = user("solo@example.com", "혼자", INSIDE);
        Group group = group(owner, "그룹", "t-solo", INSIDE);
        member(group, owner, GroupMember.Role.OWNER, INSIDE);
        event(owner, "개인 일정", INSIDE);
        em.flush();

        WeeklyMetricsService.WeeklyMetrics m = service.compute(NOW);

        assertThat(m.groupEvents()).isZero();
        assertThat(m.activeGroups()).isZero();
    }

    @Test
    void ownersAutomaticMembershipIsNotAJoin() {
        User owner = user("owner@example.com", "그룹장", INSIDE);
        Group group = group(owner, "그룹", "t-owner", INSIDE);
        member(group, owner, GroupMember.Role.OWNER, INSIDE);
        em.flush();

        WeeklyMetricsService.WeeklyMetrics m = service.compute(NOW);

        assertThat(m.newGroups()).isEqualTo(1);
        assertThat(m.joins()).isZero();
    }

    @Test
    void logLineHasNumbersButNoPersonalInformation(CapturedOutput output) {
        User owner = user("secret.person@example.com", "홍길동", INSIDE);
        Group group = group(owner, "비밀 모임", "t-pii", INSIDE);
        member(group, owner, GroupMember.Role.OWNER, INSIDE);
        event(owner, "병원 진료 예약", INSIDE, group);
        em.flush();

        service.logWeeklyMetrics();

        String line = service.compute(NOW).toLogLine();
        assertThat(line)
                .isEqualTo(
                        "weekly-metrics from=2026-10-19T00:00:00Z to=2026-10-26T00:00:00Z"
                                + " newUsers=1 newGroups=1 joins=0 groupEvents=1 activeGroups=1");
        assertThat(output.getOut()).contains(line);
        for (String pii : new String[] {"secret.person@example.com", "홍길동", "비밀 모임", "병원 진료 예약"}) {
            assertThat(line).doesNotContain(pii);
            assertThat(output.getOut()).doesNotContain(pii);
        }
    }

    private User user(String email, String name, Instant createdAt) {
        User u = new User(email, name, "hash");
        u.setCreatedAt(createdAt);
        return em.persist(u);
    }

    private Group group(User owner, String name, String token, Instant createdAt) {
        Group g = new Group();
        g.setName(name);
        g.setOwner(owner);
        g.setInviteToken(token);
        g.setCreatedAt(createdAt);
        return em.persist(g);
    }

    private void member(Group group, User user, GroupMember.Role role, Instant joinedAt) {
        GroupMember m = new GroupMember();
        m.setGroup(group);
        m.setUser(user);
        m.setRole(role);
        m.setJoinedAt(joinedAt);
        em.persist(m);
    }

    private void event(User owner, String title, Instant createdAt, Group... linked) {
        Event e = new Event();
        e.setOwner(owner);
        e.setTitle(title);
        OffsetDateTime start = OffsetDateTime.of(2026, 10, 30, 10, 0, 0, 0, ZoneOffset.UTC);
        e.setStartAt(start);
        e.setEndAt(start.plusHours(1));
        e.setCreatedAt(createdAt);
        for (Group g : linked) e.getGroups().add(g);
        em.persist(e);
    }
}
