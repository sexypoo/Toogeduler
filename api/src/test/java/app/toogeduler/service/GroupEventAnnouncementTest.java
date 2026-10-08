package app.toogeduler.service;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroupEventAnnouncementTest {
    @Test void otherMembersAreNotifiedOnceAndTheOwnerIsNot(){
        NotificationRepository notifications=mock(NotificationRepository.class);GroupMemberRepository members=mock(GroupMemberRepository.class);
        NotificationService service=new NotificationService(notifications,mock(EventRepository.class),members);
        User minji=user(1L,"김민지"),jun=user(2L,"이준"),sora=user(3L,"박소라");
        Group weekend=group(10L,"주말 탐험대"),friends=group(11L,"소친친친");
        when(members.findByGroupId(10L)).thenReturn(List.of(member(weekend,minji),member(weekend,jun),member(weekend,sora)));
        when(members.findByGroupId(11L)).thenReturn(List.of(member(friends,minji),member(friends,jun)));
        Event event=new Event();event.setId(99L);event.setOwner(minji);event.setTitle("주말 탐험대 모임");
        event.setStartAt(OffsetDateTime.parse("2026-10-09T01:30:00Z"));event.setEndAt(OffsetDateTime.parse("2026-10-09T02:30:00Z"));
        event.getGroups().add(weekend);event.getGroups().add(friends);

        service.announceGroupEvent(event);

        // 준은 두 그룹에 모두 있지만 한 번만, 소라는 한 번, 일정을 만든 민지는 받지 않는다.
        verify(notifications,times(2)).save(any(Notification.class));
        verify(notifications).save(argThat(n->n.getUser()==jun&&n.getType()==Notification.Type.GROUP_EVENT
            &&n.getMessage().equals("김민지님이 주말 탐험대에 10월 9일 (금) 오전 10:30 '주말 탐험대 모임' 일정을 잡았어요.")
            &&n.getLink().startsWith("/?group=")));
        verify(notifications).save(argThat(n->n.getUser()==sora));
        verify(notifications,never()).save(argThat(n->n.getUser()==minji));
    }

    private static User user(Long id,String name){User u=new User(name+"@example.com",name,"hash");u.setId(id);return u;}
    private static Group group(Long id,String name){Group g=new Group();g.setId(id);g.setName(name);return g;}
    private static GroupMember member(Group g,User u){GroupMember m=new GroupMember();m.setGroup(g);m.setUser(u);return m;}
}
