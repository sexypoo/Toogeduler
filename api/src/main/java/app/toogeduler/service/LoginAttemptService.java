package app.toogeduler.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 로그인 무차별 대입(brute force) 방어.
 *
 * 같은 키(이메일 또는 IP)로 연속 실패하면 일정 시간 잠근다.
 * 인스턴스 메모리에만 저장하므로 서버를 재시작하면 초기화되고 다중 인스턴스에서는 공유되지 않는다.
 * 여러 인스턴스로 확장할 때는 Redis 같은 공유 저장소로 옮겨야 한다.
 */
@Service @Slf4j
public class LoginAttemptService {
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_KEYS = 20_000;

    private record Attempt(AtomicInteger failures, Instant firstFailureAt, Instant lockedUntil) {}

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 잠겨 있으면 남은 시간(초)을, 아니면 0을 반환한다. */
    public long lockedSecondsRemaining(String key) {
        Attempt attempt = attempts.get(normalize(key));
        if (attempt == null || attempt.lockedUntil() == null) return 0;
        long remaining = Duration.between(Instant.now(), attempt.lockedUntil()).getSeconds();
        return Math.max(remaining, 0);
    }

    public void recordFailure(String key) {
        String k = normalize(key);
        Instant now = Instant.now();
        if (attempts.size() > MAX_TRACKED_KEYS) purgeExpired(now);
        attempts.compute(k, (ignored, existing) -> {
            // 관찰 창을 벗어난 기록은 새로 시작한다.
            if (existing == null || Duration.between(existing.firstFailureAt(), now).compareTo(FAILURE_WINDOW) > 0) {
                return new Attempt(new AtomicInteger(1), now, null);
            }
            int failures = existing.failures().incrementAndGet();
            if (failures >= MAX_FAILURES) {
                log.warn("Login locked for 15 minutes after {} failed attempts", failures);
                return new Attempt(existing.failures(), existing.firstFailureAt(), now.plus(LOCK_DURATION));
            }
            return new Attempt(existing.failures(), existing.firstFailureAt(), existing.lockedUntil());
        });
    }

    public void recordSuccess(String key) {
        attempts.remove(normalize(key));
    }

    /**
     * 만료된 기록을 제거하고, 그래도 상한을 넘으면 가장 오래된 기록부터 버린다.
     * 키(이메일)는 요청자가 정할 수 있으므로 만료 정리만으로는 메모리가 무한히 늘어날 수 있다.
     */
    private void purgeExpired(Instant now) {
        attempts.entrySet().removeIf(entry -> {
            Attempt a = entry.getValue();
            boolean lockExpired = a.lockedUntil() == null || a.lockedUntil().isBefore(now);
            boolean windowExpired = Duration.between(a.firstFailureAt(), now).compareTo(FAILURE_WINDOW) > 0;
            return lockExpired && windowExpired;
        });
        int overflow = attempts.size() - MAX_TRACKED_KEYS;
        if (overflow <= 0) return;
        log.warn("Login attempt tracker exceeded {} keys; evicting {} oldest entries", MAX_TRACKED_KEYS, overflow);
        attempts.entrySet().stream()
                .sorted(java.util.Comparator.comparing(e -> e.getValue().firstFailureAt()))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(attempts::remove);
    }

    private String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }
}
