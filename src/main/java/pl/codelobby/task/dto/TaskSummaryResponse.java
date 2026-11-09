package pl.codelobby.task.dto;

import pl.codelobby.task.ProgrammingLanguage;
import pl.codelobby.task.TaskDifficulty;

import java.util.List;

public record TaskSummaryResponse (
        Long id,
        Integer catalogNumber,
        String title,
        TaskDifficulty difficulty,
        List<TagResponse> tags,
        List<ProgrammingLanguage> languages
){
}
