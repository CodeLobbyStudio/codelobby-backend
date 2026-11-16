package pl.codelobby.task.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.codelobby.task.ProgrammingLanguage;

import java.util.List;

public record TaskLanguageRequest (

        @NotNull
        ProgrammingLanguage language,

        @NotEmpty
        @Size(max = 50)
        @Valid
        List<@NotNull TaskFileRequest> files
) {
}
