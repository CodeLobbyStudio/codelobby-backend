
package pl.codelobby.lobby.dto;

import jakarta.validation.constraints.*;
import pl.codelobby.task.ProgrammingLanguage;

import java.util.List;
import java.util.Set;

public record CreateLobbyRequest(
        @NotBlank @Size(max = 120) String name,
        @NotEmpty List<@NotNull @Positive Long> taskIds,
        @NotEmpty Set<@NotNull ProgrammingLanguage> allowedLanguages,
        @NotNull @Min(2) @Max(20) Integer maxParticipants,
        @NotNull @Min(5) @Max(120) Integer durationMinutes
) {
}
