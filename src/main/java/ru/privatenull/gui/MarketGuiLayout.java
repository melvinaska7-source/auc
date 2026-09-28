package ru.privatenull.gui;

import org.bukkit.configuration.ConfigurationSection;
import ru.privatenull.config.GuiConfig;

import java.util.*;

public record MarketGuiLayout(int size, List<Integer> listings, List<Integer> blackDecor,
                              List<Integer> orangeDecor, int auctionSwitch, int myItems,
                              int previous, int favorites, int next, int sort, int category) {
    private static final List<Integer> DEFAULT_LISTINGS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43);
    private static final List<Integer> DEFAULT_BLACK = List.of(
            0, 2, 3, 4, 5, 6, 8, 18, 26, 27, 35, 45, 47, 48, 49, 50, 51, 53);
    private static final List<Integer> DEFAULT_ORANGE = List.of(1, 7, 9, 17, 36, 44, 46, 52);

    public static MarketGuiLayout load(GuiConfig gui) {
        ConfigurationSection section = gui.configuration().getConfigurationSection("auction.layout");
        int rows = clamp(section == null ? 6 : section.getInt("rows", 6), 1, 6);
        int size = rows * 9;
        List<Integer> listings = slots(section, "listings", DEFAULT_LISTINGS, size);
        int bottom = size - 9;
        listings = listings.stream().filter(slot -> slot < bottom).toList();
        if (listings.isEmpty()) {
            List<Integer> generated = new ArrayList<>();
            for (int row = 0; row < rows - 1; row++) {
                for (int column = 1; column <= 7; column++) generated.add(row * 9 + column);
            }
            listings = List.copyOf(generated);
        }
        List<Integer> black = decorSlots(section, "decor.black", DEFAULT_BLACK, size);
        List<Integer> orange = decorSlots(section, "decor.orange", DEFAULT_ORANGE, size);
        Set<Integer> reserved = new LinkedHashSet<>();
        return new MarketGuiLayout(size, listings, black, orange,
                slot(section, "switch", 0, size, reserved), slot(section, "my-items", 1, size, reserved),
                slot(section, "previous", 2, size, reserved), slot(section, "favorites", 3, size, reserved),
                slot(section, "next", 6, size, reserved), slot(section, "sort", 7, size, reserved),
                slot(section, "category", 4, size, reserved));
    }

    public String role(int slot) {
        if (slot == auctionSwitch) return "switch";
        if (slot == myItems) return "my-items";
        if (slot == previous) return "previous";
        if (slot == favorites) return "favorites";
        if (slot == next) return "next";
        if (slot == sort) return "sort";
        if (slot == category) return "category";
        if (listings.contains(slot)) return "listings";
        if (blackDecor.contains(slot)) return "decor.black";
        if (orangeDecor.contains(slot)) return "decor.orange";
        return "empty";
    }

    private static int slot(ConfigurationSection section, String path, int fallbackColumn,
                            int size, Set<Integer> reserved) {
        int fallback = size - 9 + fallbackColumn;
        int value = section == null ? fallback : section.getInt(path, fallback);
        if (value < 0 || value >= size || reserved.contains(value)) value = fallback;
        if (reserved.contains(value)) {
            for (int candidate = size - 1; candidate >= 0; candidate--) {
                if (!reserved.contains(candidate)) {
                    value = candidate;
                    break;
                }
            }
        }
        reserved.add(value);
        return value;
    }

    private static List<Integer> slots(ConfigurationSection section, String path,
                                       List<Integer> fallback, int size) {
        List<Integer> source = section == null || !section.contains(path)
                ? fallback : section.getIntegerList(path);
        return source.stream().filter(slot -> slot >= 0 && slot < size).distinct().toList();
    }

    private static List<Integer> decorSlots(ConfigurationSection section, String path,
                                            List<Integer> fallback, int size) {
        List<Integer> source = section == null || !section.contains(path)
                ? fallback : section.getIntegerList(path);
        Set<Integer> result = new LinkedHashSet<>();
        source.stream().filter(slot -> slot >= 0 && slot < size).forEach(result::add);
        if (size < 54) {
            int bottom = size - 9;
            source.stream().filter(slot -> slot >= 45 && slot < 54)
                    .map(slot -> bottom + slot - 45).forEach(result::add);
        }
        return List.copyOf(result);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
