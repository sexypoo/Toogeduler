package app.toogeduler.repo;

import static org.assertj.core.api.Assertions.assertThat;

import app.toogeduler.domain.Notification;
import app.toogeduler.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * 운영과 같은 방식으로 테이블을 만든다: Flyway 가 db/migration 을 적용하고 Hibernate 는 확인(validate)만 한다. 엔티티를 고치고
 * V{n}__*.sql 을 빠뜨리면 이 테스트가 컨텍스트 시작에서 실패한다.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate"})
class SchemaMigrationTest {
    @Autowired TestEntityManager em;

    @Test
    void migrationsMatchEntities() {
        User user = em.persistAndFlush(new User("schema@example.com", "스키마", null));

        assertThat(user.getId()).isNotNull();
    }

    /** enum 값을 추가하면 CHECK 제약도 마이그레이션으로 고쳐야 한다. validate 는 CHECK 제약을 보지 않아서 저장으로 확인한다. */
    @Test
    void everyNotificationTypeFitsTheCheckConstraint() {
        User user = em.persistAndFlush(new User("notify@example.com", "알림", null));

        for (Notification.Type type : Notification.Type.values()) {
            Notification n = new Notification();
            n.setUser(user);
            n.setType(type);
            n.setTitle(type.name());
            n.setMessage("메시지");
            n.setDedupeKey(type.name());
            em.persistAndFlush(n);
        }
    }
}
