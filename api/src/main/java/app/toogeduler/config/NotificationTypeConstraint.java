package app.toogeduler.config;

import app.toogeduler.domain.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Hibernate 는 enum 컬럼을 만들 때 허용 값을 CHECK 제약으로 박아 두지만, ddl-auto: update 는 이 제약을 갱신하지 않는다.
 * 그래서 Notification.Type 에 값을 추가하면 기존 DB 가 새 알림 저장을 거부한다.
 * 시작할 때 제약을 현재 enum 값으로 다시 건다(여러 번 실행해도 결과가 같다).
 * 스키마를 Flyway 같은 마이그레이션 도구로 옮기면 이 클래스는 지운다.
 */
@Component @RequiredArgsConstructor @Slf4j
public class NotificationTypeConstraint implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override public void run(ApplicationArguments args){
        String allowed=Arrays.stream(Notification.Type.values()).map(t->"'"+t.name()+"'").collect(Collectors.joining(","));
        try{
            jdbc.execute("alter table app_notifications drop constraint if exists app_notifications_type_check");
            jdbc.execute("alter table app_notifications add constraint app_notifications_type_check check (type in ("+allowed+"))");
        }catch(RuntimeException e){
            log.warn("Could not refresh app_notifications type constraint: {}",e.getMessage());
        }
    }
}
