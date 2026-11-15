package pl.codelobby.task;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import pl.codelobby.auth.config.JwtConfig;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pl.codelobby.auth.JwtService;
import pl.codelobby.auth.User;
import pl.codelobby.auth.UserRepository;

import java.util.Locale;
import java.time.Instant;
import java.util.StringJoiner;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"DB_PASSWORD=unused", "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class TaskApiIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired JwtService tokens;
    @Autowired JwtEncoder encoder;
    String token;
    User author;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE tasks, app_users CASCADE");
        author = users.saveAndFlush(new User("author@example.com", "test-hash"));
        token = tokens.generateAccessToken(author);
    }

    String editLanguage(String name) {
        return """
                {"language":"%s","files":[
                {"path":"main.txt","type":"EDITABLE","content":"// TODO %s żółć"},
                {"path":"library.txt","type":"READ_ONLY","content":""},
                {"path":"visible.txt","type":"VISIBLE_TEST","content":"visible UTF-8 ą"},
                {"path":"hidden.txt","type":"HIDDEN_TEST","content":"private-fixture-secret-913 %s"}]}
                """.formatted(name, name, name);
    }

    long createTask(String body, String bearer) throws Exception {
        var response = mvc.perform(post("/api/tasks").header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.<Number>read(response.getResponse().getContentAsString(), "$.id").longValue();
    }

    Map<String, Set<Map<String, Object>>> languageFiles(String json) {
        List<Map<String, Object>> languages = JsonPath.read(json, "$.languages");
        Map<String, Set<Map<String, Object>>> result = new HashMap<>();
        for (Map<String, Object> language : languages) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> files = (List<Map<String, Object>>) language.get("files");
            assertEquals(Set.of("language", "files"), language.keySet());
            for (Map<String, Object> file : files) {
                assertEquals(Set.of("path", "type", "content"), file.keySet());
            }
            assertEquals(files.size(), new HashSet<>(files).size());
            assertNull(result.put((String) language.get("language"), new HashSet<>(files)));
        }
        return result;
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void shouldReadExactlyTheAuthorsEditDtoWithoutChangingStoredData(boolean cpp, boolean difficulty) throws Exception {
        String body = request(editLanguage("JAVA") + (cpp ? "," + editLanguage("CPP") : ""));
        if (difficulty) body = body.replace("\"title\":", "\"difficulty\":\"EASY\",\"title\":");
        long id = createTask(body, token);
        var tasksBefore = jdbc.queryForList("SELECT * FROM tasks ORDER BY id");
        var languagesBefore = jdbc.queryForList("SELECT * FROM task_languages ORDER BY id");
        var filesBefore = jdbc.queryForList("SELECT * FROM task_files ORDER BY id");
        String response = mvc.perform(get("/api/tasks/{id}/edit", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.title").value("Practice"))
                .andExpect(jsonPath("$.description").value("Description"))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> dto = JsonPath.read(response, "$");
        assertEquals(Set.of("id", "title", "description", "difficulty", "languages"), dto.keySet());
        assertEquals(difficulty ? "EASY" : null, dto.get("difficulty"));
        assertEquals(languageFiles(body), languageFiles(response));
        assertEquals(tasksBefore, jdbc.queryForList("SELECT * FROM tasks ORDER BY id"));
        assertEquals(languagesBefore, jdbc.queryForList("SELECT * FROM task_languages ORDER BY id"));
        assertEquals(filesBefore, jdbc.queryForList("SELECT * FROM task_files ORDER BY id"));
    }

    @Test
    void shouldDenyOtherUserWithoutLeakingFilesAndAllowTheirOwnTask() throws Exception {
        long id = createTask(request(editLanguage("JAVA")), token);
        User other = users.saveAndFlush(new User("other@example.com", "test-hash"));
        String otherToken = tokens.generateAccessToken(other);
        String denied = mvc.perform(get("/api/tasks/{id}/edit", id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TASK_ACCESS_DENIED"))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> error = JsonPath.read(denied, "$");
        assertEquals(Set.of("code", "message", "timestamp"), error.keySet());
        for (String secret : List.of("main.txt", "library.txt", "visible.txt", "hidden.txt", "private-fixture-secret-913", "żółć")) {
            assertFalse(denied.contains(secret));
        }
        long ownId = createTask(request(editLanguage("CPP")), otherToken);
        mvc.perform(get("/api/tasks/{id}/edit", ownId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(ownId));
    }

    @Test
    void shouldReturnNotFoundForAuthenticatedUser() throws Exception {
        mvc.perform(get("/api/tasks/{id}/edit", Long.MAX_VALUE).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    @Test
    void shouldRejectMissingInvalidExpiredAndDeletedUserTokensOnGet() throws Exception {
        long id = createTask(request(editLanguage("JAVA")), token);
        mvc.perform(get("/api/tasks/{id}/edit", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tasks/{id}/edit", id).header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
        Instant now = Instant.now();
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer(JwtConfig.ISSUER).subject(author.getId().toString())
                        .issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(600)).build())).getTokenValue();
        mvc.perform(get("/api/tasks/{id}/edit", id).header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        User removed = users.saveAndFlush(new User("removed@example.com", "test-hash"));
        String removedToken = tokens.generateAccessToken(removed);
        users.deleteById(removed.getId());
        mvc.perform(get("/api/tasks/{id}/edit", id).header("Authorization", "Bearer " + removedToken))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    String language(String name, String path, String content) {
        return """
                {"language":"%s","files":[
                  {"path":"%s","type":"EDITABLE","content":"%s"},
                  {"path":"tests.txt","type":"HIDDEN_TEST","content":"secret"}]}
                """.formatted(name, path, content);
    }

    String request(String languages) {
        return """
                {"title":"Practice","description":"Description","languages":[%s]}
                """.formatted(languages);
    }

    void reject(String body, String code) throws Exception {
        mvc.perform(post("/api/tasks").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
        // The request transaction has ended; this test has no wrapping transaction.
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tasks", Long.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM task_languages", Long.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM task_files", Long.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPersistCustomTaskWithIndependentLanguageFiles(boolean cpp) throws Exception {
        String languages = language("JAVA", "main.txt", "// TODO java");
        if (cpp) languages += "," + language("CPP", "main.txt", "// TODO cpp");
        var result = mvc.perform(post("/api/tasks").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(request(languages)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber()).andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        var task = jdbc.queryForMap("SELECT * FROM tasks WHERE id=?", id.longValue());
        assertEquals(author.getId(), task.get("author_id"));
        assertEquals("CUSTOM", task.get("type"));
        assertNull(task.get("catalog_number"));
        assertEquals("Practice", task.get("title"));
        assertEquals("Description", task.get("description"));
        assertNotNull(task.get("created_at"));
        assertEquals(cpp ? 2L : 1L, jdbc.queryForObject("SELECT count(*) FROM task_languages WHERE task_id=?", Long.class, id.longValue()));
        var files = jdbc.queryForList("SELECT l.language,f.path,f.file_type AS type,f.content FROM task_files f JOIN task_languages l ON l.id=f.task_language_id WHERE l.task_id=?", id.longValue());
        assertEquals(cpp ? 4 : 2, files.size());
        for (var file : files) {
            assertEquals(file.get("type").equals("EDITABLE")
                    ? "// TODO " + file.get("language").toString().toLowerCase(Locale.ROOT)
                    : "secret", file.get("content"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"title\":\"x\",\"description\":\"d\",\"languages\":[]}",
            "{\"title\":\"x\",\"description\":\"d\",\"languages\":[null]}",
            "{\"title\":\"x\",\"description\":\"d\",\"languages\":[{\"language\":null,\"files\":[]}]}",
            "{\"title\":\"x\",\"description\":\"d\",\"languages\":[{\"language\":\"JAVA\",\"files\":[null]}]}"})
    void shouldRejectInvalidNestedDtos(String body) throws Exception {
        reject(body, "VALIDATION_ERROR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"languages\":[{\"language\":\"RUST\"}]}"})
    void shouldRejectMalformedJsonOrEnums(String body) throws Exception {
        reject(body, "INVALID_REQUEST_BODY");
    }

    @Test
    void shouldRejectInvalidLaterLanguageWithoutPartialWrites() throws Exception {
        reject(request(language("JAVA", "main.txt", "") + "," + language("CPP", "../escape", "")), "INVALID_TASK_FILES");
    }

    @Test
    void shouldRejectDuplicateLanguagesAndOversizedUtf8Content() throws Exception {
        reject(request(language("JAVA", "main.txt", "") + "," + language("JAVA", "other.txt", "")), "INVALID_TASK_FILES");
        reject(request(language("JAVA", "main.txt", "ą".repeat(65537))), "INVALID_TASK_FILES");
    }

    @Test
    void shouldRejectExpiredSignedToken() throws Exception {
        Instant now = Instant.now();
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer(JwtConfig.ISSUER).subject(author.getId().toString())
                        .issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(600)).build())).getTokenValue();
        mvc.perform(post("/api/tasks").header("Authorization", "Bearer " + expired)
                        .contentType(MediaType.APPLICATION_JSON).content(request(language("JAVA", "main.txt", ""))))
                .andExpect(status().isUnauthorized());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tasks", Long.class));
    }

    @Test
    void shouldRejectAggregateUtf8LimitEvenWhenEachFileFits() throws Exception {
        StringJoiner files = new StringJoiner(",");
        for (int i = 0; i < 9; i++) {
            files.add("{\"path\":\"file" + i + ".txt\",\"type\":\""
                    + (i == 0 ? "EDITABLE" : "HIDDEN_TEST") + "\",\"content\":\"" + "ą".repeat(65536) + "\"}");
        }
        reject(request("{\"language\":\"JAVA\",\"files\":[" + files + "]}"), "INVALID_TASK_FILES");
    }

    @Test
    void shouldAcceptEmptyFileContent() throws Exception {
        mvc.perform(post("/api/tasks").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(request(language("JAVA", "main.txt", ""))))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldRejectCaseInsensitiveDuplicatePathsAndMissingRequiredFileTypes() throws Exception {
        String valid = language("JAVA", "main.txt", "");
        reject(request(valid.replace("tests.txt", "MAIN.TXT")), "INVALID_TASK_FILES");
        reject(request(valid.replace("EDITABLE", "READ_ONLY")), "INVALID_TASK_FILES");
        reject(request(valid.replace("HIDDEN_TEST", "READ_ONLY")), "INVALID_TASK_FILES");
        reject(request(valid.replace("\"type\":\"EDITABLE\"", "\"type\":null")), "VALIDATION_ERROR");
        reject(request(valid.replace("\"content\":\"\"", "\"content\":null")), "VALIDATION_ERROR");
    }

    @Test
    void shouldRequireRealBearerTokenAndRejectDeletedUser() throws Exception {
        String body = request(language("JAVA", "main.txt", ""));
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/tasks").header("Authorization", "Bearer invalid.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        users.deleteById(author.getId());
        mvc.perform(post("/api/tasks").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tasks", Long.class));
    }
}
