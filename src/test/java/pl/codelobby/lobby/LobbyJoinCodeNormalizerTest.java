package pl.codelobby.lobby;


import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LobbyJoinCodeNormalizerTest {

    private final LobbyJoinCodeNormalizer normalizer =
            new LobbyJoinCodeNormalizer();

    @Test
    void shouldTrimAndPreserveValidAndEmptyCodes() {
        assertEquals("ABCD2345", normalizer.normalize("  ABCD2345\t\n"));
        assertEquals("ABCD2345", normalizer.normalize("ABCD2345"));
        assertEquals("", normalizer.normalize(" \t\n"));
        assertEquals("AB CD", normalizer.normalize(" ab cd "));
    }

    @Test
    void shouldUseRootLocaleEvenWhenDefaultLocaleIsTurkish() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("I2345678", normalizer.normalize("i2345678"));
        } finally {
            Locale.setDefault(previous);
        }
    }

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
