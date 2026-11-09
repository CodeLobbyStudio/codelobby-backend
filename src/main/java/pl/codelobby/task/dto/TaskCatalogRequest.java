package pl.codelobby.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import pl.codelobby.task.TaskDifficulty;

public record TaskCatalogRequest(
        @Size(max = 200)
        String query,

        TaskDifficulty difficulty,

        @Size(max = 100)
        String tag,

        @Min(0)
        Integer page,

        @Min(1)
        @Max(100)
        Integer size
) {
}
