package pl.codelobby.auth;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class JwtServiceTest {
    @ParameterizedTest
    @ValueSource(longs = {0, -1, -60})
    void shouldRejectNonPositiveLifetimeBeforeUsingTheEncoder(long minutes) {
        JwtEncoder encoder = mock(JwtEncoder.class);
        assertThrows(IllegalArgumentException.class, () -> new JwtService(encoder, minutes));
        verifyNoInteractions(encoder);
    }
}
