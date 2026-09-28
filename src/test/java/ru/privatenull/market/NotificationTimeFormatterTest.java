package ru.privatenull.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationTimeFormatterTest {
    @Test
    void formatsElapsedTimeWithRussianPluralForms() {
        long now = 10_000_000L;
        assertEquals("0 секунд", NotificationTimeFormatter.elapsed(now + 1_000L, now));
        assertEquals("1 секунда", NotificationTimeFormatter.elapsed(now - 1_000L, now));
        assertEquals("2 секунды", NotificationTimeFormatter.elapsed(now - 2_000L, now));
        assertEquals("5 секунд", NotificationTimeFormatter.elapsed(now - 5_000L, now));
        assertEquals("1 минута 1 секунда", NotificationTimeFormatter.elapsed(now - 61_000L, now));
        assertEquals("4 минуты 5 секунд", NotificationTimeFormatter.elapsed(now - 245_000L, now));
        assertEquals("1 час 4 минуты 5 секунд", NotificationTimeFormatter.elapsed(now - 3_845_000L, now));
        assertEquals("2 часа", NotificationTimeFormatter.elapsed(now - 7_200_000L, now));
        assertEquals("10 часов", NotificationTimeFormatter.elapsed(now - 36_000_000L, now));
        assertEquals("21 час", NotificationTimeFormatter.elapsed(now - 75_600_000L, now));
        assertEquals("11 часов", NotificationTimeFormatter.elapsed(now - 39_600_000L, now));
    }
}
