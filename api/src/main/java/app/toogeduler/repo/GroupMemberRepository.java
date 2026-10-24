package app.toogeduler.repo;

import app.toogeduler.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    List<GroupMember> findByUserId(Long userId);

    List<GroupMember> findByGroupId(Long groupId);

    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    long countByGroupId(Long groupId);

    /**
     * 그룹을 만들 때 그룹장에게 자동으로 생기는 OWNER 멤버십은 가입으로 세지 않는다. 그룹장을 넘기면 역할이 서로 바뀌므로, 같은 주 안에 넘긴 경우 누가 세어지는지는
     * 바뀌어도 가입 수는 같다.
     */
    @Query(
            "select count(m) from GroupMember m where m.role = 'MEMBER' and m.joinedAt >= :from and m.joinedAt < :to")
    long countJoinsBetween(@Param("from") Instant from, @Param("to") Instant to);
}
