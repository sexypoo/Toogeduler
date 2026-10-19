package app.toogeduler.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import app.toogeduler.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 초대 링크 미리보기(GET /api/public/invites/{token}): 비로그인으로 볼 수 있고, 그룹을 알아볼 만큼만 보여주며, 가입시키지 않는다. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:invite-preview;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "app.seed-demo=false"
        })
@AutoConfigureMockMvc
class InvitePreviewTest {
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
        owner = users.save(new User("owner-" + run + "@invite.test", "초대한 그룹장", "hash"));
        member = users.save(new User("member-" + run + "@invite.test", "기존 멤버", "hash"));
        visitor = users.save(new User("visitor-" + run + "@invite.test", "링크 받은 사람", "hash"));
        group = new Group();
        group.setName("주말 등산 모임");
        group.setColor("#F0A63A");
        group.setOwner(owner);
        group.setInviteToken("invite-" + run);
        group = groups.save(group);
        join(owner, GroupMember.Role.OWNER);
        join(member, GroupMember.Role.MEMBER);
    }

    @Test
    void anonymousVisitorSeesOnlyNameColorMemberCountAndOwnerName() throws Exception {
        MvcResult result =
                mvc.perform(get("/api/public/invites/" + group.getInviteToken())).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        JsonNode body =
                json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("주말 등산 모임", body.path("name").asText());
        assertEquals("#F0A63A", body.path("color").asText());
        assertEquals(2, body.path("memberCount").asInt());
        assertEquals("초대한 그룹장", body.path("ownerName").asText());
        assertEquals(Set.of("name", "color", "memberCount", "ownerName"), fieldNames(body));
        assertFalse(hasKey(body, "email"), "응답 어디에도 email 키가 없어야 한다: " + body);
        assertFalse(body.toString().contains("@invite.test"), "이메일 값이 새면 안 된다: " + body);
    }

    @Test
    void unknownTokenIsNotFound() throws Exception {
        assertEquals(
                404,
                mvc.perform(get("/api/public/invites/no-such-token"))
                        .andReturn()
                        .getResponse()
                        .getStatus());
    }

    @Test
    void oldTokenIsNotFoundAfterTheLinkIsRefreshed() throws Exception {
        String old = group.getInviteToken();
        assertEquals(
                200,
                mvc.perform(
                                post("/api/groups/" + group.getId() + "/invite/refresh")
                                        .header(
                                                "Authorization",
                                                "Bearer " + jwt.create(owner.getId())))
                        .andReturn()
                        .getResponse()
                        .getStatus());
        String fresh = groups.findById(group.getId()).orElseThrow().getInviteToken();
        assertNotEquals(old, fresh);
        assertEquals(
                404,
                mvc.perform(get("/api/public/invites/" + old))
                        .andReturn()
                        .getResponse()
                        .getStatus());
        assertEquals(
                200,
                mvc.perform(get("/api/public/invites/" + fresh))
                        .andReturn()
                        .getResponse()
                        .getStatus());
    }

    @Test
    void openingThePreviewWhileLoggedInDoesNotJoinTheGroup() throws Exception {
        assertEquals(
                200,
                mvc.perform(
                                get("/api/public/invites/" + group.getInviteToken())
                                        .header(
                                                "Authorization",
                                                "Bearer " + jwt.create(visitor.getId())))
                        .andReturn()
                        .getResponse()
                        .getStatus());
        assertFalse(members.existsByGroupIdAndUserId(group.getId(), visitor.getId()));
        assertEquals(2, members.countByGroupId(group.getId()));
        // 참여는 "참여하기"가 부르는 POST 로만 일어난다.
        assertEquals(
                200,
                mvc.perform(
                                post("/api/groups/join/" + group.getInviteToken())
                                        .header(
                                                "Authorization",
                                                "Bearer " + jwt.create(visitor.getId())))
                        .andReturn()
                        .getResponse()
                        .getStatus());
        assertTrue(members.existsByGroupIdAndUserId(group.getId(), visitor.getId()));
    }

    private void join(User user, GroupMember.Role role) {
        GroupMember m = new GroupMember();
        m.setGroup(group);
        m.setUser(user);
        m.setRole(role);
        members.save(m);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static boolean hasKey(JsonNode node, String key) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getKey().equalsIgnoreCase(key) || hasKey(e.getValue(), key)) return true;
            }
        }
        if (node.isArray()) for (JsonNode child : node) if (hasKey(child, key)) return true;
        return false;
    }
}
