package pl.codelobby.lobby;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.codelobby.auth.User;
import pl.codelobby.auth.UserRepository;
import pl.codelobby.common.error.ApiException;
import pl.codelobby.lobby.dto.CreateLobbyRequest;
import pl.codelobby.lobby.dto.CreateLobbyResponse;
import pl.codelobby.lobby.dto.JoinLobbyRequest;
import pl.codelobby.lobby.dto.LobbyDetailsResponse;
import pl.codelobby.lobby.dto.LobbyRunRequest;
import pl.codelobby.lobby.dto.LobbyRunResponse;
import pl.codelobby.lobby.dto.LobbyWorkspaceResponse;
import pl.codelobby.task.ProgrammingLanguage;

@RestController
@RequestMapping("/api/lobbies")
public class LobbyController {

    private final LobbyJoinCodeService lobbyJoinCodeService;
    private final UserRepository userRepository;
    private final LobbyFlowService lobbyFlowService;
    private final RunnerClient runnerClient;

    public LobbyController(
            LobbyJoinCodeService lobbyJoinCodeService,
            UserRepository userRepository,
            LobbyFlowService lobbyFlowService,
            RunnerClient runnerClient
    ) {
        this.lobbyJoinCodeService = lobbyJoinCodeService;
        this.userRepository = userRepository;
        this.lobbyFlowService = lobbyFlowService;
        this.runnerClient = runnerClient;
    }

    @PostMapping
    public ResponseEntity<CreateLobbyResponse> createLobby(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateLobbyRequest request
    ) {
        User host = getAuthenticatedUser(jwt);

        Lobby lobby = lobbyJoinCodeService.createLobby(
                request.name().strip(),
                host,
                request.taskIds(),
                request.allowedLanguages(),
                request.maxParticipants(),
                request.durationMinutes()
        );

        CreateLobbyResponse response = new CreateLobbyResponse(
                lobby.getId(),
                lobby.getName(),
                lobby.getJoinCode(),
                lobby.getJoinUrl(),
                lobby.getStatus(),
                lobby.getMaxParticipants(),
                lobby.getDurationMinutes()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/join")
    public LobbyDetailsResponse joinLobby(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody JoinLobbyRequest request
    ) {
        return lobbyFlowService.join(getAuthenticatedUser(jwt), request.code());
    }

    @GetMapping("/{lobbyId}")
    public LobbyDetailsResponse getLobby(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long lobbyId
    ) {
        return lobbyFlowService.details(lobbyId, getAuthenticatedUser(jwt).getId());
    }

    @PostMapping("/{lobbyId}/start")
    public LobbyDetailsResponse startLobby(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long lobbyId
    ) {
        return lobbyFlowService.start(lobbyId, getAuthenticatedUser(jwt).getId());
    }

    @PostMapping("/{lobbyId}/finish")
    public LobbyDetailsResponse finishLobby(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long lobbyId
    ) {
        return lobbyFlowService.finish(lobbyId, getAuthenticatedUser(jwt).getId());
    }

    @GetMapping("/{lobbyId}/tasks/{taskId}/workspace")
    public LobbyWorkspaceResponse getWorkspace(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long lobbyId,
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "JAVA") ProgrammingLanguage language
    ) {
        return lobbyFlowService.workspace(lobbyId, taskId, getAuthenticatedUser(jwt).getId(), language);
    }

    @PostMapping("/{lobbyId}/tasks/{taskId}/run")
    public LobbyRunResponse run(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long lobbyId,
            @PathVariable Long taskId,
            @Valid @RequestBody LobbyRunRequest request
    ) {
        var run = lobbyFlowService.prepareRun(lobbyId, taskId, getAuthenticatedUser(jwt).getId(), request);
        return runnerClient.run(run);
    }

    private User getAuthenticatedUser(Jwt jwt) {
        return userRepository.findById(getUserId(jwt)).orElseThrow(this::invalidToken);
    }

    private Long getUserId(Jwt jwt) {
        if (jwt == null) {
            throw invalidToken();
        }

        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw invalidToken();
        }
    }

    private ApiException invalidToken() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_TOKEN",
                "Invalid access token"
        );
    }
}