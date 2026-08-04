package app.toogeduler.repo;

import app.toogeduler.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification,Long> {
    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndReadAtIsNull(Long userId);
    boolean existsByUserIdAndDedupeKey(Long userId,String dedupeKey);
}
