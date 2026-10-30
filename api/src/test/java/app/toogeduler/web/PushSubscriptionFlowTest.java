package app.toogeduler.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import app.toogeduler.config.VapidKeys;
import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import app.toogeduler.service.NotificationService;
import app.toogeduler.service.PushSender;
import app.toogeduler.service.PushService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 구독 등록부터 그룹 일정 알림 발송까지 실제 DB(H2 + Flyway)로 확인한다. 발송은 알림이 커밋된 뒤에 일어나므로 테스트 자체는 트랜잭션 없이 돌린다. */
@DataJpaTest(
        properties = {
            "app.vapid.public-key=test-public",
            "app.vapid.private-key=test-private",
            "app.vapid.subject=mailto:team@toogeduler.app"
        })
@Import({
    PushController.class,
    NotificationService.class,
    VapidKeys.class,
    PushSubscriptionFlowTest.Beans.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PushSubscriptionFlowTest {
    private static final String P256DH =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

    @TestConfiguration
    static class Beans {
        @Bean
        PushSender pushSender() {
            return mock(PushSender.class);
        }

        @Bean
        PushService pushService(
                PushSubscriptionRepository subscriptions, PushSender sender, VapidKeys vapid) {
            return new PushService(subscriptions, sender, vapid, Runnable::run);
        }
    }

    @Autowired PushController controller;
    @Autowired NotificationService notifications;
    @Autowired PushSender sender;
    @Autowired PushSubscriptionRepository subscriptions;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;

    @BeforeEach
    void setUp() throws Exception {
        reset(sender);
        when(sender.send(any(), any())).thenReturn(201);
    }

    @Test
    void groupEventIsPushedOnceToTheMembersSubscriptionOnly() throws Exception {
        User owner = user("owner"), member = user("member"), outsider = user("outsider");
        Group group = group(owner, member);
        String memberPhone = endpoint(), outsiderPhone = endpoint();
        controller.subscribe(auth(member), subscribe(memberPhone));
        controller.subscribe(auth(outsider), subscribe(outsiderPhone));

        Event event = new Event();
        event.setId(990L);
        event.setOwner(owner);
        event.setTitle("주말 모임");
        event.setStartAt(OffsetDateTime.parse("2026-11-07T01:30:00Z"));
        event.setEndAt(OffsetDateTime.parse("2026-11-07T02:30:00Z"));
        event.getGroups().add(group);
        notifications.announceGroupEvent(event);

        verify(sender, times(1)).send(argThat(s -> s.getEndpoint().equals(memberPhone)), any());
        verify(sender, times(1)).send(any(), any());
    }

    @Test
    void anotherUsersSubscriptionCannotBeDeleted() {
        User owner = user("mine"), other = user("other");
        String phone = endpoint();
        controller.subscribe(auth(owner), subscribe(phone));

        controller.unsubscribe(auth(other), new PushController.Unsubscribe(phone));
        assertThat(subscriptions.findByEndpoint(phone)).isPresent();

        controller.unsubscribe(auth(owner), new PushController.Unsubscribe(phone));
        assertThat(subscriptions.findByEndpoint(phone)).isEmpty();
    }

    @Test
    void subscribingTheSameBrowserAgainUpdatesTheRow() {
        User first = user("first"), second = user("second");
        String phone = endpoint();
        controller.subscribe(auth(first), subscribe(phone));
        controller.subscribe(auth(second), subscribe(phone));

        assertThat(subscriptions.findByUserId(first.getId())).isEmpty();
        assertThat(subscriptions.findByUserId(second.getId())).hasSize(1);
    }

    private User user(String name) {
        return users.save(new User(name + "-" + UUID.randomUUID() + "@example.com", name, null));
    }

    private Group group(User owner, User member) {
        Group group = new Group();
        group.setName("주말 탐험대");
        group.setOwner(owner);
        group.setInviteToken(UUID.randomUUID().toString());
        group = groups.save(group);
        for (User u : List.of(owner, member)) {
            GroupMember m = new GroupMember();
            m.setGroup(group);
            m.setUser(u);
            members.save(m);
        }
        return group;
    }

    private static String endpoint() {
        return "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
    }

    private static PushController.Subscribe subscribe(String endpoint) {
        return new PushController.Subscribe(endpoint, new PushController.Keys(P256DH, AUTH));
    }

    private static Authentication auth(User user) {
        return new UsernamePasswordAuthenticationToken(user, null, List.of());
    }
}
