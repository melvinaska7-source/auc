package ru.privatenull.market;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public record FavoriteFilter(String id, Type type, String value, double maximumPrice,
                             String enchantment, int enchantmentLevel, boolean autoBuy) {
    public FavoriteFilter(String id, Type type, String value) {
        this(id, type, value, 0, "", 0, false);
    }

    public FavoriteFilter(String id, Type type, String value, double maximumPrice) {
        this(id, type, value, maximumPrice, "", 0, false);
    }

    public FavoriteFilter(String id, Type type, String value, double maximumPrice,
                          String enchantment, int enchantmentLevel) {
        this(id, type, value, maximumPrice, enchantment, enchantmentLevel, false);
    }

    public boolean hasEnchantment() {
        return !enchantments().isEmpty();
    }

    public Map<String, Integer> enchantments() {
        Map<String, Integer> values = new LinkedHashMap<>();
        if (enchantment == null || enchantment.isBlank()) return values;
        if (!enchantment.contains("=")) {
            if (enchantmentLevel > 0) values.put(enchantment, enchantmentLevel);
            return values;
        }
        for (String entry : enchantment.split(";")) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2 || parts[0].isBlank()) continue;
            try {
                int level = Integer.parseInt(parts[1]);
                if (level > 0) values.put(parts[0], level);
            } catch (NumberFormatException ignored) {
                // Invalid conditions are ignored while valid ones remain usable.
            }
        }
        return values;
    }

    public FavoriteFilter withEnchantments(Map<String, Integer> values) {
        String encoded = values.entrySet().stream().filter(entry -> entry.getValue() > 0)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
        return new FavoriteFilter(id, type, value, maximumPrice, encoded, 0, autoBuy);
    }

    public FavoriteFilter withAutoBuy(boolean enabled) {
        return new FavoriteFilter(id, type, value, maximumPrice, enchantment, enchantmentLevel, enabled);
    }

    public enum Type {
        MATERIAL,
        NAME,
        PRICE
    }
}
