package app.toogeduler.repo;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class EventRepositoryQueryTest {
    @Autowired EventRepository events;

    @Test
    void visibilityQueriesAreValidJpaQueries() {
        assertNotNull(events);
    }
}
