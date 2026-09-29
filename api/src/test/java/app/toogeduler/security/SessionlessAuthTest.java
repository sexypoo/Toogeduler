package app.toogeduler.security;

import app.toogeduler.domain.User;
import app.toogeduler.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로그아웃은 클라이언트가 JWT 를 지우는 것이므로, 세션 쿠키만으로는 인증되면 안 된다. */
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:sessionless;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.seed-demo=false"
})
@AutoConfigureMockMvc
class SessionlessAuthTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;

    @Test
    void sessionFromTokenRequestDoesNotAuthenticateLaterRequests() throws Exception {
        User user=users.save(new User("session@toogeduler.app","세션 테스트","hash"));
        MockHttpSession session=new MockHttpSession();

        mvc.perform(get("/api/auth/me").session(session).header("Authorization","Bearer "+jwt.create(user.getId())))
            .andExpect(status().isOk());

        // 토큰을 지운 뒤(로그아웃) 같은 세션 쿠키로 요청
        mvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isUnauthorized());
    }
}
