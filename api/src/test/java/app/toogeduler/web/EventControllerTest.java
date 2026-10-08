package app.toogeduler.web;

import app.toogeduler.domain.Event;
import app.toogeduler.domain.Group;
import app.toogeduler.domain.User;
import app.toogeduler.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventControllerTest {
    private EventRepository events;
    private GroupRepository groups;
    private GroupMemberRepository members;
    private EventController controller;
    private User owner;
    private User otherUser;
    private Event event;

    @BeforeEach
    void setUp() {
        events=mock(EventRepository.class);
        groups=mock(GroupRepository.class);
        members=mock(GroupMemberRepository.class);
        controller=new EventController(events,groups,members);
        owner=user(1L,"owner@toogeduler.app","일정 주인");
        otherUser=user(2L,"other@toogeduler.app","다른 사용자");
        event=new Event();event.setId(10L);event.setOwner(owner);event.setTitle("내 일정");
        event.setStartAt(OffsetDateTime.parse("2026-08-03T10:00:00+09:00"));
        event.setEndAt(OffsetDateTime.parse("2026-08-03T11:00:00+09:00"));
        when(events.findById(10L)).thenReturn(Optional.of(event));
    }

    @Test
    void publicEventDoesNotExposeOwnerEmailOrFriendCode() {
        // /api/public/events 는 로그인 없이 열려 있으므로 작성자 정보에 연락처가 실리면 안 된다.
        event.getVisibilities().add(Event.Visibility.PUBLIC);event.setPublicToken("public-token");
        when(events.findByPublicToken("public-token")).thenReturn(Optional.of(event));
        @SuppressWarnings("unchecked") Map<String,Object> publicOwner=(Map<String,Object>)controller.publicEvent("public-token").get("owner");
        assertEquals(Set.of("id","name","avatarUrl"),publicOwner.keySet());
    }

    @Test
    void anotherUserCannotMoveEvent() {
        ApiException error=assertThrows(ApiException.class,()->controller.move(auth(otherUser),10L,Map.of(
            "startAt","2026-08-03T12:00:00+09:00","endAt","2026-08-03T13:00:00+09:00")));
        assertEquals(HttpStatus.FORBIDDEN,error.status);
        verify(events,never()).save(any());
    }

    @Test
    void anotherUserCannotDeleteEvent() {
        ApiException error=assertThrows(ApiException.class,()->controller.delete(auth(otherUser),10L));
        assertEquals(HttpStatus.FORBIDDEN,error.status);
        verify(events,never()).delete(any());
    }

    @Test
    void anotherUserCannotUpdateEvent() {
        EventController.EventInput input=new EventController.EventInput("바꾼 제목","","",event.getStartAt(),event.getEndAt(),false,Set.of(Event.Visibility.PRIVATE),null,"",null,"#168CF2",Set.of());
        ApiException error=assertThrows(ApiException.class,()->controller.update(auth(otherUser),10L,input));
        assertEquals(HttpStatus.FORBIDDEN,error.status);
        verify(events,never()).save(any());
    }

    @Test
    void eventCanBeSharedWithGroupAndFriendsAtTheSameTime() {
        Group group=new Group();group.setId(7L);group.setName("여행 친구");group.setOwner(owner);group.setInviteToken("invite");
        when(members.existsByGroupIdAndUserId(7L,1L)).thenReturn(true);
        when(groups.findById(7L)).thenReturn(Optional.of(group));
        when(events.save(any(Event.class))).thenAnswer(invocation->{Event saved=invocation.getArgument(0);saved.setId(20L);return saved;});
        EventController.EventInput input=new EventController.EventInput("공유 일정","","",OffsetDateTime.parse("2026-08-04T10:00:00+09:00"),OffsetDateTime.parse("2026-08-04T11:00:00+09:00"),false,EnumSet.of(Event.Visibility.GROUP,Event.Visibility.FRIENDS),null,"",30,"#168CF2",Set.of(7L));

        Map<String,Object> result=controller.create(auth(owner),input);

        assertEquals(Set.of(Event.Visibility.GROUP,Event.Visibility.FRIENDS),new HashSet<>((Collection<Event.Visibility>)result.get("visibilities")));
        Event saved=verifyAndGetSavedEvent();
        assertTrue(saved.getGroups().contains(group));
        assertEquals(30,saved.getReminderMinutes());
    }

    private Event verifyAndGetSavedEvent(){var captor=org.mockito.ArgumentCaptor.forClass(Event.class);verify(events).save(captor.capture());return captor.getValue();}
    private Authentication auth(User user){return new UsernamePasswordAuthenticationToken(user,null,List.of());}
    private User user(Long id,String email,String name){User user=new User(email,name,"hash");user.setId(id);return user;}
}
