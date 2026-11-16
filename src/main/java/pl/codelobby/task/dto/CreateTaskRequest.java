package pl.codelobby.task.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import pl.codelobby.task.TaskDifficulty;

import java.util.List;

public record CreateTaskRequest(

        @NotBlank
        @NotNull
        String title,

        @NotBlank
        String description,

        TaskDifficulty difficulty,

        @NotEmpty
        @Valid
        List<@NotNull TaskLanguageRequest> languages
) {
}
