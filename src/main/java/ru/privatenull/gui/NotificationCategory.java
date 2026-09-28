package ru.privatenull.gui;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;

enum NotificationCategory {
    ALL("all", "CHEST"),
    COMBAT("combat", "DIAMOND_SWORD"),
    TOOLS("tools", "DIAMOND_PICKAXE"),
    BUILDING("building", "BRICKS"),
    DECORATION("decoration", "PAINTING"),
    REDSTONE("redstone", "REDSTONE"),
    TRANSPORT("transport", "MINECART"),
    FOOD("food", "GOLDEN_APPLE"),
    BREWING("brewing", "POTION"),
    RESOURCES("resources", "DIAMOND"),
    SPAWN_EGGS("spawn-eggs", "CREEPER_SPAWN_EGG"),
    MISC("misc", "SLIME_BALL");

    private final String id;
    private final String icon;

    NotificationCategory(String id, String icon) {
        this.id = id;
        this.icon = icon;
    }

    String id() {
        return id;
    }

    Material icon() {
        Material material = Material.matchMaterial(icon);
        return material == null ? Material.PAPER : material;
    }

    boolean matches(Material material) {
        if (this == ALL) return true;
        String name = material.name();
        return switch (this) {
            case COMBAT -> contains(name, "_SWORD", "BOW", "CROSSBOW", "TRIDENT", "SHIELD",
                    "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS", "ELYTRA", "ARROW", "MACE");
            case TOOLS -> contains(name, "_PICKAXE", "_AXE", "_SHOVEL", "_HOE", "FISHING_ROD",
                    "FLINT_AND_STEEL", "SHEARS", "BRUSH", "COMPASS", "CLOCK", "SPYGLASS");
            case BUILDING -> material.isBlock() && !isDecoration(name) && !isRedstone(name) && !isResource(name);
            case DECORATION -> isDecoration(name);
            case REDSTONE -> isRedstone(name);
            case TRANSPORT -> contains(name, "BOAT", "RAFT", "MINECART", "SADDLE", "HORSE_ARMOR");
            case FOOD -> material.isEdible();
            case BREWING -> contains(name, "POTION", "TIPPED_ARROW", "BREWING_STAND", "GLASS_BOTTLE", "BLAZE_POWDER",
                    "NETHER_WART", "FERMENTED_SPIDER_EYE", "GHAST_TEAR", "RABBIT_FOOT",
                    "PHANTOM_MEMBRANE", "MAGMA_CREAM", "SPIDER_EYE");
            case RESOURCES -> isResource(name);
            case SPAWN_EGGS -> name.endsWith("_SPAWN_EGG");
            case MISC -> specific().stream().noneMatch(category -> category.matches(material));
            default -> false;
        };
    }

    static NotificationCategory byId(String id) {
        return Arrays.stream(values()).filter(category -> category.id.equalsIgnoreCase(id))
                .findFirst().orElse(ALL);
    }

    static List<NotificationCategory> specific() {
        return Arrays.stream(values()).filter(category -> category != ALL && category != MISC).toList();
    }

    private static boolean isDecoration(String name) {
        return contains(name, "SIGN", "BANNER", "BED", "CARPET", "FLOWER", "SAPLING", "LEAVES",
                "GLASS", "PANE", "CANDLE", "SKULL", "HEAD", "PAINTING", "ITEM_FRAME", "FLOWER_POT",
                "CORAL", "VINE", "LANTERN", "TORCH", "CHAIN", "BOOKSHELF", "JUKEBOX", "BELL");
    }

    private static boolean isRedstone(String name) {
        return contains(name, "REDSTONE", "PISTON", "OBSERVER", "HOPPER", "DISPENSER", "DROPPER",
                "REPEATER", "COMPARATOR", "LEVER", "BUTTON", "PRESSURE_PLATE", "RAIL", "TARGET",
                "DAYLIGHT_DETECTOR", "TRIPWIRE", "SCULK_SENSOR");
    }

    private static boolean isResource(String name) {
        return contains(name, "_ORE", "_INGOT", "_NUGGET", "RAW_", "DIAMOND", "EMERALD", "COAL",
                "LAPIS", "QUARTZ", "NETHERITE", "AMETHYST", "COPPER", "IRON", "GOLD", "REDSTONE_DUST");
    }

    private static boolean contains(String value, String... fragments) {
        return Arrays.stream(fragments).anyMatch(value::contains);
    }
}
