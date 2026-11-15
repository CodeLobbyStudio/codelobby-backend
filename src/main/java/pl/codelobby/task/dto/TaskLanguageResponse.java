package pl.codelobby.task.dto;

import pl.codelobby.task.ProgrammingLanguage;

import java.util.List;

public record TaskLanguageResponse(
        ProgrammingLanguage language,
        List<TaskFileResponse> files
) {
}
