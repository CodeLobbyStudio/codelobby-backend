package pl.codelobby.lobby;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LobbyJoinCodeNormalizerTest {

    private final LobbyJoinCodeNormalizer normalizer =
            new LobbyJoinCodeNormalizer();

    @Test
    void shouldNormalizeLowercaseCode() {
        assertEquals(
                "ABCD23456",
                normalizer.normalize("abcd23456")
        );
    }

    @Test
    void shouldRejectNullCode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalize(null)
        );
    }
}
