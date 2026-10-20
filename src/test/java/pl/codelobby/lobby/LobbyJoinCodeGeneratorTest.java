package pl.codelobby.lobby;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LobbyJoinCodeGeneratorTest {

    private final LobbyJoinCodeGenerator generator =
            new LobbyJoinCodeGenerator();

    @Test
    void shouldGenerateCodeWithEightCharacters() {
        String code = generator.generate();

        assertEquals(8, code.length());
    }

    @Test
    void shouldGenerateCodeUsingOnlyAllowedCharacters() {
        String code = generator.generate();

        assertTrue(
                code.matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}")
        );
    }

    @Test
    void shouldGenerateValidCodesRepeatedly() {
        for (int i = 0; i < 100; i++) {
            String code = generator.generate();

            assertNotNull(code);
            assertEquals(8, code.length());
            assertTrue(
                    code.matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}")
            );
        }
    }
}
