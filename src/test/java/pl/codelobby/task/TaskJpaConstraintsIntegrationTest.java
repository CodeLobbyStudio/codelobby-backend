package pl.codelobby.task;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskJpaConstraintsIntegrationTest extends TaskIntegrationTestSupport {
    @ParameterizedTest(name = "saveAndFlush rejects {0}")
    @ValueSource(strings = {
            "uk_tasks_catalog_number", "uk_task_languages_task_language", "uk_task_files_language_path",
            "uk_tags_name", "uk_tags_slug", "ck_tasks_catalog"
    })
    void shouldExposeDatabaseConstraintViolationsOnFlush(String constraint) {
        var author = author();
        Runnable rejectedWrite = switch (constraint) {
            case "uk_tasks_catalog_number" -> {
                tasks.saveAndFlush(official(author, 42, "Existing"));
                yield () -> tasks.saveAndFlush(official(author, 42, "Duplicate number"));
            }
            case "uk_task_languages_task_language" -> {
                Task task = custom(author, "Duplicate language");
                task.addLanguage(ProgrammingLanguage.JAVA);
                task.addLanguage(ProgrammingLanguage.JAVA);
                yield () -> tasks.saveAndFlush(task);
            }
            case "uk_task_files_language_path" -> {
                Task task = custom(author, "Duplicate path");
                TaskLanguage language = task.addLanguage(ProgrammingLanguage.JAVA);
                language.addFile("Main.java", TaskFileType.EDITABLE, "// TODO");
                language.addFile("Main.java", TaskFileType.HIDDEN_TEST, "Different type does not permit duplicate path");
                yield () -> tasks.saveAndFlush(task);
            }
            case "uk_tags_name" -> {
                Tag existing = tag();
                yield () -> tags.saveAndFlush(new Tag(existing.getName(), "another-" + existing.getSlug()));
            }
            case "uk_tags_slug" -> {
                Tag existing = tag();
                yield () -> tags.saveAndFlush(new Tag("Another " + existing.getName(), existing.getSlug()));
            }
            case "ck_tasks_catalog" -> {
                Task task = new Task(43, "Official without difficulty", "Description", TaskType.OFFICIAL, null, author);
                yield () -> tasks.saveAndFlush(task);
            }
            default -> throw new IllegalArgumentException(constraint);
        };
        DataIntegrityViolationException error = assertThrows(DataIntegrityViolationException.class, rejectedWrite::run);
        assertThat(error.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, sql -> {
            assertThat(sql.getSQLState()).isEqualTo(constraint.startsWith("uk_") ? "23505" : "23514");
            assertThat(sql.getMessage()).contains(constraint);
        });
        // No further queries: Spring rolls back this test transaction after the
        // assertion, including any IDENTITY inserts made before the failure.
    }
}
