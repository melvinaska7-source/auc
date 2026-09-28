package ru.privatenull.market;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Stores bundle contents separately from the version-specific visual item. */
public final class MarketBundle {
    private static final String BUNDLE_KEY = "market_bundle";
    private static final String CONTENTS_KEY = "market_bundle_contents";

    private MarketBundle() {
    }

    public static ItemStack create(Plugin plugin, List<ItemStack> contents) {
        return create(plugin, contents, "Набор");
    }

    public static ItemStack create(Plugin plugin, List<ItemStack> contents, String name) {
        if (contents == null || contents.isEmpty()) {
            throw new IllegalArgumentException("A bundle must contain at least one item");
        }

        int count = count(contents);
        byte[] encodedContents = encode(contents);
        ItemStack bundle = new ItemStack(visualMaterial(count));
        ItemMeta meta = bundle.getItemMeta();
        if (meta == null) throw new IllegalStateException("Bundle item meta is unavailable");
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(contentsKey(plugin), PersistentDataType.BYTE_ARRAY, encodedContents);
        meta.setDisplayName("§6" + sanitizeName(name));
        try {
            meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
        } catch (IllegalArgumentException ignored) {
            // The flag does not exist on legacy server versions.
        }
        bundle.setItemMeta(meta);
        return bundle;
    }

    public static int serializedSize(List<ItemStack> contents) {
        if (contents == null || contents.isEmpty()) return 0;
        return encode(contents).length;
    }

    public static String displayName(ItemStack bundle) {
        if (bundle == null || bundle.getType().isAir()) return "Набор";
        ItemMeta meta = bundle.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return "Набор";
        String name = org.bukkit.ChatColor.stripColor(meta.getDisplayName());
        return name == null || name.isBlank() ? "Набор" : name.trim();
    }

    public static Rarity rarity(List<ItemStack> contents) {
        int score = 0;
        if (contents != null) {
            for (ItemStack item : contents) {
                if (item == null || item.getType().isAir()) continue;
                String material = item.getType().name();
                if (material.contains("NETHERITE") || material.equals("ELYTRA")
                        || material.equals("DRAGON_EGG") || material.equals("NETHER_STAR")
                        || material.equals("BEACON") || material.equals("ENCHANTED_GOLDEN_APPLE")) {
                    score += 8;
                } else if (material.contains("DIAMOND") || material.equals("TOTEM_OF_UNDYING")
                        || material.contains("SHULKER_BOX") || material.equals("TRIDENT")) {
                    score += 4;
                } else if (material.contains("GOLD") || material.contains("EMERALD")
                        || material.contains("IRON")) {
                    score += 2;
                } else {
                    score++;
                }

                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    score += meta.getEnchants().size() * 2;
                    if (meta instanceof EnchantmentStorageMeta stored) {
                        score += stored.getStoredEnchants().size() * 2;
                    }
                    if (meta.hasCustomModelData()) score += 3;
                }
            }
        }
        if (score >= 40) return Rarity.LEGENDARY;
        if (score >= 24) return Rarity.EPIC;
        if (score >= 12) return Rarity.RARE;
        if (score >= 6) return Rarity.UNCOMMON;
        return Rarity.COMMON;
    }

    public static boolean isBundle(Plugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte marker = meta.getPersistentDataContainer().get(key(plugin), PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public static List<ItemStack> contents(Plugin plugin, ItemStack bundle) {
        if (bundle == null || bundle.getType().isAir()) return List.of();
        ItemMeta itemMeta = bundle.getItemMeta();
        if (itemMeta != null) {
            byte[] serialized = itemMeta.getPersistentDataContainer().get(contentsKey(plugin), PersistentDataType.BYTE_ARRAY);
            if (serialized != null) return decode(serialized);
        }

        // Compatibility with the first release of kits, where contents were in a shulker state.
        if (!(itemMeta instanceof BlockStateMeta meta)) return List.of();
        if (!(meta.getBlockState() instanceof ShulkerBox shulker)) return List.of();
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : shulker.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) result.add(item.clone());
        }
        return result;
    }

    private static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, BUNDLE_KEY);
    }

    private static NamespacedKey contentsKey(Plugin plugin) {
        return new NamespacedKey(plugin, CONTENTS_KEY);
    }

    private static Material visualMaterial(int itemCount) {
        int minor = minecraftMinorVersion();
        if (minor >= 20) {
            if (itemCount <= 4) return materialFirst("BROWN_BUNDLE", "BUNDLE", "SHULKER_BOX");
            if (itemCount <= 9) return materialFirst("ORANGE_BUNDLE", "BUNDLE", "SHULKER_BOX");
            if (itemCount <= 14) return materialFirst("YELLOW_BUNDLE", "BUNDLE", "SHULKER_BOX");
            return materialFirst("RED_BUNDLE", "BUNDLE", "SHULKER_BOX");
        }
        if (minor >= 17) return Material.SHULKER_BOX;
        return Material.BARREL;
    }

    private static Material materialFirst(String... names) {
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) return material;
        }
        return Material.BARREL;
    }

    private static int minecraftMinorVersion() {
        try {
            String version = Bukkit.getBukkitVersion().split("-", 2)[0];
            String[] parts = version.split("\\.");
            return parts.length >= 2 ? Integer.parseInt(parts[1]) : 16;
        } catch (RuntimeException ignored) {
            return 16;
        }
    }

    private static byte[] encode(List<ItemStack> contents) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             BukkitObjectOutputStream data = new BukkitObjectOutputStream(output)) {
            List<ItemStack> items = contents.stream()
                    .filter(item -> item != null && !item.getType().isAir())
                    .map(ItemStack::clone)
                    .toList();
            data.writeInt(items.size());
            for (ItemStack item : items) data.writeObject(item);
            data.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot serialize bundle contents", exception);
        }
    }

    private static List<ItemStack> decode(byte[] serialized) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(serialized);
             BukkitObjectInputStream data = new BukkitObjectInputStream(input)) {
            int size = data.readInt();
            if (size < 1 || size > 36) return List.of();
            List<ItemStack> result = new ArrayList<>();
            for (int index = 0; index < size; index++) {
                Object value = data.readObject();
                if (value instanceof ItemStack item && !item.getType().isAir()) result.add(item);
            }
            return result;
        } catch (IOException | ClassNotFoundException exception) {
            return List.of();
        }
    }

    private static int count(List<ItemStack> items) {
        return (int) items.stream().filter(item -> item != null && !item.getType().isAir()).count();
    }

    private static String sanitizeName(String value) {
        String stripped = value == null ? "" : org.bukkit.ChatColor.stripColor(value);
        if (stripped == null) stripped = "";
        stripped = stripped.replaceAll("[\\r\\n\\t]", " ").trim().replaceAll("\\s{2,}", " ");
        if (stripped.isEmpty()) stripped = "Набор";
        if (stripped.length() > 32) stripped = stripped.substring(0, 32).trim();
        return stripped;
    }

    public enum Rarity {
        COMMON("§7Обычная"),
        UNCOMMON("§aНеобычная"),
        RARE("§9Редкая"),
        EPIC("§5Эпическая"),
        LEGENDARY("§6Легендарная");

        private final String displayName;

        Rarity(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
