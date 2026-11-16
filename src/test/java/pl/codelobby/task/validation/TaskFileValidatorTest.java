package pl.codelobby.task.validation;

import org.junit.jupiter.api.Test;
import pl.codelobby.task.TaskFile;
import pl.codelobby.task.TaskFileType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskFileValidatorTest {

    private final TaskFileValidator validator = new TaskFileValidator();

    private TaskFile file(String path, TaskFileType type, String content) {
        return new TaskFile(null, path, type, content);
    }

    @Test
    void shouldAcceptValidFiles() {
        List<TaskFile> files = List.of(
                file("src/Solution.java", TaskFileType.EDITABLE, "class Solution {}"),
                file("tests/Test.java", TaskFileType.VISIBLE_TEST, "class Test {}")
        );

        assertDoesNotThrow(() -> validator.validateFiles(files));
    }

    @Test
    void shouldRejectPathTraversal() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validatePath("../secret.txt")
        );
    }

    @Test
    void shouldRejectDuplicatePaths() {
        List<TaskFile> files = List.of(
                file("src/Solution.java", TaskFileType.EDITABLE, "code"),
                file("src/solution.java", TaskFileType.VISIBLE_TEST, "tests")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateFiles(files)
        );
    }

    @Test
    void shouldRejectMissingEditableFile() {
        List<TaskFile> files = List.of(
                file("tests/Test.java", TaskFileType.VISIBLE_TEST, "tests")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateFiles(files)
        );
    }

    @Test
    void shouldRejectMissingTests() {
        List<TaskFile> files = List.of(
                file("src/Solution.java", TaskFileType.EDITABLE, "code")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateFiles(files)
        );
    }

    @Test
    void shouldRejectFileExceedingSizeLimit() {
        String largeContent = "a".repeat(128 * 1024 + 1);

        List<TaskFile> files = List.of(
                file("src/Solution.java", TaskFileType.EDITABLE, largeContent),
                file("tests/Test.java", TaskFileType.VISIBLE_TEST, "tests")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateFiles(files)
        );
    }
}