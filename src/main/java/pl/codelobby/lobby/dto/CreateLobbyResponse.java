package pl.codelobby.lobby.dto;


import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import pl.codelobby.lobby.LobbyStatus;

public record CreateLobbyResponse(
        Long id,
        String name,
        String joinCode,
        String joinUrl,
        LobbyStatus status,
        Integer maxParticipants,
        Integer durationMinutes
) {
}
