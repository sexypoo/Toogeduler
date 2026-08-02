package app.toogeduler.repo;
import app.toogeduler.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface GroupMemberRepository extends JpaRepository<GroupMember,Long>{
    List<GroupMember> findByUserId(Long userId);
    List<GroupMember> findByGroupId(Long groupId);
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId,Long userId);
    boolean existsByGroupIdAndUserId(Long groupId,Long userId);
}

