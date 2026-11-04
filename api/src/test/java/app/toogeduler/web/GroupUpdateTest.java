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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 그룹 이름·색상 수정: 그룹장만 바꾸고, 멤버는 403, 멤버가 아니면 404, 보낸 값만 바뀐다. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:group-update;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "app.seed-demo=false"
        })
@AutoConfigureMockMvc
class GroupUpdateTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;

    private User owner, member, stranger;
    private Group group;

    @BeforeEach
    void setUp() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        owner = users.save(new User("owner-" + run + "@update.test", "그룹장", "hash"));
        member = users.save(new User("member-" + run + "@update.test", "멤버", "hash"));
        stranger = users.save(new User("stranger-" + run + "@update.test", "남", "hash"));
        group = new Group();
        group.setName("사진 동아리");
        group.setColor("#19B7B1");
        group.setOwner(owner);
        group.renewInvite("update-" + run, Instant.now());
        group = groups.save(group);
        join(owner, GroupMember.Role.OWNER);
        join(member, GroupMember.Role.MEMBER);
    }

    @Test
    void ownerRenamesAndRecolorsAndTheListShowsTheNewValues() throws Exception {
        MvcResult result = update(owner, Map.of("name", "  사진 동아리 2기 ", "color", "#8B6FF7"));

        assertEquals(200, result.getResponse().getStatus());
        assertEquals("사진 동아리 2기", body(result).path("name").asText());
        JsonNode mine = mine(member);
        assertEquals("사진 동아리 2기", mine.path("name").asText());
        assertEquals("#8B6FF7", mine.path("color").asText());
    }

    @Test
    void memberGetsForbiddenAndStrangerGetsNotFoundWithoutChanges() throws Exception {
        assertEquals(403, update(member, Map.of("name", "멤버가 바꾼 이름")).getResponse().getStatus());
        assertEquals(404, update(stranger, Map.of("name", "남이 바꾼 이름")).getResponse().getStatus());

        Group stored = groups.findById(group.getId()).orElseThrow();
        assertEquals("사진 동아리", stored.getName());
        assertEquals("#19B7B1", stored.getColor());
    }

    @Test
    void invalidValuesAreRejectedWithTheExistingMessages() throws Exception {
        assertBadRequest(Map.of("name", ""), "그룹 이름을 입력해주세요.");
        assertBadRequest(Map.of("name", "   "), "그룹 이름을 입력해주세요.");
        assertBadRequest(Map.of("name", "가".repeat(41)), "그룹 이름은 40자까지 입력할 수 있어요.");
        assertBadRequest(Map.of("color", "red"), "색상 값이 올바르지 않습니다.");

        assertEquals("사진 동아리", groups.findById(group.getId()).orElseThrow().getName());
    }

    @Test
    void sendingOnlyTheNameKeepsTheColor() throws Exception {
        assertEquals(200, update(owner, Map.of("name", "가".repeat(40))).getResponse().getStatus());

        Group stored = groups.findById(group.getId()).orElseThrow();
        assertEquals("가".repeat(40), stored.getName());
        assertEquals("#19B7B1", stored.getColor());
    }

    private void assertBadRequest(Map<String, String> payload, String message) throws Exception {
        MvcResult result = update(owner, payload);
        assertEquals(400, result.getResponse().getStatus());
        assertEquals(message, body(result).path("message").asText());
    }

    private MvcResult update(User user, Map<String, String> payload) throws Exception {
        return mvc.perform(
                        patch("/api/groups/" + group.getId())
                                .header("Authorization", bearer(user))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(payload)))
                .andReturn();
    }

    private JsonNode mine(User user) throws Exception {
        MvcResult result =
                mvc.perform(get("/api/groups").header("Authorization", bearer(user))).andReturn();
        return body(result).get(0);
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
