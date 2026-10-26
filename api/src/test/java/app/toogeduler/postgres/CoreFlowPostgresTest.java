package app.toogeduler.postgres;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 핵심 흐름 한 번을 HTTP 로만 따라간다: 가입/로그인 → 그룹 생성 → 초대 토큰으로 참여 → 두 사람이 일정 등록 → 빈 시간 계산 → 후보 시간으로 그룹 일정 생성과
 * 알림. 쿼리와 저장이 실제 PostgreSQL 에서 도는지 확인하는 것이 목적이다.
 */
class CoreFlowPostgresTest extends PostgresTestBase {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void twoPeopleFindATimeAndScheduleAGroupEvent() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);

        // 1. 가입/로그인
        String ownerEmail = "owner-" + run + "@flow.test";
        call(null, post("/api/auth/register"), register(ownerEmail, "그룹장"));
        String owner =
                call(
                                null,
                                post("/api/auth/login"),
                                Map.of("email", ownerEmail, "password", "password-1234"))
                        .path("token")
                        .asText();
        String guest =
                call(
                                null,
                                post("/api/auth/register"),
                                register("guest-" + run + "@flow.test", "참여자"))
                        .path("token")
                        .asText();
        assertFalse(owner.isBlank());
        assertFalse(guest.isBlank());

        // 2. 그룹 생성
        JsonNode group = call(owner, post("/api/groups"), Map.of("name", "스터디 " + run));
        long groupId = group.path("id").asLong();
        String inviteUrl = group.path("inviteUrl").asText();
        String token = inviteUrl.substring(inviteUrl.lastIndexOf('/') + 1);

        // 3. 두 번째 사용자가 초대 토큰으로 참여
        JsonNode joined = call(guest, post("/api/groups/join/" + token), null);
        assertEquals(groupId, joined.path("id").asLong());
        assertEquals(2, joined.path("members").size());

        // 4. 두 사람이 일정 등록: 그룹장은 오전, 참여자는 점심 이후가 바쁘다.
        LocalDate day = LocalDate.now(SEOUL).plusDays(7);
        call(owner, post("/api/events"), privateEvent("오전 수업", at(day, 9), at(day, 12)));
        call(guest, post("/api/events"), privateEvent("아르바이트", at(day, 12), at(day, 15)));

        // 5. 빈 시간 계산: 두 사람 모두 되는 후보는 15시 이후에만 있다.
        JsonNode slots =
                call(
                        owner,
                        get("/api/groups/" + groupId + "/availability")
                                .param("from", day.toString())
                                .param("to", day.toString()),
                        null);
        JsonNode best = slots.path(0);
        assertTrue(best.path("everyoneAvailable").asBoolean(), "모두 되는 후보가 먼저 와야 한다: " + slots);
        assertEquals(2, best.path("totalCount").asInt());
        OffsetDateTime start = OffsetDateTime.parse(best.path("startAt").asText());
        OffsetDateTime end = OffsetDateTime.parse(best.path("endAt").asText());
        assertFalse(start.isBefore(at(day, 15)), "바쁜 시간과 겹치면 안 된다: " + best);

        // 6. 후보 시간으로 그룹 일정을 만들고 멤버에게 알린다.
        Map<String, Object> meeting = new LinkedHashMap<>();
        meeting.put("title", "스터디 모임");
        meeting.put("startAt", start.toString());
        meeting.put("endAt", end.toString());
        meeting.put("visibilities", List.of("GROUP"));
        meeting.put("groupIds", List.of(groupId));
        meeting.put("announce", true);
        call(owner, post("/api/events"), meeting);

        JsonNode inbox = call(guest, get("/api/notifications"), null);
        List<String> types = new ArrayList<>();
        inbox.forEach(n -> types.add(n.path("type").asText()));
        assertTrue(types.contains("GROUP_EVENT"), "참여자에게 그룹 일정 알림이 와야 한다: " + inbox);
        JsonNode groupEvents =
                call(
                        guest,
                        get("/api/groups/" + groupId + "/events")
                                .param("from", day.toString() + "T00:00:00+09:00")
                                .param("to", day.plusDays(1).toString() + "T00:00:00+09:00"),
                        null);
        assertTrue(groupEvents.toString().contains("스터디 모임"), "그룹 캘린더에 보여야 한다: " + groupEvents);
    }

    private static Map<String, Object> register(String email, String name) {
        return Map.of("email", email, "name", name, "password", "password-1234");
    }

    private static Map<String, Object> privateEvent(
            String title, OffsetDateTime start, OffsetDateTime end) {
        return Map.of(
                "title",
                title,
                "startAt",
                start.toString(),
                "endAt",
                end.toString(),
                "visibilities",
                List.of("PRIVATE"));
    }

    private static OffsetDateTime at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(SEOUL).toOffsetDateTime();
    }

    /** 요청을 보내고 200 인지 확인한 뒤 응답 JSON 을 돌려준다. token 이 있으면 Bearer 로 붙인다. */
    private JsonNode call(String token, MockHttpServletRequestBuilder request, Object body)
            throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null)
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MvcResult result = mvc.perform(request).andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, result.getResponse().getStatus(), request + " 응답: " + content);
        return json.readTree(content);
    }
}
