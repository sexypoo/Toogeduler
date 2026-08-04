package app.toogeduler.repo;
import app.toogeduler.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User,Long>{ Optional<User> findByEmailIgnoreCase(String email);Optional<User> findByFriendCodeIgnoreCase(String friendCode); }
