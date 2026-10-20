package pl.codelobby.lobby;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pl.codelobby.auth.User;
import pl.codelobby.auth.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@Testcontainers
@SpringBootTest(properties = "DB_PASSWORD=unused")
public class LobbyJoinCodeIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("codelobby_test");

    @Autowired
    private LobbyJoinCodeService joinCodeService;

    @Autowired
    private LobbyPersistenceService persistenceService;

    @Autowired
    private LobbyRepository lobbyRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private LobbyJoinCodeGenerator generator;

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
    void shouldCreateLobbyWithGeneratedCode() {

        when(generator.generate()).thenReturn("ABCD2345");

        Lobby lobby = joinCodeService.createLobby(
                "Java practice", host
        );

        assertNotNull(lobby.getId());
        assertEquals("ABCD2345", lobby.getJoinCode());

        assertTrue(
                lobbyRepository.findByJoinCode("ABCD2345")
                        .isPresent()
        );
    }

    @Test
    void shouldGenerateAnotherCodeWhenFirstAlreadyExists() {

        persistenceService.save(
                "Existing lobby", "ABCD2345", host
        );

        when(generator.generate())
                .thenReturn("ABCD2345", "PQRZ6789");

        Lobby lobby = joinCodeService.createLobby(
                "New lobby", host
        );

        assertEquals("PQRZ6789", lobby.getJoinCode());

        assertEquals(2, lobbyRepository.count());

        verify(generator, times(2)).generate();
    }

    @Test
    void shouldAllowAnotherTransactionAfterDuplicateInsert() {

        persistenceService.save(
                "First lobby", "ABCD2345", host
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> persistenceService.save(
                        "Duplicate lobby", "ABCD2345", host
                )
        );

        Lobby saved = persistenceService.save(
                "New lobby", "PQRZ6789", host
        );

        assertNotNull(saved.getId());
        assertEquals("PQRZ6789", saved.getJoinCode());
        assertEquals(2, lobbyRepository.count());
    }


}
