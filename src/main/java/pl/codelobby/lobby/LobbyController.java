package pl.codelobby.lobby;

package pl.codelobby.lobby;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.codelobby.auth.User;
import pl.codelobby.auth.UserRepository;
import pl.codelobby.common.error.ApiException;
import pl.codelobby.lobby.dto.CreateLobbyRequest;
import pl.codelobby.lobby.dto.CreateLobbyResponse;

@RestController
@RequestMapping("/api/lobbies")
public class LobbyController {

    private final LobbyJoinCodeService lobbyJoinCodeService;
    private final UserRepository userRepository;

    public LobbyController(
            LobbyJoinCodeService lobbyJoinCodeService,
            UserRepository userRepository
    ) {
        this.lobbyJoinCodeService = lobbyJoinCodeService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<CreateLobbyResponse> createLobby(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateLobbyRequest request
    ) {
        User host = userRepository.findById(getUserId(jwt))
                .orElseThrow(this::invalidToken);

        Lobby lobby = lobbyJoinCodeService.createLobby(
                request.name().strip(),
                host,
                request.taskIds(),
                request.allowedLanguages()
        );

        CreateLobbyResponse response = new CreateLobbyResponse(
                lobby.getId(),
                lobby.getName(),
                lobby.getJoinCode(),
                "/lobbies/join?code=" + lobby.getJoinCode(),
                lobby.getStatus()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
