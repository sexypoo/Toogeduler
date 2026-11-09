package app.toogeduler.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * 로그인 쿠키를 만들고 읽는다(SEC-011, docs/AUTH_COOKIE_DESIGN.md 결정 1·2).
 *
 * <p>웹은 API 를 같은 출처(`/backend` 프록시)로 부르므로 쿠키는 웹 호스트의 first-party 쿠키가 된다. API 는 Railway 프록시 뒤에 있어
 * request.isSecure() 를 믿을 수 없다. 그래서 Secure·Path 를 요청에서 추측하지 않고 설정값으로 정한다. `__Host-` 접두사는 Domain 지정을
 * 막아 형제 서브도메인이 쿠키를 덮어쓰지 못하게 한다(Secure·Path=/ 가 있어야 브라우저가 받아 준다).
 */
@Component
public class AuthCookies {
    static final Duration ACCESS_AGE = Duration.ofDays(30);
    static final Duration DEVICE_AGE = Duration.ofDays(365);
    static final String SIGNED_IN = "tg_signed_in";

    private final boolean secure;
    private final String accessName;
    private final String deviceName;

    /** secure=false 는 http://localhost 개발용이다. 브라우저가 `__Host-`·Secure 쿠키를 http 에서 받지 않으므로 둘 다 뺀다. */
    public AuthCookies(@Value("${app.auth-cookie.secure:true}") boolean secure) {
        this.secure = secure;
        accessName = (secure ? "__Host-" : "") + "tg_at";
        deviceName = (secure ? "__Host-" : "") + "tg_device";
    }

    /** 로그인 성공: JWT 쿠키와 비밀 아닌 로그인 표시 쿠키, 기기 토큰 쿠키를 함께 내려준다. */
    public void signIn(HttpServletResponse res, String jwt, String deviceToken) {
        add(res, accessName, jwt, ACCESS_AGE, true);
        add(res, SIGNED_IN, "1", ACCESS_AGE, false);
        if (deviceToken != null) add(res, deviceName, deviceToken, DEVICE_AGE, true);
    }

    /** 로그아웃: 기기 토큰은 남긴다. 다시 로그인할 때도 "이 기기는 주인"이어야 계정 잠금(SEC-012)을 피할 수 있다. */
    public void signOut(HttpServletResponse res) {
        add(res, accessName, "", Duration.ZERO, true);
        add(res, SIGNED_IN, "", Duration.ZERO, false);
    }

    /** 회원 탈퇴: 기기 토큰까지 지운다. */
    public void clearAll(HttpServletResponse res) {
        signOut(res);
        add(res, deviceName, "", Duration.ZERO, true);
    }

    public String access(HttpServletRequest req) {
        return read(req, accessName);
    }

    public String device(HttpServletRequest req) {
        return read(req, deviceName);
    }

    /** SPA 가 document.cookie 로 읽어 X-XSRF-TOKEN 헤더로 돌려보내는 이중 제출 토큰 저장소. */
    CookieCsrfTokenRepository csrfRepository() {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookieName((secure ? "__Host-" : "") + "XSRF-TOKEN");
        repo.setCookiePath("/");
        // 만료 없는 세션 쿠키는 앱(WebView)을 강제 종료하면 사라질 수 있어 Max-Age 를 준다.
        // 이 설정은 삭제(값 "") 때도 적용되지만, 저장소는 빈 값을 "없음"으로 보고 새 토큰을 만든다.
        repo.setCookieCustomizer(c -> c.secure(secure).sameSite("Lax").maxAge(ACCESS_AGE));
        return repo;
    }

    private void add(
            HttpServletResponse res, String name, String value, Duration age, boolean httpOnly) {
        ResponseCookie.ResponseCookieBuilder c =
                ResponseCookie.from(name, value).httpOnly(httpOnly);
        c.secure(secure).sameSite("Lax").path("/").maxAge(age);
        res.addHeader(HttpHeaders.SET_COOKIE, c.build().toString());
    }

    private static String read(HttpServletRequest req, String name) {
        if (req.getCookies() == null) return null;
        for (Cookie c : req.getCookies())
            if (c.getName().equals(name) && !c.getValue().isEmpty()) return c.getValue();
        return null;
    }
}
