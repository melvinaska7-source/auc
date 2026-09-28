package ru.privatenull.market;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.model.MarketListing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

public final class MarketCategories {
    public static final String ALL = "all";

    public record Definition(String id, String displayName, Set<Material> materials,
                             List<String> materialContains, List<String> nameContains,
                             boolean edible, boolean bundles) {
        boolean matches(ItemStack item) {
            if (bundles && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                    .getKeys().stream().anyMatch(key -> key.getKey().equals("market_bundle"))) return true;
            if (edible && item.getType().isEdible()) return true;
            if (materials.contains(item.getType())) return true;
            String materialName = item.getType().name();
            if (materialContains.stream().anyMatch(materialName::contains)) return true;
            String displayName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? ChatColor.stripColor(item.getItemMeta().getDisplayName()).toUpperCase(Locale.ROOT) : "";
            return nameContains.stream().anyMatch(displayName::contains);
        }
    }

    private final List<Definition> definitions;

    private MarketCategories(List<Definition> definitions) {
        this.definitions = definitions;
    }

    public static MarketCategories load(FileConfiguration config, Logger logger) {
        List<Definition> definitions = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("categories");
        if (section == null) return new MarketCategories(definitions);
        for (String id : section.getKeys(false)) {
            ConfigurationSection category = section.getConfigurationSection(id);
            if (category == null || ALL.equalsIgnoreCase(id)) continue;
            Set<Material> materials = new LinkedHashSet<>();
            for (String materialName : category.getStringList("materials")) {
                Material material = Material.matchMaterial(materialName);
                if (material == null) logger.warning("Неизвестный материал в categories." + id + ": " + materialName);
                else materials.add(material);
            }
            List<String> materialContains = normalize(category.getStringList("material-contains"));
            List<String> nameContains = normalize(category.getStringList("name-contains"));
            boolean edible = category.getBoolean("edible", false);
            if (materials.isEmpty() && materialContains.isEmpty() && nameContains.isEmpty() && !edible) {
                logger.warning("Категория " + id + " пропущена: укажите правило отбора предметов.");
                continue;
            }
            definitions.add(new Definition(id, category.getString("name", id), materials,
                    materialContains, nameContains, edible, "bundles".equalsIgnoreCase(id)));
        }
        return new MarketCategories(definitions);
    }

    public List<Definition> definitions() {
        return definitions;
    }

    public String categoryOf(MarketListing listing) {
        if (listing == null || listing.item() == null) return ALL;
        return categoryOf(listing.item());
    }

    public String categoryOf(ItemStack item) {
        if (item == null || item.getType().isAir()) return ALL;
        return definitions.stream().filter(definition -> definition.matches(item))
                .map(Definition::id).findFirst().orElse(ALL);
    }

    public String displayName(String id) {
        if (ALL.equals(id)) return "Все товары";
        return definitions.stream().filter(definition -> definition.id().equals(id))
                .map(Definition::displayName).findFirst().orElse(id);
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<>();
        ids.add(ALL);
        definitions.forEach(definition -> ids.add(definition.id()));
        return ids;
    }

    public String next(String current) {
        return cycle(current, 1);
    }

    public String previous(String current) {
        return cycle(current, -1);
    }

    private String cycle(String current, int step) {
        List<String> ids = ids();
        int index = ids.indexOf(current);
        if (index < 0) return ALL;
        return ids.get(Math.floorMod(index + step, ids.size()));
    }

    private static List<String> normalize(List<String> values) {
        return values.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT)).toList();
    }
}
