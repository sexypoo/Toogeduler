package app.toogeduler.postgres;

import app.toogeduler.repo.InviteExpiryMigrationTest;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/**
 * InviteExpiryMigrationTest 를 실제 PostgreSQL 에서 한다. 앱이 쓰는 public 스키마와 섞이지 않게 별도 스키마에서 V1 부터 적용한다.
 */
class InviteExpiryPostgresMigrationTest extends PostgresTestBase {
    @Test
    void existingGroupsGetAnExpirySevenDaysFromNow() throws SQLException {
        InviteExpiryMigrationTest.assertBackfillsExistingGroups(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword(),
                "invite_expiry_v2");
    }
}
