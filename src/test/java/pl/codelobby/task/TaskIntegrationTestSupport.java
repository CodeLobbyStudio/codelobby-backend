package pl.codelobby.task;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pl.codelobby.auth.User;
import pl.codelobby.auth.UserRepository;

import java.util.UUID;

@SpringBootTest(properties = "DB_PASSWORD=unused")
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class TaskIntegrationTestSupport {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired EntityManager entityManager;
    @Autowired UserRepository users;
    @Autowired TaskRepository tasks;
    @Autowired TagRepository tags;
    @Autowired TaskLanguageRepository languages;
    @Autowired TaskFileRepository files;

    User author() {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "test-hash"));
    }

    Tag tag() {
        String suffix = UUID.randomUUID().toString();
        return tags.saveAndFlush(new Tag("Tag " + suffix, "tag-" + suffix));
    }

    Task custom(User author, String title) {
        return new Task(null, title, "Opis zadania — UTF-8", TaskType.CUSTOM, null, author);
    }

    Task official(User author, int number, String title) {
        return new Task(number, title, "Catalog description", TaskType.OFFICIAL, TaskDifficulty.MEDIUM, author);
    }
}
