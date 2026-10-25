package pl.codelobby.lobby;

import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pl.codelobby.auth.User;
import pl.codelobby.auth.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "DB_PASSWORD=unused")
@ActiveProfiles("test")
@Testcontainers
@Transactional
class LobbyMemberRepositoryTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private LobbyMemberRepository memberRepository;
    @Autowired
    private LobbyRepository lobbyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistBothRolesAndLoadRelationsLazily() {
        User host = userRepository.saveAndFlush(new User("host@example.com", "hash"));
        User guest = userRepository.saveAndFlush(new User("guest@example.com", "hash"));
        Lobby lobby = lobbyRepository.saveAndFlush(new Lobby("Practice", "ABCD2345", host));
        Instant before = Instant.now().minusSeconds(1);
        LobbyMember hostMember = memberRepository.saveAndFlush(new LobbyMember(lobby, host, LobbyRole.HOST));
        LobbyMember participant = memberRepository.saveAndFlush(new LobbyMember(lobby, guest, LobbyRole.PARTICIPANT));
        Instant joinedAt = participant.getJoinedAt();
        entityManager.clear();

        LobbyMember found = memberRepository.findById(participant.getId()).orElseThrow();
        assertFalse(Hibernate.isInitialized(found.getLobby()));
        assertFalse(Hibernate.isInitialized(found.getUser()));
        assertEquals(lobby.getId(), found.getLobby().getId());
        assertEquals(guest.getId(), found.getUser().getId());
        assertEquals(LobbyRole.PARTICIPANT, found.getRole());
        // PostgreSQL stores microseconds, while the JVM may supply nanoseconds.
        assertTrue(Duration.between(joinedAt, found.getJoinedAt()).abs().toNanos() <= 1000);
        assertFalse(found.getJoinedAt().isBefore(before));
        assertFalse(found.getJoinedAt().isAfter(Instant.now().plusSeconds(1)));
        assertEquals(LobbyRole.HOST, memberRepository.findById(hostMember.getId()).orElseThrow().getRole());
    }

    @Test
    void shouldScopeEveryLookupToTheRequestedLobbyAndUser() {
        User alice = userRepository.saveAndFlush(new User("alice@example.com", "hash"));
        User bob = userRepository.saveAndFlush(new User("bob@example.com", "hash"));
        User outsider = userRepository.saveAndFlush(new User("outsider@example.com", "hash"));
        Lobby first = lobbyRepository.saveAndFlush(new Lobby("First", "ABCD2345", alice));
        Lobby second = lobbyRepository.saveAndFlush(new Lobby("Second", "PQRZ6789", bob));
        LobbyMember firstHost = memberRepository.saveAndFlush(new LobbyMember(first, alice, LobbyRole.HOST));
        LobbyMember secondHost = memberRepository.saveAndFlush(new LobbyMember(second, bob, LobbyRole.HOST));
        LobbyMember secondGuest = memberRepository.saveAndFlush(new LobbyMember(second, alice, LobbyRole.PARTICIPANT));
        entityManager.clear();

        assertEquals(firstHost.getId(), memberRepository.findByLobby_IdAndUser_Id(first.getId(), alice.getId()).orElseThrow().getId());
        assertTrue(memberRepository.findByLobby_IdAndUser_Id(first.getId(), bob.getId()).isEmpty());
        assertTrue(memberRepository.existsByLobby_IdAndUser_Id(second.getId(), alice.getId()));
        assertFalse(memberRepository.existsByLobby_IdAndUser_Id(first.getId(), bob.getId()));
        assertFalse(memberRepository.existsByLobby_IdAndUser_Id(second.getId(), outsider.getId()));
        assertEquals(Set.of(secondHost.getId(), secondGuest.getId()),
                memberRepository.findAllByLobby_Id(second.getId()).stream().map(LobbyMember::getId).collect(Collectors.toSet()));
        assertEquals(Set.of(firstHost.getId(), secondGuest.getId()),
                memberRepository.findAllByUser_Id(alice.getId()).stream().map(LobbyMember::getId).collect(Collectors.toSet()));
        assertTrue(memberRepository.findAllByUser_Id(outsider.getId()).isEmpty());
        assertTrue(memberRepository.findAllByLobby_Id(Long.MAX_VALUE).isEmpty());
    }
}
