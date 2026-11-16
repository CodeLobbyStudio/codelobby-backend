package pl.codelobby.lobby.dto;

import pl.codelobby.lobby.LobbyRole;
import pl.codelobby.lobby.LobbyStatus;
import pl.codelobby.task.ProgrammingLanguage;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record LobbyDetailsResponse(
        Long id,
        String name,
        String joinCode,
        String joinUrl,
        LobbyStatus status,
        LobbyRole role,
        Long hostId,
        int maxParticipants,
        int durationMinutes,
        Instant startedAt,
        Instant endsAt,
        Set<ProgrammingLanguage> allowedLanguages,
        List<Member> members,
        List<Task> tasks
) {
    public record Member(Long userId, LobbyRole role) {
    }

    public record Task(Long taskId, String title, int position) {
    }
}
