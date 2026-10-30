package app.toogeduler.repo;

import app.toogeduler.domain.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    List<PushSubscription> findByUserId(Long userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    /** 본인 구독만 지운다. 다른 사람의 endpoint 를 보내도 아무것도 지워지지 않는다. */
    long deleteByUserIdAndEndpoint(Long userId, String endpoint);
}
