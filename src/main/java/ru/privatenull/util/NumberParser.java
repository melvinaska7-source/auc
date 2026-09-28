package ru.privatenull.util;

import java.util.Locale;

/** Parses human-friendly amounts such as 10K, 1M, 1B, 1T and 1Q. */
public final class NumberParser {
    private NumberParser() {
    }

    public static double parse(String raw) {
        if (raw == null) throw new NumberFormatException("null");
        String value = raw.trim().toLowerCase(Locale.ROOT).replace(',', '.').replace("_", "");
        double multiplier = 1.0;
        if (value.endsWith("q")) {
            multiplier = 1_000_000_000_000_000.0;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("t")) {
            multiplier = 1_000_000_000_000.0;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("b")) {
            multiplier = 1_000_000_000.0;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("kk")) {
            multiplier = 1_000_000.0;
            value = value.substring(0, value.length() - 2);
        } else if (value.endsWith("k")) {
            multiplier = 1_000.0;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("m")) {
            multiplier = 1_000_000.0;
            value = value.substring(0, value.length() - 1);
        }
        double result = Double.parseDouble(value);
        if (!Double.isFinite(result) || !Double.isFinite(result * multiplier)) {
            throw new NumberFormatException("non-finite amount");
        }
        return result * multiplier;
    }

    public static String compact(double amount) {
        double absolute = Math.abs(amount);
        double divisor;
        String suffix;
        if (absolute >= 1_000_000_000_000_000.0) {
            divisor = 1_000_000_000_000_000.0;
            suffix = "Q";
        } else if (absolute >= 1_000_000_000_000.0) {
            divisor = 1_000_000_000_000.0;
            suffix = "T";
        } else if (absolute >= 1_000_000_000.0) {
            divisor = 1_000_000_000.0;
            suffix = "B";
        } else if (absolute >= 1_000_000.0) {
            divisor = 1_000_000.0;
            suffix = "M";
        } else if (absolute >= 1_000.0) {
            divisor = 1_000.0;
            suffix = "K";
        } else {
            return Math.rint(amount) == amount ? Long.toString((long) amount) : trim(amount);
        }
        return trim(amount / divisor) + suffix;
    }

    public static long parseDurationMillis(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("empty duration");
        String value = raw.trim().toLowerCase(Locale.ROOT);
        char unit = value.charAt(value.length() - 1);
        long multiplier = switch (unit) {
            case 's' -> 1_000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            default -> throw new IllegalArgumentException("unknown duration unit");
        };
        double count = Double.parseDouble(value.substring(0, value.length() - 1).replace(',', '.'));
        double millis = count * multiplier;
        if (!Double.isFinite(millis) || millis < 1_000.0 || millis > Long.MAX_VALUE) {
            throw new IllegalArgumentException("duration out of range");
        }
        return (long) millis;
    }

    public static String compactDuration(long millis) {
        long seconds = Math.max(0L, millis / 1_000L);
        long days = seconds / 86_400L;
        if (days > 0) return days + "d " + (seconds % 86_400L) / 3_600L + "h";
        long hours = seconds / 3_600L;
        if (hours > 0) return hours + "h " + (seconds % 3_600L) / 60L + "m";
        long minutes = seconds / 60L;
        if (minutes > 0) return minutes + "m " + seconds % 60L + "s";
        return seconds + "s";
    }

    private static String trim(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
