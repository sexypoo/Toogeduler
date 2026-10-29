package app.toogeduler.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import app.toogeduler.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 초대 링크 만료: 기간이 지난 토큰은 410 이고 가입시키지 않으며, 재발급은 그룹장만 하고 새 링크는 7일 뒤 만료된다. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:invite-expiry;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "app.seed-demo=false"
        })
@AutoConfigureMockMvc
class InviteExpiryTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;

    private User owner, member, visitor;
    private Group group;

    @BeforeEach
    void setUp() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        owner = users.save(new User("owner-" + run + "@expiry.test", "그룹장", "hash"));
        member = users.save(new User("member-" + run + "@expiry.test", "멤버", "hash"));
        visitor = users.save(new User("visitor-" + run + "@expiry.test", "링크 받은 사람", "hash"));
        group = new Group();
        group.setName("만료 테스트 모임");
        group.setOwner(owner);
        group.renewInvite("expiry-" + run, Instant.now());
        group = groups.save(group);
        join(owner, GroupMember.Role.OWNER);
        join(member, GroupMember.Role.MEMBER);
    }

    @Test
    void joiningWithAnExpiredTokenIsGoneAndAddsNoMember() throws Exception {
        group.setInviteExpiresAt(Instant.now().minusSeconds(1));
        groups.save(group);

        MvcResult result =
                mvc.perform(
                                post("/api/groups/join/" + group.getInviteToken())
                                        .header("Authorization", bearer(visitor)))
                        .andReturn();

        assertEquals(410, result.getResponse().getStatus());
        assertEquals("초대 링크가 만료됐어요. 그룹장에게 새 링크를 요청하세요.", body(result).path("message").asText());
        assertEquals(2, members.countByGroupId(group.getId()));
        assertFalse(members.existsByGroupIdAndUserId(group.getId(), visitor.getId()));
    }

    @Test
    void previewOfAnExpiredTokenIsGoneWithoutGroupDetails() throws Exception {
        group.setInviteExpiresAt(Instant.now().minusSeconds(1));
        groups.save(group);

        MvcResult result =
                mvc.perform(get("/api/public/invites/" + group.getInviteToken())).andReturn();

        assertEquals(410, result.getResponse().getStatus());
        assertFalse(
                result.getResponse()
                        .getContentAsString(StandardCharsets.UTF_8)
                        .contains("만료 테스트 모임"));
    }

    @Test
    void ownerRefreshKillsTheOldTokenAndExpiresTheNewOneInSevenDays() throws Exception {
        String old = group.getInviteToken();
        // 만료 시각은 마이크로초로 잘려 저장되므로 하한도 같은 단위로 자른다.
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

        MvcResult result =
                mvc.perform(
                                post("/api/groups/" + group.getId() + "/invite/refresh")
                                        .header("Authorization", bearer(owner)))
                        .andReturn();

        Instant after = Instant.now();
        assertEquals(200, result.getResponse().getStatus());
        Group saved = groups.findById(group.getId()).orElseThrow();
        assertNotEquals(old, saved.getInviteToken());
        assertTrue(
                body(result)
                        .path("inviteUrl")
                        .asText()
                        .endsWith("/invite/" + saved.getInviteToken()));
        assertFalse(saved.getInviteExpiresAt().isBefore(before.plus(Duration.ofDays(7))));
        assertFalse(saved.getInviteExpiresAt().isAfter(after.plus(Duration.ofDays(7))));
        assertEquals(
                saved.getInviteExpiresAt(),
                Instant.parse(body(result).path("inviteExpiresAt").asText()));
        assertEquals(404, status(post("/api/groups/join/" + old), visitor));
        assertEquals(
                404,
                mvc.perform(get("/api/public/invites/" + old))
                        .andReturn()
                        .getResponse()
                        .getStatus());
        assertEquals(200, status(post("/api/groups/join/" + saved.getInviteToken()), visitor));
    }

    @Test
    void memberCannotRefreshTheLink() throws Exception {
        String old = group.getInviteToken();

        assertEquals(403, status(post("/api/groups/" + group.getId() + "/invite/refresh"), member));
        assertEquals(old, groups.findById(group.getId()).orElseThrow().getInviteToken());
    }

    @Test
    void groupViewShowsTheInviteExpiry() throws Exception {
        MvcResult result =
                mvc.perform(get("/api/groups").header("Authorization", bearer(member))).andReturn();

        JsonNode mine = body(result).get(0);
        assertEquals(
                group.getInviteExpiresAt(), Instant.parse(mine.path("inviteExpiresAt").asText()));
    }

    private int status(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            User user)
            throws Exception {
        return mvc.perform(request.header("Authorization", bearer(user)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String bearer(User user) {
        return "Bearer " + jwt.create(user.getId());
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void join(User user, GroupMember.Role role) {
        GroupMember m = new GroupMember();
        m.setGroup(group);
        m.setUser(user);
        m.setRole(role);
        members.save(m);
    }
}
