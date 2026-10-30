package app.toogeduler.service;

import app.toogeduler.config.VapidKeys;
import app.toogeduler.domain.PushSubscription;
import io.jsonwebtoken.Jwts;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 웹 푸시 표준을 JDK 암호 API 로 직접 구현한 발송기. 내용 암호화는 RFC 8291(aes128gcm), 발신자 인증은 RFC 8292(VAPID)를 따른다. 외부
 * 라이브러리 없이 JDK 와 이미 쓰는 jjwt(ES256 서명)만 사용한다.
 */
@Component
@RequiredArgsConstructor
public class WebPushSender implements PushSender {
    /** 브라우저가 꺼져 있을 때 푸시 서비스가 알림을 보관하는 시간(초). 하루가 지난 일정 알림은 의미가 없다. */
    private static final int TTL_SECONDS = 86400;

    private static final int RECORD_SIZE = 4096;
    private static final ECParameterSpec P256 = p256();
    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    private final VapidKeys vapid;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public int send(PushSubscription subscription, byte[] payload) throws Exception {
        byte[] body =
                encrypt(
                        payload,
                        B64.decode(subscription.getP256dh()),
                        B64.decode(subscription.getAuth()),
                        newKeyPair(),
                        randomSalt());
        URI endpoint = URI.create(subscription.getEndpoint());
        HttpRequest request =
                HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", authorization(endpoint))
                        .header("Content-Encoding", "aes128gcm")
                        .header("Content-Type", "application/octet-stream")
                        .header("TTL", String.valueOf(TTL_SECONDS))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /** VAPID: 푸시 서비스 주소(origin)를 대상으로 12시간짜리 ES256 토큰을 만들고 공개키를 함께 보낸다. */
    private String authorization(URI endpoint) throws GeneralSecurityException {
        String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        PrivateKey key =
                KeyFactory.getInstance("EC")
                        .generatePrivate(
                                new ECPrivateKeySpec(
                                        new BigInteger(1, B64.decode(vapid.getPrivateKey())),
                                        P256));
        String token =
                Jwts.builder()
                        .header()
                        .type("JWT")
                        .and()
                        .audience()
                        .single(audience)
                        .subject(vapid.getSubject())
                        .expiration(Date.from(Instant.now().plus(Duration.ofHours(12))))
                        .signWith(key, Jwts.SIG.ES256)
                        .compact();
        return "vapid t=" + token + ", k=" + vapid.getPublicKey();
    }

    /**
     * RFC 8291 메시지 암호화. 서버의 일회용 키와 브라우저 공개키로 ECDH 공유 비밀을 만들고, 브라우저의 auth 비밀값과 salt 로 내용 키·nonce 를
     * 유도해 AES-128-GCM 으로 한 레코드에 담는다. 결과는 헤더(salt, 레코드 크기, 서버 공개키) 뒤에 암호문을 붙인 바이트열이다.
     */
    static byte[] encrypt(
            byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair serverKeys, byte[] salt)
            throws GeneralSecurityException {
        byte[] asPublic = rawPublicKey((ECPublicKey) serverKeys.getPublic());
        KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
        ecdh.init(serverKeys.getPrivate());
        ecdh.doPhase(publicKey(uaPublic), true);
        byte[] sharedSecret = ecdh.generateSecret();

        byte[] keyInfo = concat(ascii("WebPush: info\0"), uaPublic, asPublic);
        byte[] ikm = expand(hmac(authSecret, sharedSecret), keyInfo, 32);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = expand(prk, ascii("Content-Encoding: aes128gcm\0"), 16);
        byte[] nonce = expand(prk, ascii("Content-Encoding: nonce\0"), 12);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(cek, "AES"),
                new GCMParameterSpec(128, nonce));
        // 0x02 는 "마지막 레코드" 구분자다. 알림 하나는 레코드 하나에 들어간다.
        byte[] ciphertext = aes.doFinal(concat(plaintext, new byte[] {2}));

        ByteBuffer header = ByteBuffer.allocate(salt.length + 4 + 1 + asPublic.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
        return concat(header.array(), ciphertext);
    }

    /** 브라우저가 준 p256dh(65바이트 비압축 점)를 공개키로 바꾼다. 형식이 틀리면 예외를 던진다. */
    static ECPublicKey publicKey(byte[] raw) throws GeneralSecurityException {
        if (raw.length != 65 || raw[0] != 4)
            throw new InvalidKeySpecException("p256dh must be an uncompressed P-256 point");
        ECPoint point =
                new ECPoint(
                        new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)),
                        new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
        return (ECPublicKey)
                KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    private static byte[] rawPublicKey(ECPublicKey key) {
        return concat(
                new byte[] {4},
                unsigned32(key.getW().getAffineX()),
                unsigned32(key.getW().getAffineY()));
    }

    private static byte[] unsigned32(BigInteger n) {
        byte[] bytes = n.toByteArray();
        byte[] out = new byte[32];
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, out, 32 - length, length);
        return out;
    }

    private static KeyPair newKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(P256);
        return generator.generateKeyPair();
    }

    private static byte[] randomSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /** HKDF-Expand 의 첫 블록. 여기서 필요한 길이는 모두 32바이트 이하라 한 블록이면 된다. */
    private static byte[] expand(byte[] prk, byte[] info, int length)
            throws GeneralSecurityException {
        return Arrays.copyOf(hmac(prk, concat(info, new byte[] {1})), length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.writeBytes(part);
        return out.toByteArray();
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 is not available in this JVM", e);
        }
    }
}
