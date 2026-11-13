package pl.codelobby.lobby.dto;


import pl.codelobby.lobby.LobbyStatus;

public record CreateLobbyResponse(
        Long id,
        String name,
        String joinCode,
        String joinUrl,
        LobbyStatus status
) {
}
