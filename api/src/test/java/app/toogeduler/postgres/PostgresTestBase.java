package app.toogeduler.postgres;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 운영과 같은 PostgreSQL 18 컨테이너 위에서 도는 테스트의 바탕. 빈 DB 에 Flyway 가 V1..Vn 을 적용하고 Hibernate 는 확인(validate)만
 * 한다(application.yml 그대로). H2 에서는 통과하지만 PostgreSQL 에서 깨지는 마이그레이션을 배포 전에 잡는다.
 *
 * <p>Docker 가 없는 로컬에서는 이 클래스를 상속한 테스트만 건너뛴다. 컨테이너는 테스트 클래스들이 Spring 컨텍스트와 함께 나눠 쓰도록 한 번만 띄우고, 끝나면
 * Testcontainers(Ryuk)가 치운다.
 */
@SpringBootTest(properties = {"app.seed-demo=false"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
abstract class PostgresTestBase {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        // Docker 확인을 통과한 뒤에만 불리므로 여기서 띄운다. 이미 떠 있으면 아무것도 하지 않는다.
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
