package pl.codelobby.auth.dto;

public record LoginResponse (
        String accessToken,
        String tokenType
){
}
