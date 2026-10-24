package pl.codelobby.lobby;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class LobbyMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    @BeforeEach
    void cleanDatabase() {
        flyway(null).clean();
    }

    // Each statement uses its own auto-commit connection: constraint failures
    // cannot poison a transaction used by subsequent assertions.
    void execute(String sql) throws SQLException {
        try (Connection connection = postgres.createConnection(""); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    List<String> strings(String sql) throws SQLException {
        try (Connection connection = postgres.createConnection(""); Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (results.next()) values.add(results.getString(1));
            return values;
        }
    }

    void seed() throws SQLException {
        execute("INSERT INTO app_users(id,email,password_hash) VALUES (1,'one@example.com','hash'),(2,'two@example.com','hash'),(3,'three@example.com','hash')");
        execute("INSERT INTO lobbies(id,name,join_code,status,host_id) VALUES (1,'First','ABCD2345','WAITING',1),(2,'Second','PQRZ6789','WAITING',2)");
    }

    @Test
    void shouldMigrateFreshDatabaseAndInstallConstraintsAndIndexes() throws SQLException {
        assertEquals(4, flyway(null).migrate().migrationsExecuted);
        assertEquals(List.of("1", "2", "3", "4"), strings("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank"));
        assertTrue(strings("SELECT conname FROM pg_constraint WHERE conrelid='lobby_members'::regclass")
                .containsAll(List.of("fk_lobby_members_lobby", "fk_lobby_members_user", "uk_lobby_members_lobby_user", "ck_lobby_members_role")));
        assertTrue(strings("SELECT indexname FROM pg_indexes WHERE tablename='lobby_members'")
                .containsAll(List.of("uk_lobby_members_single_host", "idx_lobby_members_user_id", "uk_lobby_members_lobby_user")));
        assertEquals(0, flyway(null).migrate().migrationsExecuted);
    }

    @Test
    void shouldBackfillExactlyOneMatchingHostPerExistingLobbyWithoutDataLoss() throws SQLException {
        flyway("3").migrate();
        seed();
        assertEquals(1, flyway(null).migrate().migrationsExecuted);
        assertEquals(List.of("1:1:HOST", "2:2:HOST"), strings("SELECT lobby_id || ':' || user_id || ':' || role FROM lobby_members ORDER BY lobby_id"));
        assertEquals(List.of("3"), strings("SELECT count(*) FROM app_users"));
        assertEquals(List.of("First", "Second"), strings("SELECT name FROM lobbies ORDER BY id"));
        assertEquals(List.of("2"), strings("SELECT count(*) FROM lobby_members WHERE joined_at IS NOT NULL AND joined_at BETWEEN now()-interval '1 minute' AND now()+interval '1 minute'"));
        assertEquals(0, flyway(null).migrate().migrationsExecuted);
        assertEquals(List.of("2"), strings("SELECT count(*) FROM lobby_members"));
    }

    @ParameterizedTest(name = "lobby={0}, user={1}, role={2} -> {3}")
    @CsvSource(value = {
            "1|1|'PARTICIPANT'|23505", "1|2|'HOST'|23505", "1|3|'ADMIN'|23514",
            "1|3|NULL|23502", "NULL|3|'PARTICIPANT'|23502", "1|NULL|'PARTICIPANT'|23502",
            "999|3|'PARTICIPANT'|23503", "1|999|'PARTICIPANT'|23503"
    }, delimiter = '|')
    void shouldRejectInvalidMemberships(String lobby, String user, String role, String sqlState) throws SQLException {
        flyway("3").migrate();
        seed();
        flyway(null).migrate();
        SQLException error = assertThrows(SQLException.class, () -> execute(
                "INSERT INTO lobby_members(lobby_id,user_id,role) VALUES (" + lobby + "," + user + "," + role + ")"));
        assertEquals(sqlState, error.getSQLState());
        assertEquals(List.of("2"), strings("SELECT count(*) FROM lobby_members"));
    }

    @Test
    void shouldRejectDuplicateParticipantsAndNullJoinedAt() throws SQLException {
        flyway("3").migrate();
        seed();
        flyway(null).migrate();
        execute("INSERT INTO lobby_members(lobby_id,user_id,role) VALUES (1,3,'PARTICIPANT')");
        SQLException duplicate = assertThrows(SQLException.class, () -> execute(
                "INSERT INTO lobby_members(lobby_id,user_id,role) VALUES (1,3,'PARTICIPANT')"));
        assertEquals("23505", duplicate.getSQLState());
        SQLException missingTimestamp = assertThrows(SQLException.class, () -> execute(
                "INSERT INTO lobby_members(lobby_id,user_id,role,joined_at) VALUES (2,3,'PARTICIPANT',NULL)"));
        assertEquals("23502", missingTimestamp.getSQLState());
        assertEquals(List.of("3"), strings("SELECT count(*) FROM lobby_members"));
    }

    @Test
    void shouldCascadeOnlyMembershipsOfTheDeletedLobby() throws SQLException {
        flyway("3").migrate();
        seed();
        flyway(null).migrate();
        execute("INSERT INTO lobby_members(lobby_id,user_id,role) VALUES (1,3,'PARTICIPANT'),(2,1,'PARTICIPANT')");
        execute("DELETE FROM lobbies WHERE id=1");
        assertEquals(List.of("2:1:PARTICIPANT", "2:2:HOST"), strings("SELECT lobby_id || ':' || user_id || ':' || role FROM lobby_members ORDER BY user_id"));
        assertEquals(List.of("3"), strings("SELECT count(*) FROM app_users"));
        assertEquals(List.of("Second"), strings("SELECT name FROM lobbies"));
    }
}
