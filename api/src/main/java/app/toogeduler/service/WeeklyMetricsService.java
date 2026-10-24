package app.toogeduler.service;

import app.toogeduler.repo.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자가 매주 사용량을 확인하는 주간 지표.
 *
 * <p>북극성 지표(주간 확정된 그룹 일정 수)의 근사치로 "지난 7일간 만든, 그룹이 연결된 일정 수"를 센다. 결과는 Railway 로그에서 {@code
 * weekly-metrics}로 찾을 수 있는 한 줄로 남긴다. 개인 식별 정보(이름·이메일·일정 제목)는 넣지 않고 숫자만 남긴다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WeeklyMetricsService {
    static final Duration WINDOW = Duration.ofDays(7);

    private final UserRepository users;
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final EventRepository events;
    private final Clock clock;

    /** 집계 기간은 [from, to)이다. */
    public record WeeklyMetrics(
            Instant from,
            Instant to,
            long newUsers,
            long newGroups,
            long joins,
            long groupEvents,
            long activeGroups) {
        public String toLogLine() {
            return "weekly-metrics from=%s to=%s newUsers=%d newGroups=%d joins=%d groupEvents=%d activeGroups=%d"
                    .formatted(from, to, newUsers, newGroups, joins, groupEvents, activeGroups);
        }
    }

    @Transactional(readOnly = true)
    public WeeklyMetrics compute(Instant now) {
        Instant from = now.minus(WINDOW);
        return new WeeklyMetrics(
                from,
                now,
                users.countCreatedBetween(from, now),
                groups.countCreatedBetween(from, now),
                members.countJoinsBetween(from, now),
                events.countGroupEventsCreatedBetween(from, now),
                events.countGroupsWithEventsCreatedBetween(from, now));
    }

    /** 매주 월요일 09:00(서울)에 지난 7일을 집계해 로그로 남긴다. */
    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Seoul")
    public void logWeeklyMetrics() {
        log.info(compute(clock.instant()).toLogLine());
    }
}
