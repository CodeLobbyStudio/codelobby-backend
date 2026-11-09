package pl.codelobby.task.dto;

import pl.codelobby.task.ProgrammingLanguage;
import pl.codelobby.task.Tag;
import pl.codelobby.task.TaskDifficulty;
import pl.codelobby.task.TaskLanguage;

import java.util.List;
import java.util.Set;

public record TaskDetailsResponse(
        Long id,
        Integer catalogNumber,
        String title,
        String description,
        TaskDifficulty difficulty,
        List<TagResponse> tags,
        List<ProgrammingLanguage> languages
) {
}
