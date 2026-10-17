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

    @Test void blocksMergeRunsOfTheSamePeopleAndSplitWhereSomeoneGetsBusy(){
        GroupRepository groups=mock(GroupRepository.class);GroupMemberRepository members=mock(GroupMemberRepository.class);EventRepository events=mock(EventRepository.class);
        AvailabilityController controller=new AvailabilityController(groups,members,events);
        controller.clock=Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"),ZoneOffset.UTC);
        User me=user(1L,"나"),sora=user(2L,"소라");
        Group group=new Group();group.setId(5L);
        when(groups.findById(5L)).thenReturn(Optional.of(group));
        when(members.existsByGroupIdAndUserId(5L,1L)).thenReturn(true);
        when(members.findByGroupId(5L)).thenReturn(List.of(member(group,me),member(group,sora)));
        // 소라는 10월 9일 12:00–13:00(한국 시간)에 바쁘다.
        Event lunch=new Event();lunch.setId(9L);lunch.setOwner(sora);lunch.setTitle("점심");
        lunch.setStartAt(OffsetDateTime.parse("2026-10-09T12:00:00+09:00"));lunch.setEndAt(OffsetDateTime.parse("2026-10-09T13:00:00+09:00"));
        when(events.busyEvents(anyCollection(),any(),any())).thenReturn(List.of(lunch));

        List<AvailabilityController.Slot> blocks=controller.blocks(new UsernamePasswordAuthenticationToken(me,null,List.of()),5L,
            LocalDate.of(2026,10,9),LocalDate.of(2026,10,9),LocalTime.of(9,0),LocalTime.of(18,0),60,"Asia/Seoul");

        // 30분 단위 후보 18개가 아니라, 모두 되는 오전 · 나만 되는 점심 · 모두 되는 오후 세 덩어리로 온다.
        assertEquals(3,blocks.size());
        assertBlock(blocks.get(0),"2026-10-09T09:00:00+09:00","2026-10-09T12:00:00+09:00",true);
        assertBlock(blocks.get(1),"2026-10-09T12:00:00+09:00","2026-10-09T13:00:00+09:00",false);
        assertEquals(List.of("나"),blocks.get(1).availablePeople());
        assertBlock(blocks.get(2),"2026-10-09T13:00:00+09:00","2026-10-09T18:00:00+09:00",true);
    }

    @Test void blocksShorterThanTheMinimumAreDropped(){
        GroupRepository groups=mock(GroupRepository.class);GroupMemberRepository members=mock(GroupMemberRepository.class);EventRepository events=mock(EventRepository.class);
        AvailabilityController controller=new AvailabilityController(groups,members,events);
        controller.clock=Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"),ZoneOffset.UTC);
        User me=user(1L,"나");Group group=new Group();group.setId(5L);
        when(groups.findById(5L)).thenReturn(Optional.of(group));
        when(members.existsByGroupIdAndUserId(5L,1L)).thenReturn(true);
        when(members.findByGroupId(5L)).thenReturn(List.of(member(group,me)));
        // 10:30–17:00이 막혀 있어 10:00–10:30(30분)과 17:00–18:00(60분)만 빈다.
        Event work=new Event();work.setId(9L);work.setOwner(me);work.setTitle("수업");
        work.setStartAt(OffsetDateTime.parse("2026-10-09T10:30:00+09:00"));work.setEndAt(OffsetDateTime.parse("2026-10-09T17:00:00+09:00"));
        when(events.busyEvents(anyCollection(),any(),any())).thenReturn(List.of(work));

        List<AvailabilityController.Slot> blocks=controller.blocks(new UsernamePasswordAuthenticationToken(me,null,List.of()),5L,
            LocalDate.of(2026,10,9),LocalDate.of(2026,10,9),LocalTime.of(10,0),LocalTime.of(18,0),60,"Asia/Seoul");

        assertEquals(1,blocks.size());
        assertBlock(blocks.getFirst(),"2026-10-09T17:00:00+09:00","2026-10-09T18:00:00+09:00",true);
    }

    private static void assertBlock(AvailabilityController.Slot block,String start,String end,boolean everyone){
        assertEquals(OffsetDateTime.parse(start).toInstant(),block.startAt().toInstant());
        assertEquals(OffsetDateTime.parse(end).toInstant(),block.endAt().toInstant());
        assertEquals(everyone,block.everyoneAvailable());
    }
    private static User user(Long id,String name){User u=new User(name+"@example.com",name,"hash");u.setId(id);return u;}
    private static GroupMember member(Group g,User u){GroupMember m=new GroupMember();m.setGroup(g);m.setUser(u);return m;}
}
