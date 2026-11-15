package pl.codelobby.lobby.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.codelobby.task.ProgrammingLanguage;

import java.util.List;

public record LobbyRunRequest(
        @NotBlank @Size(max = 128) String taskVersionId,
        @NotNull ProgrammingLanguage language,
        @NotNull @Size(max = 50) List<@NotNull @Valid File> files
) {
    public record File(
            @NotBlank @Size(max = 240) String path,
            @NotNull @Size(max = 131072) String content
    ) {
    }
}
