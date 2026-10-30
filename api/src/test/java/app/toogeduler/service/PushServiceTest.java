package app.toogeduler.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.toogeduler.config.VapidKeys;
import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PushServiceTest {
    private static final VapidKeys KEYS =
            new VapidKeys("public", "private", "mailto:team@toogeduler.app");

    private PushSubscriptionRepository subscriptions;
    private PushSender sender;
    private User user;
    private PushSubscription phone;

    @BeforeEach
    void setUp() throws Exception {
        subscriptions = mock(PushSubscriptionRepository.class);
        sender = mock(PushSender.class);
        user = new User("push@toogeduler.app", "푸시", "hash");
        user.setId(1L);
        phone = new PushSubscription();
        phone.setId(5L);
        phone.setUser(user);
        phone.setEndpoint("https://fcm.googleapis.com/fcm/send/phone");
        when(subscriptions.findByUserId(1L)).thenReturn(List.of(phone));
        when(sender.send(any(), any())).thenReturn(201);
    }

    @Test
    void sendsOnlyTitleMessageAndUrl() throws Exception {
        service(KEYS).sendAfterCommit(user, "새 그룹 일정이 잡혔어요", "주말 모임", "/?group=1");

        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(sender).send(eq(phone), payload.capture());
        assertThat(new String(payload.getValue(), StandardCharsets.UTF_8))
                .isEqualTo(
                        "{\"title\":\"새 그룹 일정이 잡혔어요\",\"message\":\"주말 모임\",\"url\":\"/?group=1\"}");
    }

    @Test
    void goneSubscriptionIsDeleted() throws Exception {
        when(sender.send(any(), any())).thenReturn(410);

        service(KEYS).sendAfterCommit(user, "제목", "내용", "/");

        verify(subscriptions).deleteById(5L);
    }

    @Test
    void otherFailuresKeepTheSubscriptionAndDoNotThrow() throws Exception {
        when(sender.send(any(), any()))
                .thenReturn(500)
                .thenThrow(new java.io.IOException("timeout"));

        service(KEYS).sendAfterCommit(user, "제목", "내용", "/");
        service(KEYS).sendAfterCommit(user, "제목", "내용", "/");

        verify(subscriptions, never()).deleteById(any());
    }

    @Test
    void withoutVapidKeysTheNotificationIsSavedAndNothingIsSent() throws Exception {
        NotificationRepository notifications = mock(NotificationRepository.class);
        NotificationService notify =
                new NotificationService(
                        notifications,
                        mock(EventRepository.class),
                        mock(GroupMemberRepository.class),
                        service(new VapidKeys("", "", "")));

        notify.notify(user, Notification.Type.GROUP_EVENT, "제목", "내용", "/", "key");

        verify(notifications).save(any(Notification.class));
        verify(sender, never()).send(any(), any());
    }

    private PushService service(VapidKeys keys) {
        return new PushService(subscriptions, sender, keys, Runnable::run);
    }
}
