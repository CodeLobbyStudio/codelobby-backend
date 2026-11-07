package pl.codelobby.task.dto;

import pl.codelobby.task.TaskFileType;

public record TaskFileResponse (
        String path,
        TaskFileType type,
        String content
) {
}
