package pl.codelobby.lobby;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.codelobby.auth.User;

@Service
public class LobbyPersistenceService {

    private final LobbyRepository lobbyRepository;

    public LobbyPersistenceService(LobbyRepository lobbyRepository) {
        this.lobbyRepository = lobbyRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Lobby save(
            String name,
            String joinCode,
            User host
    ) {
        Lobby lobby = new Lobby(name, joinCode, host);

        return lobbyRepository.saveAndFlush(lobby);
    }
}
