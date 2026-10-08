package app.toogeduler.web;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import app.toogeduler.service.GroupCleanupService;
import app.toogeduler.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupKickTest {
    @Test void kickingAMemberInvalidatesTheOldInviteLink(){
        GroupRepository groups=mock(GroupRepository.class);GroupMemberRepository members=mock(GroupMemberRepository.class);
        GroupController controller=new GroupController(groups,members,mock(EventRepository.class),mock(NotificationService.class),mock(GroupCleanupService.class));
        User owner=new User("owner@example.com","그룹장","hash");owner.setId(1L);
        User kicked=new User("kicked@example.com","내보낼 사람","hash");kicked.setId(2L);
        Group group=new Group();group.setId(10L);group.setOwner(owner);group.setInviteToken("old-token");
        GroupMember membership=new GroupMember();membership.setGroup(group);membership.setUser(kicked);
        when(groups.findById(10L)).thenReturn(Optional.of(group));
        when(members.existsByGroupIdAndUserId(10L,1L)).thenReturn(true);
        when(members.findByGroupIdAndUserId(10L,2L)).thenReturn(Optional.of(membership));

        controller.remove(new UsernamePasswordAuthenticationToken(owner,null,List.of()),10L,2L);

        verify(members).delete(membership);
        assertNotEquals("old-token",group.getInviteToken());
        verify(groups).save(group);
    }
}
