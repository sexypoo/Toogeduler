package app.toogeduler.repo;

import app.toogeduler.domain.Group;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, Long> {
    Optional<Group> findByInviteToken(String token);

    List<Group> findByOwnerId(Long ownerId);
}
