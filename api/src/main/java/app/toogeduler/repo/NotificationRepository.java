package app.toogeduler.repo;

import app.toogeduler.domain.Notification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification,Long> {
    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUserId(Long userId);
    long countByUserIdAndReadAtIsNull(Long userId);
    boolean existsByUserIdAndDedupeKey(Long userId,String dedupeKey);
    @Modifying @Query("update Notification n set n.readAt=:now where n.user.id=:userId and n.readAt is null")
    int markAllRead(@Param("userId")Long userId,@Param("now")Instant now);
}
