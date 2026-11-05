package app.toogeduler.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import app.toogeduler.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 그룹 응답의 멤버 항목은 {id, name, avatarUrl, role}만 담는다. 다른 멤버의 이메일·친구 코드는 나가지 않는다. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:group-member-privacy;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "app.seed-demo=false"
        })
@AutoConfigureMockMvc
class GroupMemberPrivacyTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;

    private User owner, member, newcomer;
    private Group group;
    private String token;

    @BeforeEach
    void setUp() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        owner = users.save(new User("owner-" + run + "@privacy.test", "그룹장", "hash"));
        member = users.save(new User("member-" + run + "@privacy.test", "멤버", "hash"));
        newcomer = users.save(new User("new-" + run + "@privacy.test", "새멤버", "hash"));
        token = "privacy-" + run;
        group = new Group();
        group.setName("사진 동아리");
        group.setColor("#19B7B1");
        group.setOwner(owner);
        group.renewInvite(token, Instant.now());
        group = groups.save(group);
        join(owner, GroupMember.Role.OWNER);
        join(member, GroupMember.Role.MEMBER);
    }

    @Test
    void groupListShowsOnlyPublicFieldsOfMembers() throws Exception {
        MvcResult result =
                mvc.perform(get("/api/groups").header("Authorization", bearer(member))).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        String raw = text(result);
        assertFalse(raw.contains(owner.getEmail()));
        assertFalse(raw.contains(owner.getFriendCode()));
        JsonNode people = json.readTree(raw).get(0).path("members");
        assertEquals(2, people.size());
        for (JsonNode person : people) {
            List<String> keys = new ArrayList<>();
            person.fieldNames().forEachRemaining(keys::add);
            assertEquals(List.of("id", "name", "avatarUrl", "role"), keys);
        }
    }

    @Test
    void joiningDoesNotRevealExistingMembersEmails() throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/groups/join/" + token)
                                        .header("Authorization", bearer(newcomer)))
                        .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        String raw = text(result);
        assertFalse(raw.contains(owner.getEmail()));
        assertFalse(raw.contains(member.getEmail()));
        assertFalse(raw.contains("\"email\""));
        assertFalse(raw.contains("\"friendCode\""));
    }

    @Test
    void meStillReturnsMyOwnEmailAndFriendCode() throws Exception {
        MvcResult result =
                mvc.perform(get("/api/auth/me").header("Authorization", bearer(member)))
                        .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        JsonNode me = json.readTree(text(result));
        assertEquals(member.getEmail(), me.path("email").asText());
        assertEquals(member.getFriendCode(), me.path("friendCode").asText());
    }

    private String bearer(User user) {
        return "Bearer " + jwt.create(user.getId());
    }

    private String text(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void join(User user, GroupMember.Role role) {
        GroupMember m = new GroupMember();
        m.setGroup(group);
        m.setUser(user);
        m.setRole(role);
        members.save(m);
    }
}
