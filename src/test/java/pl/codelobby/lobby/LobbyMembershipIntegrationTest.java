package pl.codelobby.lobby;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pl.codelobby.auth.User;
import pl.codelobby.auth.UserRepository;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "DB_PASSWORD=unused")
@ActiveProfiles("test")
@Testcontainers
class LobbyMembershipIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired LobbyPersistenceService persistence;
    @Autowired LobbyMemberRepository members;
    @Autowired LobbyRepository lobbies;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    User host;

    @BeforeEach
    void setUp() {
        members.deleteAllInBatch();
        lobbies.deleteAllInBatch();
        users.deleteAllInBatch();
        host = users.saveAndFlush(new User("host@example.com", "hash"));
    }

    @Test
    void shouldCommitExactlyOneLobbyAndItsHostTogether() {
        Lobby lobby = persistence.save("Practice", "ABCD2345", host);
        assertEquals(1, lobbies.count());
        assertEquals(1, members.count());
        LobbyMember member = members.findByLobby_IdAndUser_Id(lobby.getId(), host.getId()).orElseThrow();
        assertEquals(LobbyRole.HOST, member.getRole());
        assertNotNull(member.getJoinedAt());
        Lobby found = lobbies.findById(lobby.getId()).orElseThrow();
        assertEquals("Practice", found.getName());
        assertEquals("ABCD2345", found.getJoinCode());
        assertEquals(host.getId(), found.getHost().getId());
        assertEquals(LobbyStatus.WAITING, found.getStatus());
        assertNotNull(found.getCreatedAt());
    }

    @Test
    void shouldRollBackTheLobbyWhenTheHostInsertFailsAndRecoverAfterwards() {
        // A test-only constraint forces a real DB failure on the second insert.
        jdbc.execute("ALTER TABLE lobby_members ADD CONSTRAINT test_reject_host CHECK (role <> 'HOST')");
        try {
            assertThrows(DataIntegrityViolationException.class,
                    () -> persistence.save("Rejected", "ABCD2345", host));
            assertEquals(0, lobbies.count());
            assertEquals(0, members.count());
            assertEquals(1, users.count());
        } finally {
            jdbc.execute("ALTER TABLE lobby_members DROP CONSTRAINT test_reject_host");
        }
        persistence.save("Recovered", "ABCD2345", host);
        assertEquals(1, lobbies.count());
        assertEquals(1, members.count());
    }
}
