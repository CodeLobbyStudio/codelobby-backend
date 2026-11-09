package pl.codelobby.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record TaskPageRequest(
        @Min(0)
        Integer page,

        @Min(1)
        @Max(100)
        Integer size
) {
}
