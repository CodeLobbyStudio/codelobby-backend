package pl.codelobby.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import pl.codelobby.auth.config.JwtConfig;

import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtConfigTest {
    final JwtConfig config = new JwtConfig();
    // Test fixtures only; never use these keys outside tests.
    final javax.crypto.SecretKey key = config.jwtSecretKey(Base64.getEncoder().encodeToString(new byte[32]));
    final JwtEncoder encoder = config.jwtEncoder(key);
    final JwtDecoder decoder = config.jwtDecoder(key);

    String token(JwtEncoder signer, String issuer, Instant issued, Instant expires) {
        return signer.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer(issuer).subject("42").issuedAt(issued).expiresAt(expires).build())).getTokenValue();
    }

    @Test
    void shouldVerifyHs256SubjectIssuerAndLifetime() {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Jwt jwt = decoder.decode(token(encoder, JwtConfig.ISSUER, now, now.plusSeconds(3600)));
        assertEquals("HS256", jwt.getHeaders().get("alg"));
        assertEquals("42", jwt.getSubject());
        assertEquals(JwtConfig.ISSUER, jwt.getIssuer().toString());
        assertEquals(now, jwt.getIssuedAt());
        assertEquals(now.plusSeconds(3600), jwt.getExpiresAt());
    }

    @Test
    void shouldRejectExpiredWrongIssuerMalformedTamperedAndOtherKeyTokens() {
        Instant now = Instant.now();
        assertThrows(JwtException.class, () -> decoder.decode("invalid.jwt.token"));
        assertThrows(JwtException.class, () -> decoder.decode(token(encoder, JwtConfig.ISSUER, now.minusSeconds(7200), now.minusSeconds(600))));
        assertThrows(JwtException.class, () -> decoder.decode(token(encoder, "other-service", now, now.plusSeconds(3600))));
        String valid = token(encoder, JwtConfig.ISSUER, now, now.plusSeconds(3600));
        int signature = valid.lastIndexOf('.') + 1;
        String tampered = valid.substring(0, signature) + (valid.charAt(signature) == 'A' ? 'B' : 'A') + valid.substring(signature + 1);
        assertThrows(JwtException.class, () -> decoder.decode(tampered));
        byte[] otherKeyBytes = new byte[32];
        java.util.Arrays.fill(otherKeyBytes, (byte) 1);
        JwtEncoder otherSigner = config.jwtEncoder(config.jwtSecretKey(Base64.getEncoder().encodeToString(otherKeyBytes)));
        assertThrows(JwtException.class, () -> decoder.decode(token(otherSigner, JwtConfig.ISSUER, now, now.plusSeconds(3600))));
    }

    @Test
    void shouldRejectInvalidBase64AndWrongKeyLengths() {
        assertThrows(IllegalStateException.class, () -> config.jwtSecretKey("%%%"));
        for (int length : new int[]{0, 16, 31, 33, 64}) {
            assertThrows(IllegalStateException.class,
                    () -> config.jwtSecretKey(Base64.getEncoder().encodeToString(new byte[length])));
        }
    }
}
