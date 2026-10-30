package app.toogeduler.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class WebPushSenderTest {
    /** RFC 8291 부록 A 의 예시 값으로 암호화 결과가 바이트 단위까지 같은지 확인한다. */
    @Test
    void encryptsTheRfc8291Example() throws Exception {
        byte[] asPublic =
                b64(
                        "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        var publicKey = WebPushSender.publicKey(asPublic);
        var privateKey =
                KeyFactory.getInstance("EC")
                        .generatePrivate(
                                new ECPrivateKeySpec(
                                        new BigInteger(
                                                1,
                                                b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw")),
                                        publicKey.getParams()));

        byte[] body =
                WebPushSender.encrypt(
                        "When I grow up, I want to be a watermelon"
                                .getBytes(StandardCharsets.UTF_8),
                        b64(
                                "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"),
                        b64("BTBZMqHH6r4Tts7J_aSIgg"),
                        new KeyPair(publicKey, privateKey),
                        b64("DGv6ra1nlYgDCS1FRnbzlw"));

        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(body))
                .isEqualTo(
                        "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    private static byte[] b64(String s) {
        return Base64.getUrlDecoder().decode(s);
    }
}
