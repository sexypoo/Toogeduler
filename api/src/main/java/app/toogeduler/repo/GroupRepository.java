package app.toogeduler.repo;

import app.toogeduler.domain.Group;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<Group, Long> {
    Optional<Group> findByInviteToken(String token);

    List<Group> findByOwnerId(Long ownerId);

    @Query("select count(g) from Group g where g.createdAt >= :from and g.createdAt < :to")
    long countCreatedBetween(@Param("from") Instant from, @Param("to") Instant to);
}
