package pl.codelobby.lobby;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pl.codelobby.auth.User;
import pl.codelobby.auth.UserRepository;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@Testcontainers
@SpringBootTest(properties = "DB_PASSWORD=unused")
public class LobbyRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("codelobby_test");

    @Autowired
    private LobbyRepository lobbyRepository;

    @Autowired
    private UserRepository userRepository;

    private User host;

    @BeforeEach
    void setUp() {
        lobbyRepository.deleteAll();
        userRepository.deleteAll();

        host = userRepository.saveAndFlush(
                new User("host@example.com", "test-hash")
        );
    }

    @Test
    void shouldSaveAndFindLobbyWithHost() {
        Lobby saved = lobbyRepository.saveAndFlush(
                new Lobby("Java practice", "ABC123", host)
        );

        Lobby foundById = lobbyRepository.findById(saved.getId())
                .orElseThrow();

        Lobby foundByCode = lobbyRepository.findByJoinCode("ABC123")
                .orElseThrow();

        assertNotNull(saved.getId());
        assertEquals(saved.getId(), foundByCode.getId());
        assertEquals("Java practice", foundById.getName());
        assertEquals("ABC123", foundById.getJoinCode());
        assertEquals(LobbyStatus.WAITING, foundById.getStatus());
        assertEquals(host.getId(), foundById.getHost().getId());
        assertNotNull(foundById.getCreatedAt());
    }

    @Test
    void shouldRejectDuplicateJoinCode() {
        lobbyRepository.saveAndFlush(
                new Lobby("First lobby", "ABC123", host)
        );

        Lobby secondLobby =
                new Lobby("Second lobby", "ABC123", host);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> lobbyRepository.saveAndFlush(secondLobby)
        );
    }
}
