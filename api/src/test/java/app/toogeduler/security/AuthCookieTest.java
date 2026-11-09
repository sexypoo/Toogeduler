package app.toogeduler.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import app.toogeduler.domain.User;
import app.toogeduler.repo.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** SEC-011 1단계: API 가 로그인 쿠키를 발급하고 CSRF 로 지키면서, 기존 Bearer 방식도 그대로 받는다. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:authcookie;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "app.seed-demo=false"
        })
@AutoConfigureMockMvc
class AuthCookieTest {
    private static final String AT = "__Host-tg_at";
    private static final String DEVICE = "__Host-tg_device";
    private static final String XSRF = "__Host-XSRF-TOKEN";
    private static final String AUTH = "Authorization";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired DeviceTokens deviceTokens;
    @Autowired OAuthLoginCodes loginCodes;
    @Autowired PasswordEncoder passwords;

    @Test
    void registerSetsCookiesWithDesignedAttributesAndKeepsBodyToken() throws Exception {
        String register =
                "{\"email\":\"reg@toogeduler.app\",\"name\":\"가입\",\"password\":\"password-123\"}";
        MvcResult r =
                mvc.perform(json(post("/api/auth/register"), register))
                        .andExpect(jsonPath("$.token").isString())
                        .andExpect(jsonPath("$.deviceToken").isString())
                        .andReturn();
        String at = has(r, AT, "HttpOnly", "Secure", "SameSite=Lax", "Path=/", "Max-Age=2592000");
        assertFalse(at.contains("Domain"), "__Host- 쿠키는 Domain 이 없어야 한다");
        String signedIn = has(r, "tg_signed_in=1;", "Secure", "Path=/", "Max-Age=2592000");
        assertFalse(signedIn.contains("HttpOnly"), "웹이 로그인 여부를 읽는 표시 쿠키");
        has(r, DEVICE, "HttpOnly", "Secure", "SameSite=Lax", "Path=/", "Max-Age=31536000");
        String xsrf = has(r, XSRF, "Secure", "Path=/", "Max-Age=2592000");
        assertFalse(xsrf.contains("HttpOnly"), "SPA 가 읽어 헤더로 돌려보내야 한다");
        // SameSite 는 Cookie 속성으로 넘어간다(Tomcat 은 내보내지만 MockMvc 헤더 문자열엔 빠진다).
        assertEquals("Lax", r.getResponse().getCookie(XSRF).getAttribute("SameSite"));
    }

    @Test
    void loginPrefersTheDeviceCookieAndExchangeSetsCookies() throws Exception {
        User u = emailUser("login@toogeduler.app");
        String own = deviceTokens.issue(u.getId());
        String login =
                "{\"email\":\"login@toogeduler.app\",\"password\":\"password-123\",\"deviceToken\":\"bogus\"}";
        MvcResult r =
                mvc.perform(json(post("/api/auth/login"), login).cookie(new Cookie(DEVICE, own)))
                        .andExpect(jsonPath("$.token").isString())
                        .andExpect(jsonPath("$.deviceToken").value(own))
                        .andReturn();
        for (String name : new String[] {AT, DEVICE + "=" + own + ";", "tg_signed_in=1;", XSRF})
            has(r, name);

        String verifier = "cookie-test-verifier-0123456789abcdefghijklmn";
        String code = loginCodes.issue(u.getId(), OAuthLoginCodes.challengeOf(verifier));
        String exchange = String.format("{\"code\":\"%s\",\"verifier\":\"%s\"}", code, verifier);
        r = mvc.perform(json(post("/api/auth/oauth/exchange"), exchange)).andReturn();
        for (String name : new String[] {AT, "tg_signed_in=1;", DEVICE, XSRF}) has(r, name);
    }

    @Test
    void cookieAuthNeedsCsrfForStateChangesOnly() throws Exception {
        Cookie at = new Cookie(AT, jwt.create(emailUser("csrf@toogeduler.app").getId()));
        Cookie xsrf = new Cookie(XSRF, "xsrf-value");
        mvc.perform(get("/api/auth/me").cookie(at)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").cookie(at)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(at, xsrf).header("X-XSRF-TOKEN", "wrong"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(at, xsrf).header("X-XSRF-TOKEN", "xsrf-value"))
                .andExpect(status().isNoContent());
    }

    @Test
    void bearerRequestsStillWorkWithoutCsrf() throws Exception {
        String token = jwt.create(emailUser("bearer@toogeduler.app").getId());
        String bearer = "Bearer " + token;
        String name = "{\"name\":\"베어러\"}";
        mvc.perform(json(patch("/api/auth/me"), name).header(AUTH, bearer))
                .andExpect(status().isOk());
        // 쿠키가 함께 있어도 헤더가 있으면 헤더만 본다. 헤더가 Bearer 가 아니어도 쿠키로 넘어가지 않는다.
        mvc.perform(
                        json(patch("/api/auth/me"), name)
                                .header(AUTH, bearer)
                                .cookie(new Cookie(AT, token)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header(AUTH, "Basic abc").cookie(new Cookie(AT, token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutKeepsTheDeviceButAccountDeletionClearsEverything() throws Exception {
        MvcResult r =
                mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent()).andReturn();
        has(r, AT, "Max-Age=0", "Secure", "Path=/");
        has(r, "tg_signed_in", "Max-Age=0");
        assertNull(cookie(r, DEVICE), "기기 토큰은 로그아웃 후에도 남는다");

        String token = jwt.create(emailUser("delete@toogeduler.app").getId());
        String pw = "{\"password\":\"password-123\"}";
        r =
                mvc.perform(json(delete("/api/auth/me"), pw).header(AUTH, "Bearer " + token))
                        .andReturn();
        assertEquals(204, r.getResponse().getStatus());
        for (String name : new String[] {AT, "tg_signed_in", DEVICE}) has(r, name, "Max-Age=0");
    }

    @Test
    void sessionUpgradesABearerTokenToCookies() throws Exception {
        User u = emailUser("session-upgrade@toogeduler.app");
        String bearer = "Bearer " + jwt.create(u.getId());
        String device = deviceTokens.issue(u.getId());
        String body = "{\"deviceToken\":\"" + device + "\"}";
        MvcResult r =
                mvc.perform(json(post("/api/auth/session"), body).header(AUTH, bearer))
                        .andExpect(status().isNoContent())
                        .andReturn();
        assertEquals("", r.getResponse().getContentAsString(), "본문에 토큰을 싣지 않는다");
        for (String name : new String[] {AT, "tg_signed_in=1;", DEVICE + "=" + device + ";"})
            has(r, name);

        body = "{\"deviceToken\":\"forged\"}";
        r = mvc.perform(json(post("/api/auth/session"), body).header(AUTH, bearer)).andReturn();
        assertNull(cookie(r, DEVICE), "잘못된 기기 토큰은 옮기지 않는다");
        mvc.perform(post("/api/auth/session").header(AUTH, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        // CSRF 검사에서 빠지는 경로이므로 쿠키만으로는 쓸 수 없다.
        mvc.perform(post("/api/auth/session").cookie(new Cookie(AT, jwt.create(u.getId()))))
                .andExpect(status().isUnauthorized());
    }

    private User emailUser(String email) {
        return users.save(new User(email, "쿠키 테스트", passwords.encode("password-123")));
    }

    private static MockHttpServletRequestBuilder json(
            MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** prefix 로 시작하는 Set-Cookie 가 있고 attrs 를 모두 담는지 확인한다. */
    private static String has(MvcResult r, String prefix, String... attrs) {
        String c = cookie(r, prefix);
        assertNotNull(c, prefix + " 쿠키가 없다");
        for (String a : attrs) assertTrue(c.contains(a), c + " 에 " + a + " 가 없다");
        return c;
    }

    private static String cookie(MvcResult r, String prefix) {
        String p = prefix.contains("=") ? prefix : prefix + "=";
        return r.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(p))
                .findFirst()
                .orElse(null);
    }
}
