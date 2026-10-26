package app.toogeduler.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import app.toogeduler.domain.Notification;
import app.toogeduler.domain.User;
import app.toogeduler.repo.NotificationRepository;
import app.toogeduler.repo.UserRepository;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * SchemaMigrationTest 와 같은 확인을 실제 PostgreSQL 에서 한다. 컨텍스트가 뜬 것 자체가 "모든 V 파일이 PostgreSQL 에서 실행되고 엔티티와
 * 맞는다(validate)"는 뜻이다.
 */
class PostgresMigrationTest extends PostgresTestBase {
    @Autowired Flyway flyway;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;

    @Test
    void everyMigrationIsAppliedToAnEmptyDatabase() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).isNotEmpty();
        // 빈 DB 라서 baseline 표시(BASELINE)가 아니라 V1 부터 실제로 실행(SUCCESS)돼야 한다.
        assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(applied)
                .extracting(MigrationInfo::getState)
                .containsOnly(MigrationState.SUCCESS);
        assertThat(flyway.info().pending()).isEmpty();
    }

    /** enum 값을 추가하면 CHECK 제약도 마이그레이션으로 고쳐야 한다. validate 는 CHECK 제약을 보지 않아서 저장으로 확인한다. */
    @Test
    void everyNotificationTypeFitsTheCheckConstraint() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(new User("notify-" + run + "@postgres.test", "알림", null));

        for (Notification.Type type : Notification.Type.values()) {
            Notification n = new Notification();
            n.setUser(user);
            n.setType(type);
            n.setTitle(type.name());
            n.setMessage("메시지");
            n.setDedupeKey(type.name());
            notifications.saveAndFlush(n);
        }

        assertThat(notifications.findTop50ByUserIdOrderByCreatedAtDesc(user.getId()))
                .hasSize(Notification.Type.values().length);
    }
}
