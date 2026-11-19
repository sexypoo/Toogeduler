package app.toogeduler.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** 운영 설정에서 로그인 쿠키의 Secure 를 끄면 시작을 멈추고, 로컬에서는 허용한다. */
class ConfigGuardTest {
    private static final String PROD_WEB = "https://app.example.com";
    private static final String PROD_DB = "jdbc:postgresql://db.example.com:5432/toogeduler";
    private static final String PROD_SECRET = "a-production-secret-that-is-long-enough-for-hs256";

    private static ConfigGuard guard(
            String webUrl, String datasourceUrl, String jwtSecret, boolean authCookieSecure) {
        return new ConfigGuard(
                webUrl,
                datasourceUrl,
                jwtSecret,
                false,
                authCookieSecure,
                "google-id",
                "kakao-id",
                new VapidKeys("", "", ""));
    }

    @Test
    void productionRejectsInsecureAuthCookie() {
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> guard(PROD_WEB, PROD_DB, PROD_SECRET, false));
        assertTrue(e.getMessage().startsWith("AUTH_COOKIE_SECURE must not be false in production"));
    }

    @Test
    void productionAcceptsSecureAuthCookie() {
        assertDoesNotThrow(() -> guard(PROD_WEB, PROD_DB, PROD_SECRET, true));
    }

    @Test
    void localAllowsInsecureAuthCookie() {
        assertDoesNotThrow(
                () ->
                        guard(
                                "http://localhost:3000",
                                "jdbc:h2:mem:local",
                                "toogeduler-local-secret-key-change-this-in-production-2026",
                                false));
    }
}
