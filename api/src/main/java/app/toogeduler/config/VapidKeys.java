package app.toogeduler.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 웹 푸시 발신자 키(VAPID, RFC 8292). 공개키는 65바이트 비압축 P-256 점, 개인키는 32바이트 값이며 둘 다 base64url 이다. 셋 중 하나라도 비어
 * 있으면 웹 푸시를 끈 것으로 보고 발송을 건너뛴다. 앱의 다른 기능은 그대로 동작한다.
 */
@Component
@Getter
public class VapidKeys {
    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public VapidKeys(
            @Value("${app.vapid.public-key:}") String publicKey,
            @Value("${app.vapid.private-key:}") String privateKey,
            @Value("${app.vapid.subject:}") String subject) {
        this.publicKey = publicKey.trim();
        this.privateKey = privateKey.trim();
        this.subject = subject.trim();
    }

    public boolean configured() {
        return !publicKey.isEmpty() && !privateKey.isEmpty() && !subject.isEmpty();
    }
}
