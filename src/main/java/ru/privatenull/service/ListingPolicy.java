package ru.privatenull.service;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.entity.Player;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.util.NumberParser;

import java.util.*;

public final class ListingPolicy {
    private final PnMarketPlugin plugin;
    private final CurrencyRegistry currencies;

    public ListingPolicy(PnMarketPlugin plugin, CurrencyRegistry currencies) {
        this.plugin = plugin;
        this.currencies = currencies;
    }

    public int listingLimit(Player player) {
        int fallback = integer("sell.limits.default", "listings.limits.default", 3);
        return Math.max(0, integer("sell.limits." + group(player), "listings.limits." + group(player), fallback));
    }

    public int kitSlots(Player player) {
        int fallback = integer("sell.kits.max-slots.default", "listings.kits.max-slots.default", 10);
        return clampSlots(integer("sell.kits.max-slots." + group(player),
                "listings.kits.max-slots." + group(player), fallback));
    }

    public long expiresAt(Player player, long createdAt) {
        String fallback = string("sell.expiration.default", "listings.expiration.default", "24h");
        String duration = string("sell.expiration.groups." + group(player),
                "listings.expiration.groups." + group(player),
                string("sell.expiration.groups.default", "listings.expiration.groups.default", fallback));
        if (!validDuration(duration)) duration = "24h";
        long millis = NumberParser.parseDurationMillis(duration);
        return createdAt > Long.MAX_VALUE - millis ? Long.MAX_VALUE : createdAt + millis;
    }

    private int integer(String path, String legacy, int fallback) {
        return plugin.getConfig().contains(path)
                ? plugin.getConfig().getInt(path, fallback) : plugin.getConfig().getInt(legacy, fallback);
    }

    private String string(String path, String legacy, String fallback) {
        return plugin.getConfig().contains(path)
                ? plugin.getConfig().getString(path, fallback) : plugin.getConfig().getString(legacy, fallback);
    }

    private String group(Player player) {
        Permission permission = currencies.permission();
        if (permission == null) return "default";
        try {
            String value = permission.getPrimaryGroup(player);
            return value == null || value.isBlank() ? "default" : value.toLowerCase(Locale.ROOT);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Не удалось определить группу игрока: " + exception.getMessage());
            return "default";
        }
    }

    private boolean validDuration(String value) {
        try {
            NumberParser.parseDurationMillis(value);
            return true;
        } catch (RuntimeException ignored) {
            plugin.getLogger().warning("Пропущен некорректный срок лота: " + value);
            return false;
        }
    }

    private int clampSlots(int value) {
        return Math.max(1, Math.min(36, value));
    }
}
