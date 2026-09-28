package ru.privatenull.market;

import java.util.ArrayList;
import java.util.List;

final class NotificationTimeFormatter {
    private NotificationTimeFormatter() {
    }

    static String elapsed(long createdAt, long now) {
        long seconds = Math.max(0, now - createdAt) / 1_000L;
        long hours = seconds / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long remainder = seconds % 60L;
        List<String> parts = new ArrayList<>(3);
        if (hours > 0) parts.add(unit(hours, "час", "часа", "часов"));
        if (minutes > 0) parts.add(unit(minutes, "минута", "минуты", "минут"));
        if (remainder > 0 || parts.isEmpty()) {
            parts.add(unit(remainder, "секунда", "секунды", "секунд"));
        }
        return String.join(" ", parts);
    }

    private static String unit(long amount, String one, String few, String many) {
        long lastTwo = amount % 100;
        long last = amount % 10;
        String form = lastTwo >= 11 && lastTwo <= 14
                ? many : last == 1 ? one : last >= 2 && last <= 4 ? few : many;
        return amount + " " + form;
    }
}
