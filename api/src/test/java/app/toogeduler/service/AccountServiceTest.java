package app.toogeduler.service;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountServiceTest {
    private UserRepository users;
    private EventRepository events;
    private GroupRepository groups;
    private GroupMemberRepository members;
    private FriendshipRepository friendships;
    private NotificationRepository notifications;
    private GroupCleanupService groupCleanup;
    private AccountService service;
    private User leaving;

    @BeforeEach void setUp(){
        users=mock(UserRepository.class);
        events=mock(EventRepository.class);
        groups=mock(GroupRepository.class);
        members=mock(GroupMemberRepository.class);
        friendships=mock(FriendshipRepository.class);
        notifications=mock(NotificationRepository.class);
        // 실제 정리 순서(일정 분리 → 멤버 삭제 → 그룹 삭제)를 검증하기 위해 진짜 구현을 사용한다.
        groupCleanup=new GroupCleanupService(events,groups,members);
        service=new AccountService(users,events,groups,members,friendships,notifications,groupCleanup);

        leaving=new User("leaving@toogeduler.app","떠나는사람","hash");leaving.setId(1L);
        when(users.findById(1L)).thenReturn(Optional.of(leaving));
        when(notifications.findByUserId(1L)).thenReturn(List.of());
        when(friendships.findByRequesterIdOrReceiverId(1L,1L)).thenReturn(List.of());
        when(members.findByUserId(1L)).thenReturn(List.of());
        when(events.findByOwnerId(1L)).thenReturn(List.of());
        when(groups.findByOwnerId(1L)).thenReturn(List.of());
    }

    @Test void deletesOwnDataAndAccount(){
        Event mine=new Event();mine.setOwner(leaving);mine.setId(10L);
        Notification note=new Notification();note.setUser(leaving);
        Friendship friend=new Friendship();friend.setRequester(leaving);
        when(events.findByOwnerId(1L)).thenReturn(List.of(mine));
        when(notifications.findByUserId(1L)).thenReturn(List.of(note));
        when(friendships.findByRequesterIdOrReceiverId(1L,1L)).thenReturn(List.of(friend));

        service.deleteAccount(1L);

        verify(events).deleteAll(List.of(mine));
        verify(notifications).deleteAll(List.of(note));
        verify(friendships).deleteAll(List.of(friend));
        verify(users).delete(leaving);
    }

    @Test void transfersOwnedGroupToEarliestRemainingMember(){
        User staying=new User("staying@toogeduler.app","남는사람","hash");staying.setId(2L);
        Group group=new Group();group.setId(7L);group.setOwner(leaving);group.setInviteToken("token");

        GroupMember leavingMember=new GroupMember();leavingMember.setGroup(group);leavingMember.setUser(leaving);
        leavingMember.setRole(GroupMember.Role.OWNER);leavingMember.setJoinedAt(Instant.parse("2026-01-01T00:00:00Z"));
        GroupMember stayingMember=new GroupMember();stayingMember.setGroup(group);stayingMember.setUser(staying);
        stayingMember.setJoinedAt(Instant.parse("2026-02-01T00:00:00Z"));

        when(groups.findByOwnerId(1L)).thenReturn(List.of(group));
        when(members.findByGroupId(7L)).thenReturn(List.of(leavingMember,stayingMember));

        service.deleteAccount(1L);

        assertEquals(staying,group.getOwner(),"남은 멤버에게 그룹장이 넘어가야 한다");
        assertEquals(GroupMember.Role.OWNER,stayingMember.getRole());
        verify(groups,never()).delete(any(Group.class));
        verify(users).delete(leaving);
    }

    @Test void deletesOwnedGroupWhenNoMemberRemains(){
        Group group=new Group();group.setId(8L);group.setOwner(leaving);group.setInviteToken("token");
        GroupMember onlyMember=new GroupMember();onlyMember.setGroup(group);onlyMember.setUser(leaving);
        onlyMember.setRole(GroupMember.Role.OWNER);onlyMember.setJoinedAt(Instant.now());

        Event shared=new Event();shared.setId(20L);shared.getGroups().add(group);
        when(groups.findByOwnerId(1L)).thenReturn(List.of(group));
        when(members.findByGroupId(8L)).thenReturn(List.of(onlyMember));
        when(events.findByGroupId(8L)).thenReturn(List.of(shared));

        service.deleteAccount(1L);

        assertTrue(shared.getGroups().isEmpty(),"삭제되는 그룹은 일정에서 먼저 분리되어야 한다");
        verify(groups).delete(group);
        verify(users).delete(leaving);
    }
}
