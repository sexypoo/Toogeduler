package app.toogeduler.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import app.toogeduler.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 공개 범위 매트릭스 (SEC-010): 보는 사람 × 조회 경로마다 "누가 무엇을 볼 수 있는지"를 고정한다.
 *
 * <p>일정 주인(OWNER)이 공개 범위가 다른 일정 다섯 개를 갖고 있고, 각 일정의 제목·메모·장소에는 [SECRET-<종류>-...] 표식이 들어 있다. 응답 본문에
 * 표식이 보이면 그 일정의 상세가 노출된 것이다.
 *
 * <p>| 경로 | 본인 | 그룹 멤버 | 친구(수락) | 그 밖의 로그인 사용자 | 비로그인 |
 * |------------------------------------|-------------|----------------------|---------------------------------|----------------------|---------|
 * | GET /api/events | 전부 | 없음(자기 일정만) | 없음 | 없음 | 401 | | GET /api/events/{id} | 200 | 403 | 403 |
 * 403 | 401 | | GET /api/groups/{A}/events | 전부(내 것) | GROUP, GROUP+FRIENDS | 403 | 403 | 401 | |
 * GET /api/groups/{B}/events | 전부(내 것) | B 멤버: 없음 | 403 | 403 | 401 | | GET
 * /api/friends/{owner}/events | 친구와 같음* | 403 | FRIENDS, PUBLIC, GROUP+FRIENDS | 403 | 401 | | GET
 * /api/public/events | PUBLIC | PUBLIC | PUBLIC | PUBLIC | PUBLIC | | GET
 * /api/public/events/{token} | PUBLIC만 200, 나머지 404 (누구든) | | GET /api/groups/{A}/availability |
 * 200(바쁨만) | 200(바쁨만) | 403 | 403 | 401 |
 *
 * <p>(*) 수락된 친구가 한 명이라도 있으면 본인도 "친구에게 보이는 내 캘린더"를 볼 수 있다. 본인 일정이므로 누출이 아니다. 친구 요청 대기 중인 사람, 그룹 A 에서
 * 내보내진 사람, 그룹 B 멤버, 무관한 사용자는 모두 "그 밖의 로그인 사용자"로 취급된다.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:visibility-matrix;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "app.seed-demo=false"
        })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VisibilityMatrixTest {
    enum Viewer {
        OWNER,
        GROUP_MEMBER,
        FRIEND,
        PENDING_FRIEND,
        KICKED_MEMBER,
        OTHER_GROUP_MEMBER,
        STRANGER,
        ANONYMOUS
    }

    enum Route {
        MY_CALENDAR,
        EVENT_DETAIL,
        GROUP_CALENDAR,
        OTHER_GROUP_CALENDAR,
        FRIEND_CALENDAR,
        PUBLIC_CALENDAR,
        PUBLIC_EVENT,
        AVAILABILITY
    }

    enum Mutation {
        PUT,
        MOVE,
        DELETE
    }

    /** 주인의 일정 종류. hour 는 그날(서울 시각) 시작 시각이며, PRIVATE 만 14시에 있어 가용시간에서 따로 확인할 수 있다. */
    enum Ev {
        PRIVATE(14, Event.Visibility.PRIVATE, EnumSet.of(Event.Visibility.PRIVATE)),
        GROUP(10, Event.Visibility.GROUP, EnumSet.of(Event.Visibility.GROUP)),
        FRIENDS(11, Event.Visibility.FRIENDS, EnumSet.of(Event.Visibility.FRIENDS)),
        PUBLIC(12, Event.Visibility.PUBLIC, EnumSet.of(Event.Visibility.PUBLIC)),
        GROUP_FRIENDS(
                13,
                Event.Visibility.GROUP,
                EnumSet.of(Event.Visibility.GROUP, Event.Visibility.FRIENDS));
        final int hour;
        final Event.Visibility legacy;
        final Set<Event.Visibility> visibilities;

        Ev(int hour, Event.Visibility legacy, Set<Event.Visibility> visibilities) {
            this.hour = hour;
            this.legacy = legacy;
            this.visibilities = visibilities;
        }

        String marker() {
            return "[SECRET-" + name() + "]";
        }
    }

    /** 기대 결과: HTTP 상태와, 200 일 때 상세(제목·메모·장소)가 보여야 하는 일정들. 나머지 일정의 표식은 없어야 한다. */
    record Expect(int status, Set<Ev> visible) {
        static Expect ok(Set<Ev> visible) {
            return new Expect(200, visible);
        }

        static Expect ok(Ev... visible) {
            return new Expect(
                    200,
                    visible.length == 0
                            ? EnumSet.noneOf(Ev.class)
                            : EnumSet.copyOf(List.of(visible)));
        }

        static Expect status(int status) {
            return new Expect(status, Set.of());
        }
    }

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String OWNER_EMAIL = "owner@visibility.test";
    private static final String OWNER_NAME = "일정 주인";
    private static final String MEMBER_NAME = "그룹 멤버";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired FriendshipRepository friendships;
    @Autowired EventRepository events;

    private final Map<Viewer, User> people = new EnumMap<>(Viewer.class);
    private final Map<Ev, Long> ids = new EnumMap<>(Ev.class);
    private final Map<Ev, String> tokens = new EnumMap<>(Ev.class);
    private Group groupA, groupB;

    /** 가용시간 검색이 "지금 이전"을 건너뛰므로 일주일 뒤 하루에 모든 일정을 둔다. */
    private final LocalDate day = LocalDate.now(SEOUL).plusDays(7);

    @BeforeAll
    void setUp() throws Exception {
        User owner = person(Viewer.OWNER, OWNER_EMAIL, OWNER_NAME);
        User member = person(Viewer.GROUP_MEMBER, "member@visibility.test", MEMBER_NAME);
        User friend = person(Viewer.FRIEND, "friend@visibility.test", "수락된 친구");
        User pending = person(Viewer.PENDING_FRIEND, "pending@visibility.test", "요청 대기 중");
        User kicked = person(Viewer.KICKED_MEMBER, "kicked@visibility.test", "내보내진 멤버");
        User other = person(Viewer.OTHER_GROUP_MEMBER, "other@visibility.test", "다른 그룹 멤버");
        person(Viewer.STRANGER, "stranger@visibility.test", "무관한 사용자");

        // 그룹 A: 주인·멤버·(곧 내보낼) 멤버. 그룹 B: 주인과 다른 그룹 멤버. GROUP 일정은 A 에만 공개한다.
        groupA = group("그룹 A", owner, "invite-a");
        join(groupA, owner, GroupMember.Role.OWNER);
        join(groupA, member, GroupMember.Role.MEMBER);
        join(groupA, kicked, GroupMember.Role.MEMBER);
        groupB = group("그룹 B", owner, "invite-b");
        join(groupB, owner, GroupMember.Role.OWNER);
        join(groupB, other, GroupMember.Role.MEMBER);
        friendship(owner, friend, Friendship.Status.ACCEPTED);
        friendship(pending, owner, Friendship.Status.PENDING);

        for (Ev ev : Ev.values()) {
            Event e = new Event();
            e.setOwner(owner);
            e.setTitle(ev.marker() + " 제목");
            e.setDescription(ev.marker() + " 메모");
            e.setLocation(ev.marker() + " 장소");
            OffsetDateTime start = day.atTime(ev.hour, 0).atZone(SEOUL).toOffsetDateTime();
            e.setStartAt(start);
            e.setEndAt(start.plusHours(1));
            e.setVisibility(ev.legacy);
            e.getVisibilities().addAll(ev.visibilities);
            if (ev.visibilities.contains(Event.Visibility.GROUP)) e.getGroups().add(groupA);
            e.setPublicToken(UUID.randomUUID().toString());
            Event saved = events.save(e);
            ids.put(ev, saved.getId());
            tokens.put(ev, saved.getPublicToken());
        }

        // 내보내기는 실제 API 로 한다.
        int kickStatus =
                mvc.perform(
                                as(
                                        Viewer.OWNER,
                                        delete(
                                                "/api/groups/"
                                                        + groupA.getId()
                                                        + "/members/"
                                                        + kicked.getId())))
                        .andReturn()
                        .getResponse()
                        .getStatus();
        assertEquals(204, kickStatus);
        assertFalse(members.existsByGroupIdAndUserId(groupA.getId(), kicked.getId()));
    }

    static Stream<Arguments> matrix() {
        return Arrays.stream(Viewer.values())
                .flatMap(v -> Arrays.stream(Route.values()).map(r -> Arguments.of(v, r)));
    }

    @ParameterizedTest(name = "{0} × {1}")
    @MethodSource("matrix")
    void viewerSeesOnlyWhatTheVisibilityAllows(Viewer viewer, Route route) throws Exception {
        switch (route) {
            case EVENT_DETAIL -> {
                for (Ev ev : Ev.values())
                    check(
                            viewer,
                            route + " " + ev,
                            get("/api/events/" + ids.get(ev)),
                            viewer == Viewer.OWNER ? Expect.ok(ev) : denied(viewer));
            }
            case PUBLIC_EVENT -> {
                for (Ev ev : Ev.values())
                    check(
                            viewer,
                            route + " " + ev,
                            get("/api/public/events/" + tokens.get(ev)),
                            ev == Ev.PUBLIC ? Expect.ok(ev) : Expect.status(404));
            }
            case AVAILABILITY -> availability(viewer);
            default -> check(viewer, route.name(), listRequest(route), expectList(route, viewer));
        }
    }

    /** 위 표의 목록 경로 부분. */
    private Expect expectList(Route route, Viewer viewer) {
        Set<Ev> all = EnumSet.allOf(Ev.class);
        return switch (route) {
            case MY_CALENDAR ->
                    viewer == Viewer.ANONYMOUS
                            ? Expect.status(401)
                            : viewer == Viewer.OWNER ? Expect.ok(all) : Expect.ok();
            case GROUP_CALENDAR ->
                    switch (viewer) {
                        case OWNER -> Expect.ok(all);
                        case GROUP_MEMBER -> Expect.ok(Ev.GROUP, Ev.GROUP_FRIENDS);
                        default -> denied(viewer);
                    };
            case OTHER_GROUP_CALENDAR ->
                    switch (viewer) {
                        case OWNER -> Expect.ok(all);
                        case OTHER_GROUP_MEMBER -> Expect.ok();
                        default -> denied(viewer);
                    };
            case FRIEND_CALENDAR ->
                    viewer == Viewer.FRIEND || viewer == Viewer.OWNER
                            ? Expect.ok(Ev.FRIENDS, Ev.PUBLIC, Ev.GROUP_FRIENDS)
                            : denied(viewer);
            case PUBLIC_CALENDAR -> Expect.ok(Ev.PUBLIC);
            default -> throw new IllegalArgumentException(route.name());
        };
    }

    private MockHttpServletRequestBuilder listRequest(Route route) {
        String path =
                switch (route) {
                    case MY_CALENDAR -> "/api/events";
                    case GROUP_CALENDAR -> "/api/groups/" + groupA.getId() + "/events";
                    case OTHER_GROUP_CALENDAR -> "/api/groups/" + groupB.getId() + "/events";
                    case FRIEND_CALENDAR ->
                            "/api/friends/" + people.get(Viewer.OWNER).getId() + "/events";
                    case PUBLIC_CALENDAR -> "/api/public/events";
                    default -> throw new IllegalArgumentException(route.name());
                };
        return get(path)
                .param("from", day.atStartOfDay(SEOUL).toOffsetDateTime().toString())
                .param("to", day.plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime().toString());
    }

    /** 가용시간은 이름과 시간만 돌려준다. 비공개 일정 시간(14~15시)에 주인은 "바쁨"으로만 빠지고 내용은 나오지 않는다. */
    private void availability(Viewer viewer) throws Exception {
        boolean member = viewer == Viewer.OWNER || viewer == Viewer.GROUP_MEMBER;
        MockHttpServletRequestBuilder request =
                get("/api/groups/" + groupA.getId() + "/availability")
                        .param("from", day.toString())
                        .param("to", day.toString())
                        .param("dayStart", "14:00")
                        .param("dayEnd", "15:00")
                        .param("minMinutes", "60")
                        .param("timeZone", SEOUL.getId());
        String body = check(viewer, "AVAILABILITY", request, member ? Expect.ok() : denied(viewer));
        if (!member) return;
        JsonNode slots = json.readTree(body);
        assertEquals(1, slots.size(), "14~15시 슬롯 하나만 있어야 한다: " + body);
        List<String> free = new ArrayList<>();
        slots.get(0).get("availablePeople").forEach(n -> free.add(n.asText()));
        assertEquals(List.of(MEMBER_NAME), free, "비공개 일정 시간에 주인은 바쁨으로 빠져야 한다");
    }

    static Stream<Arguments> mutations() {
        return Arrays.stream(Viewer.values())
                .filter(v -> v != Viewer.OWNER)
                .flatMap(v -> Arrays.stream(Mutation.values()).map(m -> Arguments.of(v, m)));
    }

    @ParameterizedTest(name = "{0} × {1}")
    @MethodSource("mutations")
    void nobodyButTheOwnerCanChangeAnEvent(Viewer viewer, Mutation mutation) throws Exception {
        for (Ev ev : Ev.values()) {
            Long id = ids.get(ev);
            Event before = events.findById(id).orElseThrow();
            OffsetDateTime start = before.getStartAt().plusDays(1),
                    end = before.getEndAt().plusDays(1);
            MockHttpServletRequestBuilder request =
                    switch (mutation) {
                        case PUT ->
                                put("/api/events/" + id)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "title",
                                                                "가로챈 제목",
                                                                "startAt",
                                                                start.toString(),
                                                                "endAt",
                                                                end.toString(),
                                                                "allDay",
                                                                false,
                                                                "visibilities",
                                                                List.of("PUBLIC"),
                                                                "color",
                                                                "#168CF2")));
                        case MOVE ->
                                patch("/api/events/" + id + "/move")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "startAt",
                                                                start.toString(),
                                                                "endAt",
                                                                end.toString())));
                        case DELETE -> delete("/api/events/" + id);
                    };
            int status = mvc.perform(as(viewer, request)).andReturn().getResponse().getStatus();
            assertEquals(denied(viewer).status(), status, viewer + " " + mutation + " " + ev);
            Event after =
                    events.findById(id).orElseThrow(() -> new AssertionError(ev + " 일정이 지워졌다"));
            assertEquals(before.getTitle(), after.getTitle());
            assertEquals(before.getStartAt().toInstant(), after.getStartAt().toInstant());
            assertEquals(before.getVisibilities(), after.getVisibilities());
        }
    }

    /** 요청을 보내고 상태와 노출 범위를 확인한 뒤 본문을 돌려준다. */
    private String check(
            Viewer viewer, String label, MockHttpServletRequestBuilder request, Expect expect)
            throws Exception {
        MvcResult result = mvc.perform(as(viewer, request)).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(
                expect.status(),
                result.getResponse().getStatus(),
                viewer + " × " + label + ": " + body);
        if (expect.status() != 200) return body;
        for (Ev ev : Ev.values()) {
            if (expect.visible().contains(ev)) {
                for (String part : List.of(" 제목", " 메모", " 장소"))
                    assertTrue(
                            body.contains(ev.marker() + part),
                            viewer + " × " + label + ": " + ev + part + "이(가) 보여야 한다");
            } else
                assertFalse(
                        body.contains(ev.marker()),
                        viewer + " × " + label + ": " + ev + " 일정이 새어 나갔다 → " + body);
        }
        // 일정 응답의 작성자 정보에는 연락처(이메일)가 실리면 안 된다.
        assertFalse(body.contains(OWNER_EMAIL), viewer + " × " + label + ": 주인 이메일이 노출됐다");
        return body;
    }

    private static Expect denied(Viewer viewer) {
        return Expect.status(viewer == Viewer.ANONYMOUS ? 401 : 403);
    }

    private MockHttpServletRequestBuilder as(Viewer viewer, MockHttpServletRequestBuilder request) {
        return viewer == Viewer.ANONYMOUS
                ? request
                : request.header(
                        "Authorization", "Bearer " + jwt.create(people.get(viewer).getId()));
    }

    private User person(Viewer viewer, String email, String name) {
        User u = users.save(new User(email, name, "hash"));
        people.put(viewer, u);
        return u;
    }

    private Group group(String name, User owner, String token) {
        Group g = new Group();
        g.setName(name);
        g.setOwner(owner);
        g.setInviteToken(token);
        return groups.save(g);
    }

    private void join(Group g, User u, GroupMember.Role role) {
        GroupMember m = new GroupMember();
        m.setGroup(g);
        m.setUser(u);
        m.setRole(role);
        members.save(m);
    }

    private void friendship(User requester, User receiver, Friendship.Status status) {
        Friendship f = new Friendship();
        f.setRequester(requester);
        f.setReceiver(receiver);
        f.setStatus(status);
        friendships.save(f);
    }
}
