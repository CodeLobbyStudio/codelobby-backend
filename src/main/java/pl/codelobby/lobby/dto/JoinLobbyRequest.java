package pl.codelobby.lobby.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinLobbyRequest(@NotBlank @Size(max = 64) String code) {
}
