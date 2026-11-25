package app.toogeduler.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** WEB_URL 출처의 CORS 사전 요청은 통과하고, 그 밖의 출처는 거절된다. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:corspreflight;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "app.seed-demo=false",
            "app.web-url=http://localhost:3000"
        })
@AutoConfigureMockMvc
class CorsPreflightTest {
    private static final String WEB = "http://localhost:3000";

    @Autowired MockMvc mvc;

    @Test
    void preflightFromWebUrlIsAllowedWithCredentials() throws Exception {
        mvc.perform(preflight(WEB, "POST", "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", WEB))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void preflightAllowsHeadersAddedForCookieLogin() throws Exception {
        // PR #94: 로그인 방식(X-Auth-Mode)과 CSRF 이중 제출 토큰(X-XSRF-TOKEN), 기존 Bearer 헤더
        String headers = "authorization,content-type,x-auth-mode,x-xsrf-token";
        mvc.perform(preflight(WEB, "DELETE", headers))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", WEB))
                .andExpect(
                        header().string(
                                        "Access-Control-Allow-Headers",
                                        "authorization, content-type, x-auth-mode, x-xsrf-token"));
    }

    @Test
    void preflightFromOtherOriginIsRejected() throws Exception {
        mvc.perform(preflight("https://evil.example", "POST", "content-type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void preflightFromSimilarLookingOriginIsRejected() throws Exception {
        // 출처는 정확히 일치해야 한다(뒤에 붙은 도메인·스킴·포트가 다르면 거절)
        for (String origin :
                new String[] {
                    "http://localhost:3000.evil.example",
                    "https://localhost:3000",
                    "http://localhost:3001"
                }) {
            mvc.perform(preflight(origin, "GET", "authorization"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    private static MockHttpServletRequestBuilder preflight(
            String origin, String method, String headers) {
        return options("/api/auth/me")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", headers);
    }
}
