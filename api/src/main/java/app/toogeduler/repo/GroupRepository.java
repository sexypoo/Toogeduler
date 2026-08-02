package app.toogeduler.repo;
import app.toogeduler.domain.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface GroupRepository extends JpaRepository<Group,Long>{ Optional<Group> findByInviteToken(String token); }

