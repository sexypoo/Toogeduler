package app.toogeduler.repo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * V2__invite_expiry 가 그룹이 이미 있는 DB(운영과 같은 상황)에서 성공하고, 기존 그룹의 초대 링크 만료를 지금부터 7일 뒤로 채우는지 확인한다. V1 까지만
 * 적용한 DB 에 그룹을 넣은 뒤 나머지를 적용한다. PostgreSQL 에서는 InviteExpiryPostgresMigrationTest 가 같은 확인을 한다.
 */
public class InviteExpiryMigrationTest {
    @Test
    void existingGroupsGetAnExpirySevenDaysFromNow() throws SQLException {
        assertBackfillsExistingGroups(
                "jdbc:h2:mem:invite-expiry-v2;DB_CLOSE_DELAY=-1", "sa", "", null);
    }

    public static void assertBackfillsExistingGroups(
            String url, String user, String password, String schema) throws SQLException {
        Flyway.configure()
                .dataSource(url, user, password)
                .schemas(schema == null ? new String[0] : new String[] {schema})
                .target("1")
                .load()
                .migrate();
        try (Connection c = DriverManager.getConnection(url, user, password);
                Statement s = c.createStatement()) {
            if (schema != null) c.setSchema(schema);
            s.executeUpdate(
                    "insert into users (created_at, email, name, provider)"
                            + " values (current_timestamp, 'old@migration.test', '기존 그룹장', 'LOCAL')");
            s.executeUpdate(
                    "insert into calendar_groups (color, created_at, invite_token, name, owner_id)"
                            + " select '#19B7B1', current_timestamp, 'old-token', '기존 그룹', id"
                            + " from users where email = 'old@migration.test'");
        }
        Instant before = Instant.now();

        Flyway.configure()
                .dataSource(url, user, password)
                .schemas(schema == null ? new String[0] : new String[] {schema})
                .load()
                .migrate();

        Instant after = Instant.now();
        try (Connection c = DriverManager.getConnection(url, user, password);
                Statement s = c.createStatement()) {
            if (schema != null) c.setSchema(schema);
            ResultSet rs =
                    s.executeQuery(
                            "select invite_expires_at from calendar_groups where invite_token = 'old-token'");
            assertThat(rs.next()).isTrue();
            Instant expires = rs.getObject(1, OffsetDateTime.class).toInstant();
            // DB 시계와 JVM 시계가 조금 달라도 되도록 1분 여유를 둔다.
            assertThat(expires)
                    .isBetween(
                            before.plus(Duration.ofDays(7)).minus(Duration.ofMinutes(1)),
                            after.plus(Duration.ofDays(7)).plus(Duration.ofMinutes(1)));
        }
    }
}
