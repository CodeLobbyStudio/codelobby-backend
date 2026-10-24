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

    @Autowired LobbyMemberRepository members;
    @Autowired LobbyRepository lobbies;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;

    @Test
    void shouldPersistBothRolesAndLoadRelationsLazily() {
        User host = users.saveAndFlush(new User("host@example.com", "hash"));
        User guest = users.saveAndFlush(new User("guest@example.com", "hash"));
        Lobby lobby = lobbies.saveAndFlush(new Lobby("Practice", "ABCD2345", host));
        Instant before = Instant.now().minusSeconds(1);
        LobbyMember hostMember = members.saveAndFlush(new LobbyMember(lobby, host, LobbyRole.HOST));
        LobbyMember participant = members.saveAndFlush(new LobbyMember(lobby, guest, LobbyRole.PARTICIPANT));
        Instant joinedAt = participant.getJoinedAt();
        entityManager.clear();

        LobbyMember found = members.findById(participant.getId()).orElseThrow();
        assertFalse(Hibernate.isInitialized(found.getLobby()));
        assertFalse(Hibernate.isInitialized(found.getUser()));
        assertEquals(lobby.getId(), found.getLobby().getId());
        assertEquals(guest.getId(), found.getUser().getId());
        assertEquals(LobbyRole.PARTICIPANT, found.getRole());
        // PostgreSQL stores microseconds, while the JVM may supply nanoseconds.
        assertTrue(Duration.between(joinedAt, found.getJoinedAt()).abs().toNanos() <= 1000);
        assertFalse(found.getJoinedAt().isBefore(before));
        assertFalse(found.getJoinedAt().isAfter(Instant.now().plusSeconds(1)));
        assertEquals(LobbyRole.HOST, members.findById(hostMember.getId()).orElseThrow().getRole());
    }

    @Test
    void shouldScopeEveryLookupToTheRequestedLobbyAndUser() {
        User alice = users.saveAndFlush(new User("alice@example.com", "hash"));
        User bob = users.saveAndFlush(new User("bob@example.com", "hash"));
        User outsider = users.saveAndFlush(new User("outsider@example.com", "hash"));
        Lobby first = lobbies.saveAndFlush(new Lobby("First", "ABCD2345", alice));
        Lobby second = lobbies.saveAndFlush(new Lobby("Second", "PQRZ6789", bob));
        LobbyMember firstHost = members.saveAndFlush(new LobbyMember(first, alice, LobbyRole.HOST));
        LobbyMember secondHost = members.saveAndFlush(new LobbyMember(second, bob, LobbyRole.HOST));
        LobbyMember secondGuest = members.saveAndFlush(new LobbyMember(second, alice, LobbyRole.PARTICIPANT));
        entityManager.clear();

        assertEquals(firstHost.getId(), members.findByLobby_IdAndUser_Id(first.getId(), alice.getId()).orElseThrow().getId());
        assertTrue(members.findByLobby_IdAndUser_Id(first.getId(), bob.getId()).isEmpty());
        assertTrue(members.existsByLobby_IdAndUser_Id(second.getId(), alice.getId()));
        assertFalse(members.existsByLobby_IdAndUser_Id(first.getId(), bob.getId()));
        assertFalse(members.existsByLobby_IdAndUser_Id(second.getId(), outsider.getId()));
        assertEquals(Set.of(secondHost.getId(), secondGuest.getId()),
                members.findAllByLobby_Id(second.getId()).stream().map(LobbyMember::getId).collect(Collectors.toSet()));
        assertEquals(Set.of(firstHost.getId(), secondGuest.getId()),
                members.findAllByUser_Id(alice.getId()).stream().map(LobbyMember::getId).collect(Collectors.toSet()));
        assertTrue(members.findAllByUser_Id(outsider.getId()).isEmpty());
        assertTrue(members.findAllByLobby_Id(Long.MAX_VALUE).isEmpty());
    }
}
