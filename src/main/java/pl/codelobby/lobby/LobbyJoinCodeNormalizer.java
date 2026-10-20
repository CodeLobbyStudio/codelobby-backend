package pl.codelobby.lobby;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class LobbyJoinCodeNormalizer {

    public String normalize(String code) {
        if (code == null) {
            throw new IllegalArgumentException(
                    "Lobby join code cannot be null"
            );
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
