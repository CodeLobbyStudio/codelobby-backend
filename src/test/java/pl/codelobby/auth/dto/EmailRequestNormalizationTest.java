package pl.codelobby.auth.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmailRequestNormalizationTest {

    @Test
    void shouldTrimRegistrationEmailWithoutChangingPassword() {
        RegisterRequest request = new RegisterRequest(
                "  USER@example.com  ",
                "Password123!"
        );

        assertEquals("USER@example.com", request.email());
        assertEquals("Password123!", request.password());
    }

    @Test
    void shouldTrimLoginEmailWithoutChangingPassword() {
        LoginRequest request = new LoginRequest(
                "  USER@example.com  ",
                "Password123!"
        );

        assertEquals("USER@example.com", request.email());
        assertEquals("Password123!", request.password());
    }

    @Test
    void shouldPreserveNullEmailForBeanValidation() {
        assertNull(new RegisterRequest(null, "Password123!").email());
        assertNull(new LoginRequest(null, "Password123!").email());
    }
}
