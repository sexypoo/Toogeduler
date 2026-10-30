package app.toogeduler.web;

import app.toogeduler.config.VapidKeys;
import app.toogeduler.domain.PushSubscription;
import app.toogeduler.domain.User;
import app.toogeduler.repo.PushSubscriptionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 웹 푸시 구독. 웹은 공개키를 받아 브라우저 구독을 만들고, 그 결과(PushSubscription.toJSON())를 그대로 등록한다. 로그인은 JWT 라 서버에 로그아웃
 * 단계가 없으므로, 웹이 로그아웃할 때 DELETE 로 이 브라우저의 구독을 지운다. 탈퇴 시에는 AccountService 가 모두 지운다.
 */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {
    /**
     * 서버가 이 주소로 직접 요청을 보내므로 아무 주소나 받으면 내부망을 두드리는 데 쓰일 수 있다. 주요 브라우저의 푸시 서비스만 허용한다(Chrome·Edge 계열의
     * FCM, Firefox, Safari, Windows).
     */
    private static final List<String> PUSH_HOSTS =
            List.of(
                    "fcm.googleapis.com",
                    "push.services.mozilla.com",
                    "push.apple.com",
                    "notify.windows.com");

    private final PushSubscriptionRepository subscriptions;
    private final VapidKeys vapid;

    public record Keys(
            @NotBlank @Size(max = 255) String p256dh, @NotBlank @Size(max = 255) String auth) {}

    public record Subscribe(
            @NotBlank @Size(max = 1000) String endpoint,
            @NotNull(message = "구독 정보가 올바르지 않습니다.") @Valid Keys keys) {}

    public record Unsubscribe(@NotBlank @Size(max = 1000) String endpoint) {}

    @GetMapping("/vapid-public-key")
    Map<String, String> publicKey(Authentication auth) {
        ApiSupport.user(auth);
        if (!vapid.configured())
            throw new ApiException(HttpStatus.NOT_FOUND, "웹 푸시 알림이 아직 준비되지 않았어요.");
        return Map.of("publicKey", vapid.getPublicKey());
    }

    /** 같은 브라우저(endpoint)가 다시 등록하면 키와 사용자를 갱신한다. 한 브라우저에서 계정을 바꿔 로그인한 경우도 마지막 사용자에게만 간다. */
    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void subscribe(Authentication auth, @Valid @RequestBody Subscribe body) {
        User user = ApiSupport.user(auth);
        if (!isPushService(body.endpoint())
                || decodedLength(body.keys().p256dh()) != 65
                || decodedLength(body.keys().auth()) != 16)
            throw new ApiException(HttpStatus.BAD_REQUEST, "구독 정보가 올바르지 않습니다.");
        PushSubscription s =
                subscriptions.findByEndpoint(body.endpoint()).orElseGet(PushSubscription::new);
        s.setUser(user);
        s.setEndpoint(body.endpoint());
        s.setP256dh(body.keys().p256dh());
        s.setAuth(body.keys().auth());
        subscriptions.save(s);
    }

    /** 본인 구독만 지운다. 다른 사람의 endpoint 이거나 없는 구독이어도 같은 응답을 준다. */
    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void unsubscribe(Authentication auth, @Valid @RequestBody Unsubscribe body) {
        subscriptions.deleteByUserIdAndEndpoint(ApiSupport.user(auth).getId(), body.endpoint());
    }

    static boolean isPushService(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            return "https".equals(uri.getScheme())
                    && host != null
                    && uri.getUserInfo() == null
                    && PUSH_HOSTS.stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int decodedLength(String base64url) {
        try {
            return Base64.getUrlDecoder().decode(base64url).length;
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }
}
