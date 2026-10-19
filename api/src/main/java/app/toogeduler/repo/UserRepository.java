package app.toogeduler.repo;

import app.toogeduler.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByFriendCodeIgnoreCase(String friendCode);
}
