package pl.codelobby.lobby;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import pl.codelobby.auth.User;
import org.hibernate.exception.ConstraintViolationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LobbyJoinCodeServiceTest {

    @Mock
    private LobbyJoinCodeGenerator generator;

    @Mock
    private LobbyPersistenceService persistenceService;

    @InjectMocks
    private LobbyJoinCodeService service;

    @Test
    void shouldRetryAfterDatabaseCodeCollision() {

        User host = mock(User.class);
        Lobby savedLobby = mock(Lobby.class);

        when(generator.generate())
                .thenReturn("ABCD2345", "PQRZ6789");

        ConstraintViolationException violation =
                mock(ConstraintViolationException.class);

        when(violation.getConstraintName())
                .thenReturn("uk_lobbies_join_code");

        DataIntegrityViolationException collision =
                new DataIntegrityViolationException(
                        "Duplicate join code",
                        violation
                );

        when(persistenceService.save(
                "Java practice", "ABCD2345", host
        )).thenThrow(collision);

        when(persistenceService.save(
                "Java practice", "PQRZ6789", host
        )).thenReturn(savedLobby);

        Lobby result = service.createLobby("Java practice", host);

        assertSame(savedLobby, result);

        verify(persistenceService, times(1))
                .save("Java practice", "ABCD2345", host);

        verify(persistenceService, times(1))
                .save("Java practice", "PQRZ6789", host);
    }


    @Test
    void shouldNotRetryUnrelatedDatabaseErrors() {

        User host = mock(User.class);

        when(generator.generate()).thenReturn("ABCD2345");

        ConstraintViolationException violation =
                mock(ConstraintViolationException.class);

        when(violation.getConstraintName())
                .thenReturn("fk_lobbies_host");

        DataIntegrityViolationException databaseError =
                new DataIntegrityViolationException(
                        "Host does not exist",
                        violation
                );

        when(persistenceService.save(
                "Java practice", "ABCD2345", host
        )).thenThrow(databaseError);

        assertSame(
                databaseError,
                assertThrows(
                        DataIntegrityViolationException.class,
                        () -> service.createLobby("Java practice", host)
                )
        );

        verify(generator, times(1)).generate();
        verify(persistenceService, times(1))
                .save("Java practice", "ABCD2345", host);
    }


    @Test
    void shouldFailAfterTenDatabaseCollisions() {

        User host = mock(User.class);

        when(generator.generate()).thenReturn("ABCD2345");

        ConstraintViolationException violation =
                mock(ConstraintViolationException.class);

        when(violation.getConstraintName())
                .thenReturn("uk_lobbies_join_code");

        DataIntegrityViolationException collision =
                new DataIntegrityViolationException(
                        "Duplicate join code",
                        violation
                );

        when(persistenceService.save(
                "Java practice", "ABCD2345", host
        )).thenThrow(collision);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.createLobby("Java practice", host)
        );

        assertEquals(
                "Could not create lobby with a unique join code",
                exception.getMessage()
        );

        verify(persistenceService, times(10))
                .save("Java practice", "ABCD2345", host);
    }



}
