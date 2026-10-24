package pl.codelobby.common.error;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.*;
import pl.codelobby.auth.config.JwtConfig;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        GlobalExceptionHandler.class,
        GlobalExceptionHandlerTest.TestController.class
})
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "not-found,404,USER_NOT_FOUND,User does not exist",
            "conflict,409,EMAIL_ALREADY_EXISTS,Email is already registered",
            "unauthorized,401,UNAUTHORIZED,Authentication is required",
            "forbidden,403,FORBIDDEN,Access denied",
            "unexpected,500,INTERNAL_SERVER_ERROR,An unexpected error occurred"
    })
    void shouldReturnOnlyThePublicErrorSchema(String path, int httpStatus, String code, String message) throws Exception {
        Instant before = Instant.now().minusSeconds(1);
        var result = mockMvc.perform(get("/test/" + path))
                .andExpect(status().is(httpStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andReturn();
        var body = com.jayway.jsonpath.JsonPath.parse(result.getResponse().getContentAsString());
        java.util.Map<String, Object> fields = body.read("$");
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("code", "message", "timestamp"), fields.keySet());
        Instant timestamp = Instant.parse(body.read("$.timestamp", String.class));
        org.junit.jupiter.api.Assertions.assertFalse(timestamp.isBefore(before));
        org.junit.jupiter.api.Assertions.assertFalse(timestamp.isAfter(Instant.now().plusSeconds(1)));
    }

    @Test
    void shouldReturn400ForInvalidEmail() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "invalid-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn400ForInvalidJson() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"));
    }

    @Test
    void shouldReturn404ForMissingUser() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("User does not exist"));
    }

    @Test
    void shouldReturn409ForDuplicateEmail() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void shouldReturn500WithoutExposingDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code")
                        .value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("An unexpected error occurred"));
    }

    @Test
    void shouldReturn401ForUnauthorized() throws Exception {
        mockMvc.perform(get("/test/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn403ForForbidden() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("FORBIDDEN"));
    }

    @Test
    void shouldReturn405ForUnsupportedMethod() throws Exception {
        mockMvc.perform(put("/test/validate"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code")
                        .value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void shouldReturn415ForUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("test@example.com"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code")
                        .value("UNSUPPORTED_MEDIA_TYPE"));
    }

    public record EmailRequest(
            @NotBlank @Email String email
    ) {
    }

    @RestController
    public static class TestController {

        @PostMapping("/test/validate")
        public void validate(
                @Valid @RequestBody EmailRequest request
        ) {
        }

        @GetMapping("/test/not-found")
        public void notFound() {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "USER_NOT_FOUND",
                    "User does not exist"
            );
        }

        @GetMapping("/test/conflict")
        public void conflict() {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_EXISTS",
                    "Email is already registered"
            );
        }

        @GetMapping("/test/unexpected")
        public void unexpected() {
            throw new IllegalStateException(
                    "Sensitive internal information"
            );
        }

        @GetMapping("/test/unauthorized")
        public void unauthorized() {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Authentication is required"
            );
        }

        @GetMapping("/test/forbidden")
        public void forbidden() {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "Access denied"
            );
        }
    }
}
