package app.toogeduler.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 운영 환경에서 개발용 기본값이 그대로 배포되는 것을 막는다.
 *
 * app.web-url 이 localhost 가 아니면 운영 배포로 간주하고,
 * 안전하지 않은 설정이 있으면 애플리케이션을 즉시 중단시킨다.
 * 조용히 약한 키로 계속 도는 것보다 배포가 실패하는 편이 안전하다.
 */
@Component @Slf4j
public class ConfigGuard {
    /** application.yml 에 들어 있는 로컬 개발용 기본 키. 운영에서 쓰이면 안 된다. */
    private static final String DEFAULT_JWT_SECRET = "toogeduler-local-secret-key-change-this-in-production-2026";

    private final String webUrl;
    private final String datasourceUrl;
    private final String jwtSecret;
    private final boolean seedDemo;
    private final String googleClientId;
    private final String kakaoClientId;

    public ConfigGuard(@Value("${app.web-url}") String webUrl,
                       @Value("${spring.datasource.url}") String datasourceUrl,
                       @Value("${app.jwt-secret}") String jwtSecret,
                       @Value("${app.seed-demo}") boolean seedDemo,
                       @Value("${spring.security.oauth2.client.registration.google.client-id}") String googleClientId,
                       @Value("${spring.security.oauth2.client.registration.kakao.client-id}") String kakaoClientId) {
        this.webUrl = webUrl;
        this.datasourceUrl = datasourceUrl;
        this.jwtSecret = jwtSecret;
        this.seedDemo = seedDemo;
        this.googleClientId = googleClientId;
        this.kakaoClientId = kakaoClientId;
        verify();
    }

    /**
     * 운영 배포 판정은 "실패 시 닫히는(fail-closed)" 방향이어야 한다.
     *
     * WEB_URL 만 보면, Railway 에 배포하면서 WEB_URL 설정을 잊은 경우 기본값 localhost 때문에
     * 로컬로 오판하고 개발용 키와 데모 계정을 그대로 통과시킨다. 이 검사가 막아야 하는 바로 그 상황이다.
     * 따라서 웹 주소와 데이터베이스 주소 중 하나라도 원격을 가리키면 운영으로 간주한다.
     * Railway 는 DATABASE_URL 을 항상 원격 호스트로 주입하므로 이 조건이 안전망이 된다.
     */
    private boolean isProduction() {
        return !isLocal(webUrl) || !isLocal(datasourceUrl);
    }

    private static boolean isLocal(String url) {
        if (url == null || url.isBlank()) return false;
        return url.contains("localhost") || url.contains("127.0.0.1");
    }

    private void verify() {
        if (!isProduction()) {
            log.info("Local configuration detected (WEB_URL={}, datasource={}). Production guards are relaxed.", webUrl, datasourceUrl);
            return;
        }
        if (isLocal(webUrl)) {
            throw new IllegalStateException(
                    "WEB_URL is not configured but the database points at a remote host (" + datasourceUrl
                            + "). Set WEB_URL to the production web address, e.g. https://app.example.com.");
        }
        if (!webUrl.startsWith("https://")) {
            throw new IllegalStateException("WEB_URL must use https:// in production. Current value: " + webUrl);
        }
        if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "JWT_SECRET is still the bundled development key. Generate one with `openssl rand -base64 48` and set it as an environment variable.");
        }
        if (jwtSecret.getBytes().length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes for HMAC-SHA256. Current length: " + jwtSecret.getBytes().length);
        }
        if (seedDemo) {
            throw new IllegalStateException("SEED_DEMO must be false in production so demo accounts are never created on a public database.");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void reportOptionalConfig() {
        if (!isProduction()) return;
        if (googleClientId.startsWith("local-")) log.warn("GOOGLE_CLIENT_ID is not configured. Google login will fail.");
        if (kakaoClientId.startsWith("local-")) log.warn("KAKAO_CLIENT_ID is not configured. Kakao login will fail.");
        log.info("Production configuration verified for {}", webUrl);
    }
}
