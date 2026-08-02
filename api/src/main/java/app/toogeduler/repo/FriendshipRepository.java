package app.toogeduler.repo;
import app.toogeduler.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface FriendshipRepository extends JpaRepository<Friendship,Long>{
    List<Friendship> findByRequesterIdOrReceiverId(Long requester,Long receiver);
}

