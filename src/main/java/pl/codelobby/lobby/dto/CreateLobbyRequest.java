
package pl.codelobby.lobby.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import pl.codelobby.task.ProgrammingLanguage;

import java.util.List;
import java.util.Set;

public record CreateLobbyRequest(
        @NotBlank @Size(max = 120) String name,
        @NotEmpty List<@NotNull @Positive Long> taskIds,
        @NotEmpty Set<@NotNull ProgrammingLanguage> allowedLanguages
) {
}
