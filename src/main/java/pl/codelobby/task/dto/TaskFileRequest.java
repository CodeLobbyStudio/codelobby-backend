package pl.codelobby.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.codelobby.task.TaskFileType;

public record TaskFileRequest(

        @NotBlank
        @Size(max = 240)
        String path,

        @NotNull
        TaskFileType type,

        @NotNull
        String content

) {
}
