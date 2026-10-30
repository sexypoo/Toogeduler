package app.toogeduler.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import app.toogeduler.config.VapidKeys;
import app.toogeduler.domain.User;
import app.toogeduler.repo.PushSubscriptionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class PushControllerTest {
    private final UsernamePasswordAuthenticationToken auth = auth();

    @Test
    void publicKeyIsNotFoundWithoutVapidKeys() {
        PushController controller =
                new PushController(
                        mock(PushSubscriptionRepository.class), new VapidKeys("", "", ""));

        ApiException e = assertThrows(ApiException.class, () -> controller.publicKey(auth));

        assertThat(e.status).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void publicKeyIsReturnedWhenConfigured() {
        PushController controller =
                new PushController(
                        mock(PushSubscriptionRepository.class),
                        new VapidKeys("BPublicKey", "private", "mailto:team@toogeduler.app"));

        assertThat(controller.publicKey(auth)).containsEntry("publicKey", "BPublicKey");
    }

    /** 서버가 직접 요청을 보내는 주소라 알려진 푸시 서비스만 받는다. */
    @Test
    void onlyKnownPushServicesAreAccepted() {
        assertThat(PushController.isPushService("https://fcm.googleapis.com/fcm/send/abc"))
                .isTrue();
        assertThat(PushController.isPushService("https://web.push.apple.com/QW1")).isTrue();
        assertThat(
                        PushController.isPushService(
                                "https://updates.push.services.mozilla.com/wpush/v2/x"))
                .isTrue();
        assertThat(PushController.isPushService("http://fcm.googleapis.com/fcm/send/abc"))
                .isFalse();
        assertThat(PushController.isPushService("https://169.254.169.254/latest")).isFalse();
        assertThat(PushController.isPushService("https://fcm.googleapis.com.evil.test/x"))
                .isFalse();
    }

    private static UsernamePasswordAuthenticationToken auth() {
        User user = new User("push@example.com", "푸시", "hash");
        user.setId(1L);
        return new UsernamePasswordAuthenticationToken(user, null, List.of());
    }
}
