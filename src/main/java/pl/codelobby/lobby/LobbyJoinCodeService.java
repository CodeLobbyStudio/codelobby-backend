
package pl.codelobby.lobby;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import pl.codelobby.auth.User;

@Service
public class LobbyJoinCodeService {

    private static final int MAX_ATTEMPTS = 10;
    private static final String JOIN_CODE_CONSTRAINT =
            "uk_lobbies_join_code";

    private final LobbyJoinCodeGenerator generator;
    private final LobbyPersistenceService persistenceService;

    public LobbyJoinCodeService(
            LobbyJoinCodeGenerator generator,
            LobbyPersistenceService persistenceService
    ) {
        this.generator = generator;
        this.persistenceService = persistenceService;
    }

    public Lobby createLobby(String name, User host) {

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {

            String code = generator.generate();

            try {
                return persistenceService.save(name, code, host);
            } catch (DataIntegrityViolationException exception) {

                if (!isJoinCodeCollision(exception)) {
                    throw exception;
                }
            }
        }

        throw new IllegalStateException(
                "Could not create lobby with a unique join code"
        );
    }

    private boolean isJoinCodeCollision(Throwable exception) {

        Throwable cause = exception;

        while (cause != null) {

            if (cause instanceof ConstraintViolationException violation
                    && JOIN_CODE_CONSTRAINT.equalsIgnoreCase(
                    violation.getConstraintName())) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}
