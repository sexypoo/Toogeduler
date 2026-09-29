package app.toogeduler.service;

import app.toogeduler.domain.*;
import app.toogeduler.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 회원 탈퇴 처리.
 *
 * App Store 심사 가이드라인 5.1.1(v)와 Google Play 데이터 삭제 정책은
 * 계정을 만들 수 있는 앱이 앱 안에서 계정 삭제도 제공하도록 요구한다.
 * 다른 사용자의 데이터는 보존하면서 탈퇴자의 개인 데이터만 완전히 지운다.
 */
@Service @RequiredArgsConstructor @Slf4j
public class AccountService {
    private final UserRepository users;
    private final EventRepository events;
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final FriendshipRepository friendships;
    private final NotificationRepository notifications;
    private final GroupCleanupService groupCleanup;

    @Transactional
    public void deleteAccount(Long userId) {
        User user = users.findById(userId).orElseThrow();

        // 1) 알림
        notifications.deleteAll(notifications.findByUserId(userId));

        // 2) 친구 관계 (요청자·수신자 양쪽)
        friendships.deleteAll(friendships.findByRequesterIdOrReceiverId(userId, userId));

        // 3) 내가 소유한 그룹: 남은 멤버가 있으면 가장 오래된 멤버에게 양도하고,
        //    아무도 없으면 그룹 자체를 삭제한다.
        for (Group group : groups.findByOwnerId(userId)) {
            List<GroupMember> groupMembers = members.findByGroupId(group.getId());
            GroupMember successor = groupMembers.stream()
                    .filter(m -> !m.getUser().getId().equals(userId))
                    .min(Comparator.comparing(GroupMember::getJoinedAt))
                    .orElse(null);
            if (successor != null) {
                group.setOwner(successor.getUser());
                successor.setRole(GroupMember.Role.OWNER);
                members.save(successor);
                groups.save(group);
                log.info("Transferred group {} ownership to user {} on account deletion", group.getId(), successor.getUser().getId());
            } else {
                // 남은 멤버가 없으면 일정 연결을 먼저 끊고 그룹을 삭제한다.
                groupCleanup.deleteGroup(group);
            }
        }

        // 4) 내 그룹 멤버십
        members.deleteAll(members.findByUserId(userId));

        // 5) 내가 만든 일정 (event_visibilities·event_groups 연결 행도 함께 정리된다)
        events.deleteAll(events.findByOwnerId(userId));

        // 6) 계정
        users.delete(user);
        log.info("Deleted account {}", userId);
    }
}
