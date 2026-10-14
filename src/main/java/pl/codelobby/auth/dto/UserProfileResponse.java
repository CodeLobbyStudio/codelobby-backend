package pl.codelobby.auth.dto;

import java.time.Instant;

public record UserProfileResponse(
        Long id,
        String email,
        Instant createdAt
) {
}
