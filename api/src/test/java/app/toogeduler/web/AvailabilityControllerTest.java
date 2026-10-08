package app.toogeduler.web;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AvailabilityControllerTest {
    @Test void pastTimesAreNeverSuggested(){
        GroupRepository groups=mock(GroupRepository.class);GroupMemberRepository members=mock(GroupMemberRepository.class);EventRepository events=mock(EventRepository.class);
        AvailabilityController controller=new AvailabilityController(groups,members,events);
        // 한국 시간 10월 8일 밤 11시 30분에 검색한다.
        controller.clock=Clock.fixed(Instant.parse("2026-10-08T14:30:00Z"),ZoneOffset.UTC);
        User me=new User("me@example.com","나","hash");me.setId(1L);
        Group group=new Group();group.setId(5L);
        GroupMember membership=new GroupMember();membership.setGroup(group);membership.setUser(me);
        when(groups.findById(5L)).thenReturn(Optional.of(group));
        when(members.existsByGroupIdAndUserId(5L,1L)).thenReturn(true);
        when(members.findByGroupId(5L)).thenReturn(List.of(membership));
        when(events.busyEvents(anyCollection(),any(),any())).thenReturn(List.of());

        List<AvailabilityController.Slot> slots=controller.find(new UsernamePasswordAuthenticationToken(me,null,List.of()),5L,
            LocalDate.of(2026,10,8),LocalDate.of(2026,10,9),LocalTime.of(9,0),LocalTime.of(22,0),60,"Asia/Seoul");

        OffsetDateTime now=OffsetDateTime.parse("2026-10-08T23:30:00+09:00");
        assertFalse(slots.isEmpty());
        assertTrue(slots.stream().noneMatch(s->s.startAt().isBefore(now)),"이미 지난 시간을 추천하면 안 된다");
        assertEquals(OffsetDateTime.parse("2026-10-09T09:00:00+09:00").toInstant(),slots.getFirst().startAt().toInstant(),"가장 빠른 시간은 다음 날 아침");
    }
}
