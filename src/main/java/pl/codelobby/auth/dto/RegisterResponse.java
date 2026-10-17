package pl.codelobby.auth.dto;

import java.time.Instant;

public record RegisterResponse (
        Long id,
        String email,
        Instant createdAt
){
}
