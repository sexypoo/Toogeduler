package app.toogeduler.repo;

import app.toogeduler.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop"})
class GroupCalendarMembershipTest {
    @Autowired TestEntityManager em;
    @Autowired EventRepository events;
    @Autowired GroupMemberRepository members;

    @Test void departedMembersEventsLeaveTheGroupCalendar(){
        User owner=em.persist(new User("owner@example.com","그룹장","hash"));
        User leaver=em.persist(new User("leaver@example.com","떠난 사람","hash"));
        Group group=new Group();group.setName("주말 탐험대");group.setOwner(owner);group.setInviteToken("token-1");em.persist(group);
        member(group,owner,GroupMember.Role.OWNER);GroupMember leaving=member(group,leaver,GroupMember.Role.MEMBER);
        OffsetDateTime start=OffsetDateTime.of(2026,10,10,10,0,0,0,ZoneOffset.UTC);
        Event ownersEvent=groupEvent(owner,group,start,"그룹장 일정");Event leaversEvent=groupEvent(leaver,group,start,"떠난 사람 일정");
        em.flush();
        assertThat(events.groupCalendar(group.getId(),start.minusDays(1),start.plusDays(1))).extracting(Event::getId).containsExactlyInAnyOrder(ownersEvent.getId(),leaversEvent.getId());

        members.delete(leaving);em.flush();em.clear();

        List<Event> after=events.groupCalendar(group.getId(),start.minusDays(1),start.plusDays(1));
        assertThat(after).extracting(Event::getId).containsExactly(ownersEvent.getId());
    }

    private GroupMember member(Group group,User user,GroupMember.Role role){GroupMember m=new GroupMember();m.setGroup(group);m.setUser(user);m.setRole(role);return em.persist(m);}
    private Event groupEvent(User owner,Group group,OffsetDateTime start,String title){
        Event e=new Event();e.setOwner(owner);e.setTitle(title);e.setStartAt(start);e.setEndAt(start.plusHours(1));
        e.setVisibility(Event.Visibility.GROUP);e.getVisibilities().add(Event.Visibility.GROUP);e.getGroups().add(group);return em.persist(e);
    }
}
