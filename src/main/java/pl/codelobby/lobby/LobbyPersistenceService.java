package pl.codelobby.lobby;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.codelobby.auth.User;

@Service
public class LobbyPersistenceService {

    private final LobbyRepository lobbyRepository;
    private final LobbyMemberRepository lobbyMemberRepository;

    public LobbyPersistenceService(LobbyRepository lobbyRepository,
                                   LobbyMemberRepository lobbyMemberRepository) {
        this.lobbyRepository = lobbyRepository;
        this.lobbyMemberRepository = lobbyMemberRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Lobby save(
            String name,
            String joinCode,
            User host
    ) {
        Lobby lobby = lobbyRepository.saveAndFlush(
                new Lobby(name, joinCode, host)
        );

        LobbyMember hostMembership = new LobbyMember(
                lobby,
                host,
                LobbyRole.HOST
        );

        lobbyMemberRepository.saveAndFlush(hostMembership);

        return lobby;
    }
}
