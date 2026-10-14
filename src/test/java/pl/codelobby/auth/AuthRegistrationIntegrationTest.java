
package pl.codelobby.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pl.codelobby.auth.config.JwtConfig;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@Testcontainers
@SpringBootTest(properties = "DB_PASSWORD=unused")
@AutoConfigureMockMvc
class AuthRegistrationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("codelobby_test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterUserAndHashPassword() throws Exception {

        String email = "USER." + UUID.randomUUID() + "@EXAMPLE.COM";
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        String password = "MojeHaslo123!";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(normalizedEmail))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User savedUser = userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow();

        assertNotNull(savedUser.getId());

        assertNotEquals(
                password,
                savedUser.getPasswordHash()
        );

        assertTrue(passwordEncoder.matches(
                password,
                savedUser.getPasswordHash()
        ));
    }

    @Test
    void shouldRejectDuplicateEmail() throws Exception {

        String email = "duplicate@example.com";
        String json = registerJson(email, "MojeHaslo123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("EMAIL_ALREADY_EXISTS"));

        assertEquals(1, userRepository.count());
    }

    @Test
    void shouldRejectInvalidEmail() throws Exception {

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(
                                "invalid-email",
                                "MojeHaslo123!"
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectShortPassword() throws Exception {

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(
                                "test@example.com",
                                "123"
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    void shouldLoginAndReturnValidJwt() throws Exception {

        String email = "login@example.com";
        String password = "MojeHaslo123!";

        // Rejestrujemy użytkownika.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, password)))
                .andExpect(status().isCreated());

        // Logujemy się.
        MvcResult result = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerJson(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        // Pobieramy JWT z odpowiedzi.
        String token = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.accessToken"
        );

        // Weryfikujemy podpis i poprawność tokenu.
        Jwt decodedJwt = jwtDecoder.decode(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow();

        assertEquals(
                user.getId().toString(),
                decodedJwt.getSubject()
        );

        assertEquals(
                JwtConfig.ISSUER,
                decodedJwt.getIssuer().toString()
        );

        assertNotNull(decodedJwt.getExpiresAt());

        assertTrue(
                decodedJwt.getExpiresAt().isAfter(Instant.now())
        );
    }


    @Test
    void shouldRejectLoginWithWrongPassword() throws Exception {

        String email = "wrong-password@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "CorrectPassword123!")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "WrongPassword123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }


    @Test
    void shouldRejectLoginWithUnknownEmail() throws Exception {

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(
                                "unknown@example.com",
                                "SomePassword123!"
                        )))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldReturnCurrentUserProfile() throws Exception {

        String email = "profile@example.com";
        String password = "MojeHaslo123!";

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email, password)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(
                post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        String token = JsonPath.read(
                loginResult.getResponse().getContentAsString(),
                "$.accessToken"
        );

        mockMvc.perform(get("/api/users/me")
                .header(
                        "Authorization",
                        "Bearer " + token
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldRejectInvalidJwt() throws Exception {

        mockMvc.perform(get("/api/user/me")
                .header(
                        "Authorization",
                        "Bearer invalid.jwt.token"
                ))
                .andExpect(status().isUnauthorized());
    }




    private String registerJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }
}
