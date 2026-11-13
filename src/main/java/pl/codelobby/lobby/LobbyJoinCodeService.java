
package pl.codelobby.lobby;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import pl.codelobby.auth.User;
import pl.codelobby.task.ProgrammingLanguage;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
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
        return saveWithUniqueJoinCode(
                code -> persistenceService.save(name, code, host)
        );
    }

    public Lobby createLobby(
            String name,
            User host,
            List<Long> taskIds,
            Set<ProgrammingLanguage> allowedLanguages
    ) {
        return saveWithUniqueJoinCode(
                code -> persistenceService.save(
                        name, code, host, taskIds, allowedLanguages
                )
        );
    }

    private Lobby saveWithUniqueJoinCode(Function<String, Lobby> saveLobby) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = generator.generate();

            try {
                return saveLobby.apply(code);
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
