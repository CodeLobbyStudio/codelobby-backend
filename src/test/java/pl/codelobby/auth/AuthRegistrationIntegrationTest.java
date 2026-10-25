
package pl.codelobby.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pl.codelobby.auth.config.JwtConfig;
import pl.codelobby.auth.config.SecurityErrorHandler;
import pl.codelobby.auth.dto.LoginRequest;
import pl.codelobby.auth.dto.RegisterRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private JwtEncoder jwtEncoder;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecurityErrorHandler securityErrorHandler;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldTrimEmailBeforeHttpRegistrationValidation() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("  PADDED@example.com  ", "Password123!")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("padded@example.com"));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("  padded@EXAMPLE.COM  ", "Password123!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        assertEquals(1, userRepository.count());
    }

    @Test
    void shouldTrimEmailBeforeHttpLoginValidation() throws Exception {
        userRepository.saveAndFlush(new User("padded@example.com", passwordEncoder.encode("Password123!")));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("  PADDED@example.com  ", "Password123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void shouldNormalizeWhitespaceAndCaseAtTheServiceBoundary() {
        var result = authService.register(new RegisterRequest(
                "  MIXED@example.com  ", "Password123!"));
        assertEquals("mixed@example.com", result.email());
        assertEquals(result.id(), authService.authenticate(new LoginRequest(
                "  MIXED@example.com  ", "Password123!")).getId());
    }

    @Test
    void shouldRejectCaseVariantDuplicateAtTheApiBoundary() throws Exception {
        userRepository.saveAndFlush(new User("duplicate@example.com", "hash"));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("DUPLICATE@EXAMPLE.COM", "Password123!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        assertEquals(1, userRepository.count());
    }

    @ParameterizedTest
    @CsvSource({"36,201", "37,400"})
    void shouldEnforceUtf8ByteLimitForBcrypt(int characters, int expectedStatus) throws Exception {
        String password = "ą".repeat(characters); // 72 and 74 bytes, both <= 72 characters.
        var response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8").content(registerJson("bytes@example.com", password)))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 400) {
            response.andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
            assertEquals(0, userRepository.count());
        } else {
            assertTrue(passwordEncoder.matches(password,
                    userRepository.findByEmail("bytes@example.com").orElseThrow().getPasswordHash()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"email\":\"\",\"password\":\"Password123!\"}",
            "{\"email\":\"test@example.com\",\"password\":\"\"}",
            "{}", "{\"email\":null,\"password\":null}"
    })
    void shouldRejectMissingOrBlankFieldsWithoutInsertingUsers(String json) throws Exception {
        for (String endpoint : List.of("register", "login")) {
            mockMvc.perform(post("/api/auth/" + endpoint).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertEquals(0, userRepository.count());
    }

    @Test
    void shouldAcceptExactly72AsciiBytesAndReject73Characters() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("boundary@example.com", "a".repeat(72))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("too-long@example.com", "a".repeat(73))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertEquals(1, userRepository.count());
    }

    @Test
    void shouldKeepAuthenticationStatelessAndDisableBasicAndFormLogin() throws Exception {
        User saved = userRepository.saveAndFlush(new User("stateless@example.com", "hash"));
        String token = jwtService.generateAccessToken(saved);
        var result = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertNull(result.getRequest().getSession(false));
        assertNull(result.getResponse().getHeader("Set-Cookie"));
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/login")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectTokenForADeletedUser() throws Exception {
        User saved = userRepository.saveAndFlush(new User("deleted@example.com", "hash"));
        String token = jwtService.generateAccessToken(saved);
        userRepository.deleteById(saved.getId());
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void shouldIssueHs256WithPositiveConfiguredLifetime() {
        User user = userRepository.saveAndFlush(new User("claims@example.com", "hash"));
        Jwt jwt = jwtDecoder.decode(jwtService.generateAccessToken(user));
        assertEquals("HS256", jwt.getHeaders().get("alg"));
        assertEquals(user.getId().toString(), jwt.getSubject());
        assertEquals("https://codelobby.example", jwt.getIssuer().toString(), "Issuer contract specified in issue #43");
        assertNotNull(jwt.getIssuedAt());
        assertEquals(Duration.ofMinutes(60), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
    }

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
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code")
                        .value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message")
                        .value("Authentication is required"))
                .andExpect(jsonPath("$.timestamp").exists());
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

        mockMvc.perform(get("/api/users/me")
                .header(
                        "Authorization",
                        "Bearer invalid.jwt.token"
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code")
                        .value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturnJsonForForbiddenAccess() throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        securityErrorHandler.handle(
                request,
                response,
                new AccessDeniedException("Access denied")
        );

        assertEquals(403, response.getStatus());

        String responseBody = response.getContentAsString();

        assertEquals(
                "FORBIDDEN",
                JsonPath.read(responseBody, "$.code")
        );

        assertEquals(
                "Access denied",
                JsonPath.read(responseBody, "$.message")
        );

        assertNotNull(
                JsonPath.read(responseBody, "$.timestamp")
        );
    }




    private String registerJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    @Test
    void shouldRejectExpiredJwt() throws Exception {

        Instant now = Instant.now();

        String token = createTestToken(
                JwtConfig.ISSUER,
                now.minusSeconds(7200),
                now.minusSeconds(600)
        );

        assertThrows(
                JwtValidationException.class,
                () -> jwtDecoder.decode(token)
        );

        mockMvc.perform(get("/api/users/me")
                .header("Authorization",
                        "Bearer " + token)
        ).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("UNAUTHORIZED"));


    }

    @Test
    void shouldRejectJwtWithInvalidSignature() throws Exception {

        Instant now = Instant.now();

        String token = createTestToken(
                JwtConfig.ISSUER,
                now,
                now.plusSeconds(3600)
        );

        int signatureStart = token.lastIndexOf('.') + 1;

        char original = token.charAt(signatureStart);
        char replacement = original == 'A' ? 'B' : 'A';

        String tamperedToken =
                token.substring(0, signatureStart)
                + replacement
                + token.substring(signatureStart + 1);

        mockMvc.perform(get("/api/users/me")
                .header(
                        "Authorization",
                        "Bearer " + tamperedToken
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("UNAUTHORIZED"));

    }

    @Test
    void shouldRejectJwtWithWrongIssuer() throws Exception {

        Instant now = Instant.now();

        String token = createTestToken(
                "https://another-service.example",
                now,
                now.plusSeconds(3600)
        );

        mockMvc.perform(get("/api/users/me")
                .header(
                        "Authorization",
                        "Bearer " + token
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("UNAUTHORIZED"));
    }


    private String createTestToken(
            String issuer,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("123")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }

}
