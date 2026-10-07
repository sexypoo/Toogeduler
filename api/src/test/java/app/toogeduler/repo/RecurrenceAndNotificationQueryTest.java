package app.toogeduler.repo;

import app.toogeduler.domain.Event;
import app.toogeduler.domain.Notification;
import app.toogeduler.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop"})
class RecurrenceAndNotificationQueryTest {
    @Autowired TestEntityManager em;
    @Autowired EventRepository events;
    @Autowired NotificationRepository notifications;

    private static final OffsetDateTime FROM=OffsetDateTime.of(2026,8,2,0,0,0,0,ZoneOffset.UTC);

    @Test
    void blankRecurrenceRuleIsTreatedAsSingleEvent(){
        User owner=em.persist(new User("owner@example.com","오너","hash"));
        Event blank=event(owner,"",FROM.minusMonths(2));
        Event weekly=event(owner,"FREQ=WEEKLY",FROM.minusMonths(2));
        em.flush();

        List<Event> found=events.calendar(owner.getId(),List.of(-1L),FROM,FROM.plusWeeks(1));

        // 빈 문자열 규칙은 반복이 아니므로 기간 밖이면 조회되지 않아야 한다.
        assertThat(found).extracting(Event::getId).containsExactly(weekly.getId()).doesNotContain(blank.getId());
    }

    @Test
    void markAllReadCoversMoreThanTheLatestFifty(){
        User user=em.persist(new User("reader@example.com","리더","hash"));
        for(int i=0;i<60;i++){
            Notification n=new Notification();n.setUser(user);n.setType(Notification.Type.FRIEND_REQUEST);
            n.setTitle("알림 "+i);n.setMessage("메시지");n.setDedupeKey("key-"+i);em.persist(n);
        }
        em.flush();

        int updated=notifications.markAllRead(user.getId(),Instant.now());
        em.clear();

        assertThat(updated).isEqualTo(60);
        assertThat(notifications.countByUserIdAndReadAtIsNull(user.getId())).isZero();
    }

    private Event event(User owner,String rule,OffsetDateTime start){
        Event e=new Event();e.setOwner(owner);e.setTitle(rule.isEmpty()?"단일":"반복");
        e.setStartAt(start);e.setEndAt(start.plusHours(1));e.setRecurrenceRule(rule);
        return em.persist(e);
    }
}
