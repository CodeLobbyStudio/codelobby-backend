package pl.codelobby.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.codelobby.auth.dto.UserProfileResponse;
import pl.codelobby.common.error.ApiException;

import static org.springframework.security.oauth2.server.resource.BearerTokenErrors.invalidToken;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfileResponse me(
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (jwt == null) {
            throw invalidToken();
        }

        Long userId;

        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw invalidToken();
        }

        return userService.getProfile(userId);
    }

    private ApiException invalidToken() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_TOKEN",
                "Invalid access token"
        );
    }
}
