package pl.codelobby.lobby.dto;

import pl.codelobby.task.ProgrammingLanguage;
import pl.codelobby.task.TaskFileType;

import java.util.List;

public record LobbyWorkspaceResponse(
        Long taskId,
        String taskVersionId,
        String title,
        String description,
        ProgrammingLanguage language,
        List<File> files,
        List<PublicTest> publicTests
) {
    public record File(String path, TaskFileType type, String content) {
    }

    public record PublicTest(String testId) {
    }
}
