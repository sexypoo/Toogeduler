package app.toogeduler.service;

import app.toogeduler.config.VapidKeys;
import app.toogeduler.domain.PushSubscription;
import app.toogeduler.domain.User;
import app.toogeduler.repo.PushSubscriptionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 저장된 알림을 그 사용자가 구독한 브라우저들로 보낸다. 발송은 알림 저장 트랜잭션이 커밋된 뒤 별도 스레드에서 하므로, 푸시 서비스가 느리거나 실패해도 알림 저장과 요청
 * 응답에는 영향이 없다.
 */
@Service
@Slf4j
public class PushService {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PushSubscriptionRepository subscriptions;
    private final PushSender sender;
    private final VapidKeys vapid;
    private final Executor executor;

    @Autowired
    public PushService(
            PushSubscriptionRepository subscriptions, PushSender sender, VapidKeys vapid) {
        this(subscriptions, sender, vapid, Executors.newVirtualThreadPerTaskExecutor());
    }

    /** 테스트는 executor 로 Runnable::run 을 넘겨 발송을 같은 스레드에서 끝낸다. */
    public PushService(
            PushSubscriptionRepository subscriptions,
            PushSender sender,
            VapidKeys vapid,
            Executor executor) {
        this.subscriptions = subscriptions;
        this.sender = sender;
        this.vapid = vapid;
        this.executor = executor;
    }

    /** 트랜잭션 안이면 커밋된 뒤에 보낸다. 롤백되면 보내지 않는다. VAPID 키가 없으면 아무것도 하지 않는다. */
    public void sendAfterCommit(User user, String title, String message, String url) {
        if (!vapid.configured()) return;
        List<PushSubscription> targets = subscriptions.findByUserId(user.getId());
        if (targets.isEmpty()) return;
        byte[] payload = payload(title, message, url);
        Runnable send = () -> executor.execute(() -> targets.forEach(s -> deliver(s, payload)));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        send.run();
                    }
                });
    }

    /** 한 구독으로 보낸다. 푸시 서비스가 404·410 으로 만료를 알리면 그 구독을 지운다. 그 밖의 실패는 기록만 한다. */
    void deliver(PushSubscription subscription, byte[] payload) {
        try {
            int status = sender.send(subscription, payload);
            if (status == 404 || status == 410) {
                subscriptions.deleteById(subscription.getId());
                log.info("Removed expired push subscription {}", subscription.getId());
            } else if (status >= 400) {
                log.warn(
                        "Push to subscription {} failed with HTTP {}",
                        subscription.getId(),
                        status);
            }
        } catch (Exception e) {
            log.warn("Push to subscription {} failed: {}", subscription.getId(), e.toString());
        }
    }

    /** 서비스 워커가 받는 내용. 알림에 보일 제목·문구와 누르면 열 주소만 담는다. */
    private static byte[] payload(String title, String message, String url) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("message", message);
        body.put("url", url);
        try {
            return JSON.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
