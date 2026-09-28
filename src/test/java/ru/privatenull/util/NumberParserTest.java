package ru.privatenull.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NumberParserTest {
    @Test
    void parsesCommonShortcuts() {
        assertEquals(10_000.0, NumberParser.parse("10k"));
        assertEquals(1_000_000.0, NumberParser.parse("1kk"));
        assertEquals(1_000_000.0, NumberParser.parse("1m"));
        assertEquals(1_500.0, NumberParser.parse("1,5k"));
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(NumberFormatException.class, () -> NumberParser.parse("k"));
        assertThrows(NumberFormatException.class, () -> NumberParser.parse("NaN"));
    }

    @Test
    void formatsCompactAmountsAndDurations() {
        assertEquals("1K", NumberParser.compact(1_000));
        assertEquals("1.5K", NumberParser.compact(1_500));
        assertEquals("1M", NumberParser.compact(1_000_000));
        assertEquals(86_400_000L, NumberParser.parseDurationMillis("1d"));
        assertEquals(90_000L, NumberParser.parseDurationMillis("1.5m"));
        assertEquals("2d 1h", NumberParser.compactDuration(49L * 3_600_000L));
    }
}
