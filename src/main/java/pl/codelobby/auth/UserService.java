package pl.codelobby.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.codelobby.auth.dto.UserProfileResponse;
import pl.codelobby.common.error.ApiException;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_TOKEN",
                        "Invalid access token"
                ));

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}
