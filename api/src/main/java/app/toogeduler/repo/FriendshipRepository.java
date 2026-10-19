package app.toogeduler.repo;

import app.toogeduler.domain.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    List<Friendship> findByRequesterIdOrReceiverId(Long requester, Long receiver);
}
