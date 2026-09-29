package app.toogeduler.service;

import app.toogeduler.domain.Event;
import app.toogeduler.domain.Group;
import app.toogeduler.repo.EventRepository;
import app.toogeduler.repo.GroupMemberRepository;
import app.toogeduler.repo.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 삭제 시 참조 정리.
 *
 * event_groups 조인 테이블은 Event 가 소유하고 Group 에는 역방향 매핑이 없다.
 * 따라서 그룹 행을 먼저 지우면 남은 조인 행 때문에 외래 키 제약 위반이 발생한다.
 * 그룹을 삭제하는 모든 경로는 이 서비스를 거쳐야 한다.
 */
@Service @RequiredArgsConstructor
public class GroupCleanupService {
    private final EventRepository events;
    private final GroupRepository groups;
    private final GroupMemberRepository members;

    /** 삭제될 그룹을 참조하는 모든 일정에서 그룹 연결을 끊는다. */
    @Transactional
    public void detachFromEvents(Group group) {
        for (Event event : events.findByGroupId(group.getId())) {
            event.getGroups().removeIf(g -> g.getId().equals(group.getId()));
            events.save(event);
        }
    }

    /** 일정 연결과 멤버십을 정리한 뒤 그룹을 삭제한다. */
    @Transactional
    public void deleteGroup(Group group) {
        detachFromEvents(group);
        members.deleteAll(members.findByGroupId(group.getId()));
        groups.delete(group);
    }
}
