package pl.codelobby.lobby;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.codelobby.auth.User;
import pl.codelobby.task.ProgrammingLanguage;
import pl.codelobby.task.Task;

import java.util.List;
import java.util.Set;

@Service
public class LobbyPersistenceService {

    private final LobbyRepository lobbyRepository;
    private final LobbyMemberRepository lobbyMemberRepository;
    private final LobbyTaskSelectionService taskSelectionService;

    public LobbyPersistenceService(
            LobbyRepository lobbyRepository,
            LobbyMemberRepository lobbyMemberRepository,
            LobbyTaskSelectionService taskSelectionService
    ) {
        this.lobbyRepository = lobbyRepository;
        this.lobbyMemberRepository = lobbyMemberRepository;
        this.taskSelectionService = taskSelectionService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Lobby save(String name, String joinCode, User host) {
        return save(name, joinCode, host, List.of(), Set.of());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Lobby save(
            String name,
            String joinCode,
            User host,
            List<Long> taskIds,
            Set<ProgrammingLanguage> allowedLanguages
    ) {
        List<Task> selectedTasks = taskSelectionService.selectTasks(
                host.getId(), taskIds, allowedLanguages
        );

        Lobby lobby = new Lobby(name, joinCode, host, allowedLanguages);

        for (int position = 0; position < selectedTasks.size(); position++) {
            lobby.addTask(selectedTasks.get(position), position);
        }

        Lobby savedLobby = lobbyRepository.saveAndFlush(lobby);

        LobbyMember hostMembership = new LobbyMember(
                savedLobby,
                host,
                LobbyRole.HOST
        );
        lobbyMemberRepository.saveAndFlush(hostMembership);

        return savedLobby;
    }
}