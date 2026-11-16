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

    @Autowired
    private LobbyPersistenceService lobbyPersistenceService;
    @Autowired
    private LobbyFlowService lobbyFlowService;
    @Autowired
    private LobbyMemberRepository memberRepository;
    @Autowired
    private LobbyRepository lobbyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private User host;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAllInBatch();
        lobbyRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        host = userRepository.saveAndFlush(new User("host@example.com", "hash"));
    }

    @Test
    void shouldCommitExactlyOneLobbyAndItsHostTogether() {
        Lobby lobby = lobbyPersistenceService.save("Practice", "ABCD2345", host);
        assertEquals(1, lobbyRepository.count());
        assertEquals(1, memberRepository.count());
        LobbyMember member = memberRepository.findByLobby_IdAndUser_Id(lobby.getId(), host.getId()).orElseThrow();
        assertEquals(LobbyRole.HOST, member.getRole());
        assertNotNull(member.getJoinedAt());
        Lobby found = lobbyRepository.findById(lobby.getId()).orElseThrow();
        assertEquals("Practice", found.getName());
        assertEquals("ABCD2345", found.getJoinCode());
        assertEquals(host.getId(), found.getHost().getId());
        assertEquals(LobbyStatus.WAITING, found.getStatus());
        assertNotNull(found.getCreatedAt());
    }

    @Test
    void shouldListOnlyLobbiesTheUserHostsOrHasJoined() {
        User otherHost = userRepository.saveAndFlush(new User("other@example.com", "hash"));
        User outsider = userRepository.saveAndFlush(new User("outsider@example.com", "hash"));
        Lobby hosted = lobbyPersistenceService.save("Hosted", "ABCD2345", host);
        Lobby joined = lobbyPersistenceService.save("Joined", "ABCD2346", otherHost);
        lobbyFlowService.join(host, joined.getJoinCode());

        var lobbies = lobbyFlowService.listForMember(host.getId());
        assertEquals(2, lobbies.size());
        assertEquals(joined.getId(), lobbies.get(0).id());
        assertEquals(LobbyRole.PARTICIPANT, lobbies.get(0).role());
        assertEquals(hosted.getId(), lobbies.get(1).id());
        assertEquals(LobbyRole.HOST, lobbies.get(1).role());
        assertTrue(lobbyFlowService.listForMember(outsider.getId()).isEmpty());
    }

    @Test
    void shouldRollBackTheLobbyWhenTheHostInsertFailsAndRecoverAfterwards() {
        // A test-only constraint forces a real DB failure on the second insert.
        jdbcTemplate.execute("ALTER TABLE lobby_members ADD CONSTRAINT test_reject_host CHECK (role <> 'HOST')");
        try {
            assertThrows(DataIntegrityViolationException.class,
                    () -> lobbyPersistenceService.save("Rejected", "ABCD2345", host));
            assertEquals(0, lobbyRepository.count());
            assertEquals(0, memberRepository.count());
            assertEquals(1, userRepository.count());
        } finally {
            jdbcTemplate.execute("ALTER TABLE lobby_members DROP CONSTRAINT test_reject_host");
        }
        lobbyPersistenceService.save("Recovered", "ABCD2345", host);
        assertEquals(1, lobbyRepository.count());
        assertEquals(1, memberRepository.count());
    }
}
