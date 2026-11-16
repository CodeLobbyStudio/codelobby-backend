package pl.codelobby.task;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class TaskMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").target(target).cleanDisabled(false).load();
    }

    @BeforeEach
    void startWithFreshDatabase() {
        flyway("5").clean();
    }

    // Every statement is committed on its own connection. A rejected INSERT cannot
    // leave the next assertion in PostgreSQL's aborted transaction state.
    private void execute(String sql) throws SQLException {
        try (Connection connection = postgres.createConnection(""); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute(sql);
        }
    }

    private List<String> strings(String sql) throws SQLException {
        try (Connection connection = postgres.createConnection(""); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (result.next()) values.add(result.getString(1));
            return values;
        }
    }

    private void seed() throws SQLException {
        flyway("5").migrate();
        execute("INSERT INTO app_users(id,email,password_hash) VALUES (1,'first@example.com','hash'),(2,'second@example.com','hash')");
        execute("INSERT INTO tags(id,name,slug) VALUES (1,'Algorithms','algorithms'),(2,'Graphs','graphs')");
        execute("INSERT INTO tasks(id,catalog_number,title,description,type,difficulty,author_id) VALUES "
                + "(1,42,'Binary Search','Description','OFFICIAL','MEDIUM',1),"
                + "(2,NULL,'Private','Description','CUSTOM',NULL,2)");
        execute("INSERT INTO task_tags(task_id,tag_id) VALUES (1,1),(1,2),(2,1)");
        execute("INSERT INTO task_languages(id,task_id,language) VALUES (1,1,'JAVA'),(2,1,'CPP'),(3,2,'JAVA')");
        execute("INSERT INTO task_files(id,task_language_id,path,file_type,content) VALUES "
                + "(1,1,'main','EDITABLE','// TODO'),(2,2,'main','HIDDEN_TEST','CPP test'),(3,3,'main','READ_ONLY','Other task')");
    }

    @Test
    void shouldMigrateFreshPostgresThroughV5WithAllTaskTablesConstraintsAndIndexes() throws SQLException {
        assertThat(flyway("5").migrate().migrationsExecuted).isEqualTo(5);
        assertThat(strings("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank"))
                .containsExactly("1", "2", "3", "4", "5");
        assertThat(strings("SELECT tablename FROM pg_tables WHERE schemaname='public'"))
                .contains("tasks", "tags", "task_tags", "task_languages", "task_files");
        Map<String, List<String>> constraints = Map.of(
                "tasks", List.of("uk_tasks_catalog_number", "fk_tasks_author", "ck_tasks_type", "ck_tasks_difficulty", "ck_tasks_catalog"),
                "tags", List.of("uk_tags_name", "uk_tags_slug"),
                "task_tags", List.of("pk_task_tags", "fk_task_tags_task", "fk_task_tags_tag"),
                "task_languages", List.of("fk_task_languages_task", "uk_task_languages_task_language", "ck_task_languages_language"),
                "task_files", List.of("fk_task_files_task_language", "uk_task_files_language_path", "ck_task_files_type", "ck_task_files_path_not_empty"));
        for (var entry : constraints.entrySet()) {
            assertThat(strings("SELECT conname FROM pg_constraint WHERE conrelid='" + entry.getKey() + "'::regclass"))
                    .containsAll(entry.getValue());
        }
        assertThat(strings("SELECT indexname FROM pg_indexes WHERE schemaname='public'"))
                .contains("idx_tasks_author_id", "idx_tasks_type_difficulty", "idx_task_tags_tag_id",
                        "uk_tasks_catalog_number", "uk_tags_name", "uk_tags_slug", "pk_task_tags",
                        "uk_task_languages_task_language", "uk_task_files_language_path");
        flyway("5").validate();
        assertThat(flyway("5").migrate().migrationsExecuted).isZero();
    }

    @Test
    void shouldUpgradeV4WithoutLosingExistingUsersOrLobbyMemberships() throws SQLException {
        flyway("4").migrate();
        execute("INSERT INTO app_users(id,email,password_hash) VALUES (1,'existing@example.com','hash')");
        execute("INSERT INTO lobbies(id,name,join_code,status,host_id) VALUES (1,'Existing','ABCD2345','WAITING',1)");
        execute("INSERT INTO lobby_members(lobby_id,user_id,role) VALUES (1,1,'HOST')");
        assertThat(flyway("5").migrate().migrationsExecuted).isEqualTo(1);
        assertThat(strings("SELECT email FROM app_users")).containsExactly("existing@example.com");
        assertThat(strings("SELECT name FROM lobbies")).containsExactly("Existing");
        assertThat(strings("SELECT lobby_id || ':' || user_id || ':' || role FROM lobby_members")).containsExactly("1:1:HOST");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRows")
    void shouldRejectInvalidRowsAtDatabaseBoundary(String description, String sql, String state, String detail) throws SQLException {
        seed();
        SQLException error = assertThrows(SQLException.class, () -> execute(sql));
        assertThat(error.getSQLState()).isEqualTo(state);
        assertThat(error.getMessage()).contains(detail);
        assertThat(strings("SELECT count(*) FROM tasks")).containsExactly("2");
        assertThat(strings("SELECT count(*) FROM task_languages")).containsExactly("3");
        assertThat(strings("SELECT count(*) FROM task_files")).containsExactly("3");
        assertThat(strings("SELECT count(*) FROM tags")).containsExactly("2");
        assertThat(strings("SELECT count(*) FROM task_tags")).containsExactly("3");
    }

    static Stream<Arguments> invalidRows() {
        List<Arguments> cases = new ArrayList<>();
        Map<String, String> task = row("id", "100", "catalog_number", "NULL", "title", "'New'", "description", "'Description'",
                "type", "'CUSTOM'", "difficulty", "NULL", "author_id", "1");
        Map<String, String> official = new LinkedHashMap<>(task);
        official.put("type", "'OFFICIAL'");
        official.put("catalog_number", "43");
        official.put("difficulty", "'EASY'");
        invalid(cases, "tasks", official, "catalog_number", "42", "23505", "uk_tasks_catalog_number");
        // An unknown type violates both type and catalog checks; PostgreSQL may report either.
        invalid(cases, "tasks", task, "type", "'INVALID'", "23514", "tasks");
        invalid(cases, "tasks", task, "difficulty", "'INVALID'", "23514", "ck_tasks_difficulty");
        for (String number : List.of("NULL", "0", "-1")) {
            invalid(cases, "tasks", official, "catalog_number", number, "23514", "ck_tasks_catalog");
        }
        invalid(cases, "tasks", official, "difficulty", "NULL", "23514", "ck_tasks_catalog");
        invalid(cases, "tasks", task, "catalog_number", "99", "23514", "ck_tasks_catalog");
        invalid(cases, "tasks", task, "author_id", "999", "23503", "fk_tasks_author");
        for (String column : List.of("title", "description", "type", "author_id")) {
            invalid(cases, "tasks", task, column, "NULL", "23502", column);
        }
        Map<String, String> withTimestamp = new LinkedHashMap<>(task);
        withTimestamp.put("created_at", "NULL");
        cases.add(Arguments.of("tasks.created_at rejects NULL", insert("tasks", withTimestamp), "23502", "created_at"));

        Map<String, String> tag = row("id", "100", "name", "'New'", "slug", "'new'");
        invalid(cases, "tags", tag, "name", "'Algorithms'", "23505", "uk_tags_name");
        invalid(cases, "tags", tag, "slug", "'algorithms'", "23505", "uk_tags_slug");
        for (String column : List.of("name", "slug")) invalid(cases, "tags", tag, column, "NULL", "23502", column);

        Map<String, String> language = row("id", "100", "task_id", "2", "language", "'CPP'");
        invalid(cases, "task_languages", language, "task_id", "1", "23505", "uk_task_languages_task_language");
        invalid(cases, "task_languages", language, "task_id", "999", "23503", "fk_task_languages_task");
        invalid(cases, "task_languages", language, "language", "'RUBY'", "23514", "ck_task_languages_language");
        for (String column : List.of("task_id", "language")) invalid(cases, "task_languages", language, column, "NULL", "23502", column);

        Map<String, String> file = row("id", "100", "task_language_id", "1", "path", "'new'", "file_type", "'EDITABLE'", "content", "'// TODO'");
        invalid(cases, "task_files", file, "path", "'main'", "23505", "uk_task_files_language_path");
        invalid(cases, "task_files", file, "task_language_id", "999", "23503", "fk_task_files_task_language");
        invalid(cases, "task_files", file, "file_type", "'EXECUTABLE'", "23514", "ck_task_files_type");
        for (String path : List.of("''", "'   '")) invalid(cases, "task_files", file, "path", path, "23514", "ck_task_files_path_not_empty");
        for (String column : List.of("task_language_id", "path", "file_type", "content")) {
            invalid(cases, "task_files", file, column, "NULL", "23502", column);
        }

        Map<String, String> link = row("task_id", "2", "tag_id", "2");
        invalid(cases, "task_tags", link, "tag_id", "1", "23505", "pk_task_tags");
        invalid(cases, "task_tags", link, "tag_id", "999", "23503", "fk_task_tags_tag");
        invalid(cases, "task_tags", link, "task_id", "999", "23503", "fk_task_tags_task");
        for (String column : List.of("task_id", "tag_id")) invalid(cases, "task_tags", link, column, "NULL", "23502", column);
        return cases.stream();
    }

    private static Map<String, String> row(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put(pairs[i], pairs[i + 1]);
        return values;
    }

    private static String insert(String table, Map<String, String> values) {
        return "INSERT INTO " + table + "(" + String.join(",", values.keySet()) + ") VALUES (" + String.join(",", values.values()) + ")";
    }

    private static void invalid(List<Arguments> cases, String table, Map<String, String> valid,
                                String column, String value, String state, String detail) {
        Map<String, String> invalid = new LinkedHashMap<>(valid);
        invalid.put(column, value);
        cases.add(Arguments.of(table + "." + column + " rejects " + value, insert(table, invalid), state, detail));
    }

    @Test
    void shouldAcceptAllDeclaredEnumsMultipleNullCatalogNumbersAndLanguageScopedPaths() throws SQLException {
        seed();
        int id = 100;
        for (TaskDifficulty difficulty : TaskDifficulty.values()) {
            execute("INSERT INTO tasks(id,catalog_number,title,description,type,difficulty,author_id) VALUES ("
                    + id + "," + id + ",'Official','Description','OFFICIAL','" + difficulty + "',1)");
            id++;
        }
        execute("INSERT INTO tasks(id,title,description,type,author_id) VALUES (200,'Custom','Description','CUSTOM',1)");
        for (ProgrammingLanguage language : ProgrammingLanguage.values()) {
            int languageId = id++;
            execute("INSERT INTO task_languages(id,task_id,language) VALUES (" + languageId + ",200,'" + language + "')");
            for (TaskFileType type : TaskFileType.values()) {
                execute("INSERT INTO task_files(id,task_language_id,path,file_type,content) VALUES ("
                        + id++ + "," + languageId + ",'" + type + "','" + type + "','// TODO')");
            }
        }
        assertThat(strings("SELECT count(*) FROM tasks WHERE type='CUSTOM' AND catalog_number IS NULL AND difficulty IS NULL"))
                .containsExactly("2");
        assertThat(strings("SELECT count(*) FROM task_languages WHERE task_id=200")).containsExactly("3");
        assertThat(strings("SELECT count(*) FROM task_files f JOIN task_languages l ON l.id=f.task_language_id WHERE l.task_id=200"))
                .containsExactly("12");
        assertThat(strings("SELECT count(*) FROM tasks WHERE created_at IS NOT NULL AND created_at BETWEEN now()-interval '1 minute' AND now()+interval '1 minute'"))
                .containsExactly("6");
    }

    @Test
    void shouldCascadeSqlTaskDeletionWithoutDeletingOtherTasksTagsOrAuthors() throws SQLException {
        seed();
        execute("DELETE FROM tasks WHERE id=1");
        assertThat(strings("SELECT id FROM tasks")).containsExactly("2");
        assertThat(strings("SELECT task_id || ':' || tag_id FROM task_tags")).containsExactly("2:1");
        assertThat(strings("SELECT id FROM task_languages")).containsExactly("3");
        assertThat(strings("SELECT id FROM task_files")).containsExactly("3");
        assertThat(strings("SELECT id FROM tags ORDER BY id")).containsExactly("1", "2");
        assertThat(strings("SELECT id FROM app_users ORDER BY id")).containsExactly("1", "2");
    }

    @Test
    void shouldCascadeSqlLanguageDeletionOnlyToItsFiles() throws SQLException {
        seed();
        execute("DELETE FROM task_languages WHERE id=1");
        assertThat(strings("SELECT id FROM task_files ORDER BY id")).containsExactly("2", "3");
        assertThat(strings("SELECT id FROM task_languages ORDER BY id")).containsExactly("2", "3");
        assertThat(strings("SELECT count(*) FROM tasks")).containsExactly("2");
        assertThat(strings("SELECT count(*) FROM task_tags")).containsExactly("3");
    }

    @Test
    void shouldRejectDeletingReferencedAuthorOrTag() throws SQLException {
        seed();
        SQLException author = assertThrows(SQLException.class, () -> execute("DELETE FROM app_users WHERE id=1"));
        assertThat(author.getSQLState()).isEqualTo("23503");
        assertThat(author.getMessage()).contains("fk_tasks_author");
        SQLException tag = assertThrows(SQLException.class, () -> execute("DELETE FROM tags WHERE id=1"));
        assertThat(tag.getSQLState()).isEqualTo("23503");
        assertThat(tag.getMessage()).contains("fk_task_tags_tag");
        assertThat(strings("SELECT count(*) FROM tasks")).containsExactly("2");
        assertThat(strings("SELECT count(*) FROM task_tags")).containsExactly("3");
    }
}
