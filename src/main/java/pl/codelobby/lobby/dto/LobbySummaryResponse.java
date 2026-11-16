package pl.codelobby.lobby.dto;

import pl.codelobby.lobby.LobbyRole;
import pl.codelobby.lobby.LobbyStatus;

public record LobbySummaryResponse(Long id, String name, LobbyStatus status, LobbyRole role) {
}
