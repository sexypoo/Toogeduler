package app.toogeduler.service;

import app.toogeduler.config.VapidKeys;
import app.toogeduler.domain.PushSubscription;
import com.zerodeplibs.webpush.VAPIDKeyPair;
import com.zerodeplibs.webpush.VAPIDKeyPairs;
import com.zerodeplibs.webpush.httpclient.StandardHttpClientRequestPreparer;
import com.zerodeplibs.webpush.key.PrivateKeySources;
import com.zerodeplibs.webpush.key.PublicKeySources;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * 웹 푸시 발송기. 내용 암호화(RFC 8291)와 발신자 인증(VAPID, RFC 8292)은 zerodep web-push 라이브러리가 하고, 여기서는 키를 읽어 요청을
 * 만들고 JDK HttpClient 로 보낸다.
 */
@Component
public class WebPushSender implements PushSender {
    /** 브라우저가 꺼져 있을 때 푸시 서비스가 알림을 보관하는 시간. 하루가 지난 일정 알림은 의미가 없다. */
    private static final long TTL_HOURS = 24;

    private final VapidKeys vapid;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private VAPIDKeyPair keyPair;

    public WebPushSender(VapidKeys vapid) {
        this.vapid = vapid;
    }

    @Override
    public int send(PushSubscription subscription, byte[] payload) throws Exception {
        return http.send(request(subscription, payload), HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    HttpRequest request(PushSubscription subscription, byte[] payload)
            throws GeneralSecurityException {
        com.zerodeplibs.webpush.PushSubscription.Keys keys =
                new com.zerodeplibs.webpush.PushSubscription.Keys();
        keys.setP256dh(subscription.getP256dh());
        keys.setAuth(subscription.getAuth());
        com.zerodeplibs.webpush.PushSubscription target =
                new com.zerodeplibs.webpush.PushSubscription();
        target.setEndpoint(subscription.getEndpoint());
        target.setKeys(keys);

        return StandardHttpClientRequestPreparer.getBuilder()
                .pushSubscription(target)
                .vapidJWTSubject(vapid.getSubject())
                .vapidJWTExpiresAfter(12, TimeUnit.HOURS)
                .pushMessage(payload)
                .ttl(TTL_HOURS, TimeUnit.HOURS)
                .build(keyPair())
                .toRequestBuilder()
                .timeout(Duration.ofSeconds(10))
                .build();
    }

    /** 환경변수의 키는 웹 푸시 도구들이 내놓는 형식(공개키 65바이트 비압축 점, 개인키 32바이트 값, 둘 다 base64url)이다. */
    private synchronized VAPIDKeyPair keyPair() throws GeneralSecurityException {
        if (keyPair == null) {
            keyPair =
                    VAPIDKeyPairs.of(
                            PrivateKeySources.ofECPrivateKey(privateKey(vapid.getPrivateKey())),
                            PublicKeySources.ofUncompressedBytes(
                                    Base64.getUrlDecoder().decode(vapid.getPublicKey())));
        }
        return keyPair;
    }

    private static ECPrivateKey privateKey(String base64url) throws GeneralSecurityException {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        BigInteger d = new BigInteger(1, Base64.getUrlDecoder().decode(base64url));
        return (ECPrivateKey)
                KeyFactory.getInstance("EC")
                        .generatePrivate(
                                new ECPrivateKeySpec(
                                        d, params.getParameterSpec(ECParameterSpec.class)));
    }
}
