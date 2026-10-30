package app.toogeduler.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.toogeduler.config.VapidKeys;
import app.toogeduler.domain.PushSubscription;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpRequest;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class WebPushSenderTest {
    /** 환경변수 형식(base64url 원시 키)의 VAPID 키를 읽어 표준 헤더가 붙은 암호화 요청을 만든다. */
    @Test
    void buildsAnEncryptedVapidRequestFromRawEnvironmentKeys() throws Exception {
        KeyPair server = p256();
        KeyPair browser = p256();
        VapidKeys vapid =
                new VapidKeys(
                        b64(raw((ECPublicKey) server.getPublic())),
                        b64(unsigned32(((ECPrivateKey) server.getPrivate()).getS())),
                        "mailto:support@toogeduler.com");
        PushSubscription subscription = new PushSubscription();
        subscription.setEndpoint("https://fcm.googleapis.com/fcm/send/abc");
        subscription.setP256dh(b64(raw((ECPublicKey) browser.getPublic())));
        subscription.setAuth(b64(new byte[16]));

        HttpRequest request =
                new WebPushSender(vapid).request(subscription, "{\"title\":\"t\"}".getBytes());

        assertThat(request.uri()).isEqualTo(URI.create("https://fcm.googleapis.com/fcm/send/abc"));
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers().firstValue("Content-Encoding")).contains("aes128gcm");
        assertThat(request.headers().firstValue("TTL")).contains("86400");
        assertThat(request.headers().firstValue("Authorization"))
                .hasValueSatisfying(
                        v ->
                                assertThat(v)
                                        .startsWith("vapid t=")
                                        .endsWith(", k=" + vapid.getPublicKey()));
        assertThat(request.bodyPublisher().orElseThrow().contentLength()).isPositive();
    }

    private static KeyPair p256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static byte[] raw(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        System.arraycopy(unsigned32(key.getW().getAffineX()), 0, out, 1, 32);
        System.arraycopy(unsigned32(key.getW().getAffineY()), 0, out, 33, 32);
        return out;
    }

    private static byte[] unsigned32(BigInteger n) {
        byte[] bytes = n.toByteArray();
        byte[] out = new byte[32];
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, out, 32 - length, length);
        return out;
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
