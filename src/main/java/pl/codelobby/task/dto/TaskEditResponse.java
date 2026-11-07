package pl.codelobby.task.dto;

import pl.codelobby.task.TaskDifficulty;

import java.util.List;

public record TaskEditResponse(
        Long id,
        String title,
        String description,
        TaskDifficulty difficulty,
        List<TaskLanguageResponse> languages
) {
}
