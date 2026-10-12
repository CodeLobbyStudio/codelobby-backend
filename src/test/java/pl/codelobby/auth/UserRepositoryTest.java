package pl.codelobby.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = "DB_PASSWORD=unused")
public class UserRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("codelobby_test");

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldSaveUser() {

        User user = new User(
                "test@example.com",
                "example-hash"
        );

        User savedUser = userRepository.saveAndFlush(user);

        assertNotNull(savedUser.getId());
        assertEquals("test@example.com", savedUser.getEmail());
        assertEquals("example-hash", savedUser.getPasswordHash());
        assertNotNull(savedUser.getCreatedAt());
    }

    @Test
    void shouldFindUserByEmail() {

        User user = new User(
                "john@example.com",
                "example-hash"
        );

        userRepository.saveAndFlush(user);

        User foundUser = userRepository.findByEmail(
                "john@example.com"
        ).orElseThrow();

        assertEquals(user.getId(), foundUser.getId());
        assertEquals("john@example.com", foundUser.getEmail());
        assertTrue(userRepository.existsByEmail("john@example.com"));
    }

    @Test
    void shouldRejectDuplicateEmail() {

        User firstUser = new User(
                "duplicate@example.com",
                "first-hash"
        );

        User secondUser = new User(
                "duplicate@example.com",
                "second-hash"
        );

        userRepository.saveAndFlush(firstUser);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(secondUser)
        );
    }
}
