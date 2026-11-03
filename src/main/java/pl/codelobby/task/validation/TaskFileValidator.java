package pl.codelobby.task.validation;

import org.springframework.stereotype.Component;
import pl.codelobby.task.ProgrammingLanguage;
import pl.codelobby.task.TaskFile;
import pl.codelobby.task.TaskFileType;
import pl.codelobby.task.TaskLanguage;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class TaskFileValidator {

    public void validatePath(String path) {

        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("File path cannot be empty");
        }

        if (path.length() > 240) {
            throw new IllegalArgumentException("File path is too long");
        }

        String[] segments = path.split("/", -1);

        for (String segment : segments) {
            validateSegment(segment);
        }
    }

    public void validateFiles(List<TaskFile> files) {

        if (files == null || files.isEmpty() || files.size() > 50) {
            throw new IllegalArgumentException("Invalid number of files");
        }

        Set<String> paths = new HashSet<>();

        boolean hasEditable = false;
        boolean hasTests = false;

        long totalSize = 0;

        for (TaskFile file : files) {

            if (file == null || file.getType() == null
                    || file.getContent() == null) {
                throw new IllegalArgumentException("Invalid file");
            }

            validatePath(file.getPath());

            String normalizedPath = file.getPath().toLowerCase(Locale.ROOT);

            if (!paths.add(normalizedPath)) {
                throw new IllegalArgumentException("Duplicate file path");
            }

            int size = getUtf8Size(file.getContent());

            if (size > 128 * 1024) {
                throw new IllegalArgumentException("File is too large");
            }

            totalSize += size;

            if (totalSize > 1024 * 1024) {
                throw new IllegalArgumentException("Total files size exceeded");
            }

            if (file.getType() == TaskFileType.EDITABLE) {
                hasEditable = true;
            }

            if (file.getType() == TaskFileType.VISIBLE_TEST
                    || file.getType() == TaskFileType.HIDDEN_TEST) {
                hasTests = true;
            }
        }

        if (!hasEditable || !hasTests) {
            throw new IllegalArgumentException(
                    "Each language requires editable files and tests"
            );
        }
    }

    public void validateSegment(String segment) {

        if (segment.isEmpty()
                || segment.equals(".")
                || segment.equals("..")) {
            throw new IllegalArgumentException("Invalid path segment");
        }

        if (!segment.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid characters in file path");
        }

        if (segment.endsWith(".")) {
            throw new IllegalArgumentException("Path segment cannot end with a dot");
        }

        String baseName = segment.split("\\.", 2)[0]
                .toUpperCase(Locale.ROOT);

        if (baseName.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) {
            throw new IllegalArgumentException("Reserved file name");
        }
    }


    private int getUtf8Size(String content) {

        var encoder = StandardCharsets.UTF_8
                .newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            return encoder.encode(CharBuffer.wrap(content)).remaining();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(
                    "File contains invalid Unicode characters", e
            );
        }
    }

    public void validateTaskLanguages(List<TaskLanguage> languages) {

        if (languages == null || languages.isEmpty()) {
            throw new IllegalArgumentException(
                    "Task must have at least one language"
            );
        }

        Set<ProgrammingLanguage> usedLanguages = new HashSet<>();

        for (TaskLanguage taskLanguage : languages) {

            if (taskLanguage == null || taskLanguage.getLanguage() == null) {
                throw new IllegalArgumentException(
                        "Invalid task language"
                );
            }

            if (!usedLanguages.add(taskLanguage.getLanguage())) {
                throw new IllegalArgumentException(
                        "Duplicate programming language"
                );
            }

            validateFiles(taskLanguage.getFiles());
        }
    }

}
