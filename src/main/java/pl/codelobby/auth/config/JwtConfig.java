
package pl.codelobby.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.util.Base64;

@Configuration
public class JwtConfig {

    public static final String ISSUER = "https://codelobby.example";

    @Bean
    public SecretKey jwtSecretKey(
            @Value("${app.jwt.secret-base64}") String secretBase64
    ) {
        byte[] keyBytes;

        try {
            keyBytes = Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "JWT secret must be valid Base64",
                    exception
            );
        }

        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "JWT secret must contain exactly 32 bytes"
            );
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder
                .withSecretKey(jwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(ISSUER)
        );

        return decoder;
    }
}
